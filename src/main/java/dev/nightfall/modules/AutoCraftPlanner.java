package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.StringSetting;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2960;
import net.minecraft.class_332;
import net.minecraft.class_7923;

public final class AutoCraftPlanner extends Module {
   private final StringSetting ingredients = this.add(new StringSetting("Zutaten (ID:Menge, Komma)", "minecraft:oak_planks:4,minecraft:stick:2", 256));
   private final BoolSetting showHud = this.add(new BoolSetting("HUD anzeigen", true));

   public AutoCraftPlanner() {
      super("AutoCraft Planner", "Prüft eine selbst definierte Zutatenliste gegen dein Inventar; craftet nicht automatisch.", Category.WORLD);
   }

   private List<AutoCraftPlanner.Requirement> requirements() {
      List<AutoCraftPlanner.Requirement> out = new ArrayList<>();
      if (mc.field_1724 == null) {
         return out;
      }

      // Merge repeated identifiers first, so each item contributes to one total requirement.
      Map<class_2960, Integer> totals = new LinkedHashMap<>();
      for (String part : this.ingredients.get().split(",")) {
         String clean = part.trim();
         int cut = clean.lastIndexOf(58);
         if (cut <= 0) {
            continue;
         }

         String idText = clean.substring(0, cut);
         int amount;
         try {
            amount = Integer.parseInt(clean.substring(cut + 1));
         } catch (NumberFormatException ignored) {
            continue;
         }

         class_2960 id = class_2960.method_12829(idText);
         if (id == null || amount < 1 || amount > 64) {
            continue;
         }

         class_1792 item = (class_1792)class_7923.field_41178.method_63535(id);
         if (item != class_1802.field_8162) {
            totals.merge(id, amount, Integer::sum);
         }
      }

      for (Map.Entry<class_2960, Integer> entry : totals.entrySet()) {
         class_1792 item = (class_1792)class_7923.field_41178.method_63535(entry.getKey());
         int owned = 0;
         for (int slot = 0; slot < mc.field_1724.method_31548().method_5439(); slot++) {
            class_1799 stack = mc.field_1724.method_31548().method_5438(slot);
            if (stack.method_31574(item)) {
               owned += stack.method_7947();
            }
         }

         out.add(new AutoCraftPlanner.Requirement(entry.getKey().toString(), entry.getValue(), owned));
      }

      return out;
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (this.showHud.get() && mc.field_1724 != null) {
         List<AutoCraftPlanner.Requirement> list = this.requirements();
         if (!list.isEmpty()) {
            int x = 8;
            int y = 96;
            int ready = 0;

            for (AutoCraftPlanner.Requirement r : list) {
               if (r.owned() >= r.amount()) {
                  ready++;
               }
            }

            ctx.method_25294(x - 4, y - 4, x + 206, y + 17 + list.size() * 12, -1441787107);
            ctx.method_51433(mc.field_1772, "CRAFT PLAN  " + ready + "/" + list.size(), x, y, -10099010, true);
            int row = 0;

            for (AutoCraftPlanner.Requirement rx : list) {
               String shortId = rx.id().substring(rx.id().indexOf(58) + 1);
               String line = shortId + "  " + rx.owned() + "/" + rx.amount();
               int color = rx.owned() >= rx.amount() ? -10099010 : -14994;
               ctx.method_51433(mc.field_1772, line, x, y + 13 + row++ * 12, color, false);
            }
         }
      }
   }

   private record Requirement(String id, int amount, int owned) {
   }
}
