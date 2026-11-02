package dev.truwty.nocturne;

public final class Fx {
   private Fx() {
   }

   public static boolean animations() {
      return NocturneClient.SETTINGS == null || !NocturneClient.SETTINGS.reducedMotion.on();
   }

   public static float animationSpeed() {
      return 1.0F;
   }

   public static boolean smoothFont() {
      return NocturneClient.SETTINGS == null || NocturneClient.SETTINGS.smoothFont.on();
   }
}
