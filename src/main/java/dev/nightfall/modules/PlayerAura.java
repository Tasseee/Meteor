package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Theme;
import net.minecraft.class_2338;
import net.minecraft.class_2680;

/**
 * PlayerAura (neu): kräftige Energie-Aura aus Flammenzungen, Leuchtsäule, Bodenglanz, Bodensiegel,
 * Druckwellen, Kometenringen, Lichtkugeln und Funken. Gezeichnet in dev.nightfall.render.PlayerAuraRenderer.
 */
public final class PlayerAura extends Module {
   // --- Allgemein
   private final NumberSetting scale = this.add(new NumberSetting("Größe", 1.0, 0.5, 2.5, 0.05));
   private final NumberSetting speed = this.add(new NumberSetting("Tempo", 1.0, 0.2, 3.0, 0.05));
   private final NumberSetting intensity = this.add(new NumberSetting("Helligkeit", 1.0, 0.2, 1.0, 0.01));
   private final NumberSetting power = this.add(new NumberSetting("Kraft (Leuchtkern)", 0.7, 0.0, 1.0, 0.05));
   private final ModeSetting quality = this.add(new ModeSetting("Qualität", "Mittel", "Niedrig", "Mittel", "Hoch"));

   // --- Elemente
   private final NumberSetting flames = this.add(new NumberSetting("Flammen", 22.0, 0.0, 32.0, 1.0));
   private final NumberSetting flameHeight = this.add(new NumberSetting("Flammenhöhe", 2.2, 0.8, 3.5, 0.1));
   private final NumberSetting lean = this.add(new NumberSetting("Neigung bei Bewegung", 1.0, 0.0, 2.0, 0.1));
   private final BoolSetting pillar = this.add(new BoolSetting("Leuchtsäule", true));
   private final BoolSetting groundGlow = this.add(new BoolSetting("Bodenglanz", true));
   private final BoolSetting sigil = this.add(new BoolSetting("Bodensiegel", true));
   private final BoolSetting pulse = this.add(new BoolSetting("Druckwelle", true));
   private final NumberSetting rings = this.add(new NumberSetting("Energieringe", 3.0, 0.0, 4.0, 1.0));
   private final NumberSetting orbs = this.add(new NumberSetting("Lichtkugeln", 6.0, 0.0, 14.0, 1.0));
   private final NumberSetting sparks = this.add(new NumberSetting("Funken", 50.0, 0.0, 120.0, 1.0));

   // --- Farben
   private final ModeSetting palette = this.add(new ModeSetting(
      "Farbschema", "Nightfall", "Nightfall", "Violett", "Gold", "Ozean", "Feuer", "Rot", "Mint", "Eisblau", "Regenbogen", "Theme", "Eigene"
   ));
   private final ColorSetting customInner = this.add(new ColorSetting("Eigene Farbe Start", 0x8C5AFF));
   private final ColorSetting customOuter = this.add(new ColorSetting("Eigene Farbe Ende", 0xFF78DC));
   private final NumberSetting rainbowSpeed = this.add(new NumberSetting("Regenbogen-Tempo", 0.6, 0.1, 3.0, 0.05));

   private long activatedAtNanos;
   private long tickCount;
   private double groundTarget;
   private boolean groundValid;

   // Pro Frame einmal aufgelöst, damit color() keine String-Vergleiche macht.
   private int palInner = 0x7C3CFF;
   private int palOuter = 0x2BE3D0;
   private boolean palRainbow;
   private double palRainbowSpeed = 0.6;

   public PlayerAura() {
      super("PlayerAura", "Kräftige Energie-Aura: Flammen, Leuchtsäule, Bodensiegel, Druckwellen, Ringe, Kugeln und Funken.", Category.VISUALS);
   }

   @Override
   protected void onEnable() {
      this.activatedAtNanos = System.nanoTime();
      this.groundValid = false;
   }

   @Override
   protected void onDisable() {
      this.activatedAtNanos = 0L;
      this.groundValid = false;
   }

   @Override
   public void onWorldLeave() {
      this.activatedAtNanos = System.nanoTime();
      this.groundValid = false;
   }

