package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Theme;
import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_2394;
import net.minecraft.class_2398;
import net.minecraft.class_243;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;

/**
 * HitParticles combines the existing vanilla particle styles with a detailed local hit burst.
 * Detection and rendering are owned by this module, so no duplicate module or manager registration
 * is needed. The world renderer is registered once when the module is constructed.
 */
public final class HitParticles extends Module {
   private static final int MAX_HITS = 12;
   private static final int MAX_SHARDS = 40;
   private static final double TWO_PI = Math.PI * 2.0;
   private static final int[][] FACES = {
      {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},
      {1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1},
      {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1},
      {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0},
      {0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0},
      {1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1}
   };
   private static final float[] SHADE = {0.75F, 0.75F, 0.55F, 1.0F, 0.85F, 0.85F};

   private static HitParticles instance;
   private static boolean rendererRegistered;

   private final ModeSetting style = this.add(new ModeSetting("Partikelstil", "Crit", "Crit", "Herzen", "Flamme", "Witch", "Soul", "Enchant"));
   private final NumberSetting amount = this.add(new NumberSetting("Partikelmenge", 8.0, 2.0, 20.0, 1.0));
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 8.0, 3.0, 16.0, 1.0));
   private final BoolSetting playersOnly = this.add(new BoolSetting("Nur Spieler", false));
   private final NumberSetting size = this.add(new NumberSetting("Effektgröße", 1.0, 0.5, 2.0, 0.1));
   private final NumberSetting duration = this.add(new NumberSetting("Dauer (Sek.)", 0.75, 0.3, 1.5, 0.05));
   private final NumberSetting shards = this.add(new NumberSetting("Scherben", 18.0, 0.0, 40.0, 1.0));
   private final ModeSetting colorMode = this.add(new ModeSetting("Farbmodus", "Verlauf", "Verlauf", "Einfarbig", "Regenbogen"));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 11955455));
   private final ColorSetting color2 = this.add(new ColorSetting("Farbe 2", 3530960));
   private final BoolSetting slashRing = this.add(new BoolSetting("Schlag-Ring", true));
   private final BoolSetting spikes = this.add(new BoolSetting("Strahlen", true));
   private final BoolSetting shockwave = this.add(new BoolSetting("Schockwelle", true));
   private final BoolSetting damageScaling = this.add(new BoolSetting("Schaden skaliert", true));

   private final Map<UUID, Float> lastHealth = new HashMap<>();
   private final List<Hit> hits = new ArrayList<>();

   private static final class Hit {
      final double x, y, z, dirX, dirZ;
      final long startNanos;
      final double scale;
      final int shardCount;
      final float[] sx, sy, sz, speed, size, spin;

      Hit(double x, double y, double z, double dirX, double dirZ, long startNanos, double scale, int shardCount) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.dirX = dirX;
         this.dirZ = dirZ;
         this.startNanos = startNanos;
         this.scale = scale;
         this.shardCount = shardCount;
         this.sx = new float[shardCount];
         this.sy = new float[shardCount];
         this.sz = new float[shardCount];
         this.speed = new float[shardCount];
         this.size = new float[shardCount];
         this.spin = new float[shardCount];
         Random random = new Random(startNanos);
         for (int i = 0; i < shardCount; i++) {
            double u = random.nextDouble() * 2.0 - 1.0;
            double angle = random.nextDouble() * TWO_PI;
            double horizontal = Math.sqrt(1.0 - u * u);
            this.sx[i] = (float)(Math.cos(angle) * horizontal);
            this.sy[i] = (float)Math.max(0.08, u * 0.6 + 0.45);
            this.sz[i] = (float)(Math.sin(angle) * horizontal);
            this.speed[i] = (float)((1.4 + random.nextDouble() * 2.0) * scale);
            this.size[i] = 0.025F + random.nextFloat() * 0.045F;
            this.spin[i] = (random.nextFloat() - 0.5F) * 16.0F;
         }
      }
   }

   public HitParticles() {
      super("HitParticles", "Trefferpartikel mit Kern, Schlagring, Strahlen, Schockwelle und Scherben (lokal).", Category.VISUALS);
      instance = this;
      if (!rendererRegistered) {
         rendererRegistered = true;
         WorldRenderEvents.AFTER_ENTITIES.register(HitParticles::renderWorld);
      }
   }

   @Override
   protected void onEnable() {
      this.reset();
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
      this.lastHealth.clear();
      this.hits.clear();
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null) {
         this.reset();
         return;
      }

      long now = System.nanoTime();
      long lifetime = (long)(this.duration.get() * 1.0E9);
      this.hits.removeIf(hit -> now - hit.startNanos > lifetime);

      Set<UUID> observed = new HashSet<>();
      boolean playersOnlyNow = this.playersOnly.get();
      for (class_1309 entity : mc.field_1687.method_8390(
         class_1309.class,
         mc.field_1724.method_5829().method_1014(this.range.get()),
         e -> e != mc.field_1724 && e.method_5805() && (!playersOnlyNow || e instanceof class_1657)
      )) {
         UUID id = entity.method_5667();
         observed.add(id);
         float health = entity.method_6032();
         Float old = this.lastHealth.put(id, health);
         if (old != null && health < old) {
            this.spawn(entity, old - health);
         }
      }

      // Forget entities that left the scan volume; this bounds memory and avoids stale baselines.
      this.lastHealth.keySet().removeIf(id -> !observed.contains(id));
   }

   private void spawn(class_1309 entity, float damage) {
      double dx = entity.method_23317() - mc.field_1724.method_23317();
      double dz = entity.method_23321() - mc.field_1724.method_23321();
      double length = Math.sqrt(dx * dx + dz * dz);
      if (length < 1.0E-3) {
         dx = 0.0;
         dz = 1.0;
         length = 1.0;
      }

      double scale = this.damageScaling.get() ? Math.max(0.8, Math.min(1.5, 0.8 + damage * 0.08)) : 1.0;
      if (this.hits.size() >= MAX_HITS) {
         this.hits.remove(0);
      }
      long now = System.nanoTime();
      this.hits.add(new Hit(
         entity.method_23317(),
         entity.method_23318() + entity.method_17682() * 0.55,
         entity.method_23321(),
         dx / length,
         dz / length,
         now,
         scale,
         Math.min(MAX_SHARDS, this.shards.getInt())
      ));
      this.spawnVanillaParticles(entity);
   }

   private void spawnVanillaParticles(class_1309 entity) {
      if (mc.field_1713 == null) return;
      class_2394 particle = switch (this.style.get()) {
         case "Herzen" -> class_2398.field_11201;
         case "Flamme" -> class_2398.field_11240;
         case "Witch" -> class_2398.field_11249;
         case "Soul" -> class_2398.field_23114;
         case "Enchant" -> class_2398.field_11215;
         default -> class_2398.field_11205;
      };
      for (int i = 0; i < this.amount.getInt(); i++) {
         double dx = (Math.random() - 0.5) * 0.8;
         double dy = Math.random() * 1.5;
         double dz = (Math.random() - 0.5) * 0.8;
         mc.field_1713.method_3056(
            particle,
            entity.method_23317() + dx,
            entity.method_23318() + dy,
            entity.method_23321() + dz,
            dx * 0.15,
            0.08 + Math.random() * 0.12,
            dz * 0.15
         );
      }
   }

   private static double easeOutCubic(double x) {
      double t = 1.0 - x;
      return 1.0 - t * t * t;
   }

   private int pickColor(int index, int count, double angle, long now) {
      String mode = this.colorMode.get();
      if ("Regenbogen".equals(mode)) {
         float hue = (float)(((double)index / Math.max(1, count) + now * 4.0E-10) % 1.0);
         return Color.HSBtoRGB(hue, 0.75F, 1.0F) & 0xFFFFFF;
      }
      if ("Einfarbig".equals(mode)) {
         return this.color.rgb();
      }
      float mix = (float)(0.5 + 0.5 * Math.sin(angle * 2.0));
      return Theme.lerp(this.color.rgb(), this.color2.rgb(), mix);
   }

   private static void renderWorld(WorldRenderContext context) {
      HitParticles module = instance;
      if (module == null || !module.isEnabled() || module.hits.isEmpty()) return;
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) return;

      class_4184 camera = mc.field_1773.method_19418();
      if (camera == null) return;
      class_243 cameraPos = camera.method_71156();
      class_4587 matrices = context.matrices();
      class_4588 fills = context.consumers().method_73477(class_12249.method_76019());
      long now = System.nanoTime();
      double durationNanos = module.duration.get() * 1.0E9;
      double durationSec = module.duration.get();
      double sizeScale = module.size.get();
      int primary = module.color.rgb();
      int secondary = module.color2.rgb();

      matrices.method_22903();
      try {
         matrices.method_22904(-cameraPos.field_1352, -cameraPos.field_1351, -cameraPos.field_1350);
         Matrix4f matrix = matrices.method_23760().method_23761();
         for (Hit hit : module.hits) {
            double age = (now - hit.startNanos) / durationNanos;
            if (age < 0.0 || age >= 1.0) continue;

            double scale = sizeScale * hit.scale;
            double ease = easeOutCubic(age);
            double time = age * durationSec;
            float alpha = (float)(0.95 * Math.min(1.0, (1.0 - age) * 3.0));
            double ux = hit.dirZ;
            double uz = -hit.dirX;
            double core = 0.15 * scale * Math.max(0.0, 1.0 - age / 0.35);
            if (core > 0.004) {
               drawBlock(fills, matrix, hit.x, hit.y, hit.z, core, age * 14.0, Theme.lerp(0xFFFFFF, primary, 0.25F), 1.0F);
            }

            double centerX = hit.x - hit.dirX * 0.25;
            double centerZ = hit.z - hit.dirZ * 0.25;
            if (module.slashRing.get()) {
               double radius = (0.25 + 1.1 * ease) * scale;
               double width = 0.05 * scale * (1.0 - age) + 0.004;
               int ringColor = Theme.lerp(0xFFFFFF, primary, (float)Math.min(1.0, age * 2.0));
               drawRing(matrix, fills, centerX, hit.y, centerZ, ux, 0.0, uz, 0.0, 1.0, 0.0, radius, width, ringColor, alpha);
               drawRing(matrix, fills, centerX, hit.y, centerZ, ux, 0.0, uz, 0.0, 1.0, 0.0,
                  (0.15 + 0.7 * ease) * scale, width * 0.7, secondary, alpha * 0.9F);
            }

            if (module.spikes.get()) {
               for (int k = 0; k < 8; k++) {
                  double theta = k * Math.PI / 4.0 + 0.2;
                  double start = (0.15 + 0.9 * ease) * scale;
                  double end = start + 0.45 * scale * Math.pow(1.0 - age, 1.5);
                  double halfWidth = 0.03 * scale * (1.0 - age);
                  int rgb = module.pickColor(k, 8, theta, now);
                  drawSpike(matrix, fills, centerX, hit.y, centerZ, ux, 0.0, uz, 0.0, 1.0, 0.0,
                     theta, start, end, halfWidth, rgb, alpha);
               }
            }

            if (module.shockwave.get()) {
               double radius = (0.2 + 1.5 * ease) * scale;
               double width = 0.035 * scale * (1.0 - age) + 0.003;
               drawRing(matrix, fills, hit.x, hit.y - 0.25 * scale, hit.z,
                  1.0, 0.0, 0.0, 0.0, 0.0, 1.0, radius, width,
                  Theme.lerp(primary, 0xFFFFFF, 0.5F), alpha * 0.8F);
            }

            double shrink = Math.min(1.0, (1.0 - age) * 2.5);
            for (int i = 0; i < hit.shardCount; i++) {
               double speed = hit.speed[i];
               double px = hit.x + hit.sx[i] * speed * time * (1.0 - 0.3 * time);
               double py = hit.y + hit.sy[i] * speed * time - 3.0 * time * time;
               double pz = hit.z + hit.sz[i] * speed * time * (1.0 - 0.3 * time);
               double half = hit.size[i] * scale * shrink;
               if (half < 0.003) continue;
               double angle = Math.atan2(hit.sz[i], hit.sx[i]);
               int rgb = Theme.lerp(module.pickColor(i, hit.shardCount, angle, now), 0xFFFFFF, 0.25F);
               drawBlock(fills, matrix, px, py, pz, half, hit.spin[i] * time, rgb, alpha);
            }
         }
      } finally {
         matrices.method_22909();
      }
   }

   private static void drawRing(Matrix4f matrix, class_4588 out, double cx, double cy, double cz,
                                double ux, double uy, double uz, double vx, double vy, double vz,
                                double radius, double halfWidth, int rgb, float alpha) {
      float red = (rgb >> 16 & 255) / 255.0F;
      float green = (rgb >> 8 & 255) / 255.0F;
      float blue = (rgb & 255) / 255.0F;
      double inner = Math.max(0.01, radius - halfWidth);
      double outer = radius + halfWidth;
      int segments = Math.max(24, Math.min(80, (int)(radius * 40.0)));
      for (int i = 0; i < segments; i++) {
         double a0 = TWO_PI * i / segments;
         double a1 = TWO_PI * (i + 1) / segments;
         double c0 = Math.cos(a0), s0 = Math.sin(a0);
         double c1 = Math.cos(a1), s1 = Math.sin(a1);
         float i0x = (float)(cx + (ux * c0 + vx * s0) * inner);
         float i0y = (float)(cy + (uy * c0 + vy * s0) * inner);
         float i0z = (float)(cz + (uz * c0 + vz * s0) * inner);
         float o0x = (float)(cx + (ux * c0 + vx * s0) * outer);
         float o0y = (float)(cy + (uy * c0 + vy * s0) * outer);
         float o0z = (float)(cz + (uz * c0 + vz * s0) * outer);
         float i1x = (float)(cx + (ux * c1 + vx * s1) * inner);
         float i1y = (float)(cy + (uy * c1 + vy * s1) * inner);
         float i1z = (float)(cz + (uz * c1 + vz * s1) * inner);
         float o1x = (float)(cx + (ux * c1 + vx * s1) * outer);
         float o1y = (float)(cy + (uy * c1 + vy * s1) * outer);
         float o1z = (float)(cz + (uz * c1 + vz * s1) * outer);
         vertex(out, matrix, red, green, blue, alpha, i0x, i0y, i0z);
         vertex(out, matrix, red, green, blue, alpha, o0x, o0y, o0z);
         vertex(out, matrix, red, green, blue, alpha, o1x, o1y, o1z);
         vertex(out, matrix, red, green, blue, alpha, i1x, i1y, i1z);
         vertex(out, matrix, red, green, blue, alpha, i1x, i1y, i1z);
         vertex(out, matrix, red, green, blue, alpha, o1x, o1y, o1z);
         vertex(out, matrix, red, green, blue, alpha, o0x, o0y, o0z);
         vertex(out, matrix, red, green, blue, alpha, i0x, i0y, i0z);
      }
   }

   private static void drawSpike(Matrix4f matrix, class_4588 out, double cx, double cy, double cz,
                                 double ux, double uy, double uz, double vx, double vy, double vz,
                                 double theta, double start, double end, double halfWidth, int rgb, float alpha) {
      float red = (rgb >> 16 & 255) / 255.0F;
      float green = (rgb >> 8 & 255) / 255.0F;
      float blue = (rgb & 255) / 255.0F;
      double ct = Math.cos(theta), st = Math.sin(theta);
      double tx = -st * ux + ct * vx;
      double ty = -st * uy + ct * vy;
      double tz = -st * uz + ct * vz;
      double rx = ct * ux + st * vx;
      double ry = ct * uy + st * vy;
      double rz = ct * uz + st * vz;
      float ax = (float)(cx + rx * start - tx * halfWidth);
      float ay = (float)(cy + ry * start - ty * halfWidth);
      float az = (float)(cz + rz * start - tz * halfWidth);
      float bx = (float)(cx + rx * end - tx * halfWidth * 0.2);
      float by = (float)(cy + ry * end - ty * halfWidth * 0.2);
      float bz = (float)(cz + rz * end - tz * halfWidth * 0.2);
      float cx2 = (float)(cx + rx * end + tx * halfWidth * 0.2);
      float cy2 = (float)(cy + ry * end + ty * halfWidth * 0.2);
      float cz2 = (float)(cz + rz * end + tz * halfWidth * 0.2);
      float dx = (float)(cx + rx * start + tx * halfWidth);
      float dy = (float)(cy + ry * start + ty * halfWidth);
      float dz = (float)(cz + rz * start + tz * halfWidth);
      vertex(out, matrix, red, green, blue, alpha, ax, ay, az);
      vertex(out, matrix, red, green, blue, alpha, bx, by, bz);
      vertex(out, matrix, red, green, blue, alpha, cx2, cy2, cz2);
      vertex(out, matrix, red, green, blue, alpha, dx, dy, dz);
      vertex(out, matrix, red, green, blue, alpha, dx, dy, dz);
      vertex(out, matrix, red, green, blue, alpha, cx2, cy2, cz2);
      vertex(out, matrix, red, green, blue, alpha, bx, by, bz);
      vertex(out, matrix, red, green, blue, alpha, ax, ay, az);
   }

   private static void emitQuad(class_4588 out, Matrix4f matrix, float red, float green, float blue,
                                float alpha, float[] a, float[] b, float[] c, float[] d) {
      vertex(out, matrix, red, green, blue, alpha, a[0], a[1], a[2]);
      vertex(out, matrix, red, green, blue, alpha, b[0], b[1], b[2]);
      vertex(out, matrix, red, green, blue, alpha, c[0], c[1], c[2]);
      vertex(out, matrix, red, green, blue, alpha, d[0], d[1], d[2]);
   }

   private static void drawBlock(class_4588 out, Matrix4f matrix, double cx, double cy, double cz,
                                 double half, double yaw, int rgb, float alpha) {
      float red = (rgb >> 16 & 255) / 255.0F;
      float green = (rgb >> 8 & 255) / 255.0F;
      float blue = (rgb & 255) / 255.0F;
      double cosine = Math.cos(yaw), sine = Math.sin(yaw);
      for (int faceIndex = 0; faceIndex < 6; faceIndex++) {
         int[] face = FACES[faceIndex];
         float shade = SHADE[faceIndex];
         for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
            double dx = face[vertexIndex * 3] == 1 ? half : -half;
            double dy = face[vertexIndex * 3 + 1] == 1 ? half : -half;
            double dz = face[vertexIndex * 3 + 2] == 1 ? half : -half;
            float x = (float)(cx + dx * cosine - dz * sine);
            float y = (float)(cy + dy);
            float z = (float)(cz + dx * sine + dz * cosine);
            vertex(out, matrix, red * shade, green * shade, blue * shade, alpha, x, y, z);
         }
      }
   }

   private static void vertex(class_4588 out, Matrix4f matrix, float red, float green, float blue,
                              float alpha, float x, float y, float z) {
      out.method_22918(matrix, x, y, z).method_22915(red, green, blue, alpha);
   }
}
