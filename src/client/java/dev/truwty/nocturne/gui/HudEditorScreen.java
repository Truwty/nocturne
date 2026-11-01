package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import java.util.*;

public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private final Map<String, SnapshotHistory> histories = new HashMap<>();
    private String context = "Gameplay", selected = "Karaoke", notice = "";
    private HudLayout layout;
    private boolean snapping = true, dragging, controlsHidden;
    private double dragX, dragY;
    private Double guideX, guideY;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("Nocturne HUD editor"));
        this.parent = parent;
        for (String key : List.of("Gameplay", "Menu"))
            histories.put(key, new SnapshotHistory(HudLayouts.load(key).json()));
        layout = HudLayout.parse(histories.get(context).value());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public String editingContext() {
        return context;
    }

    public com.google.gson.JsonObject draft(String key) {
        return histories.get(key).value();
    }

    public void applyPreset(String key, com.google.gson.JsonObject values) {
        histories.get(key).change(HudLayout.parse(values).json());
        if (context.equals(key)) layout = HudLayout.parse(histories.get(key).value());
        rebuildWidgets();
    }

    private void update(HudLayout next) {
        histories.get(context).change(next.json());
        layout = next;
        rebuildWidgets();
    }

    private void attempt(Action action) {
        try {
            action.run();
        } catch (Exception ex) {
            notice = ex.getMessage();
            NocturneClient.LOG.debug("HUD editor action failed", ex);
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws Exception;
    }

    private Button button(
            int column, int row, int columns, String label, String tip, Action action) {
        int bw = (width - 24) / columns;
        Button button =
                StudioButton.create(Draw.comp(label), b -> attempt(action))
                        .bounds(12 + column * bw, 12 + row * 24, bw - 4, 20)
                        .build();
        button.setTooltip(Tooltip.create(Component.literal(tip)));
        return addRenderableWidget(button);
    }

    @Override
    protected void init() {
        if (controlsHidden) {
            var show =
                    StudioButton.create(
                                    Draw.comp("Show controls"),
                                    b -> {
                                        controlsHidden = false;
                                        rebuildWidgets();
                                    })
                            .bounds(width - 112, 12, 100, 20)
                            .build();
            addRenderableWidget(show);
            return;
        }
        var hide =
                StudioButton.create(
                                Draw.comp("Hide controls"),
                                b -> {
                                    controlsHidden = true;
                                    rebuildWidgets();
                                })
                        .bounds(width - 112, 64, 100, 20)
                        .build();
        hide.setTooltip(
                Tooltip.create(
                        Component.literal(
                                "H hides editor panels so you can drag HUD elements underneath"
                                        + " them")));
        addRenderableWidget(hide);
        button(
                0,
                0,
                5,
                context,
                "Switch layout; both drafts retain their own undo history",
                () -> {
                    context = context.equals("Gameplay") ? "Menu" : "Gameplay";
                    layout = HudLayout.parse(histories.get(context).value());
                    rebuildWidgets();
                });
        button(
                1,
                0,
                5,
                selected,
                "Choose the HUD element to edit",
                () -> {
                    selected =
                            HudLayout.WIDGETS.get(
                                    (HudLayout.WIDGETS.indexOf(selected) + 1)
                                            % HudLayout.WIDGETS.size());
                    rebuildWidgets();
                });
        button(
                2,
                0,
                5,
                layout.node(selected).visible() ? "Visible" : "Hidden",
                "Toggle this element in this layout",
                () -> {
                    var n = layout.node(selected);
                    update(
                            layout.with(
                                    selected,
                                    new HudLayout.Node(n.x(), n.y(), n.scale(), !n.visible())));
                });
        button(
                3,
                0,
                5,
                snapping ? "Snap on" : "Snap off",
                "Snap to screen edges, center, other widgets and an 8px grid",
                () -> {
                    snapping = !snapping;
                    rebuildWidgets();
                });
        button(
                4,
                0,
                5,
                "Save layouts",
                "Save gameplay and menu drafts",
                () -> {
                    for (String key : List.of("Gameplay", "Menu"))
                        HudLayouts.save(key, HudLayout.parse(histories.get(key).value()));
                    minecraft.setScreen(parent);
                });
        button(
                0,
                1,
                5,
                "Undo",
                "Undo layout change (Ctrl+Z)",
                () -> {
                    histories.get(context).undo();
                    layout = HudLayout.parse(histories.get(context).value());
                    rebuildWidgets();
                });
        button(
                1,
                1,
                5,
                "Redo",
                "Redo layout change (Ctrl+Y)",
                () -> {
                    histories.get(context).redo();
                    layout = HudLayout.parse(histories.get(context).value());
                    rebuildWidgets();
                });
        button(
                2,
                1,
                5,
                "Reset",
                "Reset this draft to its context defaults",
                () -> update(HudLayout.defaults(context.equals("Menu"))));
        button(
                3,
                1,
                5,
                "Presets",
                "Save, favorite, preview and share HUD layouts",
                () -> minecraft.setScreen(new PresetBrowserScreen(this, "hudLayouts")));
        button(4, 1, 5, "Cancel", "Discard unsaved layout edits", this::onClose);
        int by = height - 85, bw = (width - 24) / 5;
        addRenderableWidget(
                new StudioSlider(
                        12,
                        by,
                        width - 24,
                        20,
                        Component.empty(),
                        (layout.node(selected).scale() - .5) / 1.5) {
                    {
                        updateMessage();
                        setTooltip(
                                Tooltip.create(
                                        Component.literal(
                                                "Scale selected element from 50% to 200%")));
                    }

                    protected void updateMessage() {
                        setMessage(
                                Draw.comp("Scale: " + Math.round((.5 + value * 1.5) * 100) + "%"));
                    }

                    protected void applyValue() {
                        var n = layout.node(selected);
                        layout =
                                layout.with(
                                        selected,
                                        new HudLayout.Node(
                                                n.x(), n.y(), .5 + value * 1.5, n.visible()));
                        updateMessage();
                    }

                    private void commit() {
                        histories.get(context).change(layout.json());
                    }

                    public void onRelease(MouseButtonEvent event) {
                        super.onRelease(event);
                        commit();
                    }

                    public boolean keyPressed(KeyEvent event) {
                        boolean handled = super.keyPressed(event);
                        if (handled) commit();
                        return handled;
                    }
                });
        String[] names = {"Left edge", "Center X", "Right edge", "Center Y", "Bottom"};
        for (int i = 0; i < 5; i++) {
            int align = i;
            var b =
                    StudioButton.create(Draw.comp(names[i]), ignored -> align(align))
                            .bounds(12 + i * bw, by + 24, bw - 4, 20)
                            .build();
            b.setTooltip(
                    Tooltip.create(
                            Component.literal("Align selected element within the safe margin")));
            addRenderableWidget(b);
        }
    }

    private double logicalWidth(String id) {
        return switch (id) {
            case "Karaoke" -> Math.min(width - 16, 370);
            case "Secondary", "Confidence" -> Math.min(width - 16, 300);
            case "Diagnostics" -> Math.min(width - 16, 280);
            default -> 120;
        };
    }

    private double logicalHeight(String id) {
        return switch (id) {
            case "Karaoke" -> 30;
            case "Secondary" -> 26;
            case "Diagnostics" -> 100;
            default -> 14;
        };
    }

    private Map<String, HudLayout.Box> boxes() {
        var boxes = new LinkedHashMap<String, HudLayout.Box>();
        for (String id : HudLayout.WIDGETS)
            boxes.put(id, layout.node(id).box(width, height, logicalWidth(id), logicalHeight(id)));
        return boxes;
    }

    private void align(int alignment) {
        var box = boxes().get(selected);
        double cx = box.centerX(), cy = box.centerY();
        switch (alignment) {
            case 0 -> cx = HudLayout.MARGIN + box.width() / 2;
            case 1 -> cx = width / 2.0;
            case 2 -> cx = width - HudLayout.MARGIN - box.width() / 2;
            case 3 -> cy = height / 2.0;
            default -> cy = height - HudLayout.MARGIN - box.height() / 2;
        }
        move(cx, cy, false);
        histories.get(context).change(layout.json());
    }

    public void nudge(double dx, double dy) {
        var box = boxes().get(selected);
        move(box.centerX() + dx, box.centerY() + dy, false);
        histories.get(context).change(layout.json());
    }

    private void move(double cx, double cy, boolean snap) {
        if (snap) {
            cx = Math.round(cx / 8) * 8;
            cy = Math.round(cy / 8) * 8;
        }
        var result =
                layout.move(
                        selected,
                        cx,
                        cy,
                        width,
                        height,
                        logicalWidth(selected),
                        logicalHeight(selected),
                        snap,
                        boxes());
        layout = layout.with(selected, result.node());
        guideX = result.guideX();
        guideY = result.guideY();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (super.mouseClicked(event, twice)) return true;
        if (event.button() != 0) return false;
        var boxes = boxes();
        var order = new ArrayList<>(HudLayout.WIDGETS);
        order.remove(selected);
        order.addFirst(selected);
        for (String id : order) {
            var box = boxes.get(id);
            if (box.contains(event.x(), event.y())) {
                selected = id;
                dragX = event.x() - box.centerX();
                dragY = event.y() - box.centerY();
                dragging = true;
                rebuildWidgets();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            move(event.x() - dragX, event.y() - dragY, snapping);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            histories.get(context).change(layout.json());
            guideX = guideY = null;
            rebuildWidgets();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_H) {
            controlsHidden = !controlsHidden;
            rebuildWidgets();
            return true;
        }
        if ((event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0
                && (event.key() == GLFW.GLFW_KEY_Z || event.key() == GLFW.GLFW_KEY_Y)) {
            if (event.key() == GLFW.GLFW_KEY_Y || (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0)
                histories.get(context).redo();
            else histories.get(context).undo();
            layout = HudLayout.parse(histories.get(context).value());
            rebuildWidgets();
            return true;
        }
        if ((event.modifiers() & GLFW.GLFW_MOD_ALT) != 0) {
            int step = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? 10 : 1;
            switch (event.key()) {
                case GLFW.GLFW_KEY_LEFT -> nudge(-step, 0);
                case GLFW.GLFW_KEY_RIGHT -> nudge(step, 0);
                case GLFW.GLFW_KEY_UP -> nudge(0, -step);
                case GLFW.GLFW_KEY_DOWN -> nudge(0, step);
                default -> {
                    return super.keyPressed(event);
                }
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x55000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var s = NocturneClient.SETTINGS;
        Draw.roundOutline(g, 8, 8, width - 16, height - 16, 4, Draw.alpha(s.uiAccent.argb(), .35f));
        for (var entry : boxes().entrySet()) {
            var box = entry.getValue();
            boolean picked = selected.equals(entry.getKey()),
                    visible = layout.node(entry.getKey()).visible();
            int col = picked ? s.uiAccent.argb() : 0xff9e9aaa;
            Draw.round(
                    g,
                    (int) box.x(),
                    (int) box.y(),
                    (int) box.width(),
                    (int) box.height(),
                    3,
                    Draw.alpha(s.uiBackground.argb(), visible ? .8f : .4f));
            Draw.roundOutline(
                    g,
                    (int) box.x(),
                    (int) box.y(),
                    (int) box.width(),
                    (int) box.height(),
                    3,
                    Draw.alpha(col, visible ? 1 : .4f));
            Draw.text(
                    g,
                    font,
                    entry.getKey() + (visible ? "" : " / hidden"),
                    (float) box.x() + 5,
                    (float) box.y() + 4,
                    col,
                    false);
        }
        if (guideX != null)
            g.fill(guideX.intValue(), 8, guideX.intValue() + 1, height - 8, 0xff62e9d0);
        if (guideY != null)
            g.fill(8, guideY.intValue(), width - 8, guideY.intValue() + 1, 0xff62e9d0);
        if (!controlsHidden) {
            g.fill(6, 6, width - 6, 60, Draw.alpha(s.uiBackground.argb(), .95f));
            g.fill(6, height - 91, width - 6, height - 6, Draw.alpha(s.uiBackground.argb(), .95f));
        }
        super.extractRenderState(g, mx, my, delta);
        Draw.text(
                g,
                font,
                font.plainSubstrByWidth(
                        notice.isBlank()
                                ? "Drag widgets · H: hide controls · Alt+arrows / right stick:"
                                        + " nudge · 8px safe margin"
                                : notice,
                        width - 32),
                16,
                height - 26,
                s.uiText.argb(),
                false);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
