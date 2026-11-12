package dev.nightfall.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BoolSetting extends Setting<Boolean> {
   public BoolSetting(String name, boolean def) {
      super(name, def);
   }

   public void toggle() {
      this.value = !this.value;
   }

   public void set(boolean v) {
      this.value = v;
   }

   @Override
   public JsonElement save() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void load(JsonElement e) {
      if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) {
         this.value = e.getAsBoolean();
      }
   }
}
