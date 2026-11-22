package dev.nightfall.modules;

import net.minecraft.class_1297;
import net.minecraft.class_1684;
import net.minecraft.class_1657;

/**
 * Displays the owner of loaded Ender Pearls directly above each pearl.
 * The owner is only available when the server sends the projectile owner data.
 */
public final class PearlStasisFinder extends EntityScanEsp {
   public PearlStasisFinder() {
      super("PearlStasisFinder", "Zeigt den Besitzer geladener Enderperlen direkt ueber der Pearl.", 128, 512, 0xB58CFF);
   }

   @Override
   protected boolean accept(class_1297 entity) {
      return entity instanceof class_1684;
   }

   @Override
   protected String nameOf(class_1297 entity) {
      if (!(entity instanceof class_1684 pearl)) {
         return "Pearl";
      }

      class_1297 owner = pearl.method_24921();
      if (owner instanceof class_1657 player) {
         String name = player.method_5477().getString();
         return name.isBlank() ? "Besitzer unbekannt" : name;
      }

      return "Besitzer unbekannt";
   }
}
