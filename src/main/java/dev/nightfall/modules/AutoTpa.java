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
/** Accepts chat-announced teleport requests. TPAHere acceptance is separately opt-in. */
public final class AutoTpa extends Module {
    private static final Pattern TPA=Pattern.compile("(?i)(?:^|\\s)([A-Za-z0-9_]{3,16})\\s+(?:has\\s+)?(?:requested|asked|wants|would like|is requesting)\\s+(?:to\\s+)?teleport(?:ation)?\\s+to\\s+you");
    private static final Pattern TPA_HERE=Pattern.compile("(?i)(?:^|\\s)([A-Za-z0-9_]{3,16})\\s+(?:has\\s+)?(?:requested|asked|wants|would like|is requesting)\\s+(?:that\\s+)?you\\s+teleport\\s+(?:to\\s+)?(?:them|him|her|their location|there)");
    private static final String[] HERE_HINTS={"tpahere","you to teleport","you teleport to","teleport you to","teleport to them","teleport to him","teleport to her"};
    private final NumberSetting delay=add(new NumberSetting("Verzoegerung (Ticks)",3,0,200,1));
    private final ModeSetting filter=add(new ModeSetting("Filter","Aus","Aus","Whitelist","Blacklist"));
    private final BoolSetting withName=add(new BoolSetting("tpaccept mit Name",false));
    private final BoolSetting acceptHere=add(new BoolSetting("TPAHere annehmen",false));
    private final BoolSetting info=add(new BoolSetting("Chat-Info",true));
    public final ListSetting whitelist=add(new ListSetting("Whitelist"));
    public final ListSetting blacklist=add(new ListSetting("Blacklist"));
    private static final class Pending { String name;int ticks;boolean here; }
    private final List<Pending> pending=new ArrayList<>();
    public AutoTpa() { super("AutoTPA","Nimmt TPA-Anfragen an; TPAHere ist separat abschaltbar.",Category.MISC); }
    @Override protected void onDisable() { pending.clear(); }
    @Override public void onWorldLeave() { pending.clear(); }
    public int pendingCount() { return pending.size(); }
    public void onMessage(String raw) {
        if(!isEnabled()||mc.player==null||raw==null) return;
        String lower=raw.toLowerCase(Locale.ROOT);Matcher matcher=TPA.matcher(raw);boolean here=false;
        if(!matcher.find()) {
            if(!acceptHere.get()) return;
            boolean hinted=false;for(String hint:HERE_HINTS) if(lower.contains(hint)) { hinted=true;break; }
            if(!hinted) return;
            matcher=TPA_HERE.matcher(raw);if(!matcher.find()) return;here=true;
        }
        String name=matcher.group(1);
        if(name.equalsIgnoreCase(mc.player.getName().getString())) return;
        switch(filter.get()) {
            case "Whitelist" -> { if(!whitelist.has(name)) return; }
            case "Blacklist" -> { if(blacklist.has(name)) return; }
            default -> {}
        }
        for(Pending item:pending) if(item.name.equalsIgnoreCase(name)) return;
        Pending item=new Pending();item.name=name;item.ticks=delay.getInt();item.here=here;pending.add(item);
    }
    @Override public void onTick() {
        if(mc.player==null||mc.player.networkHandler==null) return;
        for(int i=pending.size()-1;i>=0;i--) {
            Pending item=pending.get(i);if(item.ticks-->0) continue;pending.remove(i);
            boolean named=withName.get()||item.here;
            mc.player.networkHandler.sendChatCommand(named?"tpaccept "+item.name:"tpaccept");
            if(info.get()) mc.player.sendMessage(Text.literal("[Nightfall] TPA"+(item.here?"Here":"")+" von "+item.name+" angenommen"),false);
        }
    }
}
