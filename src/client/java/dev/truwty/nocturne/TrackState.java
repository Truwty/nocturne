package dev.truwty.nocturne;

import java.util.List;

public record TrackState(boolean connected, String title, String artist, String album, String source,
                         List<String> sources, String selected, boolean playing, double position,
                         double duration, boolean canToggle, boolean canNext, boolean canPrevious,
                         boolean canSeek, String art, String error, long receivedAt) {
    public static TrackState idle(String message) {
        return new TrackState(false, "Your soundtrack starts here", "Open your music app and press play",
                "", "", List.of(), "", false, 0, 0, false, false, false, false, "", message, System.nanoTime());
    }

    public double currentPosition() {
        double elapsed = playing ? (System.nanoTime() - receivedAt) / 1_000_000_000.0 : 0;
        return Math.clamp(position + elapsed, 0, Math.max(0, duration));
    }

    public static String time(double seconds) {
        int whole = (int) Math.max(0, seconds);
        return "%d:%02d".formatted(whole / 60, whole % 60);
    }

    public static String friendlySource(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("spotify")) return "Spotify";
        if (lower.contains("cider")) return "Cider";
        if (lower.contains("apple")) return "Apple Music";
        if (lower.contains("tidal")) return "TIDAL";
        if (lower.contains("foobar")) return "foobar2000";
        if (lower.contains("youtube")) return "YouTube Music";
        if (lower.contains("chrome")) return "Chrome media";
        if (lower.contains("msedge")) return "Edge media";
        if (lower.contains("firefox")) return "Firefox media";
        if (value.isBlank()) return "Desktop audio";
        return value.length() > 32 ? value.substring(0, 29) + "..." : value;
    }
}
