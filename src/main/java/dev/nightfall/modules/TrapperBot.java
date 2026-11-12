package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import net.minecraft.class_1268;
import net.minecraft.class_1294;
import net.minecraft.class_1657;
import net.minecraft.class_1747;
import net.minecraft.class_1799;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_304;
import net.minecraft.class_3965;
import net.minecraft.class_640;
import net.minecraft.class_746;

public final class TrapperBot extends Module {
   private final NumberSetting inviteDelay = this.add(new NumberSetting("Warn-Vorlauf (Ticks)", 3.0, 1.0, 100.0, 1.0));
   private final NumberSetting arrivalTimeout = this.add(new NumberSetting("Wartezeit (Sek.)", 60.0, 10.0, 180.0, 5.0));
   private final NumberSetting cooldownSeconds = this.add(new NumberSetting("Spieler-Cooldown (Sek.)", 6.0, 1.0, 300.0, 1.0));
   private final NumberSetting buildInterval = this.add(new NumberSetting("Bau-Intervall (Ticks)", 1.0, 1.0, 8.0, 1.0));
   private final NumberSetting fightRange = this.add(new NumberSetting("Kampfreichweite", 3.1, 2.5, 4.5, 0.1));
   private final BoolSetting sendWarning = this.add(new BoolSetting("Warnung vor TPAHere", true));
   private final BoolSetting buildTrap = this.add(new BoolSetting("1x2-Falle bauen", true));
   private final BoolSetting requireTrap = this.add(new BoolSetting("Nur in Falle angreifen", true));
   private final BoolSetting criticalOnly = this.add(new BoolSetting("Nur Crits", true));
   public final ListSetting blacklist = this.add(new ListSetting("Blacklist"));
   private final Random random = new Random();
   private final Set<String> attemptedThisSession = new HashSet<>();
   private TrapperBot.Phase phase = TrapperBot.Phase.IDLE;
   private String targetName = "";
   private int timer;
   private int buildIndex;
   private int buildDelay;
   private int buildPasses;
   private int previousSlot = -1;
   private int blockSlot = -1;
   private boolean controlsCaptured;
   private boolean previousJumpPressed;
   private boolean previousSprintPressed;
   private class_1657 target;
   private List<class_2338> buildOrder = List.of();

   public TrapperBot() {
      super("TrapperBot", "Warnt Spieler, sendet /tpahere und kaempft nach Annahme mit Crits.", Category.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.phase = TrapperBot.Phase.IDLE;
      this.timer = 0;
      this.targetName = "";
      this.attemptedThisSession.clear();
      this.buildPasses = 0;
   }

   @Override
   protected void onDisable() {
      this.releaseControls();
      this.restoreSlot();
      this.resetTarget();
      this.phase = TrapperBot.Phase.IDLE;
   }

   @Override
   public void onWorldLeave() {
      this.resetSession();
   }

   public void resetSession() {
      this.releaseControls();
      this.restoreSlot();
      this.resetTarget();
      this.attemptedThisSession.clear();
      this.buildPasses = 0;
      this.phase = TrapperBot.Phase.IDLE;
   }

   public String status() {
      return this.phase.name() + (this.targetName.isEmpty() ? "" : " | " + this.targetName);
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player != null && mc.field_1687 != null && mc.method_1562() != null && mc.field_1761 != null && mc.field_1755 == null && player.method_5805()) {
         switch (this.phase) {
            case IDLE:
               this.chooseAndWarn();
               break;
            case WARNING:
               this.sendRequest();
               break;
            case WAITING_FOR_ARRIVAL:
               this.waitForArrival();
               break;
            case BUILDING:
               this.buildOneBlock();
               break;
            case FIGHTING:
               this.fightTarget();
               break;
            case COOLDOWN:
               this.releaseControls();
               if (--this.timer <= 0) {
                  this.phase = TrapperBot.Phase.IDLE;
               }
         }
      } else {
         this.releaseControls();
      }
   }

   private void chooseAndWarn() {
      List<String> candidates = new ArrayList<>();

      for (class_640 entry : mc.method_1562().method_2880()) {
         String name = entry.method_2966().name();
         if (name != null
            && !name.isBlank()
            && name.matches("[A-Za-z0-9_]{3,16}")
            && !name.equalsIgnoreCase(mc.field_1724.method_5477().getString())
            && !this.blacklist.has(name)
            && !this.attemptedThisSession.contains(name.toLowerCase(Locale.ROOT))
            && !this.isFriendName(name)) {
            candidates.add(name);
         }
      }

      if (candidates.isEmpty()) {
         this.timer = 40;
         this.phase = TrapperBot.Phase.COOLDOWN;
      } else {
         Collections.shuffle(candidates, this.random);
         this.targetName = candidates.get(0);
         this.attemptedThisSession.add(this.targetName.toLowerCase(Locale.ROOT));
         if (this.sendWarning.get()) {
            mc.method_1562()
               .method_45730(
                  "msg " + this.targetName + " Achtung: PvP-/Trapper-Event. Du koenntest in eine Falle geraten. Nur annehmen, wenn du kaempfen moechtest."
               );
         }

         this.timer = this.sendWarning.get() ? this.inviteDelay.getInt() : 0;
         this.phase = TrapperBot.Phase.WARNING;
      }
   }

