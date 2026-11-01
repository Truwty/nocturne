package dev.truwty.nocturne.playback;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import dev.truwty.nocturne.lyrics.SystemMediaSource.Player;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import javax.imageio.ImageIO;

class PlaybackTest {
    @TempDir Path directory;

    private Player player(String id, boolean playing) {
        return new Player(id, id, Set.of("toggle"), playing, "");
    }

    @Test
    void responseSubscriberCancelsBeforeAccumulatingOversizedBody() throws Exception {
        var body = BoundedBody.handler(4).apply(null);
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        body.onSubscribe(
                new java.util.concurrent.Flow.Subscription() {
                    public void request(long count) {}

                    public void cancel() {
                        cancelled.set(true);
                    }
                });
        body.onNext(List.of(java.nio.ByteBuffer.wrap(new byte[5])));
        assertTrue(cancelled.get());
        assertThrows(
                java.util.concurrent.ExecutionException.class,
                () -> body.getBody().toCompletableFuture().get());
    }

    @Test
    void playingWinsOverPriority() {
        assertEquals(
                "Other",
                PlayerSelection.choose(
                                List.of(player("Spotify", false), player("Other", true)),
                                PlayerSelection.DEFAULTS,
                                "",
                                "Spotify")
                        .id());
    }

    @Test
    void priorityUsesCaseInsensitiveSubstring() {
        assertEquals(0, PlayerSelection.rank("com.spotify.desktop", PlayerSelection.DEFAULTS));
    }

    @Test
    void priorityWinsOverStickyPlayer() {
        assertEquals(
                "Spotify",
                PlayerSelection.choose(
                                List.of(player("Spotify", true), player("Other", true)),
                                PlayerSelection.DEFAULTS,
                                "",
                                "Other")
                        .id());
    }

    @Test
    void tiedRankRetainsCurrentPlayer() {
        assertEquals(
                "Z",
                PlayerSelection.choose(
                                List.of(player("A", true), player("Z", true)), List.of(), "", "Z")
                        .id());
    }

    @Test
    void pinWinsEvenWhenPaused() {
        assertEquals(
                "Other",
                PlayerSelection.choose(
                                List.of(player("Spotify", true), player("Other", false)),
                                PlayerSelection.DEFAULTS,
                                "Other",
                                "")
                        .id());
    }

    @Test
    void missingPinWaitsInsteadOfSwitching() {
        assertNull(
                PlayerSelection.choose(
                        List.of(player("Spotify", true)), PlayerSelection.DEFAULTS, "closed", ""));
    }

    @Test
    void missingPlayersProduceNoSelection() {
        assertNull(PlayerSelection.choose(List.of(), List.of(), "", ""));
    }

    @Test
    void prioritiesTrimDeduplicateAndRejectControls() {
        assertEquals(List.of("Spotify"), PlayerSelection.validate(List.of(" Spotify ", "spotify")));
        assertThrows(
                IllegalArgumentException.class, () -> PlayerSelection.validate(List.of("A\nB")));
        assertThrows(
                IllegalArgumentException.class,
                () -> PlayerSelection.validate(Collections.nCopies(33, "A")));
    }

    @Test
    void pkceMatchesRfc7636Vector() {
        assertEquals(
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                new Pkce("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk", "state", 100).challenge());
    }

    @Test
    void stateMustMatchAndNotExpire() {
        var p = Pkce.create(0);
        assertTrue(p.accepts(p.state(), 300000));
        assertFalse(p.accepts(p.state(), 300001));
        assertFalse(p.accepts("wrong", 0));
        assertFalse(p.accepts(null, 0));
    }

    @Test
    void callbackRejectsDuplicateParameters() {
        assertThrows(IllegalArgumentException.class, () -> Pkce.query("state=x&state=y"));
        assertEquals("a+b", Pkce.query("code=a%2Bb").get("code"));
    }

