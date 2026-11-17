package dev.nightfall.mixin;

import dev.nightfall.render.ItemGlowMeshRenderer;
import java.util.List;
import net.minecraft.class_10444;
import net.minecraft.class_1921;
import net.minecraft.class_4587;
import net.minecraft.class_4597;
import net.minecraft.class_777;
import net.minecraft.class_811;
import net.minecraft.class_918;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({class_918.class})
public abstract class ItemGlowMeshMixin {
   @Inject(method = {"method_62476"}, at = {@At("RETURN")})
   private static void nightfall$drawItemMeshOutline(
      class_811 displayContext,
      class_4587 matrices,
      class_4597 vertexConsumers,
      int light,
      int overlay,
      int[] tints,
      List<class_777> quads,
      class_1921 layer,
      class_10444.class_10445 glint,
      CallbackInfo ci
   ) {
      try {
         ItemGlowMeshRenderer.render(displayContext, matrices, vertexConsumers, quads);
      } catch (Throwable ignored) {
         // Cosmetic rendering must never interrupt Minecraft's item renderer.
      }
   }
}