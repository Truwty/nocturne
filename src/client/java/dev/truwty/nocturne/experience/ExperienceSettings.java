package dev.truwty.nocturne.experience;

import com.google.gson.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Optional presentation modules. These settings never change a server's world or player abilities.
 */
public final class ExperienceSettings {
    public static final String DEFAULT_DISCORD_APPLICATION_ID = "1567160067910799512";

    public enum Group {
        WORLD,
        COMBAT,
        COSMETICS,
        MOVEMENT,
        INTERFACE,
        FUN
    }

    public enum Option {
        WORLD_LOOK(
                Group.WORLD,
                "World shader look",
                "Server",
                "Nocturne",
                "Cyberspace",
                "Golden hour",
                "Glacier",
                "Rosewood",
                "Monochrome"),
        TIME(Group.WORLD, "Local time", "Server", "Dawn", "Noon", "Sunset", "Midnight"),
        WEATHER(
                Group.WORLD,
                "Local weather",
                "Server",
                "Clear",
                "Rain",
                "Snow",
                "Blizzard",
                "Sandstorm",
                "Blossom",
                "Ash"),
        SEASON(Group.WORLD, "Local season", "Off", "Spring", "Summer", "Autumn", "Winter"),
        AURORA(Group.WORLD, "Aurora", "Off", "On"),
        STARS(Group.WORLD, "Shooting stars", "Off", "On"),
        AMBIENT(Group.WORLD, "Ambient particles", "Off", "Fireflies", "Enchanting", "Cherry"),
        FOG(Group.WORLD, "Fog distance", "Server", "Close", "Balanced", "Distant", "Custom"),
        CLOUDS(Group.WORLD, "Cloud height", "Server", "Low", "High", "Hidden", "Custom"),
        KILLCAM(Group.COMBAT, "Slow-motion replay", "Off", "Record only", "Replay after kill"),
        HIT(Group.COMBAT, "Hit splashes", "Off", "Sparks", "Hearts", "Soul", "Rainbow"),
        CRIT(Group.COMBAT, "Critical hit burst", "Off", "On"),
        DAMAGE(Group.COMBAT, "Observed damage numbers", "Off", "On"),
        KILL(
                Group.COMBAT,
                "Kill effect",
                "Off",
                "Tung Tung Sahur",
                "Brainrot",
                "Hearts",
                "Bloom",
                "Soul",
                "Surprise me"),
        BANNERS(Group.COMBAT, "Multi-kill banners", "Off", "On"),
        SOUND(Group.COMBAT, "Kill sound", "Off", "Bell", "Chime", "Bonk"),
        DEATH(
                Group.COMBAT,
                "Death screen",
                "Vanilla",
                "Undertale",
                "Wasted",
                "Arcade",
                "Cinematic",
                "Starlight",
                "Minimal",
                "Glitch",
                "Bloom"),
        MACE(Group.COMBAT, "Mace hit effect", "Off", "On"),
        WIND(Group.COMBAT, "Wind charge trail", "Off", "On"),
        SKIN(Group.COSMETICS, "Local skin material", "Normal", "Glass", "Ghost"),
        CAPE(Group.COSMETICS, "Procedural cape", "Off", "Nocturne", "Cyberspace", "Sunset"),
        WINGS(Group.COSMETICS, "Wings", "Off", "Angel", "Neon", "Butterfly"),
        HEAD(Group.COSMETICS, "Head accessory", "Off", "Halo", "Crown", "Horns", "Top hat"),
        AURA(Group.COSMETICS, "Aura", "Off", "Stars", "Hearts", "Soul"),
        PET(Group.COSMETICS, "Companion", "Off", "Orb", "Sprite", "Star"),
        FLOOR(Group.MOVEMENT, "Floor trail", "Off", "Sparks", "Hearts", "Soul", "Cherry"),
        PROJECTILES(Group.MOVEMENT, "Projectile trails", "Off", "Sparks", "Soul", "Rainbow"),
        JUMP(Group.MOVEMENT, "Jump circles", "Off", "On"),
        LAND(Group.MOVEMENT, "Landing shockwave", "Off", "On"),
        HOTBAR(Group.INTERFACE, "Smooth hotbar", "Off", "On"),
        CONTAINER(
                Group.INTERFACE,
                "Container preview",
                "Off",
                "Held item",
                "Last opened chest",
                "Both"),
        INTRO(Group.INTERFACE, "Skin slash intro", "Off", "On"),
        WIDGETS(Group.INTERFACE, "Session HUD", "Off", "On"),
        ITEM_TIMER(Group.INTERFACE, "Dropped item estimate", "Off", "On"),
        OUTLINE(Group.INTERFACE, "Block outline", "Off", "Mint", "Rose", "Gold"),
        RPC(Group.FUN, "Discord Rich Presence", "Off", "On"),
        DUMMY(Group.FUN, "Local practice hologram", "Off", "On");

