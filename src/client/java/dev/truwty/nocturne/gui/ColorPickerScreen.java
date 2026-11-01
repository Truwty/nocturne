package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.IntConsumer;

public final class ColorPickerScreen extends StudioPanel {
    private PickerColor color;
    private final IntConsumer accept;
    private boolean dragging;

    public ColorPickerScreen(Screen parent, String label, int initial, IntConsumer accept) {
        super(parent, "Color / " + label);
        color = PickerColor.from(initial);
        this.accept = accept;
    }

    @Override
    protected int contentHeight() {
        return 272;
    }

    private void channel(int channel, double value) {
        color =
                switch (channel) {
                    case 0 ->
                            new PickerColor(
                                    value, color.saturation(), color.brightness(), color.opacity());
                    case 1 ->
                            new PickerColor(
                                    color.hue(), value, color.brightness(), color.opacity());
                    case 2 ->
                            new PickerColor(
                                    color.hue(), color.saturation(), value, color.opacity());
                    default ->
                            new PickerColor(
                                    color.hue(), color.saturation(), color.brightness(), value);
                };
    }

    @Override
    protected void build() {
        int cw = (w - 36) / 2;
        slider(x + 16, body + 104, cw, "Hue", color.hue(), value -> channel(0, value));
        slider(
                x + 20 + cw,
                body + 104,
                cw,
                "Saturation",
                color.saturation(),
                value -> channel(1, value));
        slider(
                x + 16,
                body + 128,
                cw,
                "Brightness",
                color.brightness(),
                value -> channel(2, value));
        slider(x + 20 + cw, body + 128, cw, "Opacity", color.opacity(), value -> channel(3, value));
        var hex =
                field(
                        x + 16,
                        body + 166,
                        w - 132,
                        "#RRGGBB or #AARRGGBB",
                        String.format("#%08X", color.argb()));
        hex.setMaxLength(9);
        button(
                x + w - 112,
                body + 166,
                96,
                "Read hex",
                "Validate and preview the entered ARGB color",
                () -> {
                    color = PickerColor.from(PickerColor.hex(hex.getValue()));
                    notice = "Color preview updated";
                    rebuildWidgets();
                });
        var recent = Studio.library().recentColors();
        for (int i = 0; i < recent.size(); i++) {
            int index = i;
            int bw = (w - 32) / 6;
            button(
                    x + 16 + (i % 6) * bw,
                    body + 215 + (i / 6) * 24,
                    bw - 3,
                    String.format("%06X", recent.get(i) & 0xffffff),
                    String.format("Recent color #%08X", recent.get(i)),
                    () -> {
                        color = PickerColor.from(recent.get(index));
                        rebuildWidgets();
                    });
        }
    }

    @Override
    protected void footer() {
        button(x + 12, y + h - 29, 90, "Cancel", "Discard this color", this::onClose);
        button(
                x + 106,
                y + h - 29,
                100,
                "Use color",
                "Use the previewed color and remember it",
                () -> {
                    Studio.library().rememberColor(color.argb());
                    accept.accept(color.argb());
                    minecraft.setScreen(parent);
                });
    }

    private int squareWidth() {
        return w - 112;
    }

    private void pick(double mx, double my) {
        color =
                new PickerColor(
                        color.hue(),
                        Math.clamp((mx - x - 16) / squareWidth(), 0, 1),
                        1 - Math.clamp((my - body) / 92, 0, 1),
                        color.opacity());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (event.button() == 0
                && event.x() >= x + 16
                && event.x() <= x + 16 + squareWidth()
                && event.y() >= body
                && event.y() <= body + 92
                && event.y() >= y + 48
                && event.y() < y + h - 52) {
            dragging = true;
            pick(event.x(), event.y());
            return true;
        }
        return super.mouseClicked(event, twice);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging) {
            pick(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            rebuildWidgets();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        Draw.hGradient(
                g,
                x + 16,
                body,
                squareWidth(),
                92,
                0xffffffff,
                new PickerColor(color.hue(), 1, 1, 1).argb());
        Draw.vGradient(g, x + 16, body, squareWidth(), 92, 0x00000000, 0xff000000);
        int markerX = x + 16 + (int) (color.saturation() * squareWidth()),
                markerY = body + (int) ((1 - color.brightness()) * 92);
        Draw.roundOutline(g, markerX - 3, markerY - 3, 6, 6, 2, -1);
        int sx = x + w - 84;
        for (int a = 0; a < 64; a += 8)
            for (int b = 0; b < 92; b += 8)
                g.fill(
                        sx + a,
                        body + b,
                        sx + a + 8,
                        body + Math.min(92, b + 8),
                        ((a + b) / 8 % 2 == 0) ? 0xff444444 : 0xff999999);
        g.fill(sx, body, sx + 64, body + 92, color.argb());
        text(
                g,
                String.format(
                        "Current #%08X · opacity %.0f%%", color.argb(), color.opacity() * 100),
                body + 192,
                NocturneClient.SETTINGS.uiText.argb());
        text(
                g,
                "Recent colors (saved across launches)",
                body + 202,
                NocturneClient.SETTINGS.uiText.argb());
        var recent = Studio.library().recentColors();
        int bw = (w - 32) / 6;
        for (int i = 0; i < recent.size(); i++) {
            int bx = x + 16 + (i % 6) * bw, by = body + 215 + (i / 6) * 24;
            g.fill(bx + 4, by + 16, bx + bw - 7, by + 18, recent.get(i));
        }
    }
}
