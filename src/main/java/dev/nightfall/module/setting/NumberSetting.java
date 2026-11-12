package dev.nightfall.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting<Double> {
   public final double min;
   public final double max;
   public final double step;

   public NumberSetting(String name, double def, double min, double max, double step) {
      super(name, def);
      this.min = min;
      this.max = max;
      this.step = step;
   }

   public void set(double v) {
      v = Math.round(v / this.step) * this.step;
      this.value = Math.max(this.min, Math.min(this.max, v));
   }

   public int getInt() {
      return (int)Math.round(this.value);
   }

   public String display() {
      return this.step >= 1.0 ? String.valueOf(this.getInt()) : String.format("%.2f", this.value);
   }

   @Override
   public JsonElement save() {
      return new JsonPrimitive(this.value);
   }

   @Override
   public void load(JsonElement e) {
      if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
         this.set(e.getAsDouble());
      }
   }
}
