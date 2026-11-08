package dev.nightfall.modules;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.nightfall.Nightfall;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.scanner.ChunkResult.Status;
import dev.nightfall.util.Storage;
import net.minecraft.block.entity.*;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.registry.Registries;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Inkrementeller Chunk-Scanner. Untersucht nur bereits geladene Chunks und wertet deren BlockEntities aus
 * (kein Blockscan). Pro Tick gilt ein festes Zeitbudget. SUS/SUS-WEG-Markierungen werden je Server+Dimension gespeichert.
 */
public class BaseFinder extends Module {
    private final NumberSetting range     = add(new NumberSetting("Range (Chunks)", 16, 2, 48, 1));
    private final NumberSetting budgetMs  = add(new NumberSetting("Budget ms/Tick", 1.0, 0.2, 5.0, 0.1));
    private final NumberSetting rescan    = add(new NumberSetting("Rescan (s)", 10, 2, 120, 1));
    private final NumberSetting threshold = add(new NumberSetting("Min Score", 12, 3, 100, 1));
    private final NumberSetting similarity = add(new NumberSetting("Ähnlichkeit (%)", 95, 50, 100, 5));
    private final NumberSetting unchangedScans = add(new NumberSetting("Unveränderte Scans", 3, 2, 10, 1));
    private final NumberSetting minSignals = add(new NumberSetting("Min. Signaltypen", 2, 1, 5, 1));
    private final NumberSetting wStorage  = add(new NumberSetting("Gewicht Storage", 1.0, 0.1, 5.0, 0.1));
    private final NumberSetting wHopper   = add(new NumberSetting("Gewicht Hopper", 1.5, 0.1, 5.0, 0.1));
    private final NumberSetting wFurnace  = add(new NumberSetting("Gewicht Furnace", 0.7, 0.1, 5.0, 0.1));
    private final NumberSetting wRedstone = add(new NumberSetting("Gewicht Redstone", 1.2, 0.1, 5.0, 0.1));
    private final NumberSetting wCluster  = add(new NumberSetting("Gewicht Cluster", 1.5, 0.0, 5.0, 0.1));
    private final BoolSetting   chat      = add(new BoolSetting("Chat-Meldung", true));

    private static final Gson GSON = new Gson();
    private static final Type MARK_LIST = new TypeToken<List<Mark>>() {}.getType();

    private static final class Mark {
        int cx, cz; double score; String status; long found; List<String> reasons; List<String> signature; int unchanged;
    }

    private final Map<Long, ChunkResult> results = new HashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private ScanTask pendingScan;

    private static final class ScanTask {
        final long key;
        final WorldChunk chunk;
        final int bottomY, total;
        final List<String> structure = new ArrayList<>();
        int cursor;
        ScanTask(long key, WorldChunk chunk) {
            this.key = key; this.chunk = chunk;
            this.bottomY = chunk.getBottomY();
            this.total = 16 * 16 * chunk.getHeight();
        }
    }
    private final List<ChunkResult> top = new ArrayList<>();
    private final List<ChunkResult> marks = new ArrayList<>();
    private boolean dirty = true, marksDirty;
    private ClientWorld lastWorld;
    private String ctxKey;
    private long tick, lastScanNanos;
    private int lastScanned;
    private int candidatesLastRefill;
    private int scannedSinceRefill;
    private boolean paused;

    public BaseFinder() {
        super("BaseFinder", "Findet verdaechtige Chunks (Storage, Hopper, Redstone) in geladenen Chunks.", Category.BASEFINDER);
    }

    private static long key(int cx, int cz) { return ((long) cx & 0xFFFFFFFFL) | ((long) cz << 32); }

    // ---- Zugriff fuer Karte/HUD/ESP ----
    public double thresholdValue() { return threshold.get(); }
    public ChunkResult get(int cx, int cz) { return results.get(key(cx, cz)); }
    public Collection<ChunkResult> results() { return results.values(); }
    public List<ChunkResult> suspects() { return top; }
    public List<ChunkResult> marks() { return marks; }
    public double lastScanMs() { return lastScanNanos / 1e6; }
    public int queueSize() { return queue.size(); }
    public int lastScanned() { return lastScanned; }
    public int resultCount() { return results.size(); }
    public boolean isPaused() { return paused; }
    public void setPaused(boolean value) { paused = value; }
    public int candidatesLastRefill() { return candidatesLastRefill; }
    public int scannedSinceRefill() { return scannedSinceRefill; }
    public double progress() {
        if (candidatesLastRefill <= 0) return queue.isEmpty() ? 1.0 : 0.0;
        return Math.max(0.0, Math.min(1.0, (double) scannedSinceRefill / candidatesLastRefill));
    }

