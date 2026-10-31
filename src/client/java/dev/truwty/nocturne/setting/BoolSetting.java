package dev.truwty.nocturne.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class BoolSetting extends Setting<Boolean> {
   public BoolSetting(String name, boolean def) {
      super(name, def);
   }

   public boolean on() {
      return this.value;
   }

   public void toggle() {
      this.value = !this.value;
   }

   @Override
   public JsonElement save() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void load(JsonElement e) {
      if (e != null && e.isJsonPrimitive()) {
         this.value = e.getAsBoolean();
      }
   }
}
