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
import dev.nightfall.util.HugoSmpCompat;
import net.minecraft.block.entity.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
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
    private final NumberSetting minSignals = add(new NumberSetting("Min. Signaltypen", 2, 1, 5, 1));
    private final NumberSetting wStorage  = add(new NumberSetting("Gewicht Storage", 1.0, 0.1, 5.0, 0.1));
    private final NumberSetting wHopper   = add(new NumberSetting("Gewicht Hopper", 1.5, 0.1, 5.0, 0.1));
    private final NumberSetting wFurnace  = add(new NumberSetting("Gewicht Furnace", 0.7, 0.1, 5.0, 0.1));
    private final NumberSetting wRedstone = add(new NumberSetting("Gewicht Redstone", 1.2, 0.1, 5.0, 0.1));
    private final NumberSetting wCluster  = add(new NumberSetting("Gewicht Cluster", 1.5, 0.0, 5.0, 0.1));
    private final BoolSetting   chat      = add(new BoolSetting("Chat-Meldung", true));
    private final BoolSetting   hugoAntiEspGuard = add(new BoolSetting("HugoSMP Anti-ESP Schutz", true));

    private static final Gson GSON = new Gson();
    private static final Type MARK_LIST = new TypeToken<List<Mark>>() {}.getType();

    private static final class Mark {
        int cx, cz; double score; String status; long found; List<String> reasons;
    }

    private final Map<Long, ChunkResult> results = new HashMap<>();
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
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
    private boolean maskStateInitialized, lastMaskActive;
    private long resumeAfterMaskTick;

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
    public boolean isHugoSmpMaskActive() { return hugoAntiEspGuard.get() && HugoSmpCompat.undergroundBlocksMayBeMasked(); }
    public boolean isHugoSmpGuardEnabled() { return hugoAntiEspGuard.get() && HugoSmpCompat.isHugoSmpServer(); }
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
        results.clear(); queue.clear(); top.clear(); marks.clear(); dirty = true; marksDirty = false; candidatesLastRefill = 0; scannedSinceRefill = 0;
    }

    public void clearMarks() { clearData(); marksDirty = true; saveMarks(); }

    @Override protected void onEnable() {
        try { dev.nightfall.module.ModuleManager.get(IntelligenceScanner.class).setEnabled(true); }
        catch (IllegalStateException ignored) { }
    }

    @Override protected void onDisable() {
        try { dev.nightfall.module.ModuleManager.get(IntelligenceScanner.class).setEnabled(false); }
        catch (IllegalStateException ignored) { }
        clear();
    }

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
            maskStateInitialized = false;
        }
        tick++;
        boolean maskActive = isHugoSmpMaskActive();
        if (!maskStateInitialized) {
            lastMaskActive = maskActive;
            maskStateInitialized = true;
        } else if (maskActive != lastMaskActive) {
            lastMaskActive = maskActive;
            queue.clear();
            for (ChunkResult result : results.values()) result.lastScanTick = 0;
            // Give the server/client a moment to deliver real blocks after entering the unmasked area.
            resumeAfterMaskTick = maskActive ? tick : tick + 40;
        }
        if (paused || tick < resumeAfterMaskTick) { lastScanned = 0; lastScanNanos = 0; return; }

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

    private void scan(ClientWorld w, long k) {
        int cx = (int) k, cz = (int) (k >> 32);
        WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz);
        if (ch == null) return;

        // A manually confirmed FakeBase teaches its block-entity layout. Later scans
        // recognize matching layouts even when their chunk contents are rotated.
        FakeBases.processChunk(ch, cx, cz);

        int storage = 0, shulker = 0, hopper = 0, furnace = 0, redstone = 0;
        Map<Integer, Integer> cells = new HashMap<>();

        boolean maskActive = isHugoSmpMaskActive();
        for (BlockEntity be : ch.getBlockEntities().values()) {
            if (maskActive && be.getPos().getY() <= 0) continue;
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
        double blockScore = storage * wStorage.get() + shulker * wStorage.get() * 1.5
                + hopper * wHopper.get() + furnace * wFurnace.get() + redstone * wRedstone.get() + cluster;
        ChunkResult old = results.get(k);
        ChunkResult res = old != null ? old : new ChunkResult(cx, cz);
        Status prev = res.status;
        res.blockScore = blockScore;
        res.blockSignalKinds = (storage + shulker > 0 ? 1 : 0) + (hopper > 0 ? 1 : 0)
                + (furnace > 0 ? 1 : 0) + (cluster > 0 ? 1 : 0);
        res.serverLimited = maskActive;
        res.stashScore = storage * wStorage.get() + shulker * wStorage.get() * 1.5
                + hopper * wHopper.get() + furnace * wFurnace.get() + cluster;
        res.score = combinedScore(res);

        res.lastScanTick = tick;
        res.blockReasons.clear();
        res.reasons.clear();
        // Expose the weighted contributions so a score can be inspected and tuned.
        if (storage > 0) res.blockReasons.add(storage + " Chest/Barrel (" + fmt(storage * wStorage.get()) + ")");
        if (shulker > 0) res.blockReasons.add(shulker + " Shulker (" + fmt(shulker * wStorage.get() * 1.5) + ")");
        if (hopper > 0) res.blockReasons.add(hopper + " Hopper (" + fmt(hopper * wHopper.get()) + ")");
        if (furnace > 0) res.blockReasons.add(furnace + " Furnace (" + fmt(furnace * wFurnace.get()) + ")");
        if (redstone > 0) res.blockReasons.add(redstone + " Dispenser/Comparator/Crafter (" + fmt(redstone * wRedstone.get()) + ")");
        if (cluster > 0) res.blockReasons.add("Lagerzone (" + maxCell + " in 4x4x4, +" + fmt(cluster) + ")");
        if (maskActive) res.blockReasons.add("HugoSMP Anti-ESP: Blockdaten <=Y0 vorsorglich ignoriert (Spieler über Y5)");

        res.reasons.addAll(res.blockReasons);
        res.reasons.addAll(res.intelligenceReasons);
        reassess(res, prev, true);
        if (old == null) results.put(k, res);
    }


    public void updateIntelligence(int cx, int cz, double entityScore, double geodeScore,
                                   double valuableScore, double redstoneScore, double farmScore,
                                   double activityScore, double completeness, List<String> reasons) {
        long k = key(cx, cz);
        ChunkResult res = results.computeIfAbsent(k, ignored -> new ChunkResult(cx, cz));
        res.entityScore = Math.max(0, entityScore);
        res.geodeScore = Math.max(0, geodeScore);
        res.valuableScore = Math.max(0, valuableScore);
        res.redstoneScore = Math.max(0, redstoneScore);
        res.farmScore = Math.max(0, farmScore);
        res.activityScore = Math.max(0, activityScore);
        res.dataCompleteness = Math.max(0, Math.min(1, completeness));
        res.intelligenceReasons.clear();
        if (reasons != null) res.intelligenceReasons.addAll(reasons);
        res.score = combinedScore(res);
        res.reasons.clear();
        res.reasons.addAll(res.blockReasons);
        res.reasons.addAll(res.intelligenceReasons);
        reassess(res, res.status, true);
        dirty = true;
    }

    /**
     * Keep score and status in sync regardless of which scanner published the latest evidence.
     * Repeated refreshes do not produce repeated chat messages; only a transition into SUS does.
     */
    private void reassess(ChunkResult res, Status previous, boolean allowNotification) {
        Status next;
        int signalKinds = res.blockSignalKinds
                + (res.entityScore > 0 ? 1 : 0)
                + (res.geodeScore > 0 ? 1 : 0)
                + (res.valuableScore > 0 ? 1 : 0)
                + (res.redstoneScore > 0 ? 1 : 0)
                + (res.farmScore > 0 ? 1 : 0)
                + (res.activityScore > 0 ? 1 : 0);

        if (FakeBases.isKnownFakeChunk(res.cx, res.cz)) {
            next = Status.DECOY;
            String label = FakeBases.matchLabel(res.cx, res.cz);
            String reason = label == null
                    ? "FakeBase/Decoy markiert — normaler SUS-Alarm unterdrückt"
                    : "Automatisch als FakeBase '" + label + "' erkannt — normaler SUS-Alarm unterdrückt";
            if (!res.reasons.contains(reason)) res.reasons.add(reason);
        } else if (res.score >= threshold.get() && signalKinds >= minSignals.getInt()) {
            next = Status.SUS;
            if (previous != Status.SUS) {
                res.foundAtMillis = System.currentTimeMillis();
                if (allowNotification && chat.get() && mc.player != null) {
                    mc.player.sendMessage(Text.literal("[Nightfall] SUS Chunk " + res.cx + "," + res.cz
                            + " (Block " + res.blockX() + ", " + res.blockZ() + ") Score " + fmt(res.score)
                            + " - " + String.join(", ", res.reasons)), false);
                }
            }
        } else if (previous == Status.SUS || previous == Status.GONE) {
            next = Status.GONE;
        } else {
            next = Status.CLEAN;
            if (res.score >= threshold.get() && signalKinds < minSignals.getInt())
                res.reasons.add("Score hoch, aber nur " + signalKinds + " Signaltyp(en) — nicht als SUS markiert");
        }

        if (next != previous) {
            dirty = true;
            marksDirty = true;
        }
        res.status = next;
    }

    private static double combinedScore(ChunkResult r) {
        return r.blockScore + r.entityScore + r.geodeScore + r.valuableScore
                + r.redstoneScore + r.farmScore + r.activityScore;
    }

    private static String fmt(double value) { return String.format(Locale.ROOT, "%.1f", value); }

    private void rebuildLists() {
        top.clear(); marks.clear();
        for (ChunkResult r : results.values()) {
            if (r.status == Status.SUS) { top.add(r); marks.add(r); }
            else if (r.status == Status.GONE || r.status == Status.DECOY) marks.add(r);
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
                r.lastScanTick = 0;
                results.put(key(m.cx, m.cz), r);
            }
            dirty = true;
        } catch (Exception e) {
            Nightfall.LOGGER.error("Markierungsdatei defekt: {}", f, e);
        }
    }
}
