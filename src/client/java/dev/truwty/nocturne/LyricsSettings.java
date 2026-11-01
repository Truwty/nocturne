package dev.truwty.nocturne;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.truwty.nocturne.gui.Draw;
import dev.truwty.nocturne.lyrics.LyricsDebug;
import dev.truwty.nocturne.lyrics.LyricsService;
import dev.truwty.nocturne.lyrics.NowPlaying;
import dev.truwty.nocturne.lyrics.SystemMediaSource;
import dev.truwty.nocturne.setting.ActionSetting;
import dev.truwty.nocturne.setting.BoolSetting;
import dev.truwty.nocturne.setting.ColorSetting;
import dev.truwty.nocturne.setting.ModeSetting;
import dev.truwty.nocturne.setting.NumberSetting;
import dev.truwty.nocturne.setting.Setting;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

@Environment(EnvType.CLIENT)
public class LyricsSettings {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nocturne.json");
   public final List<Setting<?>> settings = new ArrayList<>();
   private boolean enabled = true;
   public final BoolSetting controllerNavigation = add(new BoolSetting("Controller navigation",true));
   public final BoolSetting translation = add(new BoolSetting("Show translation", false));
   public final BoolSetting romanization = add(new BoolSetting("Show romanization", false));
   public final BoolSetting vocalCountdown = add(new BoolSetting("Vocal countdown", true));
   public final BoolSetting lyricConfidence = add(new BoolSetting("Lyric confidence", true));
   public final NumberSetting cacheDays = add(new NumberSetting("Lyric cache expiry",30,1,90,1," days"));
   private JsonObject preservedConfig = new JsonObject();
   private String configNotice = "";
   private boolean configWritable = true;
   public String configNotice() { return configNotice; }
   public final ModeSetting preset;
   public final ModeSetting follow;
   public final ModeSetting style;
   public final ModeSetting intensity;
   public final ModeSetting colorMode;
   public final ModeSetting cloudStyle;
   public final ModeSetting lyricFont;
   public final ModeSetting guiStyle;
   public final ModeSetting shaderMode;
   public final NumberSetting distance;
   public final NumberSetting size;
   public final NumberSetting offset;
   public final NumberSetting linger;
   public final NumberSetting maxWords;
   public final NumberSetting orbitSpeed;
   public final BoolSetting smoothFont;
   public final BoolSetting faceYou;
   public final BoolSetting floor;
   public final BoolSetting glow;
   public final BoolSetting chroma;
   public final BoolSetting sparkles;
   public final BoolSetting impact;
   public final BoolSetting cinematic;
   public final BoolSetting beatZoom;
   public final BoolSetting nextLine;
   public final BoolSetting titleCard;
   public final BoolSetting seeThrough;
   public final BoolSetting smartPlace;
   public final BoolSetting orbit;
   public final BoolSetting cloudDrift;
   public final BoolSetting cloudTint;
   public final BoolSetting battle;
   public final BoolSetting readable;
   public final BoolSetting topBar;
   public final BoolSetting beatPulse;
   public final BoolSetting albumRain;
   public final BoolSetting plainLyrics;
   public final BoolSetting keepUp;
   public final BoolSetting debug;
   public final ColorSetting color;
   public final ColorSetting color2;
   public final BoolSetting reducedMotion = add(new BoolSetting("Reduced motion", false));
   public final BoolSetting flashEffects = add(new BoolSetting("Flash effects", false));
   public final BoolSetting shakeEffects = add(new BoolSetting("Shake effects", false));
   public final BoolSetting highContrast = add(new BoolSetting("High contrast lyrics", false));
   public final BoolSetting artworkDisplay = add(new BoolSetting("Album artwork", true));
   public final BoolSetting audioReactive = add(new BoolSetting("Desktop audio reactive", false));
   public final NumberSetting audioGain = add(new NumberSetting("Audio sensitivity", 1.5, 0.2, 5, 0.1, "x"));
   public final ModeSetting quality = add(new ModeSetting("Effect quality", 1, "Lightweight", "Balanced", "Ultra"));
   public final BoolSetting adaptiveEffects = add(new BoolSetting("Adaptive effect budgets",true));
   public final BoolSetting distanceEffects = add(new BoolSetting("Distance effect detail",true));
   public final BoolSetting performanceReadout = add(new BoolSetting("Performance readout",false));
   public final NumberSetting effectTargetMs = add(new NumberSetting("Target frame time",16.7,4.2,50,.1," ms"));
   public final NumberSetting glowPassLimit = add(new NumberSetting("Glow pass limit",128,0,512,8," / frame"));
   public final NumberSetting cloudLayerLimit = add(new NumberSetting("Cloud lettering layer limit",64,0,256,8," / frame"));
   public final NumberSetting lingeringLimit = add(new NumberSetting("Lingering line limit",6,0,6,1,""));
   public final NumberSetting effectNearDistance = add(new NumberSetting("Full effect detail distance",12,2,64,2," blocks"));
   public final NumberSetting particleLimit = add(new NumberSetting("Particle limit", 90, 0, 180, 10, ""));
   public final NumberSetting renderDistance = add(new NumberSetting("Lyric render distance", 64, 8, 256, 8, " blocks"));
   public final NumberSetting transition = add(new NumberSetting("Transition duration", 0.45, 0.1, 2, 0.05, "s"));
   public final NumberSetting editorX = add(new NumberSetting("Editor horizontal", 0, -10, 10, 0.1, " blocks"));
   public final NumberSetting editorY = add(new NumberSetting("Editor vertical", 0, -10, 10, 0.1, " blocks"));
   public final NumberSetting editorYaw = add(new NumberSetting("Editor yaw", 0, -180, 180, 1, "°"));
   public final NumberSetting editorPitch = add(new NumberSetting("Editor pitch", 0, -90, 90, 1, "°"));
   public final NumberSetting editorRoll = add(new NumberSetting("Editor roll", 0, -180, 180, 1, "°"));
   public final ColorSetting uiBackground = add(new ColorSetting("UI background", 0xFF100E18));
   public final ColorSetting uiAccent = add(new ColorSetting("UI accent", 0xFFB77FFF));
   public final ColorSetting uiText = add(new ColorSetting("UI text", 0xFFF0EEF5));
   public final NumberSetting uiOpacity = add(new NumberSetting("UI opacity", 92, 30, 100, 1, "%"));
   public final NumberSetting uiSpacing = add(new NumberSetting("UI row spacing", 22, 22, 32, 1, "px"));
   public final ModeSetting uiFont = add(new ModeSetting("UI font", 0, "Smooth", "Elegant", "Impact", "Pixel"));
   public static final int SCATTER = 0;
   public static final int AHEAD = 1;
   public static final int FOLLOW = 2;
   public static final int SKY = 3;
   public static final int CLOUDS = 4;
   public static final int AUTO = 5;
   public static final int DRIFT = 6;
   public static final int COLOR_ALBUM = 3;
   private Boolean lastPlain;
   private int appliedPreset = -1;
   private String presetSnapshot = "";

