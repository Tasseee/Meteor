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
import net.minecraft.class_243;
import net.minecraft.class_3959;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;

public final class WebHit extends Module {
   private final BoolSetting onlyIfEntity = add(new BoolSetting("Nur mit Ziel hinter Web", true));
   private final NumberSetting range = add(new NumberSetting("Range", 3.0, 1.0, 6.0, 0.1));
   private final NumberSetting maxWebs = add(new NumberSetting("Max. Webs", 4.0, 1.0, 8.0, 1.0));

   public WebHit() {
      super("WebHit", "Ignoriert Spinnennetze beim Raycast, wenn dahinter ein Ziel steht.", Category.COMBAT);
   }

   public class_239 adjust(class_1297 camera, class_239 original, float tickDelta) {
      if (!isEnabled() || mc.field_1687 == null || camera == null) return original;

      class_243 eye = camera.method_5836(tickDelta);
      class_243 direction = camera.method_5720(tickDelta);
      class_243 end = eye.method_1019(direction.method_1021(range.get()));
      class_239 hit = original;
      boolean skippedWeb = false;

      for (int i = 0; i < maxWebs.getInt(); i++) {
         if (!(hit instanceof class_239.class_240 blockHit)) break;
         if (blockHit.method_17777() != class_240.field_1333) break;

         class_2338 pos = blockHit.method_17780();
         if (!mc.field_1687.method_8320(pos).isOf(class_2246.field_10534)) break;

         skippedWeb = true;

         class_243 start = blockHit.method_17784().method_1019(direction.method_1021(0.05));
         hit = mc.field_1687.method_17742(new class_3959(
            start,
            end,
            class_3960.field_17558,
            class_242.field_1348,
            camera
         ));
      }

      if (!skippedWeb || hit == original) return original;
      if (!onlyIfEntity.get()) return hit;
      return hit.method_17782() == class_240.field_1332 ? hit : original;
   }
}
