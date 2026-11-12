package dev.nightfall.scanner;

import java.util.ArrayList;
import java.util.List;

public final class ChunkResult {
   public final int cx;
   public final int cz;
   public double score;
   public double naturalSignalScore;
   public ChunkResult.Status status = ChunkResult.Status.CLEAN;
   public long foundAtMillis;
   public long lastScanTick;
   public final List<String> reasons = new ArrayList<>();

   public ChunkResult(int cx, int cz) {
      this.cx = cx;
      this.cz = cz;
   }

   public int blockX() {
      return this.cx * 16 + 8;
   }

   public int blockZ() {
      return this.cz * 16 + 8;
   }

   public static enum Status {
      CLEAN,
      SUS,
      GONE;
   }
}
