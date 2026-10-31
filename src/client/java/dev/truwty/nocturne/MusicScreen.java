package dev.truwty.nocturne;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class MusicScreen extends Screen {
    private final Screen parent;
    private final LyricPanel lyricPanel = new LyricPanel();
    private final Animation appear = new Animation(0);
    private int x, y, w, h, bodyY, bodyH, progressY, view, settingsPage;
    private boolean scrubbing;
    private double scrubPosition;
    private MusicButton play, previous, next;
    private MusicButton sourceButton;

    public MusicScreen(Screen parent) {
        super(Component.literal("Nocturne")); this.parent = parent;
    }

    @Override protected void init() {
        w = Math.min(660, width - 16); h = Math.min(354, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        bodyY = y + 50; bodyH = h - 144; progressY = y + h - 64;
        int tabsX = x + (w < 360 ? 80 : 104);
        button(tabsX, y + 12, 52, 23, "Player", false, b -> selectView(0)).selected(view == 0);
        button(tabsX + 56, y + 12, 52, 23, "Lyrics", false, b -> selectView(1)).selected(view == 1);
        button(tabsX + 112, y + 12, 62, 23, "Settings", false, b -> selectView(2)).selected(view == 2);
        button(x + w - 34, y + 12, 22, 23, "x", false, b -> onClose());
        play = null; next = null; previous = null; sourceButton = null;
        if (view != 2) {
        int center = x + w / 2, cy = y + h - 40;
        previous = button(center - 68, cy, 32, 28, "Previous", false, b -> NocturneClient.MEDIA.command("previous"));
        play = button(center - 26, cy, 52, 28, "Play", true, b -> NocturneClient.MEDIA.command("toggle"));
        next = button(center + 36, cy, 32, 28, "Next", false, b -> NocturneClient.MEDIA.command("next"));
        tooltip(previous, "Previous track"); tooltip(next, "Next track"); tooltip(play, "Play / pause");
        }
        if (view == 1) {
            button(x + w - 142, bodyY, 58, 18, "Follow", false, b -> lyricPanel.follow());
            button(x + w - 78, bodyY, 60, 18, "Reload", false, b -> NocturneClient.LYRICS.refresh(NocturneClient.MEDIA.state(), prefs().onlineLyrics));
            button(x + w - 148, y + h - 95, 36, 17, "-0.1s", false, b -> adjustOffset(-0.1));
            button(x + w - 108, y + h - 95, 36, 17, "+0.1s", false, b -> adjustOffset(0.1));
            button(x + w - 68, y + h - 95, 48, 17, "Reset", false, b -> { prefs().lyricsOffset = 0; prefs().save(); });
        }
        if (view == 2) initSettings();
        tick();
    }

    private Preferences prefs() { return NocturneClient.preferences; }
    private void selectView(int view) { this.view = view; appear.snap(0); rebuildWidgets(); }

    private void initSettings() {
        button(x + 18, bodyY, 86, 19, "Appearance", false, b -> { settingsPage = 0; rebuildWidgets(); }).selected(settingsPage == 0);
        button(x + 110, bodyY, 86, 19, "Controls", false, b -> { settingsPage = 1; rebuildWidgets(); }).selected(settingsPage == 1);
        int start = bodyY + 27, gap = 6, col = (w - 42) / 2;
        if (settingsPage == 0) {
            int cardWidth = (w - 48) / 3;
            int index = 0;
            for (Theme theme : Theme.values()) {
                int bx = x + 18 + (index % 3) * (cardWidth + 6), by = start + (index / 3) * 25;
                button(bx, by, cardWidth, 21, theme.label, false, b -> {
                    prefs().theme = theme.name(); prefs().save(); rebuildWidgets();
                }).selected(Theme.current() == theme);
                index++;
            }
            int row = start + 54;
            setting(x + 18, row, col, "Motion: " + (prefs().animations ? "Smooth" : "Reduced"), () -> prefs().animations = !prefs().animations);
            setting(x + 24 + col, row, col, "Opacity: " + prefs().hudOpacity + "%", () -> prefs().hudOpacity = prefs().hudOpacity >= 100 ? 40 : prefs().hudOpacity + 10);
            row += 26;
            setting(x + 18, row, col, "Lyrics size: " + Math.round(prefs().lyricsScale * 100) + "%", () -> prefs().lyricsScale = prefs().lyricsScale >= 1.49 ? 0.75 : prefs().lyricsScale + 0.25);
            setting(x + 24 + col, row, col, "Layout: " + modeName(), () -> prefs().lyricsMode = (prefs().lyricsMode + 1) % 3);
        } else {
            int row = start;
            var online = setting(x + 18, row, col, "Online lyrics: " + onOff(prefs().onlineLyrics), () -> prefs().onlineLyrics = !prefs().onlineLyrics);
            tooltip(online, "Sends title, artist, album and duration to lrclib.net. Off uses local files and cache only.");
            setting(x + 24 + col, row, col, "Lyrics overlay: " + onOff(prefs().lyricsHud), () -> prefs().lyricsHud = !prefs().lyricsHud);
            row += 26;
            setting(x + 18, row, col, "Music HUD: " + onOff(prefs().hud), () -> prefs().hud = !prefs().hud);
            setting(x + 24 + col, row, col, "HUD corner: " + (prefs().corner + 1), () -> prefs().corner = (prefs().corner + 1) % 4);
            row += 26;
            setting(x + 18, row, col, "Hide idle: " + onOff(prefs().hideWhenIdle), () -> prefs().hideWhenIdle = !prefs().hideWhenIdle);
            var pin = button(x + 24 + col, row, col, 21, LyricOverlay.pinned() ? "Move world lyrics here" : "Pin lyrics in world", false, b -> { LyricOverlay.pin(); rebuildWidgets(); });
            pin.active = minecraft.level != null; tooltip(pin, "Pins a camera-facing lyric display five blocks ahead. Hidden behind solid blocks.");
            row += 26;
            sourceButton = button(x + 18, row, col, 21, "Source: " + sourceLabel(), false, b -> cycleSource());
            button(x + 24 + col, row, col, 21, "Reconnect player", false, b -> NocturneClient.MEDIA.reconnect());
        }
    }

    private String sourceLabel() {
        String source = NocturneClient.MEDIA.state().selected();
        return source.isEmpty() ? "Auto" : TrackState.friendlySource(source);
    }
    private String onOff(boolean value) { return value ? "On" : "Off"; }
    private String modeName() { return new String[]{"Subtitle", "Side panel", "World pin"}[prefs().lyricsMode]; }
    private MusicButton setting(int bx, int by, int bw, String text, Runnable change) {
        return button(bx, by, bw, 21, text, false, b -> { change.run(); prefs().save(); rebuildWidgets(); });
    }
    private MusicButton button(int bx, int by, int bw, int bh, String text, boolean accent, net.minecraft.client.gui.components.Button.OnPress press) {
        return addRenderableWidget(new MusicButton(bx, by, bw, bh, text, accent, press));
    }
    private void tooltip(MusicButton button, String text) { button.setTooltip(Tooltip.create(Component.literal(text))); }

    private void cycleSource() {
        TrackState state = NocturneClient.MEDIA.state();
        int index = state.sources().indexOf(state.selected()) + 1;
        NocturneClient.MEDIA.select(index >= state.sources().size() ? "" : state.sources().get(index));
    }

    @Override public void tick() {
        TrackState state = NocturneClient.MEDIA.state();
        if (sourceButton != null) sourceButton.setMessage(Component.literal(font.plainSubstrByWidth("Source: " + sourceLabel(), sourceButton.getWidth() - 12)));
        if (play == null) return;
        play.setMessage(Component.literal(state.playing() ? "Pause" : "Play"));
        play.active = state.canToggle(); next.active = state.canNext(); previous.active = state.canPrevious();
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        Theme theme = Theme.current(); TrackState state = NocturneClient.MEDIA.state();
        double entrance = appear.approach(1, 14, prefs().animations);
        g.fill(0, 0, width, height, 0xA6000000);
        MusicVisuals.panel(g, x - 1, y - 1, w + 2, h + 2, theme.border);
        MusicVisuals.panel(g, x, y, w, h, theme.background);
        MusicVisuals.text(g, "Nocturne", x + 18, y + 20, theme.text, 82);
        g.fill(x + 18, y + 39, x + 34, y + 41, theme.highlight());
        int divider = view == 2 ? y + h - 36 : y + h - 75;
        g.fill(x + 18, divider, x + w - 18, divider + 1, theme.border);
        int dy = (int) ((1 - entrance) * 5);
        if (view == 0) drawPlayer(g, state, dy);
        else if (view == 1) drawLyrics(g, state);
        if (view != 2) drawTransport(g, state, mouseX, mouseY);
        super.extractRenderState(g, mouseX, mouseY, delta);
        if (view == 2) drawSettings(g);
    }

    private void drawPlayer(GuiGraphicsExtractor g, TrackState track, int dy) {
        Theme theme = Theme.current();
        int column = Math.min(180, (w - 48) / 3), art = Math.min(column - 12, Math.max(44, bodyH - 44));
        int albumX = x + 18, albumY = bodyY + dy;
        MusicVisuals.sleeve(g, albumX, albumY, art, track.playing());
        MusicVisuals.text(g, track.title(), albumX, albumY + art + 10, theme.text, column - 4);
        MusicVisuals.text(g, track.artist().isBlank() ? "Open your music app" : track.artist(), albumX, albumY + art + 24, theme.muted, column - 4);
        int lyricsX = x + column + 36, lyricsWidth = w - column - 54;
        MusicVisuals.text(g, "LYRICS", lyricsX + 14, bodyY + 1, theme.muted, lyricsWidth - 20);
        lyricPanel.draw(g, lyricsX, bodyY + 16 + dy, lyricsWidth, Math.max(32, bodyH - 16), NocturneClient.LYRICS.snapshot(), track.currentPosition() + prefs().lyricsOffset, false);
        String provider = NocturneClient.LYRICS.snapshot().message();
        MusicVisuals.text(g, provider, lyricsX + 14, y + h - 90, theme.muted, lyricsWidth - 20);
    }

    private void drawLyrics(GuiGraphicsExtractor g, TrackState track) {
        Theme theme = Theme.current();
        MusicVisuals.text(g, track.title(), x + 22, bodyY + 5, theme.muted, w - 182);
        lyricPanel.draw(g, x + 18, bodyY + 23, w - 36, Math.max(32, bodyH - 30), NocturneClient.LYRICS.snapshot(), track.currentPosition() + prefs().lyricsOffset, true);
        String timing = "Offset " + "%+.1fs".formatted(prefs().lyricsOffset) + " / click a line to seek";
        MusicVisuals.text(g, timing, x + 22, y + h - 90, theme.muted, w - 178);
    }

    private void drawSettings(GuiGraphicsExtractor g) {
        Theme theme = Theme.current();
        if (settingsPage == 0) {
            int cardWidth = (w - 48) / 3, index = 0;
            for (Theme option : Theme.values()) {
                int bx = x + 18 + (index % 3) * (cardWidth + 6), by = bodyY + 27 + (index / 3) * 25;
                g.fill(bx + 6, by + 6, bx + 9, by + 15, option.highlight()); index++;
            }
        }
        String footer = settingsPage == 0 ? "Themes apply to the player, HUD and lyrics. Changes save automatically."
                : "LRCLIB uses track metadata. Local files: config/nocturne/lyrics/";
        MusicVisuals.text(g, footer, x + 20, y + h - 22, theme.muted, w - 40);
    }

    private void drawTransport(GuiGraphicsExtractor g, TrackState track, int mouseX, int mouseY) {
        Theme theme = Theme.current();
        int left = x + 52, right = x + w - 52, length = right - left;
        double position = scrubbing ? scrubPosition : track.currentPosition();
        g.fill(left, progressY, right, progressY + 2, theme.border);
        int played = track.duration() > 0 ? (int) (length * position / track.duration()) : 0;
        g.fill(left, progressY, left + played, progressY + 2, theme.highlight());
        if (track.canSeek() && (scrubbing || mouseX >= left && mouseX <= right && Math.abs(mouseY - progressY) < 7)) {
            MusicVisuals.panel(g, left + played - 3, progressY - 2, 6, 6, theme.text);
        }
        MusicVisuals.text(g, TrackState.time(position), x + 18, progressY - 3, theme.muted, 32);
        String duration = TrackState.time(track.duration());
        g.text(font, duration, x + w - 18 - font.width(duration), progressY - 3, theme.muted, false);
        MusicVisuals.text(g, TrackState.friendlySource(track.source()), x + 18, y + h - 29, theme.muted, w / 2 - 100);
        MusicVisuals.text(g, track.playing() ? "Playing" : "Paused", x + w - 66, y + h - 29, theme.muted, 52);
        if (!track.error().isEmpty() && mouseY >= progressY) g.setTooltipForNextFrame(font, Component.literal(track.error()), mouseX, mouseY);
    }

    private void updateScrub(double mouseX) {
        TrackState track = NocturneClient.MEDIA.state();
        scrubPosition = Math.clamp((mouseX - x - 52) / Math.max(1, w - 104), 0, 1) * track.duration();
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        TrackState track = NocturneClient.MEDIA.state();
        if (view != 2 && event.button() == 0 && track.canSeek() && track.duration() > 0 && event.x() >= x + 52 && event.x() <= x + w - 52 && Math.abs(event.y() - progressY) <= 6) {
            scrubbing = true; updateScrub(event.x()); return true;
        }
        if (event.button() == 0 && view != 2 && track.canSeek()) {
            double seconds = lyricPanel.clickedLine(event.x(), event.y());
            if (seconds >= 0) { NocturneClient.MEDIA.seek(Math.max(0, seconds - prefs().lyricsOffset)); lyricPanel.follow(); return true; }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (scrubbing) { updateScrub(event.x()); return true; }
        return super.mouseDragged(event, dx, dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (scrubbing) { updateScrub(event.x()); NocturneClient.MEDIA.seek(scrubPosition); scrubbing = false; return true; }
        return super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (view != 2 && mouseY >= bodyY && mouseY < progressY - 16) { lyricPanel.scroll(vertical); return true; }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET) {
            adjustOffset(event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET ? 0.1 : -0.1); return true;
        }
        return super.keyPressed(event);
    }
    private void adjustOffset(double amount) { prefs().lyricsOffset = Math.clamp(prefs().lyricsOffset + amount, -10, 10); prefs().save(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
