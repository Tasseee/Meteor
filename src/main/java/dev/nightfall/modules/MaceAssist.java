package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1297;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_746;

public class MaceAssist extends Module {
   private final NumberSetting minFall = this.add(new NumberSetting("Min. Fallhoehe", 1.5, 1.5, 20.0, 0.5));
   private final NumberSetting maceSwapHeight = this.add(new NumberSetting("Mace-Swap Hoehe", 1.5, 1.5, 20.0, 0.5));
   private final NumberSetting range = this.add(new NumberSetting("Angriffs-Reichweite", 3.2, 1.0, 6.0, 0.1));
   private final NumberSetting aimRange = this.add(new NumberSetting("Aim-Reichweite", 8.0, 2.0, 16.0, 0.5));
   private final NumberSetting snapSmoothness = this.add(new NumberSetting("Aim-Snap Smoothness", 0.0, 0.0, 100.0, 1.0));
   private final BoolSetting visibleOnly = this.add(new BoolSetting("Nur sichtbares Ziel", true));
   private final BoolSetting autoSwitch = this.add(new BoolSetting("Auto-Switch zur Mace", true));
   private final BoolSetting switchBack = this.add(new BoolSetting("Zurueckwechseln", true));
   private final BoolSetting autoAttack = this.add(new BoolSetting("Auto-Angriff", true));
   private final BoolSetting players = this.add(new BoolSetting("Spieler", true));
   private final BoolSetting mobs = this.add(new BoolSetting("Mobs", false));

   public double estimate;
   private int prevSlot = -1;
   private int maceSlot = -1;
   private boolean swapActive = false;

   public MaceAssist() {
      super("MaceAssist", "Mace-Smash: Ziel erkennen, beim Fallen auf das Ziel snappen und automatisch zur Mace wechseln.", Category.COMBAT);
   }

   @Override
   protected void onDisable() {
      this.restoreSlot();
      this.estimate = 0.0;
   }

   @Override
   public void onWorldLeave() {
      this.restoreSlot();
      this.estimate = 0.0;
   }

   private void restoreSlot() {
      if (this.switchBack.get() && this.prevSlot >= 0 && mc.field_1724 != null
         && InvUtil.selected() == this.maceSlot) {
         InvUtil.select(this.prevSlot);
      }

      this.prevSlot = -1;
      this.maceSlot = -1;
      this.swapActive = false;
   }

   public static double smashDamage(double fall) {
      double bonus = fall <= 3.0 ? 4.0 * fall : (fall <= 8.0 ? 12.0 + 2.0 * (fall - 3.0) : 22.0 + (fall - 8.0));
      return 6.0 + bonus;
   }

   private boolean validPlayerTarget(class_1657 target, class_746 me) {
      return target != null
         && target != me
         && target.method_5805()
         && !target.method_7325()
         && !CombatUtil.isFriend(target)
         && target.method_5739(me) <= this.aimRange.get()
         && (!this.visibleOnly.get() || me.method_6057(target));
   }

   private class_1309 findFallingTarget(class_746 me) {
      if (mc.field_1692 instanceof class_1309 crosshair && crosshair != me && crosshair.method_5805()) {
         if (crosshair instanceof class_1657 player) {
            if (this.players.get() && this.validPlayerTarget(player, me)) return player;
         } else if (this.mobs.get() && me.method_5739(crosshair) <= this.aimRange.get()
            && (!this.visibleOnly.get() || me.method_6057(crosshair))) {
            return crosshair;
         }
      }

      if (this.players.get()) {
         class_1657 nearest = CombatUtil.nearestPlayer(this.aimRange.get());
         if (this.validPlayerTarget(nearest, me)) {
            return nearest;
         }
      }

      return null;
   }

