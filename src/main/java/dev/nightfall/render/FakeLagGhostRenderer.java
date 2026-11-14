package dev.nightfall.render;

import dev.nightfall.modules.FakeLag;
import dev.nightfall.util.Projection;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_1007;
import net.minecraft.class_10017;
import net.minecraft.class_10055;
import net.minecraft.class_310;
import net.minecraft.class_897;

/**
 * Draws a second copy of the local player's normal skin at the last movement
 * position that was actually sent to the server while FakeLag is holding packets.
 */
public final class FakeLagGhostRenderer {
   private FakeLagGhostRenderer() {
   }

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(FakeLagGhostRenderer::render);
   }

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null || mc.field_1687 == null
         || !FakeLag.hasPendingServerPosition()
         || context.matrices() == null
         || context.commandQueue() == null
         || context.worldState() == null
         || context.worldState().field_63082 == null) {
         return;
      }

      try {
         class_897 baseRenderer = mc.method_1561().method_3953(mc.field_1724);
         if (!(baseRenderer instanceof class_1007 playerRenderer)) return;

         float tickDelta = Projection.tickDelta();
         class_10055 state = playerRenderer.method_62608();
         playerRenderer.method_62354(mc.field_1724, state, tickDelta);

         state.field_53325 = FakeLag.serverX();
         state.field_53326 = FakeLag.serverY();
         state.field_53327 = FakeLag.serverZ();
         state.field_53446 = FakeLag.serverYaw();
         state.field_53447 = 0.0F;
         state.field_53448 = FakeLag.serverPitch();

         // Do not render a second nameplate above the visual server-position copy.
         state.field_53337 = null;
         state.field_53338 = null;

         // EntityRenderer.render expects the matrix stack to be translated to
         // the entity's camera-relative position. Without this, the ghost is drawn
         // at the render origin (which looks like it is attached to the camera/head).
         var cameraPos = mc.field_1773.method_19418().method_71156();
         context.matrices().method_22903();
         try {
            context.matrices().method_22904(
               FakeLag.serverX() - cameraPos.field_1352,
               FakeLag.serverY() - cameraPos.field_1351,
               FakeLag.serverZ() - cameraPos.field_1350
            );
            ((class_897)playerRenderer).method_3936(
               (class_10017)state,
               context.matrices(),
               context.commandQueue(),
               context.worldState().field_63082
            );
         } finally {
            context.matrices().method_22909();
         }
      } catch (Throwable ignored) {
         // A render-only helper must never interrupt the normal world render.
      }
   }
}
