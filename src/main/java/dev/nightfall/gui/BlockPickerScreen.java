package dev.nightfall.gui;

import dev.nightfall.config.Config;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.modules.BlockESP;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1799;
import net.minecraft.class_2248;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_342;
import net.minecraft.class_437;
import org.lwjgl.glfw.GLFW;

/** Searchable icon grid for selecting the exact blocks used by Block ESP. */
public final class BlockPickerScreen extends class_437 {
   private static final int CELL = 22;
   private static final int PANEL = 0xF0151320;
   private static final int ROW = 0xFF1E1B2A;
   private static final int HOVER = 0xFF2C2840;
   private static final int TEXT = 0xFFEDEBF5;
   private static final int DIM = 0xFF8D8A99;

   private final class_437 parent;
   private final BlockESP blockEsp;
   private class_342 search;
   private String query = "";
   private List<String> filtered = new ArrayList<>();
   private int page;
   private boolean previousLeft = true;
   private final boolean[] previousKeys = new boolean[GLFW.GLFW_KEY_LAST + 1];
   private class_332 graphics;
   private int mouseX;
   private int mouseY;
   private boolean leftClick;
   private String hoveredId;

   public BlockPickerScreen(class_437 parent) {
      super(class_2561.method_43470("Block ESP · Blockauswahl"));
      this.parent = parent;
      this.blockEsp = ModuleManager.get(BlockESP.class);
   }

   @Override
   protected void method_25426() {
      this.search = new class_342(this.field_22793, this.field_22789 / 2 - 110, 25, 220, 18, class_2561.method_43470("Block suchen"));
      this.search.method_1880(64);
      this.search.method_1863(value -> {
         this.query = value.toLowerCase().trim();
         this.page = 0;
         this.refilter();
      });
      this.method_37063(this.search);
      this.refilter();
   }

   private void refilter() {
      this.filtered = new ArrayList<>();
      for (String id : this.blockEsp.allBlockIds()) {
         if (this.query.isEmpty() || id.contains(this.query.replace(' ', '_'))) this.filtered.add(id);
      }
   }

   @Override
   public void method_25419() {
      Config.save();
      class_310.method_1551().method_1507(this.parent);
   }

   @Override
   public boolean method_25421() {
      return false;
   }

   private int textWidth(String value) {
      return this.field_22793.method_1727(value);
   }

   private void text(String value, int x, int y, int color) {
      this.graphics.method_27535(this.field_22793, class_2561.method_43470(value), x, y, color);
   }

   private void centered(String value, int x, int y, int color) {
      this.graphics.method_27534(this.field_22793, class_2561.method_43470(value), x, y, color);
   }

   private boolean hovered(int x1, int y1, int x2, int y2) {
      return this.mouseX >= x1 && this.mouseX < x2 && this.mouseY >= y1 && this.mouseY < y2;
   }

   private void rect(int x1, int y1, int x2, int y2, int color) {
      this.graphics.method_25294(x1 + 2, y1, x2 - 2, y1 + 1, color);
      this.graphics.method_25294(x1 + 1, y1 + 1, x2 - 1, y1 + 2, color);
      this.graphics.method_25294(x1, y1 + 2, x2, y2 - 2, color);
      this.graphics.method_25294(x1 + 1, y2 - 2, x2 - 1, y2 - 1, color);
      this.graphics.method_25294(x1 + 2, y2 - 1, x2 - 2, y2, color);
   }

   private boolean button(String label, int x, int y, int width) {
      boolean over = this.hovered(x, y, x + width, y + 17);
      this.rect(x, y, x + width, y + 17, over ? HOVER : ROW);
      this.centered(label, x + width / 2, y + 5, over ? 0xFFB8A4FF : TEXT);
      return over && this.leftClick;
   }

