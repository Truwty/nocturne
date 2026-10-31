package dev.truwty.nocturne;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class LyricsService implements AutoCloseable {
    public enum Status { IDLE, LOADING, READY, UNSYNCED, INSTRUMENTAL, MISSING, OFFLINE, ERROR }
    public record Snapshot(String key, Status status, Lyrics lyrics, String message) {
        public static Snapshot idle() { return new Snapshot("", Status.IDLE, empty(), "Play a track to find lyrics"); }
    }
    public record Response(int status, String body) {}
    @FunctionalInterface public interface Transport { Response fetch(URI uri) throws IOException, InterruptedException; }
    private final Path directory;
    private final Transport transport;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(Thread.ofPlatform().daemon().name("nocturne-lyrics").factory());
    private final AtomicLong generation = new AtomicLong();
    private final Map<String, Lyrics> memory = new LinkedHashMap<>(32, 0.75f, true);
    private volatile Snapshot snapshot = Snapshot.idle();
    private volatile boolean closed;
    private String requested = "";
    private long retryAfter;
    private java.util.concurrent.Future<?> pending;

    public LyricsService(Path directory) { this(directory, httpTransport()); }
    LyricsService(Path directory, Transport transport) { this.directory = directory; this.transport = transport; }
    public Snapshot snapshot() { return snapshot; }
    private static Lyrics empty() { return new Lyrics(java.util.List.of(), "", false, ""); }

    public synchronized void update(TrackState state, boolean online) {
        if (closed) return;
        if (state.source().isEmpty()) {
            if (!requested.isEmpty()) {
                generation.incrementAndGet(); requested = ""; snapshot = Snapshot.idle();
                if (pending != null) pending.cancel(true);
            }
            return;
        }
        LyricsTrack track = LyricsTrack.of(state);
        String identity = track.cacheKey() + ":" + online;
        if (identity.equals(requested) && !(snapshot.status() == Status.ERROR && System.nanoTime() >= retryAfter)) return;
        if (pending != null) pending.cancel(true);
        requested = identity;
        long request = generation.incrementAndGet();
        snapshot = new Snapshot(track.cacheKey(), Status.LOADING, empty(), "Finding lyrics...");
        pending = worker.submit(() -> {
            if (generation.get() != request) return;
            Snapshot result = resolve(track, online);
            synchronized (this) { if (!closed && generation.get() == request) snapshot = result; }
        });
    }

    public synchronized void refresh(TrackState state, boolean online) {
        requested = "";
        update(state, online);
    }

    private Snapshot resolve(LyricsTrack track, boolean online) {
        String key = track.cacheKey();
        try {
            Files.createDirectories(directory);
            Path local = directory.resolve(track.localFileName());
            if (Files.isRegularFile(local)) {
                Lyrics lyrics = Lyrics.parse(readBounded(local), "", false, "Local LRC");
                return result(key, lyrics);
            }
            Lyrics cached = memory.get(key);
            if (cached != null) return result(key, cached);
            Path cache = directory.resolve("cache").resolve(key + ".json");
            if (Files.isRegularFile(cache) && (!online || System.currentTimeMillis() - Files.getLastModifiedTime(cache).toMillis() < Duration.ofDays(7).toMillis())) {
                try {
                    Lyrics lyrics = decode(readBounded(cache), "LRCLIB / cached");
                    remember(key, lyrics);
                    return result(key, lyrics);
                } catch (IOException | RuntimeException ex) {
                    NocturneClient.LOG.debug("Ignoring invalid lyrics cache", ex);
                }
            }
            if (!online) return new Snapshot(key, Status.OFFLINE, empty(), "Online lyrics are off. Local LRC files still work.");
            if (!track.searchable()) return new Snapshot(key, Status.MISSING, empty(), "This player did not provide a title and artist.");
            if (System.nanoTime() < retryAfter) return new Snapshot(key, Status.ERROR, empty(), "Lyrics provider is busy. Retry in a minute.");
            Response response = transport.fetch(uri(track));
            if ((response.status() == 404 || response.status() == 503) && track.duration() > 0) {
                Response search = transport.fetch(URI.create("https://lrclib.net/api/search?track_name=" + encode(track.title()) + "&artist_name=" + encode(track.artist())));
                if (search.status() == 200) {
                    JsonObject candidate = chooseCandidate(search.body(), track);
                    if (candidate != null) response = new Response(200, candidate.toString());
                } else if (search.status() == 429 || search.status() == 503) response = search;
            }
            if (response.status() == 404) return new Snapshot(key, Status.MISSING, empty(), "No matching lyrics. Add a local LRC file or retry later.");
            if (response.status() == 429 || response.status() == 503) {
                retryAfter = System.nanoTime() + Duration.ofMinutes(1).toNanos();
                return new Snapshot(key, Status.ERROR, empty(), "Lyrics provider is busy. Retry in a minute.");
            }
            if (response.status() != 200) throw new IOException("Lyrics provider returned HTTP " + response.status());
            Lyrics lyrics = decode(response.body(), "LRCLIB");
            remember(key, lyrics);
            try {
                Path cacheDir = cache.getParent();
                Files.createDirectories(cacheDir);
                // Bound the dedicated cache without touching user-provided LRC files.
                try (var paths = Files.list(cacheDir)) {
                    var files = paths.filter(path -> path.getFileName().toString().matches("[a-f0-9]{64}\\.json")).sorted().toList();
                    for (int i = 0; i < files.size() - 63; i++) Files.deleteIfExists(files.get(i));
                }
                Path temp = Files.createTempFile(cacheDir, "lyrics-", ".tmp");
                try {
                    Files.writeString(temp, response.body());
                    Files.move(temp, cache, StandardCopyOption.REPLACE_EXISTING);
                } finally { Files.deleteIfExists(temp); }
            } catch (IOException ex) { NocturneClient.LOG.debug("Lyrics loaded but could not be cached", ex); }
            return result(key, lyrics);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Snapshot(key, Status.ERROR, empty(), "Lyrics request cancelled");
        } catch (IOException | RuntimeException ex) {
            retryAfter = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            NocturneClient.LOG.debug("Lyrics unavailable", ex);
            return new Snapshot(key, Status.ERROR, empty(), "Lyrics unavailable. Check your connection or use a local LRC.");
        }
    }

    private void remember(String key, Lyrics lyrics) {
        memory.put(key, lyrics);
        while (memory.size() > 32) memory.remove(memory.keySet().iterator().next());
    }

    static Snapshot result(String key, Lyrics lyrics) {
        Status status = lyrics.instrumental() ? Status.INSTRUMENTAL : lyrics.synced() ? Status.READY
                : !lyrics.plain().isBlank() ? Status.UNSYNCED : Status.MISSING;
        String message = switch (status) {
            case READY -> lyrics.credit();
            case INSTRUMENTAL -> "Instrumental / no vocals";
            case UNSYNCED -> "Plain lyrics / timing not available";
            default -> "No lyrics available for this track";
        };
        return new Snapshot(key, status, lyrics, message);
    }

    static URI uri(LyricsTrack track) {
        String query = "track_name=" + encode(track.title()) + "&artist_name=" + encode(track.artist());
        if (!track.album().isBlank()) query += "&album_name=" + encode(track.album());
        if (track.duration() >= 1 && track.duration() <= 3600) query += "&duration=" + track.duration();
        return URI.create("https://lrclib.net/api/get?" + query);
    }

    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private static String readBounded(Path file) throws IOException {
        if (Files.size(file) > 1_000_000) throw new IOException("Lyrics file too large");
        return Files.readString(file);
    }
    static Lyrics decode(String json, String credit) {
        if (json.length() > 1_000_000) throw new IllegalArgumentException("Lyrics response too large");
        JsonObject value = JsonParser.parseString(json).getAsJsonObject();
        String synced = value.has("syncedLyrics") && !value.get("syncedLyrics").isJsonNull() ? value.get("syncedLyrics").getAsString() : "";
        String plain = value.has("plainLyrics") && !value.get("plainLyrics").isJsonNull() ? value.get("plainLyrics").getAsString() : "";
        return Lyrics.parse(synced, plain, value.has("instrumental") && value.get("instrumental").getAsBoolean(), credit);
    }

    static JsonObject chooseCandidate(String body, LyricsTrack track) {
        if (body.length() > 1_000_000) throw new IllegalArgumentException("Lyrics response too large");
        JsonObject best = null;
        double score = -Double.MAX_VALUE;
        for (var element : JsonParser.parseString(body).getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject candidate = element.getAsJsonObject();
            if (!candidate.has("trackName") || !candidate.has("artistName") || !candidate.has("duration")) continue;
            if (!normalize(candidate.get("trackName").getAsString()).equals(normalize(track.title()))
                    || !normalize(candidate.get("artistName").getAsString()).equals(normalize(track.artist()))) continue;
            double duration = candidate.get("duration").getAsDouble();
            double difference = Math.abs(duration - track.duration());
            if (!Double.isFinite(duration) || difference > 2) continue;
            double quality = candidate.has("syncedLyrics") && !candidate.get("syncedLyrics").isJsonNull()
                    && !candidate.get("syncedLyrics").getAsString().isBlank() ? 100 : 0;
            if (candidate.has("albumName") && !candidate.get("albumName").isJsonNull()
                    && normalize(candidate.get("albumName").getAsString()).equals(normalize(track.album()))) quality += 10;
            quality -= difference;
            if (quality > score) { best = candidate; score = quality; }
        }
        return best;
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC)
                .toLowerCase(java.util.Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    private static Transport httpTransport() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        return uri -> {
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "Nocturne/0.2.0 (https://github.com/Truwty/nocturne)").GET().build();
            HttpResponse<String> response = client.send(request, info -> new LimitedBody());
            return new Response(response.statusCode(), response.body());
        };
    }

    private static final class LimitedBody implements HttpResponse.BodySubscriber<String> {
        private final java.util.concurrent.CompletableFuture<String> body = new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private java.util.concurrent.Flow.Subscription subscription;
        @Override public java.util.concurrent.CompletionStage<String> getBody() { return body; }
        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        @Override public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            for (var buffer : buffers) {
                if (buffer.remaining() > 1_000_000 - bytes.size()) {
                    subscription.cancel(); body.completeExceptionally(new IOException("Lyrics response too large")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { body.completeExceptionally(error); }
        @Override public void onComplete() { body.complete(bytes.toString(StandardCharsets.UTF_8)); }
    }

    @Override public synchronized void close() {
        closed = true; generation.incrementAndGet();
        if (pending != null) pending.cancel(true);
        worker.shutdownNow();
    }
}
