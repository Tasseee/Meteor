package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.FakeLag;
import java.util.Locale;
import net.minecraft.class_310;
import net.minecraft.class_332;

/**
 * Compact purple Nightfall status badge for the existing outgoing-movement FakeLag.
 * This display does not inspect, hide, or alter other players' packets or positions.
 */
public final class FakeLagHud {
   private static final int PANEL = 0xE510111B;
   private static final int PANEL_INNER = 0xC91B1C2A;
   private static final int ACCENT = 0xFF9B7BFF;
   private static final int TITLE = 0xFFF4F0FF;
   private static final int MUTED = 0xFFB8B3C7;
   private static final int GREEN = 0xFF55E6A5;
   private static final int RED = 0xFFFF7185;

   private FakeLagHud() {
   }

   public static void render(class_332 ctx) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null || mc.field_1690 == null || mc.field_1690.field_1842) {
         return;
      }

      FakeLag module;
      try {
         module = ModuleManager.get(FakeLag.class);
      } catch (IllegalStateException ignored) {
         return;
      }

      if (!module.shouldShowHud()) {
         return;
      }

      int queued = module.isEnabled() ? module.queuedMovementPacketCount() : 0;
      int maximum = module.configuredMaxPackets();
      int delay = module.configuredDelayMs();
      boolean enabled = module.isEnabled();
      boolean holding = enabled && queued > 0;

      String title = "NIGHTFALL  /  FAKELAG";
      String status = !enabled ? "OFF" : (holding ? "HOLD" : "READY");
      String detail = String.format(Locale.ROOT, "DELAY  %d ms     QUEUE  %d/%d", delay, queued, maximum);

      int textWidth = Math.max(mc.field_1772.method_1727(title), mc.field_1772.method_1727(detail));
      int width = Math.max(178, textWidth + 34);
      int x = 8;
      int y = 8;
      int height = 39;

      roundedPanel(ctx, x, y, width, height, PANEL);
      ctx.method_25294(x + 1, y + 4, x + 3, y + height - 4, ACCENT);
      ctx.method_25294(x + 8, y + 7, x + width - 8, y + 8, PANEL_INNER);

      int statusColor = enabled ? GREEN : RED;
      int pulse = holding
         ? 185 + (int)(70 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 300.0)))
         : 255;
      ctx.method_25294(x + 10, y + 13, x + 16, y + 19, (pulse << 24) | (statusColor & 0x00FFFFFF));
      ctx.method_51433(mc.field_1772, title, x + 21, y + 11, TITLE, true);

      int statusWidth = mc.field_1772.method_1727(status);
      ctx.method_51433(mc.field_1772, status, x + width - statusWidth - 10, y + 11, statusColor, true);
      ctx.method_51433(mc.field_1772, detail, x + 10, y + 25, MUTED, false);
   }

   private static void roundedPanel(class_332 ctx, int x, int y, int width, int height, int color) {
      ctx.method_25294(x + 3, y, x + width - 3, y + height, color);
      ctx.method_25294(x + 1, y + 2, x + width - 1, y + height - 2, color);
      ctx.method_25294(x, y + 4, x + width, y + height - 4, color);
   }
}