   /** Einmal pro Tick: Tick-Zähler (für die Frame-Interpolation) und Bodenhöhe unter dem Spieler. */
   @Override
   public void onTick() {
      this.tickCount++;
      if (mc.field_1724 == null || mc.field_1687 == null) {
         this.groundValid = false;
         return;
      }

      double x = mc.field_1724.method_23317();
      double y = mc.field_1724.method_23318();
      double z = mc.field_1724.method_23321();
      class_2338 base = new class_2338((int)Math.floor(x), (int)Math.floor(y - 0.2), (int)Math.floor(z));
      this.groundValid = false;
      for (int dy = 0; dy >= -6; dy--) {
         class_2338 pos = base.method_10086(dy);
         class_2680 state = mc.field_1687.method_8320(pos);
         var shape = state.method_26220(mc.field_1687, pos);
         if (shape.method_1110()) {
            continue;
         }
         this.groundTarget = pos.method_10264() + shape.method_1107().field_1325;
         this.groundValid = true;
         return;
      }
   }

   // ------------------------------------------------------------------ Getter für den Renderer

   public double scale() { return this.scale.get(); }
   public double speed() { return this.speed.get(); }
   public double intensity() { return this.intensity.get(); }
   public double power() { return this.power.get(); }
   /** 0 = Niedrig, 1 = Mittel, 2 = Hoch */
   public int qualityLevel() { return this.quality.is("Niedrig") ? 0 : this.quality.is("Hoch") ? 2 : 1; }
   public int flameCount() { return this.flames.getInt(); }
   public double flameHeight() { return this.flameHeight.get(); }
   public double lean() { return this.lean.get(); }
   public boolean pillarEnabled() { return this.pillar.get(); }
   public boolean groundGlowEnabled() { return this.groundGlow.get(); }
   public boolean sigilEnabled() { return this.sigil.get(); }
   public boolean pulseEnabled() { return this.pulse.get(); }
   public int ringCount() { return this.rings.getInt(); }
   public int orbCount() { return this.orbs.getInt(); }
   public int sparkCount() { return this.sparks.getInt(); }
   public long activatedAtNanos() { return this.activatedAtNanos; }
   public long tickCount() { return this.tickCount; }
   public boolean groundValid() { return this.groundValid; }
   public double groundTarget() { return this.groundTarget; }

   // ------------------------------------------------------------------ Farben

   /** Pro Frame einmal vom Renderer aufrufen. */
   public void refreshPalette() {
      this.palRainbow = false;
      this.palRainbowSpeed = this.rainbowSpeed.get();
      if (this.palette.is("Regenbogen")) {
         this.palRainbow = true;
      } else if (this.palette.is("Eigene")) {
         this.palInner = this.customInner.rgb();
         this.palOuter = this.customOuter.rgb();
      } else if (this.palette.is("Theme")) {
         this.palInner = Theme.ACCENT & 0xFFFFFF;
         this.palOuter = Theme.ACCENT_DIM & 0xFFFFFF;
      } else if (this.palette.is("Violett")) {
         this.palInner = 0x7A2DFF; this.palOuter = 0xFF3DE0;
      } else if (this.palette.is("Gold")) {
         this.palInner = 0xFF9A00; this.palOuter = 0xFFE45C;
      } else if (this.palette.is("Ozean")) {
         this.palInner = 0x1E6BFF; this.palOuter = 0x35F2FF;
      } else if (this.palette.is("Feuer")) {
         this.palInner = 0xFF2A00; this.palOuter = 0xFFD21E;
      } else if (this.palette.is("Rot")) {
         this.palInner = 0xFF1020; this.palOuter = 0xFF7A3C;
      } else if (this.palette.is("Mint")) {
         this.palInner = 0x12E08C; this.palOuter = 0xC8FF5A;
      } else if (this.palette.is("Eisblau")) {
         this.palInner = 0x4FA8FF; this.palOuter = 0xE8F6FF;
      } else {
         this.palInner = 0x7C3CFF; this.palOuter = 0x2BE3D0;
      }
   }

   /** position 0 = Start-/Innenfarbe, 1 = End-/Außenfarbe. */
   public int color(double position, double timeSeconds) {
      double p = Math.max(0.0, Math.min(1.0, position));
      if (this.palRainbow) {
         float hue = (float)((timeSeconds * this.palRainbowSpeed * 0.1 + p * 0.35) % 1.0);
         return java.awt.Color.HSBtoRGB(hue, 0.85F, 1.0F) & 0xFFFFFF;
      }
      int r = mix(this.palInner >> 16 & 255, this.palOuter >> 16 & 255, p);
      int g = mix(this.palInner >> 8 & 255, this.palOuter >> 8 & 255, p);
      int b = mix(this.palInner & 255, this.palOuter & 255, p);
      return r << 16 | g << 8 | b;
   }

   private static int mix(int a, int b, double t) {
      return (int)Math.round(a + (b - a) * t);
   }
}