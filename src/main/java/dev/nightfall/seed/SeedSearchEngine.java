package dev.nightfall.seed;

import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.finder.Finder.Type;

public final class SeedSearchEngine {
   private String appliedProfile = "";

   public void setActive(boolean active) {
      Config.get().active = active;
      Config.save();
   }

   public void applyProfile(String requestedProfile) {
      SeedSearchEngine.Profile profile = SeedSearchEngine.Profile.from(requestedProfile);
      if (!profile.label().equals(this.appliedProfile)) {
         Config config = Config.get();
         boolean balanced = profile != SeedSearchEngine.Profile.SPARSAM;
         boolean intensive = profile == SeedSearchEngine.Profile.INTENSIV;
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
         config.endPillars.set(true);
         config.endGateway.set(balanced);
         config.desertWell.set(balanced);
         config.biome.set(intensive);
         config.dungeon.set(intensive);
         config.emeraldOre.set(intensive);
         config.warpedFungus.set(intensive);
         Type.END_PILLARS.enabled.set(true);
         Type.END_GATEWAY.enabled.set(balanced);
         Type.DESERT_WELL.enabled.set(balanced);
         Type.BIOME.enabled.set(intensive);
         Type.DUNGEON.enabled.set(intensive);
         Type.EMERALD_ORE.enabled.set(intensive);
         Type.WARPED_FUNGUS.enabled.set(intensive);
         this.appliedProfile = profile.label();
         Config.save();
      }
   }

   public static enum Profile {
      SPARSAM("Sparsam"),
      AUSGEWOGEN("Ausgewogen"),
      INTENSIV("Intensiv");

      private final String label;

      private Profile(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }

      public static SeedSearchEngine.Profile from(String value) {
         for (SeedSearchEngine.Profile profile : values()) {
            if (profile.label.equals(value)) {
               return profile;
            }
         }

         return AUSGEWOGEN;
      }
   }
}
