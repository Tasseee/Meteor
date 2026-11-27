package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import java.util.IdentityHashMap;
import java.util.Map;

public final class SafeMode extends Module {
   private final Map<Module, Boolean> previousStates = new IdentityHashMap<>();
   private boolean restoring;

   public SafeMode() {
      super("Safe Mode", "Schaltet nicht erlaubte Module aus, behaelt harmlose kosmetische Visuals aktiv und blendet alle Client-HUDs aus.", Category.MISC);
   }

   private boolean isSafeVisual(Module module) {
      return module instanceof Atmosphere
         || module instanceof FakeRain
         || module instanceof SkyVisuals
         || module instanceof ChinaHat
         || module instanceof FirstPersonStyle
         || module instanceof WeaponAnimations
         || module instanceof SlowSwing
         || module instanceof MovementParticles
         || module instanceof ParticleTrail
         || module instanceof Trail
         || module instanceof HitParticles
         || module instanceof DropGlow
         || module instanceof KillEffect
         || module instanceof JumpCircles
         || module instanceof JumpWave
         || module instanceof Fog
         || module instanceof GhostSkin
         || module instanceof CustomCrosshair
         || module instanceof GUITheme;
   }

   private boolean isHiddenHud(Module module) {
      return module instanceof HudModule;
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
            if ((!this.isSafeVisual(module) || this.isHiddenHud(module)) && module.isEnabled()) {
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
