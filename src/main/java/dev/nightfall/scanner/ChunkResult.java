package dev.nightfall.scanner;

import java.util.ArrayList;
import java.util.List;

public final class ChunkResult {
    public enum Status { CLEAN, SUS, GONE, DECOY }
    public final int cx, cz;
    public double score, blockScore, stashScore, entityScore, geodeScore;
    public double valuableScore, redstoneScore, farmScore, activityScore;
    /** 0..1 deep-scan coverage; not a probability. */
    public double dataCompleteness;
    /** True when server-side anti-ESP behavior makes part of the scanned volume unavailable. */
    public boolean serverLimited;
    public Status status = Status.CLEAN;
    public long foundAtMillis, lastScanTick;
    public final List<String> reasons = new ArrayList<>();
    public final List<String> blockReasons = new ArrayList<>();
    public final List<String> intelligenceReasons = new ArrayList<>();
    public ChunkResult(int cx, int cz) { this.cx = cx; this.cz = cz; }
    public int blockX() { return cx * 16 + 8; }
    public int blockZ() { return cz * 16 + 8; }
}
