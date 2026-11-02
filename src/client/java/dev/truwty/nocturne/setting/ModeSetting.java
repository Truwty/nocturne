package dev.truwty.nocturne.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.List;

public class ModeSetting extends Setting<Integer> {
   public final List<String> options;

   public ModeSetting(String name, int def, String... options) {
      super(name, def);
      this.options = List.of(options);
   }

   public String current() {
      return this.options.get(this.value);
   }

   public boolean is(String option) {
      return this.current().equals(option);
   }

   public void cycle(int dir) {
      this.value = Math.floorMod(this.value + dir, this.options.size());
   }

   @Override
   public JsonElement save() {
      return new JsonPrimitive(this.current());
   }

   @Override
   public void load(JsonElement e) {
      if (e != null && e.isJsonPrimitive()) {
         int i = this.options.indexOf(e.getAsString());
         if (i >= 0) {
            this.value = i;
         }
      }
   }
}
