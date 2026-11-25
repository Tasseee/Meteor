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
   public double estimate;
   private int prevSlot = -1;
   private int maceSlot = -1;
   private boolean userChangedSlot = false;
   private boolean swapActive = false;

   public MaceAssist() {
      super("MaceAssist", "Mace-Smash: Auto-Switch, Zielerfassung und Angriff bei ausreichender Fallhoehe.", Category.COMBAT);
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
         && !this.userChangedSlot && InvUtil.selected() == this.maceSlot) {
         InvUtil.select(this.prevSlot);
      }

      this.prevSlot = -1;
      this.maceSlot = -1;
      this.userChangedSlot = false;
      this.swapActive = false;
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
         && p.method_5739(me) <= this.range.get()
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

            // If the player changes the hotbar slot while our swap is active,
            // immediately cancel the swap. Never fight the player's selection.
            if (this.swapActive && InvUtil.selected() != this.maceSlot) {
               this.userChangedSlot = true;
               this.swapActive = false;
            }

            if (!this.userChangedSlot && !p.method_6047().method_31574(class_1802.field_49814)) {
               if (!this.autoSwitch.get()) {
                  return;
               }

               int s = InvUtil.findHotbar(class_1802.field_49814);
               if (s < 0) {
                  return;
               }

               if (this.prevSlot < 0) {
                  this.prevSlot = InvUtil.selected();
                  this.maceSlot = s;
                  this.swapActive = true;
               }

               InvUtil.select(s);
            }

            this.estimate = smashDamage(fall);

            // Triggerbot: attack only the entity the player is actually
            // looking at with the Mace. No forced camera angle and no
            // requirement to hit the exact center of the target.
            class_1309 crosshairTarget = mc.field_1692 instanceof class_1309 entity ? entity : null;
            boolean lookingAtTarget = crosshairTarget != null
               && crosshairTarget != p
               && crosshairTarget.method_5805()
               && p.method_5739(crosshairTarget) <= 3.0
               && (!(crosshairTarget instanceof class_1657 player)
                  || (this.players.get() && this.validPlayerTarget(player, p)))
               && (!(crosshairTarget instanceof class_1657) || this.players.get())
               && (crosshairTarget instanceof class_1657 || this.mobs.get());

            if (this.autoAttack.get() && lookingAtTarget
               && p.method_18798().field_1351 < -0.03
               && p.method_6047().method_31574(class_1802.field_49814)) {
               mc.field_1761.method_2918(p, crosshairTarget);
               p.method_6104(class_1268.field_5808);
            }
         } else if (p.method_24828() && (this.prevSlot >= 0 || this.userChangedSlot || this.swapActive)) {
            if (this.switchBack.get() && !this.userChangedSlot && InvUtil.selected() == this.maceSlot) {
               InvUtil.select(this.prevSlot);
            }
            this.prevSlot = -1;
            this.maceSlot = -1;
            this.userChangedSlot = false;
         }
      }
   }
}
