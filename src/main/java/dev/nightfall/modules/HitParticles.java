package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.class_1309;
import net.minecraft.class_2394;
import net.minecraft.class_2398;

public final class HitParticles extends Module {
   private final ModeSetting style = this.add(new ModeSetting("Effekt", "Crit", "Crit", "Herzen", "Flamme", "Witch", "Soul", "Enchant"));
   private final NumberSetting amount = this.add(new NumberSetting("Partikelmenge", 8.0, 2.0, 20.0, 1.0));
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 8.0, 3.0, 16.0, 1.0));
   private final Map<UUID, Float> lastHealth = new HashMap<>();

   public HitParticles() {
      super("HitParticles", "Partikeleffekt, wenn nahe Entities Schaden bekommen (lokal).", Category.VISUALS);
   }

   @Override
   protected void onEnable() {
      this.lastHealth.clear();
   }

   @Override
   protected void onDisable() {
      this.lastHealth.clear();
   }

   @Override
   public void onWorldLeave() {
      this.lastHealth.clear();
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         for (class_1309 entity : mc.field_1687
            .method_8390(class_1309.class, mc.field_1724.method_5829().method_1014(this.range.get()), e -> e != mc.field_1724 && e.method_5805())) {
            UUID id = entity.method_5667();
            float health = entity.method_6032();
            Float old = this.lastHealth.put(id, health);
            if (old != null && health < old) {
               this.burst(entity);
            }
         }

         if (this.lastHealth.size() > 512) {
            this.lastHealth.clear();
         }
      }
   }

   private void burst(class_1309 entity) {
      String styleName = this.style.get();

      class_2394 p = switch (styleName) {
         case "Herzen" -> class_2398.field_11201;
         case "Flamme" -> class_2398.field_11240;
         case "Witch" -> class_2398.field_11249;
         case "Soul" -> class_2398.field_23114;
         case "Enchant" -> class_2398.field_11215;
         default -> class_2398.field_11205;
      };

      for (int particleIndex = 0; particleIndex < this.amount.getInt(); particleIndex++) {
         double dx = (Math.random() - 0.5) * 0.8;
         double dy = Math.random() * 1.5;
         double dz = (Math.random() - 0.5) * 0.8;
         mc.field_1713
            .method_3056(
               p, entity.method_23317() + dx, entity.method_23318() + dy, entity.method_23321() + dz, dx * 0.15, 0.08 + Math.random() * 0.12, dz * 0.15
            );
      }
   }
}
