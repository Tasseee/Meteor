package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.CombatUtil;
import dev.nightfall.util.InvUtil;
import net.minecraft.class_1268;
import net.minecraft.class_1657;
import net.minecraft.class_1743;
import net.minecraft.class_746;

public final class ShieldBreaker extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 4.2, 2.0, 6.0, 0.2));
   private final NumberSetting delay = this.add(new NumberSetting("Angriffsintervall (Ticks)", 1.0, 1.0, 8.0, 1.0));
   private int cooldown;

   public ShieldBreaker() {
      super("ShieldBreaker", "Schneller Axtwechsel und Angriff gegen blockende Gegner.", Category.COMBAT);
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1687 == null || mc.field_1761 == null || mc.field_1755 != null) {
         return;
      }

      if (this.cooldown > 0) {
         this.cooldown--;
         if (this.cooldown > 0) return;
      }

      class_1657 target = this.nearestShieldingPlayer(player);
      if (target == null) return;

      int axeSlot = this.findAxe(player);
      if (axeSlot < 0) return;

      int previousSlot = InvUtil.selected();
      if (!InvUtil.select(axeSlot)) return;

      // InvUtil.select sends the held-slot packet immediately. The attack follows
      // in the same tick, then the original slot is restored without waiting a tick.
      mc.field_1761.method_2918(player, target);
      player.method_6104(class_1268.field_5808);
      if (previousSlot != axeSlot) InvUtil.select(previousSlot);

      this.cooldown = Math.max(0, this.delay.getInt() - 1);
   }

   private class_1657 nearestShieldingPlayer(class_746 player) {
      class_1657 best = null;
      double bestDistanceSquared = this.range.get() * this.range.get();

      for (class_1657 candidate : mc.field_1687.method_18456()) {
         if (candidate == player || !candidate.method_5805() || candidate.method_7325()
            || !candidate.method_6039() || CombatUtil.isFriend(candidate)
            || !player.method_6057(candidate)) {
            continue;
         }

         double distanceSquared = player.method_5858(candidate);
         if (distanceSquared <= bestDistanceSquared) {
            best = candidate;
            bestDistanceSquared = distanceSquared;
         }
      }

      return best;
   }

   private int findAxe(class_746 player) {
      for (int i = 0; i < 9; i++) {
         if (player.method_31548().method_5438(i).method_7909() instanceof class_1743) {
            return i;
         }
      }

      return -1;
   }

   @Override
   protected void onDisable() {
      this.cooldown = 0;
   }

   @Override
   public void onWorldLeave() {
      this.cooldown = 0;
   }
}
