package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.NotificationCenter;
import net.minecraft.class_310;
import net.minecraft.class_412;
import net.minecraft.class_419;
import net.minecraft.class_639;
import net.minecraft.class_642;

public final class AutoReconnect extends Module {
   private final NumberSetting delaySeconds = this.add(new NumberSetting("Verzoegerung (Sek.)", 5.0, 2.0, 60.0, 1.0));
   private final NumberSetting maxAttempts = this.add(new NumberSetting("Max. Versuche", 3.0, 1.0, 10.0, 1.0));
   private class_642 lastServer;
   private int attempts;
   private int ticksRemaining;
   private int screenGrace;
   private boolean pending;

   public AutoReconnect() {
      super("AutoReconnect", "Verbindet nach einem unerwarteten Disconnect begrenzt erneut.", Category.MISC);
   }

   @Override
   protected void onEnable() {
      this.attempts = 0;
      this.pending = false;
      this.lastServer = null;
   }

   @Override
   protected void onDisable() {
      this.pending = false;
      this.lastServer = null;
      this.attempts = 0;
   }

   public void onDisconnected(class_642 server) {
      if (this.isEnabled() && server != null) {
         if (this.lastServer == null || this.attempts == 0) {
            this.lastServer = server;
         }

         if (this.attempts >= this.maxAttempts.getInt()) {
            this.pending = false;
            NotificationCenter.push("AutoReconnect", "Maximale Anzahl an Verbindungsversuchen erreicht.");
         } else {
            this.pending = true;
            this.ticksRemaining = this.delaySeconds.getInt() * 20;
            this.screenGrace = 0;
         }
      }
   }

   public void onJoined() {
      this.pending = false;
      this.attempts = 0;
      this.lastServer = null;
   }

   public void tickDisconnected(class_310 client) {
      if (this.isEnabled() && this.pending && this.lastServer != null && client.method_1562() == null) {
         if (!(client.field_1755 instanceof class_419)) {
            if (++this.screenGrace > 40) {
               this.pending = false;
            }
         } else if (this.ticksRemaining > 0) {
            this.ticksRemaining--;
         } else if (this.attempts >= this.maxAttempts.getInt()) {
            this.pending = false;
            NotificationCenter.push("AutoReconnect", "Verbindung nach " + this.attempts + " Versuchen abgebrochen.");
         } else {
            this.attempts++;
            this.pending = false;
            NotificationCenter.push("AutoReconnect", "Verbindungsversuch " + this.attempts + "/" + this.maxAttempts.getInt());
            class_412.method_36877(client.field_1755, client, class_639.method_2950(this.lastServer.field_3761), this.lastServer, false, null);
         }
      }
   }
}
