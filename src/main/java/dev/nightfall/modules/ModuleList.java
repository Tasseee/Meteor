package dev.nightfall.modules;

import dev.nightfall.config.Config;
import dev.nightfall.module.Category;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_332;

public final class ModuleList extends HudModule {
   private final List<Module> sorted = new ArrayList<>();
   private final Map<Module, Float> animation = new HashMap<>();
   private long lastFrameNanos;
   private static final int HEADER_HEIGHT = 15;
   private static final int ROW_HEIGHT = 12;

   public ModuleList() {
      super("ModuleList", "Clean sortierte Liste aktiver Module mit Ein-/Ausblendanimationen.");
      this.setEnabled(true);
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{sw - this.activeContentWidth() - 4, 4};
   }

   private int activeContentWidth() {
      int maxTextWidth = 0;
      for (Module module : ModuleManager.all()) {
         if (module.isEnabled() && module.getCategory() != Category.HUD) {
            maxTextWidth = Math.max(maxTextWidth, mc.field_1772.method_1727(module.getName()));
         }
      }
      return Math.max(62, maxTextWidth + 12);
   }

   @Override
   public int[] pos(int sw, int sh) {
      int[] saved = Config.hud.get(this.getName());
      if (saved != null && saved[1] == 112) saved[1] = 4;
      return super.pos(sw, sh);
   }

   @Override
   protected void draw(class_332 ctx) {
      long now = System.nanoTime();
      float dt = this.lastFrameNanos == 0L ? 1.0F / 60.0F
         : Math.min(0.1F, Math.max(0.0F, (now - this.lastFrameNanos) / 1_000_000_000.0F));
      this.lastFrameNanos = now;

      this.sorted.clear();
      for (Module module : ModuleManager.all()) {
         if (module.getCategory() == Category.HUD) continue;
         float current = this.animation.getOrDefault(module, module.isEnabled() ? 1.0F : 0.0F);
         float target = module.isEnabled() ? 1.0F : 0.0F;
         float factor = Math.min(1.0F, dt * 12.0F);
         current += (target - current) * factor;
         if (Math.abs(target - current) < 0.015F) current = target;
         this.animation.put(module, current);
         if (current > 0.015F) this.sorted.add(module);
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

      ctx.method_25294(0, 0, this.w, HEADER_HEIGHT, Theme.HUD_PANEL);
      ctx.method_25294(this.w - 2, 0, this.w, HEADER_HEIGHT, Theme.ACCENT);
      ctx.method_51433(mc.field_1772, "ACTIVE", 5, 4, Theme.TEXT_DIM, true);
      String count = Integer.toString((int)ModuleManager.all().stream().filter(m -> m.isEnabled() && m.getCategory() != Category.HUD).count());
      int countWidth = mc.field_1772.method_1727(count);
      ctx.method_51433(mc.field_1772, count, this.w - countWidth - 6, 4, Theme.TEXT, true);

      int y = HEADER_HEIGHT;
      for (Module module : this.sorted) {
         float progress = this.animation.getOrDefault(module, 0.0F);
         int textWidth = mc.field_1772.method_1727(module.getName());
         int rowWidth = Math.max(44, textWidth + 12);
         int slide = Math.round((1.0F - progress) * Math.min(18, rowWidth));
         int x = this.w - rowWidth + slide;
         int alpha = Math.max(0, Math.min(255, Math.round(progress * 255.0F)));
         int panel = (alpha / 3) << 24 | (Theme.HUD_PANEL & 0xFFFFFF);
         int accent = alpha << 24 | (Theme.ACCENT & 0xFFFFFF);
         int text = alpha << 24 | (Theme.TEXT & 0xFFFFFF);
         ctx.method_25294(x, y, this.w, y + ROW_HEIGHT - 1, panel);
         ctx.method_25294(this.w - 2, y, this.w, y + ROW_HEIGHT - 1, accent);
         ctx.method_51433(mc.field_1772, module.getName(), x + 5, y + 2, text, true);
         y += ROW_HEIGHT;
      }
   }
}
