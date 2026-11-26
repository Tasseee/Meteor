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
import net.minecraft.class_1713;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_243;
import net.minecraft.class_3532;
import net.minecraft.class_746;

public final class ElyMaceTest extends Module {
   private final NumberSetting targetRange = this.add(new NumberSetting("Ziel-Reichweite", 32.0, 8.0, 64.0, 1.0));
   private final NumberSetting climbHeight = this.add(new NumberSetting("Steig-Hoehe", 14.0, 6.0, 32.0, 0.5));
   private final NumberSetting diveDistance = this.add(new NumberSetting("Dive-Distanz", 10.0, 4.0, 20.0, 0.5));
   private final NumberSetting rocketInterval = this.add(new NumberSetting("Rocket-Intervall", 4.0, 1.0, 10.0, 1.0));
   private final NumberSetting chestSwapDistance = this.add(new NumberSetting("Chest-Swap-Distanz", 5.0, 2.0, 8.0, 0.5));
   private final NumberSetting aimSmoothness = this.add(new NumberSetting("Aim-Smoothness", 15.0, 0.0, 80.0, 1.0));
   private final NumberSetting resetDelay = this.add(new NumberSetting("Reset-Verzoegerung", 8.0, 0.0, 40.0, 1.0));
   private final BoolSetting autoRocket = this.add(new BoolSetting("Auto-Rockets", true));
   private final BoolSetting autoChestplate = this.add(new BoolSetting("Auto-Chestplate", true));
   private final BoolSetting autoMace = this.add(new BoolSetting("Auto-Mace", true));
   private final BoolSetting autoAttack = this.add(new BoolSetting("Auto-Angriff", true));
   private final BoolSetting playersOnly = this.add(new BoolSetting("Nur Spieler", true));

   private enum Phase {
      SEARCH,
      LAUNCH,
      CLIMB,
      DIVE,
      STRIKE,
      RESET
   }

   private Phase phase = Phase.SEARCH;
   private class_1657 target;
   private int rocketTimer;
   private int resetTimer;
   private boolean chestplateSwapped;
   private int previousSlot = -1;
   private int maceSlot = -1;

   public ElyMaceTest() {
      super("ElyMace Test", "Test-Bot fuer Elytra-Mace: Ziel anfliegen, hochboosten, Dive, Chestplate-Swap, Mace-Smash und erneuter Rocket-Aufstieg.", Category.COMBAT);
   }

   @Override
   protected void onDisable() {
      this.restoreState();
   }

   @Override
   public void onWorldLeave() {
      this.restoreState();
   }

   private void restoreState() {
      class_746 player = mc.field_1724;
      if (player != null && this.previousSlot >= 0 && this.maceSlot >= 0 && InvUtil.selected() == this.maceSlot) {
         InvUtil.select(this.previousSlot);
      }

      this.phase = Phase.SEARCH;
      this.target = null;
      this.rocketTimer = 0;
      this.resetTimer = 0;
      this.chestplateSwapped = false;
      this.previousSlot = -1;
      this.maceSlot = -1;
   }

   private class_1657 findTarget(class_746 player) {
      class_1657 nearest = CombatUtil.nearestPlayer(this.targetRange.get());
      if (nearest != null && nearest != player && nearest.method_5805() && !CombatUtil.isFriend(nearest)) {
         return nearest;
      }
      return null;
   }

   private String itemKey(class_1799 stack) {
      if (stack == null || stack.method_7960()) return "";
      return stack.method_7909().method_7876();
   }

   private int findHotbarByKey(String wanted) {
      class_746 player = mc.field_1724;
      if (player == null) return -1;

      for (int i = 0; i < 9; i++) {
         if (itemKey(player.method_31548().method_5438(i)).equals(wanted)) {
            return i;
         }
      }
      return -1;
   }

   private int ensureHotbarByKey(String wanted) {
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1761 == null || player.field_7512 != player.field_7498) return -1;

      int hotbar = this.findHotbarByKey(wanted);
      if (hotbar >= 0) return hotbar;

      int source = -1;
      for (int i = 9; i < 36; i++) {
         if (itemKey(player.method_31548().method_5438(i)).equals(wanted)) {
            source = i;
            break;
         }
      }
      if (source < 0) return -1;

      int destination = -1;
      for (int i = 0; i < 9; i++) {
         if (player.method_31548().method_5438(i).method_7960()) {
            destination = i;
            break;
         }
      }

      if (destination < 0) {
         int selected = player.method_31548().method_67532();
         destination = selected;
      }

