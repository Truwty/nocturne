package dev.truwty.nocturne;

import dev.truwty.nocturne.compat.ClientAccess;
import dev.truwty.nocturne.lyrics.HudLyrics;
import dev.truwty.nocturne.lyrics.LyricsHud;
import dev.truwty.nocturne.lyrics.LyricsRenderer;
import dev.truwty.nocturne.lyrics.LyricsService;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NocturneClient {
   public static final String ID = "nocturne";
   public static final String NAME = "Nocturne";
   public static final String ASCII_NAME = "Nocturne Lyrics";
   public static final Logger LOG = LoggerFactory.getLogger("Nocturne Lyrics");
   public static LyricsSettings SETTINGS;
   public static KeyMapping OPEN_KEY,SCREENSHOT_CONTEXT_KEY;
   private static int playbackTicks;

   public void onInitializeClient() {
      SETTINGS = new LyricsSettings();
      SETTINGS.load();
      LyricsService.INSTANCE.system.select(Studio.library().player());
      dev.truwty.nocturne.playback.Playback.initialize();
      Category category = dev.truwty.nocturne.platform.ClientHooks.category(Identifier.fromNamespaceAndPath("nocturne", "main"));
      OPEN_KEY = dev.truwty.nocturne.platform.ClientHooks.key(new KeyMapping("key.nocturne.open", ClientAccess.keyType(), com.mojang.blaze3d.platform.InputConstants.KEY_N, category));
      SCREENSHOT_CONTEXT_KEY=dev.truwty.nocturne.platform.ClientHooks.key(new KeyMapping("key.nocturne.screenshot_context",ClientAccess.keyType(),-1,category));
      dev.truwty.nocturne.playback.PlaybackKeys.register(category);
      dev.truwty.nocturne.platform.ClientHooks.tick(NocturneClient::tick);
      dev.truwty.nocturne.platform.ClientHooks.hud("cinema", LyricsHud::render);
      dev.truwty.nocturne.platform.ClientHooks.hud("world_lyrics", HudLyrics::render);
      if (SETTINGS.isEnabled()) {
         LyricsService.INSTANCE.start();
      }

      dev.truwty.nocturne.platform.ClientHooks.stop(client -> {
         dev.truwty.nocturne.compat.GamepadInput.close();
              dev.truwty.nocturne.maintenance.UpdateChecker.close();
         dev.truwty.nocturne.playback.Playback.close();
         dev.truwty.nocturne.gui.AlbumArtwork.close();
         SongAppearance.restore();
         LyricsService.INSTANCE.stop();
         SETTINGS.save();
         try { Studio.savePlacement(); } catch (java.io.IOException ex) { LOG.warn("Could not save placement", ex); }
      });
      LOG.info("{} loaded", "Nocturne Lyrics");
   }

   public static String minecraftVersion() {
    return dev.truwty.nocturne.platform.ModPlatform.version("minecraft");
  }

  public static Screen settingsScreen(Screen parent) {
      if (!dev.truwty.nocturne.gui.SetupScreen.completed()) return new dev.truwty.nocturne.gui.SetupScreen(parent);
      return new dev.truwty.nocturne.gui.StudioScreen(parent);
   }

   private static void tick(Minecraft mc) {
      dev.truwty.nocturne.maintenance.UpdateChecker.tick();
      if (!SETTINGS.isEnabled() && ++playbackTicks % 10 == 0) LyricsService.INSTANCE.system.start();
      while(SCREENSHOT_CONTEXT_KEY.consumeClick())if(ClientAccess.screen(mc) ==null&&mc.level!=null)dev.truwty.nocturne.accessibility.ContextProfiles.screenshot(!dev.truwty.nocturne.accessibility.ContextProfiles.screenshot());
      dev.truwty.nocturne.accessibility.Accessibility.tick(mc);
      dev.truwty.nocturne.playback.PlaybackKeys.tick(mc);
      while (OPEN_KEY.consumeClick()) {
         if (ClientAccess.screen (mc) == null) {
            ClientAccess.setScreen(mc, settingsScreen(null));
         }
      }

      boolean background=mc.getWindow().isIconified()||!mc.isWindowActive();
      LyricsService.INSTANCE.activity(background,mc.isPaused());
      dev.truwty.nocturne.playback.Playback.spotify().activity(background,mc.isPaused());
      if(!SETTINGS.isEnabled())LyricsService.INSTANCE.system.activity(background?3000:2000);
      var song=dev.truwty.nocturne.playback.Playback.current();
      var localSong=LyricsService.INSTANCE.system.latest();
      LyricsService.INSTANCE.system.audio(SETTINGS.isEnabled()&&!background&&!mc.isPaused()&&song!=null&&localSong!=null&&song.key().equals(localSong.key())&&song.playing()&&(SETTINGS.audioReactive.on()||SETTINGS.visualizer.get()!=0||SETTINGS.detectedBeat.on()) && !SETTINGS.reducedMotion.on() && !SETTINGS.photosensitiveMode.on());
      dev.truwty.nocturne.gui.StudioController.tick(mc);
      Studio.tick();
      SETTINGS.tickPreset();
      SongAppearance.tick();
      if (SETTINGS.isEnabled()) {
         SETTINGS.onTick(mc);
         LyricsRenderer.tick(mc);
      }
   }
}
