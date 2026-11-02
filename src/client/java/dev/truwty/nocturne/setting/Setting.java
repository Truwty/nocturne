package dev.truwty.nocturne.setting;

import com.google.gson.JsonElement;

public abstract class Setting<T> {
   public final String name;
   protected T value;
   public final T defaultValue;

   protected Setting(String name, T defaultValue) {
      this.name = name;
      this.value = defaultValue;
      this.defaultValue = defaultValue;
   }

   public T get() {
      return this.value;
   }

   public void set(T v) {
      this.value = v;
   }

   public abstract JsonElement save();

   public abstract void load(JsonElement var1);
}
