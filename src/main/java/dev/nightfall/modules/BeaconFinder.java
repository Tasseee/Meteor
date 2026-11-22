package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.class_2586;
import net.minecraft.class_2580;

/**
 * Finds beacon block entities in currently loaded chunks and marks them
 * through Nightfall's HUD projection pipeline.
 *
 * The scan intentionally uses loaded block entities only. It does not
 * request or force-load chunks outside the client's current view distance.
 */
public final class BeaconFinder extends BlockEntityEsp {
   private final BoolSetting showCoordinates = this.add(new BoolSetting("Koordinaten anzeigen", true));

   public BeaconFinder() {
      super("BeaconFinder", "Markiert Beacons in geladenen Chunks.", 256, 10000, 0x5AE6FF);
   }

   @Override
   protected boolean accept(class_2586 blockEntity) {
      return blockEntity instanceof class_2580;
   }

   @Override
   protected String nameOf(class_2586 blockEntity) {
      if (!this.showCoordinates.get()) return "Beacon";

      var pos = blockEntity.method_11016();
      return "Beacon " + pos.method_10263() + " " + pos.method_10264() + " " + pos.method_10260();
   }
}
