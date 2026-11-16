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
   private ItemGlowRenderer() {}

   public static void init() {
      HudElementRegistry.attachElementAfter(
         VanillaHudElements.HOTBAR,
         class_2960.method_60655("nightfall", "item_glow"),
         (context, tickCounter) -> renderHotbar(context)
      );

      ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
         if (screen instanceof class_465 handled) {
            ScreenEvents.afterRender(screen).register((current, context, mouseX, mouseY, tickDelta) ->
               renderInventory(handled, context)
            );
         }
      });
   }

   public static void renderHotbar(class_332 context) {
      ItemGlow module = getModule();
      if (module == null || !module.isEnabled() || !module.hotbar()) return;

      class_310 mc = class_310.method_1551();
      class_1657 player = mc.field_1724;
      if (player == null || mc.field_1690.field_1842) return;

      class_1661 inventory = player.method_31548();
      int centerX = context.method_51421() / 2;
      int y = context.method_51443() - 16 - 3;
      int radius = module.radius();

      for (int slot = 0; slot < 9; slot++) {
         class_1799 stack = inventory.method_5438(slot);
         if (!stack.method_7960()) {
            glow(context, centerX - 90 + slot * 20 + 2, y, radius, module);
         }
      }

      class_1799 offhand = player.method_6079();
      if (!offhand.method_7960()) {
         boolean rightHanded = player.method_6068() == class_1306.field_6183;
         int offhandX = rightHanded ? centerX - 91 - 26 : centerX + 91 + 10;
         glow(context, offhandX, y, radius, module);
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
            glow(context, left + slot.field_7873, top + slot.field_7872, radius, module);
         }
      }
   }

   private static void glow(class_332 context, int x, int y, int radius, ItemGlow module) {
      radius = Math.max(1, radius);
      int rgb = module.color();
      double time = (System.nanoTime() % 1_000_000_000_000L) / 1.0E9;
      float pulse = module.pulse() ? 0.8F + 0.2F * (float)Math.sin(time * 3.0) : 1.0F;
      float power = module.intensity() * pulse;

      context.method_25294(x, y, x + 16, y + 16, argb(power * 0.12F, rgb));

      for (int i = 1; i <= radius; i++) {
         float fade = 1.0F - (i - 1) / (float)radius;
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

   private static int argb(float alpha, int rgb) {
      int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0F)));
      return a << 24 | (rgb & 0xFFFFFF);
   }

   private static ItemGlow getModule() {
      try {
         return ModuleManager.get(ItemGlow.class);
      } catch (Throwable ignored) {
         return null;
      }
   }
}