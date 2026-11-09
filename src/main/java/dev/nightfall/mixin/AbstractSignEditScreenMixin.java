package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.AutoSign;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSignEditScreen.class)
abstract class AbstractSignEditScreenMixin {
    @Shadow @Final private String[] messages;
    @Unique private boolean nightfall$prefilled;

    @Inject(method = "init", at = @At("TAIL"))
    private void nightfall$prefillSign(CallbackInfo ci) {
        if (nightfall$prefilled) return;
        nightfall$prefilled = true;
        AutoSign autoSign;
        try {
            autoSign = ModuleManager.get(AutoSign.class);
        } catch (IllegalStateException ignored) {
            return;
        }
        if (!autoSign.isEnabled()) return;

        String[] lines = autoSign.configuredLines();
        for (int i = 0; i < Math.min(messages.length, lines.length); i++) {
            if (autoSign.overwrite.get() || messages[i] == null || messages[i].isBlank()) {
                if (lines[i] != null && !lines[i].isBlank()) messages[i] = lines[i];
            }
        }
    }
}
