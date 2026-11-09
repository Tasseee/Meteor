package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Fills an already-open vanilla furnace/smoker/blast-furnace screen.
 * It only shift-clicks known smeltables/fuels into empty input/fuel slots.
 */
public final class AutoSmelter extends Module {
    private final NumberSetting interval = add(new NumberSetting("Intervall (Ticks)", 10, 5, 40, 1));
    private int ticks;

    public AutoSmelter() {
        super("AutoSmelter", "Befüllt ein geöffnetes Ofen-Menü vorsichtig mit passenden Items und Brennstoff.", Category.WORLD);
    }

    @Override protected void onEnable() { ticks = 0; }
    @Override protected void onDisable() { ticks = 0; }
    @Override public void onWorldLeave() { ticks = 0; }

    @Override public void onTick() {
        if (mc.player == null || mc.interactionManager == null
                || !(mc.player.currentScreenHandler instanceof AbstractFurnaceScreenHandler handler)) return;
        if (++ticks < interval.getInt()) return;
        ticks = 0;

        if (!handler.getSlot(0).hasStack()) {
            for (int i = 3; i < handler.slots.size(); i++) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (isSmeltable(stack)) {
                    mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return;
                }
            }
        }
        if (!handler.getSlot(1).hasStack()) {
            for (int i = 3; i < handler.slots.size(); i++) {
                ItemStack stack = handler.getSlot(i).getStack();
                if (isFuel(stack)) {
                    mc.interactionManager.clickSlot(handler.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return;
                }
            }
        }
    }

    private static boolean isSmeltable(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        return id.startsWith("raw_") || id.endsWith("_ore") || id.equals("cobblestone")
                || id.equals("sand") || id.equals("clay_ball") || id.equals("netherrack")
                || id.equals("wet_sponge") || id.equals("kelp") || id.equals("chorus_fruit");
    }

    private static boolean isFuel(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        return id.equals("coal") || id.equals("charcoal") || id.equals("blaze_rod")
                || id.endsWith("_planks") || id.endsWith("_log") || id.endsWith("_wood");
    }
}
