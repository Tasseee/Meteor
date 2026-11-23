package dev.nightfall.mixin;

import dev.nightfall.modules.Privat;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Replaces the local account name in incoming chat lines with the configured identity. */
@Mixin(net.minecraft.class_338.class)
public abstract class FakeNameChatMixin {
   @ModifyVariable(
      method = "method_1812(Lnet/minecraft/class_2561;)V",
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private class_2561 nightfall$replaceSimpleChatName(class_2561 message) {
      return replace(message);
   }

   @ModifyVariable(
      method = "method_44811(Lnet/minecraft/class_2561;Lnet/minecraft/class_7469;Lnet/minecraft/class_7591;)V",
      at = @At("HEAD"),
      argsOnly = true,
      ordinal = 0
   )
   private class_2561 nightfall$replaceSignedChatName(class_2561 message) {
      return replace(message);
   }

   private static class_2561 replace(class_2561 message) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null || message == null || !Privat.hasVisualOverride()) return message;
      String accountName = mc.field_1724.method_5477().getString();
      return Privat.replaceName(message, accountName);
   }
}
