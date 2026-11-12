package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.class_2561;
import net.minecraft.class_418;

public final class AutoRespawn extends Module {
   private final BoolSetting chatNotice = this.add(new BoolSetting("Statusmeldung", false));
   private int delayTicks = 0;

   public AutoRespawn() {
      super("AutoRespawn", "Respawnt nach dem Tod automatisch.", Category.MISC);
   }

   @Override
   protected void onEnable() {
      this.delayTicks = 0;
   }

   @Override
   protected void onDisable() {
      this.delayTicks = 0;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1755 == null) {
         this.delayTicks = 0;
      } else if (mc.field_1755 instanceof class_418) {
         if (++this.delayTicks >= 20) {
            mc.field_1724.method_7331();
            mc.method_1507(null);
            this.delayTicks = 0;
            if (this.chatNotice.get()) {
               mc.field_1724.method_7353(class_2561.method_43470("[Nightfall] Respawn angefordert"), false);
            }
         }
      }
   }
}
