package dev.nightfall.gui;

import dev.nightfall.config.Config;
import dev.nightfall.module.Category;
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
import dev.nightfall.util.Keys;
import dev.nightfall.util.Theme;
import java.util.ArrayList;
import java.util.EnumMap;
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

public class ClickGuiScreen extends class_437 {
   private static final int HEADER_TOP = 48;
   private static final int CATEGORY_H = 32;
   private static final int MODULE_H = 29;
   private static final int SETTING_H = 23;
   private static final int GAP = 6;
   private static final int FOOTER_H = 26;
   private static final Set<Category> OPEN_CATEGORIES = new HashSet<>();
   private static final Set<Module> EXPANDED = new HashSet<>();
   private static final Map<Category, Float> CATEGORY_PROGRESS = new EnumMap<>(Category.class);
   private static final Map<Module, Float> MODULE_PROGRESS = new HashMap<>();
   private static final Map<Module, Float> ENABLE_PROGRESS = new HashMap<>();
   private static String search = "";
   private static boolean onlyFavorites;
   private final List<Category> cats = new ArrayList<>();
   private final Keys.Poller poller = new Keys.Poller();
   private boolean prevL;
   private boolean prevR;
   private boolean prevM;
   private boolean clickL;
   private boolean clickR;
   private boolean clickM;
   private boolean searchFocus;
   private Module listening;
   private String notice = "";
   private long noticeUntil;
   private NumberSetting slider;
   private int sliderX;
   private int sliderW;
   private int visibleClipBottom;
   private MessageListSetting editingMessages;
   private StringSetting editingString;
   private ColorSetting editingColor;
   private String messageDraft = "";
   private float scroll;
   private int panelX;
   private int panelW;
   private int viewportTop;
   private int viewportBottom;

   public ClickGuiScreen() {
      super(class_2561.method_43470("Nightfall"));

      for (Category c : Category.values()) {
         if (!ModuleManager.byCategory(c).isEmpty()) {
            this.cats.add(c);
         }
      }

      long h = class_310.method_1551().method_22683().method_4490();
      this.prevL = GLFW.glfwGetMouseButton(h, 0) == 1;
      this.prevR = GLFW.glfwGetMouseButton(h, 1) == 1;
      this.prevM = GLFW.glfwGetMouseButton(h, 2) == 1;
      this.poller.sync(h);
   }

   public boolean method_25421() {
      return false;
   }

   public void method_25432() {
      Config.save();
   }

   public boolean method_25401(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      if (!(mouseY < this.viewportTop) && !(mouseY > this.viewportBottom)) {
         this.scroll = Math.max(0.0F, this.scroll - (float)verticalAmount * 34.0F);
         return true;
      } else {
         return false;
      }
   }

   private List<Module> visible(Category category) {
      List<Module> all = ModuleManager.byCategory(category);
      List<Module> out = new ArrayList<>();
      String q = search.toLowerCase(Locale.ROOT);

      for (Module m : all) {
         if ((!onlyFavorites || m.isFavorite())
            && (q.isEmpty() || m.getName().toLowerCase(Locale.ROOT).contains(q) || m.getDescription().toLowerCase(Locale.ROOT).contains(q))) {
            out.add(m);
         }
      }

      return out;
   }

   private <K> float ease(Map<K, Float> values, K key, boolean target, float delta) {
      float wanted = target ? 1.0F : 0.0F;
      float current = values.getOrDefault(key, wanted);
      float factor = Math.min(1.0F, Math.max(0.04F, delta * 10.0F));
      current += (wanted - current) * factor;
      if (Math.abs(wanted - current) < 0.008F) {
         current = wanted;
      }

      values.put(key, current);
      return current;
   }

   private boolean categoryOpen(Category c) {
      return OPEN_CATEGORIES.contains(c) || !search.isEmpty() || onlyFavorites;
   }

   public boolean method_25404(class_11908 input) {
      int keyCode = input.comp_4795();
      if (this.listening != null || this.editingColor != null || this.editingMessages != null || this.editingString != null || this.searchFocus) {
         this.onKey(keyCode);
         return true;
      } else if (keyCode == Config.guiKey) {
         this.method_25419();
         return true;
      } else {
         return super.method_25404(input);
      }
   }

