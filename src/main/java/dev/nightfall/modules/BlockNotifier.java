package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Client-only notifier for block entities present in already loaded chunks.
 * It does not read server-hidden data or infer unopened container contents.
 */
public final class BlockNotifier extends Module {
    private final BoolSetting chests = add(new BoolSetting("Chests/Barrels", true));
    private final BoolSetting shulkers = add(new BoolSetting("Shulker", true));
    private final BoolSetting spawners = add(new BoolSetting("Spawner", true));
    private final BoolSetting hoppers = add(new BoolSetting("Hopper", false));
    private final NumberSetting range = add(new NumberSetting("Range (Blocks)", 48, 8, 128, 8));
    private final Set<Long> seen = new HashSet<>();
    private boolean initialized;
    private int timer;

    public BlockNotifier() {
        super("BlockNotifier", "Meldet neue, clientseitig sichtbare Block-Entities in geladenen Chunks.", Category.VISUALS);
    }

    @Override protected void onEnable() {
        seen.clear();
        initialized = false;
        timer = 0;
    }

    @Override protected void onDisable() {
        seen.clear();
        initialized = false;
    }

    @Override public void onTick() {
        if (mc.world == null || mc.player == null || timer-- > 0) return;
        timer = 20;
        int cx = mc.player.getBlockX() >> 4;
        int cz = mc.player.getBlockZ() >> 4;
        int chunkRange = (range.getInt() + 15) >> 4;
        double maxDistanceSq = (double) range.getInt() * range.getInt();
        Set<Long> current = new HashSet<>();

        for (int dz = -chunkRange; dz <= chunkRange; dz++) {
            for (int dx = -chunkRange; dx <= chunkRange; dx++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx + dx, cz + dz);
                if (chunk == null) continue;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!accept(be)) continue;
                    BlockPos pos = be.getPos();
                    if (pos.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ()) > maxDistanceSq) continue;
                    long key = pos.asLong();
                    current.add(key);
                    if (initialized && !seen.contains(key)) {
                        String type = label(be);
                        mc.player.sendMessage(net.minecraft.text.Text.literal(
                                "[Nightfall] " + type + " entdeckt bei " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                                + " (clientseitig sichtbar; Inhalt nicht geprüft)"), false);
                    }
                }
            }
        }
        seen.clear();
        seen.addAll(current);
        initialized = true;
    }

    private boolean accept(BlockEntity be) {
        if (chests.get() && (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity)) return true;
        if (shulkers.get() && be instanceof ShulkerBoxBlockEntity) return true;
        if (spawners.get() && be instanceof MobSpawnerBlockEntity) return true;
        return hoppers.get() && be instanceof HopperBlockEntity;
    }

    private String label(BlockEntity be) {
        if (be instanceof ChestBlockEntity) return "Chest";
        if (be instanceof BarrelBlockEntity) return "Barrel";
        if (be instanceof ShulkerBoxBlockEntity) return "Shulker";
        if (be instanceof MobSpawnerBlockEntity) return "Spawner";
        if (be instanceof HopperBlockEntity) return "Hopper";
        return "Block-Entity";
    }
}
