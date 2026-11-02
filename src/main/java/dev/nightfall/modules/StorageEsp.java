package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.block.entity.*;

public class StorageEsp extends BlockEntityEsp {
    private final BoolSetting chests   = add(new BoolSetting("Chests", true));
    private final BoolSetting barrels  = add(new BoolSetting("Barrels", true));
    private final BoolSetting shulkers = add(new BoolSetting("Shulker", true));
    private final BoolSetting ender    = add(new BoolSetting("Ender Chest", false));
    private final BoolSetting hoppers  = add(new BoolSetting("Hopper", false));
    private final BoolSetting furnaces = add(new BoolSetting("Furnace", false));

    public StorageEsp() { super("StorageESP", "Markiert Kisten, Fasser, Shulker u. a.", 96, 256, 0xF59E0B); }

    @Override protected boolean accept(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return chests.get();
        if (be instanceof BarrelBlockEntity) return barrels.get();
        if (be instanceof ShulkerBoxBlockEntity) return shulkers.get();
        if (be instanceof EnderChestBlockEntity) return ender.get();
        if (be instanceof HopperBlockEntity) return hoppers.get();
        if (be instanceof AbstractFurnaceBlockEntity) return furnaces.get();
        return false;
    }

    @Override protected String nameOf(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return "Chest";
        if (be instanceof BarrelBlockEntity) return "Barrel";
        if (be instanceof ShulkerBoxBlockEntity) return "Shulker";
        if (be instanceof EnderChestBlockEntity) return "Ender";
        if (be instanceof HopperBlockEntity) return "Hopper";
        return "Furnace";
    }
}
