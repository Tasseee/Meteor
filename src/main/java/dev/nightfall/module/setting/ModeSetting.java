package dev.nightfall.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class ModeSetting extends Setting<String> {
    private final String[] modes;
    private int index;

    public ModeSetting(String name, String def, String... modes) {
        super(name, def);
        this.modes = modes;
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(def)) index = i;
        this.value = modes[index];
    }

    public void cycle(int dir) {
        index = Math.floorMod(index + dir, modes.length);
        value = modes[index];
    }
    public void set(String mode) {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(mode)) { index = i; value = mode; return; }
        }
    }
    public boolean is(String m) { return value.equals(m); }

    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return;
        String s = e.getAsString();
        for (int i = 0; i < modes.length; i++) if (modes[i].equals(s)) { index = i; value = s; }
    }
}
