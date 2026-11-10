package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.ItemGlint;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Forces vanilla item glint across all render contexts while Item Glint is enabled. */
@Mixin(ItemStack.class)
public abstract class ItemStackGlintMixin {
    @Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
    private void nightfall$forceItemGlint(CallbackInfoReturnable<Boolean> cir) {
        try {
            if (ModuleManager.get(ItemGlint.class).forceGlint()) cir.setReturnValue(true);
        } catch (Throwable ignored) { }
    }
}
