package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Projection;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1311;
import net.minecraft.class_1657;
import net.minecraft.class_243;
import net.minecraft.class_4184;

public final class GlowESP extends Module {
   private final BoolSetting players = this.add(new BoolSetting("Spieler", true));
   private final BoolSetting hostile = this.add(new BoolSetting("Feindliche Mobs", true));
   private final BoolSetting passive = this.add(new BoolSetting("Passive Mobs", false));
   private final BoolSetting throughWalls = this.add(new BoolSetting("Durch Wände", true));
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 96.0, 8.0, 256.0, 8.0));
   private final ColorSetting color = this.add(new ColorSetting("Glow-Farbe", 5631709));

   public GlowESP() {
      super("GlowESP", "Echter Minecraft-Outline-Shader für ausgewählte Entities; nur lokale Darstellung.", Category.VISUALS);
   }

   public boolean shouldGlow(class_1297 entity) {
      if (this.isEnabled() && mc.field_1724 != null && mc.field_1687 != null && entity != null && entity != mc.field_1724 && entity instanceof class_1309) {
         BaseFinder baseFinder = ModuleManager.get(BaseFinder.class);
         if (baseFinder != null && baseFinder.isFakeChunk(entity.method_31477() >> 4, entity.method_31479() >> 4)) {
            return false;
         } else {
            class_4184 camera = mc.field_1773.method_19418();
            class_243 cameraPos = camera != null && camera.method_19332() ? camera.method_71156() : mc.field_1724.method_33571();
            if (entity.method_5649(cameraPos.field_1352, cameraPos.field_1351, cameraPos.field_1350) > this.range.get() * this.range.get()) {
               return false;
            } else if (!this.throughWalls.get() && !Projection.canSeeFromCamera(mc, entity)) {
               return false;
            } else if (entity instanceof class_1657) {
               return this.players.get();
            } else {
               class_1311 group = entity.method_5864().method_5891();
               return group == class_1311.field_6302 ? this.hostile.get() : this.passive.get();
            }
         }
      } else {
         return false;
      }
   }

   public int outlineRgb() {
      return this.color.rgb();
   }

   public static boolean shouldGlowLocal(class_1297 entity) {
      try {
         return ModuleManager.get(GlowESP.class).shouldGlow(entity);
      } catch (Throwable var2) {
         return false;
      }
   }

   public static int colorFor(class_1297 entity, int fallback) {
      try {
         GlowESP glow = ModuleManager.get(GlowESP.class);
         return glow.shouldGlow(entity) ? glow.outlineRgb() : fallback;
      } catch (Throwable var3) {
         return fallback;
      }
   }
}
