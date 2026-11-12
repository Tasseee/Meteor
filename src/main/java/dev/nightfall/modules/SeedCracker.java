package dev.nightfall.modules;

import dev.nightfall.Nightfall;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.seed.SeedIntegrity;
import dev.nightfall.seed.SeedSearchEngine;
import dev.nightfall.util.NotificationCenter;
import java.util.Locale;
import kaptainwutax.seedcrackerX.cracker.storage.DataStorage;
import net.minecraft.class_2561;
import net.minecraft.class_332;

public final class SeedCracker extends Module {
   private static long foundSeed;
   private static long foundAt;
   private static boolean hasResult;
   private static SeedIntegrity.Report lastIntegrity;
   private final ModeSetting searchProfile = this.add(new ModeSetting("Suchintensitaet", "Ausgewogen", "Sparsam", "Ausgewogen", "Intensiv"));
   private final SeedSearchEngine engine = new SeedSearchEngine();
   private final SeedIntegrity integrity = new SeedIntegrity();

   public SeedCracker() {
      super("SeedCracker", "Sammelt Seed-Daten und steuert die Zuverlaessigkeit und Intensitaet der Seed-Suche.", Category.WORLD);
   }

   @Override
   protected void onEnable() {
      SeedCrackerProgress.reset();
      SeedIntegrity.resetIncomingStats();
      this.setCrackerActive(true);
      hasResult = false;
      this.applySearchProfile();
   }

   @Override
   public void onTick() {
      this.applySearchProfile();
   }

   private void applySearchProfile() {
      try {
         this.engine.applyProfile(this.searchProfile.get());
      } catch (Throwable var2) {
         Nightfall.LOGGER.error("Nightfall-Seed-Suchprofil konnte nicht angewendet werden", var2);
      }
   }

   @Override
   protected void onDisable() {
      this.setCrackerActive(false);
      SeedCrackerProgress.reset();
      hasResult = false;
   }

   public static void syncState() {
      try {
         ModuleManager.get(SeedCracker.class).engine.setActive(ModuleManager.get(SeedCracker.class).isEnabled());
      } catch (Throwable var1) {
      }
   }

   private void setCrackerActive(boolean active) {
      try {
         this.engine.setActive(active);
      } catch (Throwable var3) {
         Nightfall.LOGGER.error("Nightfall-Seed-Suche konnte nicht umgeschaltet werden", var3);
      }
   }

   public static void reportSeed(long seed) {
      SeedCracker module;
      try {
         module = ModuleManager.get(SeedCracker.class);
      } catch (Throwable var4) {
         return;
      }

      if (module.isEnabled()) {
         SeedIntegrity.Report integrity = module.integrity.verify(seed);
         lastIntegrity = integrity;
         if (!integrity.accepted()) {
            NotificationCenter.push("SEED VERWORFEN", integrity.reason(), 12000L, -34953);
         } else {
            foundSeed = seed;
            foundAt = System.currentTimeMillis();
            hasResult = true;
            SeedCrackerProgress.complete();
            NotificationCenter.push(
               "SEED " + integrity.level().label(),
               Long.toString(seed) + " · " + integrity.summary(),
               12000L,
               integrity.level() == SeedIntegrity.Level.HIGH ? -10099035 : -15253
            );
         }
      }
   }

   @Override
   public void onWorldLeave() {
      hasResult = false;
      SeedCrackerProgress.reset();
      SeedIntegrity.resetIncomingStats();
   }

