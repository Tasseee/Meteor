package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2818;
import net.minecraft.class_638;
import net.minecraft.class_238;
import net.minecraft.class_332;

public final class StructureESP extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 48.0, 8.0, 96.0, 8.0));
   private final NumberSetting budgetMs = this.add(new NumberSetting("Suchbudget (ms/Tick)", 2.0, 0.5, 5.0, 0.1));
   private final BoolSetting spawners = this.add(new BoolSetting("Spawner", true));
   private final BoolSetting portals = this.add(new BoolSetting("Portalrahmen", true));
   private final BoolSetting trial = this.add(new BoolSetting("Trial-Spawner", true));
   private final BoolSetting chests = this.add(new BoolSetting("Kisten", true));
   private final BoolSetting suspicious = this.add(new BoolSetting("Verdächtige Bloecke", true));
   private static final int MAX_CHECKS_PER_TICK = 12000;
   private final List<class_238> positions = new ArrayList<>();
   private final List<class_238> working = new ArrayList<>();
   private class_638 scanWorld;
   private int timer;
   private int cursor;
   private int total;
   private int minX, minY, minZ, maxX, maxY, maxZ;
   private int scanChunkX = Integer.MIN_VALUE;
   private int scanChunkZ = Integer.MIN_VALUE;
   private class_2818 scanChunk;
   private boolean scanning;

   public StructureESP() {
      super("StructureESP", "Markiert Struktur-Hinweise in bereits geladenen Chunks.", Category.VISUALS);
   }

   private boolean match(class_2248 b) {
      return this.spawners.get() && b == class_2246.field_10260
         || this.portals.get() && b == class_2246.field_10398
         || this.trial.get() && (b == class_2246.field_47336 || b == class_2246.field_48851)
         || this.chests.get() && (b == class_2246.field_10034 || b == class_2246.field_10380)
         || this.suspicious.get() && (b == class_2246.field_42728 || b == class_2246.field_43227);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null) {
         this.resetScan(true);
         return;
      }

      if (this.scanWorld != mc.field_1687) {
         this.scanWorld = mc.field_1687;
         this.resetScan(true);
      }

      int playerChunkX = mc.field_1724.method_31477() >> 4;
      int playerChunkZ = mc.field_1724.method_31479() >> 4;
      if (this.scanning && (playerChunkX != (this.minX + (this.maxX - this.minX) / 2) >> 4
         || playerChunkZ != (this.minZ + (this.maxZ - this.minZ) / 2) >> 4)) {
         this.scanning = false;
         this.timer = 0;
      }

      if (!this.scanning) {
         if (this.timer > 0) {
            this.timer--;
            return;
         }
         this.beginScan();
      }

      if (this.scanning) this.scanStep();
   }

   private void beginScan() {
      class_2338 playerPos = mc.field_1724.method_24515();
      int r = this.range.getInt();
      this.minX = playerPos.method_10263() - r;
      this.maxX = playerPos.method_10263() + r;
      this.minY = Math.max(mc.field_1687.method_31607(), playerPos.method_10264() - r);
      this.maxY = Math.min(mc.field_1687.method_31600(), playerPos.method_10264() + r);
      this.minZ = playerPos.method_10260() - r;
      this.maxZ = playerPos.method_10260() + r;
      int xCount = this.maxX - this.minX + 1;
      int yCount = this.maxY - this.minY + 1;
      int zCount = this.maxZ - this.minZ + 1;
      this.total = xCount * yCount * zCount;
      this.cursor = 0;
      this.scanChunkX = Integer.MIN_VALUE;
      this.scanChunkZ = Integer.MIN_VALUE;
      this.scanChunk = null;
      this.working.clear();
      this.scanning = true;
   }

   private void scanStep() {
      long deadline = System.nanoTime() + (long)(this.budgetMs.get() * 1_000_000.0);
      int checks = 0;
      int yCount = this.maxY - this.minY + 1;
      int zCount = this.maxZ - this.minZ + 1;
      int cap = 160;

      while (this.cursor < this.total && this.working.size() < cap
         && checks < MAX_CHECKS_PER_TICK && (checks == 0 || System.nanoTime() < deadline)) {
         int xIndex = this.cursor / (zCount * yCount);
         int remainder = this.cursor % (zCount * yCount);
         int zIndex = remainder / yCount;
         int y = this.minY + remainder % yCount;
         int x = this.minX + xIndex;
         int z = this.minZ + zIndex;
         int chunkX = x >> 4;
         int chunkZ = z >> 4;

         if (chunkX != this.scanChunkX || chunkZ != this.scanChunkZ) {
            this.scanChunkX = chunkX;
            this.scanChunkZ = chunkZ;
            this.scanChunk = mc.field_1687.method_2935().method_21730(chunkX, chunkZ);
         }

         if (this.scanChunk != null) {
            class_2338 blockPos = new class_2338(x, y, z);
            if (this.match(this.scanChunk.method_8320(blockPos).method_26204())) {
               this.working.add(new class_238(blockPos));
            }
         }

         this.cursor++;
         checks++;
      }

      if (this.cursor >= this.total || this.working.size() >= cap) {
         this.positions.clear();
         this.positions.addAll(this.working);
         this.working.clear();
         this.scanning = false;
         this.timer = 30;
      }
   }

   private void resetScan(boolean clearVisible) {
      this.scanning = false;
      this.cursor = 0;
      this.total = 0;
      this.working.clear();
      this.scanChunk = null;
      this.scanChunkX = Integer.MIN_VALUE;
      this.scanChunkZ = Integer.MIN_VALUE;
      if (clearVisible) this.positions.clear();
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (Projection.valid()) {
         for (class_238 box : this.positions) {
            Draw.box(ctx, box, -571164350, 1, true);
         }
      }
   }

   @Override
   protected void onDisable() {
      this.resetScan(true);
   }

   @Override
   public void onWorldLeave() {
      this.resetScan(true);
      this.scanWorld = null;
      this.timer = 0;
   }
}
