package dev.truwty.nocturne.lyrics;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.LyricsSettings;
import dev.truwty.nocturne.gui.Draw;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class LyricsRain {
   private static Object rainList;
   private static boolean rain;
   private static float strength;
   private static float beat;
   private static int color = -1;
   private static long last;

   private LyricsRain() {
   }

   static long lastFrame() {
      return last;
   }

   static float strength() {
      return strength;
   }

   public static void frame(Object rainColumns) {try(var scope=dev.truwty.nocturne.accessibility.ContextProfiles.scope()){frameContents(rainColumns);}}
   private static void frameContents(Object rainColumns) {
      rainList = rainColumns;
      long now = System.nanoTime();
      float dt = last == 0L ? 0.016F : Math.min(0.1F, (float)(now - last) / 1.0E9F);
      last = now;
      LyricsSettings mod = NocturneClient.SETTINGS;
      NowPlaying np = mod != null && mod.isEnabled() && mod.albumRain.on() ? LyricsService.INSTANCE.now() : null;
      if(dev.truwty.nocturne.accessibility.Accessibility.hidden()||dev.truwty.nocturne.accessibility.Accessibility.photosensitive()){strength=beat=0;return;}
      boolean want = np != null && np.playing();
      strength = strength + ((want ? 1.0F : 0.0F) - strength) * (1.0F - (float)Math.exp(-dt * 1.6F));
      if (strength < 0.003F) {
         strength = 0.0F;
         beat = 0.0F;
      } else {
         int album = LyricsService.INSTANCE.albumColor();
         int target = album != 0 ? album : LyricsRenderer.accent();
         color = Draw.mix(color, 0xFF000000 | target, 1.0F - (float)Math.exp(-dt * 3.0F));
         if (!want) {
            beat = beat * (float)Math.exp(-dt * 6.0F);
         } else {
            Lyrics ly = LyricsService.INSTANCE.lyrics();
            long pos = LyricsRenderer.clockMs();
            beat = ly != null ? ly.beat(pos) : (float)Math.exp(-((float)(pos % 500L) / 500.0F) * 7.0F);
         }
      }
   }

   public static void which(Object columns) {
      rain = columns != null && columns == rainList;
   }

   public static int tint(int argb) {
      if (rain && !(strength <= 0.0F)) {
         float a = (argb >>> 24 & 0xFF) / 255.0F;
         int c = Draw.mix(-1, color, 0.85F * strength);
         c = Draw.mix(c, Draw.brighten(color, 0.6F), Math.min(1.0F, beat * 0.7F) * strength);
         float alpha = Math.min(1.0F, a * (1.0F + (0.2F + 0.6F * beat) * strength));
         return Draw.withAlpha(c, Math.round(alpha * 255.0F));
      } else {
         return argb;
      }
   }
}
