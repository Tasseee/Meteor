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
import java.util.HashSet;
import java.util.Set;
import net.minecraft.class_2246;
import net.minecraft.class_2248;
import net.minecraft.class_2338;
import net.minecraft.class_238;
import net.minecraft.class_243;
import net.minecraft.class_2680;
import net.minecraft.class_2818;
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
   private final BoolSetting memory = this.add(new BoolSetting("Gedaechtnis-Chunks (Momentaufnahme)", false));
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

   private Set<class_2248> selectedBlocks() {
      Set<class_2248> selected = new HashSet<>();
      if (this.customSelection.get()) {
         this.ensureBlockList();
         for (String id : this.blockIds.get()) {
            class_2248 block = this.blockById.get(id);
            if (block != null) selected.add(block);
         }
         return selected;
      }

      if (this.diamonds.get()) {
         selected.add(class_2246.field_10442);
         selected.add(class_2246.field_29029);
      }
      if (this.ancient.get()) selected.add(class_2246.field_22109);
      if (this.chests.get()) {
         selected.add(class_2246.field_10034);
         selected.add(class_2246.field_10380);
         selected.add(class_2246.field_16328);
      }
      if (this.spawners.get()) selected.add(class_2246.field_10260);
      if (this.obsidian.get()) selected.add(class_2246.field_10540);
      if (this.gold.get()) {
         selected.add(class_2246.field_10571);
         selected.add(class_2246.field_29026);
      }
      if (this.emerald.get()) {
         selected.add(class_2246.field_10013);
         selected.add(class_2246.field_29220);
      }
      return selected;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null && this.timer-- <= 0) {
         this.timer = 12;
         this.found.clear();
         class_243 scanOrigin = ChunkMemory.scanPosition();
         if (scanOrigin == null) scanOrigin = mc.field_1724.method_33571();
         class_2338 p = new class_2338(
            (int)Math.floor(scanOrigin.field_1352),
            (int)Math.floor(scanOrigin.field_1351),
            (int)Math.floor(scanOrigin.field_1350)
         );
         int r = this.range.getInt();
         int cap = this.maxBlocks.getInt();
         Set<class_2248> selectedBlocks = this.selectedBlocks();
         int minY = Math.max(mc.field_1687.method_31607(), p.method_10264() - r);
         int maxY = Math.min(mc.field_1687.method_31600() - 1, p.method_10264() + r);
         int radiusSquared = r * r;
         int minX = p.method_10263() - r;
         int maxX = p.method_10263() + r;
         int minZ = p.method_10260() - r;
         int maxZ = p.method_10260() + r;

         for (int cx = minX >> 4; cx <= maxX >> 4 && this.found.size() < cap; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4 && this.found.size() < cap; cz++) {
               class_2818 chunk = ChunkMemory.chunk(cx, cz, this.memory.get());
               if (chunk != null) {
                  long occupiedMask = ChunkMemory.occupiedMask(chunk);
                  int bottomY = mc.field_1687.method_31607();
                  if (!ChunkMemory.anyOccupied(occupiedMask, bottomY, minY, maxY)) continue;
                  int sx = Math.max(minX, cx << 4);
                  int ex = Math.min(maxX, (cx << 4) + 15);
                  int sz = Math.max(minZ, cz << 4);
                  int ez = Math.min(maxZ, (cz << 4) + 15);

                  for (int x = sx; x <= ex && this.found.size() < cap; x++) {
                     int dx = x - p.method_10263();
                     for (int z = sz; z <= ez && this.found.size() < cap; z++) {
                        int dz = z - p.method_10260();
                        int horizontalDistanceSquared = dx * dx + dz * dz;
                        if (horizontalDistanceSquared > radiusSquared) continue;
                        for (int y = minY; y <= maxY && this.found.size() < cap; y++) {
                           if (!ChunkMemory.sectionOccupied(occupiedMask, bottomY, y)) { y += 15 - ((y - bottomY) & 15); continue; }
                           int dy = y - p.method_10264();
                           if (horizontalDistanceSquared + dy * dy > radiusSquared) continue;
                           class_2680 state = ChunkMemory.getBlockState(chunk, x, y, z);
                           if (state != null && selectedBlocks.contains(state.method_26204())) {
                              this.found.add(new class_238(new class_2338(x, y, z)));
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