   private SeedCracker.ProgressView readProgress() {
      try {
         kaptainwutax.seedcrackerX.SeedCracker instance = kaptainwutax.seedcrackerX.SeedCracker.get();
         if (instance == null) {
            return new SeedCracker.ProgressView(0.0F, "Warte auf SeedCrackerX ...", false);
         } else {
            DataStorage data = instance.getDataStorage();
            double wanted = Math.max(1.0, data.getWantedBits());
            double bits = Math.max(0.0, Math.min(wanted, data.getBaseBits()));
            boolean running = data.getTimeMachine().isRunning;
            float search = SeedCrackerProgress.getSearchProgress();
            if (running && search > 0.0F) {
               return new SeedCracker.ProgressView(Math.min(1.0F, search / 100.0F), "Seed-Suche · " + Math.round(search) + "%", false);
            } else {
               return running
                  ? new SeedCracker.ProgressView(0.0F, "Seed-Suche laeuft ...", true)
                  : new SeedCracker.ProgressView(
                     (float)(bits / wanted),
                     "Daten · " + String.format(Locale.ROOT, "%.1f / %.0f Bits", bits, wanted) + " · " + SeedIntegrity.rejectedIncomingCount() + " verworfen",
                     false
                  );
            }
         }
      } catch (Throwable var9) {
         return new SeedCracker.ProgressView(0.0F, "Seed-Daten werden gelesen ...", true);
      }
   }

   @Override
   public void renderHud(class_332 ctx) {
      int sw = mc.method_22683().method_4486();
      SeedCracker.ProgressView view = this.readProgress();
      int x = 8;
      int y = 8;
      int w = Math.min(250, sw - 16);
      ctx.method_25294(x, y, x + w, y + 39, -921693669);
      ctx.method_25294(x, y, x + 3, y + 39, -10099035);
      ctx.method_51433(mc.field_1772, "SEEDCRACKER", x + 10, y + 6, -10099035, true);
      String label = view.label();

      while (mc.field_1772.method_1727(label) > w - 20 && label.length() > 8) {
         label = label.substring(0, label.length() - 2) + "…";
      }

      ctx.method_51433(mc.field_1772, label, x + 10, y + 18, -986377, false);
      int bx = x + 10;
      int by = y + 31;
      int bw = w - 20;
      ctx.method_25294(bx, by, bx + bw, by + 4, -13486783);
      if (view.indeterminate()) {
         int segment = Math.max(18, bw / 5);
         int offset = (int)(System.currentTimeMillis() / 12L % (bw + segment)) - segment;
         int left = Math.max(bx, bx + offset);
         int right = Math.min(bx + bw, bx + offset + segment);
         if (right > left) {
            ctx.method_25294(left, by, right, by + 4, -10099035);
         }
      } else {
         int fill = Math.round(bw * view.progress());
         if (fill > 0) {
            ctx.method_25294(bx, by, bx + fill, by + 4, -10099035);
         }
      }

      if (hasResult) {
         long elapsed = System.currentTimeMillis() - foundAt;
         if (elapsed >= 12000L) {
            hasResult = false;
         } else {
            int pw = Math.min(420, sw - 24);
            int ph = 72;
            int px = (sw - pw) / 2;
            int py = Math.max(54, mc.method_22683().method_4502() / 2 - ph / 2);
            float fade = elapsed > 10500L ? (float)(12000L - elapsed) / 1500.0F : 1.0F;
            int alpha = Math.max(0, Math.min(255, Math.round(235.0F * fade)));
            int panel = alpha << 24 | 1053469;
            int accent = alpha << 24 | 6678181;
            int white = alpha << 24 | 16777215;
            ctx.method_25294(px, py, px + pw, py + ph, panel);
            ctx.method_25294(px, py, px + pw, py + 3, accent);
            String integrityLabel = lastIntegrity == null ? "UNGEPRUEFT" : lastIntegrity.level().label();
            ctx.method_27534(mc.field_1772, class_2561.method_43470("SEED GEFUNDEN · " + integrityLabel), sw / 2, py + 13, accent);
            ctx.method_27534(mc.field_1772, class_2561.method_43470(Long.toString(foundSeed)), sw / 2, py + 34, white);
         }
      }
   }

   private record ProgressView(float progress, String label, boolean indeterminate) {
   }
}
