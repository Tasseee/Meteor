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
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_3965;
import net.minecraft.class_746;

public final class CrystalTest extends Module {
   private final NumberSetting targetRange = this.add(new NumberSetting("Ziel-Reichweite", 6.0, 3.0, 12.0, 0.5));
   private final NumberSetting placeRange = this.add(new NumberSetting("Platzier-Reichweite", 4.5, 2.0, 6.0, 0.1));
   private final NumberSetting breakRange = this.add(new NumberSetting("Zuend-Reichweite", 4.5, 2.0, 6.0, 0.1));
   private final NumberSetting turnSpeed = this.add(new NumberSetting("Drehgeschwindigkeit", 180.0, 5.0, 180.0, 5.0));
   private final NumberSetting placeDelay = this.add(new NumberSetting("Platzierdelay", 1.0, 0.0, 15.0, 1.0));
   private final NumberSetting spawnTimeout = this.add(new NumberSetting("Spawn-Timeout", 15.0, 3.0, 40.0, 1.0));
   private final NumberSetting betweenCrystals = this.add(new NumberSetting("Pause zwischen Crystals", 3.0, 0.0, 20.0, 1.0));

   private static final int IDLE = 0, LOOK_OBSIDIAN = 1, PLACE_OBSIDIAN = 2,
      WAIT_OBSIDIAN = 3, LOOK_CRYSTAL_1 = 4, PLACE_CRYSTAL_1 = 5,
      WAIT_CRYSTAL_1 = 6, BREAK_CRYSTAL_1 = 7, WAIT_BETWEEN = 8,
      LOOK_CRYSTAL_2 = 9, PLACE_CRYSTAL_2 = 10, BREAK_CRYSTAL_2 = 11;

   private int phase;
   private int ticks;
   private int targetId = -1;
   private class_2338 base;
   private int queuedHits;
   private int queuedHitTargetId = -1;

   public CrystalTest() {
      super("CrystalTest", "Test-CrystalAura: schaut vor jeder Obsidian- und Crystal-Interaktion auf den Zielblock.", Category.COMBAT);
   }

   @Override protected void onEnable() { reset(); }
   @Override protected void onDisable() { reset(); }
   @Override public void onWorldLeave() { reset(); }

   private void reset() {
      phase = IDLE;
      ticks = 0;
      targetId = -1;
      base = null;
      queuedHits = 0;
      queuedHitTargetId = -1;
   }

   private void next(int value) { phase = value; ticks = 0; }

   public void onConfirmedMeleeHit(class_1657 target) {
      if (!isEnabled() || mc.field_1724 == null || mc.field_1687 == null) return;
      if (CombatUtil.nearestPlayer(targetRange.get()) != target) return;
      if (queuedHitTargetId != target.method_5628()) {
         queuedHitTargetId = target.method_5628();
         queuedHits = 0;
      }
      queuedHits = Math.min(queuedHits + 1, 32);
   }

   private class_1657 findTarget(int id) {
      for (class_1657 p : mc.field_1687.method_18456())
         if (p.method_5628() == id && p.method_5805()) return p;
      return null;
   }

   private boolean isObsidian(class_2338 pos) {
      return pos != null && mc.field_1687.method_8320(pos).method_27852(class_2246.field_9987);
   }

   private boolean clearForCrystal(class_2338 pos) {
      if (!mc.field_1687.method_8320(pos.method_10084()).method_26215()
         || !mc.field_1687.method_8320(pos.method_10084().method_10084()).method_26215()) return false;
      class_238 box = new class_238(pos.method_10263(), pos.method_10264() + 1.0, pos.method_10260(),
         pos.method_10263() + 1.0, pos.method_10264() + 3.0, pos.method_10260() + 1.0);
      return mc.field_1687.method_8335(null, box).isEmpty();
   }

