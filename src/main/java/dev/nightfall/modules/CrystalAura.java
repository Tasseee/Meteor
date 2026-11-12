package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
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
import net.minecraft.class_2680;
import net.minecraft.class_3965;
import net.minecraft.class_746;

public class CrystalAura extends Module {
   private final NumberSetting targetRange = this.add(new NumberSetting("Ziel-Reichweite", 10.0, 4.0, 16.0, 1.0));
   private final NumberSetting placeRange = this.add(new NumberSetting("Platzier-Reichweite", 4.5, 2.0, 6.0, 0.5));
   private final NumberSetting breakRange = this.add(new NumberSetting("Zerstoer-Reichweite", 4.5, 2.0, 6.0, 0.5));
   private final NumberSetting placeDelay = this.add(new NumberSetting("Platzier-Delay (Ticks)", 0.0, 0.0, 20.0, 1.0));
   private final NumberSetting breakDelay = this.add(new NumberSetting("Zerstoer-Delay (Ticks)", 0.0, 0.0, 20.0, 1.0));
   private final BoolSetting place = this.add(new BoolSetting("Platzieren", true));
   private final BoolSetting breakC = this.add(new BoolSetting("Zerstoeren", true));
   private final BoolSetting autoSwitch = this.add(new BoolSetting("Auto-Switch", true));
   private final BoolSetting placeObsidianOnHit = this.add(new BoolSetting("Obsidian bei Treffer", true));
   private int trackedTargetId = -1;
   private float lastTargetHealth = Float.NaN;
   private int placeT;
   private int breakT;

   @Override
   public void onWorldLeave() {
      this.placeT = 0;
      this.breakT = 0;
      this.trackedTargetId = -1;
      this.lastTargetHealth = Float.NaN;
   }

   @Override
   protected void onDisable() {
      this.placeT = 0;
      this.breakT = 0;
      this.trackedTargetId = -1;
      this.lastTargetHealth = Float.NaN;
   }

   public CrystalAura() {
      super("CrystalAura", "Platziert und zerstoert End Crystals.", Category.COMBAT);
   }


