package dev.truwty.nocturne;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;

import dev.truwty.nocturne.gui.*;
import dev.truwty.nocturne.setting.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

class InterfaceModelsTest {
    @TempDir Path directory;

    private static JsonObject state(int number) {
        var json = new JsonObject();
        json.addProperty("value", number);
        return json;
    }

    @Test
    void historySnapshotsAreDefensiveAndRedoClearsAfterNewEdit() {
        var initial = state(1);
        var history = new SnapshotHistory(initial);
        initial.addProperty("value", 99);
        history.change(state(2));
        assertTrue(history.undo());
        assertEquals(1, history.value().get("value").getAsInt());
        assertTrue(history.redo());
        assertEquals(2, history.value().get("value").getAsInt());
        history.undo();
        history.change(state(3));
        assertFalse(history.redo());
        var copy = history.value();
        copy.addProperty("value", 4);
        assertEquals(3, history.value().get("value").getAsInt());
    }

    @Test
    void historyCoalescesIdenticalStatesAndBoundsMemory() {
        var history = new SnapshotHistory(state(0));
        for (int i = 1; i <= 90; i++) history.change(state(i));
        history.change(state(90));
        for (int i = 0; i < 60; i++) assertTrue(history.undo());
        assertFalse(history.undo());
        assertEquals(30, history.value().get("value").getAsInt());
    }

    @Test
    void pickerRoundTripsAlphaAndRgb() {
        for (int color :
                new int[] {0x00123456, 0x80336699, 0xffffffff, 0xff000000, 0xffff00ff, 0x32abcdef})
            assertEquals(color, PickerColor.from(color).argb());
        assertEquals(0xff336699, PickerColor.hex("#336699"));
        assertEquals(0x80336699, PickerColor.hex("#80336699"));
    }

    @Test
    void colorPickerRejectsMalformedHexAndNonfiniteChannels() {
        assertThrows(IllegalArgumentException.class, () -> PickerColor.hex("336699"));
        assertThrows(IllegalArgumentException.class, () -> PickerColor.hex("#12345G"));
        assertThrows(IllegalArgumentException.class, () -> new PickerColor(Double.NaN, 1, 1, 1));
        assertEquals(0xffff0000, new PickerColor(1, 1, 1, 1).argb());
    }

    @Test
    void recentColorsAreUniqueBoundedAndSurviveRestart() throws Exception {
        var library = new StudioLibrary(directory);
        for (int i = 0; i < 20; i++) library.rememberColor(0xff000000 | i);
        library.rememberColor(0xff000013);
        var colors = new StudioLibrary(directory).recentColors();
        assertEquals(12, colors.size());
        assertEquals(0xff000013, colors.getFirst());
        assertEquals(12, new HashSet<>(colors).size());
    }

    @Test
    void favoritesPersistPerPresetCategory() throws Exception {
        var library = new StudioLibrary(directory);
        library.favorite("themes", "Midnight", true);
        assertTrue(new StudioLibrary(directory).favorite("themes", "Midnight"));
        assertFalse(library.favorite("placement:1", "Midnight"));
        library.favorite("themes", "Midnight", false);
        assertFalse(library.favorite("themes", "Midnight"));
    }

    @Test
    void gameAndMenuDefaultsAreIndependent() {
        var game = HudLayout.defaults(false);
        var menu = HudLayout.defaults(true);
        assertTrue(game.node("Karaoke").visible());
        assertFalse(menu.node("Karaoke").visible());
        var moved = game.with("Karaoke", new HudLayout.Node(.1, .9, 1, true));
        assertEquals(.5, game.node("Karaoke").x());
        assertFalse(menu.node("Karaoke").visible());
        assertEquals(.1, moved.node("Karaoke").x());
    }

    @Test
    void hudLayoutRoundTripRetainsScaleVisibilityAndPositions() {
        var value =
                HudLayout.defaults(false).with("Secondary", new HudLayout.Node(.3, .4, 1.7, false));
        var parsed = HudLayout.parse(value.json());
        assertEquals(value.node("Secondary"), parsed.node("Secondary"));
    }

    @Test
    void invalidHudImportsAreRejected() {
        var json = HudLayout.defaults(false).json();
        json.getAsJsonObject("widgets").getAsJsonObject("Karaoke").addProperty("x", 1.1);
        assertThrows(IllegalArgumentException.class, () -> HudLayout.parse(json));
        assertThrows(
                IllegalArgumentException.class, () -> new HudLayout.Node(.5, .5, Double.NaN, true));
        assertThrows(IllegalArgumentException.class, () -> new HudLayout.Node(.5, .5, 3, true));
    }

