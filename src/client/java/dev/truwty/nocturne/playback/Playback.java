package dev.truwty.nocturne.playback;

import dev.truwty.nocturne.Studio;
import dev.truwty.nocturne.lyrics.*;

public final class Playback {
    private static SpotifyPlayback spotify;

    private Playback() {}

    public static synchronized SpotifyPlayback spotify() {
        if (spotify == null)
            spotify =
                    new SpotifyPlayback(
                            new PrivateTokenStore(Studio.directory().resolve("private")));
        return spotify;
    }

    public static void initialize() {
        LyricsService.INSTANCE.system.priorities(PlaybackPreferences.priorities());
        spotify().enabled(PlaybackPreferences.cloud());
    }

    public static NowPlaying sample(NowPlaying desktop) {
        if (!PlaybackPreferences.cloud()) return desktop;
        var state = spotify().state();
        return state == null ? null : state.track();
    }

    public static NowPlaying current() {
        return LyricsService.INSTANCE.demoPlaying()
                ? LyricsService.INSTANCE.now()
                : sample(LyricsService.INSTANCE.system.latest());
    }

    public static boolean can(String action) {
        if (LyricsService.INSTANCE.demoPlaying()) return false;
        if (!PlaybackPreferences.cloud()) return LyricsService.INSTANCE.system.can(action);
        var state = spotify().state();
        return state != null && state.capabilities().contains(action);
    }

    public static void command(String action, long value) {
        if (!can(action)) return;
        if (PlaybackPreferences.cloud()) spotify().command(action, value);
        else LyricsService.INSTANCE.system.command(action, value);
    }

    public static String status() {
        return PlaybackPreferences.cloud()
                ? spotify().status()
                : LyricsService.INSTANCE.system.commandStatus();
    }

    public static int volume() {
        var s = spotify().state();
        return PlaybackPreferences.cloud() && s != null ? s.volume() : -1;
    }

    public static boolean shuffle() {
        var s = spotify().state();
        return PlaybackPreferences.cloud()
                ? s != null && s.shuffle()
                : LyricsService.INSTANCE.system.shuffle();
    }

    public static String repeat() {
        var s = spotify().state();
        return PlaybackPreferences.cloud()
                ? s == null ? "off" : s.repeat()
                : LyricsService.INSTANCE.system.repeat();
    }

    public static void route(boolean cloud) throws java.io.IOException {
        PlaybackPreferences.cloud(cloud);
        spotify().enabled(cloud);
        LyricsService.INSTANCE.resynchronize();
    }

    public static void close() {
        if (spotify != null) spotify.close();
    }
}