   private class_2338 findHitObsidianBase(class_746 player, class_1657 target) {
      double dx = target.method_23317() - player.method_23317();
      double dz = target.method_23321() - player.method_23321();
      int forwardX = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? 1 : -1) : 0;
      int forwardZ = forwardX == 0 ? (dz >= 0 ? 1 : -1) : 0;
      int sideX = -forwardZ;
      int sideZ = forwardX;
      class_2338 feet = target.method_24515();
      class_2338[] candidates = new class_2338[] {
         feet.method_10069(sideX * 2, -1, sideZ * 2),
         feet.method_10069(-sideX * 2, -1, -sideZ * 2),
         feet.method_10069(forwardX * 2, -1, forwardZ * 2),
         feet.method_10069(-forwardX * 2, -1, -forwardZ * 2)
      };
      for (class_2338 base : candidates) {
         class_2680 state = mc.field_1687.method_8320(base);
         class_2338 support = base.method_10074();
         if (!state.method_45474()) continue;
         if (mc.field_1687.method_8320(support).method_26220(mc.field_1687, support).method_1110()) continue;
         if (!mc.field_1687.method_8320(base.method_10084()).method_26215()
            || !mc.field_1687.method_8320(base.method_10084().method_10084()).method_26215()) continue;
         class_238 space = new class_238(base.method_10263(), base.method_10264(),
            base.method_10260(), base.method_10263() + 1.0, base.method_10264() + 3.0,
            base.method_10260() + 1.0);
         if (!mc.field_1687.method_8335(null, space).isEmpty()) continue;
         return base;
      }
      return null;
   }

   private boolean placeObsidianAfterHit(class_746 player, class_1657 target) {
      if (!this.placeObsidianOnHit.get()) return false;
      class_2338 base = findHitObsidianBase(player, target);
      if (base == null) return false;
      if (mc.field_1687.method_8320(base).method_27852(class_2246.field_9987)) return true;
      int slot = InvUtil.ensureHotbar(class_1802.field_8281);
      if (slot < 0) return false;
      InvUtil.select(slot);
      // Place against any valid neighboring solid face. This is more reliable than
      // assuming the block below is the only usable support/face.
      for (class_2350 face : class_2350.values()) {
         class_2338 neighbor = base.method_10093(face);
         if (!mc.field_1687.method_8320(neighbor).method_26215()) {
            class_2350 clickedSide = face.method_10153();
            class_243 hit = class_243.method_24953(neighbor)
               .method_1031(clickedSide.method_10148() * 0.5, clickedSide.method_10164() * 0.5, clickedSide.method_10165() * 0.5);
            class_3965 result = new class_3965(hit, clickedSide, neighbor, false);
            if (mc.field_1761.method_2896(player, class_1268.field_5808, result).method_23665()) {
               player.method_6104(class_1268.field_5808);
               return true;
            }
         }
      }
      return false;
   }

   @Override
   public void onTick() {
      class_746 p = mc.field_1724;
      if (p != null && mc.field_1687 != null && mc.field_1761 != null && mc.field_1755 == null) {
         if (this.placeT > 0) {
            this.placeT--;
         }

         if (this.breakT > 0) {
            this.breakT--;
         }

         if (this.breakC.get() && this.breakT == 0) {
            class_1511 best = null;
            double bestDistance = Double.MAX_VALUE;

            for (class_1297 e : mc.field_1687.method_18112()) {
               if (e instanceof class_1511 c && c.method_5805() && !(p.method_5739(c) > this.breakRange.get())) {
                  double distance = c.method_5858(p);
                  if (distance < bestDistance) {
                     bestDistance = distance;
                     best = c;
                  }
               }
            }

            if (best != null) {
               mc.field_1761.method_2918(p, best);
               p.method_6104(class_1268.field_5808);
               this.breakT = this.breakDelay.getInt();
               return;
            }
         }

         if (this.place.get() && this.placeT <= 0) {
            class_1657 target = CombatUtil.nearestPlayer(this.targetRange.get());
            if (target == null) {
               this.trackedTargetId = -1;
               this.lastTargetHealth = Float.NaN;
            } else {
               int currentTargetId = target.method_5628();
               float currentHealth = target.method_6032();
               if (currentTargetId != this.trackedTargetId) {
                  this.trackedTargetId = currentTargetId;
                  this.lastTargetHealth = currentHealth;
               } else {
                  boolean targetWasHit = !Float.isNaN(this.lastTargetHealth)
                     && currentHealth < this.lastTargetHealth - 0.01F;
                  this.lastTargetHealth = currentHealth;
                  if (targetWasHit) {
                     // On every confirmed hit, place obsidian first. Crystal placement continues on the following tick.
                     if (this.placeObsidianAfterHit(p, target)) return;
                  }
               }
            }
            if (target != null && !target.method_24828()) {
               class_1268 hand = p.method_6079().method_31574(class_1802.field_8301)
                  ? class_1268.field_5810
                  : (p.method_6047().method_31574(class_1802.field_8301) ? class_1268.field_5808 : null);
               if (hand == null) {
                  if (!this.autoSwitch.get()) {
                     return;
                  }

                  int slot = InvUtil.ensureHotbar(class_1802.field_8301);
                  if (slot < 0) {
                     return;
                  }

                  InvUtil.select(slot);
                  hand = class_1268.field_5808;
               }

               class_2338 origin = p.method_24515();
               int r = (int)Math.ceil(this.placeRange.get());
               class_2338 bestPos = null;
               double bestDistance = Double.MAX_VALUE;

               for (int x = -r; x <= r; x++) {
                  for (int y = -r; y <= r; y++) {
                     for (int z = -r; z <= r; z++) {
                        class_2338 bp = origin.method_10069(x, y, z);
                        if (!(p.method_33571().method_1022(class_243.method_24953(bp)) > this.placeRange.get() + 1.0)) {
                           class_2680 state = mc.field_1687.method_8320(bp);
                           if (state.method_27852(class_2246.field_10540) || state.method_27852(class_2246.field_9987)) {
                              class_2338 up = bp.method_10084();
                              if (mc.field_1687.method_8320(up).method_26215() && mc.field_1687.method_8320(up.method_10084()).method_26215()) {
                                 class_238 crystalSpace = new class_238(
                                    bp.method_10263(),
                                    bp.method_10264() + 1,
                                    bp.method_10260(),
                                    bp.method_10263() + 1,
                                    bp.method_10264() + 3,
                                    bp.method_10260() + 1
                                 );
                                 if (mc.field_1687.method_8335(null, crystalSpace).isEmpty()) {
                                    class_243 crystalPos = new class_243(bp.method_10263() + 0.5, bp.method_10264() + 1.0, bp.method_10260() + 0.5);
                                    double targetDistance = target.method_5707(crystalPos);
                                    if (!(targetDistance > 144.0) && targetDistance < bestDistance) {
                                       bestDistance = targetDistance;
                                       bestPos = bp.method_10062();
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }

               if (bestPos != null) {
                  class_243 hit = new class_243(bestPos.method_10263() + 0.5, bestPos.method_10264() + 1.0, bestPos.method_10260() + 0.5);
                  class_3965 hitResult = new class_3965(hit, class_2350.field_11036, bestPos, false);
                  mc.field_1761.method_2896(p, hand, hitResult);
                  p.method_6104(hand);
                  this.placeT = this.placeDelay.getInt();
               }
            }
         }
      }
   }
}
