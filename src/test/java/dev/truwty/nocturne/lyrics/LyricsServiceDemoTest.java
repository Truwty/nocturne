package dev.truwty.nocturne.lyrics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class LyricsServiceDemoTest {
    @Test void demoCorrectionDoesNotReplaceTheDesktopSongLyrics() {
        LyricsService service = LyricsService.INSTANCE;
        service.stopDemo();
        Lyrics original = service.lyrics();
        try {
            service.startDemo();
            service.applyLocal(service.now(), Lyrics.parse("[00:01]Corrected demo"));
            assertEquals("Corrected demo", service.lyrics().lines.getFirst().text());
        } finally { service.stopDemo(); }
        assertSame(original, service.lyrics());
    }

    @Test void aStaleCorrectionDoesNotReplaceTheCurrentDemo() {
        LyricsService service = LyricsService.INSTANCE;
        try {
            service.startDemo();
            Lyrics original = service.lyrics();
            service.applyLocal(new NowPlaying("Other song", "Other artist", "", 10000, 0, true, System.nanoTime(), "test"), Lyrics.parse("[00:01]Wrong song"));
            assertSame(original, service.lyrics());
        } finally { service.stopDemo(); }
    }
}
