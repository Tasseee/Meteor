package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;

public final class SlowSwing extends Module {
   private final NumberSetting duration = this.add(new NumberSetting("Swing-Dauer", 1.6, 1.0, 3.0, 0.1));

   public SlowSwing() {
      super("SlowSwing", "Verlangsamt ausschliesslich die lokale Schlag-Animation.", Category.VISUALS);
   }

   public int adjustDuration(int vanilla) {
      return Math.max(vanilla, Math.round(vanilla * this.duration.get().floatValue()));
   }
}
