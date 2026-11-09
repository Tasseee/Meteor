package dev.nightfall.mixin;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.SlowSwing;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntity.class)
abstract class SlowSwingMixin {
    @Inject(method="getHandSwingDuration",at=@At("RETURN"),cancellable=true)
    private void nightfall$slowLocalSwing(CallbackInfoReturnable<Integer> cir){
        MinecraftClient client=MinecraftClient.getInstance();if(client.player!=(Object)this)return;
        try{SlowSwing module=ModuleManager.get(SlowSwing.class);if(module.isEnabled())cir.setReturnValue(module.adjustDuration(cir.getReturnValue()));}
        catch(Throwable ignored){}
    }
}
