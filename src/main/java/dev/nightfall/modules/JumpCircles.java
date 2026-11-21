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
import java.util.List;
import java.util.Random;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_1657;
import net.minecraft.class_2394;
import net.minecraft.class_2398;
import net.minecraft.class_243;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;

/**
 * JumpCircles: Beim Aufkommen (Landen) schlägt eine Aufprall-Animation um deine Füße ein:
 * Ein Ring aus drehenden Blöcken bricht ripple-artig aus dem Boden, schießt mit Überschwung nach
 * außen, springt wellenförmig hoch und runter und sinkt wieder weg. Dazu fliegen Trümmer-Würfel
 * in Bögen weg, und Schockwellen laufen über den Boden. Je höher der Fall, desto stärker der Aufprall.
 *
 * Alles in einer Datei: Modul, Landeerkennung und Renderer. Der Renderer registriert sich beim
 * Erzeugen des Moduls selbst (einmalig) für WorldRenderEvents.AFTER_ENTITIES; in Nightfall.java und
 * ModuleManager.java ist nichts weiter nötig (JumpCircles ist dort schon registriert).
 */
public final class JumpCircles extends Module {
   private static final int MAX_RINGS = 6;
   private static final double TWO_PI = Math.PI * 2.0;

   // Flächen eines Würfels als (sx, y, sz)-Ecken (0 = -half, 1 = +half), gleiche Windung wie HatsRenderer.
   private static final int[][] FACES = {
      {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0}, // -X
      {1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1}, // +X
      {0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1}, // unten
      {0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0}, // oben
      {0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0}, // -Z
      {1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1}  // +Z
   };
   private static final float[] SHADE = {0.75F, 0.75F, 0.55F, 1.0F, 0.85F, 0.85F};

   private static JumpCircles instance;
   private static boolean rendererRegistered;

