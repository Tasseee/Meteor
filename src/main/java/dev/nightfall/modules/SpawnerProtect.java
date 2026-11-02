package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.NotificationCenter;
import net.minecraft.block.Blocks;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Set;

/**
 * Client-side loss warning: remembers visible spawners near the player and alerts if one disappears.
 * It does not claim ownership, identify the breaker, or move items automatically.
 */
public final class SpawnerProtect extends Module {
    private final NumberSetting radius = add(new NumberSetting("Suchradius", 8, 4, 16, 1));
    private final BoolSetting disconnect = add(new BoolSetting("Bei Verlust trennen", false));
    private final Set<BlockPos> tracked = new HashSet<>();
    private int ticks;

    public SpawnerProtect() {
        super("SpawnerProtect", "Warnt, wenn ein zuvor sichtbarer Spawner in der Nähe verschwindet.", Category.MISC);
    }

    @Override protected void onEnable() { tracked.clear(); ticks = 0; }
    @Override protected void onDisable() { tracked.clear(); }

    @Override public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++ticks % 10 != 0) return;
        BlockPos center = mc.player.getBlockPos();
        int r = radius.getInt();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x*x + y*y + z*z > r*r) continue;
                    BlockPos p = center.add(x, y, z);
                    if (mc.world.isChunkLoaded(p) && mc.world.getBlockState(p).isOf(Blocks.SPAWNER))
                        tracked.add(p.toImmutable());
                }
            }
        }

        for (BlockPos p : new HashSet<>(tracked)) {
            if (p.getSquaredDistance(center) > (r + 2.0) * (r + 2.0)) continue;
            if (!mc.world.isChunkLoaded(p)) continue;
            if (!mc.world.getBlockState(p).isOf(Blocks.SPAWNER)) {
                tracked.remove(p);
                String msg = "Spawner bei " + p.getX() + " " + p.getY() + " " + p.getZ() + " ist nicht mehr sichtbar.";
                NotificationCenter.push("SpawnerProtect", msg, 8_000L, 0xFFFF5D6C);
                mc.player.sendMessage(Text.literal("[Nightfall] WARNUNG: " + msg), false);
                if (disconnect.get() && mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().getConnection().disconnect(Text.literal("Nightfall SpawnerProtect: Spawner verschwunden"));
                    return;
                }
            }
        }
    }
}
