package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_265;
import net.minecraft.class_2680;
import net.minecraft.class_332;
import net.minecraft.class_3965;

/**
 * Highlights the block currently targeted by the crosshair.
 *
 * Rendering is performed through Nightfall's existing world-to-HUD projection
 * instead of submitting custom world line vertices. This keeps the module
 * compatible with the current 1.21.11 render pipeline.
 */
public final class BlockHighlight extends Module {
   private final ModeSetting style = this.add(new ModeSetting(
      "Stil", "Glide", "Glide", "ExactShape"
   ));
   private final ModeSetting shapeMode = this.add(new ModeSetting(
      "Darstellung", "Beides", "Beides", "Nur Linien", "Nur Füllung"
   ));
   private final NumberSetting expand = this.add(new NumberSetting(
      "Erweiterung", 0.002, 0.0, 0.1, 0.001
   ));
   private final NumberSetting lineThickness = this.add(new NumberSetting(
      "Linienstärke", 2.0, 1.0, 5.0, 1.0
   ));

   private final ColorSetting lineColor = this.add(new ColorSetting(
      "Linienfarbe", 0x788CFF
   ));
   private final ColorSetting fillColor = this.add(new ColorSetting(
      "Füllfarbe", 0x788CFF
   ));
   private final NumberSetting fillOpacity = this.add(new NumberSetting(
      "Füll-Deckkraft", 0.18, 0.0, 0.8, 0.02
   ));
   private final BoolSetting rainbow = this.add(new BoolSetting(
      "Regenbogen", false
   ));
   private final NumberSetting rainbowSpeed = this.add(new NumberSetting(
      "Regenbogen-Geschwindigkeit", 0.25, 0.02, 2.0, 0.01
   ));

   private final BoolSetting pulse = this.add(new BoolSetting(
      "Pulsieren", true
   ));
   private final NumberSetting pulseSpeed = this.add(new NumberSetting(
      "Puls-Geschwindigkeit", 2.5, 0.2, 8.0, 0.1
   ));
   private final NumberSetting pulseStrength = this.add(new NumberSetting(
      "Puls-Stärke", 0.45, 0.0, 1.0, 0.05
   ));
   private final NumberSetting glideSpeed = this.add(new NumberSetting(
      "Gleit-Geschwindigkeit", 18.0, 2.0, 40.0, 1.0
   ));
   private final NumberSetting fadeSpeed = this.add(new NumberSetting(
      "Ein-/Ausblend-Geschwindigkeit", 10.0, 1.0, 30.0, 1.0
   ));

   private class_238 currentBox;
   private final List<class_238> exactBoxes = new ArrayList<>();
   private double alpha;
   private long lastFrameNanos;

   public BlockHighlight() {
      super("BlockHighlight", "Animierte Markierung für den Block im Fadenkreuz.", Category.VISUALS);
   }

   @Override
   public void renderHud(class_332 context) {
      if (mc.field_1687 == null || mc.field_1724 == null || !Projection.valid()) return;

      long now = System.nanoTime();
      double dt = this.lastFrameNanos == 0L
         ? 1.0 / 60.0
         : Math.max(0.0, Math.min(0.1, (now - this.lastFrameNanos) / 1_000_000_000.0));
      this.lastFrameNanos = now;

      class_3965 hit = mc.field_1765 instanceof class_3965 blockHit ? blockHit : null;
      if (hit == null) {
         this.alpha = Math.max(0.0, this.alpha - dt * this.fadeSpeed.get());
      } else {
         this.alpha = Math.min(1.0, this.alpha + dt * this.fadeSpeed.get());
         this.updateTarget(hit.method_17777(), dt);
      }

      if (this.alpha <= 0.0 || this.currentBox == null) return;

      double time = now / 1_000_000_000.0;
      double pulseFactor = this.pulse.get()
         ? 1.0 - this.pulseStrength.get()
            + this.pulseStrength.get() * (0.5 + 0.5 * Math.sin(time * this.pulseSpeed.get()))
         : 1.0;
      double opacity = this.alpha * pulseFactor;

      int lineRgb = this.lineColor.rgb();
      int fillRgb = this.fillColor.rgb();
      if (this.rainbow.get()) {
         int rgb = java.awt.Color.HSBtoRGB((float)((time * this.rainbowSpeed.get()) % 1.0), 0.75F, 1.0F) & 0xFFFFFF;
         lineRgb = rgb;
         fillRgb = rgb;
      }

      int lineArgb = withAlpha(lineRgb, opacity);
      int fillArgb = withAlpha(fillRgb, opacity * this.fillOpacity.get());

      if (this.style.is("ExactShape") && hit != null) {
         for (class_238 box : this.exactBoxes) {
            this.drawBox(context, box, lineArgb, fillArgb);
         }
      } else if (this.style.is("ExactShape")) {
         for (class_238 box : this.exactBoxes) {
            this.drawBox(context, box, lineArgb, fillArgb);
         }
      } else {
         this.drawBox(context, this.currentBox, lineArgb, fillArgb);
      }
   }

