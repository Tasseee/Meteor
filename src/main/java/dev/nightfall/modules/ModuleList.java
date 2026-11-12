package dev.nightfall.modules;

import dev.nightfall.config.Config;
import dev.nightfall.module.Category;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_332;

public class ModuleList extends HudModule {
   private final List<Module> sorted = new ArrayList<>();

   public ModuleList() {
      super("ModuleList", "Liste aktiver Module.");
      this.setEnabled(true);
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{sw - this.w - 4, 4};
   }

   @Override
   public int[] pos(int sw, int sh) {
      int[] saved = Config.hud.get(this.getName());
      if (saved != null && saved[1] == 112) {
         saved[1] = 4;
      }

      return super.pos(sw, sh);
   }

   @Override
   protected void draw(class_332 ctx) {
      this.sorted.clear();

      for (Module m : ModuleManager.all()) {
         if (m.isEnabled() && m.getCategory() != Category.HUD) {
            this.sorted.add(m);
         }
      }

      this.sorted.sort((a, b) -> mc.field_1772.method_1727(b.getName()) - mc.field_1772.method_1727(a.getName()));
      int maxW = 40;

      for (Module mx : this.sorted) {
         maxW = Math.max(maxW, mc.field_1772.method_1727(mx.getName()) + 8);
      }

      this.w = maxW;
      this.h = Math.max(12, this.sorted.size() * 11);
      int y = 0;

      for (Module mx : this.sorted) {
         int mw = mc.field_1772.method_1727(mx.getName()) + 8;
         ctx.method_25294(this.w - mw, y, this.w, y + 11, Theme.HUD_PANEL);
         ctx.method_25294(this.w - 2, y, this.w, y + 11, Theme.ACCENT);
         ctx.method_51433(mc.field_1772, mx.getName(), this.w - mw + 3, y + 2, Theme.TEXT, true);
         y += 11;
      }
   }
}
