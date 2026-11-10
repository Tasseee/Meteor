package dev.nightfall.scanner;

import java.util.ArrayList;
import java.util.List;

public final class ChunkResult {
    public enum Status { CLEAN, SUS, GONE }

    public final int cx, cz;
    public double score;
    public double naturalSignalScore;
    public Status status = Status.CLEAN;
    public long foundAtMillis;
    public long lastScanTick;
    public final List<String> reasons = new ArrayList<>();

    public ChunkResult(int cx, int cz) { this.cx = cx; this.cz = cz; }

    public int blockX() { return cx * 16 + 8; }
    public int blockZ() { return cz * 16 + 8; }
}