   private void sendRequest() {
      if (this.timer-- <= 0) {
         mc.method_1562().method_45730("tpahere " + this.targetName);
         this.timer = this.arrivalTimeout.getInt() * 20;
         this.phase = TrapperBot.Phase.WAITING_FOR_ARRIVAL;
      }
   }

   private void waitForArrival() {
      this.target = this.findWorldPlayer(this.targetName);
      if (this.target != null && this.target.method_5805() && this.target.method_5858(mc.field_1724) <= 25.0) {
         this.restoreSlot();
         this.timer = 100;
         this.buildOrder = this.createTrapOrder(this.target.method_24515());
         this.buildIndex = 0;
         this.buildDelay = 0;
         this.buildPasses = 0;
         if (this.buildTrap.get() && (this.blockSlot = this.findBuildBlock()) >= 0) {
            this.previousSlot = mc.field_1724.method_31548().method_67532();
            mc.field_1724.method_31548().method_61496(this.blockSlot);
            this.phase = TrapperBot.Phase.BUILDING;
         } else {
            if (this.buildTrap.get() && this.requireTrap.get()) {
               mc.field_1724
                  .method_7353(
                     class_2561.method_43470("[Nightfall] TrapperBot: Keine geeigneten vollen Bloecke in der Hotbar; Crit-Angriff wartet auf eine Falle."),
                     false
                  );
            }

            this.phase = TrapperBot.Phase.FIGHTING;
         }
      } else {
         if (--this.timer <= 0) {
            this.startCooldown(400);
         }
      }
   }

   private void buildOneBlock() {
      if (this.target != null && this.target.method_5805() && this.target.method_73183() == mc.field_1687) {
         if (this.buildDelay-- <= 0) {
            if (this.buildIndex < this.buildOrder.size()) {
               class_2338 place = this.buildOrder.get(this.buildIndex++);
               if (mc.field_1687.method_8320(place).method_45474()) {
                  this.placeAgainstNeighbour(place);
               }

               this.buildDelay = this.buildInterval.getInt();
            } else {
               if (this.isEnclosed(this.target)) {
                  this.restoreSlot();
                  this.phase = TrapperBot.Phase.FIGHTING;
               } else if (++this.buildPasses < 3 && this.findBuildBlock() >= 0) {
                  this.buildOrder = this.createTrapOrder(this.target.method_24515());
                  this.buildIndex = 0;
                  this.buildDelay = Math.max(2, this.buildInterval.getInt() * 2);
                  this.blockSlot = this.findBuildBlock();
                  mc.field_1724.method_31548().method_61496(this.blockSlot);
               } else {
                  this.restoreSlot();
                  mc.field_1724
                     .method_7353(
                        class_2561.method_43470("[Nightfall] TrapperBot: Falle konnte nicht vollstaendig gebaut werden; Ziel wird nicht angegriffen."), false
                     );
                  this.startCooldown(400);
               }
            }
         }
      } else {
         this.startCooldown(400);
      }
   }