   private <T extends Setting<?>> T add(T s) {
      this.settings.add(s);
      return s;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public void setEnabled(boolean v) {
      if (v != this.enabled) {
         this.enabled = v;
         if (v) {
            this.onEnable();
         } else {
            this.onDisable();
         }
      }
   }

   public void load() {
      try {
         if (!Files.exists(FILE)) {
            return;
         }

         ConfigRecovery.Loaded loaded=ConfigRecovery.load(FILE);
         configNotice=loaded.notice(); configWritable=loaded.writable();
         JsonObject root=loaded.data(); preservedConfig=root.deepCopy();
         if (root.has("enabled")) {
            this.enabled = root.get("enabled").getAsBoolean();
         }

         JsonObject s = root.has("settings") ? root.getAsJsonObject("settings") : new JsonObject();

         for (Setting<?> st : this.settings) {
            try {
               var value=s.get(st.name);
               if(value!=null && st instanceof BoolSetting && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()))throw new IllegalArgumentException("Expected boolean");
               if(value!=null && st instanceof NumberSetting && (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()))throw new IllegalArgumentException("Expected number");
               st.load(value);
            }
            catch (RuntimeException ex) {
               if(!configNotice.startsWith("Invalid setting")) Files.copy(FILE,FILE.resolveSibling(FILE.getFileName()+".invalid-settings-"+java.util.UUID.randomUUID()));
               configNotice = "Invalid setting ignored: " + st.name;
               NocturneClient.LOG.warn("Invalid setting {} retained in migration backup",st.name,ex);
            }
         }

         if (!s.has(this.lyricFont.name) && !this.smoothFont.on()) {
            this.lyricFont.set(3);
         }
      } catch (Exception e) {
         configNotice="Config could not be backed up; saving disabled"; configWritable=false;
         NocturneClient.LOG.warn("Couldn't read {}", FILE, e);
      }
   }

