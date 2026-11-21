package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import dev.nightfall.util.Theme;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_1297;
import net.minecraft.class_1299;
import net.minecraft.class_2378;
import net.minecraft.class_238;
import net.minecraft.class_332;
import net.minecraft.class_7923;

/**
 * Rounded, smoothed 2D screen-space boxes for selected non-player entities.
 * This is separate from the existing 3D EntityESP module.
 */
public final class EntityEsp2D extends Module {
   private final ListSetting entities = this.add(new ListSetting("Entity-Typen"));
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 96.0, 8.0, 256.0, 8.0));
   private final NumberSetting cornerRadius = this.add(new NumberSetting("Eckenradius", 5.0, 0.0, 12.0, 0.5));
   private final NumberSetting thickness = this.add(new NumberSetting("Linienstaerke", 1.5, 0.5, 4.0, 0.5));
   private final NumberSetting fillAlpha = this.add(new NumberSetting("Fuellen (Alpha)", 35.0, 0.0, 120.0, 5.0));
   private final NumberSetting smoothing = this.add(new NumberSetting("Smoothing", 0.55, 0.0, 0.95, 0.05));
   private final ModeSetting colorMode = this.add(new ModeSetting("Farbschema", "Entity", "Entity", "Eigene"));
   private final ColorSetting boxColor = this.add(new ColorSetting("Box-Farbe", 0x39FFB6));
   private final BoolSetting distanceFade = this.add(new BoolSetting("Distanz-Fade", true));
   private final BoolSetting glowLayer = this.add(new BoolSetting("Soft Glow", true));
   private final ColorSetting glowColor = this.add(new ColorSetting("Neon-Farbe", 0x39FFB6));
   private final Map<Integer, double[]> boxCache = new HashMap<>();
   private final Set<Integer> seenEntities = new HashSet<>();
   private final double[] projectedBounds = new double[4];
   private final double[] projected = new double[2];

   public EntityEsp2D() {
      super("EntityESP 2D", "Abgerundete, geglaettete 2D-Boxen fuer ausgewaehlte Entities.", Category.VISUALS);
      this.entities.add("minecraft:item_frame");
      this.entities.add("minecraft:glow_item_frame");
      this.entities.add("minecraft:armor_stand");
      this.entities.add("minecraft:wolf");
      this.entities.add("minecraft:cat");
      this.entities.add("minecraft:villager");
      this.entities.add("minecraft:iron_golem");
      this.entities.add("minecraft:snow_golem");
      this.entities.add("minecraft:ender_pearl");
      this.entities.add("minecraft:wind_charge");
      this.entities.add("minecraft:splash_potion");
      this.entities.add("minecraft:armadillo");
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1724 == null || mc.field_1687 == null || !Projection.valid()) {
         this.boxCache.clear();
         return;
      }

      double maxRange = this.range.get();
      double rangeSquared = maxRange * maxRange;
      float tickDelta = Projection.tickDelta();
      double smooth = this.smoothing.get();
      Set<Integer> seen = this.seenEntities;
      seen.clear();

      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (entity == null || entity == mc.field_1724 || !this.accept(entity)) continue;

         double currentX = entity.method_23317();
         double currentY = entity.method_23318();
         double currentZ = entity.method_23321();
         double x = entity.field_6038 + (currentX - entity.field_6038) * tickDelta;
         double y = entity.field_5971 + (currentY - entity.field_5971) * tickDelta;
         double z = entity.field_5989 + (currentZ - entity.field_5989) * tickDelta;

         double dx = x - Projection.eyeX();
         double dy = y - Projection.eyeY();
         double dz = z - Projection.eyeZ();
         double distanceSquared = dx * dx + dy * dy + dz * dz;
         if (distanceSquared > rangeSquared) continue;

         class_238 currentBox = entity.method_5829();
         double ox = x - currentX;
         double oy = y - currentY;
         double oz = z - currentZ;
         if (!this.projectBounds(new class_238(
            currentBox.field_1323 + ox,
            currentBox.field_1322 + oy,
            currentBox.field_1321 + oz,
            currentBox.field_1320 + ox,
            currentBox.field_1325 + oy,
            currentBox.field_1324 + oz
         ), this.projectedBounds)) continue;

         int id = entity.method_5628();
         seen.add(id);
         double[] bounds = this.boxCache.get(id);
         if (bounds == null) {
            bounds = this.projectedBounds.clone();
            this.boxCache.put(id, bounds);
         } else {
            for (int i = 0; i < 4; i++) {
               bounds[i] = bounds[i] * smooth + this.projectedBounds[i] * (1.0 - smooth);
            }
         }

         double width = bounds[2] - bounds[0];
         double height = bounds[3] - bounds[1];
         if (width < 2.0 || height < 2.0) continue;

         double fade = this.distanceFade.get()
            ? Math.max(0.35, Math.min(1.0, 1.2 - Math.sqrt(distanceSquared) / maxRange))
            : 1.0;
         int rgb = this.colorMode.is("Eigene") ? this.boxColor.rgb() : this.colorFor(entity);
         int alpha = (int)(255.0 * fade);
         int left = (int)Math.floor(bounds[0]);
         int top = (int)Math.floor(bounds[1]);
         int right = (int)Math.ceil(bounds[2]);
         int bottom = (int)Math.ceil(bounds[3]);
         double radius = Math.min(this.cornerRadius.get(), Math.min(width, height) * 0.5);

         if (this.fillAlpha.getInt() > 0) {
            int fill = Theme.withAlpha(rgb, (int)(this.fillAlpha.getInt() * fade));
            ctx.method_25294(left, top, right, bottom, fill);
         }

         if (this.glowLayer.get()) {
            int glowRgb = this.glowColor.rgb();
            this.drawRoundedOutline(ctx, bounds, radius + 2.0, Math.max(3, this.thickness.getInt() + 3),
               Theme.withAlpha(glowRgb, (int)(18.0 * fade)));
            this.drawRoundedOutline(ctx, bounds, radius + 1.0, Math.max(2, this.thickness.getInt() + 1),
               Theme.withAlpha(glowRgb, (int)(42.0 * fade)));
         }
         this.drawRoundedOutline(ctx, bounds, radius, Math.max(1, this.thickness.getInt()),
            Theme.withAlpha(rgb, alpha));

         EspBase.drawn++;
      }

      this.boxCache.keySet().retainAll(seen);
   }

   private boolean accept(class_1297 entity) {
      try {
         class_2378<class_1299<?>> registry = (class_2378<class_1299<?>>)class_7923.field_41177;
         var identifier = registry.method_10221(entity.method_5864());
         return identifier != null && this.entities.has(identifier.toString().toLowerCase(Locale.ROOT));
      } catch (RuntimeException ignored) {
         return false;
      }
   }

   private boolean projectBounds(class_238 box, double[] bounds) {
      double minX = Double.MAX_VALUE;
      double minY = Double.MAX_VALUE;
      double maxX = -Double.MAX_VALUE;
      double maxY = -Double.MAX_VALUE;
      int projectedCorners = 0;

      for (int i = 0; i < 8; i++) {
         double x = (i & 1) == 0 ? box.field_1323 : box.field_1320;
         double y = (i & 2) == 0 ? box.field_1322 : box.field_1325;
         double z = (i & 4) == 0 ? box.field_1321 : box.field_1324;
         if (Projection.project(x, y, z, this.projected)) {
            projectedCorners++;
            minX = Math.min(minX, this.projected[0]);
            minY = Math.min(minY, this.projected[1]);
            maxX = Math.max(maxX, this.projected[0]);
            maxY = Math.max(maxY, this.projected[1]);
         }
      }

      if (projectedCorners == 0 || maxX < 0.0 || maxY < 0.0
         || minX > Projection.width() || minY > Projection.height()) {
         return false;
      }

      minX = Math.max(0.0, Math.min(Projection.width(), minX - 1.5));
      minY = Math.max(0.0, Math.min(Projection.height(), minY - 1.5));
      maxX = Math.max(0.0, Math.min(Projection.width(), maxX + 1.5));
      maxY = Math.max(0.0, Math.min(Projection.height(), maxY + 1.5));
      if (maxX - minX < 1.0 || maxY - minY < 1.0) return false;
      bounds[0] = minX;
      bounds[1] = minY;
      bounds[2] = maxX;
      bounds[3] = maxY;
      return true;
   }

   private void drawRoundedOutline(class_332 ctx, double[] b, double radius, int lineWidth, int argb) {
      double x0 = b[0];
      double y0 = b[1];
      double x1 = b[2];
      double y1 = b[3];
      double r = Math.max(0.0, Math.min(radius, Math.min(x1 - x0, y1 - y0) * 0.5));
      if (r < 1.0) {
         Draw.line(ctx, (float)x0, (float)y0, (float)x1, (float)y0, argb, lineWidth, true);
         Draw.line(ctx, (float)x1, (float)y0, (float)x1, (float)y1, argb, lineWidth, true);
         Draw.line(ctx, (float)x1, (float)y1, (float)x0, (float)y1, argb, lineWidth, true);
         Draw.line(ctx, (float)x0, (float)y1, (float)x0, (float)y0, argb, lineWidth, true);
         return;
      }

      Draw.line(ctx, (float)(x0 + r), (float)y0, (float)(x1 - r), (float)y0, argb, lineWidth, true);
      Draw.line(ctx, (float)x1, (float)(y0 + r), (float)x1, (float)(y1 - r), argb, lineWidth, true);
      Draw.line(ctx, (float)(x1 - r), (float)y1, (float)(x0 + r), (float)y1, argb, lineWidth, true);
      Draw.line(ctx, (float)x0, (float)(y1 - r), (float)x0, (float)(y0 + r), argb, lineWidth, true);

      this.arc(ctx, x0 + r, y0 + r, r, 180.0, argb, lineWidth);
      this.arc(ctx, x1 - r, y0 + r, r, 270.0, argb, lineWidth);
      this.arc(ctx, x1 - r, y1 - r, r, 0.0, argb, lineWidth);
      this.arc(ctx, x0 + r, y1 - r, r, 90.0, argb, lineWidth);
   }

   private void arc(class_332 ctx, double cx, double cy, double radius, double startDegrees, int argb, int lineWidth) {
      int segments = Math.max(6, (int)(radius * 1.5));
      double previousX = cx + Math.cos(Math.toRadians(startDegrees)) * radius;
      double previousY = cy + Math.sin(Math.toRadians(startDegrees)) * radius;
      for (int i = 1; i <= segments; i++) {
         double angle = Math.toRadians(startDegrees + 90.0 * i / segments);
         double nextX = cx + Math.cos(angle) * radius;
         double nextY = cy + Math.sin(angle) * radius;
         Draw.line(ctx, (float)previousX, (float)previousY, (float)nextX, (float)nextY, argb, lineWidth, true);
         previousX = nextX;
         previousY = nextY;
      }
   }

   private int colorFor(class_1297 entity) {
      try {
         class_2378<class_1299<?>> registry = (class_2378<class_1299<?>>)class_7923.field_41177;
         String id = String.valueOf(registry.method_10221(entity.method_5864())).toLowerCase(Locale.ROOT);
         if (id.endsWith("item_frame")) return 0xFFFFBE3C;
         if (id.endsWith("armor_stand")) return 0xFFBEBEC8;
         if (id.endsWith("wolf") || id.endsWith("cat") || id.endsWith("armadillo")) return 0xFF5ADC9E;
         if (id.endsWith("villager")) return 0xFF966EFF;
         if (id.endsWith("iron_golem")) return 0xFFFF5A5A;
         if (id.endsWith("snow_golem")) return 0xFF78C8FF;
         if (id.endsWith("ender_pearl")) return 0xFFAA50FF;
         if (id.endsWith("wind_charge")) return 0xFFC8FFF0;
         if (id.endsWith("splash_potion")) return 0xFFFF69B4;
      } catch (RuntimeException ignored) {
         // Fall back to white when a registry lookup is unavailable.
      }
      return 0xFFFFFFFF;
   }

   @Override
   protected void onDisable() {
      this.boxCache.clear();
   }

   @Override
   public void onWorldLeave() {
      this.boxCache.clear();
   }
}
