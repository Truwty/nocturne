package dev.truwty.nocturne.gui;

import dev.truwty.nocturne.*;
import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.lyrics.LyricsService;
import dev.truwty.nocturne.playback.*;
import java.util.*;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;

public final class PlaybackScreen extends StudioPanel {
    private int page, queuePage;
    private String clientId = PlaybackPreferences.clientId(),
            priorities = String.join(", ", PlaybackPreferences.priorities()),
            uri = "";
    private final Map<AbstractWidget, String> controls = new HashMap<>();
    private final List<Button> rows = new ArrayList<>();
    private String queueSignature = "";

    public PlaybackScreen(Screen parent) {
        this(parent, 0);
    }

    public PlaybackScreen(Screen parent, int page) {
        super(parent, "Playback / Integrations");
        this.page = page;
    }

    @Override
    protected int contentHeight() {
        return switch (page) {
            case 1 -> 400;
            case 2 -> 325;
            case 3 -> 360;
            default -> 310;
        };
    }

    private Button control(int xx, int yy, int ww, String label, String action, long value) {
        var b =
                button(
                        xx,
                        yy,
                        ww,
                        label,
                        "Requires " + action + " support from the selected player",
                        () -> Playback.command(action, controlValue(action, value)));

        controls.put(b, action);
        b.active = Playback.can(action);
        return b;
    }

    private static long controlValue(String action, long value) {
        return switch (action) {
            case "shuffle" -> Playback.shuffle() ? 0 : 1;
            case "repeat" ->
                    (List.of("off", "track", "context").indexOf(Playback.repeat()) + 1) % 3;
            default -> value;
        };
    }

