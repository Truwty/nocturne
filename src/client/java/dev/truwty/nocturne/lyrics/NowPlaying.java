package dev.truwty.nocturne.lyrics;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public record NowPlaying(
   String title, String artist, String album, long durationMs, long progressMs, boolean playing, long sampledAtNanos, String source, String artUrl, String sessionId
) {
   public NowPlaying(String title,String artist,String album,long durationMs,long progressMs,boolean playing,long sampledAtNanos,String source,String artUrl) {
      this(title,artist,album,durationMs,progressMs,playing,sampledAtNanos,source,artUrl,source);
   }
   public NowPlaying(String title, String artist, String album, long durationMs, long progressMs, boolean playing, long sampledAtNanos, String source) {
      this(title, artist, album, durationMs, progressMs, playing, sampledAtNanos, source, "");
   }

   public long positionMs() {
      if (!this.playing) {
         return this.progressMs;
      }

      long elapsed = (System.nanoTime() - this.sampledAtNanos) / 1000000L;
      return Math.min(this.durationMs > 0L ? this.durationMs : Long.MAX_VALUE, this.progressMs + elapsed);
   }

   public String key() {
      return (this.artist + " - " + this.title).toLowerCase(java.util.Locale.ROOT);
   }
}
