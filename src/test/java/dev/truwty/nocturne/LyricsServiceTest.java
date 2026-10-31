package dev.truwty.nocturne;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LyricsServiceTest {
    @TempDir Path directory;
    private static final String BODY = "{\"syncedLyrics\":\"[00:01]Window light\\n[00:04]City quiet\",\"plainLyrics\":null,\"instrumental\":false}";
    private static TrackState track(String title) {
        return new TrackState(true, title, "Fixture", "Local test", "fixture.exe", List.of("fixture.exe"), "", true, 0, 120,
                true, true, true, true, "", "", System.nanoTime());
    }
    private static LyricsService.Snapshot await(LyricsService service) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (service.snapshot().status() == LyricsService.Status.LOADING && System.nanoTime() < deadline) Thread.sleep(5);
        assertNotEquals(LyricsService.Status.LOADING, service.snapshot().status());
        return service.snapshot();
    }
    @Test void offlineModeDoesNotContactProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (var service = new LyricsService(directory, uri -> { calls.incrementAndGet(); return new LyricsService.Response(200, BODY); })) {
            service.update(track("Offline"), false);
            assertEquals(LyricsService.Status.OFFLINE, await(service).status());
            assertEquals(0, calls.get());
        }
    }
    @Test void localOverrideWorksWithoutNetworkAndReloads() throws Exception {
        var state = track("Local");
        Path file = directory.resolve(LyricsTrack.of(state).localFileName());
        Files.writeString(file, "[00:01]Local first");
        try (var service = new LyricsService(directory, uri -> { fail("Unexpected network call"); return null; })) {
            service.update(state, false);
            assertEquals("Local first", await(service).lyrics().lines().getFirst().text());
            Files.writeString(file, "[00:01]Local changed");
            service.refresh(state, false);
            assertEquals("Local changed", await(service).lyrics().lines().getFirst().text());
        }
    }
    @Test void cachesSuccessfulLookupForOfflineRestart() throws Exception {
        var state = track("Cached");
        try (var service = new LyricsService(directory, uri -> new LyricsService.Response(200, BODY))) {
            service.update(state, true); assertEquals(LyricsService.Status.READY, await(service).status());
        }
        try (var service = new LyricsService(directory, uri -> { fail("Unexpected network call"); return null; })) {
            service.update(state, false); assertEquals(LyricsService.Status.READY, await(service).status());
        }
    }
    @Test void staleResponseCannotReplaceTheNewTrack() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var service = new LyricsService(directory, uri -> {
            if (uri.getQuery().contains("Old")) { started.countDown(); assertTrue(release.await(3, TimeUnit.SECONDS)); }
            return new LyricsService.Response(200, BODY);
        })) {
            service.update(track("Old"), true); assertTrue(started.await(2, TimeUnit.SECONDS));
            service.update(track("New"), true); release.countDown();
            assertEquals(LyricsTrack.of(track("New")).cacheKey(), await(service).key());
        } finally { release.countDown(); }
    }
    @Test void busyProviderIsNotHammeredOnTrackChange() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (var service = new LyricsService(directory, uri -> { calls.incrementAndGet(); return new LyricsService.Response(429, ""); })) {
            service.update(track("One"), true); assertEquals(LyricsService.Status.ERROR, await(service).status());
            service.update(track("Two"), true); assertEquals(LyricsService.Status.ERROR, await(service).status());
            assertEquals(1, calls.get());
        }
    }
    @Test void malformedCacheCanBeReplacedByFreshResponse() throws Exception {
        var state = track("Repair"); Path cache = directory.resolve("cache").resolve(LyricsTrack.of(state).cacheKey() + ".json");
        Files.createDirectories(cache.getParent()); Files.writeString(cache, "broken");
        try (var service = new LyricsService(directory, uri -> new LyricsService.Response(200, BODY))) {
            service.update(state, true); assertEquals(LyricsService.Status.READY, await(service).status());
        }
    }
    @Test void requestEncodesUnicodeAndDoesNotSendInvalidDuration() {
        String uri = LyricsService.uri(new LyricsTrack("夜 & light", "A/B", "", 8000)).toString();
        assertTrue(uri.startsWith("https://lrclib.net/api/get?"));
        assertTrue(uri.contains("%26")); assertTrue(uri.contains("%2F")); assertFalse(uri.contains("duration="));
    }
    @Test void instrumentalIsNotReportedAsMissingLyrics() {
        var lyrics = LyricsService.decode("{\"syncedLyrics\":null,\"plainLyrics\":null,\"instrumental\":true}", "test");
        assertEquals(LyricsService.Status.INSTRUMENTAL, LyricsService.result("test", lyrics).status());
    }
    @Test void fallbackRejectsDifferentArtistAndLiveRecording() {
        var track = new LyricsTrack("Window Light", "Fixture", "Test", 120);
        String candidates = "[{\"trackName\":\"Window Light\",\"artistName\":\"Other\",\"duration\":120},"
                + "{\"trackName\":\"Window Light\",\"artistName\":\"Fixture\",\"duration\":180},"
                + "{\"trackName\":\"Window Light\",\"artistName\":\"Fixture\",\"duration\":121,\"syncedLyrics\":\"[00:01]Fixture\"}]";
        var match = LyricsService.chooseCandidate(candidates, track);
        assertNotNull(match); assertEquals(121, match.get("duration").getAsInt());
    }
    @Test void fallbackDoesNotUseUnrelatedSong() {
        assertNull(LyricsService.chooseCandidate("[{\"trackName\":\"Different\",\"artistName\":\"Fixture\",\"duration\":120}]",
                new LyricsTrack("Window Light", "Fixture", "", 120)));
    }
}
