package dev.nightfall.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.nightfall.util.Theme;

public class ColorSetting extends Setting<Integer> {
   public ColorSetting(String name, int rgb) {
      super(name, rgb & 16777215);
   }

   public int rgb() {
      return this.value;
   }

   public void set(int rgb) {
      this.value = rgb & 16777215;
   }

   public void cycle(int dir) {
      int idx = 0;

      for (int i = 0; i < Theme.PALETTE.length; i++) {
         if (Theme.PALETTE[i] == this.value) {
            idx = i;
         }
      }

      this.value = Theme.PALETTE[Math.floorMod(idx + dir, Theme.PALETTE.length)];
   }

   @Override
   public JsonElement save() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void load(JsonElement e) {
      if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
         this.value = e.getAsInt() & 16777215;
      }
   }
}
