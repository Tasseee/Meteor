package dev.nightfall.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.nightfall.util.Theme;

public class ColorSetting extends Setting<Integer> {
    public ColorSetting(String name, int rgb) { super(name, rgb & 0xFFFFFF); }
    public int rgb() { return value; }
    public void set(int rgb) { value = rgb & 0xFFFFFF; }
    public void cycle(int dir) {
        int idx = 0;
        for (int i = 0; i < Theme.PALETTE.length; i++) if (Theme.PALETTE[i] == value) idx = i;
        value = Theme.PALETTE[Math.floorMod(idx + dir, Theme.PALETTE.length)];
    }
    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) value = e.getAsInt() & 0xFFFFFF;
    }
}
