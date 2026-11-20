package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Theme;
import java.util.Locale;
import net.minecraft.class_1297;
import net.minecraft.class_1657;
import net.minecraft.class_1937;
import net.minecraft.class_2596;
import net.minecraft.class_2684;
import net.minecraft.class_2726;
import net.minecraft.class_2777;
import net.minecraft.class_332;
import net.minecraft.class_310;
import net.minecraft.class_10264;

public final class Ghostlag extends Module {
   private final NumberSetting delay = this.add(new NumberSetting("Snapshot-Intervall (ms)", 10000.0, 0.0, 30000.0, 100.0));
   private final NumberSetting radius = this.add(new NumberSetting("Spieler-Radius", 12.0, 1.0, 64.0, 1.0));
   private final BoolSetting showHud = this.add(new BoolSetting("HUD anzeigen", true));

   public Ghostlag() {
      super("Ghostlag", "Hält Bewegungsupdates naher Spieler clientseitig zwischen Snapshots zurück.", Category.COMBAT);
   }

   public int delayMs() { return this.delay.getInt(); }
   public int radiusBlocks() { return this.radius.getInt(); }

   @Override
   protected void onDisable() { GhostlagManager.clear(); }

   @Override
   public void onWorldLeave() { GhostlagManager.clear(); }

   @Override
   public void onTick() { GhostlagManager.tick(); }

   @Override
   public void renderHud(class_332 ctx) {
      if (!this.showHud.get() || mc.field_1724 == null || mc.field_1687 == null || mc.field_1690.field_1842) return;
      int active = GhostlagManager.delayedPlayers();
      String line = "GHOSTLAG  " + active + " SPIELER  " + String.format(Locale.ROOT, "%.1fs", this.delay.get() / 1000.0);
      int width = mc.field_1772.method_1727(line) + 14;
      ctx.method_25294(8, 8, 8 + width, 26, Theme.HUD_PANEL);
      ctx.method_25294(8, 8, 10, 26, Theme.ACCENT);
      ctx.method_51433(mc.field_1772, line, 14, 13, Theme.TEXT, true);
   }

   static boolean isMovementPacket(class_2596<?> packet) {
      return packet instanceof class_2684 || packet instanceof class_2726
         || packet instanceof class_10264 || packet instanceof class_2777;
   }

   static int packetEntityId(class_2596<?> packet, class_1937 world) {
      class_1297 entity = null;
      if (packet instanceof class_2684 p) entity = p.method_11645(world);
      else if (packet instanceof class_2726 p) entity = p.method_11786(world);
      else if (packet instanceof class_10264 p) return p.comp_3223();
      else if (packet instanceof class_2777 p) return p.comp_3237();
      return entity == null ? -1 : entity.method_5628();
   }

   static boolean isTarget(class_1297 entity, class_310 client, int radius) {
      return client.field_1724 != null && entity instanceof class_1657
         && entity != client.field_1724 && client.field_1724.method_5739(entity) <= radius;
   }
}