package dev.nightfall.modules;

import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_2378;
import net.minecraft.class_7923;
import java.util.Locale;
import java.util.Set;

public class EntityEsp extends EntityScanEsp {
   private static final Set<String> ALLOWED_ENTITY_TYPES = Set.of(
      "minecraft:armor_stand",
      "minecraft:item_frame",
      "minecraft:glow_item_frame",
      "minecraft:ender_pearl",
      "minecraft:armadillo"
   );

   public EntityEsp() {
      super("EntityESP", "Zeigt Armor Stands, Item Frames, Enderperlen, Armadillos und Boote.", 128, 512, 16096779);
   }

   @Override
   protected boolean accept(class_1297 entity) {
      if (entity == null) return false;

      try {
         class_2378<class_1299<?>> registry = (class_2378<class_1299<?>>) class_7923.field_41177;
         var identifier = registry.method_10221(entity.method_5864());
         if (identifier == null) return false;

         String id = identifier.toString().toLowerCase(Locale.ROOT);
         return ALLOWED_ENTITY_TYPES.contains(id)
            || id.equals("minecraft:boat")
            || id.endsWith("_boat")
            || id.endsWith("_raft");
      } catch (RuntimeException ignored) {
         return false;
      }
   }
}
