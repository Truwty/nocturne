package dev.truwty.nocturne;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;

public final class LyricPanel {
    private record Row(int index, List<FormattedCharSequence> text, double top, int height) {}
    private final Animation scroll = new Animation(0);
    private final Animation focus = new Animation(-1);
    private Lyrics previous;
    private int previousWidth;
    private double previousScale;
    private List<Row> rows = List.of();
    private double manualScroll;
    private long manualUntil;
    private double drawnScroll;
    private int x, y, width, height;

    public void follow() { manualUntil = 0; }

    public void scroll(double amount) {
        manualScroll = drawnScroll - amount * 32;
        double maximum = rows.isEmpty() ? 0 : rows.getLast().top();
        manualScroll = Math.clamp(manualScroll, 0, maximum);
        manualUntil = System.nanoTime() + 4_000_000_000L;
    }

    public void draw(GuiGraphicsExtractor g, int x, int y, int width, int height, LyricsService.Snapshot snapshot, double position, boolean large) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        Theme theme = Theme.current();
        Lyrics lyrics = snapshot.lyrics();
        var font = Minecraft.getInstance().font;
        double baseScale = (large ? 1.3 : 1.0) * NocturneClient.preferences.lyricsScale;
        if (lyrics != previous || width != previousWidth || baseScale != previousScale) {
            previousScale = baseScale;
            previous = lyrics; previousWidth = width; manualUntil = 0; manualScroll = 0;
            var built = new ArrayList<Row>();
            double top = 0;
            List<String> texts = lyrics.synced() ? lyrics.lines().stream().map(Lyrics.Line::text).toList()
                    : lyrics.plain().lines().toList();
            for (int i = 0; i < texts.size(); i++) {
                var wrapped = font.split(Component.literal(texts.get(i).isBlank() ? "..." : texts.get(i)), Math.max(24, (int) ((width - 30) / (baseScale * 1.08))));
                int rowHeight = Math.max(30, (int) (wrapped.size() * 12 * baseScale + 16));
                built.add(new Row(i, wrapped, top, rowHeight)); top += rowHeight;
            }
            rows = List.copyOf(built);
            int current = lyrics.activeIndex(position);
            scroll.snap(current >= 0 && current < rows.size() ? rows.get(current).top() : 0);
            focus.snap(current);
        }
        if (rows.isEmpty()) {
            String title = switch (snapshot.status()) {
                case LOADING -> "Finding the words";
                case INSTRUMENTAL -> "Just the music";
                case OFFLINE -> "Offline lyrics";
                case ERROR -> "Could not load lyrics";
                case MISSING -> "Lyrics not found";
                default -> "Your next track starts here";
            };
            MusicVisuals.text(g, title, x + 14, y + height / 2 - 18, theme.text, width - 28);
            g.textWithWordWrap(font, Component.literal(snapshot.message()), x + 14, y + height / 2, width - 28, theme.muted, false);
            return;
        }
        int active = lyrics.activeIndex(position);
        boolean manual = System.nanoTime() < manualUntil || !lyrics.synced();
        double target = manual ? manualScroll : active >= 0 ? rows.get(active).top() : 0;
        drawnScroll = scroll.approach(target, 10, NocturneClient.preferences.animations);
        double focused = focus.approach(active, 12, NocturneClient.preferences.animations);
        g.enableScissor(x, y, x + width, y + height);
        for (Row row : rows) {
            float rowY = (float) (y + height * 0.38 + row.top() - drawnScroll);
            if (rowY + row.height() < y || rowY > y + height) continue;
            double intensity = lyrics.synced() ? Math.max(0, 1 - Math.abs(row.index() - focused)) : 0.4;
            double fade = Math.min(1, Math.max(0.08, Math.min((rowY - y + 14) / 26, (y + height - rowY) / 26)));
            int color = Theme.alpha(Theme.blend(theme.muted, theme.text, intensity), fade);
            if (row.index() == active && lyrics.synced()) g.fill(x + 1, (int) rowY - 1, x + 3, (int) rowY + 9, theme.highlight());
            float scale = (float) (baseScale * (1 + intensity * 0.07));
            g.pose().pushMatrix(); g.pose().translate(x + 14, rowY); g.pose().scale(scale, scale);
            int lineY = 0;
            for (var line : row.text()) { g.text(font, line, 0, lineY, color, false); lineY += 12; }
            g.pose().popMatrix();
            if (row.index() == active && !lyrics.lines().get(active).words().isEmpty() && row.text().size() == 1) {
                String text = lyrics.lines().get(active).text();
                int sungWidth = (int) (font.width(text) * scale * lyrics.wordProgress(active, position));
                if (sungWidth > 0) {
                    g.enableScissor(x + 14, Math.max(y, (int) rowY - 1), Math.min(x + width, x + 14 + sungWidth), Math.min(y + height, (int) rowY + 14));
                    MusicVisuals.scaledText(g, text, x + 14, rowY, scale, theme.highlight(), width - 28);
                    g.disableScissor();
                }
            }
        }
        g.disableScissor();
    }

    public double clickedLine(double mouseX, double mouseY) {
        if (previous == null || !previous.synced() || mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return -1;
        double top = mouseY - y - height * 0.38 + drawnScroll;
        for (Row row : rows) if (top >= row.top() - 4 && top < row.top() + row.height() - 4) return previous.lines().get(row.index()).seconds();
        return -1;
    }
}
