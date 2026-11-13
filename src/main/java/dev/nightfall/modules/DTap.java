package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1511;
import net.minecraft.class_1657;
import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_746;

public class DTap extends Module {
   private final NumberSetting targetRange = this.add(new NumberSetting("Ziel-Reichweite", 6.0, 3.0, 12.0, 0.5));
   private final NumberSetting hitDelay = this.add(new NumberSetting("Delay nach Schwerttreffer (Ticks)", 0.0, 0.0, 40.0, 1.0));
   private final NumberSetting crystalHeight = this.add(new NumberSetting("Crystal-Mindesthoehe", 0.75, 0.0, 2.5, 0.05));
   private final NumberSetting obsidianHeight = this.add(new NumberSetting("Obsidian-Mindesthoehe", 0.05, 0.0, 2.5, 0.05));
   private int phase;
   private int ticks;
   private int hitDelayTicks;
   private int targetId = -1;
   private class_2338 firstBase;
   private class_2338 secondBase;
   private int trackedTargetId = -1;
   private float lastTargetHealth = Float.NaN;

   public DTap() {
      super("DTap", "Eigenstaendige Double-Crystal-Kombo mit genau zwei Crystals.", Category.COMBAT);
   }

   @Override
   protected void onEnable() { this.reset(); }

   @Override
   protected void onDisable() { this.reset(); }

   @Override
   public void onWorldLeave() { this.reset(); }

   private void reset() {
      phase = 0;
      ticks = 0;
      hitDelayTicks = 0;
      targetId = -1;
      firstBase = null;
      secondBase = null;
      trackedTargetId = -1;
      lastTargetHealth = Float.NaN;
   }

   private int countInventory(class_746 player, class_1792 item) {
      int count = 0;
      for (int i = 0; i < 36; i++) {
         net.minecraft.class_1799 stack = player.method_31548().method_5438(i);
         if (stack.method_31574(item)) count += stack.method_7947();
      }
      return count;
   }

   private boolean isTargetAirborne(class_1657 target) {
      return !target.method_24828();
   }

   private double heightAboveBase(class_1657 target, class_2338 base) {
      // Measure in full blocks above the top face of the selected obsidian,
      // not the fractional position inside the target's current block.
      return target.method_23318() - (base.method_10264() + 1.0);
   }

   private boolean isCrystalHeight(class_1657 target, class_2338 base) {
      return this.isTargetAirborne(target)
         && this.heightAboveBase(target, base) >= this.crystalHeight.get();
   }

   private boolean entityFree(class_2338 pos, double minY, double maxY) {
      class_238 box = new class_238(pos.method_10263(), pos.method_10264() + minY, pos.method_10260(),
         pos.method_10263() + 1.0, pos.method_10264() + maxY, pos.method_10260() + 1.0);
      return mc.field_1687.method_8335(null, box).isEmpty();
   }

   private boolean validBase(class_2338 base) {
      class_2680 state = mc.field_1687.method_8320(base);
      class_2338 support = base.method_10074();
      if (!state.method_45474() || mc.field_1687.method_8320(support).method_26220(mc.field_1687, support).method_1110()) return false;
      if (!mc.field_1687.method_8320(base.method_10084()).method_26215()
         || !mc.field_1687.method_8320(base.method_10084().method_10084()).method_26215()) return false;
      return entityFree(base, 0.0, 1.0) && entityFree(base, 1.0, 3.0);
   }

   private boolean usableBase(class_2338 base) {
      class_2680 state = mc.field_1687.method_8320(base);
      if (state.method_27852(class_2246.field_9987)) {
         class_2338 support = base.method_10074();
         return !mc.field_1687.method_8320(support).method_26220(mc.field_1687, support).method_1110()
            && mc.field_1687.method_8320(base.method_10084()).method_26215()
            && mc.field_1687.method_8320(base.method_10084().method_10084()).method_26215()
            && entityFree(base, 0.0, 1.0) && entityFree(base, 1.0, 3.0);
      }
      return validBase(base);
   }