   private class_2338 findBase(class_746 player, class_1657 target) {
      class_2338 feet = target.method_24515();
      // Search outward in rings and skip already occupied obsidian positions.
      for (int drop = 0; drop <= 3; drop++) {
         for (int radius = 1; radius <= 5; radius++) {
            for (int x = -radius; x <= radius; x++) {
               for (int z = -radius; z <= radius; z++) {
                  if (Math.max(Math.abs(x), Math.abs(z)) != radius) continue;
                  class_2338 pos = feet.method_10069(x, -drop, z);
                  if (!mc.field_1687.method_8320(pos).method_45474()) continue;
                  class_2338 support = pos.method_10074();
                  if (mc.field_1687.method_8320(support).method_26220(mc.field_1687, support).method_1110()) continue;
                  if (!clearForCrystal(pos)) continue;
                  class_243 aim = new class_243(pos.method_10263() + 0.5, pos.method_10264() + 1.0, pos.method_10260() + 0.5);
                  if (player.method_33571().method_1022(aim) > placeRange.get()) continue;
                  return pos;
               }
            }
         }
      }
      return null;
   }

   private class_243 obsidianAim() {
      return new class_243(base.method_10263() + 0.5, base.method_10264(), base.method_10260() + 0.5);
   }

   private class_243 crystalAim() {
      return new class_243(base.method_10263() + 0.5, base.method_10264() + 1.0, base.method_10260() + 0.5);
   }

   private static float wrapDegrees(float value) {
      value %= 360.0F;
      if (value >= 180.0F) value -= 360.0F;
      if (value < -180.0F) value += 360.0F;
      return value;
   }

   private static float clamp(float value, float min, float max) {
      return Math.max(min, Math.min(max, value));
   }

   private void lookAt(class_746 player, class_243 point) {
      class_243 eye = player.method_33571();
      double dx = point.field_1352 - eye.field_1352;
      double dy = point.field_1351 - eye.field_1351;
      double dz = point.field_1350 - eye.field_1350;
      float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
      float max = turnSpeed.get().floatValue();
      player.method_36456(player.method_36454() + clamp(wrapDegrees(yaw - player.method_36454()), -max, max));
      player.method_36457(player.method_36455() + clamp(pitch - player.method_36455(), -max, max));
   }

   private boolean facing(class_746 player, class_243 point) {
      class_243 eye = player.method_33571();
      double dx = point.field_1352 - eye.field_1352;
      double dy = point.field_1351 - eye.field_1351;
      double dz = point.field_1350 - eye.field_1350;
      float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
      return Math.abs(wrapDegrees(yaw - player.method_36454())) < 5.0F
         && Math.abs(pitch - player.method_36455()) < 5.0F;
   }

   private boolean placeObsidian(class_746 player) {
      if (isObsidian(base)) return true;
      int slot = InvUtil.ensureHotbar(class_1802.field_8281);
      if (slot < 0 || !InvUtil.hotbarStack(slot).method_31574(class_1802.field_8281)
         || !facing(player, obsidianAim())) return false;
      // Equip on its own tick. Keep the slot selected until the interaction is sent.
      if (slot != InvUtil.selected()) {
         InvUtil.select(slot);
         return false;
      }
      class_2338 support = base.method_10074();
      class_3965 hit = new class_3965(obsidianAim(), class_2350.field_11036, support, false);
      mc.field_1761.method_2896(player, class_1268.field_5808, hit);
      player.method_6104(class_1268.field_5808);
      return isObsidian(base);
   }

