package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_5636;
import net.minecraft.class_4184;
import org.joml.Vector4f;

public final class Fog extends Module {
   private final BoolSetting remove = this.add(new BoolSetting("Nebel entfernen", false));
   private final BoolSetting scaleMode = this.add(new BoolSetting("Sichtweite skalieren", true));
   private final NumberSetting multiplier = this.add(new NumberSetting("Distanz-Multiplikator", 2.0, 0.2, 8.0, 0.1));
   private final NumberSetting fixedStart = this.add(new NumberSetting("Fester Start", 40.0, 0.0, 500.0, 5.0));
   private final NumberSetting fixedEnd = this.add(new NumberSetting("Festes Ende", 180.0, 1.0, 1000.0, 5.0));
   private final BoolSetting affectSky = this.add(new BoolSetting("Himmel/Wolken", true));
   private final BoolSetting customColor = this.add(new BoolSetting("Eigene Farbe", false));
   private final ColorSetting color = this.add(new ColorSetting("Nebel-Farbe", 0x966EFF));
   private final NumberSetting blend = this.add(new NumberSetting("Farb-Mischung", 1.0, 0.0, 1.0, 0.05));
   private final BoolSetting rainbow = this.add(new BoolSetting("Rainbow", false));
   private final NumberSetting rainbowSpeed = this.add(new NumberSetting("Rainbow-Speed", 0.05, 0.005, 0.5, 0.005));
   private final NumberSetting waterVisibility = this.add(new NumberSetting("Wasser-Sichtweite", 1.0, 0.3, 10.0, 0.1));
   private final NumberSetting lavaVisibility = this.add(new NumberSetting("Lava-Sichtweite", 1.0, 0.3, 10.0, 0.1));
   private final BoolSetting removeEffectFog = this.add(new BoolSetting("Effekt-Nebel entfernen", false));

   public Fog() {
      super("Fog", "Steuert Nebel-Distanz, Farbe und Unterwasser-/Lava-Nebel.", Category.VISUALS);
   }

   public float renderStart(float value) {
      if (remove.get()) return 100000.0F;
      if (scaleMode.get()) return value * multiplier.get().floatValue();
      return fixedStart.get().floatValue();
   }

   public float renderEnd(float value) {
      if (remove.get()) return 100000.0F;
      if (scaleMode.get()) return value * multiplier.get().floatValue();
      return fixedEnd.get().floatValue();
   }

   public float skyEnd(float value) {
      return affectSky.get() ? renderEnd(value) : value;
   }

   public float envStart(float value) {
      if (waterVisibility.get() == 1.0 && lavaVisibility.get() == 1.0) return value;
      class_5636 type = submersion();
      if (type == class_5636.field_27886) return value * waterVisibility.get().floatValue();
      if (type == class_5636.field_27885) return value * lavaVisibility.get().floatValue();
      return value;
   }

   public float envEnd(float value) {
      if (waterVisibility.get() == 1.0 && lavaVisibility.get() == 1.0 && !removeEffectFog.get()) return value;
      class_5636 type = submersion();
      if (type == class_5636.field_27886) return value * waterVisibility.get().floatValue();
      if (type == class_5636.field_27885) return value * lavaVisibility.get().floatValue();
      // Keep this mapping-safe for 1.21.11. The old imported effect IDs
      // no longer exist in the current intermediary mappings.
      if (removeEffectFog.get() && type == class_5636.field_27887) {
         return 100000.0F;
      }
      return value;
   }

   public void modifyColor(Vector4f fogColor) {
      if (!customColor.get()) return;

      int rgb;
      if (rainbow.get()) {
         float h = (float)((System.currentTimeMillis() / 1000.0 * rainbowSpeed.get()) % 1.0);
         rgb = java.awt.Color.HSBtoRGB(h, 0.6F, 1.0F) & 0xFFFFFF;
      } else {
         rgb = color.rgb();
      }

      float r = ((rgb >> 16) & 255) / 255.0F;
      float g = ((rgb >> 8) & 255) / 255.0F;
      float b = (rgb & 255) / 255.0F;
      float k = blend.get().floatValue();
      fogColor.set(
         fogColor.x + (r - fogColor.x) * k,
         fogColor.y + (g - fogColor.y) * k,
         fogColor.z + (b - fogColor.z) * k,
         fogColor.w
      );
   }

   private class_5636 submersion() {
      // FogRenderer is constructed while Minecraft is still initializing.
      // During that phase the renderer (and therefore its camera) can be null.
      if (mc == null || mc.field_1773 == null) return class_5636.field_27888;

      class_4184 camera = mc.field_1773.method_19418();
      return camera != null ? camera.method_19334() : class_5636.field_27888;
   }
}
