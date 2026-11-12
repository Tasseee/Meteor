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
import net.minecraft.class_238;
import net.minecraft.class_332;

public final class StructureESP extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 48.0, 8.0, 96.0, 8.0));
   private final BoolSetting spawners = this.add(new BoolSetting("Spawner", true));
   private final BoolSetting portals = this.add(new BoolSetting("Portalrahmen", true));
   private final BoolSetting trial = this.add(new BoolSetting("Trial-Spawner", true));
   private final BoolSetting chests = this.add(new BoolSetting("Kisten", true));
   private final BoolSetting suspicious = this.add(new BoolSetting("Verdächtige Bloecke", true));
   private final List<class_238> positions = new ArrayList<>();
   private int timer;

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
      if (mc.field_1724 != null && mc.field_1687 != null && this.timer-- <= 0) {
         this.timer = 30;
         this.positions.clear();
         class_2338 p = mc.field_1724.method_24515();
         int r = this.range.getInt();
         int cap = 160;

         for (int cx = p.method_10263() - r >> 4; cx <= p.method_10263() + r >> 4 && this.positions.size() < cap; cx++) {
            for (int cz = p.method_10260() - r >> 4; cz <= p.method_10260() + r >> 4 && this.positions.size() < cap; cz++) {
               if (mc.field_1687.method_2935().method_21730(cx, cz) != null) {
                  int x0 = Math.max(p.method_10263() - r, cx << 4);
                  int x1 = Math.min(p.method_10263() + r, (cx << 4) + 15);
                  int z0 = Math.max(p.method_10260() - r, cz << 4);
                  int z1 = Math.min(p.method_10260() + r, (cz << 4) + 15);

                  for (int x = x0; x <= x1 && this.positions.size() < cap; x++) {
                     for (int z = z0; z <= z1 && this.positions.size() < cap; z++) {
                        for (int y = Math.max(mc.field_1687.method_31607(), p.method_10264() - r);
                           y <= Math.min(mc.field_1687.method_31600(), p.method_10264() + r) && this.positions.size() < cap;
                           y++
                        ) {
                           class_2338 bp = new class_2338(x, y, z);
                           if (this.match(mc.field_1687.method_8320(bp).method_26204())) {
                              this.positions.add(new class_238(bp));
                           }
                        }
                     }
                  }
               }
            }
         }
      }
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
      this.positions.clear();
   }

   @Override
   public void onWorldLeave() {
      this.positions.clear();
      this.timer = 0;
   }
}