   private class_1511 findCrystal() {
      class_243 center = crystalAim();
      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (entity instanceof class_1511 c && c.method_5805()
            && new class_243(c.method_23317(), c.method_23318(), c.method_23321()).method_1022(center) < 1.5) return c;
      }
      return null;
   }

   private boolean placeCrystal(class_746 player) {
      if (!isObsidian(base) || !clearForCrystal(base)
         || player.method_33571().method_1022(crystalAim()) > placeRange.get()) return false;

      class_1268 hand = player.method_6079().method_31574(class_1802.field_8301)
         ? class_1268.field_5810
         : (player.method_6047().method_31574(class_1802.field_8301) ? class_1268.field_5808 : null);
      if (hand == null) {
         int slot = InvUtil.ensureHotbar(class_1802.field_8301);
         if (slot < 0 || !InvUtil.hotbarStack(slot).method_31574(class_1802.field_8301)) return false;
         // Equip on its own tick so the server has the crystal in the selected hand.
         if (slot != InvUtil.selected()) {
            InvUtil.select(slot);
            return false;
         }
         if (!player.method_6047().method_31574(class_1802.field_8301)) return false;
         hand = class_1268.field_5808;
      }

      class_3965 hit = new class_3965(crystalAim(), class_2350.field_11036, base, false);
      boolean accepted = mc.field_1761.method_2896(player, hand, hit).method_23665();
      player.method_6104(hand);
      return accepted || findCrystal() != null;
   }

   private void attack(class_746 player) {
      class_1511 crystal = findCrystal();
      if (crystal != null && player.method_5739(crystal) <= breakRange.get()) {
         mc.field_1761.method_2918(player, crystal);
         player.method_6104(class_1268.field_5808);
      }
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1687 == null || mc.field_1761 == null || mc.field_1755 != null) return;

      if (phase == IDLE) {
         class_1657 target = CombatUtil.nearestPlayer(targetRange.get());
         if (target == null) {
            targetId = -1;
            return;
         }
         targetId = target.method_5628();
         if (queuedHitTargetId != targetId || queuedHits <= 0) return;

         queuedHits--;
         base = findBase(player, target);
         if (base == null) {
            queuedHits++;
            return;
         }
         next(LOOK_OBSIDIAN);
         return;
      }

      class_1657 target = findTarget(targetId);
      if (target == null || ++ticks > 120) { reset(); return; }

      switch (phase) {
         case LOOK_OBSIDIAN:
            lookAt(player, obsidianAim());
            if (facing(player, obsidianAim())) next(PLACE_OBSIDIAN);
            break;
         case PLACE_OBSIDIAN:
            if (isObsidian(base)) next(LOOK_CRYSTAL_1);
            else if (ticks > placeDelay.getInt()) placeObsidian(player);
            break;
         case LOOK_CRYSTAL_1:
            lookAt(player, crystalAim());
            if (facing(player, crystalAim())) next(PLACE_CRYSTAL_1);
            break;
         case PLACE_CRYSTAL_1:
            if (findCrystal() != null) next(BREAK_CRYSTAL_1);
            else {
               lookAt(player, crystalAim());
               if (facing(player, crystalAim()) && ticks > placeDelay.getInt()) placeCrystal(player);
            }
            break;
         case BREAK_CRYSTAL_1:
            if (findCrystal() == null) next(WAIT_BETWEEN);
            else attack(player);
            break;
         case WAIT_BETWEEN:
            // Der erste Crystal ist entfernt. Derselbe Obsidian-Block bleibt
            // bestehen; vor dem zweiten Crystal wird erneut sichtbar darauf gezielt.
            if (ticks >= betweenCrystals.getInt()) next(LOOK_CRYSTAL_2);
            break;
         case LOOK_CRYSTAL_2:
            lookAt(player, crystalAim());
            if (facing(player, crystalAim())) next(PLACE_CRYSTAL_2);
            break;
         case PLACE_CRYSTAL_2:
            if (findCrystal() != null) next(BREAK_CRYSTAL_2);
            else {
               lookAt(player, crystalAim());
               if (facing(player, crystalAim()) && ticks > placeDelay.getInt()) placeCrystal(player);
            }
            break;
         case BREAK_CRYSTAL_2:
            if (findCrystal() == null) { next(IDLE); base = null; }
            else attack(player);
            break;
         default:
            reset();
      }
   }
}
