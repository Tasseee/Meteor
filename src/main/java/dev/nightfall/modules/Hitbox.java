package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_238;

/**
 * Client-side entity bounding-box expansion.
 * The server remains authoritative for actual hit registration.
 */
public final class Hitbox extends Module {
   private static Hitbox instance;

   private final NumberSetting extra = this.add(new NumberSetting("Extra pro Seite", 0.30, 0.0, 2.0, 0.05));
   private final BoolSetting players = this.add(new BoolSetting("Spieler", true));
   private final BoolSetting mobs = this.add(new BoolSetting("Mobs", false));

   public Hitbox() {
      super("Hitbox", "Vergroessert Entity-Hitboxen clientseitig fuer Spieler und/oder Mobs.", Category.COMBAT);
      instance = this;
   }

   public static class_238 expand(class_1297 entity, class_238 original) {
      Hitbox module = instance;
      if (module == null || !module.isEnabled() || entity == null || original == null || entity == mc.field_1724) return original;

      boolean selected = entity instanceof class_1657
         ? module.players.get()
         : entity instanceof class_1309 && module.mobs.get();
      double amount = module.extra.get();
      if (!selected || amount <= 0.0) return original;

      return new class_238(
         original.field_1323 - amount,
         original.field_1322 - amount,
         original.field_1321 - amount,
         original.field_1320 + amount,
         original.field_1325 + amount,
         original.field_1324 + amount
      );
   }
}
