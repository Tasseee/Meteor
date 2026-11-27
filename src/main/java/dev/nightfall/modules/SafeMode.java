package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import java.util.IdentityHashMap;
import java.util.Map;

public final class SafeMode extends Module {
   private final Map<Module, Boolean> previousStates = new IdentityHashMap<>();
   private boolean restoring;

   public SafeMode() {
      super("Safe Mode", "Schaltet alle nicht-vanilla Module temporaer aus und stellt ihren vorherigen Zustand beim Deaktivieren wieder her.", Category.MISC);
   }

   @Override
   protected void onEnable() {
      this.previousStates.clear();
      for (Module module : ModuleManager.all()) {
         if (module != this) {
            this.previousStates.put(module, module.isEnabled());
         }
      }

      this.restoring = true;
      ModuleManager.setSafeModeTransition(true);
      try {
         for (Module module : this.previousStates.keySet()) {
            if (module.isEnabled()) {
               module.setEnabled(false);
            }
         }
      } finally {
         ModuleManager.setSafeModeTransition(false);
         this.restoring = false;
      }
   }

   @Override
   protected void onDisable() {
      ModuleManager.setSafeModeTransition(true);
      try {
         for (Map.Entry<Module, Boolean> entry : this.previousStates.entrySet()) {
            Module module = entry.getKey();
            if (entry.getValue() && !module.isEnabled()) {
               module.setEnabled(true);
            } else if (!entry.getValue() && module.isEnabled()) {
               module.setEnabled(false);
            }
         }
      } finally {
         ModuleManager.setSafeModeTransition(false);
         this.previousStates.clear();
         this.restoring = false;
      }
   }

   public boolean isRestoring() {
      return this.restoring;
   }
}
