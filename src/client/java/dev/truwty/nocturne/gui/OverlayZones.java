package dev.truwty.nocturne.gui;

import com.google.gson.*;
import dev.truwty.nocturne.*;
import java.io.IOException;
import java.util.*;

public final class OverlayZones {
  private static List<HudLayout.Box> zones;

  private OverlayZones() {}

  public static List<HudLayout.Box> current() {
    if (zones == null) {
      try {
        zones = parse(Studio.library().preset("hudExclusions", "Gameplay"));
      } catch (RuntimeException ex) {
        NocturneClient.LOG.warn("Invalid HUD exclusions ignored", ex);
        zones = List.of();
      }
    }
    return zones;
  }

  public static List<HudLayout.Box> parse(JsonObject json) {
    if (json == null) return List.of();
    if (!json.has("zones")
        || !json.get("zones").isJsonArray()
        || json.getAsJsonArray("zones").size() > 8)
      throw new IllegalArgumentException("Invalid HUD exclusion list");
    var result = new ArrayList<HudLayout.Box>();
    for (var entry : json.getAsJsonArray("zones")) {
      var row = entry.getAsJsonObject();
      double x = row.get("x").getAsDouble(), y = row.get("y").getAsDouble();
      double w = row.get("width").getAsDouble(), h = row.get("height").getAsDouble();
      if (!Double.isFinite(x + y + w + h)
          || x < 0
          || y < 0
          || w <= 0
          || h <= 0
          || x + w > 1
          || y + h > 1) throw new IllegalArgumentException("Zone must fit inside the screen");
      result.add(new HudLayout.Box(x, y, w, h));
    }
    return List.copyOf(result);
  }

  public static void save(List<HudLayout.Box> values) throws IOException {
    var json = new JsonObject();
    var rows = new JsonArray();
    for (var zone : values) {
      var row = new JsonObject();
      row.addProperty("x", zone.x());
      row.addProperty("y", zone.y());
      row.addProperty("width", zone.width());
      row.addProperty("height", zone.height());
      rows.add(row);
    }
    json.add("zones", rows);
    var checked = parse(json);
    Studio.library().preset("hudExclusions", "Gameplay", json);
    zones = checked;
  }
}
