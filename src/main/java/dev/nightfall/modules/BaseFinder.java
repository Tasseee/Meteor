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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.ChunkSection;

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
    private final NumberSetting range     = add(new NumberSetting("Range (Chunks)", 32, 2, 48, 1));
    private final NumberSetting budgetMs  = add(new NumberSetting("Budget ms/Tick", 2.5, 0.2, 5.0, 0.1));
    private final NumberSetting rescan    = add(new NumberSetting("Rescan (s)", 8, 2, 120, 1));
    private final NumberSetting threshold = add(new NumberSetting("Min Score", 6, 3, 100, 1));
    private final NumberSetting minSignals = add(new NumberSetting("Min. Signaltypen", 1, 1, 10, 1));
    private final NumberSetting wStorage  = add(new NumberSetting("Gewicht Storage", 1.0, 0.1, 5.0, 0.1));
    private final NumberSetting wHopper   = add(new NumberSetting("Gewicht Hopper", 1.5, 0.1, 5.0, 0.1));
    private final NumberSetting wFurnace  = add(new NumberSetting("Gewicht Furnace", 0.7, 0.1, 5.0, 0.1));
    private final NumberSetting wRedstone = add(new NumberSetting("Gewicht Redstone", 1.2, 0.1, 5.0, 0.1));
    private final NumberSetting wSpawner  = add(new NumberSetting("Gewicht Spawner", 2.0, 0.1, 10.0, 0.1));
    private final NumberSetting wCluster  = add(new NumberSetting("Gewicht Lagerzone", 1.5, 0.0, 5.0, 0.1));
    private final NumberSetting clusterWeight = add(new NumberSetting("Gewicht Amethyst-Cluster", 0.25, 0.0, 2.0, 0.05));
    private final NumberSetting minClusters = add(new NumberSetting("Min. Amethyst-Cluster", 6, 1, 64, 1));
    private final NumberSetting wSculk    = add(new NumberSetting("Gewicht Sculk", 1.2, 0.0, 8.0, 0.1));
    private final BoolSetting   chat      = add(new BoolSetting("Chat-Meldung", true));

    private static final Gson GSON = new Gson();
    private static final Type MARK_LIST = new TypeToken<List<Mark>>() {}.getType();
    private static final Type FAKE_LIST = new TypeToken<List<FakePattern>>() {}.getType();

    private static final class FakePattern {
        int storage, shulker, hopper, furnace, redstone, spawner;
        List<String> seenChunks = new ArrayList<>();
    }

    private static final class Mark {
        int cx, cz; double score; String status; long found; List<String> reasons;
    }

    private final Map<Long, ChunkResult> results = new HashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final List<ChunkResult> top = new ArrayList<>();
    private final List<ChunkResult> marks = new ArrayList<>();
    private final List<FakePattern> fakePatterns = new ArrayList<>();
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

    public double thresholdValue() { return threshold.get(); }
    public ChunkResult get(int cx, int cz) { return results.get(key(cx, cz)); }
    public boolean isFakeChunk(int cx, int cz) {
        String location = cx + "," + cz;
        for (FakePattern pattern : fakePatterns) {
            if (pattern.seenChunks != null && pattern.seenChunks.size() >= 3
                    && pattern.seenChunks.contains(location)) return true;
        }
        return false;
    }
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
        saveFakePatterns();
        clearData();
        fakePatterns.clear();
        lastWorld = null;
        ctxKey = null;
    }

    @Override public void onWorldLeave() { clear(); }

    private void clearData() {
        results.clear(); queue.clear(); top.clear(); marks.clear(); dirty = true; marksDirty = false; candidatesLastRefill = 0; scannedSinceRefill = 0;
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
            fakePatterns.clear();
            loadMarks();
            loadFakePatterns();
        }
        tick++;
        if (paused) { lastScanned = 0; lastScanNanos = 0; return; }

        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        if (queue.isEmpty() && tick % 20 == 0) refill(w, pcx, pcz);

        long start = System.nanoTime();
        long deadline = start + (long) (budgetMs.get() * 1_000_000.0);
        int n = 0;
        while (!queue.isEmpty() && System.nanoTime() < deadline) {
            scan(w, queue.poll());
            n++;
            scannedSinceRefill++;
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
                if (w.getChunkManager().getWorldChunk(pcx + dx, pcz + dz) == null) continue;
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

    private void scan(ClientWorld w, long k) {
        int cx = (int) k, cz = (int) (k >> 32);
        WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz);
        if (ch == null) return;

        int storage = 0, shulker = 0, hopper = 0, furnace = 0, redstone = 0, spawner = 0;
        int buddingAmethyst = 0, amethystClusters = 0, geodeShell = 0, sculk = 0;
        Map<Integer, Integer> cells = new HashMap<>();

        for (BlockEntity be : ch.getBlockEntities().values()) {
            boolean dense = false;
            if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) { storage++; dense = true; }
            else if (be instanceof ShulkerBoxBlockEntity) { shulker++; dense = true; }
            else if (be instanceof HopperBlockEntity) { hopper++; dense = true; }
            else if (be instanceof AbstractFurnaceBlockEntity) furnace++;
            else if (be instanceof DispenserBlockEntity || be instanceof ComparatorBlockEntity
                    || be instanceof CrafterBlockEntity) redstone++;
            else if (be instanceof MobSpawnerBlockEntity) spawner++;
            if (dense) {
                int px = be.getPos().getX() & 15, pz = be.getPos().getZ() & 15, py = be.getPos().getY();
                cells.merge((px >> 2) | ((pz >> 2) << 2) | (((py >> 2) + 32) << 4), 1, Integer::sum);
            }
        }

        for (ChunkSection section : ch.getSectionArray()) {
            if (section == null || section.isEmpty()) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState state = section.getBlockState(x, y, z);
                if (state.isOf(Blocks.BUDDING_AMETHYST)) buddingAmethyst++;
                if (state.isOf(Blocks.AMETHYST_CLUSTER) || state.isOf(Blocks.LARGE_AMETHYST_BUD)
                        || state.isOf(Blocks.MEDIUM_AMETHYST_BUD) || state.isOf(Blocks.SMALL_AMETHYST_BUD))
                    amethystClusters++;
                if (state.isOf(Blocks.CALCITE) || state.isOf(Blocks.SMOOTH_BASALT)
                        || state.isOf(Blocks.AMETHYST_BLOCK)) geodeShell++;
                if (state.isOf(Blocks.SCULK_SENSOR) || state.isOf(Blocks.CALIBRATED_SCULK_SENSOR)
                        || state.isOf(Blocks.SCULK_SHRIEKER) || state.isOf(Blocks.SCULK_CATALYST)) sculk++;
            }
        }

        int maxCell = 0;
        for (int v : cells.values()) maxCell = Math.max(maxCell, v);
        double cluster = maxCell >= 6 ? (maxCell - 5) * wCluster.get() : 0;
        boolean geodeSignature = buddingAmethyst > 0 && amethystClusters > 0 && geodeShell >= 8;
        // Amethyst is a supporting clue, never a standalone base detector: natural geodes
        // receive a much smaller bonus than clusters without a strong natural-geode signature.
        double amethystScore = amethystClusters >= minClusters.getInt()
                ? Math.min(8.0, amethystClusters * clusterWeight.get()) * (geodeSignature ? 0.15 : 1.0) : 0.0;
        double sculkScore = sculk >= 3 ? (sculk - 2) * wSculk.get() : 0;
        double naturalSignalScore = amethystScore + sculkScore;
        double score = storage * wStorage.get() + shulker * wStorage.get() * 1.5
                + hopper * wHopper.get() + furnace * wFurnace.get() + redstone * wRedstone.get()
                + spawner * wSpawner.get() + cluster + naturalSignalScore;
        int signalKinds = (storage + shulker > 0 ? 1 : 0) + (hopper > 0 ? 1 : 0)
                + (furnace > 0 ? 1 : 0) + (redstone > 0 ? 1 : 0) + (spawner > 0 ? 1 : 0)
                + (cluster > 0 ? 1 : 0) + (amethystScore > 0 ? 1 : 0) + (sculkScore > 0 ? 1 : 0);

        ChunkResult old = results.get(k);
        ChunkResult res = old != null ? old : new ChunkResult(cx, cz);
        Status prev = res.status;
        res.score = score;
        res.naturalSignalScore = naturalSignalScore;
        res.lastScanTick = tick;
        res.reasons.clear();
        if (storage > 0) res.reasons.add(storage + " Chest/Barrel (" + fmt(storage * wStorage.get()) + ")");
        if (shulker > 0) res.reasons.add(shulker + " Shulker (" + fmt(shulker * wStorage.get() * 1.5) + ")");
        if (hopper > 0) res.reasons.add(hopper + " Hopper (" + fmt(hopper * wHopper.get()) + ")");
        if (furnace > 0) res.reasons.add(furnace + " Furnace (" + fmt(furnace * wFurnace.get()) + ")");
        if (redstone > 0) res.reasons.add(redstone + " Dispenser/Comparator/Crafter (" + fmt(redstone * wRedstone.get()) + ")");
        if (spawner > 0) res.reasons.add(spawner + " Mob-Spawner (" + fmt(spawner * wSpawner.get()) + ")");
        if (cluster > 0) res.reasons.add("Lagerzone (" + maxCell + " in 4x4x4, +" + fmt(cluster) + ")");
        if (amethystClusters > 0) res.reasons.add("Amethyst-Cluster/Buds (" + amethystClusters + ", +" + fmt(amethystScore)
                + (geodeSignature ? ", natürliche Geoden-Signatur abgewertet" : "") + ")");
        if (buddingAmethyst > 0) res.reasons.add("Budding Amethyst (" + buddingAmethyst + ")");
        if (geodeSignature) res.reasons.add("Geoden-Merkmale erkannt (Calcit/Smooth Basalt/Amethystblock)");
        if (sculkScore > 0) res.reasons.add("Sculk-Cluster (" + sculk + ", +" + fmt(sculkScore) + ")");

        // Repeated candidate fingerprints across three or more distinct chunks are treated
        // as a repeated/decoy layout and hidden from BaseFinder and ESP.
        boolean repeatedPattern = score >= threshold.get() && signalKinds >= minSignals.getInt()
                && isRepeatedFakePattern(storage, shulker, hopper, furnace, redstone, spawner, cx, cz);
        if (repeatedPattern) {
            res.status = Status.CLEAN;
            res.reasons.clear();
            dirty = true;
            marksDirty = true;
        } else if (score >= threshold.get() && signalKinds >= minSignals.getInt()) {
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

    private boolean isRepeatedFakePattern(int storage, int shulker, int hopper, int furnace,
                                          int redstone, int spawner, int cx, int cz) {
        for (FakePattern p : fakePatterns) {
            if (!similar(p.storage, storage) || !similar(p.shulker, shulker)
                    || !similar(p.hopper, hopper) || !similar(p.furnace, furnace)
                    || !similar(p.redstone, redstone) || !similar(p.spawner, spawner)) continue;
            String location = cx + "," + cz;
            if (!p.seenChunks.contains(location)) {
                p.seenChunks.add(location);
                saveFakePatterns();
            }
            if (p.seenChunks.size() >= 3) {
                for (String seen : p.seenChunks) {
                    String[] parts = seen.split(",");
                    if (parts.length != 2) continue;
                    try {
                        ChunkResult previous = results.get(key(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
                        if (previous != null) {
                            previous.status = Status.CLEAN;
                            previous.reasons.clear();
                        }
                    } catch (NumberFormatException ignored) { }
                }
                dirty = true;
                marksDirty = true;
                saveMarks();
                return true;
            }
            return false;
        }
        FakePattern p = new FakePattern();
        p.storage = storage; p.shulker = shulker; p.hopper = hopper;
        p.furnace = furnace; p.redstone = redstone; p.spawner = spawner;
        p.seenChunks.add(cx + "," + cz);
        fakePatterns.add(p);
        saveFakePatterns();
        return false;
    }

    private static boolean similar(int a, int b) {
        return Math.abs(a - b) <= Math.max(2, Math.round(Math.max(a, b) * 0.25f));
    }

    private Path fakePatternsFile() { return Storage.dir().resolve("fake_patterns_" + ctxKey + ".json"); }
    private void saveFakePatterns() {
        if (ctxKey == null) return;
        try { Files.writeString(fakePatternsFile(), GSON.toJson(fakePatterns)); }
        catch (IOException e) { Nightfall.LOGGER.error("Fake-Base-Fingerprints konnten nicht gespeichert werden", e); }
    }
    private void loadFakePatterns() {
        fakePatterns.clear();
        Path f = fakePatternsFile();
        if (!Files.exists(f)) return;
        try {
            List<FakePattern> loaded = GSON.fromJson(Files.readString(f), FAKE_LIST);
            if (loaded != null) for (FakePattern pattern : loaded) {
                if (pattern == null) continue;
                if (pattern.seenChunks == null) pattern.seenChunks = new ArrayList<>();
                fakePatterns.add(pattern);
            }
        } catch (Exception e) { Nightfall.LOGGER.error("Fake-Base-Fingerprints defekt: {}", f, e); }
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
                out.add(m);
            }
            Path f = marksFile();
            if (out.isEmpty() && !Files.exists(f)) return;
            Files.writeString(f, GSON.toJson(out));
            marksDirty = false;
        } catch (IOException e) { Nightfall.LOGGER.error("Markierungen konnten nicht gespeichert werden", e); }
    }
    private void loadMarks() {
        Path f = marksFile();
        if (!Files.exists(f)) return;
        try {
            List<Mark> in = GSON.fromJson(Files.readString(f), MARK_LIST);
            if (in == null) return;
            for (Mark m : in) {
                ChunkResult r = new ChunkResult(m.cx, m.cz);
                r.score = m.score; r.status = Status.valueOf(m.status); r.foundAtMillis = m.found;
                if (m.reasons != null) r.reasons.addAll(m.reasons);
                r.lastScanTick = 0;
                results.put(key(m.cx, m.cz), r);
            }
            dirty = true;
        } catch (Exception e) { Nightfall.LOGGER.error("Markierungsdatei defekt: {}", f, e); }
    }
}
