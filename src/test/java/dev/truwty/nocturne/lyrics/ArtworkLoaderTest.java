package dev.truwty.nocturne.lyrics;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.*;

import org.junit.jupiter.api.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import javax.imageio.ImageIO;

class ArtworkLoaderTest {
    private HttpServer server;
    private ExecutorService workers;
    private HttpClient http;
    private ArtworkLoader loader;
    private final CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
    private final AtomicInteger requests = new AtomicInteger(),
            obsoleteImages = new AtomicInteger(),
            closedWrites = new AtomicInteger();
    private volatile boolean holdImage, holdBody;

    @BeforeEach
    void start() throws Exception {
        workers = Executors.newVirtualThreadPerTaskExecutor();
        http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(workers);
        var image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        byte[] png = output.toByteArray();
        server.createContext(
                "/",
                e -> {
                    try {
                        requests.incrementAndGet();
                        String path = e.getRequestURI().getPath();
                        boolean old = e.getRequestURI().toString().contains("Old");
                        byte[] body;
                        if (path.equals("/search")) {
                            if (old && !holdImage) {
                                started.countDown();
                                release.await(5, TimeUnit.SECONDS);
                            }
                            String name = old ? "Old" : "New";
                            body =
                                    ("{\"results\":[{\"trackName\":\""
                                                    + name
                                                    + "\",\"artistName\":\"Nocturne\",\"artworkUrl100\":\"http://127.0.0.1:"
                                                    + server.getAddress().getPort()
                                                    + "/"
                                                    + name
                                                    + ".png\"}]}")
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        } else {
                            if (path.contains("Old")) {
                                obsoleteImages.incrementAndGet();
                                if (holdImage) {
                                    started.countDown();
                                    release.await(5, TimeUnit.SECONDS);
                                }
                            }
                            body = png;
                        }
                        e.sendResponseHeaders(200, body.length);
                        if (holdBody) {
                            started.countDown();
                            release.await(5, TimeUnit.SECONDS);
                        }
                        try (var out = e.getResponseBody()) {
                            out.write(body);
                        }
                    } catch (IOException ex) {
                        closedWrites.incrementAndGet();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        e.close();
                    }
                });
        server.start();
        loader =
                new ArtworkLoader(
                        http,
                        workers,
                        URI.create(
                                "http://127.0.0.1:" + server.getAddress().getPort() + "/search"));
    }

    private NowPlaying song(String title) {
        return new NowPlaying(
                title, "Nocturne", "Original test", 10000, 0, true, System.nanoTime(), "test", "");
    }

    @AfterEach
    void stop() {
        release.countDown();
        loader.cancel();
        server.stop(0);
        workers.shutdownNow();
        http.close();
    }

    @Test
    void supersedingTrackCancelsCatalogAndPreventsObsoleteArtwork() throws Exception {
        var old = loader.load(song("Old"));
        assertTrue(started.await(3, TimeUnit.SECONDS));
        var fresh = loader.load(song("New")).get(5, TimeUnit.SECONDS);
        assertTrue(old.isCancelled());
        assertTrue(loader.cancelledRequests() > 0);
        assertNotNull(fresh);
        release.countDown();
        assertEquals(0, obsoleteImages.get());
        assertEquals(1, loader.cached());
    }

    @Test
    void supersedingTrackCancelsImageDownload() throws Exception {
        holdImage = true;
        var old = loader.load(song("Old"));
        assertTrue(started.await(3, TimeUnit.SECONDS));
        assertNotNull(loader.load(song("New")).get(5, TimeUnit.SECONDS));
        assertTrue(old.isCancelled());
        assertTrue(loader.cancelledRequests() > 0);
    }

    @Test
    void deadlineIncludesBodyAfterHeadersArrive() throws Exception {
        holdBody = true;
        loader =
                new ArtworkLoader(
                        http,
                        workers,
                        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/search"),
                        java.time.Duration.ofMillis(500));
        var request = loader.load(song("New"));
        assertTrue(started.await(3, TimeUnit.SECONDS));
        var failure =
                assertThrows(ExecutionException.class, () -> request.get(3, TimeUnit.SECONDS));
        assertTrue(
                failure.getCause() instanceof TimeoutException
                        || failure.getCause() instanceof java.net.http.HttpTimeoutException);
        assertEquals(0, loader.cached());
    }

    @Test
    void repeatedTrackReusesBoundedThumbnailWithoutNetwork() throws Exception {
        var first = loader.load(song("New")).get(5, TimeUnit.SECONDS);
        int count = requests.get();
        var again = loader.load(song("New")).get(1, TimeUnit.SECONDS);
        assertEquals(count, requests.get());
        assertArrayEquals(first.png(), again.png());
        assertEquals(1, loader.cached());
        assertTrue(loader.cachedBytes() <= 4 * 1024 * 1024);
    }

    @Test
    void missingArtistCannotMatchAnUnrelatedCover() {
        var json =
                com.google.gson.JsonParser.parseString(
                        "{\"results\":[{\"trackName\":\"Old\",\"artistName\":\"Someone"
                                + " else\",\"artworkUrl100\":\"https://example.invalid/a.png\"}]}");
        assertNull(ArtworkLoader.match(json, song("Old")));
    }
}
