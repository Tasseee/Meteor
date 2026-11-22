package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.StringSetting;

public final class FakeName extends Module {
   public final ModeSetting rank = this.add(
      new ModeSetting("Rang", "PREMIUM", "MOD", "MEDIA", "FAMOUS", "PREMIUM")
   );
   public final StringSetting name = this.add(
      new StringSetting("Name", "", 16)
   );

   public FakeName() {
      super("Fake Name", "Ersetzt lokal den eigenen Namen durch einen HugoSMP-Rang und Namen.");
   }

   public String getNameValue() {
      if (this.name.get() == null || this.name.get().isBlank()) {
         return mc.field_1724 != null && mc.field_1724.method_7334() != null
            ? mc.field_1724.method_7334().name()
            : "";
      }
      return this.name.get().trim();
   }

   public int getRankColor() {
      return switch (this.rank.get()) {
         case "MOD" -> 0xFFFF5555;
         case "MEDIA" -> 0xFF55FFFF;
         case "FAMOUS" -> 0xFFAA55FF;
         default -> 0xFFFFAA00;
      };
   }
}
