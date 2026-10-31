package dev.truwty.nocturne;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MediaBridge implements AutoCloseable {
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean restart = new AtomicBoolean();
    private volatile TrackState state = TrackState.idle("Connecting to desktop audio...");
    private volatile Process process;
    private BufferedWriter writer;

    public TrackState state() { return state; }

    public void start() {
        if (closed.get()) return;
        if (!running.compareAndSet(false, true)) return;
        Thread.ofPlatform().daemon().name("nocturne-media").start(() -> {
            try {
                if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")
                        || !System.getProperty("os.arch").equals("amd64")) {
                    state = TrackState.idle("This release supports Windows x64. Other platforms are not yet supported.");
                    return;
                }
                state = TrackState.idle("Connecting to desktop audio...");
                Path executable = extract();
                Process child = new ProcessBuilder(executable.toString()).start();
                process = child;
                if (closed.get()) { child.destroy(); return; }
                synchronized (this) {
                    writer = new BufferedWriter(new OutputStreamWriter(child.getOutputStream(), StandardCharsets.UTF_8));
                }
                Thread.ofPlatform().daemon().name("nocturne-media-log").start(() -> {
                    try (var errors = new BufferedReader(new InputStreamReader(child.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = errors.readLine()) != null) NocturneClient.LOG.debug("Media bridge: {}", line);
                    } catch (IOException ex) { NocturneClient.LOG.debug("Media log closed", ex); }
                });
                try (var reader = new BufferedReader(new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try { state = decode(JsonParser.parseString(line).getAsJsonObject()); }
                        catch (RuntimeException ex) { NocturneClient.LOG.warn("Invalid media bridge response", ex); }
                    }
                }
                state = TrackState.idle("Media bridge stopped. Open Nocturne and choose Reconnect.");
            } catch (IOException | NoSuchAlgorithmException ex) {
                NocturneClient.LOG.error("Cannot start media bridge", ex);
                state = TrackState.idle("Cannot start media bridge. Check logs/latest.log, then Reconnect.");
            } finally {
                Process child = process;
                if (child != null) child.destroy();
                synchronized (this) { writer = null; }
                process = null;
                running.set(false);
                if (restart.getAndSet(false) && !closed.get()) start();
            }
        });
    }

    private static Path extract() throws IOException, NoSuchAlgorithmException {
        byte[] bytes;
        try (var stream = MediaBridge.class.getResourceAsStream("/native/nocturne-bridge.exe")) {
            if (stream == null) throw new IOException("Native bridge missing: run the Windows bridge build first");
            bytes = stream.readAllBytes();
        }
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Path directory = FabricLoader.getInstance().getConfigDir().resolve("nocturne/runtime/" + hash);
        Files.createDirectories(directory);
        Path target = directory.resolve("nocturne-bridge.exe");
        if (!Files.exists(target) || !MessageDigest.isEqual(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(target)),
                MessageDigest.getInstance("SHA-256").digest(bytes))) {
            Path temp = Files.createTempFile(directory, "bridge-", ".tmp");
            try {
                Files.write(temp, bytes);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temp); }
        }
        return target;
    }

    static TrackState decode(JsonObject json) {
        var sources = new ArrayList<String>();
        if (json.has("sources")) json.getAsJsonArray("sources").forEach(value -> sources.add(value.getAsString()));
        return new TrackState(bool(json, "connected"), string(json, "title"), string(json, "artist"),
                string(json, "album"), string(json, "source"), java.util.List.copyOf(sources),
                string(json, "selected"), bool(json, "playing"), number(json, "position"),
                number(json, "duration"), bool(json, "canToggle"), bool(json, "canNext"),
                bool(json, "canPrevious"), bool(json, "canSeek"), string(json, "art"),
                string(json, "error"), System.nanoTime());
    }

    private static String string(JsonObject json, String key) { return json.has(key) ? json.get(key).getAsString() : ""; }
    private static boolean bool(JsonObject json, String key) { return json.has(key) && json.get(key).getAsBoolean(); }
    private static double number(JsonObject json, String key) {
        double value = json.has(key) ? json.get(key).getAsDouble() : 0;
        return Double.isFinite(value) ? Math.max(0, value) : 0;
    }

    public void command(String action) {
        JsonObject json = new JsonObject();
        json.addProperty("action", action);
        send(json);
    }

    public void reconnect() {
        if (closed.get()) return;
        if (!running.get()) { start(); return; }
        restart.set(true);
        Process child = process;
        if (child != null) child.destroy();
    }

    public void select(String source) {
        JsonObject json = new JsonObject();
        json.addProperty("action", "select");
        json.addProperty("source", source);
        send(json);
    }

    public void seek(double seconds) {
        if (!Double.isFinite(seconds)) return;
        JsonObject json = new JsonObject();
        json.addProperty("action", "seek");
        json.addProperty("seconds", Math.clamp(seconds, 0, 604800));
        send(json);
    }

    private synchronized void send(JsonObject command) {
        if (writer == null) return;
        try {
            writer.write(command.toString());
            writer.newLine();
            writer.flush();
        } catch (IOException ex) { NocturneClient.LOG.warn("Cannot send media control", ex); }
    }

    @Override public void close() {
        closed.set(true);
        command("quit");
        Process child = process;
        if (child != null) child.destroy();
    }
}
