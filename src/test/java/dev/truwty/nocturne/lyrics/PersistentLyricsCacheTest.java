package dev.truwty.nocturne.lyrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class PersistentLyricsCacheTest {
 @TempDir Path dir;
 @Test void reusesWordTimingsAcrossCacheInstances() throws Exception {
  var lyrics=Lyrics.parse("[00:01]<00:01.00>Original <00:01.50>fixture\n[00:04]Second line");
  new PersistentLyricsCache(dir,()->1000).write("track",new LyricsFinder.Result(lyrics,"Synced lyrics"));
  var read=new PersistentLyricsCache(dir,()->1100).read("track",10000);
  assertNotNull(read);assertEquals(lyrics.lines,read.lyrics().lines);assertFalse(read.lyrics().estimated);
 }
 @Test void expiresAndManualRefreshRemovesEntry() throws Exception {
  var clock=new AtomicLong(1000);var cache=new PersistentLyricsCache(dir,clock::get);
  var value=new LyricsFinder.Result(Lyrics.fromPlain("One\nTwo",10000),"Estimated");
  cache.write("a",value);clock.set(2001);assertNull(cache.read("a",1000));
  cache.write("b",value);cache.remove("b");assertNull(cache.read("b",10000));
 }
 @Test void preservesEstimatedFlagAndQuarantinesCorruption() throws Exception {
  var cache=new PersistentLyricsCache(dir,()->1000);
  cache.write("a",new LyricsFinder.Result(Lyrics.fromPlain("One\nTwo",10000),"Estimated"));assertTrue(cache.read("a",10000).lyrics().estimated);
  Path file;try(var paths=Files.list(dir)){file=paths.filter(p->p.toString().endsWith(".json")).findFirst().orElseThrow();}Files.writeString(file,"broken");
  assertNull(cache.read("a",10000));try(var stream=Files.list(dir)){assertTrue(stream.anyMatch(p->p.toString().contains(".corrupt-")));}
 }
}
