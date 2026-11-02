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
    @Test void skipsBrokenRowsAndRanksKnownRecordingFirst() {
        var rows=LyricSearch.parse("[null,{}, {\"trackName\":\"Song\",\"artistName\":\"Artist\",\"duration\":\"bad\",\"syncedLyrics\":\"[00:01]Bad\"}, {\"trackName\":\"Song\",\"artistName\":\"Artist\",\"duration\":120,\"syncedLyrics\":\"[00:01]Good\"}]");
        assertEquals(1,rows.size());
        var track=new NowPlaying("Song","Artist","",120000,0,true,System.nanoTime(),"System");
        var wrong=new LyricSearch.Candidate("Song (Live)","Artist",120,"[00:01]Other");
        assertEquals(rows.getFirst(),LyricSearch.rank(java.util.List.of(wrong,rows.getFirst()),track).getFirst());
        assertTrue(LyricSearch.confidence(wrong,track).startsWith("Different"));
    }
}
