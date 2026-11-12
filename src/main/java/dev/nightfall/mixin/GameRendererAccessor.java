package dev.nightfall.mixin;

import net.minecraft.class_4184;
import net.minecraft.class_757;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({class_757.class})
public interface GameRendererAccessor {
   @Invoker("method_3196")
   float nightfall$invokeGetFov(class_4184 var1, float var2, boolean var3);

   @Invoker("method_74751")
   Matrix4f nightfall$invokeGetProjectionMatrix(float var1);
}
