package dev.nightfall.gui;

import dev.nightfall.config.Config;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.module.setting.MessageListSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.Setting;
import dev.nightfall.module.setting.StringSetting;
import dev.nightfall.modules.GUITheme;
import dev.nightfall.util.Theme;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_11908;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;
import org.lwjgl.glfw.GLFW;

public final class NightfallGuiScreen extends class_437 {
   private static final int SIDE = 154;
   private static final int HEAD = 25;
   private static final int ROW = 22;
   private static final List<String> TABS = new ArrayList<>(List.of("Combat", "Movement", "Visuals", "Player", "World", "Misc", "HUD", "Config"));
   private static final Map<String, Set<String>> EXPANDED = new HashMap<>();
   private boolean prevLeft;
   private boolean prevRight;
   private boolean draggingWindow;
   private boolean draggingTab;
   private String dragPanel;
   private String dragTab;
   private int dragX;
   private int dragY;
   private int tabStart;
   private String activeTab = "Combat";
   private String slider = "";
   private int sliderX;
   private int sliderW;
   private int scroll;
   private int configScroll;
   private int configScrollMax;
   private int configPanelX;
   private int configPanelY;
   private int configPanelW;
   private int configPanelH;
   private long tabChangedAt = System.nanoTime();
   private long guiOpenedAt = System.nanoTime();
   private int ninoSecretClicks;
   private long lastNinoSecretClick;
   private boolean ninoSecretUnlocked;
   private String searchQuery = "";
   private boolean searchFocused;
   private boolean favoritesOnly;
   private Module bindingModule;
   private NumberSetting numberSlider;
   private int numberSliderX;
   private int numberSliderW;
   private ColorSetting editingColor;
   private ColorSetting colorSlider;
   private StringSetting editingString;
   private MessageListSetting editingMessages;
   private ListSetting editingList;
   private String messageDraft = "";
   private String feedback = "";
   private long feedbackUntil;
   private final Map<String, Float> hoverValues = new HashMap<>();

