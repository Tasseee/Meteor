package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.util.Theme;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.class_332;
import net.minecraft.class_4184;
import net.minecraft.class_640;

public final class TextHuds {
   private TextHuds() {
   }

   public static class BiomeHud extends TextHuds.Line {
      public BiomeHud() {
         super("Biome", "Aktuelles Biom.", 146);
      }

      @Override
      String text() {
         return mc.field_1687 != null && mc.field_1724 != null
            ? mc.field_1687.method_23753(mc.field_1724.method_24515()).method_40230().map(k -> k.method_29177().method_12832()).orElse("?")
            : "-";
      }
   }

   public static class Clock extends TextHuds.Line {
      private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("HH:mm:ss");

      public Clock() {
         super("Clock", "Systemzeit.", 122);
      }

      @Override
      String text() {
         return LocalTime.now().format(F);
      }
   }

   public static class Direction extends TextHuds.Line {
      public Direction() {
         super("Direction", "Blickrichtung.", 134);
      }

      @Override
      String text() {
         if (mc.field_1724 == null) {
            return "-";
         } else {
            class_4184 camera = mc.field_1773.method_19418();
            float yaw = camera != null && camera.method_19332() ? camera.method_19330() : mc.field_1724.method_36454();
            String[] n = new String[]{"S (+Z)", "SW", "W (-X)", "NW", "N (-Z)", "NE", "E (+X)", "SE"};
            int i = Math.floorMod(Math.round(yaw / 45.0F), 8);
            return n[i] + String.format("  %.0f", (yaw % 360.0F + 360.0F) % 360.0F);
         }
      }
   }

   public static class Fps extends TextHuds.Line {
      public Fps() {
         super("FPS", "Zeigt die Bildrate.", 98);
      }

      @Override
      String text() {
         return mc.method_47599() + " FPS";
      }
   }

   abstract static class Line extends HudModule {
      private final int defY;

      Line(String name, String desc, int defY) {
         super(name, desc);
         this.defY = defY;
      }

      abstract String text();

      @Override
      protected int[] defaultPos(int sw, int sh) {
         return new int[]{4, this.defY};
      }

      @Override
      protected void draw(class_332 ctx) {
         String t = this.text();
         this.w = mc.field_1772.method_1727(t) + 10;
         this.h = 12;
         ctx.method_25294(0, 0, this.w, this.h, Theme.HUD_PANEL);
         ctx.method_25294(0, 0, 2, this.h, Theme.ACCENT);
         ctx.method_51433(mc.field_1772, t, 6, 2, Theme.TEXT, true);
      }
   }

   public static class Ping extends TextHuds.Line {
      public Ping() {
         super("Ping", "Zeigt die Latenz zum Server.", 110);
      }

      @Override
      String text() {
         if (mc.field_1724 != null && mc.method_1562() != null) {
            class_640 e = mc.method_1562().method_2871(mc.field_1724.method_5667());
            return e == null ? "Ping -" : e.method_2959() + " ms";
         } else {
            return "Ping -";
         }
      }
   }
}
