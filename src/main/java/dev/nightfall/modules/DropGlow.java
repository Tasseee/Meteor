package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Projection;
import dev.nightfall.util.LootRarity;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.class_12249;
import net.minecraft.class_1297;
import net.minecraft.class_1306;
import net.minecraft.class_1542;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_7923;
import net.minecraft.class_243;
import net.minecraft.class_4184;
import net.minecraft.class_4587;
import net.minecraft.class_4588;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * DropGlow: kompletter Item-Glow aus einem Modul.
 *  - Gedroppte Items: leuchtende Hülle, rotierende Lichtsäule, Bodenglanz mit Ring und Ripple, aufsteigende Funken.
 *  - Items in der Hand: Leuchthülle, kreisender Kometenring und Funken um das gehaltene Item (Haupt- und Zweithand),
 *    in der 1. Person an der Handposition der Kamera, in der 3. Person an der Hand des Spielers, optional auch bei anderen Spielern.
 * Die Farbe richtet sich nach dem Item-Typ (stabil pro Item), Wertvolles wird größer und heller, und kurz vor
 * dem Despawn blinkt ein gedropptes Item rot.
 *
 * Alles in einer Datei (Modul + Renderer). Der Renderer registriert sich beim Erzeugen des Moduls selbst einmalig
 * für WorldRenderEvents.AFTER_ENTITIES. Einzige nötige Änderung:
 *   ModuleManager.java:  import dev.nightfall.modules.DropGlow;  +  MODULES.add(new DropGlow());
 */
public final class DropGlow extends Module {
   private static final double TWO_PI = Math.PI * 2.0;
   private static final int DESPAWN_TICKS = 6000;
   private static final String[] VALUABLE = {
      "diamond", "diamant", "netherite", "emerald", "smaragd", "totem", "elytra", "elytren", "shulker",
      "nether star", "netherstern", "beacon", "leuchtfeuer", "enchanted", "verzauber", "ancient debris",
      "antiker schutt", "dragon", "drachen", "trident", "dreizack", "golden apple", "goldener apfel", "goldapfel",
      "heart of the sea", "herz des meeres"
   };

