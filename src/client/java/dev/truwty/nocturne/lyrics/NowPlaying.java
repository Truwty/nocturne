package dev.truwty.nocturne.lyrics;

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
      return positionAt(System.nanoTime());
   }

   public long positionAt(long nanos) {
      long elapsed = playing ? Math.max(0, (nanos - sampledAtNanos) / 1_000_000L) : 0;
      long progress = Math.max(0, progressMs);
      long position = progress > Long.MAX_VALUE - elapsed ? Long.MAX_VALUE : progress + elapsed;
      return Math.min(durationMs > 0 ? durationMs : Long.MAX_VALUE, position);
   }

   public String key() {
      return (this.artist + " - " + this.title).toLowerCase(java.util.Locale.ROOT);
   }
}