   private void onKey(int k) {
      if (this.listening != null) {
         if (k == 256) {
            this.listening = null;
         } else if (k != 259 && k != 261) {
            String err = ModuleManager.assignKey(this.listening, k);
            this.notice(err != null ? err : this.listening.getName() + " = " + Keys.name(k));
            if (err == null) {
               Config.save();
            }

            this.listening = null;
         } else {
            this.listening.setKey(-1);
            this.notice("Keybind entfernt: " + this.listening.getName());
            this.listening = null;
            Config.save();
         }
      } else if (this.editingColor != null) {
         if (k == 256) {
            this.editingColor = null;
            this.messageDraft = "";
         } else if (k == 259) {
            if (!this.messageDraft.isEmpty()) {
               this.messageDraft = this.messageDraft.substring(0, this.messageDraft.length() - 1);
            }
         } else if (k == 257) {
            String hex = this.messageDraft.replace("#", "").trim();

            try {
               if (hex.length() != 6) {
                  throw new NumberFormatException();
               }

               this.editingColor.set(Integer.parseInt(hex, 16));
               Config.save();
               this.notice("Farbe gespeichert: #" + hex.toUpperCase(Locale.ROOT));
            } catch (NumberFormatException var4) {
               this.notice("Bitte genau 6 Hex-Zeichen eingeben");
            }

            this.editingColor = null;
            this.messageDraft = "";
         } else if (k >= 48 && k <= 57 && this.messageDraft.length() < 6) {
            this.messageDraft = this.messageDraft + (char)(48 + k - 48);
         } else if (k >= 65 && k <= 70 && this.messageDraft.length() < 6) {
            this.messageDraft = this.messageDraft + (char)(65 + k - 65);
         }
      } else if (this.editingMessages == null && this.editingString == null) {
         if (this.searchFocus) {
            if (k == 259) {
               if (!search.isEmpty()) {
                  search = search.substring(0, search.length() - 1);
               }

               this.scroll = 0.0F;
            } else if (k >= 65 && k <= 90) {
               search = search + (char)(97 + k - 65);
               this.scroll = 0.0F;
            } else if (k >= 48 && k <= 57) {
               search = search + (char)(48 + k - 48);
               this.scroll = 0.0F;
            } else if (k == 32) {
               search = search + " ";
               this.scroll = 0.0F;
            } else if (k == 257) {
               this.searchFocus = false;
            } else if (k == 256) {
               search = "";
               this.searchFocus = false;
               this.scroll = 0.0F;
            }
         }
      } else if (k == 256) {
         this.editingMessages = null;
         this.editingString = null;
         this.messageDraft = "";
      } else if (k == 259) {
         if (!this.messageDraft.isEmpty()) {
            this.messageDraft = this.messageDraft.substring(0, this.messageDraft.length() - 1);
         }
      } else if (k == 257) {
         if (this.editingString != null) {
            this.editingString.set(this.messageDraft);
            Config.save();
            this.notice("Einstellung gespeichert");
            this.editingString = null;
            this.messageDraft = "";
         } else {
            if (this.editingMessages != null && !this.messageDraft.isBlank()) {
               boolean added = this.editingMessages.addMessage(this.messageDraft.trim());
               this.notice(added ? "Nachricht hinzugefügt" : "Leer oder bereits vorhanden");
               Config.save();
            }

            this.messageDraft = "";
         }
      } else if (k == 32) {
         if (this.messageDraft.length() < 256) {
            this.messageDraft = this.messageDraft + " ";
         }
      } else if (k >= 65 && k <= 90 && this.messageDraft.length() < 256) {
         boolean shift = GLFW.glfwGetKey(class_310.method_1551().method_22683().method_4490(), 340) == 1
            || GLFW.glfwGetKey(class_310.method_1551().method_22683().method_4490(), 344) == 1;
         this.messageDraft = this.messageDraft + (char)((shift ? 65 : 97) + (k - 65));
      } else if (k >= 48 && k <= 57 && this.messageDraft.length() < 256) {
         this.messageDraft = this.messageDraft + (char)(48 + k - 48);
      } else if (k == 45 && this.messageDraft.length() < 256) {
         boolean shift = GLFW.glfwGetKey(class_310.method_1551().method_22683().method_4490(), 340) == 1
            || GLFW.glfwGetKey(class_310.method_1551().method_22683().method_4490(), 344) == 1;
         this.messageDraft = this.messageDraft + (shift ? "_" : "-");
      } else if (k == 46 && this.messageDraft.length() < 256) {
         this.messageDraft = this.messageDraft + ".";
      } else if (k == 44 && this.messageDraft.length() < 256) {
         this.messageDraft = this.messageDraft + ",";
      } else if (k == 47 && this.messageDraft.length() < 256) {
         this.messageDraft = this.messageDraft + "/";
      } else if ((k == 59 || k == 46) && this.messageDraft.length() < 256) {
         boolean shift = GLFW.glfwGetKey(class_310.method_1551().method_22683().method_4490(), 340) == 1
            || GLFW.glfwGetKey(class_310.method_1551().method_22683().method_4490(), 344) == 1;
         if (shift) {
            this.messageDraft = this.messageDraft + ":";
         } else if (k == 59) {
            this.messageDraft = this.messageDraft + ";";
         } else {
            this.messageDraft = this.messageDraft + ".";
         }
      } else if (k == 61 && this.messageDraft.length() < 256) {
         this.messageDraft = this.messageDraft + "=";
      }
   }

