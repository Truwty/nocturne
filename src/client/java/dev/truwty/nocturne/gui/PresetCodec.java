package dev.truwty.nocturne.gui;

import com.google.gson.*;

import dev.truwty.nocturne.Studio;

public final class PresetCodec {
    private PresetCodec() {}

    public static String encode(String group, String name, JsonObject values) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", 1);
        root.addProperty("group", group);
        root.addProperty("name", name);
        root.add("values", values.deepCopy());
        return root.toString();
    }

    public static JsonObject decode(String group, String json) {
        if (json.length() > 65536) throw new IllegalArgumentException("Preset exceeds 64 KiB");
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject values = root;
        if (root.has("schema")) {
            if (root.get("schema").getAsInt() != 1
                    || !root.has("group")
                    || !root.get("group").getAsString().equals(group))
                throw new IllegalArgumentException(
                        "Preset belongs to a different category or version");
            values = root.getAsJsonObject("values");
        }
        if (group.equals("hudLayouts")) return HudLayout.parse(values).json();
        if (!group.equals("themes") && !group.equals(dev.truwty.nocturne.LyricLooks.GROUP) && !group.startsWith("placement:"))
            throw new IllegalArgumentException("Unsupported preset category");
        if (values == null || values.isEmpty()) throw new IllegalArgumentException("Empty preset");
        for (String key : values.keySet())
            if (!Studio.accepts(group, key))
                throw new IllegalArgumentException("Unknown preset setting: " + key);
        Studio.validate(group, values);
        return values.deepCopy();
    }
}
