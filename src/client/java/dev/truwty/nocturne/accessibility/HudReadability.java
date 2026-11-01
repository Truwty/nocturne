package dev.truwty.nocturne.accessibility;

import dev.truwty.nocturne.NocturneClient;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.FormattedCharSequence;

public final class HudReadability {
    private HudReadability() {}

    public static void text(
            GuiGraphicsExtractor graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int color) {
        boolean outline =
                NocturneClient.SETTINGS.lyricOutline.on() || Accessibility.photosensitive();
        if (outline) {
            int edge = (color & 0xFF000000) | (Accessibility.background() & 0xFFFFFF);
            graphics.text(font, text, x - 1, y, edge, false);
            graphics.text(font, text, x + 1, y, edge, false);
            graphics.text(font, text, x, y - 1, edge, false);
            graphics.text(font, text, x, y + 1, edge, false);
        }
        graphics.text(font, text, x, y, color, !outline);
    }
}
