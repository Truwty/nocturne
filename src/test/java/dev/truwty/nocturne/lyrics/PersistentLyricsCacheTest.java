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
 @Test void corruptQuarantineIsBoundedAndUnrelatedFilesArePreserved() throws Exception {
  var cache=new PersistentLyricsCache(dir,()->1000);Files.writeString(dir.resolve("user-notes.txt"),"keep");
  for(int i=0;i<24;i++) {
   String key="bad-"+i;Path file=dir.resolve(dev.truwty.nocturne.StudioLibrary.trackId(key)+".json");Files.writeString(file,"broken");assertNull(cache.read(key,10000));
  }
  try(var paths=Files.list(dir)){assertEquals(16,paths.filter(p->p.getFileName().toString().contains(".corrupt-")).count());}
  assertEquals("keep",Files.readString(dir.resolve("user-notes.txt")));
 }
 @Test void invalidWordIntervalsAreRejectedBeforeRendering() throws Exception {
  var cache=new PersistentLyricsCache(dir,()->1000);
  cache.write("track",new LyricsFinder.Result(Lyrics.parse("[00:01]One word\n[00:04]Two"),"Synced"));
  Path file=dir.resolve(dev.truwty.nocturne.StudioLibrary.trackId("track")+".json");
  var json=com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
  json.getAsJsonArray("lines").get(0).getAsJsonObject().getAsJsonArray("words").get(0).getAsJsonObject().addProperty("end",Long.MAX_VALUE);
  Files.writeString(file,json.toString());assertNull(cache.read("track",10000));
 }
}