   public void save() {
      if(!configWritable)return;
      try {
         JsonObject root = preservedConfig.deepCopy();
         root.addProperty("enabled", this.enabled);
         root.addProperty("schemaVersion",ConfigRecovery.SCHEMA);
         JsonObject s = root.has("settings") ? root.getAsJsonObject("settings").deepCopy() : new JsonObject();

         for (Setting<?> st : this.settings) {
            s.add(st.name, st.save());
         }

         SongAppearance.persistGlobal(s);
         root.add("settings", s);
         Files.createDirectories(FILE.getParent());
         ConfigRecovery.save(FILE, GSON.toJson(root));
      } catch (Exception e) {
         configNotice="Settings could not be saved: "+e.getMessage();
         NocturneClient.LOG.warn("Couldn't save {}", FILE, e);
      }
   }

   public LyricsSettings() {
      this.add(new ActionSetting("Studio", () -> "Open Nocturne Studio", Studio::open));
      this.add(new ActionSetting("Demo", () -> LyricsService.INSTANCE.demoPlaying() ? "Stop demo song" : "Play demo song", () -> {
         LyricsService svc = LyricsService.INSTANCE;
         if (svc.demoPlaying()) {
            svc.stopDemo();
         } else {
            if (!this.isEnabled()) {
               this.setEnabled(true);
            }

            svc.startDemo();
         }
      }));
      this.preset = this.add(new ModeSetting("Preset", 0, LyricsPresets.NAMES));
      this.plainLyrics = this.add(new BoolSetting("Unsynced lyrics fallback", true));
      this.debug = this.add(new BoolSetting("Debug overlay", false));
      this.add(new ActionSetting("Copy debug", () -> "Copy debug info", () -> {
         String r = LyricsDebug.report();
         Minecraft.getInstance().keyboardHandler.setClipboard(r);
      }));
      this.style = this.add(new ModeSetting("Animation", 0, "Edit", "Karaoke", "Typewriter", "Wave", "Drop", "Glitch", "Rise", "Zoom"));
      this.intensity = this.add(new ModeSetting("Intensity", 1, "Chill", "Normal", "Hype"));
      this.lyricFont = this.add(new ModeSetting("Lyrics font", 0, Draw.LYRIC_FONTS));
      this.follow = this.add(
         new ModeSetting(
            "Placement", 2, "Scattered around you", "Straight ahead", "Follow view", "In the sky", "In the clouds", "Auto", "Around you, moving along"
         )
      );
      this.battle = this.add(new BoolSetting("Battle words when fighting", true));
      this.cloudStyle = this.add(new ModeSetting("Cloud style", 0, "Puffs", "Skywriting", "Carved"));
      this.cloudDrift = this.add(new BoolSetting("Drift with the clouds", true));
      this.cloudTint = this.add(new BoolSetting("Clouds follow time of day", true));
      this.smartPlace = this.add(new BoolSetting("Smart placement", true));
      this.keepUp = this.add(new BoolSetting("Keep up when running", true));
      this.orbit = this.add(new BoolSetting("Orbit you in F5 front view", true));
      this.orbitSpeed = this.add(new NumberSetting("Orbit speed", 100.0, 25.0, 250.0, 25.0, "%"));
      this.maxWords = this.add(new NumberSetting("Words per line", 5.0, 2.0, 10.0, 1.0, ""));
      this.floor = this.add(new BoolSetting("Words on the floor", true));
      this.linger = this.add(new NumberSetting("Lines stay for", 4.0, 0.0, 10.0, 0.5, "s"));
      this.faceYou = this.add(new BoolSetting("Turn to face you", true));
      this.distance = this.add(new NumberSetting("Distance", 3.5, 2.0, 7.0, 0.5, " blocks"));
      this.size = this.add(new NumberSetting("Size", 100.0, 50.0, 200.0, 5.0, "%"));
      this.colorMode = this.add(new ModeSetting("Colour mode", 1, "Single", "Gradient", "Rainbow", "Album"));
      this.color = this.add(new ColorSetting("Colour", -9444394));
      this.color2 = this.add(new ColorSetting("Second colour", -3561729));
      this.glow = this.add(new BoolSetting("Glow", true));
      this.chroma = this.add(new BoolSetting("Chromatic split", true));
      this.sparkles = this.add(new BoolSetting("Sparkles", true));
      this.impact = this.add(new BoolSetting("Impact shake + flash", true));
      this.beatZoom = this.add(new BoolSetting("Beat zoom", true));
      this.beatPulse = this.add(new BoolSetting("Beat pulse", true));
      this.readable = this.add(new BoolSetting("Auto readability", true));
      this.albumRain = this.add(new BoolSetting("Album colour rain", true));
      this.shaderMode = this.add(new ModeSetting("Shader support", 0, "Auto", "Always", "Off"));
      this.topBar = this.add(new BoolSetting("Karaoke bar", true));
      this.cinematic = this.add(new BoolSetting("Cinematic bars", true));
      this.nextLine = this.add(new BoolSetting("Show next line", true));
      this.titleCard = this.add(new BoolSetting("Song title intro", true));
      this.seeThrough = this.add(new BoolSetting("Show through walls", false));
      this.smoothFont = this.add(new BoolSetting("Smooth font", true));
      this.guiStyle = this.add(new ModeSetting("Settings style", 1, "Vanilla", "Glass"));
      this.offset = this.add(new NumberSetting("Timing offset", 0.0, -1500.0, 1500.0, 50.0, "ms"));
      this.add(new ActionSetting("Status", this::statusLine, () -> {}));
      this.add(new ActionSetting("Detection", () -> "Automatic · no login needed", () -> {}));
      this.add(new ActionSetting("Reconnect", () -> "Rescan desktop players", () -> {
         LyricsService.INSTANCE.system.stop();
         LyricsService.INSTANCE.system.start();
      }));
   }

