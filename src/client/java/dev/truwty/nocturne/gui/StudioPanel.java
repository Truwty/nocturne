package dev.truwty.nocturne.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.compat.ClientAccess;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public abstract class StudioPanel extends Screen {
    protected final Screen parent;
    protected int x, y, w, h, body, scroll;
    protected String notice = "";

    protected StudioPanel(Screen parent, String title) {
        super(Component.literal(title));
        this.parent = parent;
    }

    protected abstract void build();

    protected int contentHeight() {
        return 270;
    }

    protected int maxScroll() {
        return Math.max(0, contentHeight() - (h - 110));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected final void init() {
        w = Math.min(650, width - 16);
        h = Math.min(406, height - 16);
        x = (width - w) / 2;
        y = (height - h) / 2;
        scroll = Math.clamp(scroll, 0, maxScroll());
        body = y + 52 - scroll;
        build();
        for (var child : children())
            if (child instanceof AbstractWidget widget)
                widget.visible = widget.getY() >= y + 48 && widget.getBottom() <= y + h - 52;
        footer();
    }

    protected void footer() {
        button(x + 12, y + h - 29, 90, "Back", "Return to the previous screen", this::onClose);
    }

    protected Button button(int bx, int by, int bw, String label, String tip, Action action) {
        Button button =
                StudioButton.create(Draw.comp(label), b -> attempt(action))
                        .bounds(bx, by, Math.max(28, bw), 20)
                        .build();
        button.setTooltip(Tooltip.create(Component.literal(tip)));
        return addRenderableWidget(button);
    }

    protected EditBox field(int bx, int by, int bw, String label, String value) {
        EditBox field = new EditBox(font, bx, by, bw, 20, Component.literal(label));
        field.setMaxLength(16384);
        field.setValue(value);
        field.setHint(Component.literal(label));
        field.setTooltip(Tooltip.create(Component.literal(label)));
        return addRenderableWidget(field);
    }

    protected void slider(
            int bx,
            int by,
            int bw,
            String label,
            double value,
            java.util.function.DoubleConsumer update) {
        addRenderableWidget(
                new StudioSlider(bx, by, bw, 20, Component.empty(), value) {
                    {
                        updateMessage();
                        setTooltip(
                                Tooltip.create(
                                        Component.literal(
                                                label
                                                        + ". Left/right arrows adjust; Enter"
                                                        + " toggles slider editing.")));
                    }

                    @Override
                    protected void updateMessage() {
                        setMessage(
                                Draw.comp(
                                        label
                                                + ": "
                                                + (label.equals("Hue")
                                                        ? Math.round(this.value * 360) + "°"
                                                        : Math.round(this.value * 100) + "%")));
                    }

                    @Override
                    protected void applyValue() {
                        update.accept(this.value);
                        updateMessage();
                    }
                });
    }

    @FunctionalInterface
    protected interface Action {
        void run() throws Exception;
    }

    protected void attempt(Action action) {
        try {
            action.run();
        } catch (Exception ex) {
            notice = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            NocturneClient.LOG.debug("Studio editor action failed", ex);
        }
    }

    protected boolean undo() {
        return false;
    }

    protected boolean redo() {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!(getFocused() instanceof EditBox)
                && (event.modifiers() & InputConstants.MOD_CONTROL) != 0) {
            if (event.key() == InputConstants.KEY_Z) {
                if ((event.modifiers() & InputConstants.MOD_SHIFT) != 0) redo();
                else undo();
                rebuildWidgets();
                return true;
            }
            if (event.key() == InputConstants.KEY_Y) {
                redo();
                rebuildWidgets();
                return true;
            }
        }
        if (event.key() == InputConstants.KEY_PAGEDOWN || event.key() == InputConstants.KEY_PAGEUP) {
            mouseScrolled(0, 0, 0, event.key() == InputConstants.KEY_PAGEDOWN ? -5 : 5);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Math.clamp(scroll - (int) Math.round(sy * 24), 0, maxScroll());
        rebuildWidgets();
        return true;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
        g.fill(0, 0, width, height, 0x88000000);
    }

    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {}

    protected void text(GuiGraphicsExtractor g, String value, int yy, int color) {
        Draw.text(g, font, font.plainSubstrByWidth(value, w - 32), x + 16, yy, color, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        var settings = NocturneClient.SETTINGS;
        Draw.round(
                g,
                x,
                y,
                w,
                h,
                12,
                Draw.alpha(settings.uiBackground.argb(), settings.uiOpacity.f() / 100));
        text(g, getTitle().getString(), y + 16, settings.uiText.argb());
        super.extractRenderState(g, mx, my, delta);
        g.enableScissor(x + 12, y + 48, x + w - 12, y + h - 52);
        content(g, mx, my, delta);
        g.disableScissor();
        if (maxScroll() > 0) {
            int top = y + 48,
                    available = h - 104,
                    thumb = Math.max(12, available * available / (available + maxScroll()));
            int thumbY = top + (available - thumb) * scroll / maxScroll();
            g.fill(x + w - 8, top, x + w - 6, top + available, 0x22333344);
            g.fill(x + w - 8, thumbY, x + w - 6, thumbY + thumb, settings.uiAccent.argb());
        }
        text(
                g,
                notice.isBlank()
                        ? (maxScroll() > 0
                                ? "Scroll / Page Up & Down · Tab to navigate"
                                : "Tab: focus · Enter: activate · Esc: back")
                        : notice,
                y + h - 45,
                settings.uiText.argb());
    }

    @Override
    public void onClose() {
        ClientAccess.setScreen(minecraft, parent);
    }
}
