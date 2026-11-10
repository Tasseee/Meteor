package dev.nightfall.modules;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.InvUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
/** Eats a golden apple at low health and can pull it from the main inventory. */
public final class AutoGap extends Module {
    private final NumberSetting health=add(new NumberSetting("Health-Schwelle",10,2,19,1));
    private boolean eating,used;
    private int previousSlot,ticks,cooldown;
    public AutoGap() { super("AutoGap","Isst bei niedriger Health einen goldenen Apfel.",Category.COMBAT); }
    @Override protected void onDisable() { finish(); }
    @Override public void onWorldLeave() { finish();cooldown=0; }
    private void finish() {
        if(eating) { mc.options.useKey.setPressed(false);InvUtil.select(previousSlot); }
        eating=false;used=false;ticks=0;
    }
    @Override public void onTick() {
        ClientPlayerEntity p=mc.player;if(p==null) return;
        if(cooldown>0) cooldown--;
        if(eating) {
            ticks++;if(p.isUsingItem()) used=true;
            if((used&&!p.isUsingItem())||ticks>60) { finish();cooldown=32; }
            else mc.options.useKey.setPressed(true);
            return;
        }
        if(cooldown>0||p.isUsingItem()||mc.currentScreen!=null) return;
        if(p.getHealth()+p.getAbsorptionAmount()>health.get()) return;
        int slot=InvUtil.ensureHotbar(Items.ENCHANTED_GOLDEN_APPLE);
        if(slot<0) slot=InvUtil.ensureHotbar(Items.GOLDEN_APPLE);
        if(slot<0) return;
        previousSlot=InvUtil.selected();InvUtil.select(slot);
        mc.options.useKey.setPressed(true);eating=true;
    }
}
