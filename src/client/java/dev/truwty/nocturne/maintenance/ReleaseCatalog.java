package dev.truwty.nocturne.maintenance;

import com.google.gson.*;

import java.net.URI;
import java.util.*;

public final class ReleaseCatalog {
    public static final String MINECRAFT = "26.1.2";
    public static final URI RELEASES = URI.create("https://github.com/Truwty/nocturne/releases");

    public record Release(String tag, URI changelog) {}

    private ReleaseCatalog() {}

    public static Optional<Release> newest(String json, String current, boolean previews) {
        if (json.length() > 262144) throw new IllegalArgumentException("Release catalog too large");
        ReleaseVersion installed = ReleaseVersion.parse(current), best = installed;
        Release found = null;
        var array = JsonParser.parseString(json).getAsJsonArray();
        if (array.size() > 100) throw new IllegalArgumentException("Too many releases");
        for (var element : array) {
            try {
                var item = element.getAsJsonObject();
                if (item.get("draft").getAsBoolean()) continue;
                String tag = item.get("tag_name").getAsString();
                var version = ReleaseVersion.parse(tag);
                if ((!version.pre().isEmpty() || item.get("prerelease").getAsBoolean())
                        && !previews) continue;
                if (version.compareTo(best) <= 0) continue;
                boolean marker = false, jar = false;
                for (var asset : item.getAsJsonArray("assets")) {
                    String name = asset.getAsJsonObject().get("name").getAsString();
                    marker |= name.equals("nocturne-mc" + MINECRAFT + ".json");
                    jar |=
                            name.equals(
                                    "nocturne-"
                                            + (tag.startsWith("v") ? tag.substring(1) : tag)
                                            + ".jar");
                }
                if (!marker || !jar) continue;
                best = version;
                found = new Release(tag, URI.create(RELEASES + "/tag/" + tag));
            } catch (IllegalArgumentException | IllegalStateException | NullPointerException ex) {
                // An unrelated or malformed release cannot prevent matching the remaining catalog.
            }
        }
        return Optional.ofNullable(found);
    }
}
