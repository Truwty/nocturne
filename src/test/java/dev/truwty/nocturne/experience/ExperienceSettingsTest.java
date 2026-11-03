package dev.truwty.nocturne.experience;

import static dev.truwty.nocturne.experience.ExperienceSettings.Option.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.*;

class ExperienceSettingsTest {
    @TempDir Path directory;

    @Test
    void allNewPresentationEffectsAreOffByDefault() {
        var settings = new ExperienceSettings(directory.resolve("settings.json"));
        for (var option : ExperienceSettings.Option.values())
            assertFalse(settings.on(option), option.name());
    }

    @Test
    void discordUsesNocturneByDefaultAndPreservesOptionalOverrides() throws Exception {
        var file = directory.resolve("discord.json");
        var settings = new ExperienceSettings(file);
        assertEquals(ExperienceSettings.DEFAULT_DISCORD_APPLICATION_ID, settings.discordAppId());
        assertFalse(settings.on(RPC));
        settings.discordAppId("123456789012345678");
        settings.save();
        var reloaded = new ExperienceSettings(file);
        reloaded.load();
        assertEquals("123456789012345678", reloaded.discordAppId());
        reloaded.discordAppId("");
        reloaded.save();
        settings.load();
        assertEquals(ExperienceSettings.DEFAULT_DISCORD_APPLICATION_ID, settings.discordAppId());
        var legacy = settings.snapshot();
        legacy.remove("discordAppId");
        settings.restore(legacy);
        assertEquals(ExperienceSettings.DEFAULT_DISCORD_APPLICATION_ID, settings.discordAppId());
        settings.discordAppId("123456789012345678");
        settings.reset();
        assertEquals(ExperienceSettings.DEFAULT_DISCORD_APPLICATION_ID, settings.discordAppId());
        assertThrows(IllegalArgumentException.class, () -> settings.discordAppId("not-a-token"));
    }

    @Test
    void choicesPetNameAndBudgetSurviveRestart() throws Exception {
        var file = directory.resolve("settings.json");
        var settings = new ExperienceSettings(file);
        settings.set(WORLD_LOOK, 2);
        settings.set(HEAD, 3);
        settings.petName("Luna 星");
        settings.limit(144);
        settings.save();
        var reloaded = new ExperienceSettings(file);
        reloaded.load();
        assertEquals(2, reloaded.get(WORLD_LOOK));
        assertEquals(3, reloaded.get(HEAD));
        assertEquals("Luna 星", reloaded.petName());
        assertEquals(144, reloaded.limit());
    }

    @Test
    void invalidOptionsCannotPartiallyReplaceSettings() {
        var settings = new ExperienceSettings(directory.resolve("settings.json"));
        settings.set(HEAD, 2);
        var json = settings.snapshot();
        json.getAsJsonObject("options").addProperty("WORLD_LOOK", "nonexistent");
        assertThrows(IllegalArgumentException.class, () -> settings.restore(json));
        assertEquals(2, settings.get(HEAD));
        assertEquals(0, settings.get(WORLD_LOOK));
    }

    @Test
    void oldHudLayoutsMigrateWithoutLosingPlacement() {
        var layout = dev.truwty.nocturne.gui.HudLayout.defaults(false).json();
        var rows = layout.getAsJsonObject("widgets");
        rows.remove("Session");
        rows.remove("Container");
        rows.getAsJsonObject("Karaoke").addProperty("x", .72);
        var migrated = dev.truwty.nocturne.gui.HudLayout.parse(layout);
        assertEquals(.72, migrated.node("Karaoke").x());
        assertNotNull(migrated.node("Session"));
        assertNotNull(migrated.node("Container"));
    }

    @Test
    void rejectsControlCharactersAndFractionalVersions() {
        var settings = new ExperienceSettings(directory.resolve("settings.json"));
        assertThrows(IllegalArgumentException.class, () -> settings.petName("Bad\nname"));
        var json = settings.snapshot();
        json.addProperty("schema", 1.5);
        assertThrows(IllegalArgumentException.class, () -> settings.restore(json));
    }

    @Test
    void corruptFileIsPreservedAndFutureFileCannotBeOverwritten() throws Exception {
        Path bad = directory.resolve("bad.json");
        Files.writeString(bad, "{broken");
        assertThrows(IOException.class, () -> new ExperienceSettings(bad).load());
        try (var files = Files.list(directory)) {
            assertTrue(
                    files.anyMatch(
                            p -> p.getFileName().toString().startsWith("bad.json.invalid-")));
        }
        var file = directory.resolve("future.json");
        Files.writeString(file, "{\"schema\":2}");
        var settings = new ExperienceSettings(file);
        assertThrows(IOException.class, settings::load);
        settings.set(HEAD, 1);
        assertThrows(IOException.class, settings::save);
        assertEquals("{\"schema\":2}", Files.readString(file));
    }
}
