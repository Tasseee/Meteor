package dev.nightfall.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class ModeSetting extends Setting<String> {
   private final String[] modes;
   private int index;

   public ModeSetting(String name, String def, String... modes) {
      super(name, def);
      this.modes = modes;

      for (int i = 0; i < modes.length; i++) {
         if (modes[i].equals(def)) {
            this.index = i;
         }
      }

      this.value = modes[this.index];
   }

   public void cycle(int dir) {
      this.index = Math.floorMod(this.index + dir, this.modes.length);
      this.value = this.modes[this.index];
   }

   public void set(String mode) {
      for (int i = 0; i < this.modes.length; i++) {
         if (this.modes[i].equals(mode)) {
            this.index = i;
            this.value = mode;
            return;
         }
      }
   }

   public boolean is(String m) {
      return this.value.equals(m);
   }

   @Override
   public JsonElement save() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void load(JsonElement e) {
      if (e != null && e.isJsonPrimitive()) {
         String s = e.getAsString();

         for (int i = 0; i < this.modes.length; i++) {
            if (this.modes[i].equals(s)) {
               this.index = i;
               this.value = s;
            }
         }
      }
   }
}
