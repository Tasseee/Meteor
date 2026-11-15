package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_2561;
import net.minecraft.class_332;

public final class OreESP extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 48.0, 8.0, 96.0, 4.0));
   private final NumberSetting maxBlocks = this.add(new NumberSetting("Max Markierungen", 500.0, 20.0, 1000.0, 20.0));
   private final BoolSetting groupVeins = this.add(new BoolSetting("Adern zusammenfassen", true));
   private final ColorSetting veinColor = this.add(new ColorSetting("Adern-Farbe", 0x00FF00));
   private final List<class_238> found = new ArrayList<>();
   private final Set<String> announcedVeins = new HashSet<>();
   private final Set<OrePos> previousOres = new HashSet<>();
   private int timer;
   private long sessionTicks;
   private int minedOreCount;

   public OreESP() {
      super("OreESP", "Markiert Erze im geladenen Gebiet, fasst Adern zusammen und zeigt Farm-Statistiken.", Category.VISUALS);
   }

   private boolean isOre(class_2248 block) {
      class_2378 registry = (class_2378)class_7923.field_41175;
      String id = registry.method_10221(block).toString();
      if (id.equals("minecraft:ancient_debris")) return true;
      if (id.equals("minecraft:nether_quartz_ore") || id.equals("minecraft:nether_gold_ore")) return false;
      return id.endsWith("_ore");
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null) return;
      this.sessionTicks++;
      if (this.timer-- > 0) return;
      this.timer = 12;
      this.found.clear();
      List<OrePos> ores = new ArrayList<>();
      Set<OrePos> currentOres = new HashSet<>();
      class_2338 p = mc.field_1724.method_24515();
      int r = this.range.getInt();
      int cap = this.maxBlocks.getInt();
      int minY = Math.max(mc.field_1687.method_31607(), p.method_10264() - r);
      int maxY = Math.min(mc.field_1687.method_31600(), p.method_10264() + r);
      int minX = p.method_10263() - r, maxX = p.method_10263() + r;
      int minZ = p.method_10260() - r, maxZ = p.method_10260() + r;

      for (int cx = minX >> 4; cx <= maxX >> 4 && ores.size() < cap; cx++) {
         for (int cz = minZ >> 4; cz <= maxZ >> 4 && ores.size() < cap; cz++) {
            if (mc.field_1687.method_2935().method_21730(cx, cz) == null) continue;
            int sx = Math.max(minX, cx << 4), ex = Math.min(maxX, (cx << 4) + 15);
            int sz = Math.max(minZ, cz << 4), ez = Math.min(maxZ, (cz << 4) + 15);
            for (int x = sx; x <= ex && ores.size() < cap; x++) {
               for (int z = sz; z <= ez && ores.size() < cap; z++) {
                  for (int y = minY; y <= maxY && ores.size() < cap; y++) {
                     class_2338 bp = new class_2338(x, y, z);
                     class_2248 block = mc.field_1687.method_8320(bp).method_26204();
                     if (this.isOre(block)) {
                        OrePos ore = new OrePos(x, y, z);
                        ores.add(ore);
                        currentOres.add(ore);
                     }
                  }
               }
            }
         }
      }

      // Count ore positions that disappeared while they remained inside the scanned, loaded area.
      // This is an estimate: another player or a world update can also remove an ore block.
      for (OrePos old : this.previousOres) {
         if (old.x() < minX || old.x() > maxX || old.y() < minY || old.y() > maxY
            || old.z() < minZ || old.z() > maxZ || currentOres.contains(old)) continue;
         if (mc.field_1687.method_2935().method_21730(old.x() >> 4, old.z() >> 4) == null) continue;
         class_2338 oldPos = new class_2338(old.x(), old.y(), old.z());
         if (!this.isOre(mc.field_1687.method_8320(oldPos).method_26204())) this.minedOreCount++;
      }
      this.previousOres.clear();
      this.previousOres.addAll(currentOres);

      if (this.groupVeins.get()) {
         this.groupConnectedOres(ores);
      } else {
         for (OrePos ore : ores) this.found.add(new class_238(
            ore.x(), ore.y(), ore.z(), ore.x() + 1.0, ore.y() + 1.0, ore.z() + 1.0
         ));
      }
   }

   private void groupConnectedOres(List<OrePos> ores) {
      Set<OrePos> remaining = new HashSet<>(ores);
      ArrayDeque<OrePos> queue = new ArrayDeque<>();

      while (!remaining.isEmpty()) {
         OrePos start = remaining.iterator().next();
         remaining.remove(start);
         queue.add(start);

         int minX = start.x(), minY = start.y(), minZ = start.z();
         int maxX = minX, maxY = minY, maxZ = minZ;
         int veinSize = 0;

         while (!queue.isEmpty()) {
            OrePos current = queue.removeFirst();
            veinSize++;
            minX = Math.min(minX, current.x());
            minY = Math.min(minY, current.y());
            minZ = Math.min(minZ, current.z());
            maxX = Math.max(maxX, current.x());
            maxY = Math.max(maxY, current.y());
            maxZ = Math.max(maxZ, current.z());

            addNeighbor(remaining, queue, current, current.x() + 1, current.y(), current.z());
            addNeighbor(remaining, queue, current, current.x() - 1, current.y(), current.z());
            addNeighbor(remaining, queue, current, current.x(), current.y() + 1, current.z());
            addNeighbor(remaining, queue, current, current.x(), current.y() - 1, current.z());
            addNeighbor(remaining, queue, current, current.x(), current.y(), current.z() + 1);
            addNeighbor(remaining, queue, current, current.x(), current.y(), current.z() - 1);
         }

         this.found.add(new class_238(minX, minY, minZ, maxX + 1.0, maxY + 1.0, maxZ + 1.0));
         if (veinSize >= 10) {
            String key = minX + ":" + minY + ":" + minZ + ":" + maxX + ":" + maxY + ":" + maxZ + ":" + veinSize;
            if (this.announcedVeins.add(key) && mc.field_1724 != null) {
               mc.field_1724.method_7353(class_2561.method_43470(veinSize + "x Ore vein found!"), false);
            }
         }
      }
   }

   private static void addNeighbor(Set<OrePos> remaining, ArrayDeque<OrePos> queue, OrePos current, int x, int y, int z) {
      OrePos neighbor = new OrePos(x, y, z);
      if (remaining.remove(neighbor)) queue.addLast(neighbor);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (Projection.valid()) {
         int color = this.veinColor.rgb() | 0xFF000000;
         for (class_238 box : this.found) Draw.box(ctx, box, color, 2, true);
      }
      if (mc.field_1724 != null && this.sessionTicks > 0L) {
         double elapsedSeconds = this.sessionTicks / 20.0;
         String average = this.minedOreCount > 0
            ? String.format(java.util.Locale.ROOT, "%.1f s/Erz", elapsedSeconds / this.minedOreCount)
            : "-- s/Erz";
         ctx.method_51433(mc.field_1772, "OreESP: " + this.minedOreCount + " abgebaut | Ø " + average, 8, 96, -1, true);
      }
   }

   private void resetSession() {
      this.sessionTicks = 0L;
      this.minedOreCount = 0;
      this.previousOres.clear();
   }

   @Override
   protected void onDisable() {
      this.found.clear();
      this.announcedVeins.clear();
      this.resetSession();
      this.timer = 0;
   }

   @Override
   public void onWorldLeave() {
      this.found.clear();
      this.announcedVeins.clear();
      this.resetSession();
      this.timer = 0;
   }

   private record OrePos(int x, int y, int z) {
   }
}
