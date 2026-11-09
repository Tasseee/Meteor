package dev.nightfall.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;

public final class InvUtil {
    private InvUtil() {}

    public static int findHotbar(Item item) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return -1;
        for (int i = 0; i < 9; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    /** Index 0..35 im Spielerinventar (0-8 Hotbar). */
    public static int findInventory(Item item) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null) return -1;
        for (int i = 0; i < 36; i++) if (p.getInventory().getStack(i).isOf(item)) return i;
        return -1;
    }

    public static ItemStack hotbarStack(int slot) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        return p == null ? ItemStack.EMPTY : p.getInventory().getStack(slot);
    }

    public static int selected() {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        return p == null ? 0 : p.getInventory().getSelectedSlot();
    }

    public static boolean select(int slot) {
        ClientPlayerEntity p = MinecraftClient.getInstance().player;
        if (p == null || slot < 0 || slot > 8) return false;
        if (p.getInventory().getSelectedSlot() != slot) p.getInventory().setSelectedSlot(slot);
        return true;
    }

    /** Tauscht Inventar-Slot (0..35) mit der Offhand (Container 0, SWAP-Taste 40). */
    public static void swapWithOffhand(int invIndex) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null
                || invIndex < 0 || invIndex > 35) return;
        int slotId = invIndex < 9 ? 36 + invIndex : invIndex;
        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, slotId, 40,
                SlotActionType.SWAP, mc.player);
    }
}
