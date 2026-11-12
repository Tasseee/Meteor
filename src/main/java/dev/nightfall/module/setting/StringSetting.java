package dev.nightfall.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class StringSetting extends Setting<String> {
   private final int maxLength;

   public StringSetting(String name, String def, int maxLength) {
      super(name, def);
      this.maxLength = Math.max(1, maxLength);
   }

   public void set(String text) {
      if (text != null) {
         String clean = text.replaceAll("[\\r\\n\\t]", " ").trim();
         this.value = clean.substring(0, Math.min(clean.length(), this.maxLength));
      }
   }

   @Override
   public JsonElement save() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void load(JsonElement e) {
      if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) {
         this.set(e.getAsString());
      }
   }
}
