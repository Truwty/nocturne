package dev.truwty.nocturne.playback;

import com.google.gson.*;
import com.sun.net.httpserver.HttpServer;

import dev.truwty.nocturne.lyrics.NowPlaying;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional public-client PKCE integration. All network and token IO runs on one worker. */
public final class SpotifyPlayback implements AutoCloseable {
    public static final String REDIRECT = "http://127.0.0.1:43869/callback";
    private static final String SCOPES =
            "user-read-playback-state user-modify-playback-state user-read-currently-playing";

    public record Track(String title, String artist, String uri) {}

    public record State(
            NowPlaying track,
            Set<String> capabilities,
            int volume,
            boolean shuffle,
            String repeat,
            String device,
            long received) {
        public boolean fresh() {
            return System.nanoTime() - received < TimeUnit.SECONDS.toNanos(12);
        }
    }

    private record Credentials(String clientId, String access, String refresh, long expiry) {}

    private final PrivateTokenStore store;
    private final URI tokenEndpoint, api;
    private final HttpClient http =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build();
    private final ScheduledExecutorService worker =
            Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r, "nocturne-spotify");
                t.setDaemon(true);
                return t;
            });
    private final ArrayBlockingQueue<Runnable> commands = new ArrayBlockingQueue<>(24);
    private volatile Credentials credentials;
    private volatile State state;
    private volatile List<Track> queue = List.of();
    private volatile String status = "Not connected";
    private volatile String queueStatus = "Refresh to load the Spotify queue";
    private volatile HttpServer callback;
    private volatile boolean enabled, closed, background, paused;
    public void activity(boolean background,boolean paused){if(this.background!=background||this.paused!=paused){this.background=background;this.paused=paused;nextPoll=0;}}
    private volatile long loginGeneration;
    private volatile long nextPoll;
    private long retryAt;

    public SpotifyPlayback(PrivateTokenStore store) {
        this(
                store,
                URI.create("https://accounts.spotify.com/api/token"),
                URI.create("https://api.spotify.com/v1/"));
    }

    SpotifyPlayback(PrivateTokenStore store, URI tokenEndpoint, URI api) {
        this.store = store;
        this.tokenEndpoint = tokenEndpoint;
        this.api = api;
        worker.execute(() -> {
            try {
                String saved = store.load();
                if (saved != null) {
                    var j = JsonParser.parseString(saved).getAsJsonObject();
                    credentials =
                            new Credentials(
                                    j.get("clientId").getAsString(),
                                    j.get("access").getAsString(),
                                    j.get("refresh").getAsString(),
                                    j.get("expiry").getAsLong());
                    status = "Connected · desktop detection remains available";
                }
            } catch (Exception ex) {
                status = "Stored connection is damaged; disconnect and reconnect";
            }
        });
        worker.scheduleWithFixedDelay(this::tick, 250, 250, TimeUnit.MILLISECONDS);
    }

    public State state() {
        State s = state;
        return s != null && s.fresh() ? s : null;
    }

    public String status() {
        return status;
    }

    public boolean connected() {
        return credentials != null;
    }

    public boolean authorizing() {
        return callback != null;
    }

    public List<Track> queue() {
        return queue;
    }

    public String queueStatus() {
        return queueStatus;
    }

    public void enabled(boolean value) {
        enabled = value;
        if (!value) state = null;
    }

    private void offer(Runnable action) {
        if (!closed && !commands.offer(action))
            status = "Spotify command queue full; wait a moment";
    }

    private void tick() {
        if (closed) return;
        try {
            if (System.currentTimeMillis() < retryAt) return;
            Runnable action = commands.poll();
            if (action != null) action.run();
            if (enabled && credentials != null && System.currentTimeMillis() >= nextPoll) {
                nextPoll = System.currentTimeMillis() + (background?10000:paused||state==null||!state.track().playing()?5000:2000);
                JsonObject json = request("GET", "me/player", null);
                state = json == null ? null : decode(json);
                if (state != null) status = "Connected · " + state.device();
                else status = "Connected · start playback on a Spotify device";
            }
        } catch (Exception ex) {
            state = null;
            if (!status.startsWith("Spotify ")) status = "Spotify connection unavailable; retrying";
            nextPoll = System.currentTimeMillis() + 5000;
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static String bounded(HttpResponse<byte[]> response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    private void token(Map<String, String> fields, String clientId)
            throws IOException, InterruptedException {
        token(fields, clientId, -1);
    }

    private void token(Map<String, String> fields, String clientId, long generation)
            throws IOException, InterruptedException {
        var response =
                http.send(
                        HttpRequest.newBuilder(tokenEndpoint)
                                .timeout(Duration.ofSeconds(8))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString(form(fields)))
                                .build(),
                        BoundedBody.handler(1048576));
        String body = bounded(response);
        if (response.statusCode() != 200) {
            if (response.statusCode() == 400 || response.statusCode() == 401) {
                credentials = null;
                state = null;
                try {
                    store.clear();
                } catch (IOException ex) {
                    status =
                            "Spotify authorization expired; stored credentials could not be"
                                    + " removed";
                    throw ex;
                }
            }
            status = "Spotify authorization failed; reconnect the account";
            throw new IOException("Authorization failed");
        }
        var json = JsonParser.parseString(body).getAsJsonObject();
        String access = json.get("access_token").getAsString();
        String refresh =
                json.has("refresh_token")
                        ? json.get("refresh_token").getAsString()
                        : credentials == null ? "" : credentials.refresh();
        if (access.isBlank() || refresh.isBlank())
            throw new IOException("Incomplete authorization response");
        long expires = Math.clamp(json.get("expires_in").getAsLong(), 60, 86400);
        var saved = new JsonObject();
        saved.addProperty("clientId", clientId);
        saved.addProperty("access", access);
        saved.addProperty("refresh", refresh);
        saved.addProperty("expiry", System.currentTimeMillis() + expires * 1000);
        if (closed || (generation >= 0 && generation != loginGeneration))
            throw new IOException("Authorization cancelled");
        store.save(saved.toString());
        credentials = new Credentials(clientId, access, refresh, saved.get("expiry").getAsLong());
        status = "Connected · choose Spotify account playback to use it";
    }

    private void refresh() throws IOException, InterruptedException {
        var c = credentials;
        if (c == null) throw new IOException("Connect Spotify first");
        token(
                Map.of(
                        "grant_type",
                        "refresh_token",
                        "refresh_token",
                        c.refresh(),
                        "client_id",
                        c.clientId()),
                c.clientId());
    }

    private JsonObject request(String method, String path, String body)
            throws IOException, InterruptedException {
        if (credentials == null) throw new IOException("Connect Spotify first");
        if (credentials.expiry() - 30000 <= System.currentTimeMillis()) refresh();
        for (int attempt = 0; attempt < 2; attempt++) {
            var builder =
                    HttpRequest.newBuilder(api.resolve(path))
                            .timeout(Duration.ofSeconds(8))
                            .header("Authorization", "Bearer " + credentials.access());
            builder.method(
                    method,
                    body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body));
            if (body != null) builder.header("Content-Type", "application/json");
            var response = http.send(builder.build(), BoundedBody.handler(1048576));
            String data = bounded(response);
            int code = response.statusCode();
            if (code == 401 && attempt == 0) {
                refresh();
                continue;
            }
            if (code == 204) return null;
            if (code == 429) {
                long delay;
                try {
                    delay =
                            Long.parseLong(
                                    response.headers().firstValue("Retry-After").orElse("30"));
                } catch (NumberFormatException ex) {
                    delay = 30;
                }
                commands.clear();
                retryAt = System.currentTimeMillis() + Math.clamp(delay, 1, 3600) * 1000;
                status = "Spotify rate limited; waiting before retry";
                throw new IOException("Rate limited");
            }
            if (code < 200 || code >= 300) {
                status =
                        switch (code) {
                            case 401 -> "Spotify authorization expired; reconnect";
                            case 403 ->
                                    "Spotify denied control: check Premium and developer app"
                                            + " access";
                            case 404 -> "Spotify has no active playback device";
                            default -> "Spotify request failed (HTTP " + code + ")";
                        };
                throw new IOException("Spotify request rejected");
            }
            return data.isBlank() ? null : JsonParser.parseString(data).getAsJsonObject();
        }
        throw new IOException("Authorization expired");
    }

    public synchronized URI connect(String clientId) throws IOException {
        if (!clientId.matches("[A-Za-z0-9]{16,80}"))
            throw new IllegalArgumentException("Enter a Spotify developer application Client ID");
        cancelLogin();
        long generation = ++loginGeneration;
        Pkce pkce = Pkce.create(System.currentTimeMillis());
        var server =
                HttpServer.create(
                        new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 43869), 0);
        callback = server;
        var used = new AtomicBoolean();
        server.createContext(
                "/callback",
                exchange -> {
                    boolean accepted = false;
                    String code = "";
                    try {
                        var values = Pkce.query(exchange.getRequestURI().getRawQuery());
                        code = values.getOrDefault("code", "");
                        accepted =
                                exchange.getRequestMethod().equals("GET")
                                        && exchange.getRequestURI().getPath().equals("/callback")
                                        && "127.0.0.1:43869"
                                                .equals(
                                                        exchange.getRequestHeaders()
                                                                .getFirst("Host"))
                                        && pkce.accepts(
                                                values.get("state"), System.currentTimeMillis())
                                        && !code.isBlank()
                                        && !values.containsKey("error")
                                        && used.compareAndSet(false, true);
                    } catch (IllegalArgumentException ex) {
                        accepted = false;
                    }
                    byte[] reply =
                            (accepted
                                            ? "Nocturne: authorization received. Return to"
                                                    + " Minecraft."
                                            : "Nocturne: invalid or expired authorization. Return"
                                                    + " to Minecraft and reconnect.")
                                    .getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    exchange.getResponseHeaders().set("Cache-Control", "no-store");
                    exchange.sendResponseHeaders(accepted ? 200 : 400, reply.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(reply);
                    } finally {
                        exchange.close();
                    }
                    if (accepted) {
                        String authorization = code;
                        worker.execute(() -> {
                            if (generation != loginGeneration) return;
                            try {
                                token(
                                        Map.of(
                                                "grant_type",
                                                "authorization_code",
                                                "code",
                                                authorization,
                                                "redirect_uri",
                                                REDIRECT,
                                                "client_id",
                                                clientId,
                                                "code_verifier",
                                                pkce.verifier()),
                                        clientId,
                                        generation);
                            } catch (Exception ex) {
                                if (!status.startsWith("Spotify "))
                                    status = "Spotify connection failed; reconnect";
                            } finally {
                                if (callback == server) {
                                    server.stop(0);
                                    callback = null;
                                }
                            }
                        });
                    }
                });
        server.start();
        status = "Waiting for browser authorization (5 minute timeout)";
        worker.schedule(
                () -> {
                    if (callback == server) {
                        server.stop(0);
                        callback = null;
                        status = "Authorization timed out; connect again";
                    }
                },
                5,
                TimeUnit.MINUTES);
        return URI.create(
                "https://accounts.spotify.com/authorize?"
                        + form(
                                Map.of(
                                        "client_id",
                                        clientId,
                                        "response_type",
                                        "code",
                                        "redirect_uri",
                                        REDIRECT,
                                        "state",
                                        pkce.state(),
                                        "code_challenge_method",
                                        "S256",
                                        "code_challenge",
                                        pkce.challenge(),
                                        "scope",
                                        SCOPES)));
    }

    public synchronized void cancelLogin() {
        loginGeneration++;
        var server = callback;
        callback = null;
        if (server != null) server.stop(0);
    }

    public void disconnect() {
        cancelLogin();
        enabled = false;
        state = null;
        queue = List.of();
        commands.clear();
        credentials = null;
        worker.execute(() -> {
            credentials = null;
            try {
                store.clear();
                status = "Disconnected; local credentials removed";
            } catch (IOException ex) {
                status = "Disconnected; could not remove local credential files";
            }
        });
    }

    public static boolean validTrackUri(String uri) {
        return uri != null && uri.matches("spotify:(track|episode):[A-Za-z0-9]{22}");
    }

    public void refreshQueue() {
        queueStatus = "Loading queue…";
        offer(() -> {
            try {
                var json = request("GET", "me/player/queue", null);
                var items = new ArrayList<Track>();
                if (json != null && json.has("queue"))
                    for (var item : json.getAsJsonArray("queue")) {
                        if (items.size() >= 50) break;
                        items.add(track(item.getAsJsonObject()));
                    }
                queue = List.copyOf(items);
                queueStatus =
                        items.isEmpty() ? "Queue is empty" : "Next " + items.size() + " items";
            } catch (Exception ex) {
                queueStatus = "Queue unavailable · " + status;
            }
        });
    }

    public void addQueue(String uri) {
        if (!validTrackUri(uri))
            throw new IllegalArgumentException("Use a spotify:track:… or spotify:episode:… URI");
        offer(() -> {
            try {
                request("POST", "me/player/queue?uri=" + enc(uri), null);
                queueStatus = "Added to queue; refresh to update";
            } catch (Exception ex) {
                queueStatus = "Could not add item · " + status;
            }
        });
    }

    public void command(String action, long value) {
        State target = state();
        if (target == null || !target.capabilities().contains(action)) {
            status = "This Spotify device does not support " + action;
            return;
        }
        offer(() -> {
            State current = state();
            if (current == null
                    || !Objects.equals(current.track().sessionId(), target.track().sessionId())) {
                status = "Playback device changed; retry the control";
                return;
            }
            try {
                String device = "device_id=" + enc(target.track().sessionId());
                String path;
                String method = "PUT";
                switch (action) {
                    case "toggle" ->
                            path =
                                    "me/player/"
                                            + (current.track().playing() ? "pause" : "play")
                                            + "?"
                                            + device;
                    case "next", "previous" -> {
                        path = "me/player/" + action + "?" + device;
                        method = "POST";
                    }
                    case "seek" ->
                            path =
                                    "me/player/seek?position_ms="
                                            + Math.clamp(
                                                    value,
                                                    0,
                                                    Math.max(0, current.track().durationMs()))
                                            + "&"
                                            + device;
                    case "volume" ->
                            path =
                                    "me/player/volume?volume_percent="
                                            + Math.clamp(value, 0, 100)
                                            + "&"
                                            + device;
                    case "shuffle" ->
                            path = "me/player/shuffle?state=" + (value != 0) + "&" + device;
                    case "repeat" ->
                            path =
                                    "me/player/repeat?state="
                                            + List.of("off", "track", "context")
                                                    .get(Math.clamp((int) value, 0, 2))
                                            + "&"
                                            + device;
                    default -> throw new IllegalArgumentException("Unsupported action");
                }
                request(method, path, null);
                status = "Spotify command accepted";
                nextPoll = 0;
            } catch (Exception ex) {
                if (!status.startsWith("Spotify ")) status = "Spotify command failed";
            }
        });
    }

    private static String str(JsonObject json, String name, String fallback) {
        return json.has(name) && !json.get(name).isJsonNull()
                ? json.get(name).getAsString()
                : fallback;
    }

    private static boolean bool(JsonObject json, String name) {
        return json.has(name) && !json.get(name).isJsonNull() && json.get(name).getAsBoolean();
    }

    static Track track(JsonObject item) {
        String artist = "";
        if (item.has("artists")) {
            var names = new ArrayList<String>();
            for (var a : item.getAsJsonArray("artists"))
                names.add(str(a.getAsJsonObject(), "name", ""));
            artist = String.join(", ", names);
        } else if (item.has("show")) artist = str(item.getAsJsonObject("show"), "name", "");
        return new Track(str(item, "name", "Unknown track"), artist, str(item, "uri", ""));
    }

    public static State decode(JsonObject json) {
        if (!json.has("item")
                || !json.get("item").isJsonObject()
                || !json.has("device")
                || !json.get("device").isJsonObject()) return null;
        var item = json.getAsJsonObject("item");
        var device = json.getAsJsonObject("device");
        String id = str(device, "id", "");
        if (id.isBlank()) return null;
        var track = track(item);
        String album = "", art = "";
        if (item.has("album")) {
            var a = item.getAsJsonObject("album");
            album = str(a, "name", "");
            if (a.has("images"))
                for (var image : a.getAsJsonArray("images")) {
                    String url = str(image.getAsJsonObject(), "url", "");
                    if (url.startsWith("https://")) {
                        art = url;
                        break;
                    }
                }
        }
        boolean playing = bool(json, "is_playing");
        long duration =
                Math.max(0, item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0);
        long progress =
                json.has("progress_ms") && !json.get("progress_ms").isJsonNull()
                        ? Math.clamp(json.get("progress_ms").getAsLong(), 0, Math.max(0, duration))
                        : 0;
        var caps = new HashSet<String>();
        JsonObject disallows =
                json.has("actions") ? json.getAsJsonObject("actions") : new JsonObject();
        if (disallows.has("disallows")) disallows = disallows.getAsJsonObject("disallows");
        if (!bool(device, "is_restricted")) {
            if (!bool(disallows, playing ? "pausing" : "resuming")) caps.add("toggle");
            if (!bool(disallows, "seeking")) caps.add("seek");
            if (!bool(disallows, "skipping_next")) caps.add("next");
            if (!bool(disallows, "skipping_prev")) caps.add("previous");
            if (!bool(disallows, "toggling_shuffle")) caps.add("shuffle");
            if (!bool(disallows, "toggling_repeat_context")
                    && !bool(disallows, "toggling_repeat_track")) caps.add("repeat");
            if (bool(device, "supports_volume")
                    && device.has("volume_percent")
                    && !device.get("volume_percent").isJsonNull()) caps.add("volume");
            caps.add("queue");
        }
        int volume =
                device.has("volume_percent") && !device.get("volume_percent").isJsonNull()
                        ? Math.clamp(device.get("volume_percent").getAsInt(), 0, 100)
                        : -1;
        return new State(
                new NowPlaying(
                        track.title(),
                        track.artist(),
                        album,
                        duration,
                        progress,
                        playing,
                        System.nanoTime(),
                        "Spotify account",
                        art,
                        id),
                Set.copyOf(caps),
                volume,
                bool(json, "shuffle_state"),
                str(json, "repeat_state", "off"),
                str(device, "name", "Spotify device"),
                System.nanoTime());
    }

    @Override
    public void close() {
        closed = true;
        cancelLogin();
        commands.clear();
        worker.shutdownNow();
        state = null;
        http.close();
    }
}
