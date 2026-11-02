package dev.nightfall.modules;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;

public class SpawnerEsp extends BlockEntityEsp {
    public SpawnerEsp() { super("SpawnerESP", "Markiert Spawner.", 128, 256, 0xEC4899); }
    @Override protected boolean accept(BlockEntity be) { return be instanceof MobSpawnerBlockEntity; }
    @Override protected String nameOf(BlockEntity be) { return "Spawner"; }
}
