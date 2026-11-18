package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_746;

public class CrystalAura extends Module {
   private final NumberSetting targetRange = this.add(new NumberSetting("Ziel-Reichweite", 10.0, 4.0, 16.0, 1.0));
   private final NumberSetting placeRange = this.add(new NumberSetting("Platzier-Reichweite", 4.5, 2.0, 6.0, 0.5));
   private final NumberSetting breakRange = this.add(new NumberSetting("Zerstoer-Reichweite", 4.5, 2.0, 6.0, 0.5));
   private final NumberSetting placeDelay = this.add(new NumberSetting("Platzier-Delay (Ticks)", 0.0, 0.0, 20.0, 1.0));
   private final NumberSetting breakDelay = this.add(new NumberSetting("Zerstoer-Delay (Ticks)", 0.0, 0.0, 20.0, 1.0));
   private final BoolSetting place = this.add(new BoolSetting("Platzieren", true));
   private final BoolSetting breakC = this.add(new BoolSetting("Zerstoeren", true));
   private final BoolSetting autoSwitch = this.add(new BoolSetting("Auto-Switch", true));

   private int placeTicks;
   private int breakTicks;

   public CrystalAura() {
      super("CrystalAura", "Platziert und zerstoert End Crystals auf gueltigen Obsidian- und Bedrock-Bloecken.", Category.COMBAT);
   }

   @Override
   public void onWorldLeave() {
      this.resetTimers();
   }

   @Override
   protected void onDisable() {
      this.resetTimers();
   }

   private void resetTimers() {
      this.placeTicks = 0;
      this.breakTicks = 0;
   }

   private class_1511 findCrystalToBreak(class_746 player) {
      class_1511 best = null;
      double bestDistance = Double.MAX_VALUE;

      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (entity instanceof class_1511 crystal && crystal.method_5805()
            && player.method_5739(crystal) <= this.breakRange.get()) {
            double distance = crystal.method_5858(player);
            if (distance < bestDistance) {
               bestDistance = distance;
               best = crystal;
            }
         }
      }
      return best;
   }

   private boolean crystalSpaceFree(class_2338 base) {
      class_238 space = new class_238(
         base.method_10263(), base.method_10264() + 1.0, base.method_10260(),
         base.method_10263() + 1.0, base.method_10264() + 3.0, base.method_10260() + 1.0
      );
      return mc.field_1687.method_8320(base.method_10084()).method_26215()
         && mc.field_1687.method_8320(base.method_10084().method_10084()).method_26215()
         && mc.field_1687.method_8335(null, space).isEmpty();
   }

   private class_2338 findBestBase(class_746 player, class_1657 target) {
      class_2338 origin = player.method_24515();
      int radius = (int)Math.ceil(this.placeRange.get());
      class_2338 best = null;
      double bestTargetDistance = Double.MAX_VALUE;

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               class_2338 base = origin.method_10069(x, y, z);
               class_2680 state = mc.field_1687.method_8320(base);
               if (!state.method_27852(class_2246.field_10540)
                  && !state.method_27852(class_2246.field_9987)) continue;

               class_243 hit = new class_243(
                  base.method_10263() + 0.5, base.method_10264() + 1.0, base.method_10260() + 0.5
               );
               if (player.method_33571().method_1022(hit) > this.placeRange.get()) continue;
               if (!this.crystalSpaceFree(base)) continue;

               double targetDistance = target.method_5707(hit);
               if (targetDistance > 144.0 || targetDistance >= bestTargetDistance) continue;

               bestTargetDistance = targetDistance;
               best = base.method_10062();
            }
         }
      }
      return best;
   }

   private class_1268 crystalHand(class_746 player) {
      if (player.method_6079().method_31574(class_1802.field_8301)) return class_1268.field_5810;
      if (player.method_6047().method_31574(class_1802.field_8301)) return class_1268.field_5808;
      return null;
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1687 == null || mc.field_1761 == null || mc.field_1755 != null) return;

      if (this.placeTicks > 0) this.placeTicks--;
      if (this.breakTicks > 0) this.breakTicks--;

      if (this.breakC.get() && this.breakTicks == 0) {
         class_1511 crystal = this.findCrystalToBreak(player);
         if (crystal != null) {
            mc.field_1761.method_2918(player, crystal);
            player.method_6104(class_1268.field_5808);
            this.breakTicks = this.breakDelay.getInt();
            return;
         }
      }

      if (!this.place.get() || this.placeTicks > 0) return;

      class_1657 target = CombatUtil.nearestPlayer(this.targetRange.get());
      if (target == null) return;

      class_1268 hand = this.crystalHand(player);
      int previousSlot = InvUtil.selected();
      boolean switchedSlot = false;

      if (hand == null) {
         if (!this.autoSwitch.get()) return;
         int crystalSlot = InvUtil.ensureHotbar(class_1802.field_8301);
         if (crystalSlot < 0) return;

         if (crystalSlot != previousSlot) {
            InvUtil.select(crystalSlot);
            switchedSlot = true;
         }
         hand = class_1268.field_5808;
      }

      try {
         class_2338 base = this.findBestBase(player, target);
         if (base == null) return;

         class_243 hit = new class_243(
            base.method_10263() + 0.5, base.method_10264() + 1.0, base.method_10260() + 0.5
         );
         class_3965 result = new class_3965(hit, class_2350.field_11036, base, false);
         if (mc.field_1761.method_2896(player, hand, result).method_23665()) {
            player.method_6104(hand);
            this.placeTicks = this.placeDelay.getInt();
         }
      } finally {
         if (switchedSlot) InvUtil.select(previousSlot);
      }
   }
}
