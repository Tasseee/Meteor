package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import net.minecraft.class_238;
import net.minecraft.class_332;
import net.minecraft.class_3965;
import net.minecraft.class_2338;

/** Draws a configurable neon outline around the block under the crosshair. */
public final class TargetBlockOutline extends Module {
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0x39FFB6));
   private final NumberSetting thickness = this.add(new NumberSetting("Linienstaerke", 2.0, 1.0, 4.0, 1.0));
   private final NumberSetting neonStrength = this.add(new NumberSetting("Neon-Staerke", 24.0, 0.0, 60.0, 4.0));

   public TargetBlockOutline() {
      super("Block-Outline", "Zeigt eine einstellbare Neon-Kontur um den anvisierten Block.", Category.VISUALS);
   }

   @Override
   public void renderHud(class_332 context) {
      if (mc.field_1724 == null || mc.field_1687 == null || mc.field_1755 != null
         || !(mc.field_1765 instanceof class_3965 hit) || !Projection.valid()) {
         return;
      }

      class_2338 blockPos = hit.method_17777();
      class_2338 supportPos = blockPos.method_10074();

      // Only outline blocks that have a real collision shape directly below them.
      // This prevents floating blocks from being highlighted.
      if (mc.field_1687.method_8320(supportPos).method_26220(mc.field_1687, supportPos).method_1110()) {
         return;
      }

      class_238 blockBox = new class_238(blockPos);
      Draw.box(context, blockBox, 0xFF000000 | color.rgb(), thickness.getInt(), true, true, neonStrength.getInt());
   }
}
