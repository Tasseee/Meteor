package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.ItemGlint;
import dev.nightfall.render.GlintColorVertexConsumer;
import net.minecraft.class_12249;
import net.minecraft.class_1921;
import net.minecraft.class_4588;
import net.minecraft.class_4597;
import net.minecraft.class_918;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({class_918.class})
public abstract class ItemRendererGlintColorMixin {
   @Redirect(method = {"method_23181"}, at = @At(value = "INVOKE",
      target = "Lnet/minecraft/class_4597;method_73477(Lnet/minecraft/class_1921;)Lnet/minecraft/class_4588;"))
   private static class_4588 nightfall$tintRegularGlint(class_4597 provider, class_1921 layer) {
      return nightfall$wrapGlint(provider, layer);
   }

   @Redirect(method = {"method_71138"}, at = @At(value = "INVOKE",
      target = "Lnet/minecraft/class_4597;method_73477(Lnet/minecraft/class_1921;)Lnet/minecraft/class_4588;"))
   private static class_4588 nightfall$tintSpecialGlint(class_4597 provider, class_1921 layer) {
      return nightfall$wrapGlint(provider, layer);
   }

   private static class_4588 nightfall$wrapGlint(class_4597 provider, class_1921 layer) {
      ItemGlint module;
      try {
         module = ModuleManager.get(ItemGlint.class);
      } catch (Throwable ignored) {
         return provider.method_73477(layer);
      }

      if (module == null || !module.shouldTint()
         || (layer != class_12249.method_75995()
            && layer != class_12249.method_75993()
            && layer != class_12249.method_75997())) {
         return provider.method_73477(layer);
      }

      try {
         // Keep Minecraft's known-good glint layer/texture and only tint its vertex color.
         return new GlintColorVertexConsumer(provider.method_73477(layer), module::currentGlintColor);
      } catch (Throwable ignored) {
         return provider.method_73477(layer);
      }
   }
}
