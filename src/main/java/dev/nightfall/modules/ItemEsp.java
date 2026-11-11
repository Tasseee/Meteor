package dev.nightfall.modules;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;

public class ItemEsp extends EntityScanEsp {
    public ItemEsp() { super("LootDropESP", "Markiert jedes geladene gedroppte Item in grosser Reichweite.", 512, 512, 0x22C55E); }
    @Override protected boolean accept(Entity e) { return e instanceof ItemEntity; }
    @Override protected String nameOf(Entity e) {
        ItemEntity ie = (ItemEntity) e;
        return ie.getStack().getName().getString() + " x" + ie.getStack().getCount();
    }
}
