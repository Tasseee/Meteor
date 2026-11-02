package dev.nightfall.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

/** Ordered, case-preserving list of user-authored chat messages. */
public final class MessageListSetting extends Setting<List<String>> {
    public MessageListSetting(String name) { super(name, new ArrayList<>()); }
    public boolean addMessage(String message) {
        if (message == null || message.isBlank() || value.contains(message)) return false;
        value.add(message);
        return true;
    }
    public boolean removeMessage(String message) { return value.remove(message); }
    @Override public JsonElement save() {
        JsonArray a = new JsonArray();
        for (String s : value) a.add(s);
        return a;
    }
    @Override public void load(JsonElement e) {
        if (e == null || !e.isJsonArray()) return;
        value.clear();
        for (JsonElement item : e.getAsJsonArray()) if (item.isJsonPrimitive()) {
            String s = item.getAsString();
            if (!s.isBlank() && !value.contains(s)) value.add(s);
        }
    }
}
