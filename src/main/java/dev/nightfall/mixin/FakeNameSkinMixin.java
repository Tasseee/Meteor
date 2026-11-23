package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.FakeName;
import net.minecraft.class_1068;
import net.minecraft.class_310;
import net.minecraft.class_742;
import net.minecraft.class_8685;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(class_742.class)
public abstract class FakeNameSkinMixin {
   @Inject(method = "method_52814", at = @At("HEAD"), cancellable = true, require = 0)
   private void nightfall$fakeNameSteveSkin(CallbackInfoReturnable<class_8685> cir) {
      try {
         FakeName module = ModuleManager.get(FakeName.class);
         if (module != null
            && module.isEnabled()
            && class_310.method_1551().field_1724 == (Object) this) {
            cir.setReturnValue(class_1068.method_62620());
         }
      } catch (Throwable ignored) {
      }
   }
}