   private class_2338 findSideBase(class_746 player, class_1657 target) {
      double dx = target.method_23317() - player.method_23317();
      double dz = target.method_23321() - player.method_23321();
      int forwardX = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? 1 : -1) : 0;
      int forwardZ = forwardX == 0 ? (dz >= 0 ? 1 : -1) : 0;
      int sideX = -forwardZ;
      int sideZ = forwardX;
      class_2338 feet = target.method_24515();
      int[][] offsets = {
         { sideX, sideZ }, { -sideX, -sideZ },
         { forwardX, forwardZ }, { -forwardX, -forwardZ },
         { sideX + forwardX, sideZ + forwardZ },
         { sideX - forwardX, sideZ - forwardZ },
         { -sideX + forwardX, -sideZ + forwardZ },
         { -sideX - forwardX, -sideZ - forwardZ },
         { 0, 0 }
      };
      for (int drop = 1; drop <= 8; drop++) {
         for (int[] offset : offsets) {
            class_2338 candidate = feet.method_10069(offset[0], -drop, offset[1]);
            if (usableBase(candidate)) return candidate;
         }
      }
      return null;
   }

   // Compatibility helper for older DTap call sites expecting two base positions.
   private class_2338[] findBases(class_746 player, class_1657 target) {
      class_2338 base = findSideBase(player, target);
      if (base == null) return new class_2338[0];
      return new class_2338[] { base, base };
   }

   private boolean placeObsidian(class_746 player, class_2338 base) {
      if (mc.field_1687.method_8320(base).method_27852(class_2246.field_9987)) return true;
      if (!validBase(base)) return false;
      int slot = InvUtil.ensureHotbar(class_1802.field_8281);
      if (slot < 0) return false;
      int previousSlot = InvUtil.selected();
      boolean switched = slot != previousSlot;
      if (switched) InvUtil.select(slot);
      boolean accepted = false;
      try {
         for (class_2350 face : class_2350.values()) {
            class_2338 neighbor = base.method_10093(face);
            if (mc.field_1687.method_8320(neighbor).method_26220(mc.field_1687, neighbor).method_1110()) continue;
            class_2350 clickedSide = face.method_10153();
            class_243 hit = class_243.method_24953(neighbor).method_1031(
               clickedSide.method_10148() * 0.5,
               clickedSide.method_10164() * 0.5,
               clickedSide.method_10165() * 0.5
            );
            class_3965 result = new class_3965(hit, clickedSide, neighbor, false);
            if (mc.field_1761.method_2896(player, class_1268.field_5808, result).method_23665()) {
               player.method_6104(class_1268.field_5808);
               accepted = true;
               break;
            }
         }
      } finally {
         if (switched) InvUtil.select(previousSlot);
      }
      return accepted || mc.field_1687.method_8320(base).method_27852(class_2246.field_9987);
   }

   private class_1511 findCrystal(class_2338 base) {
      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (entity instanceof class_1511 crystal && crystal.method_5805()
            && new class_243(crystal.method_23317(), crystal.method_23318(), crystal.method_23321())
               .method_1022(new class_243(base.method_10263() + 0.5, base.method_10264() + 1.0, base.method_10260() + 0.5)) < 1.25) return crystal;
      }
      return null;
   }

   private void placeCrystal(class_746 player, class_2338 base) {
      int slot = InvUtil.ensureHotbar(class_1802.field_8301);
      if (slot < 0) { this.setEnabled(false); return; }
      int previousSlot = InvUtil.selected();
      InvUtil.select(slot);
      class_243 hit = new class_243(base.method_10263() + 0.5, base.method_10264() + 1.0, base.method_10260() + 0.5);
      mc.field_1761.method_2896(player, class_1268.field_5808, new class_3965(hit, class_2350.field_11036, base, false));
      player.method_6104(class_1268.field_5808);
      // Restore the slot immediately so normal hotbar switching remains available.
      InvUtil.select(previousSlot);
   }

   private void attackCrystal(class_746 player, class_2338 base) {
      class_1511 crystal = findCrystal(base);
      if (crystal == null) {
         if (++this.ticks > 12) this.setEnabled(false);
         return;
      }
      mc.field_1761.method_2918(player, crystal);
      player.method_6104(class_1268.field_5808);
      if (++this.ticks >= 3) this.ticks = 0;
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1687 == null || mc.field_1761 == null || mc.field_1755 != null) return;
      if (phase == 0) {
         class_1657 target = CombatUtil.nearestPlayer(targetRange.get());
         if (target == null) {
            trackedTargetId = -1;
            lastTargetHealth = Float.NaN;
            return;
         }
         if (trackedTargetId != target.method_5628()) {
            trackedTargetId = target.method_5628();
            lastTargetHealth = target.method_6032();
            return;
         }
         float health = target.method_6032();
         boolean wasHit = !Float.isNaN(lastTargetHealth) && health < lastTargetHealth - 0.01F;
         lastTargetHealth = health;
         if (!wasHit) return;
         if (countInventory(player, class_1802.field_8281) < 1 || countInventory(player, class_1802.field_8301) < 2) return;
         // The target may still be rising at hit time. Find a valid base on later ticks.
         firstBase = null;
         secondBase = null;
         targetId = target.method_5628();
         phase = 1;
         ticks = 0;
         hitDelayTicks = hitDelay.getInt();
         return;
      }

      class_1657 target = CombatUtil.nearestPlayer(targetRange.get());
      if (target == null || target.method_5628() != targetId || !target.method_5805() || ++ticks > 220) {
         this.setEnabled(false);
         return;
      }

      // Wait the configured number of ticks after the detected sword hit before placing obsidian/crystals.
      if (hitDelayTicks > 0) {
         hitDelayTicks--;
         return;
      }

      switch (phase) {
         case 1:
            if (firstBase == null || !usableBase(firstBase)) {
               firstBase = findSideBase(player, target);
               secondBase = firstBase;
            }
            if (firstBase == null) return;
            if (target.method_24828()
               || this.heightAboveBase(target, firstBase) < this.obsidianHeight.get()) return;
            if (placeObsidian(player, firstBase)) { phase = 3; ticks = 0; }
            break;
         case 2:
            phase = 3;
            ticks = 0;
            break;
         case 3:
            if (firstBase == null || !this.isCrystalHeight(target, firstBase)) return;
            if (findCrystal(firstBase) == null) placeCrystal(player, firstBase);
            if (findCrystal(firstBase) != null) { phase = 4; ticks = 0; }
            break;
         case 4:
            attackCrystal(player, firstBase);
            if (findCrystal(firstBase) == null && ticks > 0) { phase = 5; ticks = 0; }
            break;
         case 5:
            if (secondBase == null || !this.isCrystalHeight(target, secondBase)) return;
            if (findCrystal(secondBase) == null) placeCrystal(player, secondBase);
            if (findCrystal(secondBase) != null) { phase = 6; ticks = 0; }
            break;
         case 6:
            attackCrystal(player, secondBase);
            if (findCrystal(secondBase) == null && ticks > 0) this.setEnabled(false);
            break;
         default:
            this.setEnabled(false);
      }
   }
}
