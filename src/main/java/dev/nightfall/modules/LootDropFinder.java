package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Loot discovery notifier: no per-object ESP. A newly detected chunk gets one
 * notification and a temporary outline around the whole chunk.
 */
public final class LootDropFinder extends Module {
    private final NumberSetting range = add(new NumberSetting("Reichweite", 64, 8, 256, 8));
    private final NumberSetting markerSeconds = add(new NumberSetting("Chunk-Markierung (Sek.)", 60, 5, 300, 5));
    private final BoolSetting emeraldOre = add(new BoolSetting("Emerald-Erz", true));
    private final BoolSetting diamondOre = add(new BoolSetting("Diamant-Erz", true));
    private final BoolSetting ancientDebris = add(new BoolSetting("Ancient Debris", true));
    private final BoolSetting gildedBlackstone = add(new BoolSetting("Gilded Blackstone", true));
    private final BoolSetting spawners = add(new BoolSetting("Spawner", true));
    private final BoolSetting valuableBlocks = add(new BoolSetting("Wertvolle Bloecke", true));
    private final BoolSetting droppedItems = add(new BoolSetting("Item-Drops", true));
    private final BoolSetting itemFrames = add(new BoolSetting("Itemframes", true));
    private final BoolSetting armorStands = add(new BoolSetting("Ruestungsstaender", true));
    private final BoolSetting soundAlert = add(new BoolSetting("Sound bei Fund", true));
    private final BoolSetting chatAlert = add(new BoolSetting("Nachricht bei Fund", true));

    private static final int CHUNK_REFRESH_TICKS = 100;
    private static final int ENTITY_REFRESH_TICKS = 10;
    private static final int CHUNKS_PER_TICK = 2;
    private static final int MARKER_COLOR = 0xE638BDF8;

    private static final class Target {
        final String id;
        final String label;
        final BlockPos pos;
        final Entity entity;

        Target(String id, String label, BlockPos pos, Entity entity) {
            this.id = id;
            this.label = label;
            this.pos = pos == null ? null : pos.toImmutable();
            this.entity = entity;
        }

        Vec3d center() {
            if (entity != null) {
                Box b = entity.getBoundingBox();
                return new Vec3d((b.minX + b.maxX) * 0.5, (b.minY + b.maxY) * 0.5, (b.minZ + b.maxZ) * 0.5);
            }
            return Vec3d.ofCenter(pos);
        }
    }

    private final Map<Long, List<Target>> blockTargetsByChunk = new HashMap<>();
    private final Map<Long, Long> lastChunkScan = new HashMap<>();
    private final Map<String, Target> entityTargets = new HashMap<>();
    private final Map<Long, Long> markedChunks = new HashMap<>();
    private final ArrayDeque<Long> chunkQueue = new ArrayDeque<>();
    private final Set<Long> queuedChunks = new HashSet<>();

    private net.minecraft.client.world.ClientWorld lastWorld;
    private long tick;
    private long lastRefillTick;
    private boolean initialPassStarted;
    private boolean initialPassComplete;

    public LootDropFinder() {
        super("LootDropFinder",
                "Meldet gefundene Loot-Hinweise mit Sound und markiert nur den betroffenen Chunk.",
                Category.VISUALS);
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | ((long) cz << 32);
    }
    private static int chunkX(long key) { return (int) key; }
    private static int chunkZ(long key) { return (int) (key >> 32); }

    @Override protected void onEnable() { clearState(); }
    @Override protected void onDisable() { clearState(); }
    @Override public void onWorldLeave() { clearState(); lastWorld = null; }

    private void clearState() {
        blockTargetsByChunk.clear();
        lastChunkScan.clear();
        entityTargets.clear();
        markedChunks.clear();
        chunkQueue.clear();
        queuedChunks.clear();
        tick = 0L;
        lastRefillTick = 0L;
        initialPassStarted = false;
        initialPassComplete = false;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (lastWorld != mc.world) {
            clearState();
            lastWorld = mc.world;
        }
        tick++;
        markedChunks.entrySet().removeIf(e -> tick - e.getValue() > markerSeconds.get() * 20.0);

        List<Target> found = new ArrayList<>();
        if (tick % ENTITY_REFRESH_TICKS == 0) refreshEntities(found);
        if (chunkQueue.isEmpty() && tick - lastRefillTick >= 20L) refillChunkQueue();
        for (int i = 0; i < CHUNKS_PER_TICK && !chunkQueue.isEmpty(); i++) {
            long key = chunkQueue.pollFirst();
            queuedChunks.remove(key);
            scanChunk(key, found);
        }
        if (initialPassStarted && !initialPassComplete && chunkQueue.isEmpty()) initialPassComplete = true;
        if (!found.isEmpty()) alertByChunk(found);
    }

