package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.*;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nimmt normale TPA-Anfragen automatisch an, TPAHere-Anfragen werden ignoriert.
 * Erkennung ueber Chat-Muster (Essentials-aehnliche Formulierungen) - andere Plugins koennen abweichen.
 */
public class AutoTpa extends Module {
    private static final Pattern TPA = Pattern.compile(
            "(?i)(?:^|\\s)(\\w{3,16})\\s+(?:has\\s+)?(?:requested|asked|wants|would like|is requesting)\\s+(?:to\\s+)?teleport(?:ation)?\\s+to\\s+you");
    private static final String[] HERE = {"tpahere", "you to teleport", "you teleport to", "teleport you to", "teleport to them", "teleport to him", "teleport to her"};

    private final NumberSetting delay   = add(new NumberSetting("Verzoegerung (Ticks)", 10, 0, 200, 1));
    private final ModeSetting   filter  = add(new ModeSetting("Filter", "Aus", "Aus", "Whitelist", "Blacklist"));
    private final BoolSetting   withName = add(new BoolSetting("tpaccept mit Name", false));
    private final BoolSetting   info    = add(new BoolSetting("Chat-Info", true));
    public final ListSetting whitelist = add(new ListSetting("Whitelist"));
    public final ListSetting blacklist = add(new ListSetting("Blacklist"));

    private static final class Pending { String name; int ticks; }
    private final List<Pending> pending = new ArrayList<>();

    public AutoTpa() { super("AutoTPA", "Nimmt /tpa-Anfragen automatisch an (nicht /tpahere).", Category.MISC); }

    @Override protected void onDisable() { pending.clear(); }
    @Override public void onWorldLeave() { pending.clear(); }
    public int pendingCount() { return pending.size(); }

    /** Wird fuer jede Chatnachricht aufgerufen (Hauptthread). */
    public void onMessage(String raw) {
        if (!isEnabled() || mc.player == null) return;
        String lower = raw.toLowerCase(Locale.ROOT);
        for (String h : HERE) if (lower.contains(h)) return;      // TPAHere: ignorieren
        Matcher m = TPA.matcher(raw);
        if (!m.find()) return;
        String name = m.group(1);
        if (name.equalsIgnoreCase(mc.player.getName().getString())) return;
        switch (filter.get()) {
            case "Whitelist" -> { if (!whitelist.has(name)) return; }
            case "Blacklist" -> { if (blacklist.has(name)) return; }
            default -> { }
        }
        for (Pending p : pending) if (p.name.equalsIgnoreCase(name)) return; // keine Doppelanfragen
        Pending p = new Pending();
        p.name = name; p.ticks = delay.getInt();
        pending.add(p);
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.networkHandler == null) return;
        for (int i = pending.size() - 1; i >= 0; i--) {
            Pending p = pending.get(i);
            if (p.ticks-- > 0) continue;
            pending.remove(i);
            mc.player.networkHandler.sendChatCommand(withName.get() ? "tpaccept " + p.name : "tpaccept");
            if (info.get()) mc.player.sendMessage(Text.literal("[Nightfall] TPA von " + p.name + " angenommen"), false);
        }
    }
}