   private void notice(String s) {
      this.notice = s;
      this.noticeUntil = System.currentTimeMillis() + 2600L;
   }

   public void method_25394(class_332 ctx, int mx, int my, float delta) {
      class_310 mc = class_310.method_1551();
      long handle = mc.method_22683().method_4490();
      boolean l = GLFW.glfwGetMouseButton(handle, 0) == 1;
      boolean r = GLFW.glfwGetMouseButton(handle, 1) == 1;
      boolean m = GLFW.glfwGetMouseButton(handle, 2) == 1;
      this.clickL = l && !this.prevL;
      this.clickR = r && !this.prevR;
      this.clickM = m && !this.prevM;
      this.prevL = l;
      this.prevR = r;
      this.prevM = m;
      ctx.method_25294(0, 0, this.field_22789, this.field_22790, Theme.BG_DIM);
      ctx.method_25294(0, 0, this.field_22789, 2, Theme.ACCENT);
      ctx.method_25294(0, this.field_22790 - 2, this.field_22789, this.field_22790, Theme.ACCENT_DIM);
      this.panelW = Math.min(720, this.field_22789 - 36);
      this.panelX = (this.field_22789 - this.panelW) / 2;
      this.viewportTop = 48;
      this.viewportBottom = Math.max(this.viewportTop + 40, this.field_22790 - 26 - 8);
      ctx.method_25294(this.panelX, 5, this.panelX + this.panelW, 39, Theme.PANEL);
      ctx.method_25294(this.panelX, 5, this.panelX + 3, 39, Theme.ACCENT);
      ctx.method_51433(mc.field_1772, "NIGHTFALL", this.panelX + 13, 10, Theme.TEXT, true);
      ctx.method_51433(mc.field_1772, "CLIENT  /  MODULES", this.panelX + 13, 24, Theme.TEXT_DIM, false);
      ctx.method_51433(mc.field_1772, "0.5.15", this.panelX + 132, 11, Theme.ACCENT, false);
      int searchW = Math.min(220, this.panelW - 280);
      searchW = Math.max(120, searchW);
      int searchX = this.panelX + this.panelW - searchW - 10;
      int favoriteButtonW = 112;
      int favoriteButtonX = searchX - favoriteButtonW - 8;
      int favoriteBg = onlyFavorites ? Theme.withAlpha(Theme.ACCENT, 90) : -384823016;
      ctx.method_25294(favoriteButtonX, 9, favoriteButtonX + favoriteButtonW, 31, favoriteBg);
      ctx.method_25294(favoriteButtonX, 30, favoriteButtonX + favoriteButtonW, 31, onlyFavorites ? Theme.ACCENT : Theme.ACCENT_DIM);
      String favoriteText = onlyFavorites ? "★ Nur Favoriten ✓" : "☆ Nur Favoriten";
      int favoriteTextW = mc.field_1772.method_1727(favoriteText);
      ctx.method_51433(
         mc.field_1772, favoriteText, favoriteButtonX + (favoriteButtonW - favoriteTextW) / 2, 17, onlyFavorites ? Theme.ACCENT : Theme.TEXT_DIM, false
      );
      if (this.clickL && in(mx, my, favoriteButtonX, 9, favoriteButtonW, 22)) {
         onlyFavorites = !onlyFavorites;
         this.scroll = 0.0F;
         this.searchFocus = false;
         this.clickL = false;
      }

      ctx.method_25294(searchX, 9, searchX + searchW, 31, -384823016);
      ctx.method_25294(searchX, 30, searchX + searchW, 31, this.searchFocus ? Theme.ACCENT : Theme.ACCENT_DIM);
      String searchText = search.isEmpty() && !this.searchFocus
         ? "Module suchen..."
         : search + (this.searchFocus && System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "");
      ctx.method_51433(mc.field_1772, searchText, searchX + 9, 17, search.isEmpty() ? Theme.TEXT_DIM : Theme.TEXT, false);
      if (this.clickL && in(mx, my, searchX, 9, searchW, 22)) {
         this.searchFocus = true;
         this.clickL = false;
      } else if (this.clickL && this.searchFocus) {
         this.searchFocus = false;
      }

      if (this.listening != null && this.clickR) {
         this.listening.setKey(-1);
         this.notice("Keybind entfernt: " + this.listening.getName());
         this.listening = null;
         this.clickR = false;
         Config.save();
      }

      if (!l) {
         this.slider = null;
      }

      if (this.slider != null && this.sliderW > 0) {
         double t = (double)(mx - this.sliderX) / this.sliderW;
         this.slider.set(this.slider.min + Math.max(0.0, Math.min(1.0, t)) * (this.slider.max - this.slider.min));
      }

      List<Category> displayedCats = new ArrayList<>();

      for (Category c : this.cats) {
         if (!this.visible(c).isEmpty()) {
            displayedCats.add(c);
         }
      }

      float[] categoryBodies = new float[displayedCats.size()];
      float totalHeight = 0.0F;

      for (int i = 0; i < displayedCats.size(); i++) {
         Category cx = displayedCats.get(i);
         List<Module> mods = this.visible(cx);
         float cp = this.ease(CATEGORY_PROGRESS, cx, this.categoryOpen(cx), delta);
         float bodyTarget = 0.0F;

         for (Module mod : mods) {
            float mp = this.ease(MODULE_PROGRESS, mod, EXPANDED.contains(mod), delta);
            bodyTarget += 29.0F + mod.getSettings().size() * 23 * mp;
         }

         categoryBodies[i] = bodyTarget * cp;
         totalHeight += 32.0F + categoryBodies[i] + 6.0F;
      }

      float maxScroll = Math.max(0.0F, totalHeight - (this.viewportBottom - this.viewportTop));
      this.scroll = Math.max(0.0F, Math.min(maxScroll, this.scroll));
      ctx.method_25294(this.panelX, this.viewportTop - 4, this.panelX + this.panelW, this.viewportBottom, Theme.PANEL);
      ctx.method_25294(this.panelX, this.viewportTop - 4, this.panelX + this.panelW, this.viewportTop - 2, Theme.ACCENT_DIM);
      ctx.method_25294(this.panelX, this.viewportTop - 4, this.panelX + this.panelW, this.viewportTop - 2, Theme.ACCENT_DIM);
      ctx.method_44379(this.panelX, this.viewportTop, this.panelX + this.panelW, this.viewportBottom);
      float y = this.viewportTop - this.scroll;
      Module hovered = null;
      boolean modalOpen = this.editingMessages != null || this.editingString != null || this.editingColor != null;
      if (modalOpen) {
         this.clickL = false;
         this.clickM = false;
      }

      for (int i = 0; i < displayedCats.size(); i++) {
         Category category = displayedCats.get(i);
         List<Module> mods = this.visible(category);
         float cp = CATEGORY_PROGRESS.getOrDefault(category, 0.0F);
         int cy = Math.round(y);
         this.drawCategoryHeader(ctx, category, cy, mx, my, cp);
         if (my >= this.viewportTop && my < this.viewportBottom && in(mx, my, this.panelX, cy, this.panelW, 32) && this.clickL && !modalOpen) {
            if (!OPEN_CATEGORIES.remove(category)) {
               OPEN_CATEGORIES.add(category);
            }

            this.clickL = false;
         }

         float bodyH = categoryBodies[i];
         int bodyBottom = Math.round(y + 32.0F + bodyH);
         if (bodyH > 0.5F) {
            ctx.method_25294(this.panelX + 1, cy + 32, this.panelX + this.panelW - 1, bodyBottom, Theme.withAlpha(Theme.PANEL, 248));
            ctx.method_25294(this.panelX + 1, cy + 32, this.panelX + 3, bodyBottom, Theme.withAlpha(Theme.ACCENT, 95));
            ctx.method_44379(this.panelX + 1, cy + 32, this.panelX + this.panelW - 1, Math.min(this.viewportBottom, bodyBottom));
            float rowY = y + 32.0F;

            for (Module mod : mods) {
               int ry = Math.round(rowY);
               float mp = MODULE_PROGRESS.getOrDefault(mod, 0.0F);
               boolean rowInViewport = ry + 29 >= this.viewportTop && ry <= this.viewportBottom && ry < bodyBottom;
               boolean rowHovered = rowInViewport && my < bodyBottom && in(mx, my, this.panelX + 1, ry, this.panelW - 2, 29);
               this.drawModuleRow(ctx, mod, ry, mx, my, rowHovered, delta);
               if (rowHovered && !modalOpen) {
                  int toggleX = this.panelX + this.panelW - 47;
                  int favoriteX = this.panelX + this.panelW - 82;
                  if (this.clickL && mx >= favoriteX - 5 && mx < favoriteX + 14) {
                     mod.setFavorite(!mod.isFavorite());
                     Config.save();
                     this.notice(mod.getName() + (mod.isFavorite() ? " zu Favoriten hinzugefuegt" : " aus Favoriten entfernt"));
                     this.clickL = false;
                  } else if (this.clickL && mx >= toggleX) {
                     mod.toggle();
                     Config.save();
                     this.clickL = false;
                  } else if (this.clickL && mx >= this.panelX + this.panelW - 138 && mx < favoriteX - 5) {
                     this.listening = mod;
                     this.searchFocus = false;
                     this.notice("Taste drücken für " + mod.getName() + " · Rechtsklick/Entf löscht");
                     this.clickL = false;
                  } else if (this.clickL) {
                     this.toggleExpanded(mod);
                     this.clickL = false;
                  } else if (this.clickR) {
                     this.toggleExpanded(mod);
                     this.clickR = false;
                  } else if (this.clickM) {
                     this.listening = mod;
                     this.searchFocus = false;
                     this.notice("Taste drücken für " + mod.getName() + " · Rechtsklick/Entf löscht");
                     this.clickM = false;
                  }
               }

               if (rowHovered) {
                  hovered = mod;
               }

               rowY += 29.0F;
               float settingsH = mod.getSettings().size() * 23 * mp;
               if (settingsH > 0.5F) {
                  int settingsBottom = Math.round(rowY + settingsH);
                  ctx.method_44379(this.panelX + 6, Math.round(rowY), this.panelX + this.panelW - 6, Math.min(bodyBottom, settingsBottom));
                  this.visibleClipBottom = Math.min(bodyBottom, this.viewportBottom);

                  for (int si = 0; si < mod.getSettings().size(); si++) {
                     int sy = Math.round(rowY + si * 23);
                     if (sy + 23 >= this.viewportTop && sy < this.viewportBottom && sy < bodyBottom) {
                        this.drawSetting(ctx, mod.getSettings().get(si), sy, mx, my, this.panelX + 8, this.panelW - 16, modalOpen);
                     }
                  }

                  ctx.method_44380();
               }

               rowY += settingsH;
            }

            ctx.method_44380();
         }

         y += 32.0F + bodyH + 6.0F;
      }

      ctx.method_44380();
      if (totalHeight > this.viewportBottom - this.viewportTop + 1) {
         int trackX = this.panelX + this.panelW - 3;
         int trackH = this.viewportBottom - this.viewportTop;
         int thumbH = Math.max(24, (int)(trackH * (trackH / totalHeight)));
         int thumbY = this.viewportTop + (int)((trackH - thumbH) * (this.scroll / Math.max(1.0F, maxScroll)));
         ctx.method_25294(trackX, this.viewportTop, trackX + 2, this.viewportBottom, 1157627903);
         ctx.method_25294(trackX, thumbY, trackX + 2, thumbY + thumbH, Theme.ACCENT);
      }

      if (hovered != null && !modalOpen) {
         String desc = hovered.getDescription();
         int max = this.panelW - 16;

         while (mc.field_1772.method_1727(desc) > max && desc.length() > 8) {
            desc = desc.substring(0, desc.length() - 2) + "…";
         }

         ctx.method_51433(mc.field_1772, desc, this.panelX + 2, this.field_22790 - 17, Theme.TEXT_DIM, false);
      }

      if (modalOpen) {
         this.drawTextModal(ctx, mx, my);
      }

      if (this.listening != null) {
         String msg = "Taste für " + this.listening.getName() + " drücken · Backspace = löschen · Esc = abbrechen";
         this.drawNotice(ctx, msg, -11654);
      } else if (System.currentTimeMillis() < this.noticeUntil) {
         this.drawNotice(ctx, this.notice, -11654);
      }

      String footer = "★ Favorit  ·  L Optionen  ·  R Einstellungen  ·  M Keybind  ·  Mausrad Scroll";
      ctx.method_25294(this.panelX, this.field_22790 - 22, this.panelX + this.panelW, this.field_22790 - 4, Theme.PANEL);
      ctx.method_51433(mc.field_1772, footer, this.panelX + 10, this.field_22790 - 16, Theme.TEXT_DIM, false);
      ctx.method_51433(
         mc.field_1772, "0.5.15", this.panelX + this.panelW - 10 - mc.field_1772.method_1727("0.5.15"), this.field_22790 - 16, Theme.ACCENT, false
      );
   }

