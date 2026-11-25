package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.InvUtil;
import dev.nightfall.util.NotificationCenter;
import net.minecraft.class_1268;
import net.minecraft.class_1802;
import net.minecraft.class_746;

/**
 * One-shot pearl -> wind-charge sequence. Bind a key to this module;
 * it automatically disables itself after the sequence finishes.
 */
public final class PearlCatch extends Module {
   private final NumberSetting delay = this.add(new NumberSetting("Delay bis Windladung (Ticks)", 3.0, 0.0, 20.0, 1.0));

   private int pearlSlot = -1;
   private int windSlot = -1;
   private int originalSlot = -1;
   private int waitTicks;
   private float originalYaw;
   private float originalPitch;
   private boolean savedView;
   private boolean prepared;
   private boolean pearlUsed;

   public PearlCatch() {
      super("PearlCatch", "Einmalig nach oben schauen, Enderperle werfen und danach Windladung nutzen.", Category.COMBAT);
   }

   @Override
   protected void onEnable() {
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1761 == null || mc.field_1687 == null) {
         this.setEnabled(false);
         return;
      }

      this.pearlSlot = InvUtil.findHotbar(class_1802.field_8634);
      this.windSlot = InvUtil.findHotbar(class_1802.field_49098);
      if (this.pearlSlot < 0 || this.windSlot < 0) {
         NotificationCenter.push("PearlCatch", "Enderperle und Windladung muessen in der Hotbar liegen.");
         this.pearlSlot = this.windSlot = -1;
         this.setEnabled(false);
         return;
      }

      this.originalSlot = InvUtil.selected();
      this.originalYaw = player.method_36454();
      this.originalPitch = player.method_36455();
      this.savedView = true;
      this.prepared = false;
      this.pearlUsed = false;
      this.waitTicks = 0;

      player.method_36457(-90.0F);
      InvUtil.select(this.pearlSlot);
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1761 == null || mc.field_1687 == null) {
         this.setEnabled(false);
         return;
      }

      // Let the normal player tick send the upward rotation before the pearl-use packet.
      if (!this.prepared) {
         this.prepared = true;
         return;
      }

      if (!this.pearlUsed) {
         mc.field_1761.method_2919(player, class_1268.field_5808);
         player.method_6104(class_1268.field_5808);
         this.pearlUsed = true;
         this.waitTicks = this.delay.getInt();
         return;
      }

      if (this.waitTicks > 0) {
         this.waitTicks--;
         if (this.waitTicks > 0) return;
      }

      InvUtil.select(this.windSlot);
      mc.field_1761.method_2919(player, class_1268.field_5808);
      player.method_6104(class_1268.field_5808);
      this.setEnabled(false);
   }

   private void restoreState() {
      class_746 player = mc.field_1724;
      if (player != null) {
         if (this.originalSlot >= 0) InvUtil.select(this.originalSlot);
         if (this.savedView) {
            player.method_36456(this.originalYaw);
            player.method_36457(this.originalPitch);
         }
      }

      this.pearlSlot = -1;
      this.windSlot = -1;
      this.originalSlot = -1;
      this.waitTicks = 0;
      this.savedView = false;
      this.prepared = false;
      this.pearlUsed = false;
   }

   @Override
   protected void onDisable() {
      this.restoreState();
   }

   @Override
   public void onWorldLeave() {
      if (this.isEnabled()) this.setEnabled(false);
      else this.restoreState();
   }
}
