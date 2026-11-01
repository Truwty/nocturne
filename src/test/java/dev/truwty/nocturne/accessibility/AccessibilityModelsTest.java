package dev.truwty.nocturne.accessibility;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;

import dev.truwty.nocturne.Studio;
import dev.truwty.nocturne.setting.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class AccessibilityModelsTest {
    @Test
    void screenshotWinsEveryOtherContext() {
        assertEquals(GameplayContext.SCREENSHOTS, GameplayContext.choose(true, true, true));
    }

    @Test
    void menusWinCombat() {
        assertEquals(GameplayContext.MENUS, GameplayContext.choose(false, true, true));
    }

    @Test
    void combatAndExplorationAreDistinct() {
        assertEquals(GameplayContext.COMBAT, GameplayContext.choose(false, false, true));
        assertEquals(GameplayContext.EXPLORATION, GameplayContext.choose(false, false, false));
    }

    @Test
    void screenRulesMatchOnlySelectedKinds() {
        var p = new ScreenPolicy(true, true, true, false, false, false, Set.of("mod.Selected"));
        assertTrue(p.hides("Chat", true, false, false, false, false));
        assertTrue(p.hides("Inventory", false, true, false, false, false));
        assertTrue(p.hides("mod.Selected", false, false, false, false, false));
        assertFalse(p.hides("mod.Other", false, false, false, false, false));
        assertFalse(p.hides("Pause", false, false, true, false, false));
    }

    @Test
    void debugAndF1RulesWorkWithoutAnOpenScreen() {
        var p = new ScreenPolicy(false, false, true, false, false, true, Set.of());
        assertTrue(p.hides(null, false, false, false, true, false));
        assertTrue(p.hides(null, false, false, false, false, true));
        assertFalse(p.hides(null, false, false, false, false, false));
    }

    @Test
    void otherScreenToggleDoesNotOverrideSpecificUncheckedKinds() {
        var p = new ScreenPolicy(false, false, false, false, true, false, Set.of());
        assertTrue(p.hides("Other", false, false, false, false, false));
        assertFalse(p.hides("Chat", true, false, false, false, false));
    }

    @Test
    void screenClassesAreValidatedTrimmedAndDefensive() {
        var names = new HashSet<>(Set.of(" mod.Screen$Nested "));
        var checked = ScreenPolicy.validate(names);
        names.clear();
        assertEquals(Set.of("mod.Screen$Nested"), checked);
        assertThrows(IllegalArgumentException.class, () -> ScreenPolicy.validate(List.of("mod.*")));
        assertThrows(
                IllegalArgumentException.class,
                () -> ScreenPolicy.validate(List.of("mod.Bad\nScreen")));
        assertThrows(UnsupportedOperationException.class, () -> checked.add("Other"));
    }

    @Test
    void tooManyRulesAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ScreenPolicy.validate(Collections.nCopies(33, "a.Screen")));
    }

    @Test
    void edgeFallbackHasExitHysteresis() {
        assertTrue(EdgePlacement.outside(1.1, 0, false, false));
        assertFalse(EdgePlacement.outside(.95, 0, false, false));
        assertTrue(EdgePlacement.outside(.95, 0, false, true));
        assertFalse(EdgePlacement.outside(.8, 0, false, true));
    }

    @Test
    void behindAndInvalidProjectionAlwaysUseFallback() {
        assertTrue(EdgePlacement.outside(0, 0, true, false));
        assertTrue(EdgePlacement.outside(Double.NaN, 0, false, false));
    }

    @Test
    void fallbackFitsEveryEdgeAndSmallViewports() {
        for (int w : new int[] {160, 320, 854})
            for (double x : new double[] {-4, 0, 4})
                for (double y : new double[] {-4, 0, 4}) {
                    int bw = Math.min(w - 16, 270);
                    var p = EdgePlacement.locate(x, y, false, w, 240, bw, 49);
                    assertTrue(p.x() >= 8 && p.x() + bw <= w - 8);
                    assertTrue(p.y() >= 8 && p.y() + 49 <= 232);
                }
    }

    @Test
    void behindFallbackIsCenteredAtBottom() {
        var p = EdgePlacement.locate(0, 0, true, 320, 240, 200, 40);
        assertEquals(60, p.x());
        assertEquals(192, p.y());
        assertEquals("Behind you", p.direction());
    }

    @Test
    void contextSchemaExcludesPresetAndGlobalSafetyLock() {
        assertFalse(Studio.accepts("context", "Photosensitivity-friendly mode"));
        assertFalse(Studio.accepts("context", "Preset"));
        assertFalse(Studio.accepts("context", "Hide lyrics in chat"));
        assertTrue(Studio.accepts("context", "Camera movement"));
        assertTrue(Studio.accepts("context", "Lyric backplate"));
    }

    @Test
    void renderLayerRestoresGlobalValues() {
        var flag = new BoolSetting("Animated lyric text", false);
        var number = new NumberSetting("Size", 100, 20, 300, 1, "");
        var json = new JsonObject();
        json.addProperty(flag.name, true);
        json.addProperty(number.name, 180);
        try (var layer = SettingLayer.apply(List.of(flag, number), json, n -> true)) {
            assertTrue(flag.on());
            assertEquals(180, number.get());
        }
        assertFalse(flag.on());
        assertEquals(100, number.get());
    }

    @Test
    void nestedLayersRestoreOuterThenGlobal() {
        var flag = new BoolSetting("Flash effects", false);
        var on = new JsonObject();
        on.addProperty(flag.name, true);
        var off = new JsonObject();
        off.addProperty(flag.name, false);
        try (var outer = SettingLayer.apply(List.of(flag), on, n -> true)) {
            try (var inner = SettingLayer.apply(List.of(flag), off, n -> true)) {
                assertFalse(flag.on());
            }
            assertTrue(flag.on());
        }
        assertFalse(flag.on());
    }

    @Test
    void failingLayerRollsBackAllEarlierChanges() {
        var flag = new BoolSetting("Flash effects", false);
        var number = new NumberSetting("Size", 100, 20, 300, 1, "");
        var json = new JsonObject();
        json.addProperty(flag.name, true);
        json.addProperty(number.name, "NaN");
        assertThrows(
                IllegalArgumentException.class,
                () -> SettingLayer.apply(List.of(flag, number), json, n -> true));
        assertFalse(flag.on());
        assertEquals(100, number.get());
    }

    @Test
    void disallowedFieldsCannotOverrideSafety() {
        var locked = new BoolSetting("Photosensitivity-friendly mode", true);
        var json = new JsonObject();
        json.addProperty(locked.name, false);
        try (var layer =
                SettingLayer.apply(List.of(locked), json, n -> Studio.accepts("context", n))) {
            assertTrue(locked.on());
        }
        assertTrue(locked.on());
    }

    @Test
    void profileJsonIsNeverMutatedByScope() {
        var flag = new BoolSetting("Flash effects", false);
        var json = new JsonObject();
        json.addProperty(flag.name, true);
        String before = json.toString();
        try (var layer = SettingLayer.apply(List.of(flag), json, n -> true)) {
            assertTrue(flag.on());
        }
        assertEquals(before, json.toString());
    }
}