   private final NumberSetting radius = this.add(new NumberSetting("Radius", 0.8, 0.3, 1.8, 0.1));
   private final NumberSetting blocks = this.add(new NumberSetting("Blöcke", 16.0, 6.0, 40.0, 1.0));
   private final NumberSetting blockSize = this.add(new NumberSetting("Blockgröße", 0.12, 0.05, 0.3, 0.01));
   private final NumberSetting amplitude = this.add(new NumberSetting("Wellenhöhe", 0.5, 0.1, 1.2, 0.05));
   private final NumberSetting waves = this.add(new NumberSetting("Wellen", 2.0, 1.0, 4.0, 1.0));
   private final NumberSetting layers = this.add(new NumberSetting("Ringe", 2.0, 1.0, 3.0, 1.0));
   private final NumberSetting duration = this.add(new NumberSetting("Dauer (Sek.)", 1.6, 0.5, 3.0, 0.1));
   private final ModeSetting colorMode = this.add(new ModeSetting("Farbmodus", "Verlauf", "Verlauf", "Einfarbig", "Regenbogen"));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 11955455));
   private final ColorSetting color2 = this.add(new ColorSetting("Farbe 2", 3530960));
   private final BoolSetting debris = this.add(new BoolSetting("Trümmer", true));
   private final BoolSetting groundRing = this.add(new BoolSetting("Bodenring + Schockwelle", true));
   private final BoolSetting fallScaling = this.add(new BoolSetting("Fallhöhe skaliert", true));
   private final ModeSetting particles = this.add(new ModeSetting("Partikel", "Aus", "Aus", "Cloud", "Enchant", "Soul", "Flamme", "Herzen"));

   private final List<Ring> rings = new ArrayList<>();
   private boolean wasOnGround = true;
   private int airTicks;
   private double peakY;

   /** Ein ausgelöster Aufprall: Position, Startzeit, Stärke (aus der Fallhöhe) und Trümmer-Daten. */
   private static final class Ring {
      final double x;
      final double y;
      final double z;
      final long startNanos;
      final double scale;
      final int debrisCount;
      final float[] dAngle;
      final float[] dSpeed;
      final float[] dVy;
      final float[] dSize;
      final float[] dSpin;

      Ring(double x, double y, double z, long startNanos, double scale) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.startNanos = startNanos;
         this.scale = scale;
         this.debrisCount = 12 + (int)(scale * 8.0);
         this.dAngle = new float[this.debrisCount];
         this.dSpeed = new float[this.debrisCount];
         this.dVy = new float[this.debrisCount];
         this.dSize = new float[this.debrisCount];
         this.dSpin = new float[this.debrisCount];
         Random random = new Random(startNanos);
         for (int i = 0; i < this.debrisCount; i++) {
            this.dAngle[i] = (float)(random.nextDouble() * TWO_PI);
            this.dSpeed[i] = (float)((0.9 + random.nextDouble() * 1.6) * scale);
            this.dVy[i] = (float)((2.0 + random.nextDouble() * 2.2) * (0.8 + 0.3 * scale));
            this.dSize[i] = 0.025F + random.nextFloat() * 0.04F;
            this.dSpin[i] = (random.nextFloat() - 0.5F) * 14.0F;
         }
      }
   }

   public JumpCircles() {
      super("JumpCircles", "Aufprall-Animation beim Landen: Ring aus Blöcken, Trümmer und Schockwelle (nur lokal).", Category.VISUALS);
      instance = this;
      if (!rendererRegistered) {
         rendererRegistered = true;
         WorldRenderEvents.AFTER_ENTITIES.register(JumpCircles::renderWorld);
      }
   }

   // ------------------------------------------------------------------ Lifecycle

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
      this.rings.clear();
      this.wasOnGround = true;
      this.airTicks = 0;
      this.peakY = 0.0;
   }

   // ------------------------------------------------------------------ Landeerkennung

   @Override
   public void onTick() {
      class_1657 player = mc.field_1724;
      if (player == null || mc.field_1687 == null) {
         this.reset();
         return;
      }

      long now = System.nanoTime();
      long lifetime = (long)(this.duration.get() * 1.0E9);
      this.rings.removeIf(ring -> now - ring.startNanos > lifetime);

      boolean onGround = player.method_24828();
      double y = player.method_23318();

      if (!onGround) {
         if (this.wasOnGround) {
            this.peakY = y;
            this.airTicks = 0;
         } else {
            this.peakY = Math.max(this.peakY, y);
            this.airTicks++;
         }
      } else if (!this.wasOnGround && this.airTicks >= 3) {
         // Aufkommen: kurze Hüpfer (Stufen, Treppen) ignorieren.
         double drop = Math.max(0.0, this.peakY - y);
         double scale = this.fallScaling.get() ? Math.max(0.75, Math.min(1.7, 0.75 + drop * 0.2)) : 1.0;
         this.spawn(player.method_23317(), y, player.method_23321(), scale);
         this.airTicks = 0;
      }

      this.wasOnGround = onGround;
   }

   private void spawn(double x, double y, double z, double scale) {
      if (this.rings.size() >= MAX_RINGS) {
         this.rings.remove(0);
      }
      this.rings.add(new Ring(x, y, z, System.nanoTime(), scale));
      this.spawnParticles(x, y, z, scale);
   }

   private void spawnParticles(double x, double y, double z, double scale) {
      String mode = this.particles.get();
      if ("Aus".equals(mode)) {
         return;
      }

      class_2394 particle = switch (mode) {
         case "Enchant" -> class_2398.field_11215;
         case "Soul" -> class_2398.field_23114;
         case "Flamme" -> class_2398.field_11240;
         case "Herzen" -> class_2398.field_11201;
         default -> class_2398.field_11204;
      };

      for (int i = 0; i < 18; i++) {
         double angle = TWO_PI * i / 18.0;
         double r = this.radius.get() * scale;
         mc.field_1713.method_3056(particle, x + Math.cos(angle) * r, y + 0.08, z + Math.sin(angle) * r, Math.cos(angle) * 0.025, 0.025, Math.sin(angle) * 0.025);
      }
   }

   // ------------------------------------------------------------------ Rendering

   private static double easeOutBack(double x) {
      double c1 = 1.70158;
      double c3 = c1 + 1.0;
      double t = x - 1.0;
      return 1.0 + c3 * t * t * t + c1 * t * t;
   }

   private int blockColor(int index, int count, double angle, long now) {
      String mode = this.colorMode.get();
      if ("Regenbogen".equals(mode)) {
         float hue = (float)(((double)index / count + now * 2.5E-10) % 1.0);
         return Color.HSBtoRGB(hue, 0.75F, 1.0F) & 0xFFFFFF;
      }
      if ("Einfarbig".equals(mode)) {
         return this.color.rgb();
      }
      float mix = (float)(0.5 + 0.5 * Math.sin(angle * 2.0));
      return Theme.lerp(this.color.rgb(), this.color2.rgb(), mix);
   }

   private static void renderWorld(WorldRenderContext context) {
      JumpCircles m = instance;
      if (m == null || !m.isEnabled() || m.rings.isEmpty()) {
         return;
      }
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) {
         return;
      }

      class_4184 camera = mc.field_1773.method_19418();
      if (camera == null) return;
      class_243 cameraPos = camera.method_71156();
      class_4587 matrices = context.matrices();
      class_4588 fills = context.consumers().method_73477(class_12249.method_76019());

      long now = System.nanoTime();
      double durationSec = m.duration.get();
      double lifetime = durationSec * 1.0E9;
      int layerCount = m.layers.getInt();
      double amp = m.amplitude.get();
      int baseBlocks = m.blocks.getInt();
      int waveCount = m.waves.getInt();
      double baseRadius = m.radius.get();
      double blockSize = m.blockSize.get();
      boolean ground = m.groundRing.get();
      boolean debris = m.debris.get();
      int primary = m.color.rgb();

      matrices.method_22903();
      matrices.method_22904(-cameraPos.field_1352, -cameraPos.field_1351, -cameraPos.field_1350);
      Matrix4f matrix = matrices.method_23760().method_23761();
      try {
         for (Ring ring : m.rings) {
            double age = (now - ring.startNanos) / lifetime;
            if (age < 0.0 || age >= 1.0) {
               continue;
            }

            for (int layer = 0; layer < layerCount; layer++) {
               double delay = layer * 0.10;
               double la = (age - delay) / (1.0 - delay);
               if (la <= 0.0 || la >= 1.0) {
                  continue;
               }

               double out = easeOutBack(Math.min(1.0, la * 1.5));
               double ringRadius = baseRadius * ring.scale * (1.0 + 0.6 * layer) * (0.15 + 0.85 * out);
               int n = baseBlocks + layer * 6;
               float alpha = (float)(0.95 * Math.min(1.0, (1.0 - la) * 3.0));
               double dir = layer % 2 == 0 ? 1.0 : -1.0;
               double spin = la * 1.4 * dir;
               double phase = la * durationSec * TWO_PI * 0.9;

               if (ground) {
                  drawBand(matrix, fills, ring.x, ring.y + 0.02, ring.z, ringRadius, 0.035 * (1.0 - la) + 0.01, primary, alpha * 0.8F);
                  if (layer == 0) {
                     // Schockwelle: dünner heller Ring, läuft schnell nach außen und wird dabei schmaler.
                     double shock = baseRadius * ring.scale * (0.5 + 1.8 * (1.0 - Math.pow(1.0 - la, 2.0)));
                     drawBand(matrix, fills, ring.x, ring.y + 0.025, ring.z, shock, 0.03 * (1.0 - la) + 0.003, Theme.lerp(primary, 0xFFFFFF, 0.55F), alpha);
                  }
               }

               for (int i = 0; i < n; i++) {
                  double angle = TWO_PI * i / n + spin;
                  // Ripple: die Blöcke brechen nacheinander rund um den Ring aus dem Boden.
                  double stagger = 0.20 * i / n;
                  double bt = Math.max(0.0, Math.min(1.0, (la - stagger) / 0.75));
                  if (bt <= 0.0) {
                     continue;
                  }

                  double bounce = 4.0 * bt * (1.0 - bt);
                  double wave = 0.5 + 0.5 * Math.sin(phase - angle * waveCount);
                  double lift = amp * ring.scale * bounce * (0.55 + 0.45 * wave);
                  double half = blockSize * Math.min(1.0, Math.min(bt * 6.0, (1.0 - la) * 4.0));
                  if (half <= 0.002) {
                     continue;
                  }

                  double x = ring.x + Math.cos(angle) * ringRadius;
                  double z = ring.z + Math.sin(angle) * ringRadius;
                  double y = ring.y + half + lift;
                  double yaw = i * 0.7 + la * 5.0 * dir;

                  int rgb = m.blockColor(i, n, angle, now);
                  // Je höher der Block gerade ist, desto heller leuchtet er.
                  float glow = (float)Math.min(1.0, lift / (amp * ring.scale + 1.0E-6)) * 0.35F;
                  rgb = Theme.lerp(rgb, 0xFFFFFF, glow);
                  drawBlock(fills, matrix, x, y, z, half, yaw, rgb, alpha);
               }
            }

            if (debris) {
               double t = age * durationSec;
               float alpha = (float)(0.95 * Math.min(1.0, (1.0 - age) * 3.0));
               double shrink = Math.min(1.0, (1.0 - age) * 3.0);
               for (int i = 0; i < ring.debrisCount; i++) {
                  double hr = ring.dSpeed[i] * t * (1.0 - 0.25 * t);
                  double dy = ring.dVy[i] * t - 4.9 * t * t;
                  if (dy < 0.0) {
                     continue;
                  }
                  double x = ring.x + Math.cos(ring.dAngle[i]) * (0.15 + hr);
                  double z = ring.z + Math.sin(ring.dAngle[i]) * (0.15 + hr);
                  double half = ring.dSize[i] * shrink;
                  int rgb = Theme.lerp(m.blockColor(i, ring.debrisCount, ring.dAngle[i], now), 0xFFFFFF, 0.3F);
                  drawBlock(fills, matrix, x, ring.y + 0.05 + dy, z, half, ring.dSpin[i] * t, rgb, alpha);
               }
            }
         }
      } finally {
         matrices.method_22909();
      }
   }

   /** Flaches Band (Ring) auf dem Boden, beide Windungen, damit es von oben und unten sichtbar ist. */
   private static void drawBand(
      Matrix4f matrix, class_4588 consumer, double cx, double cy, double cz, double radius, double halfWidth, int rgb, float alpha
   ) {
      float red = (rgb >> 16 & 0xFF) / 255.0F;
      float green = (rgb >> 8 & 0xFF) / 255.0F;
      float blue = (rgb & 0xFF) / 255.0F;
      double inner = Math.max(0.01, radius - halfWidth);
      double outer = radius + halfWidth;
      int segments = Math.max(24, Math.min(96, (int)(radius * 40.0)));
      float y = (float)cy;

      for (int i = 0; i < segments; i++) {
         double a0 = TWO_PI * i / segments;
         double a1 = TWO_PI * (i + 1) / segments;
         float ix0 = (float)(cx + Math.cos(a0) * inner);
         float iz0 = (float)(cz + Math.sin(a0) * inner);
         float ox0 = (float)(cx + Math.cos(a0) * outer);
         float oz0 = (float)(cz + Math.sin(a0) * outer);
         float ox1 = (float)(cx + Math.cos(a1) * outer);
         float oz1 = (float)(cz + Math.sin(a1) * outer);
         float ix1 = (float)(cx + Math.cos(a1) * inner);
         float iz1 = (float)(cz + Math.sin(a1) * inner);
         vertex(consumer, matrix, red, green, blue, alpha, ix0, y, iz0);
         vertex(consumer, matrix, red, green, blue, alpha, ox0, y, oz0);
         vertex(consumer, matrix, red, green, blue, alpha, ox1, y, oz1);
         vertex(consumer, matrix, red, green, blue, alpha, ix1, y, iz1);
         vertex(consumer, matrix, red, green, blue, alpha, ix1, y, iz1);
         vertex(consumer, matrix, red, green, blue, alpha, ox1, y, oz1);
         vertex(consumer, matrix, red, green, blue, alpha, ox0, y, oz0);
         vertex(consumer, matrix, red, green, blue, alpha, ix0, y, iz0);
      }
   }

   /** Um die Y-Achse gedrehter Würfel mit Flächen-Schattierung (Oberseite hell, Unterseite dunkel). */
   private static void drawBlock(class_4588 consumer, Matrix4f matrix, double cx, double cy, double cz, double half, double yaw, int rgb, float alpha) {
      float red = (rgb >> 16 & 0xFF) / 255.0F;
      float green = (rgb >> 8 & 0xFF) / 255.0F;
      float blue = (rgb & 0xFF) / 255.0F;
      double cs = Math.cos(yaw);
      double sn = Math.sin(yaw);

      for (int f = 0; f < 6; f++) {
         int[] face = FACES[f];
         float shade = SHADE[f];
         for (int v = 0; v < 4; v++) {
            double dx = face[v * 3] == 1 ? half : -half;
            double dy = face[v * 3 + 1] == 1 ? half : -half;
            double dz = face[v * 3 + 2] == 1 ? half : -half;
            float px = (float)(cx + dx * cs - dz * sn);
            float py = (float)(cy + dy);
            float pz = (float)(cz + dx * sn + dz * cs);
            vertex(consumer, matrix, red * shade, green * shade, blue * shade, alpha, px, py, pz);
         }
      }
   }

   private static void vertex(class_4588 consumer, Matrix4f matrix, float red, float green, float blue, float alpha, float x, float y, float z) {
      consumer.method_22918(matrix, x, y, z).method_22915(red, green, blue, alpha);
   }
}