   @Override
   public void method_25394(class_332 graphics, int mouseX, int mouseY, float delta) {
      this.graphics = graphics;
      this.mouseX = mouseX;
      this.mouseY = mouseY;
      long window = class_310.method_1551().method_22683().method_4490();
      boolean left = GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
      this.leftClick = left && !this.previousLeft;
      this.previousLeft = left;
      boolean pageUp = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_PAGE_UP) == GLFW.GLFW_PRESS;
      boolean pageDown = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_PAGE_DOWN) == GLFW.GLFW_PRESS;
      boolean previousPage = pageUp && !this.previousKeys[GLFW.GLFW_KEY_PAGE_UP];
      boolean nextPage = pageDown && !this.previousKeys[GLFW.GLFW_KEY_PAGE_DOWN];
      this.previousKeys[GLFW.GLFW_KEY_PAGE_UP] = pageUp;
      this.previousKeys[GLFW.GLFW_KEY_PAGE_DOWN] = pageDown;

      graphics.method_25294(0, 0, this.field_22789, this.field_22790, 0xCC07060C);
      this.centered("BLOCK ESP · BLÖCKE WÄHLEN", this.field_22789 / 2, 8, 0xFFB8A4FF);

      int columns = Math.max(1, Math.min(18, (this.field_22789 - 40) / CELL));
      int rows = Math.max(1, (this.field_22790 - 150) / CELL);
      int perPage = columns * rows;
      int pages = Math.max(1, (this.filtered.size() + perPage - 1) / perPage);
      this.page = Math.max(0, Math.min(pages - 1, this.page));
      int gridX = (this.field_22789 - columns * CELL) / 2;
      int gridY = 54;
      this.rect(gridX - 8, gridY - 6, gridX + columns * CELL + 8, gridY + rows * CELL + 6, PANEL);
      this.hoveredId = null;

      for (int i = 0; i < perPage; i++) {
         int index = this.page * perPage + i;
         if (index >= this.filtered.size()) break;
         String id = this.filtered.get(index);
         int x = gridX + (i % columns) * CELL;
         int y = gridY + (i / columns) * CELL;
         boolean over = this.hovered(x, y, x + CELL, y + CELL);
         boolean selected = this.blockEsp.isBlockSelected(id);
         if (over) this.hoveredId = id;
         this.rect(x + 1, y + 1, x + CELL - 1, y + CELL - 1, selected ? 0xFF2A3A33 : over ? HOVER : ROW);
         if (selected) {
            int accent = 0xFFB8A4FF;
            graphics.method_25294(x + 1, y + 1, x + CELL - 1, y + 2, accent);
            graphics.method_25294(x + 1, y + CELL - 2, x + CELL - 1, y + CELL - 1, accent);
            graphics.method_25294(x + 1, y + 1, x + 2, y + CELL - 1, accent);
            graphics.method_25294(x + CELL - 2, y + 1, x + CELL - 1, y + CELL - 1, accent);
         }
         this.drawBlockIcon(graphics, this.blockEsp.blockFor(id), x + 3, y + 3);
         if (over && this.leftClick) {
            this.blockEsp.toggleBlock(id);
            Config.save();
         }
      }

      int bottom = this.field_22790 - 72;
      if (this.hoveredId != null) {
         this.centered(this.hoveredId, this.field_22789 / 2, bottom, TEXT);
      } else {
         this.centered(this.filtered.isEmpty() ? "Keine Blöcke gefunden" : "Block anklicken zum Auswählen", this.field_22789 / 2, bottom, DIM);
      }

      int center = this.field_22789 / 2;
      String pageLabel = "Seite " + (this.page + 1) + " / " + pages;
      int controlsY = bottom + 18;
      if (this.button("<", center - this.textWidth(pageLabel) / 2 - 30, controlsY, 22) || previousPage) this.page = Math.max(0, this.page - 1);
      this.centered(pageLabel, center, controlsY + 5, TEXT);
      if (this.button(">", center + this.textWidth(pageLabel) / 2 + 8, controlsY, 22) || nextPage) this.page = Math.min(pages - 1, this.page + 1);

      this.text("Ausgewählt: " + this.blockEsp.allBlockIds().stream().filter(this.blockEsp::isBlockSelected).count(),
         gridX, controlsY + 5, 0xFFB8A4FF);
      if (this.button("Alle abwählen", this.field_22789 - gridX - 190, controlsY, 100)) {
         this.blockEsp.clearBlockSelection();
         Config.save();
      }
      if (this.button("Fertig", this.field_22789 - gridX - 82, controlsY, 82)) this.method_25419();
      this.centered("Suche oben · Seite hoch/runter blättert · Esc = zurück", center, bottom + 43, DIM);
      super.method_25394(graphics, mouseX, mouseY, delta);
   }

   private void drawBlockIcon(class_332 graphics, class_2248 block, int x, int y) {
      if (block == null) return;
      class_1799 stack = new class_1799(block);
      if (stack.method_7960()) return;
      try {
         for (Method method : class_332.class.getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (method.getReturnType() == void.class && parameters.length == 3
               && parameters[0] == class_1799.class && parameters[1] == int.class && parameters[2] == int.class) {
               method.invoke(graphics, stack, x, y);
               return;
            }
         }
      } catch (ReflectiveOperationException ignored) {
      }
   }
}
