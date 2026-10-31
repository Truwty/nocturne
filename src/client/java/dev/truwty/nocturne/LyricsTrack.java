package dev.truwty.nocturne;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public record LyricsTrack(String title, String artist, String album, int duration) {
    public static LyricsTrack of(TrackState state) {
        return new LyricsTrack(state.title().strip(), state.artist().strip(), state.album().strip(), (int) Math.round(state.duration()));
    }
    public boolean searchable() { return !title.isBlank() && !artist.isBlank(); }
    public String cacheKey() {
        String signature = title.toLowerCase(Locale.ROOT) + "\n" + artist.toLowerCase(Locale.ROOT) + "\n" + album.toLowerCase(Locale.ROOT) + "\n" + duration;
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
    public String localFileName() { return safe(artist) + " - " + safe(title) + ".lrc"; }
    private static String safe(String text) {
        String value = text.replaceAll("[\\p{Cntrl}<>:\"/\\\\|?*]", "_").strip().replaceAll("[. ]+$", "");
        return value.isBlank() ? "Unknown" : value.substring(0, Math.min(70, value.length()));
    }
}