    @Override
    protected void build() {
        controls.clear();
        rows.clear();
        int tab = (w - 44) / 4;
        String[] names = {"Controls", "Players", "Account", "Queue"};
        for (int i = 0; i < 4; i++) {
            int target = i;
            button(
                    x + 16 + i * (tab + 4),
                    body,
                    tab,
                    (page == i ? "• " : "") + names[i],
                    "Open " + names[i],
                    () -> {
                        page = target;
                        scroll = 0;
                        rebuildWidgets();
                    });
        }
        int top = body + 34;
        switch (page) {
            case 0 -> {
                int third = (w - 40) / 3;
                control(x + 16, top, third, "Previous", "previous", 0);
                control(x + 20 + third, top, third, "Play / pause", "toggle", 0);
                control(x + 24 + third * 2, top, third, "Next", "next", 0);
                control(
                        x + 16,
                        top + 26,
                        (w - 36) / 2,
                        "Shuffle: " + (Playback.shuffle() ? "on" : "off"),
                        "shuffle",
                        Playback.shuffle() ? 0 : 1);
                control(
                        x + 20 + (w - 36) / 2,
                        top + 26,
                        (w - 36) / 2,
                        "Repeat: " + Playback.repeat(),
                        "repeat",
                        (List.of("off", "track", "context").indexOf(Playback.repeat()) + 1) % 3);
                button(
                                        x + 16,
                                        top + 54,
                                        (w - 36) / 2,
                                        "Volume −5%",
                                        "Adjust selected player volume",
                                        () ->
                                                Playback.command(
                                                        "volume",
                                                        Math.max(0, Playback.volume() - 5)))
                                .active =
                        Playback.can("volume");
                button(
                                        x + 20 + (w - 36) / 2,
                                        top + 54,
                                        (w - 36) / 2,
                                        "Volume +5%",
                                        "Adjust selected player volume",
                                        () ->
                                                Playback.command(
                                                        "volume",
                                                        Math.min(100, Playback.volume() + 5)))
                                .active =
                        Playback.can("volume");
                button(
                        x + 16,
                        top + 112,
                        w - 32,
                        "Configure playback shortcuts…",
                        "Minecraft Controls → Nocturne; shortcuts start unbound",
                        () ->
                                ClientAccess.setScreen(
                                        minecraft,
                    new net.minecraft.client.gui.screens.options.controls
                                                .KeyBindsScreen(this, minecraft.options)));
                button(
                        x + 16,
                        top + 138,
                        (w - 36) / 2,
                        "Artwork: "
                                + (NocturneClient.SETTINGS.artworkDisplay.on()
                                        ? "shown"
                                        : "hidden"),
                        "Show album art in the player and HUD",
                        () -> {
                            var s = NocturneClient.SETTINGS;
                            s.artworkDisplay.set(!s.artworkDisplay.on());
                            s.save();
                            rebuildWidgets();
                        });
                button(
                        x + 20 + (w - 36) / 2,
                        top + 138,
                        (w - 36) / 2,
                        "Preview artwork theme",
                        "Preview colors from the current album, then Apply to save",
                        () -> {
                            int color = LyricsService.INSTANCE.albumColor();
                            if (color == 0)
                                throw new IllegalStateException("Wait for album artwork to load");
                            var theme = Studio.snapshot("themes");
                            theme.addProperty("UI accent", String.format("#%08X", color));
                            theme.addProperty(
                                    "UI background",
                                    String.format(
                                            "#FF%02X%02X%02X",
                                            ((color >> 16) & 255) / 10,
                                            ((color >> 8) & 255) / 10,
                                            (color & 255) / 10));
                            theme.addProperty("UI text", "#FFF4F1F8");
                            ClientAccess.setScreen(minecraft, new ThemeEditorScreen(this, theme));
                        });
            }
            case 1 -> {
                button(
                        x + 16,
                        top,
                        w - 32,
                        "Use automatic desktop detection",
                        "Playing first; configured priority; current player on ties; alphabetical"
                                + " fallback",
                        () -> {
                            Playback.route(false);
                            Studio.library().player("");
                            LyricsService.INSTANCE.system.select("");
                            rebuildWidgets();
                        });
                var edit =
                        field(
                                x + 16,
                                top + 52,
                                w - 32,
                                "Priority names, comma separated",
                                priorities);
                edit.setResponder(v -> priorities = v);
                button(
                        x + 16,
                        top + 78,
                        (w - 36) / 2,
                        "Save priority order",
                        "First name has highest priority; matches app IDs ignoring case",
                        () -> {
                            PlaybackPreferences.priorities(
                                    Arrays.stream(priorities.split(",", -1))
                                            .map(String::strip)
                                            .filter(v -> !v.isEmpty())
                                            .toList());
                            LyricsService.INSTANCE.system.priorities(
                                    PlaybackPreferences.priorities());
                            notice = "Player priorities saved";
                        });
                button(
                        x + 20 + (w - 36) / 2,
                        top + 78,
                        (w - 36) / 2,
                        "Reset priorities",
                        "Restore Spotify, Cider, YouTube Music, TIDAL, foobar",
                        () -> {
                            PlaybackPreferences.priorities(PlayerSelection.DEFAULTS);
                            priorities = String.join(", ", PlayerSelection.DEFAULTS);
                            LyricsService.INSTANCE.system.priorities(PlayerSelection.DEFAULTS);
                            rebuildWidgets();
                        });
                int yy = top + 126;
                for (var p : LyricsService.INSTANCE.system.players().stream().limit(8).toList()) {
                    button(
                            x + 16,
                            yy,
                            w - 32,
                            "Pin " + font.plainSubstrByWidth(p.label(), w - 70),
                            p.diagnostic(),
                            () -> {
                                Playback.route(false);
                                Studio.library().player(p.id());
                                LyricsService.INSTANCE.system.select(p.id());
                                notice = "Pinned player; automatic switching paused";
                            });
                    yy += 24;
                }
            }
            case 2 -> {
                var edit =
                        field(
                                x + 16,
                                top + 42,
                                w - 32,
                                "Public Spotify application Client ID",
                                clientId);
                edit.setMaxLength(80);
                edit.setResponder(v -> clientId = v);
                button(
                        x + 16,
                        top + 68,
                        (w - 36) / 2,
                        "Connect Spotify…",
                        "Public client PKCE. No client secret. Browser authorization is optional.",
                        () -> {
                            PlaybackPreferences.clientId(clientId.strip());
                            var url = Playback.spotify().connect(clientId.strip());
                            ClientAccess.openUri(url);
                        });
                button(
                        x + 20 + (w - 36) / 2,
                        top + 68,
                        (w - 36) / 2,
                        "Cancel browser login",
                        "Stop the loopback authorization listener",
                        () -> {
                            Playback.spotify().cancelLogin();
                            notice = "Authorization cancelled";
                        });
                button(
                        x + 16,
                        top + 96,
                        (w - 36) / 2,
                        "Use Spotify account",
                        "Opt in to account playback; requires a connected active device",
                        () -> {
                            if (!Playback.spotify().connected())
                                throw new IllegalStateException("Connect the account first");
                            Playback.route(true);
                            rebuildWidgets();
                        });
                button(
                        x + 20 + (w - 36) / 2,
                        top + 96,
                        (w - 36) / 2,
                        "Use desktop detection",
                        "Return to automatic detection without disconnecting",
                        () -> {
                            Playback.route(false);
                            rebuildWidgets();
                        });
                button(
                        x + 16,
                        top + 124,
                        w - 32,
                        "Disconnect & remove local tokens",
                        "Delete saved account credentials on this computer",
                        () -> {
                            Playback.route(false);
                            Playback.spotify().disconnect();
                            rebuildWidgets();
                        });
                button(
                        x + 16,
                        top + 152,
                        w - 32,
                        "Open Spotify developer dashboard",
                        "Create your own public app and register the exact redirect below",
                        () ->
                                ClientAccess.openUri(
                                                java.net.URI.create(
                                                        "https://developer.spotify.com/dashboard")));
            }
            case 3 -> {
                control(x + 16, top, (w - 36) / 2, "Skip current track", "next", 0);
                button(
                                        x + 20 + (w - 36) / 2,
                                        top,
                                        (w - 36) / 2,
                                        "Refresh queue",
                                        "Spotify account route required; desktop sessions do not"
                                                + " expose queues",
                                        () -> Playback.spotify().refreshQueue())
                                .active =
                        Playback.can("queue");
                var edit =
                        field(
                                x + 16,
                                top + 30,
                                w - 108,
                                "spotify:track:… or spotify:episode:…",
                                uri);
                edit.setResponder(v -> uri = v);
                button(
                                        x + w - 88,
                                        top + 30,
                                        72,
                                        "Add",
                                        "Append a Spotify track or episode URI",
                                        () -> Playback.spotify().addQueue(uri.strip()))
                                .active =
                        Playback.can("queue");
                var items = Playback.spotify().queue();
                queueSignature = items.toString();
                int start = queuePage * 5;
                for (int i = start; i < Math.min(start + 5, items.size()); i++) {
                    var item = items.get(i);
                    rows.add(
                            button(
                                    x + 16,
                                    top + 80 + (i - start) * 24,
                                    w - 32,
                                    (i + 1)
                                            + ". "
                                            + font.plainSubstrByWidth(
                                                    item.title() + " · " + item.artist(), w - 65),
                                    "Read-only queue entry · Spotify does not expose queue"
                                            + " reordering",
                                    () -> {}));
                }
                button(
                        x + 16,
                        top + 210,
                        (w - 36) / 2,
                        "Previous page",
                        "Previous five queue entries",
                        () -> {
                            queuePage = Math.max(0, queuePage - 1);
                            rebuildWidgets();
                        });
                button(
                        x + 20 + (w - 36) / 2,
                        top + 210,
                        (w - 36) / 2,
                        "Next page",
                        "Next five queue entries",
                        () -> {
                            if ((queuePage + 1) * 5 < items.size()) queuePage++;
                            rebuildWidgets();
                        });
            }
        }
        for (var child : children())
            if (child instanceof Button b) {
                String label = b.getMessage().getString();
                if (label.startsWith("Volume ")) controls.put(b, "volume");
                if (label.equals("Refresh queue") || label.equals("Add")) controls.put(b, "queue");
            }
    }

