package dev.nightfall.mixin;

import com.seedfinding.mcfeature.Feature;
import dev.nightfall.seed.SeedIntegrity;
import kaptainwutax.seedcrackerX.cracker.DataAddedEvent;
import kaptainwutax.seedcrackerX.cracker.storage.DataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Screens structure observations before SeedCrackerX queues them for seed search.
 */
@Mixin(value = DataStorage.class, remap = false)
abstract class SeedCrackerDataIntegrityMixin {
    @Inject(method = "addBaseData", at = @At("HEAD"), cancellable = true, remap = false)
    private void nightfall$validateIncoming(Feature.Data<?> data, DataAddedEvent event,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (!SeedIntegrity.acceptIncoming(data, this)) {
            cir.setReturnValue(false);
        }
    }
}
