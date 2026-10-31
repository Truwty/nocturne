package dev.truwty.nocturne;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class MusicVisuals {
    public static final int WHITE = 0xFFF1F3FF;
    public static final int MUTED = 0xFF8F99B5;
    public static final int CYAN = 0xFF64F4D5;
    public static final int VIOLET = 0xFFA397FF;

    private MusicVisuals() {}

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 3, y, x + w - 3, y + h, color);
        g.fill(x, y + 3, x + w, y + h - 3, color);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, color);
    }

    public static void text(GuiGraphicsExtractor g, String value, int x, int y, int color, int maxWidth) {
        var font = Minecraft.getInstance().font;
        String clipped = font.width(value) <= maxWidth ? value : font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width("..."))) + "...";
        g.text(font, clipped, x, y, color, false);
    }

    public static void sleeve(GuiGraphicsExtractor g, int x, int y, int size, boolean playing) {
        if (Artwork.draw(g, x, y, size)) return;
        g.fillGradient(x, y, x + size, y + size, 0xFF42346D, 0xFF122D3A);
        for (int i = 0; i < 7; i++) {
            int inset = size / 8 + i * size / 20;
            int color = i % 2 == 0 ? 0xFF5E5989 : 0xFF323655;
            outline(g, x + inset, y + inset, size - inset * 2, size - inset * 2, color);
        }
        int center = size / 2;
        panel(g, x + center - 8, y + center - 8, 16, 16, CYAN);
        g.fill(x + center - 2, y + center - 2, x + center + 2, y + center + 2, 0xFF15222C);
        bars(g, x + 6, y + size - 20, 20, playing);
    }

    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void bars(GuiGraphicsExtractor g, int x, int y, int height, boolean playing) {
        double time = NocturneClient.preferences.animations && playing ? System.nanoTime() / 280_000_000.0 : 0;
        for (int i = 0; i < 5; i++) {
            int h = playing ? (int) (3 + (height - 3) * (0.5 + 0.5 * Math.sin(time + i * 1.3))) : 3;
            g.fill(x + i * 4, y + height - h, x + i * 4 + 2, y + height, i % 2 == 0 ? CYAN : VIOLET);
        }
    }

    public static void hud(GuiGraphicsExtractor g, DeltaTracker delta) {
        var client = Minecraft.getInstance();
        var prefs = NocturneClient.preferences;
        TrackState track = NocturneClient.MEDIA.state();
        if (!prefs.hud || client.options.hideGui || client.screen instanceof MusicScreen
                || (prefs.hideWhenIdle && (!track.playing() || track.source().isEmpty()))) return;
        int width = Math.min(232, g.guiWidth() - 16);
        int height = 58;
        int x = prefs.corner % 2 == 0 ? 8 : g.guiWidth() - width - 8;
        int y = prefs.corner < 2 ? 8 : g.guiHeight() - height - 48;
        panel(g, x + 2, y + 3, width, height, 0x44000000);
        panel(g, x, y, width, height, 0xEB101521);
        g.fill(x + 5, y + 6, x + 7, y + height - 6, CYAN);
        sleeve(g, x + 14, y + 10, 36, track.playing());
        text(g, track.title(), x + 60, y + 11, WHITE, width - 72);
        text(g, track.artist().isBlank() ? "Press N to open Nocturne" : track.artist(), x + 60, y + 25, MUTED, width - 72);
        g.fill(x + 60, y + 42, x + width - 12, y + 44, 0xFF30384F);
        if (track.duration() > 0) {
            int progress = (int) ((width - 72) * track.currentPosition() / track.duration());
            g.fill(x + 60, y + 42, x + 60 + progress, y + 44, CYAN);
        }
    }
}