   private void aimAt(class_1309 target, class_746 me) {
      class_243 eye = me.method_33571();
      double dx = target.method_23317() - eye.field_1352;
      double dz = target.method_23321() - eye.field_1350;
      double dy = target.method_23323(0.55) - eye.field_1351;

      float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));

      double smooth = this.snapSmoothness.get();
      if (smooth <= 0.0) {
         me.method_36456(yaw);
         me.method_36457(class_3532.method_15363(pitch, -90.0F, 90.0F));
         return;
      }

      // 0 = instant snap, 100 = slowest snap.
      // The interpolation is exponential, so it remains responsive while
      // still allowing a very smooth transition at higher values.
      float factor = (float)Math.max(0.03, 1.0 - smooth / 100.0);
      float yawError = class_3532.method_15393(yaw - me.method_36454());
      float pitchError = pitch - me.method_36455();

      me.method_36456(me.method_36454() + yawError * factor);
      me.method_36457(class_3532.method_15363(me.method_36455() + pitchError * factor, -90.0F, 90.0F));
   }

   private boolean isAimAligned(class_746 me, class_1309 target) {
      class_243 eye = me.method_33571();
      double dx = target.method_23317() - eye.field_1352;
      double dy = target.method_23323(0.55) - eye.field_1351;
      double dz = target.method_23321() - eye.field_1350;

      float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));

      float yawError = Math.abs(class_3532.method_15393(yaw - me.method_36454()));
      float pitchError = Math.abs(pitch - me.method_36455());
      return yawError <= 10.0F && pitchError <= 10.0F;
   }

   private void switchToMace(class_746 me) {
      if (!this.autoSwitch.get()) return;

      int slot = InvUtil.findHotbar(class_1802.field_49814);
      if (slot < 0) return;

      if (me.method_6047().method_31574(class_1802.field_49814)) {
         if (this.maceSlot < 0) {
            this.maceSlot = InvUtil.selected();
         }
         return;
      }

      if (this.prevSlot < 0) {
         this.prevSlot = InvUtil.selected();
         this.maceSlot = slot;
         this.swapActive = true;
      } else if (this.maceSlot < 0) {
         this.maceSlot = slot;
      }

      // This intentionally does not respect a manual hotbar change while the
      // fall is active: once the configured fall height is reached, every
      // qualifying fall switches to the Mace.
      InvUtil.select(slot);
      this.swapActive = true;
   }

   @Override
   public void onTick() {
      class_746 me = mc.field_1724;
      this.estimate = 0.0;

      if (me == null || mc.field_1755 != null || mc.field_1761 == null || mc.field_1687 == null) {
         return;
      }

      double fall = me.field_6017;
      boolean falling = !me.method_24828()
         && fall >= this.minFall.get()
         && !me.method_5799()
         && !me.method_6128()
         && me.method_18798().field_1351 < -0.01;

      if (!falling) {
         if (me.method_24828() && (this.prevSlot >= 0 || this.swapActive)) {
            this.restoreSlot();
         }
         return;
      }

      class_1309 target = this.findFallingTarget(me);
      if (target == null) return;

      // Aim assist starts for every qualifying falling target, independently
      // from Auto-Switch and Auto-Attack.
      this.aimAt(target, me);

      // The Mace swap is also independent from Auto-Attack. Reaching the
      // configured fall height is enough to trigger the switch.
      if (fall >= this.maceSwapHeight.get()) {
         this.switchToMace(me);
      }
      this.estimate = smashDamage(fall);

      class_1309 crosshairTarget = mc.field_1692 instanceof class_1309 entity ? entity : null;
      boolean lookingAtTarget = crosshairTarget != null
         && crosshairTarget != me
         && crosshairTarget.method_5805()
         && me.method_5739(crosshairTarget) <= this.range.get()
         && (!(crosshairTarget instanceof class_1657 player)
            || (this.players.get() && this.validPlayerTarget(player, me)))
         && (!(crosshairTarget instanceof class_1657) || this.players.get())
         && (crosshairTarget instanceof class_1657 || this.mobs.get())
         && this.isAimAligned(me, target);

      if (this.autoAttack.get()
         && lookingAtTarget
         && me.method_18798().field_1351 < -0.03
         && me.method_6047().method_31574(class_1802.field_49814)) {
         mc.field_1761.method_2918(me, crosshairTarget);
         me.method_6104(class_1268.field_5808);
      }
   }
}
