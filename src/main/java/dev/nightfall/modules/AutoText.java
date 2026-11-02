package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.MessageListSetting;
import dev.nightfall.util.NotificationCenter;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Sends user-configured chat lines at a conservative, configurable interval. */
public final class AutoText extends Module {
    public final MessageListSetting messages = add(new MessageListSetting("Nachrichten"));
    private final NumberSetting interval = add(new NumberSetting("Intervall (Sek.)", 30, 5, 600, 5));
    private final BoolSetting rotate = add(new BoolSetting("Nachrichten rotieren", true));
    private final BoolSetting showInfo = add(new BoolSetting("Statusmeldung", true));
    private int ticks;
    private int nextIndex;

    public AutoText() {
        super("AutoText", "Sendet eigene Chatnachrichten in einem einstellbaren Intervall (mind. 5 Sekunden).", Category.MISC);
    }

    public int intervalSeconds() { return interval.getInt(); }
    public boolean addMessage(String message) {
        String clean = message == null ? "" : message.trim();
        if (clean.isEmpty() || clean.length() > 256) return false;
        return messages.addMessage(clean);
    }
    public boolean removeMessage(String message) { return messages.removeMessage(message); }
    public List<String> messageList() { return new ArrayList<>(messages.get()); }

    @Override protected void onEnable() {
        ticks = 0;
        nextIndex = 0;
        if (messages.get().isEmpty()) {
            NotificationCenter.push("AutoText", "Zuerst /nf autotext add <Nachricht> verwenden");
        }
    }

    @Override protected void onDisable() { ticks = 0; }

    @Override public void onTick() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        List<String> list = messageList();
        if (list.isEmpty()) return;
        ticks++;
        if (ticks < interval.getInt() * 20) return;
        ticks = 0;
        String message = list.get(Math.floorMod(nextIndex, list.size()));
        mc.player.networkHandler.sendChatMessage(message);
        if (rotate.get()) nextIndex++;
        if (showInfo.get()) mc.player.sendMessage(Text.literal("[Nightfall] AutoText gesendet (" + (nextIndex % Math.max(1, list.size()) + 1) + "/" + list.size() + ")"), true);
    }
}
