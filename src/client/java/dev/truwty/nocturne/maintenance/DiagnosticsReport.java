package dev.truwty.nocturne.maintenance;

import com.google.gson.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Only typed, explicitly approved facts can enter a shared report. No raw log text. */
public final class DiagnosticsReport {
    private static final Set<String> BOOLEANS =
            Set.of(
                    "enabled",
                    "trackDetected",
                    "lyricsLoaded",
                    "playing",
                    "shaders",
                    "hidden",
                    "friendlyMode",
                    "accountConnected",
                    "automaticUpdates");
    private static final Set<String> INTEGERS =
            Set.of(
                    "javaFeature",
                    "guiScale",
                    "width",
                    "height",
                    "detectedPlayers",
                    "lyricLines",
                    "particles",
                    "textSubmissions",
                    "meshVertices",
                    "projectedCommands");
    private static final Map<String, Set<String>> ENUMS =
            Map.of(
                    "os",
                    Set.of("Windows", "macOS", "Linux", "Other"),
                    "recovery",
                    Set.of("NONE", "ENABLE", "RETRY", "RESYNC", "SHOW", "RECONNECT"),
                    "renderer",
                    Set.of("WORLD", "PROJECTED"));

    private DiagnosticsReport() {}

    public static String encode(Map<String, ?> facts) {
        var out = new JsonObject();
        out.addProperty("schema", 1);
        out.addProperty(
                "privacy",
                "Technical allowlist; no logs, songs, paths, server addresses or credentials");
        for (var entry : new TreeMap<>(facts).entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (BOOLEANS.contains(key) && value instanceof Boolean flag) out.addProperty(key, flag);
            else if (INTEGERS.contains(key) && value instanceof Number number) {
                double n = number.doubleValue();
                if (Double.isFinite(n) && n >= 0 && n <= 10000000 && n == Math.rint(n))
                    out.addProperty(key, (long) n);
            } else if (key.equals("frameCpuMs")
                    && value instanceof Number number
                    && Double.isFinite(number.doubleValue())
                    && number.doubleValue() >= 0
                    && number.doubleValue() < 100000) out.addProperty(key, number.doubleValue());
            else if (ENUMS.containsKey(key)
                    && value instanceof String text
                    && ENUMS.get(key).contains(text)) out.addProperty(key, text);
            else if (key.equals("minecraft") && Set.of("26.1", "26.1.1", "26.1.2", "26.2", "26.3").contains(value))
                out.addProperty(key, (String) value);
            else if (key.equals("modVersion") && value instanceof String text) {
                try {
                    ReleaseVersion.parse(text);
                    out.addProperty(key, text);
                } catch (IllegalArgumentException ex) {
                    out.addProperty(key, "development");
                }
            }
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(out) + "\n";
    }

    public static Path write(Path directory, Map<String, ?> facts) throws IOException {
        Files.createDirectories(directory);
        Path path = Files.createTempFile(directory, "nocturne-diagnostics-", ".json");
        try {
            Files.writeString(path, encode(facts), StandardCharsets.UTF_8);
            return path;
        } catch (IOException ex) {
            Files.deleteIfExists(path);
            throw ex;
        }
    }
}
