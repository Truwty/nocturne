package dev.truwty.nocturne.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class HudAvoidanceTest {
  @Test
  void preservesUnobstructedPlacement() {
    var box = new HudLayout.Box(100, 100, 80, 30);
    assertEquals(box, HudAvoidance.solve(box, 800, 600, List.of(new HudLayout.Box(0, 0, 30, 30))));
  }

  @Test
  void movesAboveHotbarWithoutLeavingSafeArea() {
    var box = new HudLayout.Box(300, 540, 200, 40);
    var result = HudAvoidance.solve(box, 800, 600, List.of(new HudLayout.Box(290, 535, 220, 65)));
    assertTrue(result.y() + result.height() <= 531);
    assertTrue(result.x() >= 8);
    assertTrue(result.x() + result.width() <= 792);
  }

  @Test
  void handlesCrowdedSmallScreensDeterministically() {
    var box = new HudLayout.Box(8, 8, 180, 80);
    var blocked = List.of(new HudLayout.Box(0, 0, 200, 100));
    var result = HudAvoidance.solve(box, 200, 100, blocked);
    assertEquals(result, HudAvoidance.solve(box, 200, 100, blocked));
    assertTrue(Double.isFinite(result.y()));
  }

  @Test
  void rejectsExclusionZonesThatEscapeTheScreen() {
    var json =
        com.google.gson.JsonParser.parseString(
                "{\"zones\":[{\"x\":0.9,\"y\":0,\"width\":0.2,\"height\":0.2}]}")
            .getAsJsonObject();
    assertThrows(IllegalArgumentException.class, () -> OverlayZones.parse(json));
    var valid =
        com.google.gson.JsonParser.parseString(
                "{\"zones\":[{\"x\":0.7,\"y\":0,\"width\":0.2,\"height\":0.2}]}")
            .getAsJsonObject();
    assertEquals(1, OverlayZones.parse(valid).size());
  }
}