    @Test
    void tokenStorageRoundTripsEncryptedAndRemovesFiles() throws Exception {
        var store = new PrivateTokenStore(directory);
        assertNull(store.load());
        store.save("test-refresh-credential");
        assertEquals("test-refresh-credential", store.load());
        assertFalse(
                new String(
                                Files.readAllBytes(directory.resolve("spotify.bin")),
                                java.nio.charset.StandardCharsets.ISO_8859_1)
                        .contains("test-refresh-credential"));
        store.clear();
        assertNull(store.load());
        assertFalse(Files.exists(directory.resolve("key.bin")));
    }

    @Test
    void corruptTokenCannotBeSilentlyLoaded() throws Exception {
        var store = new PrivateTokenStore(directory);
        store.save("secret");
        byte[] bytes = Files.readAllBytes(directory.resolve("spotify.bin"));
        bytes[bytes.length - 1] ^= 1;
        Files.write(directory.resolve("spotify.bin"), bytes);
        assertThrows(IOException.class, store::load);
    }

    static JsonObject sample() {
        return JsonParser.parseString(
                        """
                        {"is_playing":true,"progress_ms":1000,"repeat_state":"context","shuffle_state":true,
                         "device":{"id":"device-one","name":"Desktop","supports_volume":true,"volume_percent":42},
                         "actions":{"skipping_next":true},"item":{"name":"Original fixture","uri":"spotify:track:0000000000000000000000","duration_ms":120000,
                         "artists":[{"name":"Nocturne"}],"album":{"name":"Tests","images":[{"url":"https://example.invalid/art.png"}]}}}
                        """)
                .getAsJsonObject();
    }

    @Test
    void playbackDecodesArtworkTimingAndCapabilities() {
        var s = SpotifyPlayback.decode(sample());
        assertNotNull(s);
        assertEquals("Original fixture", s.track().title());
        assertEquals("Nocturne", s.track().artist());
        assertEquals("https://example.invalid/art.png", s.track().artUrl());
        assertEquals("device-one", s.track().sessionId());
        assertEquals(42, s.volume());
        assertTrue(s.shuffle());
        assertEquals("context", s.repeat());
        assertTrue(s.capabilities().containsAll(Set.of("queue", "volume", "toggle")));
        assertFalse(s.capabilities().contains("next"));
    }

    @Test
    void restrictedDeviceExposesNoControl() {
        var j = sample();
        j.getAsJsonObject("device").addProperty("is_restricted", true);
        assertTrue(SpotifyPlayback.decode(j).capabilities().isEmpty());
    }

    @Test
    void deviceWithoutVolumeDoesNotAdvertiseIt() {
        var j = sample();
        j.getAsJsonObject("device").addProperty("supports_volume", false);
        assertFalse(SpotifyPlayback.decode(j).capabilities().contains("volume"));
    }

    @Test
    void noTrackOrDeviceMeansNoPlayback() {
        assertNull(SpotifyPlayback.decode(new JsonObject()));
        var j = sample();
        j.getAsJsonObject("device").add("id", JsonNull.INSTANCE);
        assertNull(SpotifyPlayback.decode(j));
    }

    @Test
    void queueUrisAreStrict() {
        assertTrue(SpotifyPlayback.validTrackUri("spotify:track:0123456789ABCDEFGHIJKL"));
        assertFalse(SpotifyPlayback.validTrackUri("https://spotify.com/track/foo"));
        assertFalse(SpotifyPlayback.validTrackUri("spotify:track:xx&device_id=other"));
    }

    @Test
    void artworkRejectsOversizeOrInvalidImages() {
        assertThrows(IOException.class, () -> ArtworkImage.thumbnail(new byte[4194305]));
        assertThrows(IOException.class, () -> ArtworkImage.thumbnail(new byte[] {1, 2, 3}));
    }

    @Test
    void artworkResizesBeforeGpuUpload() throws Exception {
        var image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        var thumb =
                ImageIO.read(new ByteArrayInputStream(ArtworkImage.thumbnail(bytes.toByteArray())));
        assertEquals(128, thumb.getWidth());
        assertEquals(128, thumb.getHeight());
    }
}
