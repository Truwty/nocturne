package dev.truwty.nocturne;

import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class StudioLibraryTest {
    @TempDir Path directory;
    @Test void offsetsPersistPerSong() throws Exception {
        var library = new StudioLibrary(directory); library.offset("one", 200); library.offset("two", -400);
        var loaded = new StudioLibrary(directory);
        assertEquals(200, loaded.offset("one")); assertEquals(-400, loaded.offset("two")); assertEquals(0, loaded.offset("three"));
    }
    @Test void rejectsInvalidOffsets() throws Exception {
        var library = new StudioLibrary(directory);
        assertThrows(IllegalArgumentException.class, () -> library.offset("one", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> library.offset("one", 31000));
    }
    @Test void lyricPathsCannotEscapeLibrary() throws Exception {
        var library = new StudioLibrary(directory);
        assertTrue(library.lyricPath("../../elsewhere").normalize().startsWith(directory.resolve("lyrics")));
    }
    @Test void localLrcImportsAndClears() throws Exception {
        var library = new StudioLibrary(directory); Path file = directory.resolve("song.lrc");
        Files.writeString(file, "[00:01.00]夜の歌"); library.importLyrics("song", file);
        assertEquals("夜の歌", library.local("song").lines.getFirst().text());
        library.clearLyrics("song"); assertNull(library.local("song"));
    }
    @Test void invalidLocalFileDoesNotReplaceCorrection() throws Exception {
        var library = new StudioLibrary(directory); library.chooseLyrics("song", "[00:01]Correct");
        assertThrows(java.io.IOException.class, () -> library.chooseLyrics("song", "no timestamps"));
        assertEquals("Correct", library.local("song").lines.getFirst().text());
    }
    @Test void presetsAreIsolatedByPlacementAndDefensivelyCopied() throws Exception {
        var library = new StudioLibrary(directory); var preset = new JsonObject(); preset.addProperty("Editor yaw", 30);
        library.preset("placement:1", "Desk", preset); preset.addProperty("Editor yaw", 180);
        assertEquals(30, library.preset("placement:1", "Desk").get("Editor yaw").getAsInt());
        assertNull(library.preset("placement:2", "Desk"));
        assertEquals(java.util.List.of("Desk"), new StudioLibrary(directory).presets("placement:1"));
    }
    @Test void playerSelectionPersistsAndAutoCanBeRestored() throws Exception {
        var library = new StudioLibrary(directory); library.player("Spotify.exe");
        assertEquals("Spotify.exe", new StudioLibrary(directory).player()); library.player("");
        assertEquals("", new StudioLibrary(directory).player());
    }
    @Test void invalidPresetNamesAreRejected() throws Exception {
        var library = new StudioLibrary(directory);
        assertThrows(IllegalArgumentException.class, () -> library.preset("themes", "", new JsonObject()));
    }
    @Test void damagedLibraryIsPreservedAndBecomesReadOnly() throws Exception {
        Path file = directory.resolve("library.json"); Files.writeString(file, "{broken");
        var library = new StudioLibrary(directory);
        assertFalse(library.problem().isBlank()); assertEquals(0, library.offset("song"));
        assertThrows(java.io.IOException.class, () -> library.player("Spotify.exe"));
        assertEquals("{broken", Files.readString(file));
    }
    @Test void invalidOffsetInLibraryDoesNotPoisonRendering() throws Exception {
        Files.writeString(directory.resolve("library.json"), "{\"offsets\":{\"bad\":\"NaN\"}}");
        var library = new StudioLibrary(directory);
        assertFalse(library.problem().isBlank()); assertEquals(0, library.offset("song"));
    }
}
