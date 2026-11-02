package dev.nightfall.module.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Text setting persisted in the local Nightfall config. */
public class StringSetting extends Setting<String> {
    private final int maxLength;
    public StringSetting(String name, String def, int maxLength) {
        super(name, def);
        this.maxLength = Math.max(1, maxLength);
    }
    public void set(String text) {
        if (text == null) return;
        String clean = text.replaceAll("[\\r\\n\\t]", " ").trim();
        value = clean.substring(0, Math.min(clean.length(), maxLength));
    }
    @Override public JsonElement save() { return new JsonPrimitive(value); }
    @Override public void load(JsonElement e) {
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) set(e.getAsString());
    }
}