    public void clear() {
        saveMarks();
        clearData();
        lastWorld = null;
        ctxKey = null;
    }

    private void clearData() {
        results.clear(); queue.clear(); pendingScan = null; top.clear(); marks.clear(); dirty = true; marksDirty = false; candidatesLastRefill = 0; scannedSinceRefill = 0;
    }

    public void clearMarks() { clearData(); marksDirty = true; saveMarks(); }

    @Override protected void onDisable() { clear(); }

    @Override
    public void onTick() {
        ClientWorld w = mc.world;
        if (w == null || mc.player == null) return;
        if (w != lastWorld) {
            saveMarks();
            clearData();
            lastWorld = w;
            ctxKey = Storage.server() + "_" + Storage.dim();
            loadMarks();
        }
        tick++;
        if (paused) { lastScanned = 0; lastScanNanos = 0; return; }

        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        if (queue.isEmpty() && tick % 20 == 0) refill(w, pcx, pcz);

        long start = System.nanoTime();
        long deadline = start + (long) (budgetMs.get() * 1_000_000.0);
        int n = 0;
        while (System.nanoTime() < deadline) {
            if (pendingScan == null) {
                if (queue.isEmpty()) break;
                long next = queue.poll();
                int cx = (int) next, cz = (int) (next >> 32);
                WorldChunk chunk = w.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;
                pendingScan = new ScanTask(next, chunk);
            }
            ScanTask task = pendingScan;
            BlockPos.Mutable pos = new BlockPos.Mutable();
            int processed = 0;
            while (task.cursor < task.total && processed < 512 && System.nanoTime() < deadline) {
                int i = task.cursor++;
                int x = i & 15;
                int z = (i >> 4) & 15;
                int y = task.bottomY + (i >> 8);
                pos.set(task.chunk.getPos().getStartX() + x, y, task.chunk.getPos().getStartZ() + z);
                BlockState state = task.chunk.getBlockState(pos);
                if (!state.isAir() && state.getFluidState().isEmpty()) {
                    String id = Registries.BLOCK.getId(state.getBlock()).getPath();
                    if (!isCommonTerrain(id)) {
                        task.structure.add(id + "@" + x + "," + y + "," + z);
                    }
                }
                processed++;
            }
            if (task.cursor >= task.total) {
                pendingScan = null;
                scan(w, task.key, task.structure);
                n++;
                scannedSinceRefill++;
            } else {
                break;
            }
        }
        lastScanNanos = System.nanoTime() - start;
        lastScanned = n;
        if (dirty) rebuildLists();
        if (marksDirty && tick % 600 == 0) saveMarks();
    }

    private void refill(ClientWorld w, int pcx, int pcz) {
        int r = range.getInt();
        long minAge = rescan.getInt() * 20L;
        List<long[]> cand = new ArrayList<>();
        for (int dz = -r; dz <= r; dz++) {
            for (int dx = -r; dx <= r; dx++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > r * r) continue;
                long k = key(pcx + dx, pcz + dz);
                ChunkResult res = results.get(k);
                if (res != null && res.lastScanTick != 0 && tick - res.lastScanTick < minAge) continue;
                if (w.getChunkManager().getWorldChunk(pcx + dx, pcz + dz) == null) continue; // nur geladene Chunks
                cand.add(new long[]{d2, k});
            }
        }
        cand.sort(Comparator.comparingLong(a -> a[0]));
        queue.clear();
        candidatesLastRefill = cand.size();
        scannedSinceRefill = 0;
        for (long[] c : cand) queue.add(c[1]);

