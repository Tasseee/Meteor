package dev.nightfall.mixin;

import net.minecraft.class_1703;
import net.minecraft.class_465;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(class_465.class)
public interface HandledScreenAccessor {
   @Accessor("field_2776")
   int nightfall$getX();

   @Accessor("field_2800")
   int nightfall$getY();

   @Accessor("field_2797")
   class_1703 nightfall$getHandler();
}