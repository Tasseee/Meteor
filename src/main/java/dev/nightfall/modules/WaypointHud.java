package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.util.Storage;
import dev.nightfall.util.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_332;

public class WaypointHud extends HudModule {
   private final List<Waypoint> tmp = new ArrayList<>();

   public WaypointHud() {
      super("WaypointList", "Naechste Waypoints mit Distanz.");
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{4, 234};
   }

   @Override
   protected void draw(class_332 ctx) {
      if (mc.field_1724 != null) {
         this.tmp.clear();
         String dim = Storage.dim();

         for (Waypoint wp : WaypointManager.all()) {
            if (wp.dim.equals(dim)) {
               this.tmp.add(wp);
            }
         }

         double px = mc.field_1724.method_23317();
         double pz = mc.field_1724.method_23321();
         this.tmp.sort((a, b) -> Double.compare(Math.hypot(a.x - px, a.z - pz), Math.hypot(b.x - px, b.z - pz)));
         int n = Math.min(5, this.tmp.size());
         String[] lines = new String[n];
         int widest = mc.field_1772.method_1727("Waypoints");

         for (int i = 0; i < n; i++) {
            Waypoint waypoint = this.tmp.get(i);
            lines[i] = waypoint.name + " " + (int)Math.hypot(waypoint.x - px, waypoint.z - pz) + "m";
            widest = Math.max(widest, mc.field_1772.method_1727(lines[i]));
         }

         this.w = Math.max(120, Math.min(210, widest + 12));
         this.h = 12 + n * 10;
         ctx.method_25294(0, 0, this.w, this.h, Theme.HUD_PANEL);
         ctx.method_25294(0, 0, 2, this.h, Theme.ACCENT);
         ctx.method_51433(mc.field_1772, "Waypoints", 6, 2, Theme.TEXT_DIM, false);

         for (int i = 0; i < n; i++) {
            Waypoint waypoint = this.tmp.get(i);
            String line = this.trim(lines[i], this.w - 12);
            ctx.method_51433(mc.field_1772, line, 6, 12 + i * 10, 0xFF000000 | waypoint.color, false);
         }
      }
   }

   private String trim(String text, int maxWidth) {
      while (mc.field_1772.method_1727(text) > maxWidth && text.length() > 1) {
         text = text.substring(0, text.length() - 2) + "…";
      }

      return text;
   }
}
