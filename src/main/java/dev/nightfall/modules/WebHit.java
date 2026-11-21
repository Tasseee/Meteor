package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_1297;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_239;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3965;
import net.minecraft.class_3966;
import net.minecraft.class_243;
import net.minecraft.class_3959;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public final class WebHit extends Module {
   private final BoolSetting onlyIfEntity = add(new BoolSetting("Nur mit Ziel hinter Web", true));
   private final NumberSetting maxWebs = add(new NumberSetting("Max. Webs", 4.0, 1.0, 8.0, 1.0));

   public WebHit() {
      super("WebHit", "Überspringt Cobwebs beim Fadenkreuz-Raycasts, wenn dahinter ein Ziel liegt.", Category.COMBAT);
   }

   public class_239 adjust(class_1297 cam, class_239 original, double range, float tickProgress) {
      if (!isEnabled() || mc.field_1687 == null || cam == null) return original;

      class_243 eye = cam.method_5836(tickProgress);
      class_243 dir = cam.method_5720();
      class_243 end = eye.method_1019(dir.method_1021(range));
      class_239 hit = original;
      boolean skipped = false;

      for (int i = 0; i < maxWebs.getInt(); i++) {
         if (!(hit instanceof class_3965 blockHit)) break;
         if (!mc.field_1687.method_8320(blockHit.method_17777()).method_27852(class_2246.field_10534)) break;

         class_243 start = blockHit.method_17784().method_1019(dir.method_1021(0.06));
         if (start.method_1021(-1).method_1022(eye) >= range * range) break;

         hit = mc.field_1687.method_17742(new class_3959(
            start,
            end,
            class_3960.field_17558,
            class_242.field_1348,
            cam
         ));
         skipped = true;
      }

      if (!skipped) return original;
      if (onlyIfEntity.get() && !(hit instanceof class_3966)) return original;
      return hit;
   }
}
