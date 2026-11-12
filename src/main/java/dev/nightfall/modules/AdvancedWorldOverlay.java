package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.class_1923;
import net.minecraft.class_2338;
import net.minecraft.class_332;

public final class AdvancedWorldOverlay extends Module {
   private final BoolSetting coordinates = this.add(new BoolSetting("Koordinaten", true));
   private final BoolSetting chunk = this.add(new BoolSetting("Chunk und Region", true));
   private final BoolSetting dimension = this.add(new BoolSetting("Dimension", true));
   private final BoolSetting light = this.add(new BoolSetting("Lichtlevel", true));
   private final BoolSetting facing = this.add(new BoolSetting("Blickrichtung", true));

   public AdvancedWorldOverlay() {
      super("AdvancedWorldOverlay", "Zeigt erweiterte Welt-, Chunk- und Positionsdaten an.", Category.WORLD);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         class_2338 pos = mc.field_1724.method_24515();
         class_1923 cp = new class_1923(pos);
         int x = 8;
         int y = 8;
         int color = -1512718;
         ctx.method_51433(mc.field_1772, "Nightfall World Overlay", x, y, -6587393, true);
         y += 12;
         if (this.coordinates.get()) {
            ctx.method_51433(mc.field_1772, "XYZ: " + pos.method_10263() + " / " + pos.method_10264() + " / " + pos.method_10260(), x, y, color, true);
            y += 11;
         }

         if (this.chunk.get()) {
            ctx.method_51433(
               mc.field_1772,
               "Chunk: " + cp.field_9181 + " / " + cp.field_9180 + "  |  Region: " + (cp.field_9181 >> 5) + " / " + (cp.field_9180 >> 5),
               x,
               y,
               color,
               true
            );
            y += 11;
         }

         if (this.dimension.get()) {
            ctx.method_51433(mc.field_1772, "Dimension: " + mc.field_1687.method_27983().method_29177(), x, y, color, true);
            y += 11;
         }

         if (this.light.get()) {
            ctx.method_51433(mc.field_1772, "Lichtlevel: " + mc.field_1687.method_22339(pos), x, y, color, true);
            y += 11;
         }

         if (this.facing.get()) {
            ctx.method_51433(mc.field_1772, "Richtung: " + mc.field_1724.method_5735().method_15434().toUpperCase(), x, y, color, true);
         }
      }
   }
}
