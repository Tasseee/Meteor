package dev.nightfall.modules;

import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.NotificationCenter;
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
 * ONLINE bedeutet: Profil steht in der vom verbundenen HugoSMP-Netz gelieferten Tab-Liste.
 * SERVER bedeutet: Spieler-Entity ist in der aktuell verbundenen Client-Welt geladen.
 * Die Tab-Liste kann netzwerkweit sein und nennt keinen Backend-/Unterserver.
 */
public final class StaffPresence {
   private static final String HUGO_HOST = "hugosmp.net";
   private static final Set<String> TAB = new HashSet<>();
   private static final Set<String> WORLD = new HashSet<>();
   private static final Set<String> NEARBY = new HashSet<>();
   private static final Map<String, String> TAB_NAMES = new HashMap<>();
   private static final Map<String, String> WORLD_NAMES = new HashMap<>();
   private static boolean tabAvailable;
   private static boolean worldAvailable;
   private static boolean hugoSmpServer;

   private StaffPresence() {}

   public static void update(class_310 mc) {
      Set<String> previouslyNearby = new HashSet<>(NEARBY);
      clearSnapshot();

      if (mc == null || mc.field_1724 == null || mc.field_1687 == null) {
         NEARBY.clear();
         return;
      }

      StaffWatch staff = ModuleManager.get(StaffWatch.class);
      boolean staffWatchActive = staff.isEnabled();
      boolean hintsActive = ModuleManager.get(AntiVanishHints.class).isEnabled();
      if (!staffWatchActive && !hintsActive) {
         NEARBY.clear();
         return;
      }

      Set<String> names = staff.staffNames.get();
      if (names.isEmpty()) {
         NEARBY.clear();
         return;
      }

      hugoSmpServer = isHugoSmpAddress(mc);
      if (!hugoSmpServer) {
         // Auf fremden Servern keine HugoSMP-Online- oder Nähe-Aussage treffen.
         NEARBY.clear();
         return;
      }

      var network = mc.method_1562();
      tabAvailable = network != null;
      if (network != null) {
         for (class_640 entry : network.method_45732()) {
            if (entry == null || entry.method_2966() == null) continue;
            add(TAB, TAB_NAMES, entry.method_2966().name());
         }
      }

      worldAvailable = true;
      Set<String> currentlyNearby = new HashSet<>();
      double renderDistance = renderDistanceBlocks(mc);
      double renderDistanceSquared = renderDistance * renderDistance;
      boolean alertEnabled = staffWatchActive && staff.nearbyAlertEnabled();

      for (class_742 player : mc.field_1687.method_18456()) {
         if (player == null || player == mc.field_1724 || player.method_7334() == null) continue;

         String playerName = player.method_7334().name();
         add(WORLD, WORLD_NAMES, playerName);

         if (alertEnabled && playerName != null) {
            String playerKey = key(playerName);
            if (names.contains(playerKey) && player.method_5858(mc.field_1724) <= renderDistanceSquared) {
               currentlyNearby.add(playerKey);
               if (!previouslyNearby.contains(playerKey)) {
                  NotificationCenter.push(
                     "StaffWatch",
                     displayName(playerKey) + " is in your render distance",
                     6000L,
                     0xFFFFC46B
                  );
               }
            }
         }
      }

      NEARBY.clear();
      NEARBY.addAll(currentlyNearby);
   }

   private static boolean isHugoSmpAddress(class_310 mc) {
      var serverInfo = mc.method_1558();
      if (serverInfo == null || serverInfo.field_3761 == null) return false;

      String host = serverInfo.field_3761.trim().toLowerCase(Locale.ROOT);
      int scheme = host.indexOf("://");
      if (scheme >= 0) host = host.substring(scheme + 3);
      int slash = host.indexOf('/');
      if (slash >= 0) host = host.substring(0, slash);
      int colon = host.lastIndexOf(':');
      if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
      while (host.endsWith(".")) host = host.substring(0, host.length() - 1);

      return host.equals(HUGO_HOST) || host.endsWith("." + HUGO_HOST);
   }

   private static double renderDistanceBlocks(class_310 mc) {
      int chunks = 8;
      try {
         Object value = mc.field_1690.method_42503().method_41753();
         if (value instanceof Number number) chunks = number.intValue();
      } catch (RuntimeException ignored) {
         // Use a conservative fallback if the client option is unavailable.
      }
      chunks = Math.max(2, Math.min(64, chunks));
      return chunks * 16.0;
   }

   private static void add(Set<String> target, Map<String, String> display, String name) {
      if (name == null || name.isBlank()) return;
      String normalized = key(name);
      target.add(normalized);
      display.putIfAbsent(normalized, name.trim());
   }

   public static boolean inTab(String name) {
      return name != null && TAB.contains(key(name));
   }

   public static boolean loadedInWorld(String name) {
      return name != null && WORLD.contains(key(name));
   }

   public static boolean isHugoSmpServer() {
      return hugoSmpServer;
   }

   public static boolean onlineOnHugoSmp(String name) {
      return hugoSmpServer && (inTab(name) || loadedInWorld(name));
   }

   public static boolean onCurrentServer(String name) {
      return hugoSmpServer && loadedInWorld(name);
   }

   public static boolean tabAvailable() { return tabAvailable; }
   public static boolean worldAvailable() { return worldAvailable; }

   public static String displayName(String normalizedName) {
      String normalized = key(normalizedName);
      String name = TAB_NAMES.get(normalized);
      if (name == null) name = WORLD_NAMES.get(normalized);
      return name == null ? normalizedName : name;
   }

   public static int rank(String name) {
      if (!hugoSmpServer) return 3;
      if (onCurrentServer(name)) return 0;
      if (inTab(name)) return 1;
      return 2;
   }

   public static void reset() {
      clearSnapshot();
      NEARBY.clear();
   }

   private static String key(String name) {
      return name.trim().toLowerCase(Locale.ROOT);
   }

   private static void clearSnapshot() {
      TAB.clear();
      WORLD.clear();
      TAB_NAMES.clear();
      WORLD_NAMES.clear();
      tabAvailable = false;
      worldAvailable = false;
      hugoSmpServer = false;
   }
}
