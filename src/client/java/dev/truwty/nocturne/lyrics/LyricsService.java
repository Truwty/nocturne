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
import net.minecraft.util.Mth;

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
   private record CachedLyrics(LyricsFinder.Result result,long savedAt) {}
   private final Map<String, CachedLyrics> cache = java.util.Collections.synchronizedMap(
      new java.util.LinkedHashMap<>(64, .75f, true) {
         @Override protected boolean removeEldestEntry(Map.Entry<String, CachedLyrics> entry) { return size() > 128; }
      });
   private volatile long retryLyricsAt;
   private volatile boolean background,lowActivity;
   private volatile long nextPoll;
   public void activity(boolean background,boolean paused){if(background!=this.background||paused!=lowActivity){this.background=background;lowActivity=paused;if(background)cancelArt();else nextArtAttempt=0;nextPoll=0;}}
   private java.util.concurrent.Future<?> lyricRequest;
   private void cancelLookup(){if(lyricRequest!=null){lyricRequest.cancel(true);lyricRequest=null;}}
   private PersistentLyricsCache disk;
   private volatile long playbackRevision;
   private volatile String playbackStatus = "Waiting for playback";
   public long playbackRevision() { return playbackRevision; }
   public String playbackStatus() { return playbackStatus; }
   private synchronized PersistentLyricsCache diskCache() {
      if(disk==null) disk=new PersistentLyricsCache(Studio.directory().resolve("cache/lyrics"));
      return disk;
   }
   private static boolean advertisement(NowPlaying n) {
      String title=n.title().trim().toLowerCase(Locale.ROOT);
      return title.equals("advertisement") || title.equals("advertising") || title.equals("spotify advertisement") || title.equals("ad");
   }
   public synchronized void resynchronize() {
      NowPlaying sample=dev.truwty.nocturne.playback.Playback.sample(system.latest());
      if(sample!=null) { update(sample); playbackRevision++; playbackStatus="Playback resynchronized"; }
   }
   private volatile List<LyricSearch.Candidate> candidates = List.of();
   private volatile String searchStatus = "Search for a title and artist";
   private volatile String searchTrack = "";
   private volatile long searchRevision;
   private long revision;
   private volatile long lookupGeneration;
   public List<LyricSearch.Candidate> candidates() { return candidates; }
   public String searchStatus() { return searchStatus; }
   private java.util.concurrent.Future<?> searchRequest;
   private synchronized void cancelSearch(){searchRevision++;if(searchRequest!=null)searchRequest.cancel(true);searchRequest=null;candidates=List.of();searchTrack="";searchStatus="Search for the current title and artist";}
   public synchronized void search(String query) {
      NowPlaying track = now();
      if (track == null) { searchStatus = "Play a song first"; return; }
      cancelSearch();long request = ++searchRevision;
      searchTrack = track.key(); candidates = List.of(); searchStatus = "Searching LRCLIB...";
      searchRequest=exec.submit(() -> {
         try { var result = LyricSearch.search(query); synchronized(this){if (request == searchRevision) { candidates = result; searchStatus = candidates.isEmpty() ? "No synchronized matches" : "Choose a match to save for this song"; }} }
         catch (InterruptedException ex) { Thread.currentThread().interrupt(); if(request==searchRevision)searchStatus = "Search cancelled"; }
         catch (Exception ex) { if(request==searchRevision)searchStatus = "Search failed: " + ex.getMessage(); NocturneClient.LOG.debug("Manual lyric search failed", ex); }
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
   public void saveEdited(NowPlaying expected,Lyrics document) throws IOException {
      NowPlaying current=now();if(current==null||!current.key().equals(expected.key()))throw new IOException("Song changed; reopen the lyric editor");
      Studio.library().saveDocument(current.key(),document);applyLocal(current,document.manuallyCorrected());
   }
   public synchronized void importSecondary(Path file,boolean romanized) throws IOException {
      var current=now();var primary=lyrics();if(current==null||primary==null)throw new IOException("Load the main lyrics first");
      Studio.library().importSecondary(current.key(),file,romanized);var updated=Studio.library().secondary(current.key(),primary);
      if(demoPlaying())demoLyrics=updated;else lyrics=updated;
   }
   synchronized void applyLocal(NowPlaying track, Lyrics local) {
      local=local.manuallyCorrected();
      NowPlaying demo = this.demoNow;
      if (demo != null && demo.key().equals(track.key())) {
         this.demoLyrics = local;
         return;
      }
      NowPlaying current = this.now;
      if (current != null && current.key().equals(track.key())) {
         cancelLookup();lookupGeneration++; loadingKey = "local:" + (++revision); retryLyricsAt = 0; lyrics = local; lyricsStatus = "Local / selected lyrics";
      }
   }
   private ScheduledFuture<?> task;
   private volatile NowPlaying now;
   private volatile Lyrics lyrics;
   private volatile String lyricsStatus = "";
   private volatile String loadingKey = "";
   private volatile int albumColor;
   public record Artwork(String key,byte[] png) { public Artwork { png=png.clone(); } @Override public byte[] png(){return png.clone();} }
   private volatile Artwork artwork;
   private static final Artwork DEMO_ART=demoArtwork();
   private static Artwork demoArtwork(){
      var image=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);
      for(int y=0;y<128;y++)for(int x=0;x<128;x++){double radius=Math.hypot(x-64,y-64);int color=0xFF110E20;if(radius<46&&radius>20)color=0xFFB878ED;if(radius<36&&radius>26)color=0xFF5E3B89;if(Math.abs(x-y)<2)color=0xFFE7C4FF;image.setRGB(x,y,color);}
      try{var bytes=new java.io.ByteArrayOutputStream();ImageIO.write(image,"png",bytes);return new Artwork("demo",bytes.toByteArray());}catch(IOException ex){throw new ExceptionInInitializerError(ex);}
   }
   public Artwork artwork(){if(demoPlaying())return DEMO_ART;var a=artwork;var track=now();return track!=null&&a!=null&&a.key().equals(track.key())?a:null;}
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

   public synchronized void startDemo() {
      playbackRevision++;
      this.demoLyrics = Lyrics.parse(
         "[00:01.20]Late night, the city's glowing\n[00:04.60]Neon running through my veins\n[00:08.00]We don't stop, we keep on going\n[00:11.40]Dancing in the pouring rain\n[00:14.80]Turn it UP!\n[00:16.60]Feel the bass drop\n[00:18.40]Every heartbeat on the beat\n[00:21.80]Lights out, never stopping\n[00:25.20]This is where we come alive\n[00:28.80]\n[00:32.20]One more time!\n[00:34.60]\n"
      );
      this.demoNow = new NowPlaying("Neon Rain", "Nocturne demo", "", 37000L, 0L, true, System.nanoTime(), "demo");
   }

   public synchronized void stopDemo() {
      playbackRevision++;
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

      this.task = null;nextPoll=0;
      cancelLookup();cancelSearch();cancelArt();
      this.system.stop();
      this.now = null;
      this.loadingKey = ""; lookupGeneration++;
      this.retryLyricsAt = 0;
   }

   private void poll() {
      long nowMs=System.currentTimeMillis();if(nowMs<nextPoll)return;
      NowPlaying previous=this.now;
      long interval=background?3000:previous==null?2000:lowActivity||!previous.playing()?1500:500;
      nextPoll=nowMs+interval;system.activity((int)interval);
      try {
         NowPlaying sample = null;
         if (SystemMediaSource.supported()) this.system.start();
         sample = dev.truwty.nocturne.playback.Playback.sample(this.system.latest());

         this.update(sample);
         if(sample!=null&&!background&&artwork==null&&nowMs>=nextArtAttempt&&(artRequest==null||artRequest.isDone()))fetchArt(sample);
      } catch (Exception e) {
         NocturneClient.LOG.debug("Lyrics poll failed", e);
      }
   }

   synchronized void update(NowPlaying sample) {
      NowPlaying old = this.now;
      if(sample!=null && advertisement(sample)) {
         cancelLookup();cancelSearch();cancelArt();
         this.now=sample; this.lyrics=null; this.loadingKey=""; lookupGeneration++; this.retryLyricsAt=0;
         lyricsStatus="Advertisement: waiting for music"; playbackStatus="Advertisement";
         if(old==null || !old.key().equals(sample.key())) playbackRevision++;
         return;
      }
      String transition=PlaybackSync.transition(old,sample);
      if(!transition.isBlank()) { playbackRevision++;playbackStatus=transition; }
      if (sample == null) {
         cancelLookup();if(artRequest!=null)cancelArt();
         this.now = null; this.lyrics=null;
         playbackStatus="Player disconnected";
         loadingKey = ""; lookupGeneration++;
      } else if (old == null
         || !old.key().equals(sample.key())
         || old.playing() != sample.playing()
         || !old.sessionId().equals(sample.sessionId())
         || old.durationMs() != sample.durationMs()
         || Math.abs(old.positionMs() - sample.progressMs()) >= 350L) {
         this.now = sample;
         if (old == null || !old.key().equals(sample.key()) || (old.durationMs() <= 0 && sample.durationMs() > 0)) {
            this.fetchLyrics(sample);
            if (old == null || !old.key().equals(sample.key())) this.fetchArt(sample);
         }
      }
      var displayed=now();if(searchRequest!=null&&(displayed==null||!displayed.key().equals(searchTrack)))cancelSearch();
      if (sample != null && retryLyricsAt > 0 && System.nanoTime() >= retryLyricsAt) {
         retryLyricsAt = 0;
         fetchLyrics(sample);
      }
   }

   public void refetch() {
      this.exec.execute(() -> {
         NowPlaying n = this.now;
         if (n != null) {
            String key=n.key() + (NocturneClient.SETTINGS.plainLyrics.on() ? "|plain" : "") + "|" + n.durationMs();
            this.cache.remove(key);
            try { diskCache().remove(key); } catch(IOException ex) { NocturneClient.LOG.warn("Could not refresh disk lyric cache",ex); }
            this.fetchLyrics(n);
         }
      });
   }

   private synchronized void fetchLyrics(NowPlaying np) {
      cancelLookup();
      if (now == null || !now.key().equals(np.key()) || now.durationMs()!=np.durationMs()) return;
      try {
         Lyrics local = Studio.library().local(np.key());
         if (local != null && !local.lines.isEmpty()) { applyLocal(np, local); return; }
      } catch (IOException ex) { NocturneClient.LOG.warn("Local lyrics could not be read", ex); }
      boolean plain = NocturneClient.SETTINGS == null || NocturneClient.SETTINGS.plainLyrics.on();
      String key = np.key() + (plain ? "|plain" : "") + "|" + np.durationMs();
      this.loadingKey = key;
      long generation=++lookupGeneration;
      this.retryLyricsAt = 0;
      long maxAge=TimeUnit.DAYS.toMillis(NocturneClient.SETTINGS.cacheDays.i());
      CachedLyrics memory=cache.get(key);
      long cacheNow=System.currentTimeMillis();
      LyricsFinder.Result hit=memory!=null && memory.savedAt()<=cacheNow && cacheNow-memory.savedAt()<=maxAge ? memory.result() : null;
      if(hit==null) {
         cache.remove(key);
         try {
            var stored=diskCache().readEntry(key,maxAge);
            if(stored!=null) {hit=stored.result();cache.put(key,new CachedLyrics(hit,stored.savedAt()));}
         } catch(IOException ex) { NocturneClient.LOG.warn("Lyric disk cache unavailable",ex); }
      }
      if (hit != null) {
         this.lyrics = hit.lyrics();
         if(this.lyrics!=null)try {this.lyrics=Studio.library().secondary(np.key(),this.lyrics);}catch(IOException ex){NocturneClient.LOG.warn("Secondary lyrics unavailable",ex);}
         this.lyricsStatus = hit.status();
      } else {
         this.lyrics = null;
         this.lyricsStatus = "Finding lyrics...";
         lyricRequest=this.exec.submit(() -> this.lookup(np, key, plain, 0, generation));
      }
   }

   private void lookup(NowPlaying np,String key,boolean plain,int attempt,long generation) {
      if(generation!=lookupGeneration || !key.equals(loadingKey))return;
      LyricsFinder finder=new LyricsFinder(); LyricsFinder.Result found=null;
      try {found=finder.find(np,plain);}
      catch(Exception ex) {NocturneClient.LOG.debug("Lyrics lookup failed",ex);LyricsDebug.note("Lyrics lookup failed: "+ex);}
      synchronized(this) {
         if(generation!=lookupGeneration || !key.equals(loadingKey))return;
         if(found==null && !finder.reached && attempt<1) {
            lyricsStatus="Can't reach the lyrics sites, trying again...";
            lyricRequest=exec.schedule(()->lookup(np,key,plain,attempt+1,generation),10,TimeUnit.SECONDS);return;
         }
         if(found==null || (found.lyrics()!=null && found.lyrics().lines.isEmpty()))found=new LyricsFinder.Result(null,"No lyrics found for this song");
         if(found.lyrics()!=null || "Instrumental".equals(found.status())) {
            cache.put(key,new CachedLyrics(found,System.currentTimeMillis()));
            try {diskCache().write(key,found);}catch(IOException ex){NocturneClient.LOG.warn("Could not persist lyrics",ex);}
         } else retryLyricsAt=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
         if(found.lyrics()!=null) {
            try {found=new LyricsFinder.Result(Studio.library().secondary(np.key(),found.lyrics()),found.status());}
            catch(IOException ex) {NocturneClient.LOG.warn("Secondary lyrics unavailable",ex);}
         }
         lyrics=found.lyrics();lyricsStatus=found.status();
      }
   }

   private ArtworkLoader artLoader;
   public synchronized void invalidateDownloadCaches() {
      cache.clear();
      if (artLoader != null) artLoader.clearMemory();
   }
   private synchronized ArtworkLoader artLoader(){if(artLoader==null)artLoader=new ArtworkLoader(HTTP,exec);return artLoader;}
   private java.util.concurrent.CompletableFuture<dev.truwty.nocturne.performance.ArtworkCache.Entry> artRequest;
   private volatile long nextArtAttempt;
   public String artworkCacheStatus(){return artLoader().cached()+" / 24 covers · "+artLoader().cachedBytes()/1024+" / 4096 KiB";}
   private synchronized void cancelArt(){artRequest=null;if(artLoader!=null)artLoader.cancel();artKey="";artwork=null;albumColor=0;}
   private synchronized void fetchArt(NowPlaying np) {
      if(background)return;
      this.albumColor=0;this.artwork=null;this.artKey=np.key();nextArtAttempt=System.currentTimeMillis()+30000;
      var request=artLoader().load(np);artRequest=request;
      request.whenComplete((entry,error)->{synchronized(this){
         if(request!=artRequest)return;
         if(error!=null){if(!(error instanceof java.util.concurrent.CancellationException))NocturneClient.LOG.debug("Album artwork unavailable",error);return;}
         if(entry!=null){albumColor=entry.color();artwork=new Artwork(np.key(),entry.png());}
      }});
   }

   static int mainColor(BufferedImage img) {
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

   private static String enc(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8);
   }

}