    @Test
    void safeMarginsFitEveryScaleAndSmallViewport() {
        for (int sw : new int[] {320, 640, 1280})
            for (double scale : new double[] {.5, 1, 2}) {
                var box = new HudLayout.Node(1, 0, scale, true).box(sw, 240, 370, 90);
                assertTrue(box.x() >= 8);
                assertTrue(box.y() >= 8);
                assertTrue(box.x() + box.width() <= sw - 8.0);
                assertTrue(box.y() + box.height() <= 232.0);
                assertEquals(370.0 / 90, box.width() / box.height(), .001);
            }
    }

    @Test
    void dragClampsOutsideViewport() {
        var layout = HudLayout.defaults(false);
        var snap = layout.move("Countdown", -500, 900, 640, 480, 120, 14, false, Map.of());
        var box = snap.node().box(640, 480, 120, 14);
        assertEquals(8, box.x());
        assertEquals(472, box.y() + box.height());
    }

    @Test
    void snapsToCenterAndNeighborEdges() {
        var layout = HudLayout.defaults(false);
        var centered = layout.move("Countdown", 318, 239, 640, 480, 120, 14, true, Map.of());
        assertEquals(320, centered.guideX());
        assertEquals(240, centered.guideY());
        assertEquals(.5, centered.node().x());
        var neighbor = new HudLayout.Box(100, 100, 80, 20);
        var snap =
                layout.move(
                        "Countdown", 242, 160, 640, 480, 120, 14, true, Map.of("Other", neighbor));
        assertEquals(180, snap.guideX());
        assertEquals(240, snap.node().x() * 640);
    }

    @Test
    void layoutsPersistSeparately() throws Exception {
        var library = new StudioLibrary(directory);
        library.preset("hudLayouts", "Gameplay", HudLayout.defaults(false).json());
        library.preset("hudLayouts", "Menu", HudLayout.defaults(true).json());
        var loaded = new StudioLibrary(directory);
        assertTrue(
                HudLayout.parse(loaded.preset("hudLayouts", "Gameplay")).node("Karaoke").visible());
        assertFalse(HudLayout.parse(loaded.preset("hudLayouts", "Menu")).node("Karaoke").visible());
    }

    @Test
    void presetEnvelopeRejectsCrossCategoryAndOversizeInput() {
        var json = PresetCodec.encode("hudLayouts", "Fixture", HudLayout.defaults(false).json());
        assertTrue(
                HudLayout.parse(PresetCodec.decode("hudLayouts", json)).node("Karaoke").visible());
        assertThrows(IllegalArgumentException.class, () -> PresetCodec.decode("themes", json));
        assertThrows(
                IllegalArgumentException.class,
                () -> PresetCodec.decode("hudLayouts", "x".repeat(65537)));
    }

    @Test
    void settingSearchIncludesCategoriesAndRanges() {
        List<Setting<?>> settings =
                List.of(
                        new NumberSetting("Distance", 3, 1, 10, 1, " blocks"),
                        new BoolSetting("Reduced motion", false),
                        new ColorSetting("UI accent", 0xff112233));
        assertEquals("Placement", SettingsCatalog.category(settings.getFirst()));
        assertEquals(1, SettingsCatalog.search(settings, "All", "accent").size());
        assertEquals(1, SettingsCatalog.search(settings, "All", "blocks").size());
        assertEquals(1, SettingsCatalog.search(settings, "Interface", "").size());
        assertTrue(SettingsCatalog.search(settings, "Appearance", "Distance").isEmpty());
    }

    @Test
    void controllerAcceptDoesNotRepeatWhenHeld() {
        var repeater = new ControllerRepeater();
        var held = Set.of(ControllerRepeater.Action.ACCEPT);
        assertEquals(List.of(ControllerRepeater.Action.ACCEPT), repeater.sample(held, 0));
        assertTrue(repeater.sample(held, 1000).isEmpty());
        repeater.sample(Set.of(), 1001);
        assertEquals(1, repeater.sample(held, 1002).size());
    }

    @Test
    void controllerNavigationRepeatsWithDelayAndResetsOnDisconnect() {
        var repeater = new ControllerRepeater();
        var held = Set.of(ControllerRepeater.Action.NEXT);
        assertEquals(1, repeater.sample(held, 0).size());
        assertTrue(repeater.sample(held, 349).isEmpty());
        assertEquals(1, repeater.sample(held, 350).size());
        assertTrue(repeater.sample(held, 400).isEmpty());
        assertEquals(1, repeater.sample(held, 470).size());
        repeater.reset();
        assertEquals(1, repeater.sample(held, 471).size());
    }

    @Test
    void malformedSavedPresetEntriesDoNotCrashTheBrowser() throws Exception {
        java.nio.file.Files.writeString(
                directory.resolve("library.json"),
                "{\"themes\":{\"Bad\":4},\"favorites:themes\":{\"Bad\":{\"favorite\":[]}}}");
        var library = new StudioLibrary(directory);
        assertNull(library.preset("themes", "Bad"));
        assertTrue(library.presets("themes").isEmpty());
        assertFalse(library.favorite("themes", "Bad"));
    }
}
