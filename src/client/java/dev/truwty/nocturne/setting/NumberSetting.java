package dev.truwty.nocturne.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class NumberSetting extends Setting<Double> {
   public final double min;
   public final double max;
   public final double step;
   public final String unit;

   public NumberSetting(String name, double def, double min, double max, double step, String unit) {
      super(name, def);
      this.min = min;
      this.max = max;
      this.step = step;
      this.unit = unit;
   }

   public float f() {
      return this.value.floatValue();
   }

   public int i() {
      return (int)Math.round(this.value);
   }

   public float fraction() {
      return (float)((this.value - this.min) / (this.max - this.min));
   }

   public void setFraction(double p) {
      double raw = this.min + Math.max(0.0, Math.min(1.0, p)) * (this.max - this.min);
      double snapped = Math.round(raw / this.step) * this.step;
      this.value = Math.max(this.min, Math.min(this.max, Math.round(snapped * 1000.0) / 1000.0));
   }

   public String display() {
      String n = this.step < 1.0 ? String.format("%.1f", this.value) : String.valueOf(Math.round(this.value));
      return n + this.unit;
   }

   @Override
   public JsonElement save() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void load(JsonElement e) {
      if (e != null && e.isJsonPrimitive()) {
         double candidate = e.getAsDouble();
         if (!Double.isFinite(candidate)) throw new IllegalArgumentException("Non-finite setting: " + this.name);
         this.value = Math.max(this.min, Math.min(this.max, candidate));
      }
   }
}
