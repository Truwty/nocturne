package dev.truwty.nocturne.lyrics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.truwty.nocturne.NocturneClient;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
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
   public record Player(String id, String label, Set<String> capabilities, boolean playing, String issue) {
      public Player(String id,String label) { this(id,label,Set.of(),false,"Capability details unavailable on this platform"); }
      public String diagnostic() {
         if(!issue.isBlank())return issue;
         return (playing?"Playing":"Paused / idle")+" · "+(capabilities.isEmpty()?"No transport advertised":String.join(", ",new java.util.TreeSet<>(capabilities)));
      }
   }
   private volatile List<Player> players = List.of();
   private volatile Set<String> capabilities = Set.of();
   private volatile String activeId = "";
   private volatile List<String> priorities = dev.truwty.nocturne.playback.PlayerSelection.DEFAULTS;
   private volatile boolean shuffle;
   private volatile String repeat = "off";
   public boolean shuffle() { return shuffle; }
   public String repeat() { return repeat; }
   public void priorities(List<String> values) {
      priorities=dev.truwty.nocturne.playback.PlayerSelection.validate(values);
      var cmd=new JsonObject();cmd.addProperty("action","priorities");var array=new com.google.gson.JsonArray();priorities.forEach(array::add);cmd.add("values",array);send(cmd);
   }
   private volatile String selectedId = "";
   private volatile String commandStatus = "";
   private volatile int pollInterval=500;
   public void activity(int millis){int value=Math.clamp(millis,500,3000);if(value==pollInterval)return;pollInterval=value;sendActivity();}
   private void sendActivity(){var cmd=new JsonObject();cmd.addProperty("action","activity");cmd.addProperty("interval",pollInterval);send(cmd);}
   private volatile float peak;
   private volatile boolean audioAvailable;
   private volatile boolean audioEnabled;
   private volatile String audioError = "";
   private volatile long audioSample;
   private volatile ArrayBlockingQueue<JsonObject> commands;

   public List<Player> players() { return players; }
   public String selected() { return selectedId; }
   public String commandStatus() { return commandStatus; }
   public boolean can(String action) { return capabilities.contains(action); }
   public float peak() { return System.nanoTime() - audioSample < 1_000_000_000L ? peak : 0; }
   public boolean audioAvailable() { return audioAvailable && System.nanoTime() - audioSample < 1_000_000_000L; }
   public String audioStatus() { return !WINDOWS ? "Audio meter requires Windows" : !audioEnabled ? "Audio meter off" : audioAvailable() ? "Desktop output (all sounds)" : audioError; }
   public void select(String id) { selectedId = id; JsonObject cmd = new JsonObject(); cmd.addProperty("action", "select"); cmd.addProperty("app", id); send(cmd); }
   public void audio(boolean enabled) { if (enabled == audioEnabled) return; audioEnabled = enabled; JsonObject cmd = new JsonObject(); cmd.addProperty("action", "audio"); cmd.addProperty("enabled", enabled); send(cmd); }
   public void command(String action, long millis) {
      if (!Set.of("toggle", "next", "previous", "seek", "shuffle", "repeat").contains(action)) throw new IllegalArgumentException("Unknown transport command");
      if (!can(action)) { commandStatus = "This player does not support " + action; return; }
      JsonObject cmd = new JsonObject(); cmd.addProperty("action", action); cmd.addProperty("target", activeId); cmd.addProperty("millis", Math.max(0, millis));
      String targetId = activeId;
      if (WINDOWS) send(cmd);
      else Thread.ofPlatform().daemon().name("nocturne-command").start(() -> {
         try {
            String result;
            if (LINUX) result = action.equals("seek") ? run("playerctl", "-p", targetId, "position", Double.toString(millis / 1000.0)) : run("playerctl", "-p", targetId, action.equals("toggle") ? "play-pause" : action);
            else {
               String app = targetId.equals("Music") ? "Music" : "Spotify";
               String operation = switch (action) { case "toggle" -> "playpause"; case "next" -> "next track"; case "previous" -> "previous track"; default -> "set player position to " + millis / 1000.0; };
               result = run("osascript", "-e", "tell application \"" + app + "\" to " + operation);
            }
            commandStatus = result == null ? "Player rejected command" : "Command accepted";
         } catch (IOException ex) { commandStatus = ex.getMessage(); NocturneClient.LOG.debug("Transport failed", ex); }
      });
   }
   private void send(JsonObject command) {
      ArrayBlockingQueue<JsonObject> queue = commands;
      if (queue != null && !queue.offer(command)) commandStatus = "Player command queue is full";
   }

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
                  Thread.sleep(pollInterval);
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
      String[][] apps={{"Spotify",MAC_SPOTIFY,"Spotify app"},{"Music",MAC_MUSIC,"Apple Music"}};
      var candidates=new java.util.HashMap<String,NowPlaying>();var detected=new java.util.ArrayList<Player>();
      for(String[] app:apps) {
         if(run("pgrep","-x",app[0])==null)continue;
         String out=run("osascript","-e",app[1]);if(out==null||out.isBlank())continue;
         String[] f=out.split("\t",-1);if(f.length<6||f[0].isBlank())continue;
         boolean playing=f[5].trim().equalsIgnoreCase("playing");
         detected.add(new Player(app[0],app[2],Set.of("toggle","next","previous","seek"),playing,"AppleScript transport; advanced controls unavailable"));
         candidates.put(app[0],new NowPlaying(f[0],f[1],f[2],num(f[3]),num(f[4]),playing,System.nanoTime(),app[2],f.length>6?f[6].trim():"",app[0]));
      }
      players=List.copyOf(detected);Player selected=dev.truwty.nocturne.playback.PlayerSelection.choose(players,priorities,selectedId,activeId);
      activeId=selected==null?"":selected.id();capabilities=selected==null?Set.of():selected.capabilities();return selected==null?null:candidates.get(selected.id());
   }

   private NowPlaying pollLinux() {
      try {
         String listing=run("playerctl","-l");
         var candidates=new java.util.HashMap<String,NowPlaying>();
         var detected=new java.util.ArrayList<Player>();
         if(listing!=null) for(String id:listing.lines().limit(32).toList()) {
            String out=run("playerctl","-p",id,"metadata","--format","{{status}}\t{{xesam:title}}\t{{xesam:artist}}\t{{xesam:album}}\t{{mpris:length}}\t{{position}}\t{{playerName}}\t{{mpris:artUrl}}");
            if(out==null)continue;
            String[] f=out.split("\t",-1);if(f.length<7||f[1].isBlank()||f[0].equalsIgnoreCase("Stopped"))continue;
            boolean playing=f[0].equalsIgnoreCase("Playing");
            detected.add(new Player(id,id,Set.of("toggle","next","previous","seek"),playing,"Advanced controls not exposed by this adapter"));
            candidates.put(id,new NowPlaying(f[1],f[2],f[3],num(f[4])/1000,num(f[5])/1000,playing,System.nanoTime(),f[6],f.length>7?f[7].trim():"",id));
         }
         players=List.copyOf(detected);
         Player selected=dev.truwty.nocturne.playback.PlayerSelection.choose(players,priorities,selectedId,activeId);
         activeId=selected==null?"":selected.id();capabilities=selected==null?Set.of():selected.capabilities();
         return selected==null?null:candidates.get(selected.id());
      } catch(IOException ex) {capabilities=Set.of();players=List.of();status="Linux: install playerctl to detect desktop players";return null;}
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
                  this.retryAt = now + 60000L;
                  this.status = "System media helper stopped repeatedly; retrying in 60 seconds";
                  NocturneClient.LOG.warn("System media helper was stopped 3 times in a row; retrying after a 60-second backoff");
                  return;
               }

               this.retryAt = now + 20000L * this.quickDeaths;
            }

            if (now >= this.retryAt) {
               try (InputStream in = SystemMediaSource.class.getResourceAsStream("/nocturne/media-v2.ps1")) {
                  if (in != null) {
                     String script = new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\n}\n", "\n}\n\n");
                     String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
                     Process p = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
                        .redirectErrorStream(true).start();
                     ArrayBlockingQueue<JsonObject> queue = new ArrayBlockingQueue<>(32);
                     this.commands = queue;
                     this.process = p;
                     this.startedAt = now;
                     this.status = "Listening to system media";
                     Thread.ofPlatform().daemon().name("nocturne-command-writer").start(() -> {
                        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(p.getOutputStream(), StandardCharsets.UTF_8))) {
                           while (this.process == p && p.isAlive()) {
                              JsonObject cmd = queue.poll(200, TimeUnit.MILLISECONDS);
                              if (cmd != null) { writer.write(cmd.toString()); writer.newLine(); writer.flush(); }
                           }
                        } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
                        catch (IOException ex) { NocturneClient.LOG.debug("Media command pipe closed", ex); }
                     });
                     select(selectedId);
                     priorities(priorities);sendActivity();
                     JsonObject audio = new JsonObject(); audio.addProperty("action", "audio"); audio.addProperty("enabled", audioEnabled); send(audio);
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
      this.commands = null;
      this.capabilities = Set.of();
      this.players = List.of();
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
                     if (o.has("error")) { this.status = o.get("error").getAsString(); this.latest = null; continue; }
                     if(o.has("sessions")) players=decodePlayers(o);
                     else if (o.has("apps")) {
                        var list = new java.util.ArrayList<Player>();
                        for (var app : o.getAsJsonArray("apps")) list.add(new Player(app.getAsString(), app.getAsString()));
                        players = List.copyOf(list);
                     }
                     var controls = new java.util.HashSet<String>();
                     for (String action : List.of("toggle", "next", "previous", "seek", "shuffle", "repeat")) if (o.has(action) && o.get(action).getAsBoolean()) controls.add(action);
                     capabilities = Set.copyOf(controls);
                     shuffle = o.has("shuffleActive") && o.get("shuffleActive").getAsBoolean();
                     repeat = o.has("repeatMode") ? o.get("repeatMode").getAsString() : "off";
                     activeId = o.has("app") ? o.get("app").getAsString() : "";
                     commandStatus = o.has("commandStatus") ? o.get("commandStatus").getAsString() : "";
                     float rawPeak = o.has("peak") ? o.get("peak").getAsFloat() : 0;
                     peak = Float.isFinite(rawPeak) ? Math.clamp(rawPeak, 0, 1) : 0;
                     audioAvailable = o.has("audioAvailable") && o.get("audioAvailable").getAsBoolean();
                     audioError = o.has("audioError") ? o.get("audioError").getAsString() : "";
                     audioSample = System.nanoTime();
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

         if (this.process == p) {
            this.status="System media helper stopped";this.latest=null;this.capabilities=Set.of();this.players=List.of();
         }
      }
   }
   static List<Player> decodePlayers(JsonObject o) {
      var result=new java.util.ArrayList<Player>();
      for(var value:o.getAsJsonArray("sessions")) {
         var app=value.getAsJsonObject(); var caps=new java.util.HashSet<String>();
         for(String action:List.of("toggle","seek","next","previous","shuffle","repeat")) if(app.has(action)&&app.get(action).getAsBoolean())caps.add(action);
         String id=app.get("id").getAsString();
         result.add(new Player(id,id,Set.copyOf(caps),app.has("playing")&&app.get("playing").getAsBoolean(),app.has("issue")?app.get("issue").getAsString():""));
      }
      return List.copyOf(result);
   }

   static NowPlaying decodeSession(JsonObject o) {
      if (!o.has("title") || o.get("title").getAsString().isBlank()) return null;
      boolean playing = o.get("playing").getAsBoolean();
      long duration = Math.max(0L, o.get("dur").getAsLong());
      long position = Math.max(0L, o.get("pos").getAsLong());
      if (playing && o.has("updated") && o.get("updated").getAsLong() > 0L) {
         long elapsed = Math.max(0L, o.get("now").getAsLong() - o.get("updated").getAsLong());
         // Extrapolate sparse timeline updates, but reject timestamps older than the track.
         // Invalid/default timestamps otherwise force every sample to the song's end.
         long maximumAge = duration > 0 ? duration : TimeUnit.DAYS.toMillis(1);
         if (elapsed <= maximumAge) position = position > Long.MAX_VALUE - elapsed ? Long.MAX_VALUE : position + elapsed;
      }
      if (duration > 0) position = Math.min(position, duration);
      String app = o.has("app") ? o.get("app").getAsString() : "System";
      return new NowPlaying(o.get("title").getAsString(), o.get("artist").getAsString(),
         o.get("album").getAsString(), duration, position, playing, System.nanoTime(),
         app.toLowerCase(Locale.ROOT).contains("spotify") ? "Spotify app" : "System", "",
         app+"|"+(o.has("deviceId")?o.get("deviceId").getAsString():""));
   }

}
