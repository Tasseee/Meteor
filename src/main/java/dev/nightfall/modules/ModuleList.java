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

public final class ModuleList extends HudModule {
   private final List<Module> sorted = new ArrayList<>();
   private static final int HEADER_HEIGHT = 15;
   private static final int ROW_HEIGHT = 12;

   public ModuleList() {
      super("ModuleList", "Clean sortierte Liste aktiver Module.");
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

      for (Module module : ModuleManager.all()) {
         if (module.isEnabled() && module.getCategory() != Category.HUD) {
            this.sorted.add(module);
         }
      }

      this.sorted.sort((a, b) -> {
         int width = mc.field_1772.method_1727(b.getName()) - mc.field_1772.method_1727(a.getName());
         return width != 0 ? width : a.getName().compareToIgnoreCase(b.getName());
      });

      int maxTextWidth = 0;
      for (Module module : this.sorted) {
         maxTextWidth = Math.max(maxTextWidth, mc.field_1772.method_1727(module.getName()));
      }

      this.w = Math.max(62, maxTextWidth + 12);
      this.h = HEADER_HEIGHT + this.sorted.size() * ROW_HEIGHT;

      // Compact header gives the list a clear anchor without adding visual noise.
      ctx.method_25294(0, 0, this.w, HEADER_HEIGHT, Theme.HUD_PANEL);
      ctx.method_25294(this.w - 2, 0, this.w, HEADER_HEIGHT, Theme.ACCENT);
      ctx.method_51433(mc.field_1772, "ACTIVE", 5, 4, Theme.TEXT_DIM, true);

      String count = Integer.toString(this.sorted.size());
      int countWidth = mc.field_1772.method_1727(count);
      ctx.method_51433(mc.field_1772, count, this.w - countWidth - 6, 4, Theme.TEXT, true);

      int y = HEADER_HEIGHT;
      for (Module module : this.sorted) {
         int textWidth = mc.field_1772.method_1727(module.getName());
         int rowWidth = Math.max(44, textWidth + 12);
         int x = this.w - rowWidth;

         ctx.method_25294(x, y, this.w, y + ROW_HEIGHT - 1, Theme.HUD_PANEL);
         ctx.method_25294(this.w - 2, y, this.w, y + ROW_HEIGHT - 1, Theme.ACCENT);
         ctx.method_51433(mc.field_1772, module.getName(), x + 5, y + 2, Theme.TEXT, true);
         y += ROW_HEIGHT;
      }
   }
}
