package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.ItemGlow;
import java.util.List;
import net.minecraft.class_4597;
import net.minecraft.class_777;
import net.minecraft.class_811;
import net.minecraft.class_4587;

/**
 * Optional 3D item contour renderer.
 *
 * The previous implementation used Minecraft's batched line vertex format,
 * which can crash with render backends such as ImmediatelyFast because the
 * required LineWidth element is not accepted consistently. The normal
 * ItemGlowRenderer remains responsible for the safe glow effects.
 */
public final class ItemGlowMeshRenderer {
   private ItemGlowMeshRenderer() {}

   public static void render(
      class_811 displayContext,
      class_4587 matrices,
      class_4597 consumers,
      List<?> rawQuads
   ) {
      try {
         ItemGlow module = ModuleManager.get(ItemGlow.class);
         if (module == null || !module.isEnabled() || !module.modelOutline()) {
            return;
         }

         // Intentionally disabled: the old mesh/line path is incompatible with
         // some batched render backends and caused world-entry crashes.
         return;
      } catch (Throwable ignored) {
         // Cosmetic rendering must never interrupt Minecraft's renderer.
      }
   }
}
