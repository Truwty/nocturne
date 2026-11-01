package dev.truwty.nocturne.playback;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import com.sun.net.httpserver.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

class SpotifyApiTest {
    @TempDir Path directory;
    private HttpServer server;
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger reply = new AtomicInteger(200);
    private SpotifyPlayback playback;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/token",
                e -> {
                    tokenCalls.incrementAndGet();
                    String body =
                            new String(
                                    e.getRequestBody().readAllBytes(),
                                    java.nio.charset.StandardCharsets.UTF_8);
                    assertTrue(
                            body.contains("grant_type=refresh_token")
                                    || body.contains("grant_type=authorization_code"));
                    respond(
                            e,
                            200,
                            body.contains("grant_type=authorization_code")
                                    ? "{\"access_token\":\"test-access\",\"refresh_token\":\"test-refresh\",\"expires_in\":3600}"
                                    : "{\"access_token\":\"refreshed-test-access\",\"expires_in\":3600}");
                });
        server.createContext(
                "/v1/",
                e -> {
                    requests.add(e.getRequestMethod() + " " + e.getRequestURI());
                    if (reply.get() == 429) e.getResponseHeaders().set("Retry-After", "2");
                    if (e.getRequestURI().getPath().endsWith("/queue"))
                        respond(
                                e,
                                200,
                                "{\"queue\":[{\"name\":\"Next original"
                                    + " fixture\",\"artists\":[{\"name\":\"Nocturne\"}],\"uri\":\"spotify:track:0000000000000000000000\"}]}");
                    else if (e.getRequestMethod().equals("GET"))
                        respond(e, reply.get(), PlaybackTest.sample().toString());
                    else respond(e, 204, "");
                });
        server.start();
    }

    private void create(long expiry) throws Exception {
        var store = new PrivateTokenStore(directory);
        var j = new JsonObject();
        j.addProperty("clientId", "0123456789abcdef0123456789abcdef");
        j.addProperty("access", "test-access");
        j.addProperty("refresh", "test-refresh");
        j.addProperty("expiry", expiry);
        store.save(j.toString());
        String root = "http://127.0.0.1:" + server.getAddress().getPort();
        playback =
                new SpotifyPlayback(store, URI.create(root + "/token"), URI.create(root + "/v1/"));
        playback.enabled(true);
    }

    private static void respond(HttpExchange e, int code, String text) throws java.io.IOException {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        e.sendResponseHeaders(code, code == 204 ? -1 : bytes.length);
        try (var out = e.getResponseBody()) {
            if (code != 204) out.write(bytes);
        }
        e.close();
    }

    private static void until(java.util.function.BooleanSupplier condition) throws Exception {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) fail("Timed out waiting for integration worker");
            Thread.sleep(25);
        }
    }

    @AfterEach
    void stop() {
        if (playback != null) playback.close();
        server.stop(0);
    }

    @Test
    void refreshPreservesRefreshTokenAndPollsActiveDevice() throws Exception {
        create(0);
        until(() -> playback.state() != null);
        assertEquals(1, tokenCalls.get());
        assertTrue(new PrivateTokenStore(directory).load().contains("test-refresh"));
        assertEquals(42, playback.state().volume());
    }

    @Test
    void volumeShuffleRepeatAndQueueUseDocumentedEndpoints() throws Exception {
        create(System.currentTimeMillis() + 3600000);
        until(() -> playback.state() != null);
        playback.command("volume", 500);
        playback.command("shuffle", 1);
        playback.command("repeat", 2);
        playback.refreshQueue();
        until(() ->
                playback.queue().size() == 1
                        && requests.stream().anyMatch(s -> s.contains("repeat?")));
        assertTrue(
                requests.stream()
                        .anyMatch(s -> s.contains("volume_percent=100&device_id=device-one")));
        assertTrue(requests.stream().anyMatch(s -> s.contains("shuffle?state=true")));
        assertTrue(requests.stream().anyMatch(s -> s.contains("repeat?state=context")));
        assertEquals("Next original fixture", playback.queue().getFirst().title());
    }

    @Test
    void forbiddenResponseIsActionableAndNotTreatedAsPlayback() throws Exception {
        reply.set(403);
        create(System.currentTimeMillis() + 3600000);
        until(() -> playback.status().contains("Premium"));
        assertNull(playback.state());
        assertFalse(playback.status().contains("test-access"));
    }

    @Test
    void rateLimitBacksOffRequests() throws Exception {
        reply.set(429);
        create(System.currentTimeMillis() + 3600000);
        until(() -> playback.status().contains("rate limited"));
        int count = requests.size();
        Thread.sleep(700);
        assertEquals(count, requests.size());
    }

    @Test
    void callbackRejectsBadStateBeforeExchangingValidCode() throws Exception {
        String root = "http://127.0.0.1:" + server.getAddress().getPort();
        playback =
                new SpotifyPlayback(
                        new PrivateTokenStore(directory),
                        URI.create(root + "/token"),
                        URI.create(root + "/v1/"));
        var authorize = playback.connect("0123456789abcdef0123456789abcdef");
        var parameters = Pkce.query(authorize.getRawQuery());
        assertEquals("S256", parameters.get("code_challenge_method"));
        assertFalse(parameters.containsKey("client_secret"));
        try (var http = java.net.http.HttpClient.newHttpClient()) {
            var invalid =
                    http.send(
                            java.net.http.HttpRequest.newBuilder(
                                            URI.create(
                                                    SpotifyPlayback.REDIRECT
                                                            + "?state=wrong&code=test"))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(400, invalid.statusCode());
            assertEquals(0, tokenCalls.get());
            var valid =
                    http.send(
                            java.net.http.HttpRequest.newBuilder(
                                            URI.create(
                                                    SpotifyPlayback.REDIRECT
                                                            + "?state="
                                                            + parameters.get("state")
                                                            + "&code=test"))
                                    .GET()
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200, valid.statusCode());
            until(() -> playback.connected());
            assertEquals(1, tokenCalls.get());
        }
    }

    @Test
    void disconnectErasesCredentialFiles() throws Exception {
        create(System.currentTimeMillis() + 3600000);
        until(() -> playback.state() != null);
        playback.disconnect();
        until(() -> playback.status().contains("removed"));
        assertFalse(playback.connected());
        assertNull(playback.state());
        assertFalse(Files.exists(directory.resolve("spotify.bin")));
    }
}
