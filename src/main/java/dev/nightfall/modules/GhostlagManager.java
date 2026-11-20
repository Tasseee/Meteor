package dev.nightfall.modules;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.class_1297;
import net.minecraft.class_1937;
import net.minecraft.class_2547;
import net.minecraft.class_2596;
import net.minecraft.class_310;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GhostlagManager {
   private static final Logger LOG = LoggerFactory.getLogger("nightfall-ghostlag");
   private static final Map<Integer, Long> SNAPSHOT_UNTIL = new HashMap<>();
   private static class_1937 lastWorld;

   private GhostlagManager() {}

   public static void handle(class_2596<?> packet, class_2547 listener) {
      class_310 mc = class_310.method_1551();
      class_1937 world = mc.field_1687;
      Ghostlag module;
      try {
         module = dev.nightfall.module.ModuleManager.get(Ghostlag.class);
      } catch (IllegalStateException ignored) {
         applyNow(packet, listener);
         return;
      }

      if (!module.isEnabled() || world == null || mc.field_1724 == null) {
         applyNow(packet, listener);
         return;
      }

      int id = Ghostlag.packetEntityId(packet, world);
      if (id < 0 || id == mc.field_1724.method_5628()) {
         applyNow(packet, listener);
         return;
      }

      class_1297 entity = world.method_8469(id);
      if (!Ghostlag.isTarget(entity, mc, module.radiusBlocks())) {
         SNAPSHOT_UNTIL.remove(id);
         applyNow(packet, listener);
         return;
      }

      long now = System.currentTimeMillis();
      Long until = SNAPSHOT_UNTIL.get(id);
      if (until != null && now < until) return;

      SNAPSHOT_UNTIL.put(id, now + Math.max(0, module.delayMs()));
      applyNow(packet, listener);
   }

   public static void tick() {
      class_310 mc = class_310.method_1551();
      if (mc.field_1687 != lastWorld) {
         SNAPSHOT_UNTIL.clear();
         lastWorld = mc.field_1687;
      }
      if (mc.field_1687 == null) {
         SNAPSHOT_UNTIL.clear();
         return;
      }

      long now = System.currentTimeMillis();
      Iterator<Map.Entry<Integer, Long>> it = SNAPSHOT_UNTIL.entrySet().iterator();
      while (it.hasNext()) {
         Map.Entry<Integer, Long> entry = it.next();
         if (entry.getValue() <= now || mc.field_1687.method_8469(entry.getKey()) == null) it.remove();
      }
   }

   public static int delayedPlayers() {
      long now = System.currentTimeMillis();
      int count = 0;
      for (long until : SNAPSHOT_UNTIL.values()) if (until > now) count++;
      return count;
   }

   public static void clear() {
      SNAPSHOT_UNTIL.clear();
      lastWorld = null;
   }

   @SuppressWarnings({"rawtypes", "unchecked"})
   private static void applyNow(class_2596<?> packet, class_2547 listener) {
      try {
         ((class_2596) packet).method_65081(listener);
      } catch (Throwable error) {
         LOG.warn("Ghostlag konnte ein Bewegungs-Paket nicht anwenden", error);
      }
   }
}