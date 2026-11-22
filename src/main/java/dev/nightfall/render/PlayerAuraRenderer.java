package dev.nightfall.render;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.PlayerAura;
import dev.nightfall.util.Projection;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_243;
import net.minecraft.class_310;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;

/**
 * PlayerAura-Renderer (neu).
 *
 * Warum die alte Version ruckelte: Sie las die Spielerposition nur 20-mal pro Sekunde (Tick-Position),
 * die Kamera wird aber pro Frame interpoliert. Hier wird die Position wie bei Minecrafts Entity-Rendering
 * pro Frame zwischen letzter und aktueller Tick-Position interpoliert (lastRender -> aktuell).
 * Dazu kommt ein weicher Bodenwert, eine geglättete Bewegungsgeschwindigkeit (Flammen lehnen sich zurück)
 * und keine Objekt-Allokationen pro Quad mehr.
 */
public final class PlayerAuraRenderer {
   private static final double TWO_PI = Math.PI * 2.0;

   // ---- Frame-zu-Frame-Zustand
   private static boolean started;
   private static long lastFrameNanos;
   private static long lastTickCount;
   private static float lastHud = -1.0F;
   private static double ownDelta;
   private static double lastX;
   private static double lastZ;
   private static double velX;
   private static double velZ;
   private static double groundSmooth;
   private static boolean groundInit;

   // ---- Frame-Parameter (nur Render-Thread)
   private static class_4588 out;
   private static Matrix4f mat;
   private static PlayerAura aura;
   private static double time;
   private static double spd;
   private static double scl;
   private static double alp;
   private static double boost;
   private static double px;
   private static double py;
   private static double pz;
   private static double gy;
   private static double leanX;
   private static double leanZ;
   private static int segs;
   private static int bladeSegs;
   private static int orbLat;
   private static int orbLon;
   private static boolean halo;

   // ---- wiederverwendeter Quad-Puffer (keine Allokation pro Quad)
   private static final double[] QX = new double[4];
   private static final double[] QY = new double[4];
   private static final double[] QZ = new double[4];
   private static final int[] QC = new int[4];
   private static final float[] QA = new float[4];
   private static final double[] BX = new double[16];
   private static final double[] BY = new double[16];
   private static final double[] BZ = new double[16];
   private static final double[] BW = new double[16];

   private PlayerAuraRenderer() {
   }

   public static void register() {
      WorldRenderEvents.AFTER_ENTITIES.register(PlayerAuraRenderer::render);
   }

   // ================================================================== Einstieg

   private static void render(WorldRenderContext context) {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) {
         started = false;
         return;
      }

      PlayerAura module;
      try {
         module = ModuleManager.get(PlayerAura.class);
      } catch (Throwable ignored) {
         return;
      }
      if (!module.isEnabled()) {
         started = false;
         groundInit = false;
         return;
      }

      boolean firstPerson = mc.field_1690.method_31044().method_31034();
      if (firstPerson && !module.firstPerson()) {
         return;
      }

      long now = System.nanoTime();
      // Vor frameDelta() lesen: frameDelta aktualisiert lastFrameNanos auf diesen Frame.
      double dtSeconds = started
         ? Math.max(0.0005, Math.min(0.25, (now - lastFrameNanos) / 1.0E9))
         : 1.0 / 60.0;
      double delta = frameDelta(module, now);

      // Interpolierte Spielerposition (wie Minecrafts Entity-Rendering).
      var player = mc.field_1724;
      double cx = player.method_23317();
      double cy = player.method_23318();
      double cz = player.method_23321();
      double ix = player.field_6038 + (cx - player.field_6038) * delta;
      double iy = player.field_5971 + (cy - player.field_5971) * delta;
      double iz = player.field_5989 + (cz - player.field_5989) * delta;

