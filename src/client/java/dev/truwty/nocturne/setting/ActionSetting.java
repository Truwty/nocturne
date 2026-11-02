package dev.truwty.nocturne.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import java.util.function.Supplier;

public class ActionSetting extends Setting<Boolean> {
   public final Supplier<String> label;
   public final Runnable action;

   public ActionSetting(String name, Supplier<String> label, Runnable action) {
      super(name, false);
      this.label = label;
      this.action = action;
   }

   @Override
   public JsonElement save() {
      return JsonNull.INSTANCE;
   }

   @Override
   public void load(JsonElement e) {
   }
}
