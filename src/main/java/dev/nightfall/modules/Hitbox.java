package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1657;

/**
 * Expands the entity targeting margin used by Minecraft's target selection.
 * This intentionally does not mutate the entity's visible/physical bounding box.
 * The remote server still performs its own authoritative hit validation.
 */
public final class Hitbox extends Module {
   private static Hitbox instance;

   private final NumberSetting extra = this.add(new NumberSetting("Extra pro Seite", 0.30, 0.0, 2.0, 0.05));
   private final BoolSetting players = this.add(new BoolSetting("Spieler", true));
   private final BoolSetting mobs = this.add(new BoolSetting("Mobs", false));

   public Hitbox() {
      super("Hitbox", "Erweitert die clientseitige Zielerfassung fuer Spieler und/oder Mobs.", Category.COMBAT);
      instance = this;
   }

   public static float adjustTargetingMargin(class_1297 entity, float vanillaMargin) {
      Hitbox module = instance;
      if (module == null || !module.isEnabled() || entity == null || entity == mc.field_1724) {
         return vanillaMargin;
      }

      boolean selected = entity instanceof class_1657
         ? module.players.get()
         : entity instanceof class_1309 && module.mobs.get();

      if (!selected) return vanillaMargin;

      return vanillaMargin + (float)module.extra.get();
   }
}