   private void updateTarget(class_2338 pos, double dt) {
      class_2680 state = mc.field_1687.method_8320(pos);
      class_265 outline = state.method_26218(mc.field_1687, pos);
      class_238 localBounds = outline.method_1107();

      double minX = localBounds.field_1323;
      double minY = localBounds.field_1322;
      double minZ = localBounds.field_1321;
      double maxX = localBounds.field_1320;
      double maxY = localBounds.field_1325;
      double maxZ = localBounds.field_1324;

      // Some non-solid blocks have an empty outline shape. Keep them selectable
      // and give them a small, predictable full-block marker.
      if (maxX - minX < 1.0E-5 || maxY - minY < 1.0E-5 || maxZ - minZ < 1.0E-5) {
         minX = minY = minZ = 0.0;
         maxX = maxY = maxZ = 1.0;
      }

      double margin = this.expand.get();
      class_238 target = new class_238(
         pos.method_10263() + minX - margin,
         pos.method_10264() + minY - margin,
         pos.method_10260() + minZ - margin,
         pos.method_10263() + maxX + margin,
         pos.method_10264() + maxY + margin,
         pos.method_10260() + maxZ + margin
      );

      if (this.currentBox == null) {
         this.currentBox = target;
      } else {
         double k = 1.0 - Math.exp(-this.glideSpeed.get() * dt);
         this.currentBox = new class_238(
            lerp(k, this.currentBox.field_1323, target.field_1323),
            lerp(k, this.currentBox.field_1322, target.field_1322),
            lerp(k, this.currentBox.field_1321, target.field_1321),
            lerp(k, this.currentBox.field_1320, target.field_1320),
            lerp(k, this.currentBox.field_1325, target.field_1325),
            lerp(k, this.currentBox.field_1324, target.field_1324)
         );
      }

      this.exactBoxes.clear();
      List<class_238> parts = outline.method_1090();
      if (parts.isEmpty()) {
         this.exactBoxes.add(target);
      } else {
         for (class_238 part : parts) {
            if (this.exactBoxes.size() >= 64) break;
            this.exactBoxes.add(new class_238(
               pos.method_10263() + part.field_1323 - margin,
               pos.method_10264() + part.field_1322 - margin,
               pos.method_10260() + part.field_1321 - margin,
               pos.method_10263() + part.field_1320 + margin,
               pos.method_10264() + part.field_1325 + margin,
               pos.method_10260() + part.field_1324 + margin
            ));
         }
      }
   }

   private void drawBox(class_332 context, class_238 box, int lineArgb, int fillArgb) {
      if (this.shapeMode.is("Beides") || this.shapeMode.is("Nur Füllung")) {
         Draw.fillProjectedBounds(context, box, fillArgb);
      }
      if (this.shapeMode.is("Beides") || this.shapeMode.is("Nur Linien")) {
         Draw.box(context, box, lineArgb, this.lineThickness.getInt(), true, true);
      }
   }

   private static double lerp(double delta, double start, double end) {
      return start + (end - start) * delta;
   }

   private static int withAlpha(int rgb, double opacity) {
      int a = (int)Math.max(0.0, Math.min(255.0, Math.round(255.0 * opacity)));
      return a << 24 | rgb & 0xFFFFFF;
   }

   @Override
   protected void onDisable() {
      this.reset();
   }

   @Override
   public void onWorldLeave() {
      this.reset();
   }

   private void reset() {
      this.currentBox = null;
      this.exactBoxes.clear();
      this.alpha = 0.0;
      this.lastFrameNanos = 0L;
   }
}
