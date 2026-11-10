package dev.nightfall.seed;

import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.finder.Finder;

/**
 * Nightfall-owned orchestration layer for seed evidence collection.
 *
 * SeedCrackerX remains the proven low-level finder/solver backend for now;
 * Nightfall controls which evidence sources are active and when collection runs.
 */
public final class SeedSearchEngine {
    public enum Profile {
        SPARSAM("Sparsam"),
        AUSGEWOGEN("Ausgewogen"),
        INTENSIV("Intensiv");

        private final String label;
        Profile(String label) { this.label = label; }
        public String label() { return label; }

        public static Profile from(String value) {
            for (Profile profile : values()) if (profile.label.equals(value)) return profile;
            return AUSGEWOGEN;
        }
    }

    private String appliedProfile = "";

    public void setActive(boolean active) {
        Config.get().active = active;
        Config.save();
    }

    public void applyProfile(String requestedProfile) {
        Profile profile = Profile.from(requestedProfile);
        if (profile.label().equals(appliedProfile)) return;

        Config config = Config.get();
        boolean balanced = profile != Profile.SPARSAM;
        boolean intensive = profile == Profile.INTENSIV;

        // Structure evidence is the reliable baseline in every profile.
        config.buriedTreasure.set(true);
        config.desertTemple.set(true);
        config.endCity.set(true);
        config.jungleTemple.set(true);
        config.monument.set(true);
        config.swampHut.set(true);
        config.shipwreck.set(true);
        config.outpost.set(true);
        config.igloo.set(true);
        config.trialChambers.set(true);

        // Additional End/decorator evidence is enabled from Ausgewogen upward.
        config.endPillars.set(true);
        config.endGateway.set(balanced);
        config.desertWell.set(balanced);

        // Intensive enables every remaining finder exposed by SeedCrackerX 2.15.6.
        config.biome.set(intensive);
        config.dungeon.set(intensive);
        config.emeraldOre.set(intensive);
        config.warpedFungus.set(intensive);

        // Keep the finder registry synchronized with the persisted config.
        Finder.Type.END_PILLARS.enabled.set(true);
        Finder.Type.END_GATEWAY.enabled.set(balanced);
        Finder.Type.DESERT_WELL.enabled.set(balanced);
        Finder.Type.BIOME.enabled.set(intensive);
        Finder.Type.DUNGEON.enabled.set(intensive);
        Finder.Type.EMERALD_ORE.enabled.set(intensive);
        Finder.Type.WARPED_FUNGUS.enabled.set(intensive);

        appliedProfile = profile.label();
        Config.save();
    }
}
