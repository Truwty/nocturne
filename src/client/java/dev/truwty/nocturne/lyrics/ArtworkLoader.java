package dev.truwty.nocturne.lyrics;

import com.google.gson.*;

import dev.truwty.nocturne.performance.ArtworkCache;
import dev.truwty.nocturne.performance.PersistentArtworkCache;
import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.Studio;
import dev.truwty.nocturne.playback.*;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.*;

import javax.imageio.ImageIO;

/** One current artwork operation; obsolete catalog/image HTTP exchanges are cancelled. */
public final class ArtworkLoader {
    private final HttpClient http;
    private final Executor decode;
    private final URI catalog;
    private final Duration timeout;
    private final ArtworkCache cache =
            new ArtworkCache(24, 4 * 1024 * 1024, TimeUnit.HOURS.toMillis(24));
    private CompletableFuture<?> network, result;
    private long generation;
    private int cancellations;
    private final PersistentArtworkCache disk;

    public ArtworkLoader(HttpClient http, Executor decode) {
        this(http, decode, URI.create("https://itunes.apple.com/search"), Duration.ofSeconds(8),
                new PersistentArtworkCache(Studio.directory().resolve("cache/artwork")));
    }

    ArtworkLoader(HttpClient http, Executor decode, URI catalog) {
        this(http, decode, catalog, Duration.ofSeconds(8));
    }

    ArtworkLoader(HttpClient http, Executor decode, URI catalog, Duration timeout) {
        this(http, decode, catalog, timeout, null);
    }

    ArtworkLoader(HttpClient http, Executor decode, URI catalog, Duration timeout, PersistentArtworkCache disk) {
        this.http = http;
        this.decode = decode;
        this.catalog = catalog;
        if (timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("Invalid artwork timeout");
        this.timeout = timeout;
        this.disk = disk;
    }

    public synchronized void cancel() {
        generation++;
        if (result != null) result.cancel(false);
        if (network != null && !network.isDone() && network.cancel(true)) cancellations++;
        network = result = null;
    }

    public synchronized CompletableFuture<ArtworkCache.Entry> load(NowPlaying song) {
        cancel();
        long id = generation;
        String key = song.key() + "|" + song.album() + "|" + song.artUrl();
        var hit = cache.get(key, System.currentTimeMillis());
        if (hit != null) return CompletableFuture.completedFuture(hit);
        var pending = CompletableFuture.supplyAsync(() -> {
            check(id);
            if (disk == null) return null;
            try { return disk.read(key); }
            catch (java.io.IOException ex) {
                NocturneClient.LOG.debug("Artwork disk cache read failed", ex);
                return null;
            }
        }, decode).thenCompose(stored -> {
            check(id);
            return stored == null ? download(id, song) : CompletableFuture.completedFuture(stored);
        }).thenApplyAsync(entry -> {
            synchronized (this) {
                check(id);
                if (entry != null) {
                    cache.put(key, entry);
                    if (disk != null) try { disk.write(key, entry); }
                    catch (java.io.IOException ex) { NocturneClient.LOG.debug("Artwork disk cache write failed", ex); }
                }
                return entry;
            }
        }, decode);
        result = pending;
        return pending;
    }

    private CompletableFuture<ArtworkCache.Entry> download(long id, NowPlaying song) {
        String given = song.artUrl();
        CompletableFuture<String> url;
        if (given != null && given.startsWith("https://"))
            url = CompletableFuture.completedFuture(given);
        else {
            String term =
                    URLEncoder.encode(song.artist() + " " + song.title(), StandardCharsets.UTF_8);
            url =
                    request(
                                    id,
                                    URI.create(
                                            catalog
                                                    + "?media=music&entity=song&limit=5&term="
                                                    + term),
                                    262144)
                            .thenApply(bytes ->
                                    match(
                                            JsonParser.parseString(
                                                    new String(bytes, StandardCharsets.UTF_8)),
                                            song));
        }
        return
                url.thenCompose(
                                value -> {
                                    if (value == null)
                                        return CompletableFuture.completedFuture(null);
                                    return request(id, URI.create(value), 4194304);
                                })
                        .thenApplyAsync(
                                bytes -> {
                                    check(id);
                                    if (bytes == null) return null;
                                    try {
                                        byte[] png = ArtworkImage.thumbnail(bytes);
                                        int color =
                                                LyricsService.mainColor(
                                                        ImageIO.read(
                                                                new java.io.ByteArrayInputStream(
                                                                        png)));
                                        return new ArtworkCache.Entry(
                                                png, color, System.currentTimeMillis());
                                    } catch (java.io.IOException ex) {
                                        throw new CompletionException(ex);
                                    }
                                },
                                decode);
    }

    public synchronized void clearMemory() { cancel(); cache.clear(); }

    private synchronized void check(long id) {
        if (id != generation) throw new CancellationException("Obsolete artwork request");
    }

    private synchronized CompletableFuture<byte[]> request(long id, URI uri, int maximum) {
        check(id);
        // Production URLs must be HTTPS; loopback HTTP is used only by the isolated test catalog.
        boolean fixture =
                catalog.getScheme().equals("http")
                        && "127.0.0.1".equals(catalog.getHost())
                        && "127.0.0.1".equals(uri.getHost());
        if (!uri.getScheme().equals("https") && !fixture)
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Artwork requires HTTPS"));
        var request =
                HttpRequest.newBuilder(uri)
                        .timeout(timeout)
                        .header("User-Agent", "Nocturne Lyrics (Minecraft client mod)")
                        .GET()
                        .build();
        var response = http.sendAsync(request, BoundedBody.handler(maximum));
        network = response;
        var deadline =
                response.thenApply(java.util.function.Function.identity())
                        .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
        return deadline.whenComplete(
                        (r, error) -> {
                            if (error instanceof TimeoutException) response.cancel(true);
                        })
                .thenApply(r -> {
                    if (r.statusCode() != 200)
                        throw new CompletionException(
                                new java.io.IOException(
                                        "Artwork request rejected (HTTP " + r.statusCode() + ")"));
                    return r.body();
                });
    }

    static String match(JsonElement document, NowPlaying song) {
        if (!document.isJsonObject() || !document.getAsJsonObject().has("results")) return null;
        String title = normalize(song.title()), artist = normalize(song.artist().split(",")[0]);
        if (title.isBlank() || artist.isBlank()) return null;
        for (var value : document.getAsJsonObject().getAsJsonArray("results")) {
            var row = value.getAsJsonObject();
            String name = normalize(str(row, "trackName")), who = normalize(str(row, "artistName"));
            if (!name.isBlank()
                    && !who.isBlank()
                    && (name.contains(title) || title.contains(name))
                    && (who.contains(artist) || artist.contains(who))) {
                String art = str(row, "artworkUrl100").replace("100x100", "300x300");
                if (art.startsWith("https://") || art.startsWith("http://127.0.0.1:")) return art;
            }
        }
        return null;
    }

    private static String str(JsonObject row, String key) {
        return row.has(key) && row.get(key).isJsonPrimitive() ? row.get(key).getAsString() : "";
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    public synchronized int cancelledRequests() {
        return cancellations;
    }

    public int cached() {
        return cache.size();
    }

    public int cachedBytes() {
        return cache.bytes();
    }
}