   private static DropGlow instance;
   private static boolean rendererRegistered;

   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 40.0, 8.0, 64.0, 1.0));
   private final NumberSetting maxItems = this.add(new NumberSetting("Max. Items", 24.0, 4.0, 64.0, 1.0));
   private final NumberSetting size = this.add(new NumberSetting("Größe", 1.0, 0.5, 2.5, 0.05));
   private final NumberSetting brightness = this.add(new NumberSetting("Helligkeit", 1.0, 0.2, 1.0, 0.01));
   private final BoolSetting pulse = this.add(new BoolSetting("Pulsieren", true));
   private final BoolSetting hull = this.add(new BoolSetting("Leuchthülle", true));
   private final BoolSetting beam = this.add(new BoolSetting("Lichtsäule", true));
   private final NumberSetting beamHeight = this.add(new NumberSetting("Säulenhöhe", 2.0, 0.8, 4.0, 0.1));
   private final BoolSetting groundGlow = this.add(new BoolSetting("Bodenglanz + Ripple", true));
   private final NumberSetting sparks = this.add(new NumberSetting("Funken pro Item", 5.0, 0.0, 12.0, 1.0));
   private final BoolSetting highlightValuable = this.add(new BoolSetting("Wertvolles hervorheben", true));
   private final BoolSetting onlyValuable = this.add(new BoolSetting("Nur Wertvolles", false));
   private final BoolSetting despawnWarning = this.add(new BoolSetting("Despawn-Warnung (rot)", true));
   private final BoolSetting handGlow = this.add(new BoolSetting("Items in der Hand", true));
   private final BoolSetting handOffhand = this.add(new BoolSetting("Zweithand", true));
   private final BoolSetting handOthers = this.add(new BoolSetting("Auch andere Spieler", false));
   private final NumberSetting handSize = this.add(new NumberSetting("Hand-Größe", 1.0, 0.5, 2.0, 0.05));
   private final NumberSetting handFirstPersonOpacity = this.add(new NumberSetting("Deckkraft 1. Person", 0.7, 0.1, 1.0, 0.05));
   private final ModeSetting colorMode = this.add(new ModeSetting("Farbmodus", "Item-Typ", "Item-Typ", "Fortnite-Loot", "Verlauf", "Einfarbig", "Regenbogen"));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0x39FF14));
   private final ColorSetting color2 = this.add(new ColorSetting("Farbe 2", 0x2DE2FF));

   private static final class Entry {
      final class_1542 entity;
      final int nameHash;
      final String itemId;
      final boolean valuable;
      final double distSq;

      Entry(class_1542 entity, int nameHash, String itemId, boolean valuable, double distSq) {
         this.entity = entity;
         this.nameHash = nameHash;
         this.itemId = itemId;
         this.valuable = valuable;
         this.distSq = distSq;
      }
   }

   private final List<Entry> entries = new ArrayList<>();
   private long tickCount;
   private boolean started;
   private long lastFrameNanos;
   private long lastTickCount;
   private float lastHud = -1.0F;
   private double ownDelta;

   // ---- Zeichenpuffer (keine Allokation pro Quad)
   private static class_4588 out;
   private static Matrix4f mat;
   private static final double[] QX = new double[4];
   private static final double[] QY = new double[4];
   private static final double[] QZ = new double[4];
   private static final int[] QC = new int[4];
   private static final float[] QA = new float[4];

   public DropGlow() {
      super("DropGlow", "Item-Glow für gedroppte Items und Items in der Hand: Hülle, Lichtsäule, Bodenglanz, Kometenring, Funken, Despawn-Warnung (nur lokal).", Category.VISUALS);
      instance = this;
      if (!rendererRegistered) {
         rendererRegistered = true;
         WorldRenderEvents.AFTER_ENTITIES.register(DropGlow::renderWorld);
      }
   }

   // ------------------------------------------------------------------ Lifecycle / Item-Auswahl

   @Override
   protected void onEnable() {
      this.entries.clear();
      this.started = false;
   }

   @Override
   protected void onDisable() {
      this.entries.clear();
      this.started = false;
   }

   @Override
   public void onWorldLeave() {
      this.entries.clear();
      this.started = false;
   }

   /** Einmal pro Tick: nächste Items in Reichweite einsammeln (der Renderer zeichnet nur diese Auswahl). */
   @Override
   public void onTick() {
      this.tickCount++;
      this.entries.clear();
      if (mc.field_1687 == null || mc.field_1724 == null) {
         return;
      }

      double rangeSq = this.range.get() * this.range.get();
      boolean only = this.onlyValuable.get();
      List<Entry> found = new ArrayList<>();
      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (!(entity instanceof class_1542 item) || !item.method_5805()) {
            continue;
         }
         double distSq = item.method_5858(mc.field_1724);
         if (distSq > rangeSq) {
            continue;
         }
         class_1799 stack = item.method_6983();
         String name = stack.method_7964().getString();
         String itemId = class_7923.field_41178.method_10221(stack.method_7909()).toString();
         boolean valuable = isValuable(name);
         if (only && !valuable) {
            continue;
         }
         found.add(new Entry(item, name.hashCode(), itemId, valuable, distSq));
      }

      found.sort(Comparator.comparingDouble(e -> e.distSq));
      int limit = Math.min(found.size(), this.maxItems.getInt());
      for (int i = 0; i < limit; i++) {
         this.entries.add(found.get(i));
      }
   }

   private static boolean isValuable(String name) {
      String lower = name.toLowerCase();
      for (String keyword : VALUABLE) {
         if (lower.contains(keyword)) {
            return true;
         }
      }
      return false;
   }

   // ------------------------------------------------------------------ Tick-Fortschritt (wie bei PlayerAura/RunTrail)

   private double frameDelta(long now) {
      float hud = Projection.tickDelta();
      double d;
      if (!this.started) {
         d = hud;
      } else {
         double dt = (now - this.lastFrameNanos) / 5.0E7;
         long ran = this.tickCount - this.lastTickCount;
         d = (hud != this.lastHud ? hud : this.ownDelta) + dt - ran;
      }
      d = clamp(d, 0.0, 1.0);
      this.ownDelta = d;
      this.lastHud = hud;
      this.lastFrameNanos = now;
      this.lastTickCount = this.tickCount;
      this.started = true;
      return d;
   }

   // ------------------------------------------------------------------ Rendering

   private static void renderWorld(WorldRenderContext context) {
      DropGlow m = instance;
      if (m == null || !m.isEnabled()) {
         return;
      }
      if (mc.field_1687 == null || mc.field_1724 == null || context.matrices() == null || context.consumers() == null) {
         m.started = false;
         return;
      }

      long now = System.nanoTime();
      double delta = m.frameDelta(now);
      if (m.entries.isEmpty() && !m.handGlow.get()) {
         return;
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
         double time = (now % 3_600_000_000_000L) / 1.0E9;
         for (Entry entry : m.entries) {
            class_1542 e = entry.entity;
            if (!e.method_5805()) {
               continue;
            }
            double ix = e.field_6038 + (e.method_23317() - e.field_6038) * delta;
            double iy = e.field_5971 + (e.method_23318() - e.field_5971) * delta;
            double iz = e.field_5989 + (e.method_23321() - e.field_5989) * delta;
            double dx = ix - cameraPos.field_1352;
            double dy = iy - cameraPos.field_1351;
            double dz = iz - cameraPos.field_1350;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            m.drawItem(entry, ix, iy, iz, time, dist);
         }
         if (m.handGlow.get()) {
            m.drawHands(camera, cameraPos, delta, time);
         }
      } finally {
         matrices.method_22909();
         out = null;
      }
   }

   private int baseColor(int nameHash, String itemId, double time, double phase) {
      String mode = this.colorMode.get();
      if ("Fortnite-Loot".equals(mode)) {
         int rarityColor = LootRarity.color(itemId);
         return rarityColor >= 0 ? rarityColor : this.color.rgb();
      }
      if ("Regenbogen".equals(mode)) {
         return Color.HSBtoRGB((float)((time * 0.15 + phase * 0.3) % 1.0), 0.85F, 1.0F) & 0xFFFFFF;
      }
      if ("Verlauf".equals(mode)) {
         return mix(this.color.rgb(), this.color2.rgb(), 0.5 + 0.5 * Math.sin(time * 0.8 + phase));
      }
      if ("Einfarbig".equals(mode)) {
         return this.color.rgb();
      }
      // Item-Typ: stabile Farbe pro Itemname
      float hue = (nameHash & 0xFFFF) / 65536.0F;
      return Color.HSBtoRGB(hue, 0.85F, 1.0F) & 0xFFFFFF;
   }

   private void drawItem(Entry entry, double ix, double iy, double iz, double time, double dist) {
      class_1542 e = entry.entity;
      int lod = dist < 20.0 ? 0 : dist < 40.0 ? 1 : 2;
      boolean boosted = entry.valuable && this.highlightValuable.get();
      double s = this.size.get() * (boosted ? 1.35 : 1.0);
      double phase = e.method_5628() * 0.37;
      double pulseF = this.pulse.get() ? 0.85 + 0.15 * Math.sin(time * 3.0 + phase) : 1.0;

      int rgb = this.baseColor(entry.nameHash, entry.itemId, time, phase);
      double warn = 0.0;
      if (this.despawnWarning.get()) {
         int remaining = DESPAWN_TICKS - e.method_6985();
         if (remaining >= 0 && remaining < 600) {
            warn = 0.5 + 0.5 * Math.sin(time * 14.0);
            rgb = mix(rgb, 0xFF2020, warn * 0.8);
         }
      }
      double a = this.brightness.get() * pulseF * (boosted ? 1.0 : 0.85) * (1.0 + 0.25 * warn);

      double cx = ix;
      double cy = iy + 0.28 + 0.04 * Math.sin(time * 2.0 + phase);
      double cz = iz;

      if (this.groundGlow.get()) {
         this.drawGround(ix, iy + 0.015, iz, s, rgb, a, time, phase, boosted);
      }
      if (this.beam.get()) {
         double height = this.beamHeight.get() * s * (boosted ? 1.4 : 1.0) * (0.92 + 0.08 * pulseF);
         this.drawBeam(ix, iy + 0.02, iz, s, height, rgb, a, time, phase, lod);
      }
      if (this.hull.get() && lod == 0) {
         sphere(cx, cy, cz, 0.20 * s * pulseF, brighten(rgb, 0.35), a * 0.40);
         sphere(cx, cy, cz, 0.34 * s * pulseF, rgb, a * 0.20);
      }
      int sparkCount = this.sparks.getInt();
      if (sparkCount > 0 && lod == 0) {
         this.drawSparks(ix, iy, iz, s, rgb, a, time, e.method_5628(), sparkCount * (boosted ? 2 : 1));
      }
   }

   // ---------------- Lichtsäule: vier rotierende Ebenen mit hellem Kern

   private void drawBeam(double x, double y, double z, double s, double height, int rgb, double a, double time, double phase, int lod) {
      int planes = lod >= 2 ? 2 : 4;
      double base = time * 0.8 + phase;
      int coreRgb = brighten(rgb, 0.7);
      for (int k = 0; k < planes; k++) {
         double ang = base + k * Math.PI / planes * (k % 2 == 0 ? 1.0 : 1.0);
         double dx = Math.cos(ang);
         double dz = Math.sin(ang);
         boolean major = k % 2 == 0;
         double w0 = (major ? 0.12 : 0.08) * s;
         double w1 = 0.03 * s;
         double aBottom = a * (major ? 0.55 : 0.35);
         set(0, x - dx * w0, y, z - dz * w0, rgb, aBottom);
         set(1, x + dx * w0, y, z + dz * w0, rgb, aBottom);
         set(2, x + dx * w1, y + height, z + dz * w1, rgb, 0.0);
         set(3, x - dx * w1, y + height, z - dz * w1, rgb, 0.0);
         emit(true);
         if (major) {
            double c0 = 0.024 * s;
            set(0, x - dx * c0, y, z - dz * c0, coreRgb, a * 0.9);
            set(1, x + dx * c0, y, z + dz * c0, coreRgb, a * 0.9);
            set(2, x + dx * c0 * 0.3, y + height * 0.8, z + dz * c0 * 0.3, coreRgb, 0.0);
            set(3, x - dx * c0 * 0.3, y + height * 0.8, z - dz * c0 * 0.3, coreRgb, 0.0);
            emit(true);
         }
      }
   }

   // ---------------- Boden: weiche Scheibe, gestrichelter Ring, Ripple

   private void drawGround(double x, double y, double z, double s, int rgb, double a, double time, double phase, boolean boosted) {
      int segs = 20;
      double radius = 0.75 * s;
      int bands = 3;
      for (int band = 0; band < bands; band++) {
         double f0 = (double)band / bands;
         double f1 = (double)(band + 1) / bands;
         double a0 = Math.pow(1.0 - f0, 1.6) * 0.6 * a;
         double a1 = Math.pow(1.0 - f1, 1.6) * 0.6 * a;
         int c0 = brighten(rgb, 0.3 * (1.0 - f0));
         for (int i = 0; i < segs; i++) {
            double ang0 = TWO_PI * i / segs;
            double ang1 = TWO_PI * (i + 1) / segs;
            set(0, x + Math.cos(ang0) * f0 * radius, y, z + Math.sin(ang0) * f0 * radius, c0, a0);
            set(1, x + Math.cos(ang0) * f1 * radius, y, z + Math.sin(ang0) * f1 * radius, rgb, a1);
            set(2, x + Math.cos(ang1) * f1 * radius, y, z + Math.sin(ang1) * f1 * radius, rgb, a1);
            set(3, x + Math.cos(ang1) * f0 * radius, y, z + Math.sin(ang1) * f0 * radius, c0, a0);
            emit(true);
         }
      }

      // gestrichelter, rotierender Ring
      int dashes = boosted ? 16 : 12;
      double ringR = 0.58 * s;
      double spin = time * 0.9 + phase;
      for (int i = 0; i < dashes; i++) {
         double a0 = spin + TWO_PI * i / dashes;
         double a1 = a0 + TWO_PI / dashes * 0.55;
         double inner = ringR - 0.02 * s;
         double outer = ringR + 0.02 * s;
         set(0, x + Math.cos(a0) * inner, y + 0.004, z + Math.sin(a0) * inner, brighten(rgb, 0.5), a * 0.9);
         set(1, x + Math.cos(a0) * outer, y + 0.004, z + Math.sin(a0) * outer, brighten(rgb, 0.5), a * 0.9);
         set(2, x + Math.cos(a1) * outer, y + 0.004, z + Math.sin(a1) * outer, brighten(rgb, 0.5), a * 0.9);
         set(3, x + Math.cos(a1) * inner, y + 0.004, z + Math.sin(a1) * inner, brighten(rgb, 0.5), a * 0.9);
         emit(true);
      }

      // Ripple: dünner Ring läuft nach außen und blendet aus
      double ph = frac(time / 1.4 + phase * 0.1);
      double rr = (0.2 + 0.8 * ph) * s;
      double rw = (0.03 * (1.0 - ph) + 0.008) * s;
      double ra = (1.0 - ph) * a * 0.9;
      for (int i = 0; i < segs * 2; i++) {
         double a0 = TWO_PI * i / (segs * 2);
         double a1 = TWO_PI * (i + 1) / (segs * 2);
         set(0, x + Math.cos(a0) * (rr - rw), y + 0.006, z + Math.sin(a0) * (rr - rw), rgb, ra);
         set(1, x + Math.cos(a0) * (rr + rw), y + 0.006, z + Math.sin(a0) * (rr + rw), rgb, ra);
         set(2, x + Math.cos(a1) * (rr + rw), y + 0.006, z + Math.sin(a1) * (rr + rw), rgb, ra);
         set(3, x + Math.cos(a1) * (rr - rw), y + 0.006, z + Math.sin(a1) * (rr - rw), rgb, ra);
         emit(true);
      }
   }

   // ---------------- Funken

   private void drawSparks(double x, double y, double z, double s, int rgb, double a, double time, int id, int count) {
      int headRgb = brighten(rgb, 0.7);
      for (int k = 0; k < count; k++) {
         double seed = id * 13.7 + k * 7.13;
         double h1 = frac(Math.sin(seed) * 43758.5453);
         double h2 = frac(Math.sin(seed * 1.7) * 12345.678);
         double h3 = frac(Math.sin(seed * 3.1) * 999.1);
         double ph = frac(time * 0.5 * (0.7 + 0.6 * h3) + h2);
         double ang = h1 * TWO_PI + ph * 2.0;
         double r = (0.10 + 0.35 * h2) * s;
         double ex = x + Math.cos(ang) * r;
         double ey = y + 0.1 + ph * 1.5 * s;
         double ez = z + Math.sin(ang) * r;
         double al = Math.pow(Math.sin(ph * Math.PI), 1.2) * a;
         if (al < 0.03) continue;
         double len = (0.14 + 0.10 * h1) * s;
         double hw = 0.014 * s;
         for (int plane = 0; plane < 2; plane++) {
            double dx = plane == 0 ? hw : 0.0;
            double dz = plane == 0 ? 0.0 : hw;
            set(0, ex - dx, ey - len, ez - dz, rgb, 0.0);
            set(1, ex + dx, ey - len, ez + dz, rgb, 0.0);
            set(2, ex + dx, ey, ez + dz, headRgb, al);
            set(3, ex - dx, ey, ez - dz, headRgb, al);
            emit(true);
         }
      }
   }

   // ---------------- Hand-Glow (gehaltene Items)

   private void drawHands(class_4184 camera, class_243 cameraPos, double delta, double time) {
      class_1657 self = mc.field_1724;
      boolean firstPerson = mc.field_1690.method_31044().method_31034();
      this.drawPlayerHands(self, true, firstPerson, camera, cameraPos, delta, time);
      if (this.handOthers.get()) {
         double rangeSq = this.range.get() * this.range.get();
         int drawn = 0;
         for (class_1657 other : mc.field_1687.method_18456()) {
            if (other == self || !other.method_5805() || other.method_5858(self) > rangeSq) {
               continue;
            }
            if (drawn++ >= 12) {
               break;
            }
            this.drawPlayerHands(other, false, false, camera, cameraPos, delta, time);
         }
      }
   }

   private void drawPlayerHands(class_1657 p, boolean own, boolean firstPerson, class_4184 camera, class_243 cameraPos, double delta, double time) {
      boolean rightHanded = p.method_6068() == class_1306.field_6183;
      for (int hand = 0; hand < 2; hand++) {
         class_1799 stack = hand == 0 ? p.method_6047() : (this.handOffhand.get() ? p.method_6079() : null);
         if (stack == null || stack.method_7960()) {
            continue;
         }

         double side = (hand == 0) == rightHanded ? 1.0 : -1.0;
         double ax;
         double ay;
         double az;
         double alphaMul = 1.0;
         if (own && firstPerson) {
            // Handposition der 1. Person im Kamerasystem (rechts, unten, vorn); Glow liegt hinter dem Item-Modell.
            Quaternionf q = camera.method_23767();
            Vector3f right = new Vector3f(1.0F, 0.0F, 0.0F).rotate(q);
            Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F).rotate(q);
            Vector3f fwd = new Vector3f(0.0F, 0.0F, -1.0F).rotate(q);
            ax = cameraPos.field_1352 + right.x * 0.58 * side - up.x * 0.32 + fwd.x * 0.70;
            ay = cameraPos.field_1351 + right.y * 0.58 * side - up.y * 0.32 + fwd.y * 0.70;
            az = cameraPos.field_1350 + right.z * 0.58 * side - up.z * 0.32 + fwd.z * 0.70;
            alphaMul = this.handFirstPersonOpacity.get();
         } else {
            double ix = p.field_6038 + (p.method_23317() - p.field_6038) * delta;
            double iy = p.field_5971 + (p.method_23318() - p.field_5971) * delta;
            double iz = p.field_5989 + (p.method_23321() - p.field_5989) * delta;
            double yaw = Math.toRadians(p.method_36454());
            ax = ix - Math.cos(yaw) * 0.40 * side - Math.sin(yaw) * 0.25;
            ay = iy + 0.95;
            az = iz - Math.sin(yaw) * 0.40 * side + Math.cos(yaw) * 0.25;
         }

         String name = stack.method_7964().getString();
         String itemId = class_7923.field_41178.method_10221(stack.method_7909()).toString();
         this.drawHeld(ax, ay, az, name.hashCode(), itemId, isValuable(name), time, hand, alphaMul);
      }
   }

   /** Glow um ein gehaltenes Item: weiche Hülle, kreisender Kometenring und Funken. */
   private void drawHeld(double x, double y, double z, int nameHash, String itemId, boolean valuable, double time, int hand, double alphaMul) {
      boolean boosted = valuable && this.highlightValuable.get();
      double s = this.handSize.get() * (boosted ? 1.25 : 1.0);
      double phase = (nameHash & 0xFF) * 0.11 + hand * 1.3;
      double pulseF = this.pulse.get() ? 0.85 + 0.15 * Math.sin(time * 3.0 + phase) : 1.0;
      int rgb = this.baseColor(nameHash, itemId, time, phase);
      double a = this.brightness.get() * pulseF * alphaMul * (boosted ? 1.0 : 0.85);

      if (this.hull.get()) {
         sphere(x, y, z, 0.16 * s * pulseF, brighten(rgb, 0.35), a * 0.30);
         sphere(x, y, z, 0.28 * s * pulseF, rgb, a * 0.16);
      }

      // Kreisender Ring: Ebene kippt langsam, heller Komet läuft mit Schweif darauf um.
      double tilt = time * 0.9 + phase;
      double nx = Math.sin(tilt) * 0.6;
      double ny = 1.0;
      double nz = Math.cos(tilt) * 0.6;
      double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
      nx /= nl;
      ny /= nl;
      nz /= nl;
      double ux = ny;
      double uy = -nx;
      double uz = 0.0;
      double ul = Math.sqrt(ux * ux + uy * uy + uz * uz);
      ux /= ul;
      uy /= ul;
      uz /= ul;
      double vx = ny * uz - nz * uy;
      double vy = nz * ux - nx * uz;
      double vz = nx * uy - ny * ux;
      double radius = 0.26 * s;
      double thick = 0.012 * s;
      double head = time * 2.6 + phase;
      int ringRgb = brighten(rgb, 0.5);
      int segments = 24;
      for (int i = 0; i < segments; i++) {
         double a0 = TWO_PI * i / segments;
         double a1 = TWO_PI * (i + 1) / segments;
         double tail = Math.pow(1.0 - frac((head - (a0 + a1) * 0.5) / TWO_PI), 1.8);
         double al = (0.15 + 0.85 * tail) * a;
         double c0 = Math.cos(a0) * radius, s0 = Math.sin(a0) * radius;
         double c1 = Math.cos(a1) * radius, s1 = Math.sin(a1) * radius;
         double p0x = x + ux * c0 + vx * s0, p0y = y + uy * c0 + vy * s0, p0z = z + uz * c0 + vz * s0;
         double p1x = x + ux * c1 + vx * s1, p1y = y + uy * c1 + vy * s1, p1z = z + uz * c1 + vz * s1;
         set(0, p0x - nx * thick, p0y - ny * thick, p0z - nz * thick, ringRgb, al);
         set(1, p1x - nx * thick, p1y - ny * thick, p1z - nz * thick, ringRgb, al);
         set(2, p1x + nx * thick, p1y + ny * thick, p1z + nz * thick, ringRgb, al);
         set(3, p0x + nx * thick, p0y + ny * thick, p0z + nz * thick, ringRgb, al);
         emit(true);
      }
      sphere(x + (ux * Math.cos(head) + vx * Math.sin(head)) * radius,
         y + (uy * Math.cos(head) + vy * Math.sin(head)) * radius,
         z + (uz * Math.cos(head) + vz * Math.sin(head)) * radius,
         0.035 * s, 0xFFFFFF, a);

      int sparkCount = this.sparks.getInt();
      if (sparkCount > 0) {
         this.drawSparks(x, y - 0.12 * s, z, s * 0.45, rgb, a, time, nameHash, Math.max(1, sparkCount / 2));
      }
   }

   // ---------------- Low-Poly-Kugel (Leuchthülle)

   private static void sphere(double x, double y, double z, double r, int rgb, double alpha) {
      if (alpha < 0.01 || r < 0.002) return;
      int lat = 4;
      int lon = 7;
      for (int i = 0; i < lat; i++) {
         double p0 = -Math.PI / 2.0 + Math.PI * i / lat;
         double p1 = -Math.PI / 2.0 + Math.PI * (i + 1) / lat;
         double cp0 = Math.cos(p0), sp0 = Math.sin(p0), cp1 = Math.cos(p1), sp1 = Math.sin(p1);
         for (int j = 0; j < lon; j++) {
            double a0 = TWO_PI * j / lon;
            double a1 = TWO_PI * (j + 1) / lon;
            set(0, x + cp0 * Math.cos(a0) * r, y + sp0 * r, z + cp0 * Math.sin(a0) * r, rgb, alpha);
            set(1, x + cp0 * Math.cos(a1) * r, y + sp0 * r, z + cp0 * Math.sin(a1) * r, rgb, alpha);
            set(2, x + cp1 * Math.cos(a1) * r, y + sp1 * r, z + cp1 * Math.sin(a1) * r, rgb, alpha);
            set(3, x + cp1 * Math.cos(a0) * r, y + sp1 * r, z + cp1 * Math.sin(a0) * r, rgb, alpha);
            emit(false);
         }
      }
   }

   // ------------------------------------------------------------------ Quad-Helfer

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

   private static int mix(int a, int b, double t) {
      double u = clamp(t, 0.0, 1.0);
      int r = (int)Math.round((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * u);
      int g = (int)Math.round((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * u);
      int bl = (int)Math.round((a & 255) + ((b & 255) - (a & 255)) * u);
      return r << 16 | g << 8 | bl;
   }

   private static double frac(double value) {
      return value - Math.floor(value);
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }
}