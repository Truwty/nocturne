package dev.truwty.nocturne.lyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.truwty.nocturne.NocturneClient;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class SystemMediaSource {
   private volatile Process process;
   private Thread reader;
   private volatile NowPlaying latest;
   private volatile String status = "Not started";
   private long startedAt;
   private long retryAt;
   private int quickDeaths;
   private boolean gaveUp;
   private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
   private static final boolean WINDOWS = OS.startsWith("windows");
   private static final boolean MAC = OS.contains("mac") || OS.contains("darwin");
   private static final boolean LINUX = !WINDOWS && !MAC && (OS.contains("linux") || OS.contains("bsd") || OS.contains("nux"));
   private Thread poller;
   private volatile boolean polling;
   private volatile int pollGen;
   private static final String MAC_SPOTIFY = String.join(
      "\n",
      "tell application \"Spotify\"",
      "if player state is stopped then return \"\"",
      "set t to current track",
      "return (name of t) & tab & (artist of t) & tab & (album of t) & tab & (duration of t) & tab & ((player position * 1000) as integer) & tab & (player state as string) & tab & (artwork url of t)",
      "end tell"
   );
   private static final String MAC_MUSIC = String.join(
      "\n",
      "tell application \"Music\"",
      "if player state is stopped then return \"\"",
      "set t to current track",
      "return (name of t) & tab & (artist of t) & tab & (album of t) & tab & (((duration of t) * 1000) as integer) & tab & ((player position * 1000) as integer) & tab & (player state as string) & tab & \"\"",
      "end tell"
   );

   public static boolean supported() {
      return WINDOWS || MAC || LINUX;
   }

   private synchronized void startPolling() {
      if (!this.polling) {
         this.polling = true;
         int gen = ++this.pollGen;
         this.status = "Listening to system media";
         this.poller = new Thread(() -> {
            while (this.polling && gen == this.pollGen) {
               try {
                  this.latest = MAC ? this.pollMac() : this.pollLinux();
                  if (this.latest != null) {
                     this.status = "Listening to " + this.latest.source();
                  } else if (!this.status.startsWith("Linux") && !this.status.startsWith("Mac")) {
                     this.status = "Nothing playing";
                  }
               } catch (Exception e) {
                  NocturneClient.LOG.debug("System media poll failed", e);
               }

               try {
                  Thread.sleep(700L);
               } catch (InterruptedException e) {
                  return;
               }
            }
         }, "Nocturne system media");
         this.poller.setDaemon(true);
         this.poller.start();
      }
   }

   private static String run(String... cmd) throws IOException {
      Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();

      try {
         if (!p.waitFor(2L, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return null;
         } else {
            return p.exitValue() != 0 ? null : new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
         }
      } catch (InterruptedException e) {
         p.destroyForcibly();
         Thread.currentThread().interrupt();
         return null;
      }
   }

   private static long num(String s) {
      try {
         return Math.round(Double.parseDouble(s.trim().replace(',', '.')));
      } catch (Exception e) {
         return 0L;
      }
   }

   private NowPlaying pollMac() throws IOException {
      String[][] apps = new String[][]{{"Spotify", MAC_SPOTIFY, "Spotify app"}, {"Music", MAC_MUSIC, "Apple Music"}};
      NowPlaying paused = null;

      for (String[] app : apps) {
         if (run("pgrep", "-x", app[0]) != null) {
            String out = run("osascript", "-e", app[1]);
            if (out != null && !out.isEmpty()) {
               String[] f = out.split("\t", -1);
               if (f.length >= 6 && !f[0].isBlank()) {
                  boolean playing = f[5].trim().equalsIgnoreCase("playing");
                  NowPlaying np = new NowPlaying(f[0], f[1], f[2], num(f[3]), num(f[4]), playing, System.nanoTime(), app[2], f.length > 6 ? f[6].trim() : "");
                  if (playing) {
                     return np;
                  }

                  if (paused == null) {
                     paused = np;
                  }
               }
            }
         }
      }

      return paused;
   }

   private NowPlaying pollLinux() {
      String out;
      try {
         out = run(
            "playerctl",
            "-p",
            "spotify,%any",
            "metadata",
            "--format",
            "{{status}}\t{{xesam:title}}\t{{xesam:artist}}\t{{xesam:album}}\t{{mpris:length}}\t{{position}}\t{{playerName}}\t{{mpris:artUrl}}"
         );
      } catch (IOException e) {
         this.status = "Linux: install playerctl for the system player (e.g. sudo apt install playerctl)";
         return null;
      }

      if (this.status.startsWith("Linux")) {
         this.status = "Listening to system media";
      }

      if (out != null && !out.isEmpty()) {
         String[] f = out.split("\t", -1);
         if (f.length >= 7 && !f[1].isBlank() && !f[0].equalsIgnoreCase("Stopped")) {
            boolean playing = f[0].equalsIgnoreCase("Playing");
            String app = f[6].toLowerCase(Locale.ROOT).contains("spotify") ? "Spotify app" : "System";
            return new NowPlaying(f[1], f[2], f[3], num(f[4]) / 1000L, num(f[5]) / 1000L, playing, System.nanoTime(), app, f.length > 7 ? f[7].trim() : "");
         } else {
            return null;
         }
      } else {
         return null;
      }
   }

   public String status() {
      return this.status;
   }

   public NowPlaying latest() {
      return this.latest;
   }

   public synchronized void start() {
      if (!MAC && !LINUX) {
         if (supported() && !this.gaveUp && (this.process == null || !this.process.isAlive())) {
            long now = System.currentTimeMillis();
            if (this.process != null) {
               if (now - this.startedAt < 15000L) {
                  this.quickDeaths++;
               } else {
                  this.quickDeaths = 0;
               }

               this.process = null;
               if (this.quickDeaths >= 3) {
                  this.gaveUp = true;
                  this.status = "System media helper keeps getting stopped (antivirus?). Check security software, then Rescan desktop players";
                  NocturneClient.LOG.warn("System media helper was stopped 3 times in a row; not starting it again this session");
                  return;
               }

               this.retryAt = now + 20000L * this.quickDeaths;
            }

            if (now >= this.retryAt) {
               try (InputStream in = SystemMediaSource.class.getResourceAsStream("/nocturne/media.ps1")) {
                  if (in != null) {
                     String script = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\n}\n", "\n}\n\n");
                     Process p = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", "-")
                        .redirectErrorStream(true)
                        .start();

                     try (Writer w = new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8)) {
                        w.write(script);
                        w.write("\n\n");
                     }

                     this.process = p;
                     this.startedAt = now;
                     this.status = "Listening to system media";
                     this.reader = new Thread(() -> this.readLoop(p), "Nocturne system media");
                     this.reader.setDaemon(true);
                     this.reader.start();
                  } else {
                     this.status = "Helper script missing";
                  }
               } catch (Exception e) {
                  this.status = "Couldn't start: " + e.getMessage();
                  NocturneClient.LOG.warn("System media helper failed", e);
                  this.retryAt = now + 60000L;
               }
            }
         }
      } else {
         this.startPolling();
      }
   }

   public synchronized void stop() {
      this.gaveUp = false;
      this.polling = false;
      if (this.poller != null) {
         this.poller.interrupt();
      }

      this.poller = null;
      if (this.process != null) {
         this.process.destroy();
      }

      this.retryAt = 0L;
      this.quickDeaths = 0;
      this.process = null;
      this.latest = null;
   }

   private void readLoop(Process p) {
      if (p != null) {
         String line;
         try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            while (this.process == p && (line = r.readLine()) != null) {
               line = line.trim();
               if (line.startsWith("{")) {
                  try {
                     JsonObject o = JsonParser.parseString(line).getAsJsonObject();
                     NowPlaying detected = decodeSession(o);
                     if (this.process == p) {
                        this.latest = detected;
                        this.status = detected == null ? "Nothing playing" : "Listening to " + detected.source();
                     }
                  } catch (Exception ex) {
                     NocturneClient.LOG.debug("Invalid desktop media response", ex);
                  }
               }
            }
         } catch (IOException ex) {
            NocturneClient.LOG.debug("Desktop media helper disconnected", ex);
         }

         if (this.process == p) this.status = "System media helper stopped";
      }
   }
   static NowPlaying decodeSession(JsonObject o) {
      if (!o.has("title") || o.get("title").getAsString().isBlank()) return null;
      boolean playing = o.get("playing").getAsBoolean();
      long duration = Math.max(0L, o.get("dur").getAsLong());
      long position = Math.max(0L, o.get("pos").getAsLong());
      if (playing && o.has("updated") && o.get("updated").getAsLong() > 0L) {
         long elapsed = Math.max(0L, o.get("now").getAsLong() - o.get("updated").getAsLong());
         position = position > Long.MAX_VALUE - elapsed ? Long.MAX_VALUE : position + elapsed;
      }
      if (duration > 0) position = Math.min(position, duration);
      String app = o.has("app") ? o.get("app").getAsString() : "System";
      return new NowPlaying(o.get("title").getAsString(), o.get("artist").getAsString(),
         o.get("album").getAsString(), duration, position, playing, System.nanoTime(),
         app.toLowerCase(Locale.ROOT).contains("spotify") ? "Spotify app" : "System");
   }

}
