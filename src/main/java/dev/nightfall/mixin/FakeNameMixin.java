package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.FakeName;
import net.minecraft.class_1657;
import net.minecraft.class_2561;
import net.minecraft.class_5250;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_1657.class)
public abstract class FakeNameMixin {
   @Inject(method = "method_5477", at = @At("HEAD"), cancellable = true, require = 0)
   private void nightfall$fakeName(CallbackInfoReturnable<class_2561> cir) {
      try {
         class_1657 player = (class_1657)(Object)this;
         FakeName module = ModuleManager.get(FakeName.class);

         if (!module.isEnabled()
            || player != net.minecraft.class_310.method_1551().field_1724) {
            return;
         }

         String name = module.getNameValue();
         if (name.isBlank()) return;

         class_5250 rank = class_2561.method_43470("[" + module.rank.get() + "] ")
            .method_54663(module.getRankColor());

         class_5250 playerName = class_2561.method_43470(name).method_54663(0xFFFFFFFF);
         rank.method_10852(playerName);
         cir.setReturnValue(rank);
      } catch (Throwable ignored) {
      }
   }
}