      // Inventory 9-35 maps to PlayerScreenHandler slots 10-36.
      // SWAP puts the inventory rocket directly into the selected hotbar slot.
      mc.field_1761.method_2906(
         player.field_7498.field_7763,
         source + 1,
         destination,
         class_1713.field_7791,
         player
      );
      return destination;
   }

   private int findInventoryByKey(String wanted) {
      class_746 player = mc.field_1724;
      if (player == null) return -1;

      for (int i = 9; i < 36; i++) {
         if (itemKey(player.method_31548().method_5438(i)).equals(wanted)) {
            return i;
         }
      }

      return this.findHotbarByKey(wanted);
   }

   private int findChestplateHotbar() {
      class_746 player = mc.field_1724;
      if (player == null) return -1;

      for (int i = 0; i < 9; i++) {
         String key = itemKey(player.method_31548().method_5438(i));
         if (key.endsWith("_chestplate")) return i;
      }
      return -1;
   }

   private int findChestplateInventory() {
      class_746 player = mc.field_1724;
      if (player == null) return -1;

      for (int i = 9; i < 36; i++) {
         String key = itemKey(player.method_31548().method_5438(i));
         if (key.endsWith("_chestplate")) return i;
      }

      return this.findChestplateHotbar();
   }

   private boolean hasElytraEquipped(class_746 player) {
      return itemKey(player.method_31548().method_5438(38)).equals("item.minecraft.elytra");
   }

   private boolean hasChestplateEquipped(class_746 player) {
      return itemKey(player.method_31548().method_5438(38)).endsWith("_chestplate");
   }

   private void equipArmorFromInventory(int inventorySlot) {
      class_746 player = mc.field_1724;
      if (player == null || inventorySlot < 0 || inventorySlot >= 36 || player.field_7512 != player.field_7498) return;

      // PlayerScreenHandler chest armor slot is 6.
      // Hotbar slots can be exchanged directly with SWAP. Main-inventory
      // slots are moved with PICKUP so the actual Elytra/chestplate slot is
      // explicitly used instead of relying on the currently selected item.
      int screenSlot = inventorySlot < 9 ? 36 + inventorySlot : 9 + (inventorySlot - 9);

      if (inventorySlot < 9) {
         mc.field_1761.method_2906(
            player.field_7498.field_7763,
            6,
            inventorySlot,
            class_1713.field_7791,
            player
         );
      } else {
         mc.field_1761.method_2906(
            player.field_7498.field_7763,
            screenSlot,
            0,
            class_1713.field_7790,
            player
         );
         mc.field_1761.method_2906(
            player.field_7498.field_7763,
            6,
            0,
            class_1713.field_7790,
            player
         );
         mc.field_1761.method_2906(
            player.field_7498.field_7763,
            screenSlot,
            0,
            class_1713.field_7790,
            player
         );
      }
   }

   private void ensureElytraEquipped() {
      class_746 player = mc.field_1724;
      if (player == null || this.hasElytraEquipped(player)) return;

      int elytra = this.findInventoryByKey("item.minecraft.elytra");
      if (elytra >= 0) {
         this.equipArmorFromInventory(elytra);
      }
   }

   private void swapToChestplate() {
      class_746 player = mc.field_1724;
      if (player == null || !this.autoChestplate.get() || this.chestplateSwapped || !this.hasElytraEquipped(player)) return;

      int chest = this.findChestplateInventory();
      if (chest < 0) return;

      this.equipArmorFromInventory(chest);
      this.chestplateSwapped = this.hasChestplateEquipped(player);
   }

   private void swapBackToElytra() {
      class_746 player = mc.field_1724;
      if (player == null || !this.chestplateSwapped || !this.hasChestplateEquipped(player)) return;

      int elytra = this.findInventoryByKey("item.minecraft.elytra");
      if (elytra < 0) return;

      this.equipArmorFromInventory(elytra);
      this.chestplateSwapped = !this.hasElytraEquipped(player);
   }

   private void aimAt(class_1309 entity, class_746 player, double yOffset) {
      class_243 eye = player.method_33571();
      double dx = entity.method_23317() - eye.field_1352;
      double dz = entity.method_23321() - eye.field_1350;
      double dy = entity.method_23323(yOffset) - eye.field_1351;

      float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));

      double smooth = this.aimSmoothness.get();
      if (smooth <= 0.0) {
         player.method_36456(yaw);
         player.method_36457(class_3532.method_15363(pitch, -90.0F, 90.0F));
         return;
      }

      float factor = (float)Math.max(0.05, 1.0 - smooth / 100.0);
      float yawError = class_3532.method_15393(yaw - player.method_36454());
      float pitchError = pitch - player.method_36455();

      player.method_36456(player.method_36454() + yawError * factor);
      player.method_36457(class_3532.method_15363(player.method_36455() + pitchError * factor, -90.0F, 90.0F));
   }

   private double horizontalDistance(class_746 player, class_1309 entity) {
      double dx = entity.method_23317() - player.method_23317();
      double dz = entity.method_23321() - player.method_23321();
      return Math.hypot(dx, dz);
   }

   private void useRocket() {
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1761 == null) return;

      int rocket = this.findHotbarByKey("item.minecraft.firework_rocket");
      if (rocket < 0) return;

      int old = InvUtil.selected();
      InvUtil.select(rocket);
      mc.field_1761.method_2919(player, class_1268.field_5808);
      player.method_6104(class_1268.field_5808);
      if (old != rocket && this.phase != Phase.CLIMB) {
         InvUtil.select(old);
      }
   }

   private void startFlight(class_746 player) {
      if (player.method_24828()) {
         player.method_6043();
         return;
      }

      if (!player.method_6128() && hasElytraEquipped(player)) {
         player.method_23668();
      }
   }

   private void selectMace() {
      class_746 player = mc.field_1724;
      if (player == null || !this.autoMace.get()) return;

      int slot = InvUtil.findHotbar(class_1802.field_49814);
      if (slot < 0) return;

      if (this.previousSlot < 0) {
         this.previousSlot = InvUtil.selected();
         this.maceSlot = slot;
      }

      InvUtil.select(slot);
   }

   private void attackTarget(class_1657 player) {
      if (!this.autoAttack.get() || this.target == null || mc.field_1761 == null) return;
      if (!this.target.method_5805()) return;
      if (player.method_5739(this.target) > 5.0) return;

      mc.field_1761.method_2918(player, this.target);
      player.method_6104(class_1268.field_5808);
   }

   private void enterReset() {
      this.swapBackToElytra();
      this.resetTimer = this.resetDelay.getInt();
      this.phase = Phase.RESET;
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1687 == null || mc.field_1761 == null || mc.field_1755 != null) {
         return;
      }

      if (this.phase == Phase.SEARCH) {
         this.target = this.findTarget(player);
         if (this.target != null) {
            this.rocketTimer = 0;
            this.phase = Phase.LAUNCH;
         }
         return;
      }

      if (this.target == null || !this.target.method_5805()) {
         this.phase = Phase.SEARCH;
         return;
      }

      switch (this.phase) {
         case LAUNCH -> {
            // At the start of the module, explicitly move the Elytra into
            // the chest armor slot. Do not assume it is already equipped.
            if (!this.hasElytraEquipped(player)) {
               this.ensureElytraEquipped();
               if (!this.hasElytraEquipped(player)) {
                  return;
               }
            }

            double height = player.method_23318() - this.target.method_23318();

            // Do not run aim assist during launch/climb. The bot only starts
            // steering toward the target once the actual dive begins.
            if (height < this.climbHeight.get()) {
               player.method_36457(-35.0F);
               this.startFlight(player);

               // Once Elytra flight is actually active, use a real firework
               // interaction to boost upward. This starts automatically as
               // soon as the module is enabled.
               if (player.method_6128() && this.autoRocket.get()) {
                  if (this.rocketTimer-- <= 0) {
                     this.useRocket();
                     this.rocketTimer = this.rocketInterval.getInt();
                  }
               }
            } else {
               this.phase = Phase.DIVE;
            }
         }

         case CLIMB -> {
            this.phase = Phase.DIVE;
         }

         case DIVE -> {
            double height = player.method_23318() - this.target.method_23318();
            double horizontal = this.horizontalDistance(player, this.target);

            this.aimAt(this.target, player, 0.55);

            if (height > this.chestSwapDistance.get() || horizontal > this.diveDistance.get()) {
               if (player.method_6128() && this.autoRocket.get() && this.rocketTimer-- <= 0 && height > 2.0) {
                  this.useRocket();
                  this.rocketTimer = this.rocketInterval.getInt();
               }
               return;
            }

            if (this.autoChestplate.get()) {
               this.swapToChestplate();
            }

            if (this.hasChestplateEquipped(player)) {
               this.selectMace();
               this.phase = Phase.STRIKE;
            }
         }

         case STRIKE -> {
            double fall = player.field_6017;
            this.aimAt(this.target, player, 0.55);

            if (!this.hasChestplateEquipped(player)) {
               this.phase = Phase.DIVE;
               return;
            }

            if (fall >= 1.5 && !player.method_24828() && player.method_18798().field_1351 < -0.01) {
               this.selectMace();
               this.attackTarget(player);
               this.enterReset();
               return;
            }

            if (player.method_24828()) {
               this.enterReset();
            }
         }

         case RESET -> {
            if (this.resetTimer > 0) {
               this.resetTimer--;
               return;
            }

            this.target = this.findTarget(player);
            if (this.target != null) {
               this.rocketTimer = 0;
               this.phase = Phase.LAUNCH;
            } else {
               this.phase = Phase.SEARCH;
            }
         }

         default -> this.phase = Phase.SEARCH;
      }
   }
}