   private void drawCategoryHeader(class_332 ctx, Category c, int y, int mx, int my, float progress) {
      class_310 mc = class_310.method_1551();
      boolean open = this.categoryOpen(c);
      boolean hover = in(mx, my, this.panelX, y, this.panelW, 32);
      int top = Theme.DESIGN.equals("Flat") ? Theme.HEADER_BOT : Theme.HEADER_TOP;
      ctx.method_25296(this.panelX, y, this.panelX + this.panelW, y + 32 - 1, top, Theme.HEADER_BOT);
      if (hover) {
         ctx.method_25294(this.panelX, y, this.panelX + this.panelW, y + 32 - 1, Theme.ROW_HOVER);
      }

      ctx.method_25294(this.panelX, y + 32 - 2, this.panelX + this.panelW, y + 32 - 1, Theme.withAlpha(Theme.ACCENT, open ? 210 : 90));
      String icon = this.categoryIcon(c);
      ctx.method_25294(this.panelX + 9, y + 8, this.panelX + 27, y + 25, Theme.withAlpha(Theme.ACCENT, 45));
      ctx.method_51433(mc.field_1772, icon, this.panelX + 15, y + 13, Theme.ACCENT, true);
      ctx.method_51433(mc.field_1772, c.display.toUpperCase(Locale.ROOT), this.panelX + 36, y + 13, Theme.TEXT, true);
      String count = String.valueOf(this.visible(c).size());
      int countW = mc.field_1772.method_1727(count);
      ctx.method_51433(mc.field_1772, count, this.panelX + this.panelW - 38 - countW, y + 13, Theme.TEXT_DIM, false);
      String chevron = open ? "v" : ">";
      ctx.method_51433(mc.field_1772, chevron, this.panelX + this.panelW - 22, y + 13, Theme.ACCENT, true);
   }

