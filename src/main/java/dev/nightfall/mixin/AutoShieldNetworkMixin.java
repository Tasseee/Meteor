package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.AutoShield;
import net.minecraft.class_1657;
import net.minecraft.class_2616;
import net.minecraft.class_310;
import net.minecraft.class_634;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_634.class})
abstract class AutoShieldNetworkMixin {
   @Inject(
      method = {"method_11160"},
      at = {@At("HEAD")}
   )
   private void nightfall$onEntityAnimation(class_2616 packet, CallbackInfo ci) {
      class_310 client = class_310.method_1551();
      if (client.field_1724 != null && client.field_1687 != null) {
         int animation = packet.method_11267();
         if (animation == 0 || animation == 3) {
            if (client.field_1687.method_8469(packet.method_11269()) instanceof class_1657 attacker) {
               try {
                  ModuleManager.get(AutoShield.class).onAttackAnimation(attacker);
               } catch (Throwable var8) {
               }
            }
         }
      }
   }
}