   public NightfallGuiScreen() {
      super(class_2561.method_43470("Nightfall"));
      long h = class_310.method_1551().method_22683().method_4490();
      this.prevLeft = GLFW.glfwGetMouseButton(h, 0) == 1;
      this.prevRight = GLFW.glfwGetMouseButton(h, 1) == 1;
      restoreTabs();

      for (String tab : TABS) {
         this.panel(tab, 188, 105)[2] = tab.equals(this.activeTab)
            || tab.equals("Visuals") || tab.equals("Player") || tab.equals("HUD") ? 1 : 0;
      }
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25432() {
      Config.save();
   }

   public boolean method_25401(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      int mx = (int)mouseX;
      int my = (int)mouseY;
      if (this.activeTab.equals("Config")
         && inside(mx, my, this.configPanelX, this.configPanelY + 25, this.configPanelW, this.configPanelH)) {
         this.configScroll = Math.max(0, Math.min(this.configScrollMax, this.configScroll - (int)Math.round(verticalAmount * 28.0)));
      } else {
         this.scroll = Math.max(0, Math.min(5000, this.scroll - (int)Math.round(verticalAmount * 28.0)));
      }
      return true;
   }

   public boolean method_25404(class_11908 input) {
      int key = input.comp_4795();
      if (this.bindingModule != null) {
         if (key == 256) {
            this.bindingModule = null;
            return true;
         } else if (key != 259 && key != 261) {
            String error = ModuleManager.assignKey(this.bindingModule, key);
            if (error == null) {
               this.showFeedback(this.bindingModule.getName() + " · " + keyLabel(key));
               Config.save();
            } else {
               this.showFeedback(error);
            }

            this.bindingModule = null;
            return true;
         } else {
            this.bindingModule.setKey(-1);
            this.bindingModule = null;
            Config.save();
            this.showFeedback("Keybind entfernt");
            return true;
         }
      } else if (this.editingColor == null && this.editingString == null && this.editingMessages == null && this.editingList == null) {
         if (this.searchFocused) {
            if (key == 256 || key == 257 || key == 335) {
               this.searchFocused = false;
               return true;
            } else if (key == 259) {
               if (!this.searchQuery.isEmpty()) {
                  this.searchQuery = this.searchQuery.substring(0, this.searchQuery.length() - 1);
               }

               this.scroll = 0;
               return true;
            } else {
               String typed = keyText(key);
               if (typed != null && this.searchQuery.length() < 40) {
                  this.searchQuery = this.searchQuery + typed;
                  this.scroll = 0;
               }

               return true;
            }
         } else if (key != Config.guiKey && key != 256) {
            return super.method_25404(input);
         } else {
            this.method_25419();
            return true;
         }
      } else if (key == 256) {
         this.closeEditor();
         return true;
      } else if (key == 259) {
         if (!this.messageDraft.isEmpty()) {
            this.messageDraft = this.messageDraft.substring(0, this.messageDraft.length() - 1);
         }

         return true;
      } else if (key != 257 && key != 335) {
         String typed = keyText(key);
         if (typed != null && this.messageDraft.length() < 256) {
            boolean shift = GLFW.glfwGetKey(class_310.method_1551().method_22683().method_4490(), 340) == 1
               || GLFW.glfwGetKey(class_310.method_1551().method_22683().method_4490(), 344) == 1;
            if (shift && typed.length() == 1 && Character.isLetter(typed.charAt(0))) {
               typed = typed.toUpperCase(Locale.ROOT);
            }

            this.messageDraft = this.messageDraft + typed;
         }

         return true;
      } else {
         if (this.editingColor != null) {
            String hex = this.messageDraft.replace("#", "").trim();

            try {
               if (hex.length() != 6) {
                  throw new NumberFormatException();
               }

               this.editingColor.set(Integer.parseInt(hex, 16));
               Config.save();
               this.showFeedback("Farbe gespeichert");
               this.closeEditor();
            } catch (NumberFormatException var5) {
               this.showFeedback("Farbe als sechs Hex-Zeichen eingeben");
            }
         } else if (this.editingString != null) {
            this.editingString.set(this.messageDraft);
            Config.save();
            this.showFeedback("Einstellung gespeichert");
            this.closeEditor();
         } else if (!this.messageDraft.isBlank()) {
            boolean added = this.editingMessages != null
               ? this.editingMessages.addMessage(this.messageDraft.trim())
               : this.editingList.add(this.messageDraft.trim());
            if (added) {
               Config.save();
               this.showFeedback("Eintrag hinzugefügt");
               this.messageDraft = "";
            } else {
               this.showFeedback("Leer oder bereits vorhanden");
            }
         }

         return true;
      }
   }

   private static String keyText(int key) {
      if (key >= 65 && key <= 90) {
         return String.valueOf((char)(97 + key - 65));
      } else if (key >= 48 && key <= 57) {
         return String.valueOf((char)(48 + key - 48));
      } else if (key == 32) {
         return " ";
      } else if (key == 45) {
         return "-";
      } else {
         return key == 46 ? "." : null;
      }
   }

   private float hoverValue(String id, boolean over) {
      float value = this.hoverValues.getOrDefault(id, 0.0F);
      value += ((over ? 1.0F : 0.0F) - value) * 0.28F;
      this.hoverValues.put(id, value);
      return value;
   }

   private static String keyLabel(int key) {
      if (key < 0) {
         return "UNBOUND";
      } else if (key >= 65 && key <= 90) {
         return String.valueOf((char)(65 + key - 65));
      } else if (key >= 48 && key <= 57) {
         return String.valueOf((char)(48 + key - 48));
      } else {
         return switch (key) {
            case 32 -> "SPACE";
            case 340 -> "L-SHIFT";
            case 341 -> "L-CTRL";
            case 342 -> "L-ALT";
            case 344 -> "R-SHIFT";
            case 345 -> "R-CTRL";
            case 346 -> "R-ALT";
            default -> "KEY " + key;
         };
      }
   }

   private static void restoreTabs() {
      if (Config.categoryOrder != null && !Config.categoryOrder.isBlank()) {
         List<String> saved = new ArrayList<>(Arrays.asList(Config.categoryOrder.split(",")));
         if (saved.size() == 8 && new HashSet<>(saved).equals(new HashSet<>(TABS))) {
            TABS.clear();
            TABS.addAll(saved);
         }
      }
   }

   private static void saveTabs() {
      Config.categoryOrder = String.join(",", TABS);
      Config.save();
   }

   private List<Module> modules(String tab) {
      List<Module> out = new ArrayList<>();

      for (Module m : ModuleManager.all()) {
         String c = switch (m.getCategory()) {
            case COMBAT -> "Combat";
            case MOVEMENT -> "Movement";
            case VISUALS -> "Visuals";
            case PLAYER -> "Player";
            case WORLD, BASEFINDER -> "World";
            case MISC -> "Misc";
            case HUD -> "HUD";
         };
         if (tab.equals(c)) {
            out.add(m);
         }
      }

      return out;
   }

   private int[] panel(String id, int defaultX, int defaultY) {
      return Config.panels.computeIfAbsent("window:" + id, k -> new int[]{defaultX, defaultY, 1});
   }

   private boolean opened(String id) {
      return this.panel(id, 0, 0)[2] != 0;
   }

   private void setOpen(String id, boolean v) {
      this.panel(id, 0, 0)[2] = v ? 1 : 0;
      Config.save();
   }

   private void selectTab(String tab) {
      if (!tab.equals(this.activeTab)) {
         this.activeTab = tab;

         for (String name : TABS) {
            this.panel(name, 0, 0)[2] = name.equals(tab) ? 1 : 0;
         }

         this.tabChangedAt = System.nanoTime();
         this.scroll = 0;
         Config.save();
      }
   }

   public void method_25394(class_332 ctx, int mx, int my, float delta) {
      class_310 mc = class_310.method_1551();
      long h = mc.method_22683().method_4490();
      boolean left = GLFW.glfwGetMouseButton(h, 0) == 1;
      boolean right = GLFW.glfwGetMouseButton(h, 1) == 1;
      boolean click = left && !this.prevLeft;
      boolean release = !left && this.prevLeft;
      boolean rightClick = right && !this.prevRight;
      this.prevLeft = left;
      this.prevRight = right;
      if (this.field_22789 >= 0) {
         float duration = Math.max(50, Config.guiAnimationDuration) / Math.max(0.1F, Config.guiAnimationSpeed);
         float progress = Config.guiAnimations
            ? Math.max(0.0F, Math.min(1.0F, (float)(System.nanoTime() - this.guiOpenedAt) / 1000000.0F / duration))
            : 1.0F;
         if (Config.guiAnimations) {
            progress = switch (Config.guiEasing) {
               case "Linear" -> progress;
               case "Sine" -> (float)(1.0 - Math.cos(progress * Math.PI * 0.5));
               default -> 1.0F - (float)Math.pow(1.0F - progress, 3.0);
            };
         }
          if (click && progress >= 1.0F && inside(mx, my, 12, 10, 164, 78)) {
             long now = System.currentTimeMillis();
             if (now - this.lastNinoSecretClick > 1200L) {
                this.ninoSecretClicks = 0;
             }

             this.lastNinoSecretClick = now;
             if (++this.ninoSecretClicks >= 5) {
                this.ninoSecretUnlocked = true;
                this.ninoSecretClicks = 0;
             }
          }

         float offsetX = 0.0F;
         float offsetY = 0.0F;
         float scale = 1.0F;
         if (Config.guiAnimations && Config.guiAnimationType.equals("Slide")) {
            float distance = 28.0F * (1.0F - progress);
            switch (Config.guiAnimationDirection) {
               case "Right" -> offsetX = distance;
               case "Up" -> offsetY = -distance;
               case "Down" -> offsetY = distance;
               default -> offsetX = -distance;
            }
         } else if (Config.guiAnimations && Config.guiAnimationType.equals("Scale")) {
            scale = 0.92F + 0.08F * progress;
         } else if (Config.guiAnimations && Config.guiAnimationType.equals("Zoom")) {
            scale = 0.78F + 0.22F * progress;
         } else if (Config.guiAnimations && Config.guiAnimationType.equals("Fade")) {
            scale = 0.985F + 0.015F * progress;
         }

         var matrices = ctx.method_51448();
         matrices.pushMatrix();
         if (scale != 1.0F) {
            float cx = this.field_22789 / 2.0F;
            float cy = this.field_22790 / 2.0F;
            matrices.translate(cx + offsetX, cy + offsetY);
            matrices.scale(scale, scale);
            matrices.translate(-cx, -cy);
         } else if (offsetX != 0.0F || offsetY != 0.0F) {
            matrices.translate(offsetX, offsetY);
         }
         this.drawPremiumLayout(ctx, mx, my, click && progress >= 1.0F, release, rightClick && progress >= 1.0F, left && progress >= 1.0F, mc);
         matrices.popMatrix();

         if (Config.guiAnimations && Config.guiAnimationType.equals("Fade") && progress < 1.0F) {
            int alpha = Math.max(0, Math.min(255, Math.round((1.0F - progress) * 255.0F)));
            ctx.method_25294(0, 0, this.field_22789, this.field_22790, alpha << 24);
         }
         return;
      }
      ctx.method_25294(0, 0, this.field_22789, this.field_22790, -1459091184);
      int blurShade = Math.max(0, Math.min(150, Config.blurStrength));
      ctx.method_25294(0, 0, this.field_22789, this.field_22790, blurShade << 24 | 526095);
      ctx.method_25294(0, 0, this.field_22789, 3, Theme.ACCENT);
      this.panel(ctx, 12, 10, 164, 78);
      ctx.method_51433(mc.field_1772, "NIGHTFALL", 29, 24, Theme.TEXT, true);
      ctx.method_51433(mc.field_1772, "CLIENT  /  CONTROL CENTER", 29, 43, Theme.TEXT_DIM, false);
      ctx.method_51433(mc.field_1772, "FPS " + mc.method_47599(), this.field_22789 - 190, 24, Theme.ACCENT, true);
      ctx.method_51433(mc.field_1772, "1.21.11", this.field_22789 - 100, 24, Theme.TEXT, false);
      int sy = 100;

      for (int i = 0; i < TABS.size(); i++) {
         String tab = TABS.get(i);
         int y = sy + i * 38;
         boolean over = inside(mx, my, 14, y, 154, 32);
         if (tab.equals(this.activeTab)) {
            ctx.method_25294(14, y, 168, y + 32, -718531246);
            ctx.method_25294(14, y, 17, y + 32, Theme.ACCENT);
         } else {
            int hoverAlpha = Math.round(75.0F * this.hoverValue("tab:" + tab, over));
            if (hoverAlpha > 0) {
               ctx.method_25294(14, y, 168, y + 32, Theme.withAlpha(Theme.ACCENT, hoverAlpha));
            }
         }

         ctx.method_51433(mc.field_1772, icon(tab) + "  " + tab, 27, y + 11, tab.equals(this.activeTab) ? Theme.TEXT : Theme.TEXT_DIM, true);
         if (click && over) {
            if (this.draggingTab && this.dragTab != null) {
               int from = TABS.indexOf(this.dragTab);
               if (from >= 0) {
                  TABS.remove(from);
                  TABS.add(Math.max(0, Math.min(TABS.size(), i)), this.dragTab);
                  saveTabs();
               }
            } else {
               this.selectTab(tab);
            }

            click = false;
         }

         if (left && over && this.dragTab == null && !this.draggingWindow) {
            this.dragTab = tab;
            this.tabStart = i;
         }
      }

      if (left && this.dragTab != null) {
         int overIndex = (my - sy) / 38;
         if (my >= sy && overIndex >= 0 && overIndex < TABS.size()) {
            String overTab = TABS.get(overIndex);
            if (!overTab.equals(this.dragTab)) {
               int from = TABS.indexOf(this.dragTab);
               if (from >= 0) {
                  TABS.remove(from);
                  TABS.add(Math.max(0, Math.min(TABS.size(), overIndex)), this.dragTab);
                  saveTabs();
               }
            }
         }
      }

      if (release) {
         this.dragTab = null;
      }

      if (!this.opened(this.activeTab)) {
         for (String name : TABS) {
            this.panel(name, 0, 0)[2] = name.equals(this.activeTab) ? 1 : 0;
         }
      }

      int defaultX = Math.max(174, (this.field_22789 + 154 - 270) / 2);
      int defaultY = Math.max(82, (this.field_22790 - Math.min(520, this.field_22790 - 110)) / 2);
      int[] p = this.panel(this.activeTab, defaultX, defaultY);
      int minX = Math.min(172, Math.max(8, this.field_22789 - 220));
      int maxX = Math.max(minX, this.field_22789 - 220);
      int maxY = Math.max(72, this.field_22790 - 25 - 36);
      int x = Math.max(minX, Math.min(maxX, p[0]));
      int yx = Math.max(72, Math.min(maxY, p[1]));
      int w = Math.max(210, Math.min(300, this.field_22789 - x - 10));
      float anim = 1.0F;
      if (Config.guiAnimations) {
         float duration = Math.max(50, Config.guiAnimationDuration) / Math.max(0.1F, Config.guiAnimationSpeed);
         float t = Math.max(0.0F, Math.min(1.0F, (float)(System.nanoTime() - this.tabChangedAt) / 1000000.0F / duration));
         String ex = Config.guiEasing;

         anim = switch (ex) {
            case "Linear" -> t;
            case "Sine" -> (float)(1.0 - Math.cos(t * Math.PI * 0.5));
            default -> 1.0F - (float)Math.pow(1.0F - t, 3.0);
         };
      }

      int offsetX = 0;
      int offsetY = 0;
      if (Config.guiAnimations && Config.guiAnimationType.equals("Slide")) {
         int distance = Math.round((1.0F - anim) * 24.0F);
         String var49 = Config.guiAnimationDirection;
         switch (var49) {
            case "Right":
               offsetX = distance;
               break;
            case "Up":
               offsetY = -distance;
               break;
            case "Down":
               offsetY = distance;
               break;
            default:
               offsetX = -distance;
         }
      }

      this.drawAnimatedWindow(ctx, this.activeTab, x + offsetX, yx + offsetY, w, mx, my, click, rightClick, mc, true);
      if (this.numberSlider != null) {
         float amount = Math.max(0.0F, Math.min(1.0F, (float)(mx - this.numberSliderX) / Math.max(1, this.numberSliderW)));
         this.numberSlider.set(this.numberSlider.min + amount * (this.numberSlider.max - this.numberSlider.min));
         if (release) {
            Config.save();
            this.numberSlider = null;
         }
      }

      if (this.editingColor != null || this.editingString != null || this.editingMessages != null || this.editingList != null) {
         this.drawTextEditor(ctx);
      }

      if (System.currentTimeMillis() < this.feedbackUntil) {
         this.drawFeedback(ctx);
      }

      if (this.draggingWindow && this.activeTab.equals(this.dragPanel)) {
         if (left) {
            p[0] = Math.max(minX, Math.min(maxX, mx - this.dragX));
            p[1] = Math.max(72, Math.min(maxY, my - this.dragY));
         } else {
            this.draggingWindow = false;
            this.dragPanel = null;
            Config.save();
         }
      }

      if (!left && this.slider.isEmpty()) {
         this.draggingWindow = false;
         this.dragPanel = null;
      }

      if (!this.slider.isEmpty() && left) {
         float t = Math.max(0.0F, Math.min(1.0F, (float)(mx - this.sliderX) / Math.max(1, this.sliderW)));
         if (this.slider.equals("hue")) {
            Config.accentHue = t * 360.0F;
            this.applyHue();
         } else if (this.slider.equals("blur")) {
            Config.blurStrength = Math.round(t * 150.0F);
         } else if (this.slider.equals("espColor")) {
            this.updateEspColorSlider(mx);
         }
      }

      if (!left) {
         if (this.slider.equals("espColor")) {
            Config.save();
            this.colorSlider = null;
         }
         this.slider = "";
      }

      if (this.ninoSecretUnlocked) {
         int ex = Math.max(178, this.field_22789 - 224);
         int ey = Math.max(12, this.field_22790 - 102);
         float pulse = (float)(0.5 + 0.5 * Math.sin(System.currentTimeMillis() * 0.006));
         int glow = Theme.withAlpha(10494192, Math.round(150.0F + pulse * 90.0F));
         ctx.method_25294(ex - 3, ey - 3, ex + 210, ey + 76, glow);
         ctx.method_25294(ex, ey, ex + 204, ey + 70, -267384037);
         ctx.method_25294(ex + 6, ey + 6, ex + 198, ey + 8, Theme.withAlpha(16741862, Math.round(150.0F + pulse * 100.0F)));
         ctx.method_51433(mc.field_1772, "N I N O   S E C R E T", ex + 12, ey + 16, -18200, true);
         ctx.method_51433(mc.field_1772, "NINO SUPREMACY", ex + 12, ey + 32, -1, true);
         ctx.method_51433(mc.field_1772, "Miku fans may leave now.", ex + 12, ey + 48, -2504718, false);
         ctx.method_51433(mc.field_1772, "*  *  *", ex + 164, ey + 32, -10645, true);
      }

      ctx.method_51433(
         mc.field_1772,
         "Nightfall  •  Ziehen: Fenster | Tabs per Drag & Drop sortieren | Rechtsklick: Einstellungen",
         182,
         this.field_22790 - 22,
         Theme.TEXT_DIM,
         false
      );
   }

   private boolean drawPremiumLayout(class_332 ctx, int mx, int my, boolean click, boolean release, boolean rightClick, boolean left, class_310 mc) {
      if (this.field_22789 < 720 || this.field_22790 < 400) {
         this.drawCompactPremiumLayout(ctx, mx, my, click, release, rightClick, left, mc);
         return true;
      }

      float scale = Config.guiScalePercent / 100.0F;
      int edge = Config.guiEdgeMargin;
      int sw = this.field_22789;
      int sh = this.field_22790;
      if (click && inside(mx, my, 10 + edge, 9 + edge, Math.max(126, Math.min(150, sw / 6)) - 8, 54)) {
         this.activeTab = "Config";
         this.tabChangedAt = System.nanoTime();
         this.scroll = 0;
         click = false;
      }
      int side = Math.max(126, Math.min(150, sw / 6));
      int x1 = side + 18 + edge;
      int gap = Math.round(12 * scale);
      int panelW = Math.min(Math.round(310 * scale), (sw - x1 - 30 - gap - edge) / 2);
      int x2 = x1 + panelW + gap;
      int topY = 72 + edge;
      int bottomY = Math.max(220 + edge, sh / 2 + 8);
      int headerH = 54;

      if (click && inside(mx, my, 8 + edge, 8 + edge, sw - 16 - edge * 2, headerH)) {
         this.activeTab = "Config";
         this.tabChangedAt = System.nanoTime();
         this.scroll = 0;
         click = false;
      }
      int transparency = Math.max(0, Math.min(100, Config.guiTransparency));
      int overlayAlpha = Math.round(169.0F * (100 - transparency) / 100.0F);
      ctx.method_25294(0, 0, sw, sh, overlayAlpha << 24 | 0x080910);
      int shade = transparency >= 100 ? 0 : Math.max(0, Math.min(150, Config.blurStrength));
      if (shade > 0) {
         ctx.method_25294(0, 0, sw, sh, shade << 24 | 526095);
      }
      this.panel(ctx, 10 + edge, 9 + edge, side - 8, headerH);
      ctx.method_51433(mc.field_1772, "◒", 24 + edge, 27 + edge, Theme.ACCENT, true);
      ctx.method_51433(mc.field_1772, "NIGHTFALL", 48 + edge, 23 + edge, Theme.TEXT, true);
      ctx.method_51433(mc.field_1772, "PREMIUM CLIENT", 49 + edge, 39 + edge, Theme.TEXT_DIM, false);

      int statusW = 190;
      int statusX = sw - statusW - 12 - edge;
      this.panel(ctx, statusX, 14 + edge, statusW, 27);
      ctx.method_51433(mc.field_1772, "FPS", statusX + 9, 23 + edge, Theme.ACCENT, true);
      ctx.method_51433(mc.field_1772, String.valueOf(mc.method_47599()), statusX + 34, 23 + edge, Theme.TEXT, false);
      ctx.method_51433(mc.field_1772, "1.21.11", statusX + 104, 23 + edge, Theme.TEXT_DIM, false);

      String[] nav = {"Combat", "Movement", "Visuals", "Player", "World", "Misc", "HUD", "Config"};
      for (int i = 0; i < nav.length; i++) {
         String tab = nav[i];
         int ny = 78 + edge + i * Math.round(35 * scale);
         boolean active = tab.equals(this.activeTab);
         boolean over = inside(mx, my, 12 + edge, ny, side - 12, Math.round(30 * scale));
         if (active) {
            roundedRect(ctx, 12 + edge, ny, side - 12, Math.round(30 * scale), Config.cornerRadius, Theme.withAlpha(Theme.ACCENT, 115));
            roundedRect(ctx, 12 + edge, ny, 3, Math.round(30 * scale), 2, Theme.ACCENT);
         } else if (over) {
            roundedRect(ctx, 12 + edge, ny, side - 12, Math.round(30 * scale), Config.cornerRadius, Theme.withAlpha(Theme.ACCENT, 45));
         }
         ctx.method_51433(mc.field_1772, icon(tab), 23 + edge, ny + 10, Theme.ACCENT, true);
         ctx.method_51433(mc.field_1772, tab, 49 + edge, ny + 10, active ? Theme.TEXT : Theme.TEXT_DIM, true);
         if (click && over) {
            this.activeTab = tab;
            this.tabChangedAt = System.nanoTime();
            click = false;
         }
      }

      int profileY = sh - 57 - edge;
      this.panel(ctx, 12 + edge, profileY, side - 20, 45);
      ctx.method_51433(mc.field_1772, "FW", 22 + edge, profileY + 12, Theme.ACCENT, true);
      ctx.method_51433(mc.field_1772, "Finni0512", 49 + edge, profileY + 10, Theme.TEXT, true);
      ctx.method_51433(mc.field_1772, "Premium", 49 + edge, profileY + 25, Theme.ACCENT, false);

      if (this.activeTab.equals("Config")) {
         this.drawAnimatedWindow(ctx, "Config", x1, topY, Math.max(260, sw - x1 - 18), mx, my, click, rightClick, mc, true);
      } else {
         String[] visibleTabs = {"Combat", "Visuals", "Player", "HUD"};
         int[] xs = {x1, x2, x1, x2};
         int[] ys = {topY, topY, bottomY, bottomY};
         for (int i = 0; i < visibleTabs.length; i++) {
            String tab = visibleTabs[i];
            if (this.opened(tab)) {
               this.drawAnimatedWindow(ctx, tab, xs[i], ys[i], panelW, mx, my, click, rightClick, mc, tab.equals(this.activeTab));
            }
         }
      }


      if (this.numberSlider != null) {
         float amount = Math.max(0.0F, Math.min(1.0F, (float)(mx - this.numberSliderX) / Math.max(1, this.numberSliderW)));
         this.numberSlider.set(this.numberSlider.min + amount * (this.numberSlider.max - this.numberSlider.min));
         if (release) {
            Config.save();
            this.numberSlider = null;
         }
      }

      if (this.editingColor != null || this.editingString != null || this.editingMessages != null || this.editingList != null) {
         this.drawTextEditor(ctx);
      }
      if (System.currentTimeMillis() < this.feedbackUntil) {
         this.drawFeedback(ctx);
      }
      this.updateConfigSlider(mx, left, release);

      if (this.draggingWindow && this.dragPanel != null) {
         int[] dragged = this.panel(this.dragPanel, x1, topY);
         if (left) {
            dragged[0] = Math.max(8, Math.min(sw - panelW - 8, mx - this.dragX));
            dragged[1] = Math.max(55, Math.min(sh - 50, my - this.dragY));
         } else {
            this.draggingWindow = false;
            this.dragPanel = null;
            Config.save();
         }
      }
      return true;
   }

   private void drawCompactPremiumLayout(class_332 ctx, int mx, int my, boolean click, boolean release, boolean rightClick, boolean left, class_310 mc) {
      float scale = Config.guiScalePercent / 100.0F;
      int edge = Config.guiEdgeMargin;
      int sw = this.field_22789;
      int sh = this.field_22790;
      int side = Math.max(82, Math.min(126, Math.round(sw / 4.0F * scale)));
      int headerH = 46;
      int transparency = Math.max(0, Math.min(100, Config.guiTransparency));
      int overlayAlpha = Math.round(169.0F * (100 - transparency) / 100.0F);
      ctx.method_25294(0, 0, sw, sh, overlayAlpha << 24 | 0x080910);
      int shade = transparency >= 100 ? 0 : Math.max(0, Math.min(150, Config.blurStrength));
      if (shade > 0) {
         ctx.method_25294(0, 0, sw, sh, shade << 24 | 526095);
      }
      this.panel(ctx, 8 + edge, 8 + edge, sw - 16 - edge * 2, headerH);
      ctx.method_51433(mc.field_1772, "◒", 17 + edge, 19 + edge, Theme.ACCENT, true);
      ctx.method_51433(mc.field_1772, "NIGHTFALL", 37 + edge, 17 + edge, Theme.TEXT, true);
      ctx.method_51433(mc.field_1772, "PREMIUM CLIENT", 38 + edge, 31 + edge, Theme.TEXT_DIM, false);
      ctx.method_51433(mc.field_1772, "FPS " + mc.method_47599(), sw - 74 - edge, 23 + edge, Theme.ACCENT, true);

      String[] nav = {"Combat", "Movement", "Visuals", "Player", "World", "Misc", "HUD", "Config"};
      int navY = headerH + 18 + edge;
      int navStep = Math.max(21, Math.min(27, (sh - navY - 40) / nav.length));
      for (int i = 0; i < nav.length; i++) {
         String tab = nav[i];
         int ny = navY + i * navStep;
         boolean active = tab.equals(this.activeTab);
         boolean over = inside(mx, my, 7, ny, side - 8, navStep - 2);
         if (active) {
            ctx.method_25294(7, ny, side, ny + navStep - 2, Theme.withAlpha(Theme.ACCENT, 115));
            ctx.method_25294(7, ny, 10, ny + navStep - 2, Theme.ACCENT);
         } else if (over) {
            ctx.method_25294(7, ny, side, ny + navStep - 2, Theme.withAlpha(Theme.ACCENT, 45));
         }
         String label = tab.length() > 9 ? tab.substring(0, 8) : tab;
         ctx.method_51433(mc.field_1772, label, 15, ny + Math.max(4, (navStep - 8) / 2), active ? Theme.TEXT : Theme.TEXT_DIM, true);
         if (click && over) {
            this.activeTab = tab;
            this.tabChangedAt = System.nanoTime();
            this.scroll = 0;
            click = false;
         }
      }

      int profileY = Math.max(navY + nav.length * navStep + 3, sh - 31);
      if (profileY + 25 < sh) {
         ctx.method_51433(mc.field_1772, "Finni0512", 10, profileY, Theme.TEXT, true);
         ctx.method_51433(mc.field_1772, "Premium", 10, profileY + 12, Theme.ACCENT, false);
      }

      int panelX = side + 8 + edge;
      int panelY = headerH + 10 + edge;
      int panelW = Math.max(150, sw - panelX - 8 - edge);
      this.drawAnimatedWindow(ctx, this.activeTab, panelX, panelY, panelW, mx, my, click, rightClick, mc, true);

      if (this.numberSlider != null) {
         float amount = Math.max(0.0F, Math.min(1.0F, (float)(mx - this.numberSliderX) / Math.max(1, this.numberSliderW)));
         this.numberSlider.set(this.numberSlider.min + amount * (this.numberSlider.max - this.numberSlider.min));
      }
      if (this.editingColor != null || this.editingString != null || this.editingMessages != null || this.editingList != null) {
         this.drawTextEditor(ctx);
      }
      if (System.currentTimeMillis() < this.feedbackUntil) {
         this.drawFeedback(ctx);
      }
      this.updateConfigSlider(mx, left, release);
   }

   private void drawAnimatedWindow(class_332 ctx, String tab, int x, int y, int w, int mx, int my, boolean click, boolean rightClick, class_310 mc, boolean animate) {
      if (!animate || !Config.guiAnimations) {
         this.drawWindow(ctx, tab, x, y, w, mx, my, click, rightClick, mc);
         return;
      }
      float duration = Math.max(50, Config.guiAnimationDuration) / Math.max(0.1F, Config.guiAnimationSpeed);
      float progress = Math.max(0.0F, Math.min(1.0F, (float)(System.nanoTime() - this.tabChangedAt) / 1000000.0F / duration));
      progress = switch (Config.guiEasing) {
         case "Linear" -> progress;
         case "Sine" -> (float)(1.0 - Math.cos(progress * Math.PI * 0.5));
         default -> 1.0F - (float)Math.pow(1.0F - progress, 3.0);
      };
      float offsetX = 0.0F;
      float offsetY = 0.0F;
      float scale = 1.0F;
      if (Config.guiAnimationType.equals("Slide")) {
         float distance = 24.0F * (1.0F - progress);
         switch (Config.guiAnimationDirection) {
            case "Right" -> offsetX = distance;
            case "Up" -> offsetY = -distance;
            case "Down" -> offsetY = distance;
            default -> offsetX = -distance;
         }
      } else if (Config.guiAnimationType.equals("Scale")) {
         scale = 0.92F + 0.08F * progress;
      } else if (Config.guiAnimationType.equals("Zoom")) {
         scale = 0.78F + 0.22F * progress;
      } else if (Config.guiAnimationType.equals("Fade")) {
         scale = 0.985F + 0.015F * progress;
      }
      var matrices = ctx.method_51448();
      matrices.pushMatrix();
      if (scale != 1.0F) {
         float cx = x + w / 2.0F;
         float cy = y + 90.0F;
         matrices.translate(cx + offsetX, cy + offsetY);
         matrices.scale(scale, scale);
         matrices.translate(-cx, -cy);
      } else if (offsetX != 0.0F || offsetY != 0.0F) {
         matrices.translate(offsetX, offsetY);
      }
      this.drawWindow(ctx, tab, x, y, w, mx, my, click && progress >= 1.0F, rightClick && progress >= 1.0F, mc);
      matrices.popMatrix();
   }

   private void drawWindow(class_332 ctx, String tab, int x, int y, int w, int mx, int my, boolean click, boolean rightClick, class_310 mc) {
      List<Module> mods = this.modules(tab);
      if (!this.searchQuery.isBlank()) {
         String q = this.searchQuery.toLowerCase(Locale.ROOT);
         mods.removeIf(m -> !m.getName().toLowerCase(Locale.ROOT).contains(q) && !m.getDescription().toLowerCase(Locale.ROOT).contains(q));
      }

      if (this.favoritesOnly) {
         mods.removeIf(m -> !m.isFavorite());
      }

      Set<String> expanded = EXPANDED.computeIfAbsent(tab, k -> new HashSet<>());
      int visible = 0;

      for (Module m : mods) {
         visible += 22 + (expanded.contains(m.getName()) ? m.getSettings().size() * 22 : 0);
      }

      int availableBody = Math.max(64, this.field_22790 - y - 25 - 12);
      if (tab.equals("Config")) availableBody = Math.max(availableBody, this.field_22790 - y - 36);
      if (!tab.equals("Config") && this.field_22789 >= 720 && this.field_22790 >= 400) {
         availableBody = Math.min(availableBody, Math.max(82, this.field_22790 / 2 - 48));
      }
      int bodyH = this.pMinimized(tab)
         ? 0
         : Math.max(0, Math.min(availableBody, tab.equals("Config") ? 340 : Math.max(82, visible + 34)));
      this.panel(ctx, x, y, w, 25 + bodyH);
      roundedRect(ctx, x, y, w, 25, Config.cornerRadius, Theme.withAlpha(-300937959, Math.max(0, (100 - Config.guiTransparency) * 255 / 100)));
      ctx.method_51433(mc.field_1772, icon(tab) + "  " + tab, x + 10, y + 9, Theme.TEXT, true);
      if (click && inside(mx, my, x, y, w, 25)) {
         this.draggingWindow = true;
         this.dragPanel = tab;
         this.dragX = mx - x;
         this.dragY = my - y;
      }

      if (bodyH != 0) {
         if (tab.equals("Config")) {
            this.configPanelX = x;
            this.configPanelY = y;
            this.configPanelW = w;
            this.configPanelH = bodyH;
            this.configScrollMax = Math.max(0, 440 - bodyH);
            this.configScroll = Math.max(0, Math.min(this.configScroll, this.configScrollMax));
            int clipTop = y + 25;
            int clipBottom = clipTop + bodyH;
            ctx.method_44379(x + 4, clipTop, x + w - 4, clipBottom);
            this.drawConfig(ctx, x, y - this.configScroll, w, mx, my,
               click && inside(mx, my, x + 4, clipTop, w - 8, bodyH));
            ctx.method_44380();
            if (this.configScrollMax > 0) {
               int trackX = x + w - 5;
               int thumbH = Math.max(24, bodyH * bodyH / 440);
               int thumbY = clipTop + (bodyH - thumbH) * this.configScroll / this.configScrollMax;
               ctx.method_25294(trackX, clipTop + 3, trackX + 2, clipBottom - 3, 0x55333333);
               roundedRect(ctx, trackX - 1, thumbY + 3, 4, Math.max(12, thumbH - 6), 2, Theme.withAlpha(Theme.ACCENT, 210));
            }
         } else {
            int controlY = y + 25 + 5;
            boolean searchOver = inside(mx, my, x + 8, controlY, w - 82, 19);
            int searchAlpha = Math.round(85.0F * this.hoverValue("search", searchOver || this.searchFocused));
            ctx.method_25294(x + 8, controlY, x + w - 74, controlY + 19, -1525016798);
            if (searchAlpha > 0) {
               ctx.method_25294(x + 8, controlY, x + w - 74, controlY + 19, Theme.withAlpha(Theme.ACCENT, searchAlpha));
            }

            ctx.method_51433(
               mc.field_1772,
               (this.searchQuery.isEmpty() ? "Search modules..." : this.searchQuery) + (this.searchFocused ? "_" : ""),
               x + 14,
               controlY + 6,
               this.searchQuery.isEmpty() ? Theme.TEXT_DIM : Theme.TEXT,
               false
            );
            boolean favOver = inside(mx, my, x + w - 68, controlY, 60, 19);
            int favAlpha = Math.round(85.0F * this.hoverValue("favorites", favOver || this.favoritesOnly));
            ctx.method_25294(x + w - 68, controlY, x + w - 8, controlY + 19, -1525016798);
            if (favAlpha > 0) {
               ctx.method_25294(x + w - 68, controlY, x + w - 8, controlY + 19, Theme.withAlpha(Theme.ACCENT, favAlpha));
            }

            ctx.method_51433(
               mc.field_1772, this.favoritesOnly ? "FAV ON" : "FAV", x + w - 61, controlY + 6, this.favoritesOnly ? Theme.ACCENT : Theme.TEXT_DIM, true
            );
            if (click && searchOver) {
               this.searchFocused = true;
            } else if (click && favOver) {
               this.favoritesOnly = !this.favoritesOnly;
               this.searchFocused = false;
            } else if (click && !searchOver) {
               this.searchFocused = false;
            }

            int contentHeight = 0;

            for (Module mod : mods) {
               contentHeight += 22 + (expanded.contains(mod.getName()) ? mod.getSettings().size() * 22 : 0);
            }

            int maxScroll = Math.max(0, contentHeight - Math.max(0, bodyH - 29));
            this.scroll = Math.max(0, Math.min(this.scroll, maxScroll));
            int rowY = y + 25 + 29 - this.scroll;
            ctx.method_44379(x + 3, y + 25 + 27, x + w - 3, y + 25 + bodyH);

            for (Module mod : mods) {
               if (rowY > y + 25 + bodyH - 22) {
                  rowY += 22;
               } else {
                  boolean over = inside(mx, my, x + 4, rowY, w - 8, 22);
                  int rowHover = Math.round(75.0F * this.hoverValue("module:" + tab + ":" + mod.getName(), over));
                  if (rowHover > 0) {
                     roundedRect(ctx, x + 4, rowY, w - 8, 21, Math.min(10, Config.cornerRadius), Theme.withAlpha(Theme.ACCENT, rowHover));
                  }

                  ctx.method_51433(mc.field_1772, mod.getName(), x + 10, rowY + 7, mod.isEnabled() ? Theme.TEXT : Theme.TEXT_DIM, false);
                  int starX = x + w - 112;
                  int keyX = x + w - 88;
                  int tx = x + w - 34;
                  int ty = rowY + 6;
                  ctx.method_51433(mc.field_1772, mod.isFavorite() ? "*" : "+", starX, rowY + 7, mod.isFavorite() ? -10645 : Theme.TEXT_DIM, true);
                  String keyText = this.bindingModule == mod ? "PRESS..." : keyLabel(mod.getKey());
                  if (keyText.length() > 8) {
                     keyText = keyText.substring(0, 8);
                  }

                  ctx.method_25294(keyX, rowY + 4, tx - 5, rowY + 18, this.bindingModule == mod ? Theme.withAlpha(Theme.ACCENT, 170) : 1715222344);
                  ctx.method_51433(mc.field_1772, keyText, keyX + 3, rowY + 7, this.bindingModule == mod ? Theme.TEXT : Theme.TEXT_DIM, false);
                  ctx.method_25294(tx, ty, tx + 24, ty + 12, mod.isEnabled() ? Theme.ACCENT : -12238002);
                  ctx.method_25294(mod.isEnabled() ? tx + 14 : tx + 2, ty + 2, mod.isEnabled() ? tx + 22 : tx + 10, ty + 10, -658182);
                  if (click && over) {
                     if (mx >= tx - 3) {
                        mod.toggle();
                        Config.save();
                     } else if (mx >= keyX - 2 && mx < tx - 4) {
                        this.bindingModule = mod;
                        this.searchFocused = false;
                     } else if (mx >= starX - 3 && mx < keyX - 3) {
                        mod.setFavorite(!mod.isFavorite());
                        Config.save();
                     } else if (!mod.getSettings().isEmpty() && !expanded.add(mod.getName())) {
                        expanded.remove(mod.getName());
                     }
                  }

                  if (rightClick && over && !mod.getSettings().isEmpty() && !expanded.add(mod.getName())) {
                     expanded.remove(mod.getName());
                  }

                  rowY += 22;
                  if (expanded.contains(mod.getName())) {
                     for (Setting<?> setting : mod.getSettings()) {
                        if (rowY > y + 25 + bodyH - 22) {
                           rowY += 22;
                        } else {
                           this.drawSetting(ctx, setting, x + 8, rowY, w - 16, mx, my, click, rightClick);
                           rowY += 22;
                        }
                     }
                  }
               }
            }

            ctx.method_44380();
         }
      }
   }

   private void drawSetting(class_332 ctx, Setting<?> s, int x, int y, int w, int mx, int my, boolean click, boolean rightClick) {
      class_310 mc = class_310.method_1551();
      boolean over = inside(mx, my, x, y, w, 22);
      int hover = Math.round(50.0F * this.hoverValue("setting:" + s.getName(), over));
      ctx.method_25294(x, y, x + w, y + 22 - 1, 639310116);
      if (hover > 0) {
         ctx.method_25294(x, y, x + w, y + 22 - 1, Theme.withAlpha(Theme.ACCENT, hover));
      }

      ctx.method_51433(mc.field_1772, s.getName(), x + 5, y + 7, Theme.TEXT_DIM, false);
      if (s instanceof BoolSetting b) {
         int bx = x + w - 28;
         ctx.method_25294(bx, y + 5, bx + 22, y + 16, b.get() ? Theme.ACCENT : -12238002);
         ctx.method_25294(b.get() ? bx + 13 : bx + 2, y + 7, b.get() ? bx + 20 : bx + 9, y + 14, -1);
         if ((click || rightClick) && over) {
            b.toggle();
            Config.save();
         }
      } else if (s instanceof ModeSetting m) {
         String v = "< " + m.get() + " >";
         ctx.method_51433(mc.field_1772, v, x + w - 8 - mc.field_1772.method_1727(v), y + 7, Theme.ACCENT, false);
         if (click && over) {
            m.cycle(1);
            Config.save();
         } else if (rightClick && over) {
            m.cycle(-1);
            Config.save();
         }
      } else if (s instanceof NumberSetting n) {
         String label = n.getName();

         while (mc.field_1772.method_1727(label) > 92 && label.length() > 5) {
            label = label.substring(0, label.length() - 2) + "…";
         }

         ctx.method_51433(mc.field_1772, label, x + 5, y + 7, Theme.TEXT_DIM, false);
         String value = n.display();
         ctx.method_51433(mc.field_1772, value, x + w - 8 - mc.field_1772.method_1727(value), y + 7, Theme.ACCENT, false);
         int trackX = x + 102;
         int trackW = Math.max(24, x + w - 38 - trackX);
         double t = n.max == n.min ? 0.0 : (n.get() - n.min) / (n.max - n.min);
         int fillW = (int)Math.round(trackW * Math.max(0.0, Math.min(1.0, t)));
         ctx.method_25294(trackX, y + 17, trackX + trackW, y + 19, -12567734);
         if (fillW > 0) {
            ctx.method_25294(trackX, y + 17, trackX + fillW, y + 19, Theme.ACCENT);
         }

         int knobX = trackX + fillW;
         ctx.method_25294(knobX - 2, y + 14, knobX + 3, y + 22, -723974);
         if (click && over) {
            if (mx >= trackX - 4 && mx <= trackX + trackW + 4) {
               this.numberSlider = n;
               this.numberSliderX = trackX;
               this.numberSliderW = trackW;
            } else {
               n.set(n.get() + n.step);
               Config.save();
            }
         } else if (rightClick && over) {
            n.set(n.get() - n.step);
            Config.save();
         }
      } else if (s instanceof ColorSetting c) {
         ctx.method_51433(mc.field_1772, c.getName(), x + 5, y + 7, Theme.TEXT_DIM, false);
         int trackX = x + 88;
         int trackW = Math.max(28, w - 138);
         float[] hsv = java.awt.Color.RGBtoHSB((c.rgb() >> 16) & 255, (c.rgb() >> 8) & 255, c.rgb() & 255, null);
         for (int i = 0; i < 36; i++) {
            int sx = trackX + i * trackW / 36;
            int ex = trackX + (i + 1) * trackW / 36;
            int hueColor = java.awt.Color.HSBtoRGB(i / 36.0F, Math.max(0.75F, hsv[1]), Math.max(0.85F, hsv[2]));
            ctx.method_25294(sx, y + 16, Math.max(sx + 1, ex), y + 20, hueColor | 0xFF000000);
         }
         int knobX = trackX + Math.round(hsv[0] * trackW);
         ctx.method_25294(knobX - 2, y + 14, knobX + 3, y + 22, -1);
         ctx.method_25294(x + w - 38, y + 5, x + w - 24, y + 18, 0xFF000000 | c.rgb());
         if (click && over && my >= y + 13 && mx >= trackX - 5 && mx <= trackX + trackW + 5) {
            this.slider = "espColor";
            this.sliderX = trackX;
            this.sliderW = trackW;
            this.colorSlider = c;
            this.updateEspColorSlider(mx);
         } else if (click && over) {
            try {
               ModuleManager.get(GUITheme.class).prepareColorEdit(c);
            } catch (Throwable var29) {
            }

            this.editingColor = c;
            this.messageDraft = String.format(Locale.ROOT, "%06X", c.rgb());
            this.searchFocused = false;
         } else if (rightClick && over) {
            try {
               ModuleManager.get(GUITheme.class).prepareColorEdit(c);
            } catch (Throwable var28) {
            }

            c.cycle(-1);
            Config.save();
         }
      } else if (s instanceof StringSetting st) {
         String valuex = st.get();
         if (valuex.length() > 18) {
            valuex = valuex.substring(0, 17) + "…";
         }

         ctx.method_51433(mc.field_1772, st.getName(), x + 5, y + 7, Theme.TEXT_DIM, false);
         ctx.method_51433(mc.field_1772, valuex, x + w - 8 - mc.field_1772.method_1727(valuex), y + 7, Theme.ACCENT, false);
         if (click && over) {
            this.editingString = st;
            this.messageDraft = st.get();
            this.searchFocused = false;
         }
      } else if (s instanceof MessageListSetting messages) {
         String valuexx = messages.get().size() + " · bearbeiten";
         ctx.method_51433(mc.field_1772, messages.getName(), x + 5, y + 7, Theme.TEXT_DIM, false);
         ctx.method_51433(mc.field_1772, valuexx, x + w - 8 - mc.field_1772.method_1727(valuexx), y + 7, Theme.ACCENT, false);
         if (click && over) {
            this.editingMessages = messages;
            this.messageDraft = "";
            this.searchFocused = false;
         } else if (rightClick && over && !messages.get().isEmpty()) {
            messages.removeMessage(messages.get().get(messages.get().size() - 1));
            Config.save();
            this.showFeedback("Letzter Eintrag entfernt");
         }
      } else if (s instanceof ListSetting list) {
         String valuexx = list.get().size() + " · bearbeiten";
         ctx.method_51433(mc.field_1772, list.getName(), x + 5, y + 7, Theme.TEXT_DIM, false);
         ctx.method_51433(mc.field_1772, valuexx, x + w - 8 - mc.field_1772.method_1727(valuexx), y + 7, Theme.ACCENT, false);
         if (click && over) {
            this.editingList = list;
            this.messageDraft = "";
            this.searchFocused = false;
         } else if (rightClick && over && !list.get().isEmpty()) {
            List<String> values = new ArrayList<>(list.get());
            list.remove(values.get(values.size() - 1));
            Config.save();
            this.showFeedback("Letzter Eintrag entfernt");
         }
      }
   }

   private void closeEditor() {
      this.editingColor = null;
      this.editingString = null;
      this.editingMessages = null;
      this.editingList = null;
      this.messageDraft = "";
   }

   private void showFeedback(String message) {
      this.feedback = message;
      this.feedbackUntil = System.currentTimeMillis() + 2400L;
   }

   private void drawTextEditor(class_332 ctx) {
      class_310 mc = class_310.method_1551();
      int modalW = Math.min(360, Math.max(200, this.field_22789 - 24));
      int modalH = 112;
      int x = (this.field_22789 - modalW) / 2;
      int y = (this.field_22790 - modalH) / 2;
      String title = this.editingColor != null
         ? "FARBE · RGB HEX"
         : (this.editingString != null ? "TEXT BEARBEITEN" : (this.editingMessages != null ? "NACHRICHT HINZUFÜGEN" : "LISTENEINTRAG HINZUFÜGEN"));
      ctx.method_25294(x - 2, y - 2, x + modalW + 2, y + modalH + 2, -1342177280);
      ctx.method_25294(x, y, x + modalW, y + modalH, -267120099);
      ctx.method_25294(x, y, x + modalW, y + 2, Theme.ACCENT);
      ctx.method_51433(mc.field_1772, title, x + 12, y + 12, Theme.TEXT, true);
      ctx.method_25294(x + 12, y + 33, x + modalW - 12, y + 55, -16250867);
      String draft = (this.editingColor != null ? "#" : "") + this.messageDraft + (System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "");
      ctx.method_51433(mc.field_1772, draft, x + 18, y + 41, Theme.ACCENT, false);
      String instruction = this.editingString == null && this.editingColor == null
         ? "Enter fügt hinzu · Rechtsklick entfernt den letzten Eintrag"
         : "Enter speichert · Esc bricht ab";
      ctx.method_51433(mc.field_1772, instruction, x + 12, y + 72, Theme.TEXT_DIM, false);
   }

   private void drawFeedback(class_332 ctx) {
      class_310 mc = class_310.method_1551();
      int w = Math.min(this.field_22789 - 24, mc.field_1772.method_1727(this.feedback) + 20);
      int x = (this.field_22789 - w) / 2;
      int y = this.field_22790 - 40;
      ctx.method_25294(x, y, x + w, y + 22, -435088871);
      ctx.method_25294(x, y, x + 2, y + 22, Theme.ACCENT);
      ctx.method_51433(mc.field_1772, this.feedback, x + 9, y + 7, Theme.TEXT, false);
   }

   private void drawConfig(class_332 ctx, int x, int y, int w, int mx, int my, boolean click) {
      class_310 mc = class_310.method_1551();
      ctx.method_51433(mc.field_1772, "TAB-ANIMATION", x + 10, y + 37, Theme.ACCENT, true);
      this.drawConfigOption(ctx, mc, "Enabled", Config.guiAnimations ? "AN" : "AUS", x + 10, y + 53, w - 20);
      this.drawConfigOption(ctx, mc, "Style", Config.guiAnimationType, x + 10, y + 73, w - 20);
      this.drawConfigOption(ctx, mc, "Richtung", Config.guiAnimationDirection, x + 10, y + 93, w - 20);
      this.drawConfigOption(ctx, mc, "Easing", Config.guiEasing, x + 10, y + 113, w - 20);
      this.drawConfigOption(ctx, mc, "Dauer", Config.guiAnimationDuration + " ms", x + 10, y + 133, w - 20);
      this.drawConfigOption(ctx, mc, "Geschwindigkeit", String.format(Locale.ROOT, "%.1fx", Config.guiAnimationSpeed), x + 10, y + 153, w - 20);
      ctx.method_51433(mc.field_1772, "Akzentfarbe · Farbton", x + 10, y + 185, Theme.TEXT, false);
      this.drawSlider(ctx, x + 10, y + 207, w - 20, Config.accentHue / 360.0F);
      ctx.method_51433(mc.field_1772, String.format(Locale.ROOT, "%.0f°", Config.accentHue), x + w - 42, y + 192, Theme.ACCENT, false);
      ctx.method_51433(mc.field_1772, "Hintergrund · Blur-Stärke", x + 10, y + 224, Theme.TEXT, false);
      this.drawSlider(ctx, x + 10, y + 246, w - 20, Config.blurStrength / 150.0F);
      ctx.method_51433(
         mc.field_1772,
         Config.blurStrength == 0 ? "Aus" : (Config.blurStrength < 60 ? "Leicht" : (Config.blurStrength < 110 ? "Mittel" : "Stark")),
         x + w - 48,
         y + 231,
         Theme.ACCENT,
         false
      );
      this.drawConfigSlider(ctx, mc, "Transparenz", Config.guiTransparency + "%", "transparency", x + 10, y + 270, w - 20, Config.guiTransparency / 100.0F);
      this.drawConfigSlider(ctx, mc, "Randgröße", Config.borderSize + " px", "border", x + 10, y + 322, w - 20, (Config.borderSize - 1) / 5.0F);
      this.drawConfigSlider(ctx, mc, "Rundungen", Config.cornerRadius + " px", "radius", x + 10, y + 360, w - 20, (Config.cornerRadius - 10) / 22.0F);
      this.drawConfigSlider(ctx, mc, "Menügröße", Config.guiScalePercent + "%", "scale", x + 10, y + 398, w - 20, (Config.guiScalePercent - 70) / 30.0F);
      this.drawConfigSlider(ctx, mc, "Randabstand", Config.guiEdgeMargin + " px", "margin", x + 10, y + 436, w - 20, Config.guiEdgeMargin / 60.0F);
      if (click && inside(mx, my, x + 10, y + 202, w - 20, 12)) {
         this.slider = "hue";
         this.sliderX = x + 10;
         this.sliderW = w - 20;
         Config.accentHue = Math.max(0.0F, Math.min(360.0F, (float)(mx - this.sliderX) / this.sliderW * 360.0F));
         this.applyHue();
      }

      if (click && inside(mx, my, x + 10, y + 241, w - 20, 12)) {
         this.slider = "blur";
         this.sliderX = x + 10;
         this.sliderW = w - 20;
         Config.blurStrength = Math.round(Math.max(0.0F, Math.min(1.0F, (float)(mx - this.sliderX) / this.sliderW)) * 150.0F);
      }

      if (click && inside(mx, my, x + 10, y + 53, w - 20, 18)) {
         Config.guiAnimations = !Config.guiAnimations;
      }

      if (click && inside(mx, my, x + 10, y + 73, w - 20, 18)) {
         Config.guiAnimationType = cycle(Config.guiAnimationType, List.of("Fade", "Slide", "Scale", "Zoom"));
      }

      if (click && inside(mx, my, x + 10, y + 93, w - 20, 18)) {
         Config.guiAnimationDirection = cycle(Config.guiAnimationDirection, List.of("Left", "Right", "Up", "Down"));
      }

      if (click && inside(mx, my, x + 10, y + 113, w - 20, 18)) {
         Config.guiEasing = cycle(Config.guiEasing, List.of("Linear", "Sine", "Cubic"));
      }

      if (click && inside(mx, my, x + 10, y + 133, w - 20, 18)) {
         Config.guiAnimationDuration = Config.guiAnimationDuration >= 1000 ? 50 : Math.min(1000, Config.guiAnimationDuration + 50);
      }

      if (click && inside(mx, my, x + 10, y + 153, w - 20, 18)) {
         Config.guiAnimationSpeed = Config.guiAnimationSpeed >= 5.0F ? 0.1F : Math.min(5.0F, Config.guiAnimationSpeed + 0.1F);
      }

      this.beginConfigSlider(click, mx, my, x + 10, y + 284, w - 20, "transparency");
      this.beginConfigSlider(click, mx, my, x + 10, y + 336, w - 20, "border");
      this.beginConfigSlider(click, mx, my, x + 10, y + 374, w - 20, "radius");
      this.beginConfigSlider(click, mx, my, x + 10, y + 412, w - 20, "scale");
      this.beginConfigSlider(click, mx, my, x + 10, y + 450, w - 20, "margin");
      if (click) {
         Config.save();
      }
   }

   private void drawConfigSlider(class_332 ctx, class_310 mc, String label, String value, String id, int x, int y, int w, float amount) {
      ctx.method_51433(mc.field_1772, label, x + 2, y, Theme.TEXT_DIM, false);
      int vw = mc.field_1772.method_1727(value);
      ctx.method_51433(mc.field_1772, value, x + w - vw - 2, y, Theme.ACCENT, true);
      this.drawSlider(ctx, x, y + 17, w, amount);
   }

   private void beginConfigSlider(boolean click, int mx, int my, int x, int y, int w, String id) {
      if (click && inside(mx, my, x, y - 3, w, 20)) {
         this.slider = id;
         this.sliderX = x;
         this.sliderW = w;
      }
   }

   private void updateEspColorSlider(int mx) {
      if (this.colorSlider == null) return;
      float t = Math.max(0.0F, Math.min(1.0F, (float)(mx - this.sliderX) / Math.max(1, this.sliderW)));
      int rgb = this.colorSlider.rgb();
      float[] hsv = java.awt.Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
      this.colorSlider.set(java.awt.Color.HSBtoRGB(t, Math.max(0.75F, hsv[1]), Math.max(0.85F, hsv[2])) & 0xFFFFFF);
   }

   private void updateConfigSlider(int mx, boolean active, boolean release) {
      if (this.slider.isEmpty()) return;
      float t = Math.max(0.0F, Math.min(1.0F, (float)(mx - this.sliderX) / Math.max(1, this.sliderW)));
      switch (this.slider) {
         case "hue" -> { Config.accentHue = t * 360.0F; this.applyHue(); }
         case "blur" -> Config.blurStrength = Math.round(t * 150.0F);
         case "transparency" -> Config.guiTransparency = Math.round(t * 100);
         case "border" -> Config.borderSize = 1 + Math.round(t * 5);
         case "radius" -> Config.cornerRadius = 10 + Math.round(t * 22);
         case "scale" -> Config.guiScalePercent = 70 + Math.round(t * 30);
         case "margin" -> Config.guiEdgeMargin = Math.round(t * 60);
         case "espColor" -> this.updateEspColorSlider(mx);
      }
      if (release) {
         Config.save();
         if (this.slider.equals("espColor")) this.colorSlider = null;
         this.slider = "";
      }
   }

   private void drawConfigOption(class_332 ctx, class_310 mc, String label, String value, int x, int y, int w) {
      roundedRect(ctx, x, y, w, 18, Math.min(9, Config.cornerRadius), 941627435);
      ctx.method_51433(mc.field_1772, label, x + 5, y + 5, Theme.TEXT_DIM, false);
      int vw = mc.field_1772.method_1727(value);
      ctx.method_51433(mc.field_1772, value, x + w - vw - 5, y + 5, Theme.ACCENT, true);
   }

   private static String cycle(String current, List<String> values) {
      int index = values.indexOf(current);
      return values.get((index + 1 + values.size()) % values.size());
   }

   private void drawSlider(class_332 ctx, int x, int y, int w, float t) {
      int clamped = Math.max(0, Math.min(w, Math.round(w * t)));
      roundedRect(ctx, x, y, w, 5, 3, Theme.withAlpha(-12567734, 220));
      roundedRect(ctx, x, y, Math.max(1, clamped), 5, 3, Theme.ACCENT);
      roundedRect(ctx, x + clamped - 5, y - 3, 10, 11, 5, -1);
   }

   private void applyHue() {
      try {
         ModuleManager.get(GUITheme.class).setAccentHue(Config.accentHue);
      } catch (IllegalStateException var2) {
      }
   }

   private boolean pMinimized(String tab) {
      return this.panel(tab, 0, 0)[2] == 2;
   }

   private void pMin(String tab) {
      int[] p = this.panel(tab, 0, 0);
      p[2] = p[2] == 2 ? 1 : 2;
      Config.save();
   }

   private void panel(class_332 c, int x, int y, int w, int h) {
      int border = Math.max(1, Math.min(6, Config.borderSize));
      int radius = Math.max(10, Math.min(32, Config.cornerRadius));
      int transparency = Math.max(0, Math.min(100, Config.guiTransparency));
      int fillAlpha = (100 - transparency) * 255 / 100;
      int edgeAlpha = Math.max(95, Math.min(190, 175 - transparency / 3));

      // Soft shadow + luminous accent rim create the rounded "bubble" silhouette.
      roundedRect(c, x - border - 3, y - border - 2, w + border * 2 + 6, h + border * 2 + 5,
         radius + border + 3, Theme.withAlpha(0xFF000000, 48));
      roundedRect(c, x - border, y - border, w + border * 2, h + border * 2,
         radius + border, Theme.withAlpha(Theme.ACCENT, edgeAlpha));

      // The slider controls opacity, not brightness: higher transparency reveals more of the game.
      if (fillAlpha > 0) {
         roundedRect(c, x, y, w, h, radius, Theme.withAlpha(0xFF0B0A11, fillAlpha));
      }
   }

   private static void roundedRect(class_332 c, int x, int y, int w, int h, int radius, int color) {
      if (w <= 0 || h <= 0 || (color >>> 24 & 255) == 0) return;
      int r = Math.max(1, Math.min(radius, Math.min(w, h) / 2));
      int baseAlpha = color >>> 24 & 255;
      int rgb = color & 0xFFFFFF;
      final int samples = 4;

      for (int row = 0; row < h; row++) {
         double insetTotal = 0.0;
         for (int sample = 0; sample < samples; sample++) {
            double py = row + (sample + 0.5) / samples;
            double inset = 0.0;
            if (py < r) {
               double dy = r - py;
               inset = r - Math.sqrt(Math.max(0.0, r * r - dy * dy));
            } else if (py > h - r) {
               double dy = py - (h - r);
               inset = r - Math.sqrt(Math.max(0.0, r * r - dy * dy));
            }
            insetTotal += Math.max(0.0, inset);
         }

         double inset = insetTotal / samples;
         int edge = Math.max(0, Math.min(w / 2, (int)Math.floor(inset)));
         double coverage = Math.max(0.0, Math.min(1.0, 1.0 - (inset - Math.floor(inset))));
         int left = x + edge;
         int right = x + w - edge - 1;

         if (coverage > 0.015) {
            int alpha = Math.max(0, Math.min(255, (int)Math.round(baseAlpha * coverage)));
            int edgeColor = (alpha << 24) | rgb;
            c.method_25294(left, y + row, left + 1, y + row + 1, edgeColor);
            if (right != left) {
               c.method_25294(right, y + row, right + 1, y + row + 1, edgeColor);
            }
         }

         int innerLeft = left + 1;
         int innerRight = right;
         if (innerRight > innerLeft) {
            c.method_25294(innerLeft, y + row, innerRight, y + row + 1, color);
         }
      }
   }

   private static String icon(String s) {
      return switch (s) {
         case "Combat" -> "[C]";
         case "Movement" -> "[M]";
         case "Visuals" -> "[V]";
         case "Player" -> "[P]";
         case "World" -> "[W]";
         case "Misc" -> "[X]";
         case "HUD" -> "[H]";
         default -> "[CFG]";
      };
   }

   private static boolean inside(int mx, int my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }
}
