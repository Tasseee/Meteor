package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_2378;
import net.minecraft.class_238;
import net.minecraft.class_332;
import net.minecraft.class_7923;

public final class OreESP extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 48.0, 8.0, 96.0, 4.0));
   private final NumberSetting maxBlocks = this.add(new NumberSetting("Max Markierungen", 500.0, 20.0, 1000.0, 20.0));
   private final List<class_238> found = new ArrayList<>();
   private int timer;

   public OreESP() {
      super("OreESP", "Markiert Erze im geladenen Gebiet; Netherquarz und Nethergold werden ignoriert.", Category.VISUALS);
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
      if (mc.field_1724 == null || mc.field_1687 == null || this.timer-- > 0) return;
      this.timer = 12;
      this.found.clear();
      class_2338 p = mc.field_1724.method_24515();
      int r = this.range.getInt();
      int cap = this.maxBlocks.getInt();
      int minY = Math.max(mc.field_1687.method_31607(), p.method_10264() - r);
      int maxY = Math.min(mc.field_1687.method_31600(), p.method_10264() + r);
      int minX = p.method_10263() - r, maxX = p.method_10263() + r;
      int minZ = p.method_10260() - r, maxZ = p.method_10260() + r;

      for (int cx = minX >> 4; cx <= maxX >> 4 && this.found.size() < cap; cx++) {
         for (int cz = minZ >> 4; cz <= maxZ >> 4 && this.found.size() < cap; cz++) {
            if (mc.field_1687.method_2935().method_21730(cx, cz) == null) continue;
            int sx = Math.max(minX, cx << 4), ex = Math.min(maxX, (cx << 4) + 15);
            int sz = Math.max(minZ, cz << 4), ez = Math.min(maxZ, (cz << 4) + 15);
            for (int x = sx; x <= ex && this.found.size() < cap; x++) {
               for (int z = sz; z <= ez && this.found.size() < cap; z++) {
                  for (int y = minY; y <= maxY && this.found.size() < cap; y++) {
                     class_2338 bp = new class_2338(x, y, z);
                     if (this.isOre(mc.field_1687.method_8320(bp).method_26204())) this.found.add(new class_238(bp));
                  }
               }
            }
         }
      }
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (Projection.valid()) for (class_238 box : this.found) Draw.box(ctx, box, -16711936, 2, true);
   }

   @Override
   protected void onDisable() { this.found.clear(); }

   @Override
   public void onWorldLeave() { this.found.clear(); this.timer = 0; }
}
