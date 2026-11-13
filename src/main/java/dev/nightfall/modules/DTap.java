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

   private static final int IDLE = 0;
   private static final int PLACE_OBSIDIAN = 1;
   private static final int WAIT_OBSIDIAN = 2;
   private static final int PLACE_FIRST = 3;
   private static final int WAIT_FIRST = 4;
   private static final int DETONATE_FIRST = 5;
   private static final int WAIT_SECOND = 6;
   private static final int PLACE_SECOND = 7;
   private static final int WAIT_SECOND_SPAWN = 8;
   private static final int DETONATE_SECOND = 9;

   private int phase;
   private int phaseTicks;
   private int sequenceTicks;
   private int hitDelayTicks;
   private int targetId = -1;
   private class_2338 base;
   private int trackedTargetId = -1;
   private float lastTargetHealth = Float.NaN;

   public DTap() {
      super("DTap", "Platziert einen Obsidian-Block und detoniert zwei End-Crystals nacheinander.", Category.COMBAT);
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
      phase = IDLE;
      phaseTicks = 0;
      sequenceTicks = 0;
      hitDelayTicks = 0;
      targetId = -1;
      base = null;
      trackedTargetId = -1;
      lastTargetHealth = Float.NaN;
   }

   private void setPhase(int next) {
      phase = next;
      phaseTicks = 0;
   }

   private int countInventory(class_746 player, class_1792 item) {
      int count = 0;
      for (int i = 0; i < 36; i++) {
         net.minecraft.class_1799 stack = player.method_31548().method_5438(i);
         if (stack.method_31574(item)) count += stack.method_7947();
      }
      return count;
   }

   private class_1657 findTrackedTarget(int id) {
      for (class_1657 candidate : mc.field_1687.method_18456()) {
         if (candidate.method_5628() == id && candidate.method_5805()) return candidate;
      }
      return null;
   }

   private boolean isObsidian(class_2338 pos) {
      return mc.field_1687.method_8320(pos).method_27852(class_2246.field_9987);
   }

   private boolean clearCrystalBlocks(class_2338 pos) {
      return mc.field_1687.method_8320(pos.method_10084()).method_26215()
         && mc.field_1687.method_8320(pos.method_10084().method_10084()).method_26215();
   }

   private boolean entityFree(class_2338 pos, double minY, double maxY) {
      class_238 box = new class_238(pos.method_10263(), pos.method_10264() + minY, pos.method_10260(),
         pos.method_10263() + 1.0, pos.method_10264() + maxY, pos.method_10260() + 1.0);
      return mc.field_1687.method_8335(null, box).isEmpty();
   }

   private boolean validBase(class_2338 candidate) {
      class_2680 state = mc.field_1687.method_8320(candidate);
      class_2338 support = candidate.method_10074();
      if (!state.method_45474()) return false;
      if (mc.field_1687.method_8320(support).method_26220(mc.field_1687, support).method_1110()) return false;
      if (!clearCrystalBlocks(candidate)) return false;
      return entityFree(candidate, 0.0, 1.0) && entityFree(candidate, 1.0, 3.0);
   }

   private boolean usableObsidian(class_2338 candidate) {
      if (!isObsidian(candidate) || !clearCrystalBlocks(candidate)) return false;
      return entityFree(candidate, 1.0, 3.0);
   }

   // Search for a FRESH obsidian position beside the target.
   // Nearby obsidian never creates a no-place radius: occupied cells are simply
   // skipped and the search continues to the next side position.
   private class_2338 findSideBase(class_746 player, class_1657 target) {
      double dx = target.method_23317() - player.method_23317();
      double dz = target.method_23321() - player.method_23321();
      int forwardX = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? 1 : -1) : 0;
      int forwardZ = forwardX == 0 ? (dz >= 0 ? 1 : -1) : 0;
      int sideX = -forwardZ;
      int sideZ = forwardX;
      int[][] offsets = {
         // The first choices remain one or two blocks beside the target.
         { -forwardX, -forwardZ }, { sideX, sideZ }, { -sideX, -sideZ },
         { sideX * 2, sideZ * 2 }, { -sideX * 2, -sideZ * 2 },
         { forwardX, forwardZ }, { forwardX * 2, forwardZ * 2 },
         { -forwardX * 2, -forwardZ * 2 },
         { sideX + forwardX, sideZ + forwardZ },
         { sideX - forwardX, sideZ - forwardZ },
         { -sideX + forwardX, -sideZ + forwardZ },
         { -sideX - forwardX, -sideZ - forwardZ },
         // Expand outward only when every closer side cell is occupied/blocked.
         { sideX * 3, sideZ * 3 }, { -sideX * 3, -sideZ * 3 },
         { forwardX * 3, forwardZ * 3 }, { -forwardX * 3, -forwardZ * 3 },
         { sideX * 4, sideZ * 4 }, { -sideX * 4, -sideZ * 4 },
         { forwardX * 4, forwardZ * 4 }, { -forwardX * 4, -forwardZ * 4 }
      };
      class_2338 feet = target.method_24515();
      for (int drop = 0; drop <= 3; drop++) {
         for (int[] offset : offsets) {
            class_2338 candidate = feet.method_10069(offset[0], -drop, offset[1]);
            if (!validBase(candidate)) continue;

            // Crystal interaction is one block higher than the obsidian
            // placement face. Prefer only positions the player can actually reach.
            class_243 crystalHit = new class_243(candidate.method_10263() + 0.5,
               candidate.method_10264() + 1.0, candidate.method_10260() + 0.5);
            if (player.method_33571().method_1022(crystalHit) > 4.5) continue;
            return candidate;
         }
      }
      return null;
   }

   private boolean placeObsidian(class_746 player, class_2338 candidate) {
      if (isObsidian(candidate)) return true;
      if (!validBase(candidate)) return false;

      int slot = InvUtil.ensureHotbar(class_1802.field_8281);
      if (slot < 0) return false;

      int previousSlot = InvUtil.selected();
      InvUtil.select(slot);
      boolean accepted = false;
      try {
         // Click the solid block directly below the target cell's base position.
         class_2338 support = candidate.method_10074();
         class_243 hit = new class_243(candidate.method_10263() + 0.5,
            candidate.method_10264(), candidate.method_10260() + 0.5);
         class_3965 result = new class_3965(hit, class_2350.field_11036, support, false);
         accepted = mc.field_1761.method_2896(player, class_1268.field_5808, result).method_23665();
         if (accepted) player.method_6104(class_1268.field_5808);
      } finally {
         InvUtil.select(previousSlot);
      }
      return accepted || isObsidian(candidate);
   }

   private class_1511 findCrystal(class_2338 candidate) {
      class_243 center = new class_243(candidate.method_10263() + 0.5,
         candidate.method_10264() + 1.0, candidate.method_10260() + 0.5);
      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (entity instanceof class_1511 crystal && crystal.method_5805()
            && new class_243(crystal.method_23317(), crystal.method_23318(), crystal.method_23321())
               .method_1022(center) < 1.5) return crystal;
      }
      return null;
   }

   private boolean placeCrystal(class_746 player, class_2338 candidate) {
      if (!isObsidian(candidate) || !clearCrystalBlocks(candidate) || !entityFree(candidate, 1.0, 3.0)) return false;

      // Match CrystalAura's hand selection: an offhand crystal is valid too.
      class_1268 hand = player.method_6079().method_31574(class_1802.field_8301)
         ? class_1268.field_5810
         : (player.method_6047().method_31574(class_1802.field_8301) ? class_1268.field_5808 : null);
      int previousSlot = InvUtil.selected();
      boolean switched = false;
      if (hand == null) {
         int slot = InvUtil.ensureHotbar(class_1802.field_8301);
         if (slot < 0) return false;
         if (slot != previousSlot) {
            InvUtil.select(slot);
            switched = true;
         }
         hand = class_1268.field_5808;
      }

      boolean accepted = false;
      try {
         class_243 hit = new class_243(candidate.method_10263() + 0.5,
            candidate.method_10264() + 1.0, candidate.method_10260() + 0.5);
         // Do not send an out-of-reach placement packet.
         if (player.method_33571().method_1022(hit) > 4.5) return false;
         class_3965 result = new class_3965(hit, class_2350.field_11036, candidate, false);
         accepted = mc.field_1761.method_2896(player, hand, result).method_23665();
         player.method_6104(hand);
      } finally {
         if (switched) InvUtil.select(previousSlot);
      }
      // A world update can arrive immediately even when the local interaction
      // result is PASS; count the spawned crystal as a successful placement.
      return accepted || findCrystal(candidate) != null;
   }

   private void attackCrystal(class_746 player, class_1511 crystal) {
      if (crystal != null && player.method_5739(crystal) <= 4.5) {
         mc.field_1761.method_2918(player, crystal);
         player.method_6104(class_1268.field_5808);
      }
   }

   private void finishSequence(class_1657 target) {
      phase = IDLE;
      phaseTicks = 0;
      sequenceTicks = 0;
      hitDelayTicks = 0;
      targetId = -1;
      base = null;
      if (target != null && target.method_5805()) {
         trackedTargetId = target.method_5628();
         lastTargetHealth = target.method_6032();
      } else {
         trackedTargetId = -1;
         lastTargetHealth = Float.NaN;
      }
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1687 == null || mc.field_1761 == null || mc.field_1755 != null) return;

      if (phase == IDLE) {
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
         if (!wasHit || countInventory(player, class_1802.field_8301) < 2) return;

         targetId = target.method_5628();
         // DTap always starts with a fresh obsidian block. There is no
         // nearby-obsidian radius that suppresses a new placement.
         base = findSideBase(player, target);
         setPhase(PLACE_OBSIDIAN);
         hitDelayTicks = hitDelay.getInt();
         sequenceTicks = 0;
         return;
      }

      class_1657 target = findTrackedTarget(targetId);
      if (target == null || ++sequenceTicks > 180) {
         finishSequence(target);
         return;
      }

      if (hitDelayTicks > 0) {
         hitDelayTicks--;
         return;
      }

      phaseTicks++;
      switch (phase) {
         case PLACE_OBSIDIAN:
            if (base == null || (!isObsidian(base) && !validBase(base))) {
               base = findSideBase(player, target);
            }
            if (base == null) return;
            if (isObsidian(base)) {
               setPhase(PLACE_FIRST);
               return;
            }
            if (countInventory(player, class_1802.field_8281) < 1) {
               finishSequence(target);
               return;
            }
            if (phaseTicks % 3 == 1 && placeObsidian(player, base)) setPhase(WAIT_OBSIDIAN);
            break;

         case WAIT_OBSIDIAN:
            if (isObsidian(base)) {
               setPhase(PLACE_FIRST);
            } else if (phaseTicks > 25) {
               finishSequence(target);
            } else if (phaseTicks % 4 == 0) {
               placeObsidian(player, base);
            }
            break;

         case PLACE_FIRST:
            if (!isObsidian(base)) {
               setPhase(PLACE_OBSIDIAN);
               return;
            }
            if (findCrystal(base) != null) {
               setPhase(DETONATE_FIRST);
               return;
            }
            if (phaseTicks % 3 == 1 && placeCrystal(player, base)) setPhase(WAIT_FIRST);
            break;

         case WAIT_FIRST:
            if (findCrystal(base) != null) {
               setPhase(DETONATE_FIRST);
            } else if (phaseTicks > 18) {
               setPhase(PLACE_FIRST);
            }
            break;

         case DETONATE_FIRST: {
            class_1511 crystal = findCrystal(base);
            if (crystal == null) {
               if (phaseTicks >= 2) setPhase(WAIT_SECOND);
            } else {
               attackCrystal(player, crystal);
            }
            break;
         }

         case WAIT_SECOND:
            // Allow the first explosion and the totem/equipment swap to resolve.
            if (phaseTicks >= 4) setPhase(PLACE_SECOND);
            break;

         case PLACE_SECOND:
            if (!isObsidian(base)) {
               finishSequence(target);
               return;
            }
            if (findCrystal(base) != null) {
               setPhase(DETONATE_SECOND);
               return;
            }
            if (phaseTicks % 3 == 1 && placeCrystal(player, base)) setPhase(WAIT_SECOND_SPAWN);
            break;

         case WAIT_SECOND_SPAWN:
            if (findCrystal(base) != null) {
               setPhase(DETONATE_SECOND);
            } else if (phaseTicks > 18) {
               setPhase(PLACE_SECOND);
            }
            break;

         case DETONATE_SECOND: {
            class_1511 crystal = findCrystal(base);
            if (crystal == null) {
               if (phaseTicks >= 2) finishSequence(target);
            } else {
               attackCrystal(player, crystal);
            }
            break;
         }

         default:
            finishSequence(target);
      }
   }
}
