package dev.nightfall.modules;

import dev.nightfall.module.ModuleManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_310;
import net.minecraft.class_640;
import net.minecraft.class_742;

/**
 * Gemeinsamer, rein clientseitiger Präsenz-Snapshot für StaffWatch und AntiVanishHints.
 *
 * TAB bedeutet nur: Profil ist in der vom Server gelieferten Spielerliste.
 * WORLD bedeutet nur: Spieler-Entity ist aktuell in der Client-Welt geladen.
 * Die Tab-Liste enthält auf Netzwerkverbünden möglicherweise Spieler anderer Unterserver.
 */
public final class StaffPresence {
   private static final Set<String> TAB = new HashSet<>();
   private static final Set<String> WORLD = new HashSet<>();
   private static final Map<String, String> TAB_NAMES = new HashMap<>();
   private static final Map<String, String> WORLD_NAMES = new HashMap<>();
   private static boolean tabAvailable;
   private static boolean worldAvailable;

   private StaffPresence() {}

   public static void update(class_310 mc) {
      clear();
      if (mc == null || mc.field_1724 == null || mc.field_1687 == null) {
         return;
      }

      StaffWatch staff = ModuleManager.get(StaffWatch.class);
      if (!staff.isEnabled() && !ModuleManager.get(AntiVanishHints.class).isEnabled()) {
         return;
      }

      Set<String> names = staff.staffNames.get();
      if (names.isEmpty()) {
         return;
      }

      var network = mc.method_1562();
      tabAvailable = network != null;
      if (network != null) {
         for (class_640 entry : network.method_45732()) {
            if (entry == null || entry.method_2966() == null) continue;
            String name = entry.method_2966().name();
            add(TAB, TAB_NAMES, name);
         }
      }

      worldAvailable = true;
      for (class_742 player : mc.field_1687.method_18456()) {
         if (player == null || player == mc.field_1724 || player.method_7334() == null) continue;
         add(WORLD, WORLD_NAMES, player.method_7334().name());
      }
   }

   private static void add(Set<String> target, Map<String, String> display, String name) {
      if (name == null || name.isBlank()) return;
      String key = name.trim().toLowerCase(Locale.ROOT);
      target.add(key);
      display.putIfAbsent(key, name.trim());
   }

   public static boolean inTab(String name) {
      return name != null && TAB.contains(key(name));
   }

   public static boolean loadedInWorld(String name) {
      return name != null && WORLD.contains(key(name));
   }

   public static boolean tabAvailable() { return tabAvailable; }
   public static boolean worldAvailable() { return worldAvailable; }

   public static String displayName(String normalizedName) {
      String key = key(normalizedName);
      String name = TAB_NAMES.get(key);
      if (name == null) name = WORLD_NAMES.get(key);
      return name == null ? normalizedName : name;
   }

   public static int rank(String name) {
      boolean tab = inTab(name);
      boolean world = loadedInWorld(name);
      if (tab && world) return 0;
      return tab || world ? 1 : 2;
   }

   public static void reset() {
      clear();
   }

   private static String key(String name) {
      return name.trim().toLowerCase(Locale.ROOT);
   }

   private static void clear() {
      TAB.clear();
      WORLD.clear();
      TAB_NAMES.clear();
      WORLD_NAMES.clear();
      tabAvailable = false;
      worldAvailable = false;
   }
}
