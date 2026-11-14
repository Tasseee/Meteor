package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.util.NotificationCenter;
import dev.nightfall.util.Storage;
import java.util.Locale;
import net.minecraft.class_1657;

public final class AutoRtp extends Module {
   private final NumberSetting interval = this.add(new NumberSetting("RTP Intervall (Sek.)", 45.0, 30.0, 300.0, 5.0));
   private final BoolSetting stopOnPlayer = this.add(new BoolSetting("Stop bei Spieler", true));
   private final NumberSetting playerRadius = this.add(new NumberSetting("Spieler-Radius", 64.0, 16.0, 128.0, 8.0));
   private final BoolSetting stopOnSus = this.add(new BoolSetting("Stop bei SUS-Chunk", true));
   private final BoolSetting donutOnly = this.add(new BoolSetting("Nur DonutSMP", true));
   private int cooldownTicks;
   private int teleportWaitTicks;
   private boolean waitingForTeleport;
   private double startX;
   private double startZ;
   private boolean warnedServer;

   public AutoRtp() {
      super("AutoRTP", "Sendet /rtp in Intervallen und stoppt bei Spielern oder einem SUS-Chunk.", Category.WORLD);
   }

   @Override
   protected void onEnable() {
      this.cooldownTicks = 0;
      this.teleportWaitTicks = 0;
      this.waitingForTeleport = false;
      this.warnedServer = false;
   }

   @Override
   protected void onDisable() {
      this.waitingForTeleport = false;
      this.cooldownTicks = 0;
      this.teleportWaitTicks = 0;
   }

   @Override
   public void onWorldLeave() {
      this.setEnabled(false);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null || mc.method_1562() == null
         || mc.field_1724.field_3944 == null) {
         return;
      }

      String server = Storage.server().toLowerCase(Locale.ROOT);
      if (this.donutOnly.get() && !server.contains("donutsmp")) {
         if (!this.warnedServer) {
            NotificationCenter.push("AutoRTP", "Nicht auf DonutSMP erkannt. AutoRTP gestoppt.");
            this.warnedServer = true;
         }
         this.setEnabled(false);
         return;
      }

      if (this.stopOnPlayer.get()) {
         double radiusSquared = this.playerRadius.get() * this.playerRadius.get();
         for (class_1657 player : mc.field_1687.method_18456()) {
            if (player != mc.field_1724 && player.method_5858(mc.field_1724) <= radiusSquared) {
               NotificationCenter.push("AutoRTP", "Spieler in der Naehe erkannt: " + player.method_7334().name());
               this.setEnabled(false);
               return;
            }
         }
      }

      if (this.stopOnSus.get()) {
         BaseFinder finder = ModuleManager.get(BaseFinder.class);
         if (finder.isEnabled()) {
            int pcx = mc.field_1724.method_31477() >> 4;
            int pcz = mc.field_1724.method_31479() >> 4;
            for (ChunkResult result : finder.results()) {
               if (result.status == ChunkResult.Status.SUS
                  && Math.abs(result.cx - pcx) <= 2 && Math.abs(result.cz - pcz) <= 2) {
                  NotificationCenter.push("AutoRTP", "SUS-Chunk erkannt (" + result.cx + ", " + result.cz + "). AutoRTP gestoppt.");
                  this.setEnabled(false);
                  return;
               }
            }
         }
      }

      if (this.waitingForTeleport) {
         double dx = mc.field_1724.method_23317() - this.startX;
         double dz = mc.field_1724.method_23321() - this.startZ;
         if (dx * dx + dz * dz >= 64.0 * 64.0) {
            this.waitingForTeleport = false;
            this.cooldownTicks = this.interval.getInt() * 20;
            this.teleportWaitTicks = 0;
            NotificationCenter.push("AutoRTP", "Teleport erkannt. Naechster RTP-Versuch nach Cooldown.");
         } else if (++this.teleportWaitTicks >= 2400) {
            NotificationCenter.push("AutoRTP", "Nach 120 Sekunden kein RTP-Teleport erkannt. AutoRTP gestoppt.");
            this.setEnabled(false);
         }
         return;
      }

      if (this.cooldownTicks > 0) {
         this.cooldownTicks--;
         return;
      }

      this.startX = mc.field_1724.method_23317();
      this.startZ = mc.field_1724.method_23321();
      mc.field_1724.field_3944.method_45730("rtp");
      this.waitingForTeleport = true;
      this.teleportWaitTicks = 0;
      NotificationCenter.push("AutoRTP", "/rtp gesendet. Warte auf Teleport.");
   }
}