      // Geglättete Geschwindigkeit (Blöcke/Sekunde) für die Neigung der Flammen.
      if (started) {
         double rawX = (ix - lastX) / dtSeconds;
         double rawZ = (iz - lastZ) / dtSeconds;
         if (Math.abs(rawX) > 40.0 || Math.abs(rawZ) > 40.0) {
            rawX = 0.0;
            rawZ = 0.0;
         }
         double k = 1.0 - Math.exp(-dtSeconds * 9.0);
         velX += (rawX - velX) * k;
         velZ += (rawZ - velZ) * k;
      } else {
         velX = 0.0;
         velZ = 0.0;
      }
      lastX = ix;
      lastZ = iz;

      // Weicher Bodenwert (Sigil bleibt beim Springen am Boden, Treppen ruckeln nicht).
      double groundTarget = module.groundValid() ? module.groundTarget() : iy;
      if (!groundInit) {
         groundSmooth = groundTarget;
         groundInit = true;
      } else {
         groundSmooth += (groundTarget - groundSmooth) * (1.0 - Math.exp(-dtSeconds * 18.0));
      }

      // Einblend-Animation
      double elapsed = Math.max(0.0, (now - module.activatedAtNanos()) / 1.0E9);
      double appear = clamp(elapsed / 0.9, 0.0, 1.0);
      double ease = 1.0 + 2.70158 * Math.pow(appear - 1.0, 3.0) + 1.70158 * Math.pow(appear - 1.0, 2.0);
      double scale = module.scale() * ease;
      double alpha = module.intensity() * Math.min(1.0, appear * 3.0) * (firstPerson ? module.firstPersonOpacity() : 1.0);
      if (alpha < 0.01 || scale < 0.01) {
         return;
      }

      module.refreshPalette();
      int quality = module.qualityLevel();
      segs = quality == 0 ? 20 : quality == 1 ? 32 : 48;
      bladeSegs = quality == 0 ? 5 : quality == 1 ? 7 : 9;
      orbLat = quality == 0 ? 3 : quality == 1 ? 4 : 5;
      orbLon = quality == 0 ? 5 : quality == 1 ? 7 : 9;
      halo = quality > 0;

      aura = module;
      time = (now % 3_600_000_000_000L) / 1.0E9;
      spd = module.speed();
      scl = scale;
      alp = alpha;
      boost = module.power();
      px = ix;
      py = iy;
      pz = iz;
      gy = groundSmooth + 0.03;
      double leanScale = module.lean() * 0.16;
      leanX = -velX * leanScale;
      leanZ = -velZ * leanScale;
      double leanLen = Math.sqrt(leanX * leanX + leanZ * leanZ);
      if (leanLen > 1.0) {
         leanX /= leanLen;
         leanZ /= leanLen;
      }

