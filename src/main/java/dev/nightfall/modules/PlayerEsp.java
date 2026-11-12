package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Projection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_638;

public class PlayerEsp extends EntityScanEsp {
   private final BoolSetting autoInvsee = this.add(new BoolSetting("Auto Invsee", true));
   private final Set<String> inspectedPlayers = new HashSet<>();
   private class_638 trackedWorld;

   public PlayerEsp() {
      super("PlayerESP", "Markiert andere Spieler und kann sie automatisch mit /invsee öffnen.", 256, 512, 15680580);
   }

   @Override
   protected boolean accept(class_1297 e) {
      return e instanceof class_1657;
   }

   @Override
   public void onTick() {
      if (mc.field_1687 != null && mc.field_1724 != null && mc.method_1562() != null) {
         if (this.trackedWorld != mc.field_1687) {
            this.inspectedPlayers.clear();
            this.trackedWorld = mc.field_1687;
         }

         if (this.autoInvsee.get()) {
            double rangeSquared = this.range.get() * this.range.get();

            for (class_1657 player : mc.field_1687.method_18456()) {
               if (player != mc.field_1724) {
                  String name = player.method_7334().name();
                  if (name != null && name.matches("[A-Za-z0-9_]{3,16}")) {
                     String key = name.toLowerCase(Locale.ROOT);
                     if (!this.inspectedPlayers.contains(key)
                        && !(player.method_5858(mc.field_1724) > rangeSquared)
                        && (!this.visibleOnly.get() || Projection.canSeeFromCamera(mc, player))) {
                        this.inspectedPlayers.add(key);
                        mc.method_1562().method_45730("invsee " + name);
                     }
                  }
               }
            }
         }
      } else {
         this.inspectedPlayers.clear();
         this.trackedWorld = null;
      }
   }

   @Override
   protected void onDisable() {
      this.inspectedPlayers.clear();
      this.trackedWorld = null;
   }
}
