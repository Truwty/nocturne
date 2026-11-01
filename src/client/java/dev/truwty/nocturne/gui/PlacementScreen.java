package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.setting.NumberSetting;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class PlacementScreen extends Screen {
    private final Screen parent;
    private int mode;
    private boolean dragging;
    private com.google.gson.JsonObject beforeDrag;

    public PlacementScreen(Screen parent) {
        super(Component.literal("Nocturne placement editor"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        String[] modes = {"Move", "Rotate", "Resize"};
        for (int i = 0; i < 3; i++) {
            int next = i;
            addRenderableWidget(
                    StudioButton.create(Component.literal(modes[i]), b -> mode = next)
                            .bounds(
                                    12 + i * Math.min(76, (width - 32) / 5),
                                    12,
                                    Math.min(72, (width - 32) / 5 - 4),
                                    20)
                            .build());
        }
        addRenderableWidget(
                StudioButton.create(
                                Component.literal("Reset"),
                                b -> {
                                    var before = dev.truwty.nocturne.Studio.snapshot("placement");
                                    var s = NocturneClient.SETTINGS;
                                    s.editorX.set(0.0);
                                    s.editorY.set(0.0);
                                    s.editorYaw.set(0.0);
                                    s.editorPitch.set(0.0);
                                    s.editorRoll.set(0.0);
                                    s.size.set(100.0);
                                    s.distance.set(3.5);
                                    StudioEdits.record(
                                            "placement",
                                            before,
                                            dev.truwty.nocturne.Studio.snapshot("placement"));
                                })
                        .bounds(
                                width - 16 - 2 * Math.min(74, (width - 32) / 5),
                                12,
                                Math.min(66, (width - 32) / 5 - 4),
                                20)
                        .build());
        addRenderableWidget(
                StudioButton.create(Component.literal("Save"), b -> onClose())
                        .bounds(
                                width - 16 - Math.min(74, (width - 32) / 5),
                                12,
                                Math.min(66, (width - 32) / 5 - 4),
                                20)
                        .build());
        addRenderableWidget(
                StudioButton.create(Component.literal("Undo"), b -> StudioEdits.undo("placement"))
                        .bounds(12, 36, 64, 20)
                        .build());
        addRenderableWidget(
                StudioButton.create(Component.literal("Redo"), b -> StudioEdits.redo("placement"))
                        .bounds(80, 36, 64, 20)
                        .build());
        addRenderableWidget(
                StudioButton.create(
                                Component.literal("HUD editor"),
                                b -> ClientAccess.setScreen(minecraft, new HudEditorScreen(this)))
                        .bounds(148, 36, 90, 20)
                        .build());
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        var s = NocturneClient.SETTINGS;
        g.fill(
                12,
                height - 53,
                width - 12,
                height - 12,
                Draw.alpha(NocturneClient.SETTINGS.uiBackground.argb(), .9f));
        Draw.text(
                g,
                font,
                "Drag to "
                        + new String[] {"move", "rotate", "resize"}[mode]
                        + " · Wheel: distance · Right drag: roll · Alt+arrows / right stick: nudge",
                20,
                height - 45,
                -1,
                false);
        Draw.text(
                g,
                font,
                "X "
                        + s.editorX.display()
                        + "  Y "
                        + s.editorY.display()
                        + "  Yaw "
                        + s.editorYaw.display()
                        + "  Pitch "
                        + s.editorPitch.display()
                        + "  Size "
                        + s.size.display(),
                20,
                height - 28,
                -1,
                false);
        Draw.roundOutline(
                g,
                width / 2 - 110,
                height / 2 - 45,
                220,
                90,
                6,
                Draw.alpha(s.uiAccent.argb(), .6f));
        Draw.textCentered(
                g,
                font,
                minecraft.level == null
                        ? "Join a world for a live preview"
                        : "Live lyric placement",
                width / 2f,
                height / 2f + 51,
                -1,
                1,
                false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (super.mouseClicked(event, twice)) return true;
        dragging = event.y() > 64 && event.y() < height - 60;
        if (dragging) beforeDrag = dev.truwty.nocturne.Studio.snapshot("placement");
        return dragging;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging) return super.mouseDragged(event, dx, dy);
        var s = NocturneClient.SETTINGS;
        if (event.button() == 1) adjust(s.editorRoll, dx);
        else if (mode == 0) {
            adjust(s.editorX, dx * 0.02);
            adjust(s.editorY, -dy * 0.02);
        } else if (mode == 1) {
            adjust(s.editorYaw, dx);
            adjust(s.editorPitch, dy);
        } else adjust(s.size, dx * 0.5 - dy * 0.5);
        return true;
    }

    private void adjust(NumberSetting setting, double delta) {
        setting.setFraction((setting.get() + delta - setting.min) / (setting.max - setting.min));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging && beforeDrag != null)
            StudioEdits.record(
                    "placement", beforeDrag, dev.truwty.nocturne.Studio.snapshot("placement"));
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        var before = dev.truwty.nocturne.Studio.snapshot("placement");
        adjust(NocturneClient.SETTINGS.distance, -sy * 0.5);
        StudioEdits.record("placement", before, dev.truwty.nocturne.Studio.snapshot("placement"));
        return true;
    }

    public void controllerNudge(double dx, double dy) {
        var before = dev.truwty.nocturne.Studio.snapshot("placement");
        var s = NocturneClient.SETTINGS;
        if (mode == 0) {
            adjust(s.editorX, dx * .02);
            adjust(s.editorY, -dy * .02);
        } else if (mode == 1) {
            adjust(s.editorYaw, dx);
            adjust(s.editorPitch, dy);
        } else adjust(s.size, dx * .5 - dy * .5);
        StudioEdits.record("placement", before, dev.truwty.nocturne.Studio.snapshot("placement"));
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if ((event.modifiers() & com.mojang.blaze3d.platform.InputConstants.MOD_CONTROL) != 0) {
            if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_Z) {
                if ((event.modifiers() & com.mojang.blaze3d.platform.InputConstants.MOD_SHIFT) != 0)
                    StudioEdits.redo("placement");
                else StudioEdits.undo("placement");
                return true;
            }
            if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_Y) {
                StudioEdits.redo("placement");
                return true;
            }
        }
        if ((event.modifiers() & com.mojang.blaze3d.platform.InputConstants.MOD_ALT) != 0) {
            switch (event.key()) {
                case com.mojang.blaze3d.platform.InputConstants.KEY_LEFT -> controllerNudge(-1, 0);
                case com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT -> controllerNudge(1, 0);
                case com.mojang.blaze3d.platform.InputConstants.KEY_UP -> controllerNudge(0, -1);
                case com.mojang.blaze3d.platform.InputConstants.KEY_DOWN -> controllerNudge(0, 1);
                default -> {
                    return super.keyPressed(event);
                }
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        NocturneClient.SETTINGS.save();
        try {
            dev.truwty.nocturne.Studio.savePlacement();
        } catch (java.io.IOException ex) {
            NocturneClient.LOG.warn("Placement save failed", ex);
        }
        ClientAccess.setScreen(minecraft, parent);
    }
}
