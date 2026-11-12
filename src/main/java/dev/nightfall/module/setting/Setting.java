package dev.nightfall.module.setting;

import com.google.gson.JsonElement;

public abstract class Setting<T> {
   private final String name;
   protected T value;

   protected Setting(String name, T def) {
      this.name = name;
      this.value = def;
   }

   public String getName() {
      return this.name;
   }

   public T get() {
      return this.value;
   }

   public abstract JsonElement save();

   public abstract void load(JsonElement var1);
}
