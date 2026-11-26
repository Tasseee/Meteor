package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1297;
import net.minecraft.class_238;
import net.minecraft.class_746;

public class Hitbox extends Module {
   private final NumberSetting expansion = this.add(new NumberSetting("Hitbox-Erweiterung", 0.0, 0.0, 2.0, 0.05));
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 3.0, 1.0, 6.0, 0.1));
   private final BoolSetting players = this.add(new BoolSetting("Spieler", true));
   private final BoolSetting mobs = this.add(new BoolSetting("Mobs", true));

   public Hitbox() {
      super("Hitbox", "Erweitert die clientseitige Ziel-/Raycast-Hitbox von Entities.", Category.COMBAT);
   }

   public double getExpansion() {
      return this.isEnabled() ? this.expansion.get() : 0.0;
   }

   public double getRange() {
      return this.range.get();
   }

   public boolean allows(class_1297 entity) {
      return entity instanceof class_746 ? this.players.get() : this.mobs.get();
   }

   public class_238 expandedBox(class_1297 entity) {
      return entity.method_5829().method_1014(this.getExpansion());
   }
}
