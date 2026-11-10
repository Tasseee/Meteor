package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.AutoShield;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observes incoming swing animations from other entities. */
@Mixin(ClientPlayNetworkHandler.class)
abstract class AutoShieldNetworkMixin {
    @Inject(method = "onEntityAnimation", at = @At("HEAD"))
    private void nightfall$onEntityAnimation(EntityAnimationS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        Entity entity = client.world.getEntityById(packet.getEntityId());
        if (!(entity instanceof PlayerEntity attacker)) return;
        try {
            ModuleManager.get(AutoShield.class).onAttackAnimation(attacker);
        } catch (Throwable ignored) {
            // Keep packet processing safe if modules are not initialized yet.
        }
    }
}
