package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1309;
import net.minecraft.class_1657;
import net.minecraft.class_1802;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_746;

public class MaceAssist extends Module {
   private final NumberSetting minFall = this.add(new NumberSetting("Min. Fallhoehe", 1.5, 1.5, 20.0, 0.5));
   private final NumberSetting range = this.add(new NumberSetting("Angriffs-Reichweite", 3.0, 1.0, 6.0, 0.1));
   private final NumberSetting aimRange = this.add(new NumberSetting("Aim-Reichweite", 6.0, 3.0, 12.0, 0.1));
   private final NumberSetting hitDelay = this.add(new NumberSetting("Hit-Verzoegerung (Ticks)", 0.0, 0.0, 20.0, 1.0));
   private final BoolSetting visibleOnly = this.add(new BoolSetting("Nur sichtbares Ziel", true));
   private final BoolSetting autoSwitch = this.add(new BoolSetting("Auto-Switch zur Mace", true));
   private final BoolSetting switchBack = this.add(new BoolSetting("Zurueckwechseln", true));
   private final BoolSetting autoAttack = this.add(new BoolSetting("Auto-Angriff", true));
   private final BoolSetting players = this.add(new BoolSetting("Spieler", true));
   private final BoolSetting mobs = this.add(new BoolSetting("Mobs", false));
   public double estimate;
   private int prevSlot = -1;
   private int readyTicks;
   private int readyTargetId = -1;

   public MaceAssist() {
      super("MaceAssist", "Mace-Smash: Auto-Switch, Zielerfassung und Angriff bei ausreichender Fallhoehe.", Category.COMBAT);
   }

   @Override
   protected void onDisable() {
      this.restoreSlot();
      this.estimate = 0.0;
      this.readyTicks = 0;
      this.readyTargetId = -1;
   }

   @Override
   public void onWorldLeave() {
      this.restoreSlot();
      this.estimate = 0.0;
      this.readyTicks = 0;
      this.readyTargetId = -1;
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

   private boolean validPlayerTarget(class_1657 p, class_746 me) {
      return p != null
         && p != me
         && p.method_5805()
         && !p.method_7325()
         && !CombatUtil.isFriend(p)
         && p.method_5739(me) <= this.aimRange.get()
         && (!this.visibleOnly.get() || me.method_6057(p));
   }

   private class_1309 findFallingTarget(class_746 me) {
      if (mc.field_1692 instanceof class_1309 crosshair && crosshair != me && crosshair.method_5805()) {
         if (crosshair instanceof class_1657 player) {
            if (this.players.get() && this.validPlayerTarget(player, me)) return player;
         } else if (this.mobs.get() && me.method_5739(crosshair) <= this.range.get()
            && (!this.visibleOnly.get() || me.method_6057(crosshair))) {
            return crosshair;
         }
      }

      if (this.players.get()) {
         class_1657 nearest = CombatUtil.nearestPlayer(this.range.get());
         if (this.validPlayerTarget(nearest, me)) {
            return nearest;
         }
      }

      return null;
   }

   private void aimAt(class_1309 target, class_746 me) {
      class_243Target:
      {
         double dx = target.method_23317() - me.method_33571().field_1352;
         double dz = target.method_23321() - me.method_33571().field_1350;
         double dy = target.method_23323(0.55) - me.method_33571().field_1351;
         float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
         float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
         me.method_36456(yaw);
         me.method_36457(class_3532.method_15363(pitch, -90.0F, 90.0F));
         break class_243Target;
      }
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      this.estimate = 0.0;
      if (p != null && mc.field_1755 == null && mc.field_1761 != null) {
         double fall = p.field_6017;
         if (!p.method_24828() && !(fall < this.minFall.get()) && !p.method_5799() && !p.method_6128()) {
            class_1309 target = this.findFallingTarget(p);

            if (target != null) {
               this.aimAt(target, p);

               // Bereits vor der normalen Hit-Distanz horizontal zum aktuellen
               // Zielpunkt ziehen. Die vertikale Windcharge-/Fallbewegung bleibt erhalten.
               if (!p.method_24828()) {
                  double dx = target.method_23317() - p.method_23317();
                  double dz = target.method_23321() - p.method_23321();
                  double horizontal = Math.hypot(dx, dz);
                  if (horizontal > 0.15 && horizontal <= this.aimRange.get() + 2.0) {
                     class_243 velocity = p.method_18798();
                     double desiredX = dx / horizontal * 0.42;
                     double desiredZ = dz / horizontal * 0.42;
                     double steer = 0.10;
                     double nextX = velocity.field_1352 + (desiredX - velocity.field_1352) * steer;
                     double nextZ = velocity.field_1350 + (desiredZ - velocity.field_1350) * steer;
                     p.method_18800(nextX, velocity.field_1351, nextZ);
                  }
               }
            }

            if (!p.method_6047().method_31574(class_1802.field_49814)) {
               if (!this.autoSwitch.get()) {
                  return;
               }

               int s = InvUtil.findHotbar(class_1802.field_49814);
               if (s < 0) {
                  return;
               }

               if (this.prevSlot < 0) {
                  this.prevSlot = InvUtil.selected();
               }

               InvUtil.select(s);
            }

            this.estimate = smashDamage(fall);

            boolean hitReady = target != null
               && p.method_5739(target) <= Math.min(this.range.get(), 3.0)
               && (p.method_23318() - target.method_23318()) <= 2.2
               && (p.method_23318() - target.method_23318()) >= 0.0
               && p.method_18798().field_1351 < -0.03;

            if (!hitReady) {
               this.readyTicks = 0;
               this.readyTargetId = -1;
            } else {
               int targetId = target.method_5628();
               if (this.readyTargetId != targetId) {
                  this.readyTargetId = targetId;
                  this.readyTicks = 0;
               }

               // Start counting only once the target is actually in the hit-ready
               // state. The configurable delay therefore happens after range,
               // height and attack-readiness have been detected.
               this.readyTicks++;

               if (this.autoAttack.get() && this.readyTicks > this.hitDelay.getInt()) {
                  mc.field_1761.method_2918(p, target);
                  p.method_6104(class_1268.field_5808);
                  this.readyTicks = 0;
               }
            }
         } else if (p.method_24828() && this.prevSlot >= 0 && this.switchBack.get()) {
            InvUtil.select(this.prevSlot);
            this.prevSlot = -1;
         }
      }
   }
}