        public final Group group;
        public final String label;
        public final List<String> choices;

        Option(Group group, String label, String... choices) {
            this.group = group;
            this.label = label;
            this.choices = List.of(choices);
        }
    }

    private final EnumMap<Option, Integer> values = new EnumMap<>(Option.class);
    private String petName = "Luna", discordAppId = "";
    private int limit = 96;
    private int fogDistance = 128, cloudHeight = 192;
    private float fogHue = .45f;
    private boolean dirty;
    private boolean writable = true;
    private final Path file;

    public ExperienceSettings(Path file) {
        this.file = file;
    }

    public int get(Option option) {
        return values.getOrDefault(option, 0);
    }

    public boolean on(Option option) {
        return get(option) != 0;
    }

    public String label(Option option) {
        return option.choices.get(get(option));
    }

    public void set(Option option, int value) {
        if (value < 0 || value >= option.choices.size())
            throw new IllegalArgumentException("Invalid " + option.label);
        values.put(option, value);
        dirty = true;
    }

    public void cycle(Option option) {
        set(option, (get(option) + 1) % option.choices.size());
    }

    public String petName() {
        return petName;
    }

    public void petName(String value) {
        if (value == null
                || value.isBlank()
                || value.codePointCount(0, value.length()) > 24
                || value.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Pet name must contain 1–24 visible characters");
        petName = value.strip();
        dirty = true;
    }

    public String discordAppId() {
        return discordAppId.isEmpty() ? DEFAULT_DISCORD_APPLICATION_ID : discordAppId;
    }

    public void discordAppId(String id) {
        if (!id.isEmpty() && !id.matches("[0-9]{17,20}"))
            throw new IllegalArgumentException(
                    "Use a 17–20 digit Discord application ID, not a token");
        discordAppId = id;
        dirty = true;
    }

    public int fogDistance() {
        return fogDistance;
    }

    public int cloudHeight() {
        return cloudHeight;
    }

    public float fogHue() {
        return fogHue;
    }

    public void fogDistance(int value) {
        fogDistance = Math.clamp(value, 16, 512);
        dirty = true;
    }

    public void cloudHeight(int value) {
        cloudHeight = Math.clamp(value, 64, 384);
        dirty = true;
    }

    public void fogHue(float value) {
        if (!Float.isFinite(value)) throw new IllegalArgumentException("Invalid fog hue");
        fogHue = Math.clamp(value, 0, 1);
        dirty = true;
    }

    public int limit() {
        return limit;
    }

    public void limit(int value) {
        limit = Math.clamp(value, 16, 256);
        dirty = true;
    }

    public void reset() {
        values.clear();
        petName = "Luna";
        discordAppId = "";
        limit = 96;
        fogDistance = 128;
        cloudHeight = 192;
        fogHue = .45f;
        dirty = true;
    }

    public JsonObject snapshot() {
        var json = new JsonObject();
        json.addProperty("schema", 1);
        json.addProperty("petName", petName);
        json.addProperty("particleLimit", limit);
        json.addProperty("discordAppId", discordAppId);
        json.addProperty("fogDistance", fogDistance);
        json.addProperty("cloudHeight", cloudHeight);
        json.addProperty("fogHue", fogHue);
        var options = new JsonObject();
        for (var option : Option.values()) options.addProperty(option.name(), label(option));
        json.add("options", options);
        return json;
    }

    public void restore(JsonObject json) {
        if (!json.has("schema")
                || !json.get("schema").isJsonPrimitive()
                || !json.getAsJsonPrimitive("schema").isNumber()
                || !json.get("schema").getAsString().equals("1"))
            throw new IllegalArgumentException("Unsupported experience settings schema");
        var next = new EnumMap<Option, Integer>(Option.class);
        var options = json.getAsJsonObject("options");
        if (options != null)
            for (var option : Option.values())
                if (options.has(option.name())) {
                    int index = option.choices.indexOf(options.get(option.name()).getAsString());
                    if (index < 0)
                        throw new IllegalArgumentException("Unknown value for " + option.label);
                    next.put(option, index);
                }
        String name = json.has("petName") ? json.get("petName").getAsString() : "Luna";
        if (json.has("particleLimit")
                && (!json.get("particleLimit").isJsonPrimitive()
                        || !json.getAsJsonPrimitive("particleLimit").isNumber()
                        || !json.get("particleLimit").getAsString().matches("[0-9]{1,3}")))
            throw new IllegalArgumentException("Invalid particle budget");
        int count = json.has("particleLimit") ? json.get("particleLimit").getAsInt() : 96;
        String app = json.has("discordAppId") ? json.get("discordAppId").getAsString() : "";
        if (!app.isEmpty() && !app.matches("[0-9]{17,20}"))
            throw new IllegalArgumentException("Invalid Discord application ID");
        int fog = json.has("fogDistance") ? json.get("fogDistance").getAsInt() : 128;
        int clouds = json.has("cloudHeight") ? json.get("cloudHeight").getAsInt() : 192;
        float hue = json.has("fogHue") ? json.get("fogHue").getAsFloat() : .45f;
        if (!Float.isFinite(hue)
                || hue < 0
                || hue > 1
                || fog < 16
                || fog > 512
                || clouds < 64
                || clouds > 384)
            throw new IllegalArgumentException("Invalid environment customization");
        petName(name);
        limit(count);
        fogDistance = fog;
        cloudHeight = clouds;
        fogHue = hue;
        discordAppId = app;
        values.clear();
        values.putAll(next);
        dirty = true;
    }

    public void load() throws IOException {
        if (!Files.exists(file)) return;
        if (Files.size(file) > 65536) throw new IOException("Experience config exceeds 64 KiB");
        try {
            var json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (json.has("schema")
                    && json.get("schema").isJsonPrimitive()
                    && json.getAsJsonPrimitive("schema").isNumber()
                    && json.get("schema").getAsDouble() > 1) {
                writable = false;
                throw new IOException(
                        "Experience config is from a newer Nocturne version; it is read-only");
            }
            restore(json);
            dirty = false;
        } catch (RuntimeException error) {
            Files.copy(
                    file,
                    file.resolveSibling(
                            file.getFileName() + ".invalid-" + System.currentTimeMillis()));
            throw new IOException(
                    "Invalid experience settings; original preserved in a backup", error);
        }
    }

    public void save() throws IOException {
        if (!dirty) return;
        if (!writable) throw new IOException("Newer experience settings are read-only");
        Files.createDirectories(file.getParent());
        Path pending = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(
                pending, new GsonBuilder().setPrettyPrinting().create().toJson(snapshot()) + "\n");
        try {
            Files.move(
                    pending,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(pending, file, StandardCopyOption.REPLACE_EXISTING);
        }
        dirty = false;
    }
}
