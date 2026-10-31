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

    public static Preferences load() {
        if (!Files.exists(FILE)) return new Preferences();
        try (var reader = Files.newBufferedReader(FILE)) {
            Preferences result = GSON.fromJson(reader, Preferences.class);
            if (result == null) return new Preferences();
            result.corner = Math.clamp(result.corner, 0, 3);
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
