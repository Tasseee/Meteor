package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Alerts when selected valuable items are newly dropped nearby. */
public final class DroppedItemNotifier extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", 48, 8, 128, 8));
    private final BoolSetting elytra = add(new BoolSetting("Elytra", true));
    private final BoolSetting mace = add(new BoolSetting("Mace", true));
    private final BoolSetting totem = add(new BoolSetting("Totem", true));
    private final BoolSetting heavyCore = add(new BoolSetting("Heavy Core", true));
    private final BoolSetting netherite = add(new BoolSetting("Netherite-Barren", true));
    private final BoolSetting diamond = add(new BoolSetting("Diamanten", true));
    private final BoolSetting emerald = add(new BoolSetting("Emeralds", true));
    private final BoolSetting enchantedApple = add(new BoolSetting("Verzauberter Goldapfel", true));
    private final BoolSetting sound = add(new BoolSetting("Sound bei Fund", true));
    private final BoolSetting chat = add(new BoolSetting("Chat-Nachricht", true));

    private final Set<UUID> seenItems = new HashSet<>();
    private boolean baselineReady;

    public DroppedItemNotifier() {
        super("DroppedItemNotifier", "Warnt, wenn ausgewaehlte wertvolle Items in der Naehe gedroppt werden.", Category.VISUALS);
    }

    @Override
    protected void onEnable() {
        seenItems.clear();
        baselineReady = false;
    }

    @Override
    protected void onDisable() {
        seenItems.clear();
        baselineReady = false;
    }

    @Override
    public void onWorldLeave() {
        seenItems.clear();
        baselineReady = false;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        Set<UUID> currentlyPresent = new HashSet<>();
        double maxDistanceSq = range.get() * range.get();

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity itemEntity)
                    || itemEntity == mc.player
                    || itemEntity.squaredDistanceTo(mc.player) > maxDistanceSq) continue;

            UUID id = itemEntity.getUuid();
            currentlyPresent.add(id);
            if (!baselineReady || seenItems.contains(id)) continue;

            ItemStack stack = itemEntity.getStack();
            String label = selectedItemName(stack);
            if (label == null) continue;

            if (chat.get()) {
                mc.player.sendMessage(Text.literal("[Nightfall] ITEM GEFUNDEN: " + label
                        + " x" + stack.getCount()), false);
            }
            if (sound.get()) {
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
            }
        }

        seenItems.retainAll(currentlyPresent);
        seenItems.addAll(currentlyPresent);
        baselineReady = true;
    }

    private String selectedItemName(ItemStack stack) {
        if (elytra.get() && stack.isOf(Items.ELYTRA)) return "Elytra";
        if (mace.get() && stack.isOf(Items.MACE)) return "Mace";
        if (totem.get() && stack.isOf(Items.TOTEM_OF_UNDYING)) return "Totem";
        if (heavyCore.get() && stack.isOf(Items.HEAVY_CORE)) return "Heavy Core";
        if (netherite.get() && stack.isOf(Items.NETHERITE_INGOT)) return "Netherite-Barren";
        if (diamond.get() && stack.isOf(Items.DIAMOND)) return "Diamanten";
        if (emerald.get() && stack.isOf(Items.EMERALD)) return "Emeralds";
        if (enchantedApple.get() && stack.isOf(Items.ENCHANTED_GOLDEN_APPLE)) return "Verzauberter Goldapfel";
        return null;
    }
}