    private void refillChunkQueue() {
        if (mc.world == null || mc.player == null) return;
        lastRefillTick = tick;
        int blockRange = range.getInt();
        int chunkRange = (blockRange + 15) >> 4;
        int pcx = mc.player.getBlockX() >> 4;
        int pcz = mc.player.getBlockZ() >> 4;
        double rangeSq = (double) blockRange * blockRange;

        Iterator<Long> cached = blockTargetsByChunk.keySet().iterator();
        while (cached.hasNext()) {
            long key = cached.next();
            double dx = chunkX(key) * 16.0 + 8.0 - mc.player.getX();
            double dz = chunkZ(key) * 16.0 + 8.0 - mc.player.getZ();
            if (dx * dx + dz * dz > rangeSq + 512.0
                    || mc.world.getChunkManager().getWorldChunk(chunkX(key), chunkZ(key)) == null) {
                cached.remove();
                lastChunkScan.remove(key);
                queuedChunks.remove(key);
                chunkQueue.remove(key);
            }
        }

        List<Long> candidates = new ArrayList<>();
        for (int dz = -chunkRange; dz <= chunkRange; dz++) {
            for (int dx = -chunkRange; dx <= chunkRange; dx++) {
                int cx = pcx + dx, cz = pcz + dz;
                double wx = cx * 16.0 + 8.0 - mc.player.getX();
                double wz = cz * 16.0 + 8.0 - mc.player.getZ();
                if (wx * wx + wz * wz > rangeSq + 512.0) continue;
                if (mc.world.getChunkManager().getWorldChunk(cx, cz) == null) continue;
                long key = chunkKey(cx, cz);
                if (queuedChunks.contains(key)) continue;
                if (tick - lastChunkScan.getOrDefault(key, Long.MIN_VALUE / 2) < CHUNK_REFRESH_TICKS) continue;
                candidates.add(key);
            }
        }
        candidates.sort(Comparator.comparingDouble(key -> {
            double dx = chunkX(key) * 16.0 + 8.0 - mc.player.getX();
            double dz = chunkZ(key) * 16.0 + 8.0 - mc.player.getZ();
            return dx * dx + dz * dz;
        }));
        if (!initialPassStarted) initialPassStarted = true;
        for (long key : candidates) {
            chunkQueue.addLast(key);
            queuedChunks.add(key);
        }
        if (chunkQueue.isEmpty() && !initialPassComplete) initialPassComplete = true;
    }

