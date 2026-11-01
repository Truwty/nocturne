package dev.truwty.nocturne.lyrics;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.Studio;
import java.util.List;
import java.io.IOException;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;

@Environment(EnvType.CLIENT)
public final class LyricsService {
   public static final LyricsService INSTANCE = new LyricsService();
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6L)).followRedirects(Redirect.NORMAL).build();
   public final SystemMediaSource system = new SystemMediaSource();
   private final ScheduledExecutorService exec = Executors.newScheduledThreadPool(3, r -> {
      Thread t = new Thread(r, "Nocturne lyrics");
      t.setDaemon(true);
      return t;
   });
   private final ScheduledExecutorService polling = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread thread = new Thread(r, "nocturne-playback-poll"); thread.setDaemon(true); return thread;
   });
   private final Map<String, LyricsFinder.Result> cache = java.util.Collections.synchronizedMap(
      new java.util.LinkedHashMap<>(64, .75f, true) {
         @Override protected boolean removeEldestEntry(Map.Entry<String, LyricsFinder.Result> entry) { return size() > 128; }
      });
   private volatile long retryLyricsAt;
   private volatile List<LyricSearch.Candidate> candidates = List.of();
   private volatile String searchStatus = "Search for a title and artist";
   private volatile String searchTrack = "";
   private volatile long searchRevision;
   private long revision;
   public List<LyricSearch.Candidate> candidates() { return candidates; }
   public String searchStatus() { return searchStatus; }
   public void search(String query) {
      NowPlaying track = now();
      if (track == null) { searchStatus = "Play a song first"; return; }
      long request = ++searchRevision;
      searchTrack = track.key(); candidates = List.of(); searchStatus = "Searching LRCLIB...";
      exec.execute(() -> {
         try { var result = LyricSearch.search(query); if (request == searchRevision) { candidates = result; searchStatus = candidates.isEmpty() ? "No synchronized matches" : "Choose a match to save for this song"; } }
         catch (InterruptedException ex) { Thread.currentThread().interrupt(); searchStatus = "Search cancelled"; }
         catch (Exception ex) { searchStatus = "Search failed: " + ex.getMessage(); NocturneClient.LOG.debug("Manual lyric search failed", ex); }
      });
   }
   public void choose(LyricSearch.Candidate candidate) throws IOException {
      NowPlaying track = now();
      if (track == null || !track.key().equals(searchTrack)) throw new IOException("Song changed; search again for the current song");
      Studio.library().chooseLyrics(track.key(), candidate.lrc());
      applyLocal(track, Lyrics.parse(candidate.lrc()));
   }
   public void importLocal(Path path) throws IOException {
      NowPlaying track = now(); if (track == null) throw new IOException("Play a song first");
      Studio.library().importLyrics(track.key(), path);
      applyLocal(track, Studio.library().local(track.key()));
   }
   public void clearLocal() throws IOException {
      NowPlaying track = now(); if (track == null) throw new IOException("Play a song first");
      Studio.library().clearLyrics(track.key());
      if (demoPlaying()) demoLyrics = Lyrics.parse(DEMO_LRC); else refetch();
   }
   synchronized void applyLocal(NowPlaying track, Lyrics local) {
      NowPlaying demo = this.demoNow;
      if (demo != null && demo.key().equals(track.key())) {
         this.demoLyrics = local;
         return;
      }
      NowPlaying current = this.now;
      if (current != null && current.key().equals(track.key())) {
         loadingKey = "local:" + (++revision); retryLyricsAt = 0; lyrics = local; lyricsStatus = "Local / selected lyrics";
      }
   }
   private ScheduledFuture<?> task;
   private volatile NowPlaying now;
   private volatile Lyrics lyrics;
   private volatile String lyricsStatus = "";
   private volatile String loadingKey = "";
   private volatile int albumColor;
   private volatile String artKey = "";
   private volatile NowPlaying demoNow;
   private volatile Lyrics demoLyrics;
   private static final String DEMO_LRC = "[00:01.20]Late night, the city's glowing\n[00:04.60]Neon running through my veins\n[00:08.00]We don't stop, we keep on going\n[00:11.40]Dancing in the pouring rain\n[00:14.80]Turn it UP!\n[00:16.60]Feel the bass drop\n[00:18.40]Every heartbeat on the beat\n[00:21.80]Lights out, never stopping\n[00:25.20]This is where we come alive\n[00:28.80]\n[00:32.20]One more time!\n[00:34.60]\n";

   private LyricsService() {
   }

   public NowPlaying now() {
      NowPlaying d = this.demoNow;
      if (d != null) {
         if (d.positionMs() < d.durationMs()) {
            return d;
         }

         this.demoNow = null;
      }

      return this.now;
   }

   public int albumColor() {
      return this.demoPlaying() ? -28729 : this.albumColor;
   }

   public Lyrics lyrics() {
      return this.demoNow != null ? this.demoLyrics : this.lyrics;
   }

   public boolean demoPlaying() {
      NowPlaying d = this.demoNow;
      return d != null && d.positionMs() < d.durationMs();
   }

   public void startDemo() {
      this.demoLyrics = Lyrics.parse(
         "[00:01.20]Late night, the city's glowing\n[00:04.60]Neon running through my veins\n[00:08.00]We don't stop, we keep on going\n[00:11.40]Dancing in the pouring rain\n[00:14.80]Turn it UP!\n[00:16.60]Feel the bass drop\n[00:18.40]Every heartbeat on the beat\n[00:21.80]Lights out, never stopping\n[00:25.20]This is where we come alive\n[00:28.80]\n[00:32.20]One more time!\n[00:34.60]\n"
      );
      this.demoNow = new NowPlaying("Neon Rain", "Nocturne demo", "", 37000L, 0L, true, System.nanoTime(), "demo");
   }

   public void stopDemo() {
      this.demoNow = null;
   }

   public String lyricsStatus() {
      return this.demoPlaying() ? "Demo song" : this.lyricsStatus;
   }

   public synchronized void start() {
      if (this.task == null) {
         this.task = this.polling.scheduleWithFixedDelay(this::poll, 0L, 500L, TimeUnit.MILLISECONDS);
      }
   }

   public synchronized void stop() {
      if (this.task != null) {
         this.task.cancel(false);
      }

      this.task = null;
      this.system.stop();
      this.now = null;
      this.loadingKey = "";
      this.retryLyricsAt = 0;
   }

   private void poll() {
      try {
         NowPlaying sample = null;
         if (SystemMediaSource.supported()) {
            this.system.start();
            sample = this.system.latest();
         }

         this.update(sample);
      } catch (Exception e) {
         NocturneClient.LOG.debug("Lyrics poll failed", e);
      }
   }

   synchronized void update(NowPlaying sample) {
      NowPlaying old = this.now;
      if (sample == null) {
         this.now = null;
         loadingKey = "";
      } else if (old == null
         || !old.key().equals(sample.key())
         || old.playing() != sample.playing()
         || old.durationMs() != sample.durationMs()
         || Math.abs(old.positionMs() - sample.progressMs()) >= 350L) {
         this.now = sample;
         if (old == null || !old.key().equals(sample.key()) || (old.durationMs() <= 0 && sample.durationMs() > 0)) {
            this.fetchLyrics(sample);
            if (old == null || !old.key().equals(sample.key())) this.fetchArt(sample);
         }
      }
      if (sample != null && retryLyricsAt > 0 && System.nanoTime() >= retryLyricsAt) {
         retryLyricsAt = 0;
         fetchLyrics(sample);
      }
   }

   public void refetch() {
      this.exec.execute(() -> {
         NowPlaying n = this.now;
         if (n != null) {
            this.cache.remove(n.key() + (NocturneClient.SETTINGS.plainLyrics.on() ? "|plain" : "") + "|" + n.durationMs());
            this.fetchLyrics(n);
         }
      });
   }

   private synchronized void fetchLyrics(NowPlaying np) {
      if (now == null || !now.key().equals(np.key())) return;
      try {
         Lyrics local = Studio.library().local(np.key());
         if (local != null && !local.lines.isEmpty()) { applyLocal(np, local); return; }
      } catch (IOException ex) { NocturneClient.LOG.warn("Local lyrics could not be read", ex); }
      boolean plain = NocturneClient.SETTINGS == null || NocturneClient.SETTINGS.plainLyrics.on();
      String key = np.key() + (plain ? "|plain" : "") + "|" + np.durationMs();
      this.loadingKey = key;
      this.retryLyricsAt = 0;
      LyricsFinder.Result hit = this.cache.get(key);
      if (hit != null) {
         this.lyrics = hit.lyrics();
         this.lyricsStatus = hit.status();
      } else {
         this.lyrics = null;
         this.lyricsStatus = "Finding lyrics...";
         this.exec.execute(() -> this.lookup(np, key, plain, 0));
      }
   }

   private void lookup(NowPlaying np, String key, boolean plain, int attempt) {
      if (key.equals(this.loadingKey)) {
         LyricsFinder finder = new LyricsFinder();
         LyricsFinder.Result found = null;

         try {
            found = finder.find(np, plain);
         } catch (Exception e) {
            NocturneClient.LOG.debug("Lyrics lookup failed", e);
            LyricsDebug.note("Lyrics lookup failed: " + e);
         }

         if (!key.equals(this.loadingKey)) return;
         if (found == null && !finder.reached && attempt < 1) {
            this.lyricsStatus = "Can't reach the lyrics sites, trying again...";
            this.exec.schedule(() -> this.lookup(np, key, plain, attempt + 1), 10L << attempt, TimeUnit.SECONDS);
         } else {
            if (found == null) {
               found = new LyricsFinder.Result(null, "No lyrics found for this song");
            }

            if (found.lyrics() != null && found.lyrics().lines.isEmpty()) {
               found = new LyricsFinder.Result(null, "No lyrics found for this song");
            }

            if (!key.equals(this.loadingKey)) return;
            if (found.lyrics() != null || "Instrumental".equals(found.status())) this.cache.put(key, found);
            else this.retryLyricsAt = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            if (key.equals(this.loadingKey)) {
               this.lyrics = found.lyrics();
               this.lyricsStatus = found.status();
            }
         }
      }
   }

   private void fetchArt(NowPlaying np) {
      this.albumColor = 0;
      String given = np.artUrl();
      this.artKey = np.key();
      String key = np.key();
      this.exec.execute(() -> {
         int c = 0;

         try {
            String url = given != null && given.startsWith("https://") ? given : itunesArt(np);
            if (url == null || !key.equals(this.artKey)) {
               return;
            }

            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8L)).GET().build();
            byte[] bytes = HTTP.send(req, BodyHandlers.ofByteArray()).body();
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img != null) {
               c = mainColor(img);
            }
         } catch (Throwable e) {
            NocturneClient.LOG.debug("Album colour failed", e);
            LyricsDebug.note("Album colour failed: " + e);
         }

         if (key.equals(this.artKey)) {
            this.albumColor = c;
         }
      });
   }

   private static String itunesArt(NowPlaying np) {
      String artist = np.artist() == null ? "" : np.artist().split(",")[0].trim();
      String title = np.title();
      if (title != null && !title.isBlank()) {
         JsonElement res = get("https://itunes.apple.com/search?media=music&entity=song&limit=5&term=" + enc((artist + " " + title).trim()));
         if (res != null && res.isJsonObject() && res.getAsJsonObject().has("results")) {
            String want = title.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");

            for (JsonElement e : res.getAsJsonObject().getAsJsonArray("results")) {
               if (e.isJsonObject()) {
                  JsonObject o = e.getAsJsonObject();
                  if (o.has("artworkUrl100") && o.get("artworkUrl100").isJsonPrimitive()) {
                     String art = o.get("artworkUrl100").getAsString().replace("100x100", "300x300");
                     if (art.startsWith("https://")) {
                        String name = o.has("trackName") && o.get("trackName").isJsonPrimitive() ? o.get("trackName").getAsString() : "";
                        String n = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
                        if (!n.isEmpty() && (n.contains(want) || want.contains(n))) {
                           return art;
                        }
                     }
                  }
               }
            }

            return null;
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   private static int mainColor(BufferedImage img) {
      double r = 0.0;
      double g = 0.0;
      double b = 0.0;
      double wsum = 0.0;
      int step = Math.max(1, Math.min(img.getWidth(), img.getHeight()) / 32);

      for (int y = 0; y < img.getHeight(); y += step) {
         for (int x = 0; x < img.getWidth(); x += step) {
            int p = img.getRGB(x, y);
            float[] hsv = hsv(p >> 16 & 0xFF, p >> 8 & 0xFF, p & 0xFF);
            double w = 0.05 + hsv[1] * hsv[1] * hsv[2];
            r += (p >> 16 & 0xFF) * w;
            g += (p >> 8 & 0xFF) * w;
            b += (p & 0xFF) * w;
            wsum += w;
         }
      }

      if (wsum <= 0.0) {
         return 0;
      }

      float[] hsv = hsv((int)(r / wsum), (int)(g / wsum), (int)(b / wsum));
      float sat = hsv[1] < 0.12F ? hsv[1] : Math.max(0.45F, Math.min(0.8F, hsv[1] * 1.3F));
      return 0xFF000000 | Mth.hsvToRgb(hsv[0], sat, Math.max(0.88F, hsv[2])) & 16777215;
   }

   private static float[] hsv(int r, int g, int b) {
      int max = Math.max(r, Math.max(g, b));
      int min = Math.min(r, Math.min(g, b));
      float d = max - min;
      float h = 0.0F;
      if (d > 0.0F) {
         if (max == r) {
            h = (g - b) / d % 6.0F;
         } else if (max == g) {
            h = (b - r) / d + 2.0F;
         } else {
            h = (r - g) / d + 4.0F;
         }

         h /= 6.0F;
         if (h < 0.0F) {
            h++;
         }
      }

      return new float[]{h, max == 0 ? 0.0F : d / max, max / 255.0F};
   }

   private static JsonElement get(String url) {
      try {
         HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8L))
            .header("User-Agent", "Nocturne Lyrics (Minecraft client mod)")
            .GET()
            .build();
         HttpResponse<String> res = HTTP.send(req, BodyHandlers.ofString());
         return res.statusCode() != 200 ? null : JsonParser.parseString(res.body());
      } catch (Exception e) {
         return null;
      }
   }

   private static String enc(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8);
   }

}
