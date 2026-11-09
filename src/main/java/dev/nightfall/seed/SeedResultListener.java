package dev.nightfall.seed;

import dev.nightfall.modules.SeedCracker;
import kaptainwutax.seedcrackerX.api.SeedCrackerAPI;
import net.minecraft.client.MinecraftClient;

import java.util.concurrent.atomic.AtomicLong;

/** Receives completed seed results from the bundled SeedCrackerX mod. */
public final class SeedResultListener implements SeedCrackerAPI {
    private static final AtomicLong LAST_REPORTED = new AtomicLong(Long.MIN_VALUE);

    @Override
    public void pushWorldSeed(long seed) {
        if (LAST_REPORTED.getAndSet(seed) == seed) return;
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> SeedCracker.reportSeed(seed));
    }

    /** Allows the same seed to be reported again after joining a new world. */
    public static void resetForNewWorld() {
        LAST_REPORTED.set(Long.MIN_VALUE);
    }
}
