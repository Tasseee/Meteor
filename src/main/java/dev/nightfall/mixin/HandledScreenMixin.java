package dev.nightfall.mixin;

import dev.nightfall.modules.InvSeeValue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Displays item-price totals only for the container opened following /invsee. */
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void nightfall$renderInvSeeValue(DrawContext context, int mouseX, int mouseY,
                                             float deltaTicks, CallbackInfo ci) {
        InvSeeValue.renderWindow((Screen) (Object) this, context);
    }
}
