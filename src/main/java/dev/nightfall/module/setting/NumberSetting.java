package dev.nightfall.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class NumberSetting extends Setting<Double> {
    public final double min, max, step;

    public NumberSetting(String name, double def, double min, double max, double step) {
        super(name, def);
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max) {
            throw new IllegalArgumentException("Ungueltiger Wertebereich fuer " + name);
        }
        if (!Double.isFinite(step) || step <= 0.0) {
            throw new IllegalArgumentException("Step muss endlich und groesser als 0 sein: " + name);
        }
        this.min = min;
        this.max = max;
        this.step = step;
        set(def);
    }

    public void set(double v) {
        // Ignore invalid runtime/config values instead of poisoning the setting with NaN/Infinity.
        if (!Double.isFinite(v)) return;

        // Snap relative to min so ranges such as 0.6..2.5 with step 0.1 stay aligned.
        double snapped = min + Math.round((v - min) / step) * step;
        value = Math.max(min, Math.min(max, snapped));
    }

    public int getInt() { return (int) Math.round(value); }
    public String display() { return step >= 1 ? String.valueOf(getInt()) : String.format("%.2f", value); }

    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            set(e.getAsDouble());
        }
    }
}
