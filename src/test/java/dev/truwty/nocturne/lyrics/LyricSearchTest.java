package dev.truwty.nocturne.lyrics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LyricSearchTest {
    @Test void onlySynchronizedCandidatesCanBeSelected() {
        var result = LyricSearch.parse("""
            [{"trackName":"Song","artistName":"Artist","duration":120,"syncedLyrics":"[00:01]Line"},
             {"trackName":"Plain","artistName":"Artist","duration":120,"syncedLyrics":null},
             {"trackName":"Invalid","artistName":"Artist","duration":120,"syncedLyrics":"No timestamps"}]
            """);
        assertEquals(1, result.size()); assertEquals("Song", result.getFirst().title());
    }
    @Test void emptyCatalogIsAValidResult() { assertTrue(LyricSearch.parse("[]").isEmpty()); }
}
