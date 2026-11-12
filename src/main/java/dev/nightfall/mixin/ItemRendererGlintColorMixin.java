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
   @Redirect(
      method = {"method_23181"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_4597;method_73477(Lnet/minecraft/class_1921;)Lnet/minecraft/class_4588;"
      )
   )
   private static class_4588 nightfall$tintRegularGlint(class_4597 provider, class_1921 layer) {
      return nightfall$wrapGlint(provider, layer);
   }

   @Redirect(
      method = {"method_71138"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_4597;method_73477(Lnet/minecraft/class_1921;)Lnet/minecraft/class_4588;"
      )
   )
   private static class_4588 nightfall$tintSpecialGlint(class_4597 provider, class_1921 layer) {
      return nightfall$wrapGlint(provider, layer);
   }

   private static class_4588 nightfall$wrapGlint(class_4597 provider, class_1921 layer) {
      class_4588 consumer = provider.method_73477(layer);
      if (layer != class_12249.method_75995() && layer != class_12249.method_75993() && layer != class_12249.method_75997()) {
         return consumer;
      } else {
         try {
            ItemGlint module = ModuleManager.get(ItemGlint.class);
            return (class_4588)(module != null && module.shouldTint() ? new GlintColorVertexConsumer(consumer, module::currentGlintColor) : consumer);
         } catch (Throwable var4) {
            return consumer;
         }
      }
   }
}
