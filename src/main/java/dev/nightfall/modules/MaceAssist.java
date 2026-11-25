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
import net.minecraft.class_3532;
import net.minecraft.class_746;

public class MaceAssist extends Module {
   private final NumberSetting minFall = this.add(new NumberSetting("Min. Fallhoehe", 1.5, 1.5, 20.0, 0.5));
   private final NumberSetting range = this.add(new NumberSetting("Angriffs-Reichweite", 3.2, 1.0, 6.0, 0.1));
   private final BoolSetting visibleOnly = this.add(new BoolSetting("Nur sichtbares Ziel", true));
   private final BoolSetting autoSwitch = this.add(new BoolSetting("Auto-Switch zur Mace", true));
   private final BoolSetting switchBack = this.add(new BoolSetting("Zurueckwechseln", true));
   private final BoolSetting autoAttack = this.add(new BoolSetting("Auto-Angriff", true));
   private final BoolSetting players = this.add(new BoolSetting("Spieler", true));
   private final BoolSetting mobs = this.add(new BoolSetting("Mobs", false));
   private final BoolSetting aim = this.add(new BoolSetting("Auto-Aim", true));
   private final NumberSetting aimSpeed = this.add(new NumberSetting("Aim Speed (Grad/Tick)", 180.0, 15.0, 180.0, 5.0));
   public double estimate;
   private int prevSlot = -1;

   public MaceAssist() {
      super("MaceAssist", "Mace-Smash mit Zielauswahl, Auto-Aim und Auto-Switch.", Category.COMBAT);
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
      if (this.switchBack.get() && this.prevSlot >= 0 && mc.field_1724 != null) {
         InvUtil.select(this.prevSlot);
      }

      this.prevSlot = -1;
   }

   public static double smashDamage(double fall) {
      double bonus = fall <= 3.0 ? 4.0 * fall : (fall <= 8.0 ? 12.0 + 2.0 * (fall - 3.0) : 22.0 + (fall - 8.0));
      return 6.0 + bonus;
   }

   private class_1309 findTarget(class_746 player) {
      if (mc.field_1687 == null) return null;

      class_1309 best = null;
      double bestDistance = this.range.get();

      for (class_1297 entity : mc.field_1687.method_18112()) {
         if (!(entity instanceof class_1309 living) || living == player || !living.method_5805()) continue;

         if (living instanceof class_1657 other) {
            if (!this.players.get() || other.method_7325() || CombatUtil.isFriend(other)) continue;
         } else if (!this.mobs.get()) {
            continue;
         }

         double distance = player.method_5739(living);
         if (distance > bestDistance) continue;
         if (this.visibleOnly.get() && !player.method_6057(living)) continue;

         best = living;
         bestDistance = distance;
      }

      return best;
   }

   private void aimAt(class_746 player, class_1309 target) {
      if (!this.aim.get() || target == null) return;

      double dx = target.method_23317() - player.method_23317();
      double dy = target.method_23323(0.62) - player.method_33571().field_1351;
      double dz = target.method_23321() - player.method_23321();
      float desiredYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float desiredPitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));

      float yawDelta = class_3532.method_15393(desiredYaw - player.method_36454());
      float pitchDelta = desiredPitch - player.method_36455();
      float maxStep = this.aimSpeed.get().floatValue();

      player.method_36456(player.method_36454() + class_3532.method_15363(yawDelta, -maxStep, maxStep));
      player.method_36457(class_3532.method_15363(player.method_36455() + class_3532.method_15363(pitchDelta, -maxStep, maxStep), -90.0F, 90.0F));
   }

   private boolean aimedAt(class_746 player, class_1309 target) {
      if (!this.aim.get() || target == null) return true;

      double dx = target.method_23317() - player.method_23317();
      double dy = target.method_23323(0.62) - player.method_33571().field_1351;
      double dz = target.method_23321() - player.method_23321();
      float desiredYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float desiredPitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
      float yawError = Math.abs(class_3532.method_15393(desiredYaw - player.method_36454()));
      float pitchError = Math.abs(desiredPitch - player.method_36455());
      return yawError <= 8.0F && pitchError <= 8.0F;
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      this.estimate = 0.0;
      if (player == null || mc.field_1755 != null || mc.field_1761 == null || mc.field_1687 == null) return;

      double fall = player.field_6017;
      if (player.method_24828() || fall < this.minFall.get() || player.method_5799() || player.method_6128()) {
         if (player.method_24828() && this.prevSlot >= 0) this.restoreSlot();
         return;
      }

      class_1309 target = this.findTarget(player);
      this.estimate = smashDamage(fall);
      if (target != null) this.aimAt(player, target);

      if (!player.method_6047().method_31574(class_1802.field_49814)) {
         if (!this.autoSwitch.get()) return;

         int maceSlot = InvUtil.findHotbar(class_1802.field_49814);
         if (maceSlot < 0) return;

         if (this.prevSlot < 0) this.prevSlot = InvUtil.selected();
         InvUtil.select(maceSlot);
      }

      if (this.autoAttack.get()
         && target != null
         && player.method_5739(target) <= this.range.get()
         && (!this.visibleOnly.get() || player.method_6057(target))
         && this.aimedAt(player, target)
         && player.method_7261(0.5F) >= 0.95F) {
         mc.field_1761.method_2918(player, target);
         player.method_6104(class_1268.field_5808);
      }
   }
}
