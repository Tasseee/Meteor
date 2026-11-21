package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Nightfall-Port der Meteor-Favorites-Logik.
 * Die eigentlichen Favoriten werden bereits direkt am Nightfall-Modul
 * über Module#setFavorite gespeichert. Dieses Modul pflegt zusätzlich
 * die zuletzt aktivierten Module für spätere GUI-Anzeige.
 */
public final class Favorites extends Module {
   private final List<String> recent = new ArrayList<>();
   private final Set<String> lastActive = new HashSet<>();
   private int recentCount = 8;

   public Favorites() {
      super("Favorites", "Favoriten und zuletzt benutzte Module.", Category.MISC);
   }

   @Override
   protected void onEnable() {
      lastActive.clear();
      for (Module m : ModuleManager.all()) {
         if (m != this && m.isEnabled()) lastActive.add(m.getName());
      }
   }

   @Override
   public void onTick() {
      if (!isEnabled()) return;

      Set<String> now = new HashSet<>();
      for (Module m : ModuleManager.all()) {
         if (m != this && m.isEnabled()) now.add(m.getName());
      }

      for (String name : now) {
         if (!lastActive.contains(name)) {
            recent.remove(name);
            recent.add(0, name);
         }
      }

      while (recent.size() > recentCount) recent.remove(recent.size() - 1);
      lastActive.clear();
      lastActive.addAll(now);
   }

   public List<String> getFavorites() {
      List<String> out = new ArrayList<>();
      for (Module m : ModuleManager.all()) {
         if (m.isFavorite()) out.add(m.getName());
      }
      return Collections.unmodifiableList(out);
   }

   public List<String> getRecent() {
      return Collections.unmodifiableList(recent);
   }

   public int getRecentCount() {
      return recentCount;
   }

   public void setRecentCount(int count) {
      recentCount = Math.max(1, Math.min(20, count));
      while (recent.size() > recentCount) recent.remove(recent.size() - 1);
   }
}