      class_4184 camera = mc.field_1773.method_19418();
      if (camera == null) {
         return;
      }
      class_243 cameraPos = camera.method_71156();
      class_4587 matrices = context.matrices();
      out = context.consumers().method_73477(class_12249.method_76019());
      matrices.method_22903();
      matrices.method_22904(-cameraPos.field_1352, -cameraPos.field_1351, -cameraPos.field_1350);
      try {
         mat = matrices.method_23760().method_23761();
         if (module.groundGlowEnabled()) drawGroundGlow();
         if (module.sigilEnabled()) drawSigil();
         if (module.pulseEnabled()) drawPulse();
         if (module.pillarEnabled()) drawPillar();
         if (module.flameCount() > 0) drawBlades();
         if (module.ringCount() > 0) drawEnergyRings();
         if (module.orbCount() > 0) drawOrbs();
         if (module.sparkCount() > 0) drawSparks();
      } finally {
         matrices.method_22909();
         out = null;
         aura = null;
      }
   }

   /**
    * Tick-Fortschritt dieses Frames (0..1). Minecrafts Wert für den aktuellen Frame ist erst nach dem HUD
    * verfügbar; Projection.tickDelta() ist der exakte Wert des Vorframes. Wir rechnen ihn mit der
    * vergangenen Zeit und den inzwischen gelaufenen Ticks auf diesen Frame weiter.
    */
   private static double frameDelta(PlayerAura module, long now) {
      float hud = Projection.tickDelta();
      long ticks = module.tickCount();
      double d;
      if (!started) {
         d = hud;
      } else {
         double dt = (now - lastFrameNanos) / 5.0E7;
         long ran = ticks - lastTickCount;
         d = (hud != lastHud ? hud : ownDelta) + dt - ran;
      }
      d = clamp(d, 0.0, 1.0);
      ownDelta = d;
      lastHud = hud;
      lastFrameNanos = now;
      lastTickCount = ticks;
      started = true;
      return d;
   }

   // ================================================================== Flammenzungen

   private static void drawBlades() {
      int n = aura.flameCount();
      double height = aura.flameHeight() * scl;
      for (int pass = 0; pass < 2; pass++) {
         int count = pass == 0 ? n : n / 2;
         double heightScale = pass == 0 ? 1.0 : 0.62;
         double radiusScale = pass == 0 ? 1.0 : 0.6;
         double spin = (pass == 0 ? 0.55 : -0.9) * spd;
         for (int b = 0; b < count; b++) {
            double h1 = frac(Math.sin(b * 12.9898 + pass * 78.233) * 43758.5453);
            double h2 = frac(Math.sin(b * 4.1414 + pass * 9.17) * 9999.77);
            double ang = TWO_PI * b / count + time * spin + h1 * 0.4;
            double rx = Math.cos(ang);
            double rz = Math.sin(ang);
            double tx = -rz;
            double tz = rx;
            double baseR = (0.38 + 0.20 * h2) * scl * radiusScale;
            double bladeH = height * heightScale * (0.62 + 0.38 * h1) * (0.92 + 0.08 * Math.sin(time * (5.0 + 3.0 * h2) * spd + b));
            double halfW = (0.27 * scl) * (0.7 + 0.5 * h2) * (pass == 0 ? 1.0 : 0.8);

            for (int k = 0; k <= bladeSegs; k++) {
               double u = (double)k / bladeSegs;
               double r = baseR * (1.0 - 0.5 * u);
               double sway = Math.sin(time * 3.1 * spd + b * 1.9 + u * 5.5) * 0.11 * scl * u;
               BX[k] = px + rx * r + tx * sway + leanX * u * u * bladeH * 0.5;
               BY[k] = py + 0.05 + bladeH * u;
               BZ[k] = pz + rz * r + tz * sway + leanZ * u * u * bladeH * 0.5;
               BW[k] = halfW * Math.pow(1.0 - u, 0.75) * (1.0 + 0.25 * Math.sin(time * 6.0 + b + u * 8.0));
            }

            for (int layer = 0; layer < 3; layer++) {
               if (layer == 0 && !halo) continue;
               double widthMul = layer == 0 ? 1.9 : layer == 1 ? 1.0 : 0.30;
               double alphaMul = layer == 0 ? 0.40 : 1.0;
               for (int k = 0; k < bladeSegs; k++) {
                  double u0 = (double)k / bladeSegs;
                  double u1 = (double)(k + 1) / bladeSegs;
                  int c0 = bladeColor(layer, u0, h2);
                  int c1 = bladeColor(layer, u1, h2);
                  double a0 = alphaMul * Math.pow(1.0 - u0, 0.85) * alp;
                  double a1 = alphaMul * Math.pow(Math.max(0.0, 1.0 - u1), 0.85) * alp;
                  set(0, BX[k] - tx * BW[k] * widthMul, BY[k], BZ[k] - tz * BW[k] * widthMul, c0, a0);
                  set(1, BX[k] + tx * BW[k] * widthMul, BY[k], BZ[k] + tz * BW[k] * widthMul, c0, a0);
                  set(2, BX[k + 1] + tx * BW[k + 1] * widthMul, BY[k + 1], BZ[k + 1] + tz * BW[k + 1] * widthMul, c1, a1);
                  set(3, BX[k + 1] - tx * BW[k + 1] * widthMul, BY[k + 1], BZ[k + 1] - tz * BW[k + 1] * widthMul, c1, a1);
                  emit(true);
               }
            }
         }
      }
   }

   private static int bladeColor(int layer, double u, double h2) {
      int base = aura.color(0.1 + 0.8 * u + h2 * 0.1, time);
      if (layer == 2) return brighten(base, 0.55 + 0.4 * boost);
      if (layer == 1) return brighten(base, 0.10 * boost);
      return base;
   }

   // ================================================================== Leuchtsäule

   private static void drawPillar() {
      double height = aura.flameHeight() * scl * 0.95;
      double flicker = 0.88 + 0.12 * Math.sin(time * 7.0 * spd);
      double[] radii = {0.66, 0.50, 0.34};
      double[] layerAlpha = {0.50, 0.62, 0.78};
      for (int layer = 0; layer < 3; layer++) {
         double r = radii[layer] * scl * (1.0 + 0.03 * Math.sin(time * 3.0 * spd + layer));
         double rot = time * 0.4 * spd * (layer % 2 == 0 ? 1.0 : -1.0);
         int bottom = aura.color(layer * 0.1, time);
         int top = aura.color(1.0, time);
         for (int s = 0; s < segs; s++) {
            double a0 = TWO_PI * s / segs + rot;
            double a1 = TWO_PI * (s + 1) / segs + rot;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            double topR = r * 0.5;
            double lx = leanX * height * 0.5;
            double lz = leanZ * height * 0.5;
            set(0, px + c0 * r, py + 0.02, pz + s0 * r, bottom, alp * layerAlpha[layer] * flicker);
            set(1, px + c1 * r, py + 0.02, pz + s1 * r, bottom, alp * layerAlpha[layer] * flicker);
            set(2, px + c1 * topR + lx, py + height, pz + s1 * topR + lz, top, 0.0);
            set(3, px + c0 * topR + lx, py + height, pz + s0 * topR + lz, top, 0.0);
            emit(true);
         }
      }
   }

   // ================================================================== Bodenglanz

   private static void drawGroundGlow() {
      double radius = 1.5 * scl;
      int bands = 5;
      double y = gy + 0.03;
      double pulse = 0.88 + 0.12 * Math.sin(time * 3.2 * spd);
      for (int band = 0; band < bands; band++) {
         double f0 = (double)band / bands;
         double f1 = (double)(band + 1) / bands;
         double a0 = Math.pow(1.0 - f0, 1.7) * 0.9 * alp * pulse;
         double a1 = Math.pow(1.0 - f1, 1.7) * 0.9 * alp * pulse;
         int col0 = brighten(aura.color(f0, time), 0.35 * boost + 0.1 * (1.0 - f0));
         int col1 = aura.color(f1, time);
         double r0 = f0 * radius;
         double r1 = f1 * radius;
         for (int s = 0; s < segs; s++) {
            double ang0 = TWO_PI * s / segs;
            double ang1 = TWO_PI * (s + 1) / segs;
            set(0, px + Math.cos(ang0) * r0, y, pz + Math.sin(ang0) * r0, col0, a0);
            set(1, px + Math.cos(ang0) * r1, y, pz + Math.sin(ang0) * r1, col1, a1);
            set(2, px + Math.cos(ang1) * r1, y, pz + Math.sin(ang1) * r1, col1, a1);
            set(3, px + Math.cos(ang1) * r0, y, pz + Math.sin(ang1) * r0, col0, a0);
            emit(true);
         }
      }
   }

   // ================================================================== Bodensiegel

   private static void drawSigil() {
      double y = gy + 0.06;
      double pulse = 0.8 + 0.2 * Math.sin(time * 2.0 * spd);
      double a = 0.95 * alp * pulse;
      flatRing(y, 1.28 * scl, 0.07 * scl, a, 0.0);
      flatRing(y + 0.004, 1.52 * scl, 0.04 * scl, a * 0.9, 0.5);
      flatRing(y + 0.008, 0.82 * scl, 0.03 * scl, a * 0.8, 0.25);

      for (int i = 0; i < 24; i++) {
         double angle = i * TWO_PI / 24.0 + time * 0.25 * spd;
         double r0 = 1.34 * scl;
         double r1 = (i % 3 == 0 ? 1.47 : 1.43) * scl;
         double ca = Math.cos(angle);
         double sa = Math.sin(angle);
         flatSegment(px + ca * r0, pz + sa * r0, px + ca * r1, pz + sa * r1, (i % 3 == 0 ? 0.03 : 0.018) * scl, y + 0.012,
            aura.color(i / 24.0, time), a * 0.85);
      }

      for (int triangle = 0; triangle < 2; triangle++) {
         double rotation = time * 0.5 * spd * (triangle == 0 ? 1.0 : -1.0) + triangle * Math.PI / 3.0;
         double radius = 1.22 * scl;
         for (int edge = 0; edge < 3; edge++) {
            double a0 = rotation + edge * TWO_PI / 3.0;
            double a1 = rotation + (edge + 1) * TWO_PI / 3.0;
            flatSegment(px + Math.cos(a0) * radius, pz + Math.sin(a0) * radius,
               px + Math.cos(a1) * radius, pz + Math.sin(a1) * radius,
               0.032 * scl, y + 0.016, aura.color(triangle * 0.5 + edge / 6.0, time), a * 0.9);
         }
      }

      for (int i = 0; i < 6; i++) {
         double angle = i * Math.PI / 3.0 - time * 0.7 * spd;
         flatSegment(px + Math.cos(angle) * 0.17 * scl, pz + Math.sin(angle) * 0.17 * scl,
            px + Math.cos(angle) * 0.42 * scl, pz + Math.sin(angle) * 0.42 * scl,
            0.02 * scl, y + 0.02, 0xFFFFFF, a * 0.7);
      }
      flatRing(y + 0.024, 0.12 * scl, 0.03 * scl, a, 0.0);
   }

   private static void flatRing(double y, double radius, double width, double alpha, double colorOffset) {
      double inner = radius - width * 0.5;
      double outer = radius + width * 0.5;
      for (int s = 0; s < segs; s++) {
         double a0 = TWO_PI * s / segs;
         double a1 = TWO_PI * (s + 1) / segs;
         int c0 = aura.color(0.5 + 0.5 * Math.sin(a0 * 2.0 + time * 0.6 + colorOffset * TWO_PI), time);
         int c1 = aura.color(0.5 + 0.5 * Math.sin(a1 * 2.0 + time * 0.6 + colorOffset * TWO_PI), time);
         set(0, px + Math.cos(a0) * inner, y, pz + Math.sin(a0) * inner, c0, alpha);
         set(1, px + Math.cos(a0) * outer, y, pz + Math.sin(a0) * outer, c0, alpha);
         set(2, px + Math.cos(a1) * outer, y, pz + Math.sin(a1) * outer, c1, alpha);
         set(3, px + Math.cos(a1) * inner, y, pz + Math.sin(a1) * inner, c1, alpha);
         emit(true);
      }
   }

   private static void flatSegment(double x0, double z0, double x1, double z1, double width, double y, int rgb, double alpha) {
      double dx = x1 - x0;
      double dz = z1 - z0;
      double length = Math.sqrt(dx * dx + dz * dz);
      if (length < 1.0E-5) return;
      double nx = -dz / length * width;
      double nz = dx / length * width;
      set(0, x0 - nx, y, z0 - nz, rgb, alpha);
      set(1, x1 - nx, y, z1 - nz, rgb, alpha);
      set(2, x1 + nx, y, z1 + nz, rgb, alpha);
      set(3, x0 + nx, y, z0 + nz, rgb, alpha);
      emit(true);
   }

   // ================================================================== Druckwelle

   private static void drawPulse() {
      double period = 2.3 / spd;
      double phase = frac(time / period);
      double radius = (0.5 + phase * 2.8) * scl;
      double width = (0.14 * (1.0 - phase) + 0.02) * scl;
      double a = Math.pow(1.0 - phase, 1.3) * alp;
      double y = gy + 0.05;
      int rgb = brighten(aura.color(phase, time), 0.3 * boost);
      double inner = Math.max(0.01, radius - width);
      double outer = radius + width;
      double wall = 0.45 * scl * (1.0 - phase);
      for (int s = 0; s < segs * 2; s++) {
         double a0 = TWO_PI * s / (segs * 2);
         double a1 = TWO_PI * (s + 1) / (segs * 2);
         double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
         set(0, px + c0 * inner, y, pz + s0 * inner, rgb, a);
         set(1, px + c0 * outer, y, pz + s0 * outer, rgb, a);
         set(2, px + c1 * outer, y, pz + s1 * outer, rgb, a);
         set(3, px + c1 * inner, y, pz + s1 * inner, rgb, a);
         emit(true);
         // senkrechte Kante der Welle: unten kräftig, oben transparent
         set(0, px + c0 * radius, y, pz + s0 * radius, rgb, a * 0.85);
         set(1, px + c1 * radius, y, pz + s1 * radius, rgb, a * 0.85);
         set(2, px + c1 * radius, y + wall, pz + s1 * radius, rgb, 0.0);
         set(3, px + c0 * radius, y + wall, pz + s0 * radius, rgb, 0.0);
         emit(true);
      }
   }

   // ================================================================== Energieringe

   private static void drawEnergyRings() {
      double[] heights = {0.30, 0.95, 1.60, 2.15};
      double[] radii = {0.95, 1.10, 0.90, 0.75};
      double[] tilts = {0.35, 0.50, 0.30, 0.45};
      double[] dirs = {1.0, -1.0, 1.0, -1.0};
      int ringCount = Math.min(4, aura.ringCount());
      int segments = Math.min(96, segs * 2);

      for (int ring = 0; ring < ringCount; ring++) {
         double bob = 0.06 * Math.sin(time * 1.3 + ring * 2.0);
         double cx = px + leanX * heights[ring] * 0.5;
         double cy = py + heights[ring] * scl + bob;
         double cz = pz + leanZ * heights[ring] * 0.5;
         double radius = radii[ring] * scl;
         double tilt = tilts[ring] + 0.12 * Math.sin(time * 0.9 * spd + ring);
         double phi = time * 0.6 * spd * dirs[ring] + ring * 2.1;
         double nx = Math.sin(tilt) * Math.cos(phi);
         double ny = Math.cos(tilt);
         double nz = Math.sin(tilt) * Math.sin(phi);
         double hx = Math.abs(ny) < 0.99 ? 0.0 : 1.0;
         double hy = Math.abs(ny) < 0.99 ? 1.0 : 0.0;
         double hz = 0.0;
         double ux = ny * hz - nz * hy;
         double uy = nz * hx - nx * hz;
         double uz = nx * hy - ny * hx;
         double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
         ux /= ul;
         uy /= ul;
         uz /= ul;
         double vx = ny * uz - nz * uy;
         double vy = nz * ux - nx * uz;
         double vz = nx * uy - ny * ux;

         double head = time * 1.8 * spd * dirs[ring] + ring * 1.3;
         double band = 0.07 * scl;
         for (int i = 0; i < segments; i++) {
            double a0 = i * TWO_PI / segments;
            double a1 = (i + 1) * TWO_PI / segments;
            double amid = (a0 + a1) * 0.5;
            double behind = frac(dirs[ring] * (head - amid) / TWO_PI);
            double tail = Math.pow(1.0 - behind, 1.6);
            double a = (0.25 + 0.75 * tail) * alp;
            int rgb = aura.color(tail, time);
            if (tail > 0.8) rgb = brighten(rgb, (tail - 0.8) / 0.2 * (0.35 + 0.4 * boost));

            double r0x = Math.cos(a0) * radius, r0y = Math.sin(a0) * radius;
            double r1x = Math.cos(a1) * radius, r1y = Math.sin(a1) * radius;
            double p0x = cx + ux * r0x + vx * r0y, p0y = cy + uy * r0x + vy * r0y, p0z = cz + uz * r0x + vz * r0y;
            double p1x = cx + ux * r1x + vx * r1y, p1y = cy + uy * r1x + vy * r1y, p1z = cz + uz * r1x + vz * r1y;
            double thick = band * (0.6 + 0.8 * tail);

            if (halo) {
               bandQuad(p0x, p0y, p0z, p1x, p1y, p1z, nx, ny, nz, thick * 2.4, rgb, a * 0.28);
            }
            bandQuad(p0x, p0y, p0z, p1x, p1y, p1z, nx, ny, nz, thick, rgb, a * 0.95);
            bandQuad(p0x, p0y, p0z, p1x, p1y, p1z, nx, ny, nz, thick * 0.22, brighten(rgb, 0.6 + 0.35 * boost), a);
         }

         double hxp = cx + ux * Math.cos(head) * radius + vx * Math.sin(head) * radius;
         double hyp = cy + uy * Math.cos(head) * radius + vy * Math.sin(head) * radius;
         double hzp = cz + uz * Math.cos(head) * radius + vz * Math.sin(head) * radius;
         orb(hxp, hyp, hzp, 0.115 * scl, aura.color(1.0, time), alp);
      }
   }

   private static void bandQuad(double p0x, double p0y, double p0z, double p1x, double p1y, double p1z,
                                double nx, double ny, double nz, double thick, int rgb, double alpha) {
      set(0, p0x - nx * thick, p0y - ny * thick, p0z - nz * thick, rgb, alpha);
      set(1, p1x - nx * thick, p1y - ny * thick, p1z - nz * thick, rgb, alpha);
      set(2, p1x + nx * thick, p1y + ny * thick, p1z + nz * thick, rgb, alpha);
      set(3, p0x + nx * thick, p0y + ny * thick, p0z + nz * thick, rgb, alpha);
      emit(true);
   }

   // ================================================================== Lichtkugeln

   private static void drawOrbs() {
      int count = aura.orbCount();
      for (int i = 0; i < count; i++) {
         double baseAngle = i * TWO_PI / count;
         double rate = (1.1 + 0.18 * (i % 3)) * spd * (i % 2 == 0 ? 1.0 : -1.0);
         for (int trail = 3; trail >= 0; trail--) {
            double t = time - trail * 0.06;
            double angle = baseAngle + t * rate;
            double h = 0.35 + 1.35 * (0.5 + 0.5 * Math.sin(t * 0.9 * spd + i * 1.3));
            double radius = (1.0 + 0.15 * Math.sin(t * 1.4 * spd + i)) * scl;
            double ox = px + Math.cos(angle) * radius + leanX * h * 0.5;
            double oy = py + h * scl;
            double oz = pz + Math.sin(angle) * radius + leanZ * h * 0.5;
            double fade = Math.pow(1.0 - trail / 4.0, 1.4);
            double size = (0.10 * fade + 0.03) * scl;
            int rgb = aura.color(frac(i * 0.17 + 0.3), time);
            if (trail == 0) {
               orb(ox, oy, oz, size, brighten(rgb, 0.4 * boost), alp);
            } else {
               sphere(ox, oy, oz, size * 1.1, rgb, fade * alp * 0.55);
            }
         }
      }
   }

   /** Leuchtkugel: weiche Hülle, farbiger Körper, weißer Kern. */
   private static void orb(double x, double y, double z, double radius, int rgb, double alpha) {
      if (halo) sphere(x, y, z, radius * 2.1, rgb, alpha * 0.22);
      sphere(x, y, z, radius * 1.15, rgb, alpha * 0.85);
      sphere(x, y, z, radius * 0.55, 0xFFFFFF, alpha);
   }

   private static void sphere(double x, double y, double z, double radius, int rgb, double alpha) {
      if (alpha < 0.01 || radius < 0.002) return;
      for (int lat = 0; lat < orbLat; lat++) {
         double p0 = -Math.PI / 2.0 + Math.PI * lat / orbLat;
         double p1 = -Math.PI / 2.0 + Math.PI * (lat + 1) / orbLat;
         double cp0 = Math.cos(p0), sp0 = Math.sin(p0), cp1 = Math.cos(p1), sp1 = Math.sin(p1);
         for (int lon = 0; lon < orbLon; lon++) {
            double a0 = TWO_PI * lon / orbLon;
            double a1 = TWO_PI * (lon + 1) / orbLon;
            set(0, x + cp0 * Math.cos(a0) * radius, y + sp0 * radius, z + cp0 * Math.sin(a0) * radius, rgb, alpha);
            set(1, x + cp0 * Math.cos(a1) * radius, y + sp0 * radius, z + cp0 * Math.sin(a1) * radius, rgb, alpha);
            set(2, x + cp1 * Math.cos(a1) * radius, y + sp1 * radius, z + cp1 * Math.sin(a1) * radius, rgb, alpha);
            set(3, x + cp1 * Math.cos(a0) * radius, y + sp1 * radius, z + cp1 * Math.sin(a0) * radius, rgb, alpha);
            emit(false);
         }
      }
   }

   // ================================================================== Funken

   private static void drawSparks() {
      int count = aura.sparkCount();
      for (int k = 0; k < count; k++) {
         double seed = k * 7.13;
         double h1 = frac(Math.sin(seed) * 43758.5453);
         double h2 = frac(Math.sin(seed * 1.7) * 12345.678);
         double h3 = frac(Math.sin(seed * 3.1) * 999.1);
         double phase = frac(time * 0.42 * spd * (0.7 + 0.6 * h3) + h2);
         double angle = h1 * TWO_PI + phase * 2.6;
         double radius = (0.30 + 0.6 * h2 + 0.1 * Math.sin(phase * 6.0 + k)) * scl;
         double height = phase * 2.6 * scl;
         double a = Math.pow(Math.sin(phase * Math.PI), 1.2) * alp;
         if (a < 0.02) continue;

         double ex = px + Math.cos(angle) * radius + leanX * phase * height * 0.5;
         double ey = py + height;
         double ez = pz + Math.sin(angle) * radius + leanZ * phase * height * 0.5;
         double len = (0.22 + 0.18 * h1) * scl;
         double hw = (0.022 + 0.02 * h3) * scl;
         int tailRgb = aura.color(h3, time);
         int headRgb = brighten(tailRgb, 0.7);
         for (int plane = 0; plane < 2; plane++) {
            double dx = plane == 0 ? hw : 0.0;
            double dz = plane == 0 ? 0.0 : hw;
            set(0, ex - dx, ey - len, ez - dz, tailRgb, 0.0);
            set(1, ex + dx, ey - len, ez + dz, tailRgb, 0.0);
            set(2, ex + dx, ey, ez + dz, headRgb, a);
            set(3, ex - dx, ey, ez - dz, headRgb, a);
            emit(true);
         }
      }
   }

   // ================================================================== Quad-Helfer (ohne Allokation)

   private static void set(int i, double x, double y, double z, int rgb, double alpha) {
      QX[i] = x;
      QY[i] = y;
      QZ[i] = z;
      QC[i] = rgb;
      QA[i] = (float)clamp(alpha, 0.0, 1.0);
   }

   private static void emit(boolean bothSides) {
      for (int i = 0; i < 4; i++) put(i);
      if (bothSides) {
         for (int i = 3; i >= 0; i--) put(i);
      }
   }

   private static void put(int i) {
      int c = QC[i];
      out.method_22918(mat, (float)QX[i], (float)QY[i], (float)QZ[i]).method_22915(
         (c >> 16 & 255) / 255.0F, (c >> 8 & 255) / 255.0F, (c & 255) / 255.0F, QA[i]
      );
   }

   private static int brighten(int rgb, double amount) {
      double t = clamp(amount, 0.0, 1.0);
      int r = (int)Math.round((rgb >> 16 & 255) + (255 - (rgb >> 16 & 255)) * t);
      int g = (int)Math.round((rgb >> 8 & 255) + (255 - (rgb >> 8 & 255)) * t);
      int b = (int)Math.round((rgb & 255) + (255 - (rgb & 255)) * t);
      return r << 16 | g << 8 | b;
   }

   private static double frac(double value) {
      return value - Math.floor(value);
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }
}