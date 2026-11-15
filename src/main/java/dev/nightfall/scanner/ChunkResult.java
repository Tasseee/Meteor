package dev.nightfall.scanner;

import java.util.ArrayList;
import java.util.List;

public final class ChunkResult {
   public final int cx;
   public final int cz;
   public double score;
   public double naturalSignalScore;
   public Confidence confidence = Confidence.LOW;
   public Naturalness naturalness = Naturalness.UNCLEAR;
   public int largestConnectedGroup;
   public int connectedBlockEntities;
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

   public enum Confidence {
      LOW("NIEDRIG"), MEDIUM("MITTEL"), HIGH("HOCH");
      private final String label;
      Confidence(String label) { this.label = label; }
      public String label() { return this.label; }
   }

   public enum Naturalness {
      NATURAL("NATUERLICH"), PLAYER_MODIFIED("VERAENDERT"), MIXED("GEMISCHT"), UNCLEAR("UNKLAR");
      private final String label;
      Naturalness(String label) { this.label = label; }
      public String label() { return this.label; }
   }

   public enum Status {
      CLEAN,
      SUS,
      GONE;
   }
}
