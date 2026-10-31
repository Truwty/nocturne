package dev.truwty.nocturne;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class MusicScreen extends Screen {
    private final Screen parent;
    private int x, y, w, h, side, content, contentWidth, progressY;
    private MusicButton play, previous, next, source;
    private List<String> sources = List.of();

    public MusicScreen(Screen parent) {
        super(Component.literal("Nocturne music cockpit"));
        this.parent = parent;
    }

    @Override protected void init() {
        w = Math.min(580, width - 16);
        h = Math.min(280, height - 16);
        x = (width - w) / 2;
        y = (height - h) / 2;
        side = w > 470 ? 128 : 102;
        content = x + side + 18;
        contentWidth = w - side - 36;
        progressY = y + h - 102;
        button(x + w - 32, y + 10, 22, 20, "x", false, b -> onClose());
        int controlsY = y + h - 78;
        int center = content + contentWidth / 2;
        previous = button(center - 88, controlsY, 44, 26, "|<", false, b -> NocturneClient.MEDIA.command("previous"));
        play = button(center - 36, controlsY, 72, 26, "PLAY", true, b -> NocturneClient.MEDIA.command("toggle"));
        next = button(center + 44, controlsY, 44, 26, ">|", false, b -> NocturneClient.MEDIA.command("next"));
        previous.setTooltip(Tooltip.create(Component.literal("Previous track")));
        next.setTooltip(Tooltip.create(Component.literal("Next track")));
        source = button(x + 10, y + 67, side - 20, 25, "Auto select", false, b -> cycleSource());
        source.setTooltip(Tooltip.create(Component.literal("Cycle through available desktop media sessions")));
        button(x + 10, y + 111, side - 20, 22, hudLabel(), false, b -> {
            NocturneClient.preferences.hud = !NocturneClient.preferences.hud;
            NocturneClient.preferences.save(); b.setMessage(Component.literal(hudLabel()));
        });
        button(x + 10, y + 139, side - 20, 22, "Move HUD", false, b -> {
            NocturneClient.preferences.corner = (NocturneClient.preferences.corner + 1) % 4;
            NocturneClient.preferences.save();
        }).setTooltip(Tooltip.create(Component.literal("Cycle HUD: top left, top right, bottom left, bottom right")));
        button(x + 10, y + 167, side - 20, 22, "Motion: " + (NocturneClient.preferences.animations ? "ON" : "OFF"), false, b -> {
            NocturneClient.preferences.animations = !NocturneClient.preferences.animations;
            NocturneClient.preferences.save();
            b.setMessage(Component.literal("Motion: " + (NocturneClient.preferences.animations ? "ON" : "OFF")));
        });
        if (h >= 254) button(x + 10, y + 195, side - 20, 22, "Idle: " + (NocturneClient.preferences.hideWhenIdle ? "HIDE" : "SHOW"), false, b -> {
            NocturneClient.preferences.hideWhenIdle = !NocturneClient.preferences.hideWhenIdle;
            NocturneClient.preferences.save();
            b.setMessage(Component.literal("Idle: " + (NocturneClient.preferences.hideWhenIdle ? "HIDE" : "SHOW")));
        });
        button(content, y + h - 31, 74, 19, "Reconnect", false, b -> NocturneClient.MEDIA.reconnect());
        tick();
    }

    private String hudLabel() { return "HUD: " + (NocturneClient.preferences.hud ? "ON" : "OFF"); }

    private MusicButton button(int bx, int by, int bw, int bh, String text, boolean accent, net.minecraft.client.gui.components.Button.OnPress press) {
        return addRenderableWidget(new MusicButton(bx, by, bw, bh, text, accent, press));
    }

    private void cycleSource() {
        TrackState state = NocturneClient.MEDIA.state();
        int index = sources.indexOf(state.selected()) + 1;
        NocturneClient.MEDIA.select(index >= sources.size() ? "" : sources.get(index));
    }

    @Override public void tick() {
        TrackState state = NocturneClient.MEDIA.state();
        sources = state.sources();
        play.setMessage(Component.literal(state.playing() ? "PAUSE" : "PLAY"));
        play.active = state.canToggle();
        next.active = state.canNext();
        previous.active = state.canPrevious();
        String selected = state.selected().isEmpty() ? "Auto select" : TrackState.friendlySource(state.selected());
        source.setMessage(Component.literal(font.plainSubstrByWidth(selected, side - 30)));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        TrackState state = NocturneClient.MEDIA.state();
        g.fill(0, 0, width, height, 0xCB070B14);
        MusicVisuals.panel(g, x + 3, y + 5, w, h, 0x66000000);
        MusicVisuals.panel(g, x, y, w, h, 0xFF101522);
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 42, 0xFF24233D, 0xFF141A2A);
        g.fill(x + side, y + 44, x + side + 1, y + h - 12, 0xFF293047);
        g.fill(x + 10, y + 14, x + 13, y + 28, MusicVisuals.CYAN);
        MusicVisuals.text(g, "N O C T U R N E", x + 22, y + 13, MusicVisuals.WHITE, w - 90);
        MusicVisuals.text(g, "YOUR SOUNDTRACK. YOUR WORLD.", x + 22, y + 27, MusicVisuals.MUTED, w - 90);
        MusicVisuals.text(g, "SOURCE", x + 12, y + 51, MusicVisuals.MUTED, side - 20);
        MusicVisuals.text(g, "PERSONALIZE", x + 12, y + 98, MusicVisuals.MUTED, side - 20);
        int cover = Math.min(100, Math.max(40, h - 174));
        int coverY = y + 58;
        MusicVisuals.sleeve(g, content, coverY, cover, state.playing());
        int titleX = content + cover + 14;
        int titleWidth = contentWidth - cover - 14;
        MusicVisuals.text(g, state.playing() ? "NOW PLAYING" : "READY WHEN YOU ARE", titleX, coverY + 3, MusicVisuals.CYAN, titleWidth);
        MusicVisuals.text(g, state.title(), titleX, coverY + 23, MusicVisuals.WHITE, titleWidth);
        MusicVisuals.text(g, state.artist().isBlank() ? "Start music in your desktop player" : state.artist(), titleX, coverY + 38, MusicVisuals.MUTED, titleWidth);
        if (cover > 70) MusicVisuals.text(g, state.album(), titleX, coverY + 53, MusicVisuals.MUTED, titleWidth);
        if (cover >= 70) MusicVisuals.text(g, TrackState.friendlySource(state.source()), titleX, coverY + cover - 11, MusicVisuals.VIOLET, titleWidth);
        g.fill(content, progressY, content + contentWidth, progressY + 3, 0xFF30384F);
        if (state.duration() > 0) {
            int progress = (int) (contentWidth * state.currentPosition() / state.duration());
            g.fill(content, progressY, content + progress, progressY + 3, MusicVisuals.CYAN);
        }
        MusicVisuals.text(g, TrackState.time(state.currentPosition()), content, progressY + 7, MusicVisuals.MUTED, 60);
        String duration = TrackState.time(state.duration());
        g.text(font, duration, content + contentWidth - font.width(duration), progressY + 7, MusicVisuals.MUTED, false);
        String status = state.error().isEmpty()
                ? state.source().isEmpty() ? "Start playback in a media-session compatible app" : "Connected / " + sources.size() + " media session(s)"
                : state.error();
        MusicVisuals.text(g, status, content, y + h - 45, state.error().isEmpty() ? MusicVisuals.MUTED : 0xFFFFB58B, contentWidth);
        if (!state.error().isEmpty() && mouseY >= y + h - 48 && mouseY <= y + h - 34 && mouseX >= content) {
            g.setTooltipForNextFrame(font, Component.literal(status), mouseX, mouseY);
        }
        MusicVisuals.text(g, "CLIENT ONLY", content + 86, y + h - 25, MusicVisuals.MUTED, contentWidth - 86);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.x() >= content && event.x() <= content + contentWidth
                && event.y() >= progressY - 5 && event.y() <= progressY + 7) {
            TrackState state = NocturneClient.MEDIA.state();
            if (state.canSeek() && state.duration() > 0) {
                NocturneClient.MEDIA.seek((event.x() - content) / contentWidth * state.duration());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
