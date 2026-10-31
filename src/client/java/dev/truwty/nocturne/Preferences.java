package dev.truwty.nocturne;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class Preferences {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nocturne.json");
    public boolean hud = true;
    public int corner = 0;
    public boolean animations = true;
    public boolean hideWhenIdle = false;
    public String theme = "GRAPHITE";
    public boolean onlineLyrics = true;
    public boolean lyricsHud = true;
    public int lyricsMode = 0;
    public double lyricsOffset = 0;
    public double lyricsScale = 1;
    public int hudOpacity = 90;

    public static Preferences load() {
        if (!Files.exists(FILE)) return new Preferences();
        try (var reader = Files.newBufferedReader(FILE)) {
            Preferences result = GSON.fromJson(reader, Preferences.class);
            if (result == null) return new Preferences();
            result.corner = Math.clamp(result.corner, 0, 3);
            result.theme = Theme.fromName(result.theme).name();
            result.lyricsMode = Math.clamp(result.lyricsMode, 0, 2);
            result.lyricsOffset = Double.isFinite(result.lyricsOffset) ? Math.clamp(result.lyricsOffset, -10, 10) : 0;
            result.lyricsScale = Double.isFinite(result.lyricsScale) ? Math.clamp(result.lyricsScale, 0.75, 1.5) : 1;
            result.hudOpacity = Math.clamp(result.hudOpacity, 20, 100);
            return result;
        } catch (IOException | com.google.gson.JsonParseException ex) {
            NocturneClient.LOG.warn("Cannot read Nocturne settings; using defaults", ex);
            return new Preferences();
        }
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Path temporary = FILE.resolveSibling("nocturne.json.tmp");
            Files.writeString(temporary, GSON.toJson(this));
            Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            NocturneClient.LOG.error("Cannot save Nocturne settings", ex);
        }
    }
}
