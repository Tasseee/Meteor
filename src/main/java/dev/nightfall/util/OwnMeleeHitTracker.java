package dev.nightfall.util;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.CrystalTest;
import dev.nightfall.modules.DTap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_310;

public final class OwnMeleeHitTracker {
   private static final List<PendingHit> PENDING = new ArrayList<>();

   private OwnMeleeHitTracker() {
   }

   public static void recordAttack(class_1657 player, class_1297 target) {
      class_310 mc = class_310.method_1551();
      if (player == null || player != mc.field_1724 || mc.field_1687 == null
         || !(target instanceof class_1657 victim)) return;

      try {
         if (!ModuleManager.get(DTap.class).isEnabled()
            && !ModuleManager.get(CrystalTest.class).isEnabled()) return;
      } catch (RuntimeException ignored) {
         return;
      }

      class_1799 held = player.method_6047();
      if (!held.method_7960() && !isSword(held)) return;
      if (PENDING.size() >= 64) PENDING.remove(0);
      PENDING.add(new PendingHit(victim.method_5628(), totalHealth(victim)));
   }

   private static boolean isSword(class_1799 stack) {
      return stack.method_31574(class_1802.field_22022)
         || stack.method_31574(class_1802.field_8802)
         || stack.method_31574(class_1802.field_8371)
         || stack.method_31574(class_1802.field_8528)
         || stack.method_31574(class_1802.field_8845)
         || stack.method_31574(class_1802.field_8091);
   }

   private static float totalHealth(class_1657 player) {
      return player.method_6032() + player.method_6067();
   }

   public static void tick() {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 == null || mc.field_1687 == null) {
         clear();
         return;
      }

      boolean dtapEnabled;
      boolean crystalTestEnabled;
      try {
         dtapEnabled = ModuleManager.get(DTap.class).isEnabled();
         crystalTestEnabled = ModuleManager.get(CrystalTest.class).isEnabled();
      } catch (RuntimeException ignored) {
         clear();
         return;
      }
      if (!dtapEnabled && !crystalTestEnabled) {
         clear();
         return;
      }

      Iterator<PendingHit> iterator = PENDING.iterator();
      while (iterator.hasNext()) {
         PendingHit hit = iterator.next();
         class_1297 entity = mc.field_1687.method_8469(hit.targetId);
         if (!(entity instanceof class_1657 target) || !target.method_5805()) {
            iterator.remove();
            continue;
         }

         float currentHealth = totalHealth(target);
         if (currentHealth < hit.healthBefore - 0.01F) {
            iterator.remove();
            if (dtapEnabled) ModuleManager.get(DTap.class).onConfirmedMeleeHit(target);
            if (crystalTestEnabled) ModuleManager.get(CrystalTest.class).onConfirmedMeleeHit(target);
            // Rebase queued attacks so the same health update cannot count repeatedly.
            for (PendingHit later : PENDING) {
               if (later.targetId == hit.targetId) {
                  later.healthBefore = currentHealth;
                  later.age = 0;
               }
            }
         } else if (++hit.age > 8) {
            iterator.remove();
         }
      }
   }

   public static void clear() {
      PENDING.clear();
   }

   private static final class PendingHit {
      private final int targetId;
      private float healthBefore;
      private int age;

      private PendingHit(int targetId, float healthBefore) {
         this.targetId = targetId;
         this.healthBefore = healthBefore;
      }
   }
}
