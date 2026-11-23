package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Esp2DRenderer;
import dev.nightfall.util.Projection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_2680;
import net.minecraft.class_7923;
import net.minecraft.class_1799;
import net.minecraft.class_332;

public final class BlockESP extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 12.0, 6.0, 200.0, 1.0));
   private final NumberSetting maxBlocks = this.add(new NumberSetting("Max Markierungen", 600.0, 20.0, 1200.0, 20.0));
   private final ModeSetting style = this.add(new ModeSetting(
      "Rahmen-Stil", "Block-Outline", "Ecken", "Voll", "Leuchten", "Block-Outline", "2D"
   ));
   private final NumberSetting outline = this.add(new NumberSetting("Outline-Stärke", 2.0, 1.0, 5.0, 1.0));
   private final ColorSetting color = this.add(new ColorSetting("Farbe", 0x62D8FF));
   private final BoolSetting customSelection = this.add(new BoolSetting("Eigene Blockauswahl verwenden", false));
   private final ListSetting blockIds = this.add(new ListSetting("Eigene Block-IDs"));
   private final BoolSetting diamonds = this.add(new BoolSetting("Diamanterz", true));
   private final BoolSetting ancient = this.add(new BoolSetting("Antiker Schrott", true));
   private final BoolSetting chests = this.add(new BoolSetting("Kisten", true));
   private final BoolSetting spawners = this.add(new BoolSetting("Spawner", true));
   private final BoolSetting obsidian = this.add(new BoolSetting("Obsidian", false));
   private final BoolSetting gold = this.add(new BoolSetting("Golderz", false));
   private final BoolSetting emerald = this.add(new BoolSetting("Smaragderz", false));
   private final List<class_238> found = new ArrayList<>();
   private final List<String> allBlockIds = new ArrayList<>();
   private final Map<String, class_2248> blockById = new HashMap<>();
   private int timer;

   public BlockESP() {
      super("BlockESP", "Waehlt Blocktypen einzeln aus und markiert sie im geladenen Gebiet.", Category.VISUALS);
   }

   private void ensureBlockList() {
      if (!this.allBlockIds.isEmpty()) return;
      for (class_2248 block : class_7923.field_41175) {
         if (new class_1799(block).method_7960()) continue;
         String id = class_7923.field_41175.method_10221(block).toString();
         this.blockById.put(id, block);
         this.allBlockIds.add(id);
      }
      this.allBlockIds.sort(String::compareTo);
   }

   public List<String> allBlockIds() {
      this.ensureBlockList();
      return this.allBlockIds;
   }

   public class_2248 blockFor(String id) {
      this.ensureBlockList();
      return this.blockById.get(id);
   }

   public boolean isBlockSelected(String id) {
      return this.blockIds.has(id);
   }

   public void toggleBlock(String id) {
      this.customSelection.set(true);
      if (!this.blockIds.remove(id)) this.blockIds.add(id);
   }

   public void clearBlockSelection() {
      this.customSelection.set(true);
      this.blockIds.get().clear();
   }

   private boolean selected(class_2248 b) {
      if (this.customSelection.get()) {
         String id = class_7923.field_41175.method_10221(b).toString();
         return this.blockIds.has(id);
      }
      return this.diamonds.get() && (b == class_2246.field_10442 || b == class_2246.field_29029)
         || this.ancient.get() && b == class_2246.field_22109
         || this.chests.get() && (b == class_2246.field_10034 || b == class_2246.field_10380 || b == class_2246.field_16328)
         || this.spawners.get() && b == class_2246.field_10260
         || this.obsidian.get() && b == class_2246.field_10540
         || this.gold.get() && (b == class_2246.field_10571 || b == class_2246.field_29026)
         || this.emerald.get() && (b == class_2246.field_10013 || b == class_2246.field_29220);
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null && this.timer-- <= 0) {
         this.timer = 12;
         this.found.clear();
         class_2338 p = mc.field_1724.method_24515();
         int r = this.range.getInt();
         int cap = this.maxBlocks.getInt();
         int minY = Math.max(mc.field_1687.method_31607(), p.method_10264() - r);
         int maxY = Math.min(mc.field_1687.method_31600(), p.method_10264() + r);
         int minX = p.method_10263() - r;
         int maxX = p.method_10263() + r;
         int minZ = p.method_10260() - r;
         int maxZ = p.method_10260() + r;

         for (int cx = minX >> 4; cx <= maxX >> 4 && this.found.size() < cap; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4 && this.found.size() < cap; cz++) {
               if (mc.field_1687.method_2935().method_21730(cx, cz) != null) {
                  int sx = Math.max(minX, cx << 4);
                  int ex = Math.min(maxX, (cx << 4) + 15);
                  int sz = Math.max(minZ, cz << 4);
                  int ez = Math.min(maxZ, (cz << 4) + 15);

                  for (int x = sx; x <= ex && this.found.size() < cap; x++) {
                     for (int z = sz; z <= ez && this.found.size() < cap; z++) {
                        for (int y = minY; y <= maxY && this.found.size() < cap; y++) {
                           int dx = x - p.method_10263();
                           int dy = y - p.method_10264();
                           int dz = z - p.method_10260();
                           if (dx * dx + dy * dy + dz * dz > r * r) continue;
                           class_2338 bp = new class_2338(x, y, z);
                           class_2680 state = ChunkMemory.getBlockState(x, y, z);
                           if (state != null && this.selected(state.method_26204())) {
                              this.found.add(new class_238(bp));
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (Projection.valid()) {
         int rgb = this.color.rgb();
         int argb = 0xFF000000 | rgb;
         int thickness = this.outline.getInt();
         boolean threeD = this.style.is("Block-Outline") || this.style.is("Leuchten");
         boolean smooth = !this.style.is("Voll");

         for (class_238 box : this.found) {
            if (this.style.is("2D")) {
               Esp2DRenderer.draw(ctx, box, argb, thickness, 35, true);
               continue;
            }
            if (this.style.is("Leuchten")) {
               Draw.box(ctx, box, (0x35 << 24) | rgb, thickness + 4, true, true);
            }
            Draw.box(ctx, box, argb, thickness, threeD, smooth);
         }
      }
   }

   @Override
   protected void onDisable() {
      this.found.clear();
   }

   @Override
   public void onWorldLeave() {
      this.found.clear();
      this.timer = 0;
   }
}
