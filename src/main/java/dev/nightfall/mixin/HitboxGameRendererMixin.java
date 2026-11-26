package dev.nightfall.mixin;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.Hitbox;
import net.minecraft.class_1297;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3966;
import net.minecraft.class_746;
import net.minecraft.class_757;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_757.class)
public abstract class HitboxGameRendererMixin {
   @Inject(method = "method_3190", at = @At("TAIL"))
   private void nightfall$expandEntityHitboxes(float tickDelta, CallbackInfo ci) {
      Hitbox module = ModuleManager.get(Hitbox.class);
      if (!module.isEnabled() || module.getExpansion() <= 0.0) return;

      net.minecraft.class_310 mc = net.minecraft.class_310.method_1551();
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1687 == null || mc.field_1755 != null) return;

      class_1297 current = mc.field_1692;
      class_243 start = player.method_33571();
      class_243 direction = player.method_5828(tickDelta);
      double maxDistance = module.getRange();
      class_243 end = start.method_1019(direction.method_1021(maxDistance));

      double minX = Math.min(start.field_1352, end.field_1352);
      double minY = Math.min(start.field_1351, end.field_1351);
      double minZ = Math.min(start.field_1350, end.field_1350);
      double maxX = Math.max(start.field_1352, end.field_1352);
      double maxY = Math.max(start.field_1351, end.field_1351);
      double maxZ = Math.max(start.field_1350, end.field_1350);
      class_238 searchBox = new class_238(minX, minY, minZ, maxX, maxY, maxZ).method_1014(module.getExpansion());

      class_1297 bestEntity = null;
      class_243 bestHit = null;
      double bestDistanceSq = maxDistance * maxDistance;

      for (class_1297 entity : mc.field_1687.method_8333(player, searchBox, e ->
         e != player && e.method_5805() && module.allows(e))) {
         Optional<class_243> hit = module.expandedBox(entity).method_992(start, end);
         if (hit.isEmpty()) continue;

         class_243 hitPos = hit.get();
         double distanceSq = start.method_1025(hitPos);
         if (distanceSq >= bestDistanceSq) continue;

         bestDistanceSq = distanceSq;
         bestEntity = entity;
         bestHit = hitPos;
      }

      if (bestEntity == null || bestHit == null) return;

      if (current != null && current != player && current.method_5805()) {
         Optional<class_243> currentHit = current.method_5829().method_992(start, end);
         if (currentHit.isPresent()) {
            double currentDistanceSq = start.method_1025(currentHit.get());
            if (currentDistanceSq < bestDistanceSq) return;
         }
      }

      mc.field_1692 = bestEntity;
      mc.field_1765 = new class_3966(bestEntity, bestHit);
   }
}
