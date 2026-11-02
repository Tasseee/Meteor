package dev.nightfall.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Namensliste (z. B. Whitelist). Wird ueber Chat-Befehle bearbeitet, im GUI nur als Anzahl gezeigt. */
public class ListSetting extends Setting<Set<String>> {
    public ListSetting(String name) { super(name, new TreeSet<>()); }
    public boolean add(String s) { return value.add(s.toLowerCase(Locale.ROOT)); }
    public boolean remove(String s) { return value.remove(s.toLowerCase(Locale.ROOT)); }
    public boolean has(String s) { return value.contains(s.toLowerCase(Locale.ROOT)); }
    @Override public JsonElement save() { JsonArray a = new JsonArray(); for (String s : value) a.add(s); return a; }
    @Override public void load(JsonElement e) {
        if (e == null || !e.isJsonArray()) return;
        value.clear();
        for (JsonElement x : e.getAsJsonArray()) value.add(x.getAsString().toLowerCase(Locale.ROOT));
    }
}
