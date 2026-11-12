package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.class_1268;
import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2302;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;

public final class AutoFarm extends Module {
   private final BoolSetting replant = this.add(new BoolSetting("Automatisch nachpflanzen", true));
   private class_2338 pendingCrop;
   private class_2338 pendingSoil;
   private class_1792 pendingSeed;
   private int breakTicks;
   private int cooldown;

   public AutoFarm() {
      super("AutoFarm", "Erntet nur die reife Pflanze unter dem Fadenkreuz und pflanzt optional nach.", Category.WORLD);
   }

   @Override
   protected void onEnable() {
      this.clearPending();
      this.cooldown = 0;
   }

   @Override
   protected void onDisable() {
      this.clearPending();
      this.cooldown = 0;
   }

   @Override
   public void onWorldLeave() {
      this.clearPending();
      this.cooldown = 0;
   }

   private void clearPending() {
      this.pendingCrop = null;
      this.pendingSoil = null;
      this.pendingSeed = null;
      this.breakTicks = 0;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null && mc.field_1761 != null) {
         if (this.cooldown > 0) {
            this.cooldown--;
         }

         if (this.pendingCrop != null) {
            if (mc.field_1687.method_8320(this.pendingCrop).method_26215()) {
               if (this.replant.get()) {
                  this.plantAgain();
               }

               this.clearPending();
               this.cooldown = 4;
            } else if (++this.breakTicks <= 80) {
               mc.field_1761.method_2902(this.pendingCrop, class_2350.field_11036);
            } else {
               this.clearPending();
            }
         } else if (this.cooldown <= 0 && mc.field_1765 instanceof class_3965 hit) {
            class_2338 var6 = hit.method_17777();
            class_2680 state = mc.field_1687.method_8320(var6);
            if (state.method_26204() instanceof class_2302 crop && crop.method_9825(state)) {
               if (state.method_27852(class_2246.field_10293)) {
                  this.pendingSeed = class_1802.field_8317;
               } else if (state.method_27852(class_2246.field_10609)) {
                  this.pendingSeed = class_1802.field_8179;
               } else if (state.method_27852(class_2246.field_10247)) {
                  this.pendingSeed = class_1802.field_8567;
               } else {
                  if (!state.method_27852(class_2246.field_10341)) {
                     return;
                  }

                  this.pendingSeed = class_1802.field_8309;
               }

               this.pendingCrop = var6.method_10062();
               this.pendingSoil = var6.method_10074().method_10062();
               this.breakTicks = 0;
               mc.field_1761.method_2910(var6, hit.method_17780());
               mc.field_1761.method_2902(var6, hit.method_17780());
            }
         }
      }
   }

   private void plantAgain() {
      if (this.pendingSeed != null && this.pendingSoil != null && mc.field_1687 != null && mc.field_1724 != null) {
         if (mc.field_1687.method_8320(this.pendingSoil).method_27852(class_2246.field_10362) && mc.field_1687.method_8320(this.pendingCrop).method_26215()) {
            int oldSlot = mc.field_1724.method_31548().method_67532();

            for (int slot = 0; slot < 9; slot++) {
               if (mc.field_1724.method_31548().method_5438(slot).method_31574(this.pendingSeed)) {
                  mc.field_1724.method_31548().method_61496(slot);
                  class_3965 placeHit = new class_3965(
                     class_243.method_24953(this.pendingSoil).method_1031(0.0, 0.5, 0.0), class_2350.field_11036, this.pendingSoil, false
                  );
                  mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, placeHit);
                  mc.field_1724.method_31548().method_61496(oldSlot);
                  return;
               }
            }
         }
      }
   }
}
