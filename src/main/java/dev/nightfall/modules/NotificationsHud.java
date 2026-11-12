package dev.nightfall.modules;

import dev.nightfall.gui.HudEditorScreen;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.NotificationCenter;
import dev.nightfall.util.Theme;
import java.util.List;
import net.minecraft.class_332;

public final class NotificationsHud extends HudModule {
   private final NumberSetting maxVisible = this.add(new NumberSetting("Max. Meldungen", 4.0, 1.0, 6.0, 1.0));

   public NotificationsHud() {
      super("Notifications", "Animierte Status- und Warnmeldungen.");
      this.setEnabled(true);
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{sw - 184, 8};
   }

   @Override
   protected void draw(class_332 ctx) {
      List<NotificationCenter.Toast> toasts = NotificationCenter.active(System.currentTimeMillis());
      boolean preview = mc.field_1755 instanceof HudEditorScreen;
      int count = Math.min(toasts.size(), this.maxVisible.getInt());
      if (count == 0 && preview) {
         count = 1;
      }

      this.w = 176;
      this.h = count * 34;
      long now = System.currentTimeMillis();

      for (int i = 0; i < count; i++) {
         NotificationCenter.Toast toast = toasts.isEmpty() ? null : toasts.get(toasts.size() - 1 - Math.min(i, toasts.size() - 1));
         int y = i * 34;
         float progress = toast == null ? 0.72F : toast.progress(now);
         int offset = Math.round((1.0F - progress) * 8.0F);
         ctx.method_25294(offset, y, this.w, y + 30, Theme.HUD_PANEL);
         int color = toast == null ? Theme.ACCENT : toast.color;
         String title = toast == null ? "Nightfall" : toast.title;
         String message = toast == null ? "Beispiel-Benachrichtigung" : toast.message;
         ctx.method_25294(offset, y, offset + 3, y + 30, color);
         ctx.method_51433(mc.field_1772, this.trim(title, 23), offset + 8, y + 4, Theme.TEXT, true);
         ctx.method_51433(mc.field_1772, this.trim(message, 25), offset + 8, y + 15, Theme.TEXT_DIM, false);
         int barWidth = this.w - offset - 8;
         ctx.method_25294(offset + 4, y + 27, offset + 4 + barWidth, y + 29, -13619142);
         ctx.method_25294(offset + 4, y + 27, offset + 4 + Math.round(barWidth * progress), y + 29, color);
      }
   }

   private String trim(String text, int max) {
      if (text == null) {
         return "";
      } else {
         return text.length() <= max ? text : text.substring(0, Math.max(1, max - 1)) + "…";
      }
   }
}
