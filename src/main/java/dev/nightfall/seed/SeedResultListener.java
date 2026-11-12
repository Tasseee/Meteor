package dev.nightfall.seed;

import dev.nightfall.modules.SeedCracker;
import java.util.concurrent.atomic.AtomicLong;
import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import net.minecraft.class_310;

public final class SeedResultListener implements SeedCrackerAPI {
   private static final AtomicLong LAST_REPORTED = new AtomicLong(Long.MIN_VALUE);

   public void pushWorldSeed(long seed) {
      if (LAST_REPORTED.getAndSet(seed) != seed) {
         class_310 client = class_310.method_1551();
         client.execute(() -> SeedCracker.reportSeed(seed));
      }
   }

   public static void resetForNewWorld() {
      LAST_REPORTED.set(Long.MIN_VALUE);
   }
}
