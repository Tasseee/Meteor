package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import java.awt.Color;
import net.minecraft.class_1799;

public final class ItemGlint extends Module {
   private final ModeSetting scope = this.add(new ModeSetting("Umfang", "Nur Hand", "Nur Hand", "Alle Items"));
   private final ModeSetting colorMode = this.add(new ModeSetting("Farbmodus", "Fest", "Fest", "Regenbogen"));
   private final ColorSetting fixedColor = this.add(new ColorSetting("Glitzerfarbe", 5614335));

   public ItemGlint() {
      super("Item Glint", "Aktiviert den Glitzereffekt mit fester Farbe oder Regenbogen für gehaltene oder alle Items.", Category.VISUALS);
   }

   public boolean forceGlint(class_1799 stack) {
      if (!this.isEnabled() || stack == null) {
         return false;
      } else {
         return this.scope.is("Alle Items") ? true : mc.field_1724 != null && (stack == mc.field_1724.method_6047() || stack == mc.field_1724.method_6079());
      }
   }

   public boolean shouldTint() {
      return this.isEnabled();
   }

   public int currentGlintColor() {
      if (!this.colorMode.is("Regenbogen")) {
         return this.fixedColor.rgb();
      } else {
         float hue = (float)(System.currentTimeMillis() % 3000L) / 3000.0F;
         return Color.HSBtoRGB(hue, 0.9F, 1.0F) & 16777215;
      }
   }
}
