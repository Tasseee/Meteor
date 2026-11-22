package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.class_2586;
import net.minecraft.class_2580;

/**
 * Finds beacons which currently expose a calculated beam in loaded chunks.
 * The scan never requests or force-loads chunks outside the client's view.
 */
public final class BeaconFinder extends BlockEntityEsp {
   private final BoolSetting showCoordinates = this.add(new BoolSetting("Koordinaten anzeigen", true));
   private final BoolSetting activeBeamsOnly = this.add(new BoolSetting("Nur aktive Strahlen", true));
   private final BoolSetting showBeamHeight = this.add(new BoolSetting("Strahlhöhe anzeigen", true));

   public BeaconFinder() {
      super("BeaconFinder", "Erkennt aktive Beacon-Lichtstrahlen in geladenen Chunks.", 256, 10000, 0x5AE6FF);
   }

   @Override
   protected boolean accept(class_2586 blockEntity) {
      if (!(blockEntity instanceof class_2580 beacon)) {
         return false;
      }

      // BeaconBlockEntity#getBeamSegments is the same beam data the vanilla
      // beacon renderer consumes. Empty means there is currently no active beam.
      return !this.activeBeamsOnly.get() || !beacon.method_10937().isEmpty();
   }

   @Override
   protected String nameOf(class_2586 blockEntity) {
      if (!(blockEntity instanceof class_2580 beacon)) {
         return "Beacon";
      }

      var segments = beacon.method_10937();
      String name = "Beacon-Strahl";

      if (this.showBeamHeight.get() && !segments.isEmpty()) {
         int totalHeight = 0;
         for (var segment : segments) {
            totalHeight += segment.method_10943();
         }
         if (totalHeight > 0) {
            name += " " + totalHeight + "m";
         }
      } else if (segments.isEmpty()) {
         name = "Beacon (kein Strahl)";
      }

      if (!this.showCoordinates.get()) {
         return name;
      }

      var pos = beacon.method_11016();
      return name + " " + pos.method_10263() + " " + pos.method_10264() + " " + pos.method_10260();
   }
}
