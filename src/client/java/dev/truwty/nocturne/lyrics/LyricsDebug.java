package dev.truwty.nocturne.lyrics;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.LyricsSettings;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public final class LyricsDebug {
   public static final int OK = 0;
   public static final int WARN = 1;
   public static final int BAD = 2;
   public static final int INFO = 3;
   static long lastRender;
   static boolean hud;
   static boolean raining;
   static String placement = "?";
   static int line = -1;
   static int lines;
   static int worldPieces;
   static int behindWalls;
   static long pos;
   static int hudRecorded;
   static int hudDrawn;
   static int hudSteep;
   static int hudOff;
   private static int wp;
   private static int bw;
   private static final Deque<String> ERRORS = new ArrayDeque<>();

   private LyricsDebug() {
   }

   static void frame() {
      lastRender = System.nanoTime();
      worldPieces = wp;
      behindWalls = bw;
      wp = 0;
      bw = 0;
   }

   static void worldPiece() {
      wp++;
   }

   static void behindWall() {
      bw++;
   }

   public static synchronized void note(String what) {
      String t = String.format(Locale.ROOT, "%tT %s", System.currentTimeMillis(), what);
      if (ERRORS.isEmpty() || !ERRORS.peekLast().endsWith(what)) {
         ERRORS.addLast(t);

         while (ERRORS.size() > 4) {
            ERRORS.removeFirst();
         }
      }
   }

   private static synchronized List<String> errors() {
      return new ArrayList<>(ERRORS);
   }

   private static String mmss(long ms) {
      long s = Math.max(0L, ms) / 1000L;
      return s / 60L + ":" + (s % 60L < 10L ? "0" : "") + s % 60L;
   }

   public static List<LyricsDebug.Row> rows() {
      List<LyricsDebug.Row> out = new ArrayList<>();
      LyricsSettings mod = NocturneClient.SETTINGS;
      LyricsService svc = LyricsService.INSTANCE;
      if (mod == null) {
         out.add(new LyricsDebug.Row(2, "Likely cause", "Settings didn't load"));
         return out;
      }

      out.add(new LyricsDebug.Row(INFO,"Performance",dev.truwty.nocturne.performance.RenderPerformance.timing()));
      out.add(new LyricsDebug.Row(INFO,"Geometry",dev.truwty.nocturne.performance.RenderPerformance.geometry()));
      NowPlaying np = svc.now();
      Lyrics ly = svc.lyrics();
      String status = svc.lyricsStatus();
      String src = "Automatic desktop detection";
      boolean rendering = System.nanoTime() - lastRender < 1000000000L;
      int lvl = 2;
      String sys = svc.system.status();
      String cause;
      if (!mod.isEnabled()) {
         cause = "Lyrics are switched off (Lyrics: ON on the main page)";
      } else if (np == null) {
         if (!SystemMediaSource.supported()) {
            cause = "Automatic detection requires Windows, macOS or Linux";
         } else if (sys.contains("playerctl")) {
            cause = "Linux needs playerctl installed for the system player";
         } else if (sys.contains("antivirus") || sys.contains("keeps getting stopped")) {
            cause = "The Windows media helper keeps stopping. Check security software, then Rescan desktop players.";
         } else if (sys.startsWith("Couldn't start")) {
            cause = "The system media helper couldn't start: " + sys;
         } else {
            cause = "No music found: start a song in Spotify or your player (" + src + ")";
         }
      } else if (ly == null) {
         if (status.startsWith("Finding")) {
            cause = "Still looking up the lyrics...";
            lvl = 1;
         } else if (status.startsWith("Can't reach")) {
            cause = "Can't reach the lyrics sites: check your internet / firewall";
         } else if (status.startsWith("Instrumental")) {
            cause = "This song is instrumental (no lyrics)";
            lvl = 1;
         } else {
            cause = "No lyrics for this song on LRCLIB or NetEase" + (mod.plainLyrics.on() ? "" : " (try \"Unsynced lyrics fallback\")");
         }
      } else if (!np.playing()) {
         cause = "The music is paused";
         lvl = 1;
      } else if (!rendering) {
         cause = "The world isn't being drawn (in a menu or loading?)";
      } else if (line < 0 && !ly.lines.isEmpty() && pos < ly.lines.get(0).start()) {
         cause = "Intro: the first line comes at " + mmss(ly.lines.get(0).start());
         lvl = 1;
      } else if (hud && hudRecorded > 0 && hudDrawn == 0 && behindWalls > 0) {
         cause = "The lyrics are behind blocks (move, or turn on \"Show through walls\")";
      } else if (hud && hudRecorded > 0 && hudDrawn == 0 && hudOff > 0) {
         cause = "The lyrics are off screen or behind you: turn around or use Follow view";
      } else if (!hud && worldPieces == 0 && line >= 0) {
         cause = "Between lines (instrumental part)";
         lvl = 1;
      } else if (ly.estimated) {
         cause = "Plain lyrics: the timing is guessed, use \"Timing offset\" to line it up";
         lvl = 1;

      } else {
         cause = "Everything looks fine";
         lvl = 0;
      }

      out.add(new LyricsDebug.Row(lvl, "Likely cause", cause));
      out.add(new LyricsDebug.Row(mod.isEnabled() ? 0 : 2, "Lyrics", mod.isEnabled() ? "ON" : "OFF"));
      out.add(new LyricsDebug.Row(3, "Source", src + " · no login"));
      out.add(new LyricsDebug.Row(3, "System player", SystemMediaSource.supported() ? sys : "not on this OS"));
      if (np == null) {
         out.add(new LyricsDebug.Row(2, "Now playing", "nothing"));
      } else {
         out.add(new LyricsDebug.Row(0, "Now playing", np.title() + " · " + np.artist()));
         out.add(
            new LyricsDebug.Row(
               np.playing() ? 0 : 1,
               "Player",
               np.source() + " · " + (np.playing() ? "playing" : "paused") + " · " + mmss(np.positionMs()) + " / " + mmss(np.durationMs())
            )
         );
      }

      out.add(new LyricsDebug.Row(ly != null ? (ly.estimated ? 1 : 0) : (np == null ? 3 : 2), "Lyrics found", status.isEmpty() ? "-" : status));
      if (ly != null) {
         long off = Math.round(mod.offset.get());
         out.add(new LyricsDebug.Row(3, "Timing", "line " + (line + 1) + " / " + lines + " · at " + mmss(pos) + (off != 0L ? " · offset " + off + " ms" : "")));
      }

      boolean pack = ShaderCompat.shaderPackOn();
      out.add(
         new LyricsDebug.Row(
            3,
            "Shaders",
            (pack ? "shader pack ON" : "no shader pack")
               + " · drawing "
               + (hud ? "on the HUD" : "in the world")
               + " (Shader support: "
               + mod.shaderMode.options.get(mod.shaderMode.get())
               + ")"
         )
      );
      out.add(new LyricsDebug.Row(rendering ? 0 : 1, "Placement", placement));
      if (hud) {
         int lv = hudRecorded > 0 && hudDrawn == 0 ? 2 : 0;
         out.add(
            new LyricsDebug.Row(
               lv,
               "Drawn",
               hudDrawn
                  + " of "
                  + hudRecorded
                  + " pieces · "
                  + hudSteep
                  + " faded (steep angle) · "
                  + hudOff
                  + " off screen · "
                  + behindWalls
                  + " spots behind blocks"
            )
         );
      } else {
         out.add(new LyricsDebug.Row(3, "Drawn", worldPieces + " pieces in the world"));
      }

      long rainAge = (System.nanoTime() - LyricsRain.lastFrame()) / 1000000L;
      String rain = !mod.albumRain.on()
         ? "off"
         : (
            !raining
               ? "on (not raining)"
               : (
                  rainAge < 2000L
                     ? "tinting rain (" + Math.round(LyricsRain.strength() * 100.0F) + "%)"
                     : "rain hook isn't running (another mod changes the rain?)"
               )
         );
      out.add(new LyricsDebug.Row(mod.albumRain.on() && raining && rainAge >= 2000L ? 1 : 3, "Album colour rain", rain));
      int album = svc.albumColor();
      out.add(new LyricsDebug.Row(3, "Album colour", album == 0 ? "none (no cover found)" : String.format(Locale.ROOT, "#%06X", album & 16777215)));

      for (String e : errors()) {
         out.add(new LyricsDebug.Row(1, "Recent problem", e));
      }

      return out;
   }

   public static String report() {
      StringBuilder b = new StringBuilder("Nocturne Lyrics debug\n");
      b.append("Mod version: ")
         .append(FabricLoader.getInstance().getModContainer("nocturne").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"))
         .append(" · OS: ")
         .append(System.getProperty("os.name"))
         .append(" · Java ")
         .append(System.getProperty("java.version"))
         .append('\n');

      for (LyricsDebug.Row r : rows()) {
         String tag = switch (r.level()) {
            case 0 -> "[ok]  ";
            case 1 -> "[!]   ";
            case 2 -> "[X]   ";
            default -> "      ";
         };
         b.append(tag).append(r.label()).append(": ").append(r.value()).append('\n');
      }

      return b.toString();
   }

   @Environment(EnvType.CLIENT)
   public record Row(int level, String label, String value) {
   }
}
