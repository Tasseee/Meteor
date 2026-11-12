package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.class_2394;
import net.minecraft.class_2398;

public final class MovementParticles extends Module {
   private final ModeSetting effect = this.add(new ModeSetting("Effekt", "Totem", "Totem", "Soul", "Portal", "Herzen", "Wolke", "Flamme", "End Rod"));
   private final NumberSetting amount = this.add(new NumberSetting("Menge", 3.0, 1.0, 10.0, 1.0));
   private final BoolSetting running = this.add(new BoolSetting("Beim Laufen", true));
   private final BoolSetting jumping = this.add(new BoolSetting("Beim Springen/Landen", true));
   private boolean wasGrounded = true;
   private int ticks;

   @Override
   public void onWorldLeave() {
      this.wasGrounded = true;
      this.ticks = 0;
   }

   public MovementParticles() {
      super("MovementParticles", "Zusätzliche lokale Bewegungs-, Sprung- und Totem-Optik.", Category.VISUALS);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         boolean grounded = mc.field_1724.method_24828();
         boolean moving = mc.field_1724.method_18798().method_37268() > 0.003;
         if (this.running.get() && moving && grounded && ++this.ticks % 3 == 0) {
            this.burst(false);
         }

         if (this.jumping.get() && grounded && !this.wasGrounded) {
            this.burst(true);
         }

         this.wasGrounded = grounded;
      }
   }

   private void burst(boolean ring) {
      String effectName = this.effect.get();

      class_2394 p = switch (effectName) {
         case "Soul" -> class_2398.field_23114;
         case "Portal" -> class_2398.field_11214;
         case "Herzen" -> class_2398.field_11201;
         case "Wolke" -> class_2398.field_11204;
         case "Flamme" -> class_2398.field_11240;
         case "End Rod" -> class_2398.field_11207;
         default -> class_2398.field_11220;
      };
      int particleCount = this.amount.getInt() * (ring ? 4 : 1);

      for (int i = 0; i < particleCount; i++) {
         double a = (Math.PI * 2) * i / particleCount;
         double r = ring ? 0.65 : 0.28;
         double x = mc.field_1724.method_23317() + Math.cos(a) * r + (Math.random() - 0.5) * 0.12;
         double z = mc.field_1724.method_23321() + Math.sin(a) * r + (Math.random() - 0.5) * 0.12;
         mc.field_1713.method_3056(p, x, mc.field_1724.method_23318() + 0.1 + Math.random() * 0.5, z, Math.cos(a) * 0.015, 0.025, Math.sin(a) * 0.015);
      }
   }
}
