package dev.truwty.nocturne;

import dev.truwty.nocturne.gui.GlassLyricsScreen;
import dev.truwty.nocturne.gui.LyricsScreen;
import dev.truwty.nocturne.lyrics.HudLyrics;
import dev.truwty.nocturne.lyrics.LyricsHud;
import dev.truwty.nocturne.lyrics.LyricsRenderer;
import dev.truwty.nocturne.lyrics.LyricsService;
import com.mojang.blaze3d.platform.InputConstants.Type;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.ClientStopping;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class NocturneClient implements ClientModInitializer {
   public static final String ID = "nocturne";
   public static final String NAME = "Nocturne";
   public static final String ASCII_NAME = "Nocturne Lyrics";
   public static final Logger LOG = LoggerFactory.getLogger("Nocturne Lyrics");
   public static LyricsSettings SETTINGS;
   public static KeyMapping OPEN_KEY;
   private static int playbackTicks;

   public void onInitializeClient() {
      SETTINGS = new LyricsSettings();
      SETTINGS.load();
      LyricsService.INSTANCE.system.select(Studio.library().player());
      dev.truwty.nocturne.playback.Playback.initialize();
      Category category = Category.register(Identifier.fromNamespaceAndPath("nocturne", "main"));
      OPEN_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.nocturne.open", Type.KEYSYM, 78, category));
      dev.truwty.nocturne.playback.PlaybackKeys.register(category);
      ClientTickEvents.END_CLIENT_TICK.register(NocturneClient::tick);
      LevelRenderEvents.COLLECT_SUBMITS.register(LyricsRenderer::render);
      HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("nocturne", "cinema"), LyricsHud::render);
      HudElementRegistry.addFirst(Identifier.fromNamespaceAndPath("nocturne", "world_lyrics"), HudLyrics::render);
      if (SETTINGS.isEnabled()) {
         LyricsService.INSTANCE.start();
      }

      ClientLifecycleEvents.CLIENT_STOPPING.register((ClientStopping)client -> {
         dev.truwty.nocturne.playback.Playback.close();
         dev.truwty.nocturne.gui.AlbumArtwork.close();
         SongAppearance.restore();
         LyricsService.INSTANCE.stop();
         SETTINGS.save();
         try { Studio.savePlacement(); } catch (java.io.IOException ex) { LOG.warn("Could not save placement", ex); }
      });
      LOG.info("{} loaded", "Nocturne Lyrics");
   }

   public static Screen settingsScreen(Screen parent) {
      return new dev.truwty.nocturne.gui.StudioScreen(parent);
   }

   private static void tick(Minecraft mc) {
      if (!SETTINGS.isEnabled() && ++playbackTicks % 10 == 0) LyricsService.INSTANCE.system.start();
      dev.truwty.nocturne.playback.PlaybackKeys.tick(mc);
      while (OPEN_KEY.consumeClick()) {
         if (mc.screen == null) {
            mc.setScreen(settingsScreen(null));
         }
      }

      LyricsService.INSTANCE.system.audio(SETTINGS.audioReactive.on() && !SETTINGS.reducedMotion.on());
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
