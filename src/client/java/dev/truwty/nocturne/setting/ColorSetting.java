package dev.truwty.nocturne.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ColorSetting extends Setting<Integer> {
   public static final int[] PRESETS = new int[]{-4883457, -3561729, -7359233, -34150, -42396, -19620, -12470, -9444394, -9120662, -921608};

   public ColorSetting(String name, int def) {
      super(name, def);
   }

   public int argb() {
      return this.value;
   }

   @Override
   public JsonElement save() {
      return new JsonPrimitive(String.format("#%08X", this.value));
   }

   @Override
   public void load(JsonElement e) {
      if (e != null && e.isJsonPrimitive()) {
         try {
            String s = e.getAsString().replace("#", "");
            this.value = (int)Long.parseLong(s, 16);
         } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid colour setting: " + this.name, ex);
         }
      }
   }
}
