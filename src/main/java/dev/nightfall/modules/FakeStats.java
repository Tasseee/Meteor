package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.StringSetting;
import dev.nightfall.util.Theme;
import net.minecraft.class_332;

public final class FakeStats extends HudModule {
   public final StringSetting displayName = this.add(new StringSetting("Anzeigename", "Nightfall", 32));
   public final NumberSetting kills = this.add(new NumberSetting("Fake Kills", 42.0, 0.0, 999999.0, 1.0));
   public final NumberSetting deaths = this.add(new NumberSetting("Fake Tode", 3.0, 0.0, 999999.0, 1.0));
   public final NumberSetting wins = this.add(new NumberSetting("Fake Siege", 17.0, 0.0, 999999.0, 1.0));

   public FakeStats() {
      super("FakeStats", "Nur lokales, deutlich markiertes Fake-Stats-HUD; keine Serverdaten werden veraendert.");
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{sw - 148, sh / 2 - 30};
   }

   @Override
   protected void draw(class_332 ctx) {
      String[] lines = new String[]{
         "FAKE STATS · LOKAL", this.displayName.get(), "Kills  " + this.kills.getInt(), "Tode   " + this.deaths.getInt(), "Siege  " + this.wins.getInt()
      };
      int mw = 0;

      for (String line : lines) {
         mw = Math.max(mw, mc.field_1772.method_1727(line));
      }

      this.w = mw + 14;
      this.h = lines.length * 12 + 8;
      ctx.method_25294(0, 0, this.w, this.h, -804253672);
      ctx.method_25294(0, 0, 3, this.h, -19372);
      ctx.method_51433(mc.field_1772, lines[0], 7, 4, -19372, true);

      for (int i = 1; i < lines.length; i++) {
         ctx.method_51433(mc.field_1772, lines[i], 7, 4 + i * 12, i == 1 ? Theme.TEXT : Theme.TEXT_DIM, false);
      }
   }
}
