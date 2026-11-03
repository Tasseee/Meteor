package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Progressive client-side intelligence scanner.
 * It only reads chunks already loaded by the client. Entity scope is limited to item frames
 * (including glow item frames), villagers and armor stands; player activity is tracked separately.
 */
public final class IntelligenceScanner extends Module {
    private final NumberSetting range = add(new NumberSetting("Intel-Reichweite (Chunks)", 16, 2, 32, 1));
    private final NumberSetting budgetMs = add(new NumberSetting("Deep-Scan Budget ms/Tick", 1.5, 0.2, 5.0, 0.1));
    private final NumberSetting rescanSeconds = add(new NumberSetting("Deep-Rescan (s)", 90, 15, 600, 5));
    private final NumberSetting undergroundDepth = add(new NumberSetting("Untergrund ab (Bloecke)", 5, 3, 20, 1));
    private final BoolSetting entityEsp = add(new BoolSetting("Underground Entity ESP", true));
    private final BoolSetting crystalEsp = add(new BoolSetting("Geoden-Kristalle ESP", true));
    private final BoolSetting geodeBoxEsp = add(new BoolSetting("Geoden-Box ESP", true));
    private final ColorSetting entityColor = add(new ColorSetting("Entity-ESP Farbe", 0xFFB84D));
    private final ColorSetting crystalColor = add(new ColorSetting("Kristall-ESP Farbe", 0x9B5CF6));
    private final ColorSetting geodeColor = add(new ColorSetting("Geoden-Box Farbe", 0x6C55D9));

    private final Map<Long, ScanSnapshot> snapshots = new HashMap<>();
    private final ArrayDeque<ScanJob> queue = new ArrayDeque<>();
    private final Set<Long> inQueue = new HashSet<>();
    private final Set<Long> entityKnownChunks = new HashSet<>();
    private final Map<Long, EntityEvidence> entityEvidence = new HashMap<>();
    private final Map<UUID, Integer> playerSightings = new HashMap<>();
    private final List<Entity> undergroundEntities = new ArrayList<>();

    private ClientWorld lastWorld;
    private long tick, lastEntityRefresh;
    private int scannedChunks, lastEntityCount;

    public IntelligenceScanner() {
        super("Intelligence", "Untergrund-Entities, Geoden, wertvolle Bloecke, Redstone- und Farmhinweise; scannt nur geladene Chunks.", Category.BASEFINDER);
    }

    private static long key(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | ((long) cz << 32);
    }

    public int scannedChunks() { return scannedChunks; }
    public int undergroundEntityCount() { return lastEntityCount; }
    public int pendingChunks() { return queue.size(); }

    @Override
    protected void onDisable() {
        queue.clear();
        inQueue.clear();
        undergroundEntities.clear();
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (lastWorld != mc.world) {
            lastWorld = mc.world;
            tick = 0;
            queue.clear();
            inQueue.clear();
            snapshots.clear();
            entityEvidence.clear();
            entityKnownChunks.clear();
            playerSightings.clear();
            undergroundEntities.clear();
            scannedChunks = 0;
            lastEntityCount = 0;
            lastEntityRefresh = 0;
        }
        tick++;
        if (tick - lastEntityRefresh >= 20) refreshEntities();
        if (tick % 20 == 0) refillQueue();
        runDeepScan();
    }

