package dev.truwty.nocturne.lyrics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PlaybackSyncTest {
 private NowPlaying sample(String title,long position,boolean playing,String session) {return new NowPlaying(title,"Artist","",180000,position,playing,System.nanoTime(),"System","",session);}
 @Test void detectsSeekingPauseResumeAndDeviceChange() {
  var n=sample("Song",20000,true,"app|speakers");
  assertEquals("Seek synchronized",PlaybackSync.transition(n,sample("Song",80000,true,"app|speakers")));
  assertEquals("Playback paused",PlaybackSync.transition(n,sample("Song",20000,false,"app|speakers")));
  assertEquals("Playback resumed",PlaybackSync.transition(sample("Song",20000,false,"app|speakers"),n));
  assertEquals("Player/device changed",PlaybackSync.transition(n,sample("Song",20000,true,"app|headphones")));
 }
 @Test void handlesAdReturnDisconnectAndStablePlayback() {
  var n=sample("Song",20000,true,"app");assertEquals("Track changed",PlaybackSync.transition(n,sample("Advertisement",0,true,"app")));
  assertEquals("Track changed",PlaybackSync.transition(sample("Advertisement",0,true,"app"),n));
  assertEquals("Player disconnected",PlaybackSync.transition(n,null));assertEquals("",PlaybackSync.transition(n,sample("Song",20000,true,"app")));
 }
 @Test void delayedSamplesAreComparedAtTheSameInstant() {
  long now=System.nanoTime();
  var before=new NowPlaying("Song","Artist","",180000,20000,true,now-10_000_000_000L,"System");
  var after=new NowPlaying("Song","Artist","",180000,21000,true,now-9_000_000_000L,"System");
  assertEquals("",PlaybackSync.transition(before,after));
 }
 @Test void positionClampsInvalidAndFutureProgress() {
  var n=new NowPlaying("Song","Artist","",180000,-400,false,1000,"System");
  assertEquals(0,n.positionAt(2000));
  n=new NowPlaying("Song","Artist","",180000,200000,false,1000,"System");assertEquals(180000,n.positionAt(2000));
  n=new NowPlaying("Song","Artist","",180000,1000,true,10_000_000_000L,"System");assertEquals(1000,n.positionAt(0));
 }
}
