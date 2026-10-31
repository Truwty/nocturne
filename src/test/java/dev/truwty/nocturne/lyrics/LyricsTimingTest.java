package dev.truwty.nocturne.lyrics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LyricsTimingTest {
    @Test void selectsLinesAtTimestampBoundaries() {
        Lyrics lyrics = Lyrics.parse("[00:01.20]First line\n[00:04.60]Second line");
        assertEquals(-1, lyrics.indexAt(1199));
        assertEquals(0, lyrics.indexAt(1200));
        assertEquals(0, lyrics.indexAt(4599));
        assertEquals(1, lyrics.indexAt(4600));
    }

    @Test void retainsExplicitWordTimestamps() {
        Lyrics lyrics = Lyrics.parse("[00:01.00]<00:01.00>One <00:01.70>two\n[00:03.00]Next");
        assertEquals("One two", lyrics.lines.getFirst().text());
        assertEquals(1000, lyrics.lines.getFirst().words().get(0).start());
        assertEquals(1700, lyrics.lines.getFirst().words().get(1).start());
    }

    @Test void chunksWithoutLosingWordsOrTimingOrder() {
        Lyrics lyrics = Lyrics.parse("[00:01.00]One two three four five six\n[00:08.00]Next").chunked(2);
        assertEquals(4, lyrics.lines.size());
        assertEquals("One two", lyrics.lines.getFirst().text());
        assertEquals("five six", lyrics.lines.get(2).text());
        for (int i = 1; i < lyrics.lines.size(); i++) {
            assertTrue(lyrics.lines.get(i).start() >= lyrics.lines.get(i - 1).end());
        }
    }

    @Test void emptyLyricsHaveNoActiveLine() {
        Lyrics lyrics = Lyrics.parse("[ar:Artist]\nNo synchronized lyrics");
        assertTrue(lyrics.lines.isEmpty());
        assertEquals(-1, lyrics.indexAt(5000));
    }
}
