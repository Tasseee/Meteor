package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.AutoSign;
import net.minecraft.class_7743;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_7743.class})
abstract class AbstractSignEditScreenMixin {
   @Shadow
   @Final
   private String[] field_40425;
   @Unique
   private boolean nightfall$prefilled;

   @Inject(
      method = {"method_25426"},
      at = {@At("TAIL")}
   )
   private void nightfall$prefillSign(CallbackInfo ci) {
      if (!this.nightfall$prefilled) {
         this.nightfall$prefilled = true;

         AutoSign autoSign;
         try {
            autoSign = ModuleManager.get(AutoSign.class);
         } catch (IllegalStateException var5) {
            return;
         }

         if (autoSign.isEnabled()) {
            String[] lines = autoSign.configuredLines();

            for (int i = 0; i < Math.min(this.field_40425.length, lines.length); i++) {
               if ((autoSign.overwrite.get() || this.field_40425[i] == null || this.field_40425[i].isBlank()) && lines[i] != null && !lines[i].isBlank()) {
                  this.field_40425[i] = lines[i];
               }
            }
         }
      }
   }
}
