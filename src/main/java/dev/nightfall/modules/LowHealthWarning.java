package dev.nightfall.modules;

import dev.nightfall.gui.HudEditorScreen;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.NotificationCenter;
import net.minecraft.class_332;

public final class LowHealthWarning extends HudModule {
   private final NumberSetting threshold = this.add(new NumberSetting("Schwelle Herzen", 4.0, 1.0, 10.0, 0.5));
   private boolean wasLow;

   public LowHealthWarning() {
      super("LowHealthWarning", "Pulsierende Warnung bei wenig Leben.");
      this.setEnabled(true);
   }

   @Override
   public void onWorldLeave() {
      this.wasLow = false;
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{sw / 2 - 60, 36};
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null) {
         this.wasLow = false;
      } else {
         boolean low = mc.field_1724.method_6032() <= this.threshold.get() * 2.0;
         if (low && !this.wasLow) {
            NotificationCenter.push("Wenig Leben", String.format("Noch %.1f Herzen", mc.field_1724.method_6032() / 2.0F), 4500L, -41620);
         }

         if (!low && mc.field_1724.method_6032() > (this.threshold.get() + 1.0) * 2.0) {
            this.wasLow = false;
         } else if (low) {
            this.wasLow = true;
         }
      }
   }

   @Override
   protected void draw(class_332 ctx) {
      boolean preview = mc.field_1755 instanceof HudEditorScreen;
      if (mc.field_1724 != null && (!(mc.field_1724.method_6032() > this.threshold.get() * 2.0) || preview)) {
         String text = preview && mc.field_1724.method_6032() > this.threshold.get() * 2.0
            ? "LOW HEALTH  3.5 ♥ (Preview)"
            : String.format("LOW HEALTH  %.1f ♥", mc.field_1724.method_6032() / 2.0F);
         this.w = mc.field_1772.method_1727(text) + 18;
         this.h = 18;
         float pulse = (float)(0.65 + 0.35 * Math.sin(System.currentTimeMillis() / 150.0));
         int alpha = Math.max(120, Math.min(255, (int)(220.0F * pulse)));
         ctx.method_25294(0, 0, this.w, this.h, alpha << 24 | 6098968);
         ctx.method_25294(0, 0, 3, this.h, -41620);
         ctx.method_51433(mc.field_1772, text, 8, 5, -6424, true);
      } else {
         this.w = 120;
         this.h = 18;
      }
   }
}
