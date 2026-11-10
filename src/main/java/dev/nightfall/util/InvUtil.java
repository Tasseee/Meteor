package dev.nightfall.util;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
public final class InvUtil {
    private InvUtil() {}
    public static int findHotbar(Item item) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return -1;
        for (int i=0;i<9;i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    /** Player inventory index 0..35. */
    public static int findInventory(Item item) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return -1;
        for (int i=0;i<36;i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }
    /** Moves an item from main inventory into hotbar, preserving the selected slot where possible. */
    public static int ensureHotbar(Item item) {
        MinecraftClient client=MinecraftClient.getInstance(); ClientPlayerEntity p=client.player;
        if(p==null || client.interactionManager==null || p.currentScreenHandler!=p.playerScreenHandler) return -1;
        int existing=findHotbar(item); if(existing>=0) return existing;
        int source=findInventory(item); if(source<9) return -1;
        int destination=-1;
        for(int i=0;i<9;i++) if(p.getInventory().getStack(i).isEmpty()) { destination=i;break; }
        if(destination<0) {
            int selected=p.getInventory().getSelectedSlot();
            for(int i=0;i<9;i++) if(i!=selected) { destination=i;break; }
        }
        if(destination<0) destination=p.getInventory().getSelectedSlot();
        client.interactionManager.clickSlot(p.playerScreenHandler.syncId,source,destination,SlotActionType.SWAP,p);
        return destination;
    }
    public static int findBestSword() {
        Item[] priority={Items.NETHERITE_SWORD,Items.DIAMOND_SWORD,Items.IRON_SWORD,Items.STONE_SWORD,Items.GOLDEN_SWORD,Items.WOODEN_SWORD};
        for(Item item:priority) { int slot=ensureHotbar(item); if(slot>=0) return slot; }
        return -1;
    }
    public static ItemStack hotbarStack(int slot) {
        ClientPlayerEntity p=MinecraftClient.getInstance().player;
        return p==null || slot<0 || slot>8 ? ItemStack.EMPTY:p.getInventory().getStack(slot);
    }
    public static int selected() {
        ClientPlayerEntity p=MinecraftClient.getInstance().player;
        return p==null?0:p.getInventory().getSelectedSlot();
    }
    public static boolean select(int slot) {
        ClientPlayerEntity p=MinecraftClient.getInstance().player;
        if(p==null || slot<0 || slot>8) return false;
        if(p.getInventory().getSelectedSlot()!=slot) p.getInventory().setSelectedSlot(slot);
        return true;
    }
    public static boolean swapWithOffhand(int index) {
        MinecraftClient client=MinecraftClient.getInstance();
        ClientPlayerEntity player=client.player;
        if(player==null || client.interactionManager==null || player.currentScreenHandler!=player.playerScreenHandler || index<0 || index>35) return false;
        int slotId=index<9?36+index:index;
        client.interactionManager.clickSlot(player.playerScreenHandler.syncId,slotId,40,SlotActionType.SWAP,player);
        return true;
    }
}
