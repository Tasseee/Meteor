package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.util.InvUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1268;
import net.minecraft.class_1269;
import net.minecraft.class_1657;
import net.minecraft.class_1792;
import net.minecraft.class_1802;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_3966;
import net.minecraft.class_746;

public final class ObiTrapBot extends Module {
   private class_1657 target;
   private class_2338 anchor;
   private String phase = "obsidian";
   private int idleTicks;
   private int previousSlot = -1;

   public ObiTrapBot() {
      super("ObiTrapBot", "Baut eine Obsidian-Box um den anvisierten Spieler und platziert Cobwebs.", Category.COMBAT);
   }

   @Override
   protected void onEnable() {
      this.start();
   }

   @Override
   protected void onDisable() {
      this.resetState();
   }

   @Override
   public void onWorldLeave() {
      this.resetState();
   }

   private void resetState() {
      this.restoreSlot();
      this.target = null;
      this.anchor = null;
      this.idleTicks = 0;
      this.phase = "obsidian";
   }

   private void start() {
      if (mc.field_1724 != null && mc.field_1687 != null) {
         if (mc.field_1765 instanceof class_3966 hit && hit.method_17782() instanceof class_1657 player && player != mc.field_1724) {
            this.target = player;
            this.anchor = this.target.method_24515();
            this.phase = "obsidian";
            this.idleTicks = 0;
            mc.field_1724.method_7353(class_2561.method_43470("[Obitrap] Baue die Box um " + this.target.method_5477().getString() + "."), false);
         } else {
            mc.field_1724.method_7353(class_2561.method_43470("[Obitrap] Ziele mit deinem Fadenkreuz auf einen anderen Spieler und aktiviere den Bot."), false);
            this.setEnabled(false);
         }
      }
   }

   @Override
   public void onTick() {
      class_746 player = mc.field_1724;
      if (player == null || mc.field_1687 == null || mc.field_1761 == null || this.anchor == null || this.target == null) {
         this.stop("Abgebrochen: Kein gültiges Ziel oder keine Welt.");
         this.setEnabled(false);
      } else if (this.target.method_5805() && !this.target.method_31481()) {
         List<class_2338> positions = this.phase.equals("obsidian") ? this.getObsidianPositions() : this.getCobwebPositions();
         class_1792 requiredItem = this.phase.equals("obsidian") ? class_1802.field_8281 : class_1802.field_8786;
         class_2338 next = null;

         for (class_2338 pos : positions) {
            class_2680 state = mc.field_1687.method_8320(pos);
            if (state.method_26215() || state.method_45474()) {
               next = pos;
               break;
            }
         }

         if (next == null) {
            if (this.phase.equals("obsidian")) {
               this.phase = "cobweb";
               this.idleTicks = 0;
               player.method_7353(class_2561.method_43470("[Obitrap] Box fertig. Platziere Cobwebs."), false);
            } else {
               this.stop("Fertig! Box und Cobwebs wurden platziert.");
               this.setEnabled(false);
            }
         } else {
            int slot = InvUtil.ensureHotbar(requiredItem);
            if (slot < 0) {
               this.stop("Abgebrochen: " + (this.phase.equals("obsidian") ? "Obsidian" : "Cobwebs") + " fehlen im Inventar.");
               this.setEnabled(false);
            } else {
               if (this.previousSlot < 0) {
                  this.previousSlot = InvUtil.selected();
               }

               InvUtil.select(slot);
               if (this.tryPlace(next)) {
                  this.idleTicks = 0;
               } else if (++this.idleTicks > 100) {
                  this.stop("Abgebrochen: Die nächsten Blöcke sind nicht erreichbar oder können nicht platziert werden.");
                  this.setEnabled(false);
               }
            }
         }
      } else {
         this.stop("Ziel nicht mehr verfügbar.");
         this.setEnabled(false);
      }
   }

   private List<class_2338> getObsidianPositions() {
      List<class_2338> result = new ArrayList<>();

      for (int x = -1; x <= 1; x++) {
         for (int z = -1; z <= 1; z++) {
            result.add(this.anchor.method_10069(x, -1, z));
            result.add(this.anchor.method_10069(x, 2, z));
         }
      }

      for (int y = 0; y <= 1; y++) {
         for (int x = -1; x <= 1; x++) {
            result.add(this.anchor.method_10069(x, y, -1));
            result.add(this.anchor.method_10069(x, y, 1));
         }

         result.add(this.anchor.method_10069(-1, y, 0));
         result.add(this.anchor.method_10069(1, y, 0));
      }

      return result;
   }

   private List<class_2338> getCobwebPositions() {
      return List.of(this.anchor, this.anchor.method_10084());
   }

   private boolean tryPlace(class_2338 placePos) {
      for (class_2350 face : class_2350.values()) {
         class_2338 neighbor = placePos.method_10093(face);
         if (!mc.field_1687.method_8320(neighbor).method_26215()) {
            class_2350 clickedSide = face.method_10153();
            class_243 hitPosition = class_243.method_24953(neighbor)
               .method_1031(clickedSide.method_10148() * 0.5, clickedSide.method_10164() * 0.5, clickedSide.method_10165() * 0.5);
            class_3965 hit = new class_3965(hitPosition, clickedSide, neighbor, false);
            class_1269 result = mc.field_1761.method_2896(mc.field_1724, class_1268.field_5808, hit);
            if (result.method_23665()) {
               mc.field_1724.method_6104(class_1268.field_5808);
               return true;
            }
         }
      }

      return false;
   }

   private void restoreSlot() {
      if (this.previousSlot >= 0 && mc.field_1724 != null) {
         InvUtil.select(this.previousSlot);
      }

      this.previousSlot = -1;
   }

   private void stop(String message) {
      this.restoreSlot();
      if (mc.field_1724 != null) {
         mc.field_1724.method_7353(class_2561.method_43470("[Obitrap] " + message), false);
      }

      this.target = null;
      this.anchor = null;
      this.idleTicks = 0;
      this.phase = "obsidian";
   }
}