    private void scanChunk(long key, List<Target> found) {
        if (mc.world == null) return;
        int cx = chunkX(key), cz = chunkZ(key);
        WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
        if (chunk == null) {
            blockTargetsByChunk.remove(key);
            lastChunkScan.remove(key);
            return;
        }
        List<Target> old = blockTargetsByChunk.getOrDefault(key, List.of());
        Set<String> oldIds = new HashSet<>();
        for (Target target : old) oldIds.add(target.id);
        List<Target> next = new ArrayList<>();

        ChunkSection[] sections = chunk.getSectionArray();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            ChunkSection section = sections[sectionIndex];
            if (section == null || section.isEmpty()) continue;
            int baseY = chunk.getBottomY() + sectionIndex * 16;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState state = section.getBlockState(x, y, z);
                String name = null;
                if (emeraldOre.get() && (state.isOf(Blocks.EMERALD_ORE) || state.isOf(Blocks.DEEPSLATE_EMERALD_ORE))) name = "Emerald-Erz";
                else if (diamondOre.get() && (state.isOf(Blocks.DIAMOND_ORE) || state.isOf(Blocks.DEEPSLATE_DIAMOND_ORE))) name = "Diamant-Erz";
                else if (ancientDebris.get() && state.isOf(Blocks.ANCIENT_DEBRIS)) name = "Ancient Debris";
                else if (gildedBlackstone.get() && state.isOf(Blocks.GILDED_BLACKSTONE)) name = "Gilded Blackstone";
                else if (spawners.get() && state.isOf(Blocks.SPAWNER)) name = "Spawner";
                else if (valuableBlocks.get() && (state.isOf(Blocks.EMERALD_BLOCK) || state.isOf(Blocks.DIAMOND_BLOCK)
                        || state.isOf(Blocks.NETHERITE_BLOCK) || state.isOf(Blocks.GOLD_BLOCK))) name = "Wertvoller Block";
                if (name == null) continue;
                BlockPos pos = new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z);
                String id = "block:" + pos.asLong() + ":" + name;
                Target target = new Target(id, name, pos, null);
                next.add(target);
                if (initialPassComplete && !oldIds.contains(id)) found.add(target);
            }
        }
        blockTargetsByChunk.put(key, next);
        lastChunkScan.put(key, tick);
    }

    private void refreshEntities(List<Target> found) {
        if (mc.world == null || mc.player == null) return;
        Map<String, Target> next = new HashMap<>();
        double maxDistanceSq = range.get() * range.get();
        Vec3d playerPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || entity.squaredDistanceTo(playerPos) > maxDistanceSq) continue;
            Target target = null;
            if (droppedItems.get() && entity instanceof ItemEntity item) {
                target = new Target("entity:" + entity.getUuidAsString(),
                        "Item-Drop: " + item.getStack().getName().getString() + " x" + item.getStack().getCount(), null, entity);
            } else if (itemFrames.get() && entity instanceof ItemFrameEntity frame) {
                String held = frame.getHeldItemStack().isEmpty() ? "leer" : frame.getHeldItemStack().getName().getString();
                target = new Target("entity:" + entity.getUuidAsString(), "Itemframe: " + held, null, entity);
            } else if (armorStands.get() && entity instanceof ArmorStandEntity) {
                target = new Target("entity:" + entity.getUuidAsString(), "Ruestungsstaender", null, entity);
            }
            if (target != null) {
                next.put(target.id, target);
                if (initialPassComplete && !entityTargets.containsKey(target.id)) found.add(target);
            }
        }
        entityTargets.clear();
        entityTargets.putAll(next);
    }

    private void alertByChunk(List<Target> found) {
        Map<Long, List<Target>> grouped = new LinkedHashMap<>();
        for (Target target : found) {
            Vec3d p = target.center();
            long key = chunkKey(((int) Math.floor(p.x)) >> 4, ((int) Math.floor(p.z)) >> 4);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(target);
        }
        for (Map.Entry<Long, List<Target>> entry : grouped.entrySet()) {
            long key = entry.getKey();
            if (markedChunks.containsKey(key)) continue;
            markedChunks.put(key, tick);
            List<Target> items = entry.getValue();
            if (chatAlert.get() && mc.player != null) {
                mc.player.sendMessage(Text.literal("[Nightfall] LOOTDROP FOUND! Chunk "
                        + chunkX(key) + " / " + chunkZ(key) + " · " + items.size() + " Treffer"), false);
            }
            if (soundAlert.get() && mc.player != null) {
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.15f);
            }
        }
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.world == null || mc.player == null || !Projection.valid()) return;
        List<Long> keys = new ArrayList<>(markedChunks.keySet());
        keys.sort(Comparator.comparingDouble(key -> {
            double x = chunkX(key) * 16.0 + 8.0 - Projection.eyeX();
            double z = chunkZ(key) * 16.0 + 8.0 - Projection.eyeZ();
            return x * x + z * z;
        }));
        int drawn = 0;
        int minY = mc.world.getBottomY();
        int maxY = mc.world.getTopY();
        for (long key : keys) {
            if (drawn >= 64) break;
            int x = chunkX(key) << 4;
            int z = chunkZ(key) << 4;
            Box chunkBox = new Box(x, minY, z, x + 16, maxY, z + 16);
            if (Draw.box(ctx, chunkBox, MARKER_COLOR, 2, true, true)) drawn++;
        }
    }
}
