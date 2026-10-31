package dev.truwty.nocturne;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricsTest {
    @Test void parsesFractionsMultipleStampsAndUnicode() {
        var lyrics = Lyrics.parse("[ar:Fixture]\n[00:01.5][00:03.050]静かな街\n[01:02.25]Window light", "", false, "test");
        assertEquals(3, lyrics.lines().size());
        assertEquals(1.5, lyrics.lines().getFirst().seconds());
        assertEquals(3.05, lyrics.lines().get(1).seconds());
        assertEquals("静かな街", lyrics.lines().getFirst().text());
        assertEquals(62.25, lyrics.lines().getLast().seconds());
    }
    @Test void seeksInBothDirectionsAndDoesNotShowFirstLineBeforeItStarts() {
        var lyrics = Lyrics.parse("[00:10]Window light\n[00:20]City quiet\n[00:30]One more block", "", false, "test");
        assertEquals(-1, lyrics.activeIndex(9.99));
        assertEquals(0, lyrics.activeIndex(10));
        assertEquals(2, lyrics.activeIndex(100));
        assertEquals(1, lyrics.activeIndex(21));
        assertEquals(0, lyrics.activeIndex(11));
    }
    @Test void appliesGlobalPositiveOffsetEarlierAndClampsNegativeTime() {
        var lyrics = Lyrics.parse("[00:00.2]Before\n[00:10]After\n[offset:+500]", "", false, "test");
        assertEquals(0, lyrics.lines().getFirst().seconds());
        assertEquals(9.5, lyrics.lines().getLast().seconds());
    }
    @Test void sortsOutOfOrderLinesAndKeepsEmptyInstrumentalBreaks() {
        var lyrics = Lyrics.parse("[00:20]Second\n[00:10]First\n[00:15]", "", false, "test");
        assertEquals("First", lyrics.lines().getFirst().text());
        assertEquals("", lyrics.lines().get(1).text());
        assertEquals(1, lyrics.activeIndex(17));
    }
    @Test void enhancedLrcUsesActualWordTimestamps() {
        var lyrics = Lyrics.parse("[00:10]<00:10>Window <00:12>light\n[00:15]Next", "", false, "test");
        assertEquals("Window light", lyrics.lines().getFirst().text());
        assertEquals(2, lyrics.lines().getFirst().words().size());
        assertEquals(0, lyrics.wordProgress(0, 9));
        assertTrue(lyrics.wordProgress(0, 11) > 0);
        assertEquals(1, lyrics.wordProgress(0, 15));
    }
    @Test void plainLyricsAreNeverAssignedFabricatedTimestamps() {
        var lyrics = Lyrics.parse("", "Window light\nCity quiet", false, "test");
        assertFalse(lyrics.synced());
        assertEquals(-1, lyrics.activeIndex(60));
        assertEquals(LyricsService.Status.UNSYNCED, LyricsService.result("test", lyrics).status());
    }
    @Test void rejectsUnboundedFiles() {
        assertThrows(IllegalArgumentException.class, () -> Lyrics.parse("a".repeat(1_000_001), "", false, "test"));
    }
    @Test void sourcePathsCannotEscapeLyricsDirectory() {
        var track = new LyricsTrack("../track:<test>", "../../artist", "", 180);
        assertFalse(track.localFileName().contains("/"));
        assertFalse(track.localFileName().contains("\\"));
        assertEquals(64, track.cacheKey().length());
    }
}