   private boolean placeAgainstNeighbour(class_2338 place) {
      if (this.blockSlot >= 0 && this.blockSlot <= 8) {
         class_746 player = mc.field_1724;

         for (class_2350 face : class_2350.values()) {
            class_2338 support = place.method_10093(face.method_10153());
            if (!mc.field_1687.method_8320(support).method_26220(mc.field_1687, support).method_1110()) {
               class_243 hit = support.method_46558().method_1031(face.method_10148() * 0.5, face.method_10164() * 0.5, face.method_10165() * 0.5);
               class_3965 result = new class_3965(hit, face, support, false);
               mc.field_1761.method_2896(player, class_1268.field_5808, result);
               player.method_6104(class_1268.field_5808);
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private void fightTarget() {
      if (this.target == null || !this.target.method_5805() || this.target.method_73183() != mc.field_1687) {
         this.target = this.findWorldPlayer(this.targetName);
      }

      if (this.target != null && this.target.method_5805()) {
         class_746 player = mc.field_1724;
         if (player.method_5739(this.target) > this.fightRange.get() + 1.0) {
            this.releaseControls();
            if (--this.timer <= 0) {
               this.startCooldown(400);
            }
         } else {
            this.timer = 100;
            class_243 eye = player.method_33571();
            double dx = this.target.method_23317() - eye.field_1352;
            double dy = this.target.method_23323(0.5) - eye.field_1351;
            double dz = this.target.method_23321() - eye.field_1350;
            player.method_36456((float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
            player.method_36457((float)(-Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))));
            boolean trapped = this.isEnclosed(this.target);
            if (this.requireTrap.get() && !trapped) {
               this.buildOrder = this.createTrapOrder(this.target.method_24515());
               this.buildIndex = 0;
               this.buildDelay = 0;
               this.buildPasses = 0;
               if (this.buildTrap.get() && this.findBuildBlock() >= 0) {
                  this.blockSlot = this.findBuildBlock();
                  if (this.previousSlot < 0) {
                     this.previousSlot = player.method_31548().method_67532();
                  }

                  player.method_31548().method_61496(this.blockSlot);
                  this.phase = TrapperBot.Phase.BUILDING;
               }
            } else {
               this.captureControls();
               mc.field_1690.field_1867.method_23481(false);
               player.method_5728(false);
               if (player.method_24828()) {
                  mc.field_1690.field_1903.method_23481(true);
               } else {
                  mc.field_1690.field_1903.method_23481(false);
               }

               boolean critReady = !player.method_24828()
                  && player.field_6017 > 0.0
                  && !player.method_6101()
                  && !player.method_5799()
                  && !player.method_5765()
                  && !player.method_5624()
                  && !player.method_6059(class_1294.field_5919);
               if (player.method_5739(this.target) <= this.fightRange.get()
                  && player.method_6057(this.target)
                  && player.method_7261(0.5F) >= 0.95F
                  && (!this.criticalOnly.get() || critReady)) {
                  mc.field_1761.method_2918(player, this.target);
                  player.method_6104(class_1268.field_5808);
               }
            }
         }
      } else {
         this.restoreSlot();
         this.startCooldown(this.cooldownSeconds.getInt() * 20);
      }
   }

   private List<class_2338> createTrapOrder(class_2338 feet) {
      List<class_2338> positions = new ArrayList<>(9);
      class_2350[] sides = new class_2350[]{class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034};

      for (class_2350 side : sides) {
         positions.add(feet.method_10093(side));
      }

      for (class_2350 side : sides) {
         positions.add(feet.method_10084().method_10093(side));
      }

      positions.add(feet.method_10086(2));
      return positions;
   }

   private boolean isEnclosed(class_1657 entity) {
      class_2338 feet = entity.method_24515();

      for (class_2350 side : new class_2350[]{class_2350.field_11043, class_2350.field_11035, class_2350.field_11039, class_2350.field_11034}) {
         if (mc.field_1687.method_8320(feet.method_10093(side)).method_26220(mc.field_1687, feet.method_10093(side)).method_1110()) {
            return false;
         }

         class_2338 headSide = feet.method_10084().method_10093(side);
         if (mc.field_1687.method_8320(headSide).method_26220(mc.field_1687, headSide).method_1110()) {
            return false;
         }
      }

      class_2338 roof = feet.method_10086(2);
      return !mc.field_1687.method_8320(roof).method_26220(mc.field_1687, roof).method_1110();
   }

   private int findBuildBlock() {
      for (int slot = 0; slot < 9; slot++) {
         class_1799 stack = mc.field_1724.method_31548().method_5438(slot);
         if (stack.method_7909() instanceof class_1747 blockItem) {
            class_2680 state = blockItem.method_7711().method_9564();
            if (!state.method_26220(mc.field_1687, mc.field_1724.method_24515()).method_1110()
               && state.method_26234(mc.field_1687, mc.field_1724.method_24515())) {
               return slot;
            }
         }
      }

      return -1;
   }

   private class_1657 findWorldPlayer(String name) {
      if (name != null && !name.isEmpty() && mc.field_1687 != null) {
         for (class_1657 p : mc.field_1687.method_18456()) {
            if (p != mc.field_1724 && p.method_5477().getString().equalsIgnoreCase(name)) {
               return p;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   private boolean isFriendName(String name) {
      return ModuleManager.get(KillAura.class).friends.has(name);
   }

   private void startCooldown(int ticks) {
      this.releaseControls();
      this.restoreSlot();
      this.timer = Math.max(1, ticks);
      this.phase = TrapperBot.Phase.COOLDOWN;
   }

   private void resetTarget() {
      this.target = null;
      this.targetName = "";
      this.buildOrder = List.of();
      this.buildIndex = 0;
      this.buildPasses = 0;
      this.blockSlot = -1;
      this.previousSlot = -1;
      this.timer = 0;
   }

   private void restoreSlot() {
      if (this.previousSlot >= 0 && mc.field_1724 != null) {
         mc.field_1724.method_31548().method_61496(this.previousSlot);
      }

      this.previousSlot = -1;
      this.blockSlot = -1;
   }

   private void captureControls() {
      if (!this.controlsCaptured && mc.field_1690 != null) {
         this.previousJumpPressed = mc.field_1690.field_1903.method_1434();
         this.previousSprintPressed = mc.field_1690.field_1867.method_1434();
         this.controlsCaptured = true;
      }
   }

   private void releaseControls() {
      if (mc.field_1690 != null && this.controlsCaptured) {
         mc.field_1690.field_1903.method_23481(this.previousJumpPressed);
         mc.field_1690.field_1867.method_23481(this.previousSprintPressed);
         class_304.method_1424();
         this.controlsCaptured = false;
      }
   }

   private static enum Phase {
      IDLE,
      WARNING,
      WAITING_FOR_ARRIVAL,
      BUILDING,
      FIGHTING,
      COOLDOWN;
   }
}
