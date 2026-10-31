package dev.truwty.nocturne;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class MusicVisuals {
    private MusicVisuals() {}

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(5, Math.min(w, h) / 2);
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int row = 0; row < r; row++) {
            int inset = (int) Math.ceil(r - Math.sqrt(r * r - (r - row - 0.5) * (r - row - 0.5)));
            g.fill(x + inset, y + row, x + w - inset, y + row + 1, color);
            g.fill(x + inset, y + h - row - 1, x + w - inset, y + h - row, color);
        }
    }

    public static void text(GuiGraphicsExtractor g, String value, int x, int y, int color, int maxWidth) {
        if (maxWidth <= 0) return;
        var font = Minecraft.getInstance().font;
        String clipped = font.width(value) <= maxWidth ? value : font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
        g.text(font, clipped, x, y, color, false);
    }

    public static void scaledText(GuiGraphicsExtractor g, String text, float x, float y, float scale, int color, int width) {
        g.pose().pushMatrix(); g.pose().translate(x, y); g.pose().scale(scale, scale);
        text(g, text, 0, 0, color, (int) (width / scale));
        g.pose().popMatrix();
    }

    public static void sleeve(GuiGraphicsExtractor g, int x, int y, int size, boolean playing) {
        if (size < 1 || Artwork.draw(g, x, y, size)) return;
        Theme theme = Theme.current();
        panel(g, x, y, size, size, theme.elevated);
        int centerX = x + size / 2, centerY = y + size / 2;
        for (int ring = size / 3; ring > size / 7; ring -= Math.max(1, size / 22)) {
            for (int angle = 0; angle < 360; angle += 6) {
                double radians = Math.toRadians(angle);
                int px = centerX + (int) (Math.cos(radians) * ring), py = centerY + (int) (Math.sin(radians) * ring);
                g.fill(px, py, px + 1, py + 1, theme.border);
            }
        }
        panel(g, centerX - size / 12, centerY - size / 12, Math.max(3, size / 6), Math.max(3, size / 6), theme.highlight());
    }

    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color); g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color); g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void transportIcon(GuiGraphicsExtractor g, String icon, int cx, int cy, int color) {
        if (icon.equals("Pause")) {
            g.fill(cx - 4, cy - 5, cx - 1, cy + 5, color); g.fill(cx + 2, cy - 5, cx + 5, cy + 5, color); return;
        }
        boolean reverse = icon.equals("Previous");
        for (int column = 0; column < 8; column++) {
            int extent = (8 - column) * 5 / 8;
            int px = reverse ? cx + 3 - column : cx - 3 + column;
            g.fill(px, cy - extent, px + 1, cy + extent + 1, color);
        }
        if (icon.equals("Previous")) g.fill(cx - 7, cy - 5, cx - 5, cy + 5, color);
        if (icon.equals("Next")) g.fill(cx + 7, cy - 5, cx + 9, cy + 5, color);
    }

    public static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        var client = Minecraft.getInstance(); var prefs = NocturneClient.preferences;
        TrackState track = NocturneClient.MEDIA.state();
        if (client.options.hideGui || client.screen instanceof MusicScreen
                || (prefs.hideWhenIdle && (!track.playing() || track.source().isEmpty()))) return;
        if (prefs.hud) {
            int width = Math.min(220, g.guiWidth() - 16), height = 54;
            int x = prefs.corner % 2 == 0 ? 8 : g.guiWidth() - width - 8;
            int y = prefs.corner < 2 ? 8 : g.guiHeight() - height - 48;
            Theme theme = Theme.current();
            panel(g, x, y, width, height, Theme.alpha(theme.background, prefs.hudOpacity / 100.0));
            sleeve(g, x + 9, y + 9, 36, track.playing());
            text(g, track.title(), x + 55, y + 10, theme.text, width - 66);
            text(g, track.artist().isBlank() ? "N / Open Nocturne" : track.artist(), x + 55, y + 23, theme.muted, width - 66);
            g.fill(x + 55, y + 40, x + width - 10, y + 42, theme.border);
            if (track.duration() > 0) g.fill(x + 55, y + 40,
                    x + 55 + (int) ((width - 65) * track.currentPosition() / track.duration()), y + 42, theme.highlight());
        }
        if (prefs.lyricsHud) LyricOverlay.draw(g, track);
    }
}
