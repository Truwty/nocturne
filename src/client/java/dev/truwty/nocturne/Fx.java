package dev.truwty.nocturne;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class Fx {
   private Fx() {
   }

   public static boolean animations() {
      return true;
   }

   public static float animationSpeed() {
      return 1.0F;
   }

   public static boolean smoothFont() {
      return NocturneClient.SETTINGS == null || NocturneClient.SETTINGS.smoothFont.on();
   }
}
