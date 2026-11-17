package dev.nightfall.render;

import dev.nightfall.mixin.HandledScreenAccessor;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.ItemGlow;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.class_1306;
import net.minecraft.class_1657;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_465;

public final class ItemGlowRenderer {
   private static final int LAYERS = 12;

   private ItemGlowRenderer() {}

   public static void init() {
      HudElementRegistry.attachElementAfter(
         VanillaHudElements.HOTBAR,
         class_2960.method_60655("nightfall", "item_glow"),
         (context, tickCounter) -> {
            renderHand(context);
            renderHotbar(context);
         }
      );

      ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
         if (screen instanceof class_465 handled) {
            ScreenEvents.afterRender(screen).register((current, context, mouseX, mouseY, tickDelta) ->
               renderInventory(handled, context)
            );
         }
      });
   }

   private static void renderHand(class_332 context) {
      ItemGlow module = getModule();
      if (module == null || !module.isEnabled()) return;

      class_310 mc = class_310.method_1551();
      class_1657 player = mc.field_1724;
      if (player == null || mc.field_1690.field_1842 || !mc.field_1690.method_31044().method_31034()) return;

      float width = context.method_51421();
      float height = context.method_51443();
      float pulse = module.pulse()
         ? 0.8F + 0.2F * (float)Math.sin(System.nanoTime() / 1_000_000_000.0 * 3.0)
         : 1.0F;

      if (!player.method_6047().method_7960()) {
         drawGlow(context, width * module.positionX(), height * module.positionY(), module, 0.0F, pulse);
      }

      if (module.offhand() && !player.method_6079().method_7960()) {
         drawGlow(context, width * (1.0F - module.positionX()), height * module.positionY(), module, 0.5F, pulse);
      }
   }

   private static void renderHotbar(class_332 context) {
      ItemGlow module = getModule();
      if (module == null || !module.isEnabled() || !module.hotbar()) return;

      class_310 mc = class_310.method_1551();
      class_1657 player = mc.field_1724;
      if (player == null || mc.field_1690.field_1842) return;

      class_1661 inventory = player.method_31548();
      int centerX = context.method_51421() / 2;
      int y = context.method_51443() - 16 - 3;
      int radius = Math.min(4, module.radius());

      for (int slot = 0; slot < 9; slot++) {
         class_1799 stack = inventory.method_5438(slot);
         if (!stack.method_7960()) {
            glowSlot(context, centerX - 90 + slot * 20 + 2, y, radius, module, slot * 0.035F);
         }
      }

      class_1799 offhand = player.method_6079();
      if (module.offhand() && !offhand.method_7960()) {
         boolean rightHanded = player.method_6068() == class_1306.field_6183;
         int offhandX = rightHanded ? centerX - 91 - 26 : centerX + 91 + 10;
         glowSlot(context, offhandX, y, radius, module, 0.5F);
      }
   }

   private static void renderInventory(class_465 screen, class_332 context) {
      ItemGlow module = getModule();
      if (module == null || !module.isEnabled() || !module.inventory()) return;

      HandledScreenAccessor accessor = (HandledScreenAccessor)screen;
      int left = accessor.nightfall$getX();
      int top = accessor.nightfall$getY();
      int radius = Math.min(module.radius(), 4);
      class_1703 handler = accessor.nightfall$getHandler();

      for (class_1735 slot : handler.field_7761) {
         if (!slot.method_7677().method_7960()) {
            glowSlot(context, left + slot.field_7873, top + slot.field_7872, radius, module, 0.0F);
         }
      }
   }

   private static void drawGlow(class_332 context, float centerX, float centerY, ItemGlow module, float hueOffset, float pulse) {
      double time = System.nanoTime() / 1_000_000_000.0;
      int rgb = module.color(hueOffset);
      int bright = mix(rgb, 0xFFFFFF, 0.45F);
      float flash = 1.0F + (float)Math.max(0.0, Math.sin(time * 5.0)) * 0.25F;
      float maxRadius = module.radius() * pulse * flash;
      float layerAlpha = module.intensity() * pulse * flash * 0.07F;

      // Outer-to-inner fade creates the soft neon bloom around the held item.
      for (int i = 0; i < LAYERS; i++) {
         float f = 1.0F - i / (float)LAYERS;
         int radius = Math.round(maxRadius * f);
         if (radius < 1) continue;
         int color = argb(layerAlpha, i > LAYERS - 4 ? bright : rgb);
         fillCircle(context, Math.round(centerX), Math.round(centerY), radius, color);
      }

      if (!module.sparks()) return;

      // Animated segmented ring.
      float ringRadius = maxRadius * 0.72F;
      for (int i = 0; i < 80; i++) {
         double angle = Math.PI * 2.0 * i / 80.0;
         float dash = 0.5F + 0.5F * (float)Math.sin(angle * 4.0 - time * 3.0);
         if (dash < 0.35F) continue;
         int x = Math.round(centerX + (float)Math.cos(angle) * ringRadius);
         int y = Math.round(centerY + (float)Math.sin(angle) * ringRadius);
         context.method_25294(x, y, x + 2, y + 2, argb(0.55F * dash * pulse, bright));
      }

      // Orbiting sparks, each with a faint outer glow.
      for (int i = 0; i < 12; i++) {
         double angle = time * (0.9 + 0.25 * (i % 3)) + i * (Math.PI * 2.0 / 12.0);
         float orbit = maxRadius * (0.40F + 0.38F * (0.5F + 0.5F * (float)Math.sin(time * 1.7 + i)));
         int x = Math.round(centerX + (float)Math.cos(angle) * orbit);
         int y = Math.round(centerY + (float)Math.sin(angle) * orbit * 0.85F);
         float twinkle = 0.5F + 0.5F * (float)Math.sin(time * 5.0 + i * 2.1);
         context.method_25294(x - 2, y - 2, x + 3, y + 3, argb(0.16F * twinkle, rgb));
         context.method_25294(x - 1, y - 1, x + 2, y + 2, argb(0.55F + 0.45F * twinkle, bright));
      }
   }

   private static void glowSlot(class_332 context, int x, int y, int radius, ItemGlow module, float hueOffset) {
      int rgb = module.color(hueOffset);
      float pulse = module.pulse()
         ? 0.8F + 0.2F * (float)Math.sin(System.nanoTime() / 1_000_000_000.0 * 3.0)
         : 1.0F;
      float power = module.intensity() * pulse;
      context.method_25294(x, y, x + 16, y + 16, argb(power * 0.12F, rgb));

      for (int i = 1; i <= Math.max(1, radius); i++) {
         float fade = 1.0F - (i - 1) / (float)Math.max(1, radius);
         int color = argb(power * fade * fade * 0.9F, rgb);
         int x0 = x - i;
         int y0 = y - i;
         int x1 = x + 16 + i;
         int y1 = y + 16 + i;
         context.method_25294(x0, y0, x1, y0 + 1, color);
         context.method_25294(x0, y1 - 1, x1, y1, color);
         context.method_25294(x0, y0 + 1, x0 + 1, y1 - 1, color);
         context.method_25294(x1 - 1, y0 + 1, x1, y1 - 1, color);
      }
   }

   private static void fillCircle(class_332 context, int cx, int cy, int radius, int color) {
      for (int dy = -radius; dy <= radius; dy++) {
         int dx = (int)Math.sqrt((double)radius * radius - (double)dy * dy);
         context.method_25294(cx - dx, cy + dy, cx + dx, cy + dy + 1, color);
      }
   }

   private static int argb(float alpha, int rgb) {
      int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
      return a << 24 | (rgb & 0xFFFFFF);
   }

   private static int mix(int first, int second, float amount) {
      int red = Math.round((first >> 16 & 0xFF) + ((second >> 16 & 0xFF) - (first >> 16 & 0xFF)) * amount);
      int green = Math.round((first >> 8 & 0xFF) + ((second >> 8 & 0xFF) - (first >> 8 & 0xFF)) * amount);
      int blue = Math.round((first & 0xFF) + ((second & 0xFF) - (first & 0xFF)) * amount);
      return Math.max(0, Math.min(255, red)) << 16
         | Math.max(0, Math.min(255, green)) << 8
         | Math.max(0, Math.min(255, blue));
   }

   private static ItemGlow getModule() {
      try {
         return ModuleManager.get(ItemGlow.class);
      } catch (Throwable ignored) {
         return null;
      }
   }
}