    private boolean underground(Entity entity) {
        int surface = mc.world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                entity.getBlockX(), entity.getBlockZ());
        return entity.getY() <= surface - undergroundDepth.get();
    }

    private void refreshEntities() {
        lastEntityRefresh = tick;
        undergroundEntities.clear();
        Map<Long, EntityEvidence> next = new HashMap<>();
        List<Entity> accepted = new ArrayList<>();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !underground(entity)) continue;
            if (entity instanceof ItemFrameEntity || entity instanceof VillagerEntity
                    || entity instanceof ArmorStandEntity) {
                accepted.add(entity);
                undergroundEntities.add(entity);
                long k = key(entity.getBlockX() >> 4, entity.getBlockZ() >> 4);
                EntityEvidence ev = next.computeIfAbsent(k, ignored -> new EntityEvidence());
                if (entity instanceof ItemFrameEntity) ev.frames++;
                else if (entity instanceof VillagerEntity) ev.villagers++;
                else if (entity instanceof ArmorStandEntity) ev.armorStands++;
            } else if (entity instanceof PlayerEntity player) {
                long k = key(player.getBlockX() >> 4, player.getBlockZ() >> 4);
                int seen = Math.min(10, playerSightings.merge(player.getUuid(), 1, Integer::sum));
                EntityEvidence ev = next.computeIfAbsent(k, ignored -> new EntityEvidence());
                ev.activityScore = Math.min(3.0, seen * 0.35);
                ev.playerCount++;
            }
        }

        // Loose connected grouping: groups can reinforce across a radius of up to 6 chunks.
        for (int i = 0; i < accepted.size(); i++) {
            Entity a = accepted.get(i);
            int nearby = 0;
            for (int j = 0; j < accepted.size(); j++) {
                if (i != j && a.squaredDistanceTo(accepted.get(j)) <= 96.0 * 96.0) nearby++;
            }
            if (nearby > 0) {
                long k = key(a.getBlockX() >> 4, a.getBlockZ() >> 4);
                next.computeIfAbsent(k, ignored -> new EntityEvidence()).clusterBonus
                        += Math.min(1.5, nearby * 0.25);
            }
        }

        Set<Long> affected = new HashSet<>(entityKnownChunks);
        affected.addAll(next.keySet());
        entityKnownChunks.clear();
        entityKnownChunks.addAll(next.keySet());
        entityEvidence.clear();
        entityEvidence.putAll(next);
        lastEntityCount = undergroundEntities.size();
        for (long k : affected) publish(k, null);
    }

    private void refillQueue() {
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        int r = range.getInt();
        long minAge = rescanSeconds.getInt() * 20L;
        List<long[]> candidates = new ArrayList<>();
        for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
            int d2 = dx * dx + dz * dz;
            if (d2 > r * r) continue;
            int cx = pcx + dx, cz = pcz + dz;
            long k = key(cx, cz);
            if (inQueue.contains(k)) continue;
            ScanSnapshot old = snapshots.get(k);
            if (old != null && tick - old.lastCompleteTick < minAge) continue;
            if (mc.world.getChunkManager().getWorldChunk(cx, cz) == null) continue;
            candidates.add(new long[]{d2, k});
        }
        candidates.sort(Comparator.comparingLong(a -> a[0]));
        for (long[] candidate : candidates) {
            long k = candidate[1];
            inQueue.add(k);
            queue.add(new ScanJob((int) k, (int) (k >> 32),
                    mc.world.getBottomY(), mc.world.getHeight()));
        }
    }

    private void runDeepScan() {
        long deadline = System.nanoTime() + (long) (budgetMs.get() * 1_000_000.0);
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            ScanJob job = queue.poll();
            WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(job.cx, job.cz);
            if (chunk == null) {
                inQueue.remove(job.key);
                continue;
            }
            int processed = 0;
            BlockPos.Mutable pos = new BlockPos.Mutable();
            while (job.cursor < job.total && System.nanoTime() < deadline && processed < 4096) {
                int column = job.cursor / job.height;
                int y = job.bottomY + job.cursor % job.height;
                int x = column & 15, z = column >> 4;
                Block block = chunk.getBlockState(pos.set(job.cx * 16 + x, y, job.cz * 16 + z)).getBlock();
                job.accept(block, pos.getX(), pos.getY(), pos.getZ());
                job.cursor++;
                processed++;
            }
            if (job.cursor >= job.total) {
                ScanSnapshot previous = snapshots.get(job.key);
                job.harvestObserved = previous != null && previous.matureCrystals > job.matureCrystals
                        && job.budding >= previous.budding;
                ScanSnapshot complete = job.snapshot(tick);
                snapshots.put(job.key, complete);
                scannedChunks++;
                inQueue.remove(job.key);
                publish(job.key, complete);
            } else {
                publish(job.key, job.snapshot(tick));
                queue.add(job);
            }
        }
    }

    private void publish(long k, ScanSnapshot supplied) {
        ScanSnapshot scan = supplied != null ? supplied : snapshots.get(k);
        EntityEvidence entities = entityEvidence.getOrDefault(k, new EntityEvidence());
        double entityScore = Math.max(0, entities.frames - 2) * 1.25
                + entities.villagers * 3.0 + entities.armorStands * 2.5 + entities.clusterBonus;
        double geodeScore = 0, valuableScore = 0, redstoneScore = 0, farmScore = 0;
        double completeness = scan == null ? 0 : scan.completeness;
        List<String> reasons = new ArrayList<>();

        if (entities.frames > 0) reasons.add(entities.frames + " Item Frames unterirdisch (Glow inklusive)");
        if (entities.villagers > 0) reasons.add(entities.villagers + " Villager unterirdisch");
        if (entities.armorStands > 0) reasons.add(entities.armorStands + " Armor Stands unterirdisch");
        if (entities.clusterBonus > 0) reasons.add("Entity-Gruppe innerhalb 6 Chunks");
        if (entities.playerCount > 0) reasons.add(entities.playerCount + " Spielerbeobachtung(en) unterirdisch");

        if (scan != null) {
            if (scan.budding > 0) {
                geodeScore += 0.45;
                reasons.add("Geode: " + scan.budding + " Budding, " + scan.buds
                        + " Buds, " + scan.matureCrystals + " reife Cluster");
            }
            if (scan.harvestObserved) {
                geodeScore += 1.75;
                reasons.add("Harvest-Verdacht: Kristallzahl seit letzter Messung gesunken");
            }
            if (scan.budding >= 4 && scan.matureCrystals >= 8) {
                geodeScore += 1.0;
                farmScore += 1.25;
                reasons.add("Geodenfarm-Kandidat (Budding + reife Kristalle)");
            }
            if (scan.valuableBlocks > 0) {
                valuableScore = Math.min(15.0, scan.valuableBlocks * 3.0);
                reasons.add(scan.valuableBlocks + " wertvolle Bloecke (Netherite/Diamond/Dragon Head/Emerald/Iron)");
            }
            if (scan.redstoneBlocks > 0) {
                redstoneScore = scan.redstoneBlocks >= 12
                        ? Math.min(4.0, 0.35 + scan.redstoneBlocks * 0.12)
                        : scan.redstoneBlocks * 0.08;
                reasons.add(scan.redstoneBlocks + " Redstone-/Maschinenbloecke (Sculk Sensor ausgeschlossen)");
            }
            if (scan.farmBlocks > 0) {
                farmScore = Math.max(farmScore, scan.farmBlocks >= 24
                        ? Math.min(5.0, scan.farmBlocks / 16.0) : scan.farmBlocks * 0.025);
                reasons.add(scan.farmBlocks + " Farm-/Anbaubloecke");
            }
            if (scan.calcite + scan.smoothBasalt > 0 && scan.budding > 0)
                reasons.add("Geodenstruktur mit Calcite/Smooth Basalt");
        }

        double activityScore = entities.activityScore;
        if (activityScore > 0) reasons.add("Wiederholte unterirdische Spieleraktivitaet");
        try {
            dev.nightfall.module.ModuleManager.get(BaseFinder.class).updateIntelligence(
                    (int) k, (int) (k >> 32), entityScore, geodeScore, valuableScore,
                    redstoneScore, farmScore, activityScore, completeness, reasons);
        } catch (IllegalStateException ignored) { }
    }

    private final class ScanJob {
        final int cx, cz, bottomY, height, total;
        final long key;
        int cursor;
        int budding, smallBuds, mediumBuds, largeBuds, matureCrystals, amethystBlocks, calcite, smoothBasalt;
        int valuableBlocks, redstoneBlocks, farmBlocks;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        boolean harvestObserved;
        final List<BlockPos> crystalPositions = new ArrayList<>();

        ScanJob(int cx, int cz, int bottomY, int height) {
            this.cx = cx; this.cz = cz; this.bottomY = bottomY; this.height = Math.max(1, height);
            this.total = 256 * this.height;
            this.key = key(cx, cz);
        }

        void accept(Block b, int x, int y, int z) {
            boolean geode = false;
            if (b == Blocks.BUDDING_AMETHYST) { budding++; geode = true; }
            else if (b == Blocks.SMALL_AMETHYST_BUD) { smallBuds++; geode = true; addCrystal(x, y, z); }
            else if (b == Blocks.MEDIUM_AMETHYST_BUD) { mediumBuds++; geode = true; addCrystal(x, y, z); }
            else if (b == Blocks.LARGE_AMETHYST_BUD) { largeBuds++; geode = true; addCrystal(x, y, z); }
            else if (b == Blocks.AMETHYST_CLUSTER) { matureCrystals++; geode = true; addCrystal(x, y, z); }
            else if (b == Blocks.AMETHYST_BLOCK) { amethystBlocks++; geode = true; }
            else if (b == Blocks.CALCITE) { calcite++; geode = true; }
            else if (b == Blocks.SMOOTH_BASALT) { smoothBasalt++; geode = true; }
            if (geode) {
                minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
            }
            if (b == Blocks.NETHERITE_BLOCK || b == Blocks.DIAMOND_BLOCK || b == Blocks.DRAGON_HEAD
                    || b == Blocks.EMERALD_BLOCK || b == Blocks.IRON_BLOCK) valuableBlocks++;
            if (isRedstone(b)) redstoneBlocks++;
            if (isFarmBlock(b)) farmBlocks++;
        }

        void addCrystal(int x, int y, int z) {
            if (crystalPositions.size() < 256) crystalPositions.add(new BlockPos(x, y, z));
        }

        ScanSnapshot snapshot(long now) {
            ScanSnapshot s = new ScanSnapshot();
            s.budding = budding;
            s.smallBuds = smallBuds; s.mediumBuds = mediumBuds; s.largeBuds = largeBuds;
            s.buds = smallBuds + mediumBuds + largeBuds;
            s.matureCrystals = matureCrystals; s.amethystBlocks = amethystBlocks;
            s.calcite = calcite; s.smoothBasalt = smoothBasalt;
            s.valuableBlocks = valuableBlocks; s.redstoneBlocks = redstoneBlocks; s.farmBlocks = farmBlocks;
            s.completeness = Math.min(1.0, (double) cursor / total);
            s.harvestObserved = harvestObserved;
            s.lastCompleteTick = cursor >= total ? now : 0;
            s.crystals.addAll(crystalPositions);
            if (minX != Integer.MAX_VALUE && maxX - minX <= 64 && maxY - minY <= 64 && maxZ - minZ <= 64)
                s.bounds = new Box(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
            return s;
        }
    }

    private static final class ScanSnapshot {
        int budding, smallBuds, mediumBuds, largeBuds, buds, matureCrystals, amethystBlocks;
        int calcite, smoothBasalt, valuableBlocks, redstoneBlocks, farmBlocks;
        boolean harvestObserved;
        long lastCompleteTick;
        double completeness;
        Box bounds;
        final List<BlockPos> crystals = new ArrayList<>();
    }

    private static final class EntityEvidence {
        int frames, villagers, armorStands, playerCount;
        double clusterBonus, activityScore;
    }

    private static boolean isRedstone(Block b) {
        return b == Blocks.REDSTONE_WIRE || b == Blocks.REPEATER || b == Blocks.COMPARATOR
                || b == Blocks.REDSTONE_TORCH || b == Blocks.REDSTONE_WALL_TORCH || b == Blocks.REDSTONE_BLOCK
                || b == Blocks.OBSERVER || b == Blocks.DISPENSER || b == Blocks.DROPPER
                || b == Blocks.PISTON || b == Blocks.STICKY_PISTON || b == Blocks.LEVER
                || b == Blocks.TRIPWIRE_HOOK || b == Blocks.DAYLIGHT_DETECTOR || b == Blocks.TARGET
                || b == Blocks.CRAFTER;
    }

    private static boolean isFarmBlock(Block b) {
        return b == Blocks.WHEAT || b == Blocks.CARROTS || b == Blocks.POTATOES
                || b == Blocks.BEETROOTS || b == Blocks.SUGAR_CANE || b == Blocks.BAMBOO
                || b == Blocks.CACTUS || b == Blocks.KELP || b == Blocks.KELP_PLANT
                || b == Blocks.COCOA || b == Blocks.NETHER_WART || b == Blocks.SWEET_BERRY_BUSH
                || b == Blocks.MELON || b == Blocks.PUMPKIN || b == Blocks.MELON_STEM
                || b == Blocks.ATTACHED_MELON_STEM || b == Blocks.PUMPKIN_STEM
                || b == Blocks.ATTACHED_PUMPKIN_STEM || b == Blocks.MOSS_BLOCK
                || b == Blocks.POINTED_DRIPSTONE || b == Blocks.BEEHIVE || b == Blocks.BEE_NEST
                || b == Blocks.HONEY_BLOCK || b == Blocks.GLOW_BERRIES;
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (!Projection.valid() || mc.player == null) return;
        if (entityEsp.get()) {
            int drawn = 0;
            for (Entity entity : undergroundEntities) {
                double dx = entity.getX() - mc.player.getX();
                double dy = entity.getY() - mc.player.getY();
                double dz = entity.getZ() - mc.player.getZ();
                if (dx * dx + dy * dy + dz * dz > 96 * 96) continue;
                if (!Draw.box(ctx, entity.getBoundingBox(), 0xFF000000 | entityColor.rgb(), 1, true)) continue;
                if (++drawn >= 96) break;
            }
        }
        for (ScanSnapshot snapshot : snapshots.values()) {
            if (snapshot.completeness < 1.0 || snapshot.crystals.isEmpty()) continue;
            if (crystalEsp.get()) for (BlockPos pos : snapshot.crystals) {
                double dx = pos.getX() + 0.5 - mc.player.getX();
                double dy = pos.getY() + 0.5 - mc.player.getY();
                double dz = pos.getZ() + 0.5 - mc.player.getZ();
                if (dx * dx + dy * dy + dz * dz > 96.0 * 96.0) continue;
                Draw.box(ctx, new Box(pos.getX(), pos.getY(), pos.getZ(),
                        pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1),
                        0xFF000000 | crystalColor.rgb(), 1, true);
            }
            if (geodeBoxEsp.get() && snapshot.bounds != null) {
                double bx = (snapshot.bounds.minX + snapshot.bounds.maxX) * 0.5 - mc.player.getX();
                double by = (snapshot.bounds.minY + snapshot.bounds.maxY) * 0.5 - mc.player.getY();
                double bz = (snapshot.bounds.minZ + snapshot.bounds.maxZ) * 0.5 - mc.player.getZ();
                if (bx * bx + by * by + bz * bz <= 96.0 * 96.0)
                    Draw.box(ctx, snapshot.bounds, 0xAA000000 | geodeColor.rgb(), 2, true);
            }
        }
    }
}
