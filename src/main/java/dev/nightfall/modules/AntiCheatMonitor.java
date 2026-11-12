package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.class_332;

public final class AntiCheatMonitor extends Module {
   private static final Pattern ENABLED = Pattern.compile("(?i).*(?:anti.?cheat|anticheat).*(?:enabled|active|activated|started|on).*");
   private static final Pattern DISABLED = Pattern.compile("(?i).*(?:anti.?cheat|anticheat).*(?:disabled|inactive|deactivated|stopped|off).*");
   private final BoolSetting showOverlay = this.add(new BoolSetting("Status anzeigen", true));
   private final BoolSetting showLastSignal = this.add(new BoolSetting("Letzte Meldung anzeigen", true));
   private AntiCheatMonitor.Status status = AntiCheatMonitor.Status.UNKNOWN;
   private String lastSignal = "Keine eindeutige Servermeldung erkannt";
   private long lastSignalAt = 0L;

   public AntiCheatMonitor() {
      super("AntiCheatMonitor", "Erkennt ausdrueckliche Anti-Cheat-Statusmeldungen des Servers.", Category.MISC);
   }

   public void onServerMessage(String raw) {
      if (raw != null) {
         String normalized = raw.toLowerCase(Locale.ROOT);
         if (ENABLED.matcher(normalized).matches()) {
            this.status = AntiCheatMonitor.Status.ACTIVE;
         } else {
            if (!DISABLED.matcher(normalized).matches()) {
               return;
            }

            this.status = AntiCheatMonitor.Status.INACTIVE;
         }

         this.lastSignal = raw.length() > 100 ? raw.substring(0, 100) + "..." : raw;
         this.lastSignalAt = System.currentTimeMillis();
      }
   }

   @Override
   public void onWorldLeave() {
      this.status = AntiCheatMonitor.Status.UNKNOWN;
      this.lastSignal = "Keine eindeutige Servermeldung erkannt";
      this.lastSignalAt = 0L;
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (this.showOverlay.get()) {
         String label = switch (this.status) {
            case UNKNOWN -> "Anti-Cheat: UNKNOWN";
            case ACTIVE -> "Anti-Cheat: ACTIVE (Servermeldung)";
            case INACTIVE -> "Anti-Cheat: INACTIVE (Servermeldung)";
         };

         int color = switch (this.status) {
            case UNKNOWN -> -14739;
            case ACTIVE -> -11151733;
            case INACTIVE -> -39558;
         };
         int x = 8;
         int y = 72;
         ctx.method_51433(mc.field_1772, label, x, y, color, true);
         if (this.showLastSignal.get() && this.lastSignalAt > 0L) {
            long seconds = Math.max(0L, (System.currentTimeMillis() - this.lastSignalAt) / 1000L);
            ctx.method_51433(mc.field_1772, "Letztes Signal vor " + seconds + " s", x, y + 11, -1512718, true);
         }
      }
   }

   public static enum Status {
      UNKNOWN,
      ACTIVE,
      INACTIVE;
   }
}
