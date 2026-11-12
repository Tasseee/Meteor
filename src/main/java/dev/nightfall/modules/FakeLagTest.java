package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Theme;
import net.minecraft.class_332;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client-side latency display for local testing.
 * This module never queues, changes, or cancels network packets.
 */
public final class FakeLagTest extends Module {
   private final NumberSetting simulatedPing = this.add(new NumberSetting("Test-Ping", 180.0, 0.0, 1000.0, 10.0));
   private final NumberSetting jitter = this.add(new NumberSetting("Jitter", 30.0, 0.0, 250.0, 5.0));
   private int displayedPing;

   public FakeLagTest() {
      super("FakeLag Test", "Lokale Ping-Anzeige zur UI-/Testsimulation; veraendert kein Netzwerk.", Category.PLAYER);
   }

   @Override
   public void onTick() {
      int spread = this.jitter.getInt();
      int offset = spread == 0 ? 0 : ThreadLocalRandom.current().nextInt(-spread, spread + 1);
      this.displayedPing = Math.max(0, this.simulatedPing.getInt() + offset);
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 == null) {
         return;
      }

      String line = "FakeLag TEST  " + this.displayedPing + " ms";
      int width = mc.field_1772.method_1727(line) + 12;
      ctx.method_25294(4, 4, width, 16, Theme.HUD_PANEL);
      ctx.method_25294(4, 4, 2, 16, Theme.ACCENT);
      ctx.method_51433(mc.field_1772, line, 9, 8, Theme.TEXT, true);
   }
}
