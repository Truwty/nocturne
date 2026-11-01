package dev.truwty.nocturne.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

final class LyricsFinderTest {
    private NowPlaying track() { return new NowPlaying("Neon Rain (Official Audio)", "Night Test", "", 180000, 15000, true, System.nanoTime(), "System"); }
    private JsonObject record(String field, String lyrics) {
        var o=new JsonObject(); o.addProperty("trackName","Neon Rain");o.addProperty("artistName","Night Test");o.addProperty("duration",180);o.addProperty(field,lyrics);return o;
    }
    @Test void extractsBrowserArtistWhenMetadataIsMissing() {
        var n=new NowPlaying("Night Test - Neon Rain - YouTube Music","","",180000,0,true,System.nanoTime(),"System");
        var first=LyricsFinder.queries(n).getFirst();assertEquals("Night Test",first.artist());assertEquals("Neon Rain",first.title());
    }
    @Test void preservesLiveVersionIntentWhileNormalizingRemastersAndFeatures() {
        var live=new NowPlaying("Neon Rain (Live)","Night Test feat. Guest","",180000,0,true,System.nanoTime(),"System");
        assertTrue(LyricsFinder.queries(live).stream().allMatch(q->q.title().toLowerCase().contains("live")));
        var remaster=new NowPlaying("Neon Rain (2025 Remaster)","Night Test feat. Guest","",180000,0,true,System.nanoTime(),"System");
        assertTrue(LyricsFinder.queries(remaster).stream().anyMatch(q->q.title().equals("Neon Rain")&&q.artist().equals("Night Test")));
    }
    @Test void findsNormalizedTitleAfterExactMetadataMisses() {
        var finder=new LyricsFinder(url->{
            if(url.contains("/search?track_name=Neon+Rain&")) { var a=new JsonArray();a.add(record("syncedLyrics","[00:01]Original fixture line"));return a; }
            return null;
        });
        var result=finder.find(track(),true);
        assertNotNull(result);assertEquals("Synced lyrics",result.status());assertEquals(1,result.lyrics().lines.size());
    }
    @Test void plainFallbackDoesNotWaitForAnotherProvider() {
        AtomicInteger otherProvider=new AtomicInteger();
        var finder=new LyricsFinder(url->{
            if(url.contains("music.163.com"))otherProvider.incrementAndGet();
            if(url.contains("/api/get?"))return record("plainLyrics","Original fixture line\nAnother original line");
            return null;
        });
        var result=finder.find(track(),true);
        assertNotNull(result);assertEquals("Lyrics (timing estimated)",result.status());assertFalse(result.lyrics().lines.isEmpty());assertEquals(0,otherProvider.get());
    }
    @Test void instrumentalDoesNotFallBackToPlainText() {
        var finder=new LyricsFinder(url->{
            if(url.contains("/api/get?")) { var o=record("plainLyrics","This should not be displayed");o.addProperty("instrumental",true);return o; }
            return null;
        });
        var result=finder.find(track(),true);
        assertEquals("Instrumental",result.status());assertNull(result.lyrics());
    }
    @Test void sharedArtistWordDoesNotSelectTheWrongRecording() {
        var finder=new LyricsFinder(url->{var a=new JsonArray();var o=record("syncedLyrics","[00:01]Wrong fixture");o.addProperty("artistName","Night Other");a.add(o);return a;});
        assertNull(finder.find(track(),false));
    }
    @Test void doesNotAcceptADifferentArtist() {
        var finder=new LyricsFinder(url->{var a=new JsonArray();var o=record("syncedLyrics","[00:01]Wrong fixture");o.addProperty("artistName","Unrelated Person");a.add(o);return a;});
        assertNull(finder.find(track(),false));
    }
    @Test @EnabledIfEnvironmentVariable(named="NOCTURNE_LIVE_LYRICS",matches="true")
    void resolvesRealDesktopMetadataFromPublicProvider() {
        var result=new LyricsFinder().find(new NowPlaying("Blinding Lights","The Weeknd","After Hours",200000,35000,true,System.nanoTime(),"Spotify app"),true);
        assertNotNull(result,"Public provider must resolve a known track");assertNotNull(result.lyrics());assertFalse(result.lyrics().lines.isEmpty());
    }
}
