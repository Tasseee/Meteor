package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import java.util.Locale;
import net.minecraft.class_332;

/** Compact status panel for FakeLag's held packets and server/client position gap. */
public final class FakeLagHud extends HudModule {
   public FakeLagHud() {
      super("FakeLag HUD", "Zeigt Queue, Verzögerung und Abstand zur Server-Ghost-Position.");
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{4, Math.max(4, sh - 54)};
   }

   @Override
   protected void draw(class_332 ctx) {
      FakeLag fakeLag = ModuleManager.get(FakeLag.class);
      FakeLag.GhostSnapshot snapshot = FakeLag.snapshot();

      String status = fakeLag.isEnabled() ? "FakeLag  AKTIV" : "FakeLag  AUS";
      String queueLine = snapshot == null
         ? (fakeLag.isEnabled() ? "Warte auf Bewegungspakete" : "Keine Pakete zurueckgehalten")
         : String.format(Locale.ROOT, "%d Pakete  |  %d ms", snapshot.queuedPackets(), snapshot.heldMillis());

      String gapLine;
      double gap = 0.0;
      if (snapshot != null && mc.field_1724 != null) {
         double dx = mc.field_1724.method_23317() - snapshot.x();
         double dy = mc.field_1724.method_23318() - snapshot.y();
         double dz = mc.field_1724.method_23321() - snapshot.z();
         gap = Math.sqrt(dx * dx + dy * dy + dz * dz);
         gapLine = String.format(
            Locale.ROOT,
            "Server-Abstand: %.1f m  |  Ghost %s",
            gap,
            fakeLag.isGhostEnabled() ? "AN" : "AUS"
         );
      } else {
         gapLine = "Ghost " + (fakeLag.isGhostEnabled() ? "AN" : "AUS");
      }

      this.w = Math.max(
         126,
         Math.max(
            mc.field_1772.method_1727(status),
            Math.max(mc.field_1772.method_1727(queueLine), mc.field_1772.method_1727(gapLine))
         ) + 12
      );
      this.h = 44;

      ctx.method_25294(0, 0, this.w, this.h, Theme.HUD_PANEL);
      ctx.method_25294(0, 0, 2, this.h, Theme.ACCENT);
      ctx.method_51433(mc.field_1772, status, 6, 3, fakeLag.isEnabled() ? Theme.ACCENT : Theme.TEXT_DIM, true);
      ctx.method_51433(mc.field_1772, queueLine, 6, 14, Theme.TEXT, false);
      ctx.method_51433(mc.field_1772, gapLine, 6, 25, Theme.TEXT_DIM, false);

      int barLeft = 6;
      int barRight = this.w - 6;
      int barWidth = Math.max(1, barRight - barLeft);
      int fillWidth = snapshot == null || !fakeLag.isEnabled()
         ? 0
         : (int)(barWidth * Math.min(1.0, snapshot.heldMillis() / (double)Math.max(1, fakeLag.maxDelayMillis())));
      ctx.method_25294(barLeft, 38, barRight, 41, Theme.withAlpha(Theme.ACCENT, 70));
      if (fillWidth > 0) {
         ctx.method_25294(barLeft, 38, barLeft + fillWidth, 41, Theme.ACCENT);
      }
   }
}
