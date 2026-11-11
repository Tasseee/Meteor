package dev.nightfall.modules;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;\nimport net.minecraft.world.chunk.WorldChunk;

public class SpawnerEsp extends BlockEntityEsp {
    public SpawnerEsp() { super("SpawnerESP", "Markiert Spawner.", 128, 256, 0xEC4899); }
    @Override protected boolean accept(BlockEntity be) { return be instanceof MobSpawnerBlockEntity; }
    @Override protected String nameOf(BlockEntity be) { return "Spawner"; }\n\n    /** Only reveal a chunk when it contains at least ten loaded monster spawners. */\n    @Override protected boolean acceptChunk(WorldChunk chunk) {\n        int count = 0;\n        for (BlockEntity be : chunk.getBlockEntities().values()) {\n            if (be instanceof MobSpawnerBlockEntity && ++count >= 10) return true;\n        }\n        return false;\n    }
}