    @Override
    public void tick() {
        super.tick();
        for (var e : controls.entrySet()) {
            e.getKey().active = Playback.can(e.getValue());
            if (e.getValue().equals("shuffle"))
                e.getKey()
                        .setMessage(
                                net.minecraft.network.chat.Component.literal(
                                        "Shuffle: " + (Playback.shuffle() ? "on" : "off")));
            if (e.getValue().equals("repeat"))
                e.getKey()
                        .setMessage(
                                net.minecraft.network.chat.Component.literal(
                                        "Repeat: " + Playback.repeat()));
        }
        if (page == 3 && !queueSignature.equals(Playback.spotify().queue().toString()))
            rebuildWidgets();
    }

    @Override
    protected void content(GuiGraphicsExtractor g, int mx, int my, float delta) {
        int top = body + 34;
        int color = NocturneClient.SETTINGS.uiText.argb();
        switch (page) {
            case 0 -> {
                text(
                        g,
                        "Volume: "
                                + (Playback.volume() < 0
                                        ? "not exposed by this player"
                                        : Playback.volume() + "%"),
                        top + 84,
                        color);
                text(g, "Shortcuts act only during gameplay, with menus closed.", top + 180, color);
                text(
                        g,
                        "Route: "
                                + (PlaybackPreferences.cloud()
                                        ? "Spotify account"
                                        : "Desktop detection"),
                        top + 198,
                        color);
                text(g, Playback.status(), top + 216, color);
                AlbumArtwork.draw(g, x + w - 60, top + 180, 36, 1);
            }
            case 1 -> {
                text(g, "Playing → priority → current player on ties → app ID.", top + 28, color);
                text(g, "A pin wins; if that app closes, playback waits for it.", top + 108, color);
            }
            case 2 -> {
                text(g, Playback.spotify().status(), top, color);
                text(
                        g,
                        "Desktop detection works without a Spotify account connection.",
                        top + 18,
                        color);
                text(g, "Register redirect: " + SpotifyPlayback.REDIRECT, top + 184, color);
                text(
                        g,
                        "Spotify controls may require Premium and app allowlist access.",
                        top + 202,
                        color);
                text(
                        g,
                        "Tokens stay in private local storage, outside preset exports.",
                        top + 220,
                        color);
            }
            case 3 -> {
                text(
                        g,
                        Playback.can("queue")
                                ? Playback.spotify().queueStatus()
                                : "Queue requires Spotify account playback and an active device.",
                        top + 58,
                        color);
            }
        }
    }
}
