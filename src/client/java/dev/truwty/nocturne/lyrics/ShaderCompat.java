package dev.truwty.nocturne.lyrics;

import dev.truwty.nocturne.NocturneClient;
import dev.truwty.nocturne.LyricsSettings;
import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;

@Environment(EnvType.CLIENT)
public final class ShaderCompat {
   private static final boolean IRIS = FabricLoader.getInstance().isModLoaded("iris");
   private static final boolean OCULUS_LIKE = FabricLoader.getInstance().isModLoaded("oculus");
   private static Object api;
   private static Method inUse;
   private static boolean broken;
   private static long nextCheck;
   private static boolean cached;

   private ShaderCompat() {
   }

   public static boolean shaderPackOn() {
      if ((IRIS || OCULUS_LIKE) && !broken) {
         long now = System.currentTimeMillis();
         if (now < nextCheck) {
            return cached;
         }

         nextCheck = now + 500L;

         try {
            if (inUse == null) {
               Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
               api = c.getMethod("getInstance").invoke(null);
               inUse = api.getClass().getMethod("isShaderPackInUse");
            }

            cached = (Boolean)inUse.invoke(api);
         } catch (Throwable t) {
            broken = true;
            cached = false;
            NocturneClient.LOG.debug("Couldn't ask Iris whether a shader pack is on", t);
         }

         return cached;
      } else {
         return false;
      }
   }

   public static boolean hudMode(LyricsSettings mod) {
      return switch (mod.shaderMode.get()) {
         case 1 -> true;
         case 2 -> false;
         default -> shaderPackOn();
      };
   }
}
