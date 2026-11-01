package dev.truwty.nocturne.playback;

import com.google.gson.*;

import dev.truwty.nocturne.Studio;

import java.io.IOException;
import java.util.*;

public final class PlaybackPreferences {
    private PlaybackPreferences() {}

    private static JsonObject values() {
        var json = Studio.library().preset("playback", "Options");
        return json == null ? new JsonObject() : json;
    }

    public static List<String> priorities() {
        try {
            var v = values();
            if (!v.has("priorities")) return PlayerSelection.DEFAULTS;
            var result = new ArrayList<String>();
            for (var entry : v.getAsJsonArray("priorities")) result.add(entry.getAsString());
            return PlayerSelection.validate(result);
        } catch (RuntimeException ex) {
            return PlayerSelection.DEFAULTS;
        }
    }

    public static void priorities(List<String> entries) throws IOException {
        var json = values();
        var array = new JsonArray();
        PlayerSelection.validate(entries).forEach(array::add);
        json.add("priorities", array);
        Studio.library().preset("playback", "Options", json);
    }

    public static boolean cloud() {
        var json = values();
        return json.has("spotifyPlayback")
                && json.get("spotifyPlayback").isJsonPrimitive()
                && json.getAsJsonPrimitive("spotifyPlayback").isBoolean()
                && json.get("spotifyPlayback").getAsBoolean();
    }

    public static void cloud(boolean enabled) throws IOException {
        var json = values();
        json.addProperty("spotifyPlayback", enabled);
        Studio.library().preset("playback", "Options", json);
    }

    public static String clientId() {
        var json = values();
        return json.has("spotifyClientId")
                        && json.get("spotifyClientId").isJsonPrimitive()
                        && json.getAsJsonPrimitive("spotifyClientId").isString()
                ? json.get("spotifyClientId").getAsString()
                : "";
    }

    public static void clientId(String id) throws IOException {
        if (!id.matches("[A-Za-z0-9]{16,80}"))
            throw new IllegalArgumentException("Enter the public Spotify application Client ID");
        var json = values();
        json.addProperty("spotifyClientId", id);
        Studio.library().preset("playback", "Options", json);
    }
}
