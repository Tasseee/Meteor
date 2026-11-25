package dev.nightfall.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class KeySetting extends Setting<Integer> {
   public KeySetting(String name, int def) {
      super(name, def);
   }

   public void set(int key) {
      this.value = key;
   }

   @Override
   public JsonElement save() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void load(JsonElement e) {
      if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
         this.value = e.getAsInt();
      }
   }
}