        int limit = (r + 4) * (r + 4);
        results.values().removeIf(e -> e.status == Status.CLEAN
                && (e.cx - pcx) * (e.cx - pcx) + (e.cz - pcz) * (e.cz - pcz) > limit);
    }

    private void scan(ClientWorld w, long k, List<String> structureSignature) {
        int cx = (int) k, cz = (int) (k >> 32);
        WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz);
        if (ch == null) return;

        int storage = 0, shulker = 0, hopper = 0, furnace = 0, redstone = 0;
        Map<Integer, Integer> cells = new HashMap<>();
        List<String> signature = new ArrayList<>(structureSignature);

        for (BlockEntity be : ch.getBlockEntities().values()) {
            // Nur Typ + Position vergleichen: Facing/Rotation ist absichtlich kein Bestandteil.
            String typeId = String.valueOf(Registries.BLOCK_ENTITY_TYPE.getId(be.getType()));
            signature.add(typeId + "@" + (be.getPos().getX() & 15) + "," + be.getPos().getY() + "," + (be.getPos().getZ() & 15));
            boolean dense = false;
            if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) { storage++; dense = true; }
            else if (be instanceof ShulkerBoxBlockEntity) { shulker++; dense = true; }
            else if (be instanceof HopperBlockEntity) { hopper++; dense = true; }
            else if (be instanceof AbstractFurnaceBlockEntity) furnace++;
            else if (be instanceof DispenserBlockEntity || be instanceof ComparatorBlockEntity
                    || be instanceof CrafterBlockEntity) redstone++;
            if (dense) {
                int px = be.getPos().getX() & 15, pz = be.getPos().getZ() & 15, py = be.getPos().getY();
                cells.merge((px >> 2) | ((pz >> 2) << 2) | (((py >> 2) + 32) << 4), 1, Integer::sum);
            }
        }
        int maxCell = 0;
        for (int v : cells.values()) maxCell = Math.max(maxCell, v);

        double cluster = maxCell >= 6 ? (maxCell - 5) * wCluster.get() : 0;
        double score = storage * wStorage.get() + shulker * wStorage.get() * 1.5
                + hopper * wHopper.get() + furnace * wFurnace.get() + redstone * wRedstone.get() + cluster;
        int signalKinds = (storage + shulker > 0 ? 1 : 0) + (hopper > 0 ? 1 : 0)
                + (furnace > 0 ? 1 : 0) + (redstone > 0 ? 1 : 0) + (cluster > 0 ? 1 : 0);
        Collections.sort(signature);

        ChunkResult old = results.get(k);
        ChunkResult res = old != null ? old : new ChunkResult(cx, cz);
        Status prev = res.status;
        List<String> previousSignature = res.signature == null ? Collections.emptyList() : res.signature;
        boolean candidate = score >= threshold.get() && signalKinds >= minSignals.getInt();
        if (candidate && !previousSignature.isEmpty()) {
            double similarityRatio = similarity(previousSignature, signature);
            if (similarityRatio * 100.0 >= similarity.get()) res.unchangedScans++;
            else res.unchangedScans = 1;
        } else if (candidate) {
            res.unchangedScans = 1;
        } else {
            res.unchangedScans = 0;
        }
        res.signature.clear();
        res.signature.addAll(signature);
        res.score = score;
        res.lastScanTick = tick;
        res.reasons.clear();
        // Expose the weighted contributions so a score can be inspected and tuned.
        if (storage > 0) res.reasons.add(storage + " Chest/Barrel (" + fmt(storage * wStorage.get()) + ")");
        if (shulker > 0) res.reasons.add(shulker + " Shulker (" + fmt(shulker * wStorage.get() * 1.5) + ")");
        if (hopper > 0) res.reasons.add(hopper + " Hopper (" + fmt(hopper * wHopper.get()) + ")");
        if (furnace > 0) res.reasons.add(furnace + " Furnace (" + fmt(furnace * wFurnace.get()) + ")");
        if (redstone > 0) res.reasons.add(redstone + " Dispenser/Comparator/Crafter (" + fmt(redstone * wRedstone.get()) + ")");
        if (cluster > 0) res.reasons.add("Lagerzone (" + maxCell + " in 4x4x4, +" + fmt(cluster) + ")");

        if (candidate && res.unchangedScans >= unchangedScans.getInt()) {
            res.status = Status.HIDDEN;
            if (prev != Status.HIDDEN) { dirty = true; marksDirty = true; }
        } else if (candidate) {
            res.status = Status.SUS;
            if (prev != Status.SUS) {
                res.foundAtMillis = System.currentTimeMillis();
                dirty = true; marksDirty = true;
                if (chat.get() && mc.player != null) {
                    mc.player.sendMessage(Text.literal("[Nightfall] SUS Chunk " + cx + "," + cz + " (Block "
                            + res.blockX() + ", " + res.blockZ() + ") Score " + fmt(score)
                            + " - " + String.join(", ", res.reasons)), false);
                }
            }
        } else if (prev == Status.SUS || prev == Status.GONE) {
            res.status = Status.GONE;
            if (prev == Status.SUS) { dirty = true; marksDirty = true; }
        } else {
            res.status = Status.CLEAN;
            if (score >= threshold.get() && signalKinds < minSignals.getInt())
                res.reasons.add("Score hoch, aber nur " + signalKinds + " Signaltyp(en) — nicht als SUS markiert");
        }
        if (old == null) results.put(k, res);
    }

    // Häufige natürliche Terrainblöcke nicht in die Strukturähnlichkeit einrechnen.
    // Holz, Glas, Steinziegel und andere typische Baumaterialien bleiben ausdrücklich enthalten.
    private static boolean isCommonTerrain(String id) {
        return switch (id) {
            case "stone", "granite", "diorite", "andesite", "tuff", "deepslate",
                 "dirt", "coarse_dirt", "rooted_dirt", "grass_block", "podzol", "mycelium",
                 "sand", "red_sand", "gravel", "clay", "snow", "snow_block",
                 "ice", "packed_ice", "blue_ice", "bedrock", "netherrack",
                 "end_stone", "calcite", "dripstone_block" -> true;
            default -> id.endsWith("_ore") || id.startsWith("deepslate_") && id.endsWith("_ore");
        };
    }

    private static double similarity(List<String> a, List<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> left = new HashSet<>(a), right = new HashSet<>(b);
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
    }

    private static String fmt(double value) { return String.format(Locale.ROOT, "%.1f", value); }

    private void rebuildLists() {
        top.clear(); marks.clear();
        for (ChunkResult r : results.values()) {
            if (r.status == Status.SUS) { top.add(r); marks.add(r); }
            else if (r.status == Status.GONE) marks.add(r);
        }
        top.sort((a, b) -> Double.compare(b.score, a.score));
        dirty = false;
    }

    // ---- Persistenz ----
    private Path marksFile() { return Storage.dir().resolve("marks_" + ctxKey + ".json"); }

    public void saveMarks() {
        if (ctxKey == null) return;
        try {
            List<Mark> out = new ArrayList<>();
            for (ChunkResult r : results.values()) {
                if (r.status == Status.CLEAN) continue;
                Mark m = new Mark();
                m.cx = r.cx; m.cz = r.cz; m.score = r.score; m.status = r.status.name();
                m.found = r.foundAtMillis; m.reasons = new ArrayList<>(r.reasons);
                m.signature = r.signature == null ? new ArrayList<>() : new ArrayList<>(r.signature); m.unchanged = r.unchangedScans;
                out.add(m);
            }
            Path f = marksFile();
            if (out.isEmpty() && !Files.exists(f)) return;
            Files.writeString(f, GSON.toJson(out));
            marksDirty = false;
        } catch (IOException e) {
            Nightfall.LOGGER.error("Markierungen konnten nicht gespeichert werden", e);
        }
    }

    private void loadMarks() {
        Path f = marksFile();
        if (!Files.exists(f)) return;
        try {
            List<Mark> in = GSON.fromJson(Files.readString(f), MARK_LIST);
            if (in == null) return;
            for (Mark m : in) {
                ChunkResult r = new ChunkResult(m.cx, m.cz);
                r.score = m.score;
                r.status = Status.valueOf(m.status);
                r.foundAtMillis = m.found;
                if (m.reasons != null) r.reasons.addAll(m.reasons);
                if (m.signature != null) r.signature.addAll(m.signature); r.unchangedScans = m.unchanged;
                r.lastScanTick = 0;
                results.put(key(m.cx, m.cz), r);
            }
            dirty = true;
        } catch (Exception e) {
            Nightfall.LOGGER.error("Markierungsdatei defekt: {}", f, e);
        }
    }
}