   private String categoryIcon(Category c) {
      return switch (c) {
         case COMBAT -> "C";
         case MOVEMENT -> "M";
         case VISUALS -> "V";
         case WORLD -> "W";
         case BASEFINDER -> "B";
         case PLAYER -> "P";
         case HUD -> "H";
         case MISC -> "*";
      };
   }

   private void drawModuleRow(class_332 ctx, Module mod, int y, int mx, int my, boolean hover, float delta) {
      class_310 mc = class_310.method_1551();
      float enabled = this.ease(ENABLE_PROGRESS, mod, mod.isEnabled(), delta);
      int rowBg = hover ? Theme.ROW_HOVER : 402653184;
      ctx.method_25294(this.panelX + 3, y, this.panelX + this.panelW - 3, y + 29 - 1, rowBg);
      if (enabled > 0.01F) {
         int alpha = Math.max(0, Math.min(155, (int)(enabled * 155.0F)));
         ctx.method_25294(this.panelX + 3, y, this.panelX + 5, y + 29 - 1, Theme.withAlpha(Theme.ACCENT, alpha));
      }

      int iconX = this.panelX + 11;
      ctx.method_25294(iconX, y + 7, iconX + 17, y + 23, Theme.withAlpha(mod.isEnabled() ? Theme.ACCENT : Theme.TEXT_DIM, mod.isEnabled() ? 58 : 28));
      String icon = mod.getName().substring(0, 1).toUpperCase(Locale.ROOT);
      ctx.method_51433(mc.field_1772, icon, iconX + 5, y + 11, mod.isEnabled() ? Theme.ACCENT : Theme.TEXT_DIM, true);
      ctx.method_51433(mc.field_1772, mod.getName(), this.panelX + 38, y + 11, 0xFF000000 | Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, enabled), false);
      String key = this.listening == mod ? "..." : (mod.getKey() >= 0 ? Keys.name(mod.getKey()) : "KEY");
      int keyW = mc.field_1772.method_1727(key);
      int keyBoxX = this.panelX + this.panelW - 138;
      ctx.method_25294(keyBoxX, y + 7, this.panelX + this.panelW - 91, y + 22, this.listening == mod ? 1716903355 : 1429486153);
      ctx.method_51433(mc.field_1772, key, this.panelX + this.panelW - 114 - keyW / 2, y + 11, this.listening == mod ? Theme.ACCENT : Theme.TEXT_DIM, false);
      int favoriteX = this.panelX + this.panelW - 82;
      String favoriteIcon = mod.isFavorite() ? "★" : "☆";
      int favoriteColor = mod.isFavorite() ? -11414 : Theme.TEXT_DIM;
      ctx.method_51433(mc.field_1772, favoriteIcon, favoriteX, y + 8, favoriteColor, true);
      int tx = this.panelX + this.panelW - 43;
      int ty = y + 8;
      ctx.method_25294(tx, ty, tx + 29, ty + 13, mod.isEnabled() ? Theme.ACCENT : -13355968);
      int knobX = mod.isEnabled() ? tx + 18 : tx + 3;
      ctx.method_25294(knobX, ty + 2, knobX + 8, ty + 11, -526342);
      if (!mod.getSettings().isEmpty()) {
         String expand = EXPANDED.contains(mod) ? "v" : ">";
         ctx.method_51433(mc.field_1772, expand, tx - 15, y + 11, Theme.ACCENT_DIM, false);
      }
   }

   private void toggleExpanded(Module mod) {
      if (!mod.getSettings().isEmpty() && !EXPANDED.remove(mod)) {
         EXPANDED.add(mod);
      }
   }

   private void drawSetting(class_332 ctx, Setting<?> setting, int y, int mx, int my, int x, int w, boolean modalOpen) {
      class_310 mc = class_310.method_1551();
      boolean over = !modalOpen && my >= this.viewportTop && my < this.visibleClipBottom && in(mx, my, x, y, w, 23);
      ctx.method_25294(x, y, x + w, y + 23 - 1, 889192448);
      if (over) {
         ctx.method_25294(x, y, x + w, y + 23 - 1, 620756991);
      }

      if (setting instanceof BoolSetting b) {
         ctx.method_51433(mc.field_1772, b.getName(), x + 10, y + 8, Theme.TEXT_DIM, false);
         int bx = x + w - 31;
         ctx.method_25294(bx, y + 5, bx + 22, y + 15, b.get() ? Theme.ACCENT : -13355968);
         ctx.method_25294(b.get() ? bx + 13 : bx + 2, y + 7, b.get() ? bx + 20 : bx + 9, y + 13, -526342);
         if (over && this.clickL) {
            b.toggle();
            Config.save();
            this.clickL = false;
         }
      } else if (setting instanceof NumberSetting n) {
         double t = n.max == n.min ? 0.0 : (n.get() - n.min) / (n.max - n.min);
         int barX = x + w - 112;
         ctx.method_25294(barX, y + 17, x + w - 8, y + 19, -13355968);
         ctx.method_25294(barX, y + 17, barX + (int)((w - 120) * t), y + 17, Theme.ACCENT);
         ctx.method_51433(mc.field_1772, n.getName(), x + 9, y + 6, Theme.TEXT_DIM, false);
         String val = n.display();
         ctx.method_51433(mc.field_1772, val, barX - 7 - mc.field_1772.method_1727(val), y + 6, Theme.TEXT, false);
         if (over && this.clickL) {
            this.slider = n;
            this.sliderX = barX;
            this.sliderW = w - (barX - x) - 8;
            this.clickL = false;
         }
      } else if (setting instanceof ModeSetting mode) {
         ctx.method_51433(mc.field_1772, mode.getName(), x + 9, y + 6, Theme.TEXT_DIM, false);
         String value = mode.get();
         ctx.method_51433(mc.field_1772, "‹  " + value + "  ›", x + w - 112, y + 6, Theme.ACCENT, false);
         if (over && this.clickL) {
            mode.cycle(1);
            Config.save();
            this.clickL = false;
         } else if (over && this.clickR) {
            mode.cycle(-1);
            Config.save();
            this.clickR = false;
         }
      } else if (setting instanceof ColorSetting color) {
         ctx.method_51433(mc.field_1772, color.getName(), x + 9, y + 6, Theme.TEXT_DIM, false);
         ctx.method_25294(x + w - 34, y + 6, x + w - 9, y + 18, 0xFF000000 | color.rgb());
         if (over && this.clickL) {
            try {
               ModuleManager.get(GUITheme.class).prepareColorEdit(color);
            } catch (Throwable var23) {
            }

            color.cycle(1);
            Config.save();
            this.clickL = false;
         } else if (over && this.clickR) {
            this.editingColor = color;
            this.messageDraft = String.format(Locale.ROOT, "%06X", color.rgb());

            try {
               ModuleManager.get(GUITheme.class).prepareColorEdit(color);
            } catch (Throwable var22) {
            }

            this.searchFocus = false;
            this.clickR = false;
         }
      } else if (setting instanceof StringSetting string) {
         String value = string.get();
         if (value.length() > 26) {
            value = value.substring(0, 25) + "…";
         }

         ctx.method_51433(mc.field_1772, string.getName(), x + 9, y + 6, Theme.TEXT_DIM, false);
         ctx.method_51433(mc.field_1772, value, x + w - 9 - mc.field_1772.method_1727(value), y + 6, Theme.ACCENT, false);
         if (over && this.clickL) {
            this.editingString = string;
            this.messageDraft = string.get();
            this.searchFocus = false;
            this.clickL = false;
         }
      } else if (setting instanceof MessageListSetting messages) {
         String valuex = messages.get().size() + " gespeichert · bearbeiten";
         ctx.method_51433(mc.field_1772, messages.getName(), x + 9, y + 6, Theme.TEXT_DIM, false);
         ctx.method_51433(mc.field_1772, valuex, x + w - 9 - mc.field_1772.method_1727(valuex), y + 6, Theme.ACCENT, false);
         if (over && this.clickL) {
            this.editingMessages = messages;
            this.messageDraft = "";
            this.searchFocus = false;
            this.clickL = false;
         }
      } else if (setting instanceof ListSetting list) {
         String valuex = list.get().size() + " Einträge";
         ctx.method_51433(mc.field_1772, list.getName(), x + 9, y + 6, Theme.TEXT_DIM, false);
         ctx.method_51433(mc.field_1772, valuex, x + w - 9 - mc.field_1772.method_1727(valuex), y + 6, Theme.TEXT_DIM, false);
      }
   }

   private void drawTextModal(class_332 ctx, int mx, int my) {
      class_310 mc = class_310.method_1551();
      int mw = Math.min(340, this.field_22789 - 24);
      int mh = 122;
      int mx0 = (this.field_22789 - mw) / 2;
      int my0 = (this.field_22790 - mh) / 2;
      ctx.method_25294(mx0 - 2, my0 - 2, mx0 + mw + 2, my0 + mh + 2, -1610612736);
      ctx.method_25294(mx0, my0, mx0 + mw, my0 + mh, -266856414);
      ctx.method_25294(mx0, my0, mx0 + mw, my0 + 2, Theme.ACCENT);
      ctx.method_51433(
         mc.field_1772,
         this.editingColor != null ? "FARBE · #RRGGBB" : (this.editingString != null ? "TEXT BEARBEITEN" : "AUTOTEXT · NACHRICHT HINZUFÜGEN"),
         mx0 + 10,
         my0 + 10,
         Theme.TEXT,
         true
      );
      ctx.method_25294(mx0 + 10, my0 + 29, mx0 + mw - 10, my0 + 49, -16250867);
      String draftView = (this.editingColor != null ? "#" : "") + this.messageDraft + (System.currentTimeMillis() / 500L % 2L == 0L ? "_" : "");
      ctx.method_51433(mc.field_1772, draftView, mx0 + 15, my0 + 36, Theme.ACCENT, false);
      String instruction = this.editingColor == null && this.editingString == null ? "Enter: hinzufügen · Esc: schließen" : "Enter: speichern · Esc: abbrechen";
      ctx.method_51433(mc.field_1772, instruction, mx0 + 10, my0 + 59, Theme.TEXT_DIM, false);
      List<String> existing = this.editingMessages == null ? List.of() : this.editingMessages.get();
      ctx.method_51433(
         mc.field_1772,
         this.editingColor == null && this.editingString == null ? "Gespeichert: " + existing.size() + " Nachrichten" : "Änderungen werden lokal gespeichert",
         mx0 + 10,
         my0 + 76,
         Theme.TEXT_DIM,
         false
      );
      if (this.editingMessages != null && !existing.isEmpty()) {
         String last = existing.get(existing.size() - 1);
         if (last.length() > 32) {
            last = last.substring(0, 31) + "…";
         }

         String remove = "Letzte: " + last + " · Rechtsklick löscht";
         ctx.method_51433(mc.field_1772, remove, mx0 + 10, my0 + 94, Theme.TEXT_DIM, false);
         if (this.clickR && in(mx, my, mx0, my0 + 88, mw, 24)) {
            this.editingMessages.removeMessage(existing.get(existing.size() - 1));
            Config.save();
            this.notice("Letzte Nachricht gelöscht");
            this.clickR = false;
         }
      }
   }

   private void drawNotice(class_332 ctx, String message, int color) {
      class_310 mc = class_310.method_1551();
      int w = Math.min(this.field_22789 - 24, mc.field_1772.method_1727(message) + 20);
      int x = (this.field_22789 - w) / 2;
      int y = this.viewportBottom + 1;
      ctx.method_25294(x, y, x + w, y + 16, -435088871);
      ctx.method_25294(x, y, x + 2, y + 16, color);
      ctx.method_51433(mc.field_1772, message, x + 8, y + 5, color, false);
   }

   private static boolean in(int mx, int my, int x, int y, int w, int h) {
      return mx >= x && mx < x + w && my >= y && my < y + h;
   }
}