   public float intensityFactor() {
      return switch (this.intensity.get()) {
         case 0 -> 0.45F;
         case 2 -> 1.7F;
         default -> 1.0F;
      };
   }

   private String statusLine() {
      LyricsService svc = LyricsService.INSTANCE;
      NowPlaying np = svc.now();
      if (np != null) {
         return np.title() + " · " + svc.lyricsStatus();
      } else if (!SystemMediaSource.supported()) {
         return "System player needs Windows, macOS or Linux";
      } else {
         return svc.system.status();
      }
   }

   protected void onEnable() {
      LyricsService.INSTANCE.start();
   }

   protected void onDisable() {
      LyricsService.INSTANCE.stop();
   }

   public void onTick(Minecraft mc) {
      boolean plain = this.plainLyrics.on();
      if (this.lastPlain != null && this.lastPlain != plain) {
         LyricsService.INSTANCE.refetch();
      }

      this.lastPlain = plain;
   }

   public List<Setting<?>> presetControlled() {
      return List.of(
         this.style,
         this.intensity,
         this.lyricFont,
         this.colorMode,
         this.color,
         this.color2,
         this.glow,
         this.chroma,
         this.sparkles,
         this.impact,
         this.beatZoom,
         this.cinematic
      );
   }

   private String snapshot() {
      StringBuilder b = new StringBuilder();

      for (Setting<?> st : this.presetControlled()) {
         b.append(st.save()).append('|');
      }

      return b.toString();
   }

   public void adoptPreset() { appliedPreset=preset.get();presetSnapshot=snapshot(); }

   public boolean tickPreset() {
      int p = this.preset.get();
      if (this.appliedPreset == -1) {
         this.appliedPreset = p;
         this.presetSnapshot = this.snapshot();
         return false;
      }

      if (p != this.appliedPreset) {
         if (p != 0) {
            LyricsPresets.apply(this, p);
         }

         this.appliedPreset = p;
         this.presetSnapshot = this.snapshot();
         this.save();
         return true;
      } else if (p != 0 && !this.snapshot().equals(this.presetSnapshot)) {
         this.preset.set(0);
         this.appliedPreset = 0;
         this.presetSnapshot = this.snapshot();
         this.save();
         return true;
      } else {
         return false;
      }
   }
}
