package dev.nightfall.mixin;

import net.minecraft.class_243;
import net.minecraft.class_4184.class_6355;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the effective camera projection plane.
 * Zoomify adjusts this projection while zooming, unlike GameRenderer#getFov.
 */
@Mixin({class_6355.class})
public interface CameraProjectionAccessor {
   @Accessor("field_33624")
   class_243 nightfall$getHalfHeight();
}
