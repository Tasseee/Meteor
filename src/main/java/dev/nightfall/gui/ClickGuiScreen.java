package dev.nightfall.gui;

import dev.nightfall.Nightfall;
import dev.nightfall.config.Config;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.*;
import dev.nightfall.modules.GUITheme;
import dev.nightfall.util.Keys;
import dev.nightfall.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Full-height vertical accordion ClickGUI. Categories contain modules, and each module
 * reveals its settings in-place. There is deliberately no secondary/side settings pane.
 */
public class ClickGuiScreen extends Screen {
    private static final int HEADER_TOP = 52;
    private static final int CATEGORY_H = 32;
    private static final int MODULE_H = 29;
    private static final int SETTING_H = 23;
    private static final int GAP = 6;
    private static final int FOOTER_H = 26;
    private static final int SIDEBAR_W = 126;
    private static final int CARD_GAP = 8;

    private static final Set<Category> OPEN_CATEGORIES = new HashSet<>();
    private static final Set<Module> EXPANDED = new HashSet<>();
    private static final Map<Category, Float> CATEGORY_PROGRESS = new EnumMap<>(Category.class);
    private static final Map<Module, Float> MODULE_PROGRESS = new HashMap<>();
    private static final Map<Module, Float> ENABLE_PROGRESS = new HashMap<>();
    private static final Map<Module, int[]> CARD_OFFSETS = new HashMap<>();
    private static String search = "";

    private final List<Category> cats = new ArrayList<>();
    private Category selectedCategory;
    private final Keys.Poller poller = new Keys.Poller();
    private boolean prevL, prevR, prevM, clickL, clickR, clickM;
    private boolean searchFocus;
    private Module listening;
    private String notice = "";
    private long noticeUntil;
    private NumberSetting slider;
    private int sliderX, sliderW;
    private int visibleClipBottom;
    private MessageListSetting editingMessages;
    private StringSetting editingString;
    private ColorSetting editingColor;
    private String messageDraft = "";
    private float scroll;
    private int panelX, panelW, viewportTop, viewportBottom;
    private int panelOffsetX;
    private boolean draggingPanel;
    private Module draggingCard;
    private int dragMouseX, dragMouseY;

    public ClickGuiScreen() {
        super(Text.literal("Nightfall"));
        for (Category c : Category.values()) {
            if (c != Category.COMBAT && !ModuleManager.byCategory(c).isEmpty()) cats.add(c);
        }
        if (!cats.isEmpty()) selectedCategory = cats.get(0);
        long h = MinecraftClient.getInstance().getWindow().getHandle();
        prevL = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        prevR = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        prevM = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        poller.sync(h);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Config.save(); }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseY < viewportTop || mouseY > viewportBottom) return false;
        scroll = Math.max(0, scroll - (float) verticalAmount * 34.0f);
        return true;
    }

    private List<Module> visible(Category category) {
        List<Module> all = ModuleManager.byCategory(category);
        if (search.isEmpty()) return all;
        List<Module> out = new ArrayList<>();
        for (Module m : all) {
            // Keep combat/weapon controls outside this UI redesign.
            if (m.getName().equals("WeaponAnimations")) continue;
            String q = search.toLowerCase(Locale.ROOT);
            if (m.getName().toLowerCase(Locale.ROOT).contains(q)
                    || m.getDescription().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        }
        return out;
    }

    private <K> float ease(Map<K, Float> values, K key, boolean target, float delta) {
        float wanted = target ? 1.0f : 0.0f;
        float current = values.getOrDefault(key, wanted);
        double speed = 10.0;
        try { speed = ModuleManager.get(GUITheme.class).getAnimationSpeed(); } catch (Throwable ignored) { }
        float factor = speed >= 100.0 ? 1.0f : Math.min(1.0f, Math.max(0.04f, delta * (float) speed));
        current += (wanted - current) * factor;
        if (Math.abs(wanted - current) < 0.008f) current = wanted;
        values.put(key, current);
        return current;
    }

    private boolean categoryOpen(Category c) { return OPEN_CATEGORIES.contains(c) || !search.isEmpty(); }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        if (listening != null || editingColor != null || editingMessages != null || editingString != null || searchFocus) {
            onKey(keyCode);
            return true;
        }
        if (keyCode == Config.guiKey) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    private void onKey(int k) {
        if (listening != null) {
            if (k == GLFW.GLFW_KEY_ESCAPE) { listening = null; return; }
            if (k == GLFW.GLFW_KEY_BACKSPACE) { listening.setKey(-1); notice("Keybind entfernt: " + listening.getName()); listening = null; Config.save(); return; }
            String err = ModuleManager.assignKey(listening, k);
            notice(err != null ? err : listening.getName() + " = " + Keys.name(k));
            if (err == null) Config.save();
            listening = null;
            return;
        }
        if (editingColor != null) {
            if (k == GLFW.GLFW_KEY_ESCAPE) { editingColor = null; messageDraft = ""; return; }
            if (k == GLFW.GLFW_KEY_BACKSPACE) { if (!messageDraft.isEmpty()) messageDraft = messageDraft.substring(0, messageDraft.length() - 1); return; }
            if (k == GLFW.GLFW_KEY_ENTER) {
                String hex = messageDraft.replace("#", "").trim();
                try {
                    if (hex.length() != 6) throw new NumberFormatException();
                    editingColor.set(Integer.parseInt(hex, 16)); Config.save(); notice("Farbe gespeichert: #" + hex.toUpperCase(Locale.ROOT));
                } catch (NumberFormatException ex) { notice("Bitte genau 6 Hex-Zeichen eingeben"); }
                editingColor = null; messageDraft = ""; return;
            }
            if (k >= GLFW.GLFW_KEY_0 && k <= GLFW.GLFW_KEY_9 && messageDraft.length() < 6) { messageDraft += (char) ('0' + k - GLFW.GLFW_KEY_0); return; }
            if (k >= GLFW.GLFW_KEY_A && k <= GLFW.GLFW_KEY_F && messageDraft.length() < 6) { messageDraft += (char) ('A' + k - GLFW.GLFW_KEY_A); return; }
            return;
        }
        if (editingMessages != null || editingString != null) {
            if (k == GLFW.GLFW_KEY_ESCAPE) { editingMessages = null; editingString = null; messageDraft = ""; return; }
            if (k == GLFW.GLFW_KEY_BACKSPACE) { if (!messageDraft.isEmpty()) messageDraft = messageDraft.substring(0, messageDraft.length() - 1); return; }
            if (k == GLFW.GLFW_KEY_ENTER) {
                if (editingString != null) {
                    editingString.set(messageDraft); Config.save(); notice("Einstellung gespeichert"); editingString = null; messageDraft = ""; return;
                }
                if (editingMessages != null && !messageDraft.isBlank()) {
                    boolean added = editingMessages.addMessage(messageDraft.trim());
                    notice(added ? "Nachricht hinzugefügt" : "Leer oder bereits vorhanden"); Config.save();
                }
                messageDraft = ""; return;
            }
            if (k == GLFW.GLFW_KEY_SPACE) { if (messageDraft.length() < 256) messageDraft += " "; return; }
            if (k >= GLFW.GLFW_KEY_A && k <= GLFW.GLFW_KEY_Z && messageDraft.length() < 256) {
                boolean shift = GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                messageDraft += (char) ((shift ? 'A' : 'a') + (k - GLFW.GLFW_KEY_A)); return;
            }
            if (k >= GLFW.GLFW_KEY_0 && k <= GLFW.GLFW_KEY_9 && messageDraft.length() < 256) { messageDraft += (char) ('0' + k - GLFW.GLFW_KEY_0); return; }
            if (k == GLFW.GLFW_KEY_MINUS && messageDraft.length() < 256) { messageDraft += "-"; return; }
            if (k == GLFW.GLFW_KEY_PERIOD && messageDraft.length() < 256) { messageDraft += "."; return; }
            if (k == GLFW.GLFW_KEY_COMMA && messageDraft.length() < 256) { messageDraft += ","; return; }
            if (k == GLFW.GLFW_KEY_SLASH && messageDraft.length() < 256) { messageDraft += "/"; return; }
            return;
        }
        if (!searchFocus) return;
        if (k == GLFW.GLFW_KEY_BACKSPACE) { if (!search.isEmpty()) search = search.substring(0, search.length() - 1); scroll = 0; }
        else if (k >= GLFW.GLFW_KEY_A && k <= GLFW.GLFW_KEY_Z) { search += (char) ('a' + k - GLFW.GLFW_KEY_A); scroll = 0; }
        else if (k >= GLFW.GLFW_KEY_0 && k <= GLFW.GLFW_KEY_9) { search += (char) ('0' + k - GLFW.GLFW_KEY_0); scroll = 0; }
        else if (k == GLFW.GLFW_KEY_SPACE) { search += ' '; scroll = 0; }
        else if (k == GLFW.GLFW_KEY_ENTER) searchFocus = false;
        else if (k == GLFW.GLFW_KEY_ESCAPE) { search = ""; searchFocus = false; scroll = 0; }
    }

    private void notice(String s) { notice = s; noticeUntil = System.currentTimeMillis() + 2600; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long handle = mc.getWindow().getHandle();
        boolean l = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean m = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        clickL = l && !prevL; clickR = r && !prevR; clickM = m && !prevM;
        prevL = l; prevR = r; prevM = m;
        if (!l) draggingCard = null;

        ctx.fill(0, 0, width, height, Theme.BG_DIM);
        ctx.fill(0, 0, width, 2, Theme.ACCENT);
        ctx.fill(0, height - 2, width, height, Theme.ACCENT_DIM);
        double guiScale = 1.0;
        try { guiScale = ModuleManager.get(GUITheme.class).getGuiScale(); } catch (Throwable ignored) { }
        panelW = Math.min(width - 36, Math.max(Math.min(540, width - 36), (int) (860 * guiScale)));
        if (draggingPanel && l) panelOffsetX = mx - width / 2;
        if (!l) draggingPanel = false;
        int maxOffset = Math.max(0, (width - panelW) / 2 - 8);
        panelOffsetX = Math.max(-maxOffset, Math.min(maxOffset, panelOffsetX));
        panelX = (width - panelW) / 2 + panelOffsetX;
        viewportTop = HEADER_TOP;
        viewportBottom = Math.max(viewportTop + 40, height - FOOTER_H - 8);

        // Fixed app-style top bar with a clear brand lockup.
        ctx.fill(panelX - 1, 4, panelX + panelW + 1, 42, Theme.withAlpha(Theme.ACCENT, 65));
        ctx.fill(panelX, 5, panelX + panelW, 41, Theme.HUD_PANEL);
        ctx.fill(panelX, 5, panelX + 3, 41, Theme.ACCENT);
        ctx.fill(panelX + 3, 5, panelX + panelW, 6, Theme.withAlpha(Theme.ACCENT, 130));
        ctx.drawText(mc.textRenderer, "NIGHTFALL", panelX + 13, 10, Theme.TEXT, true);
        ctx.drawText(mc.textRenderer, "PREMIUM CLIENT  /  MODULE CONTROL", panelX + 13, 24, Theme.TEXT_DIM, false);
        ctx.drawText(mc.textRenderer, Nightfall.VERSION, panelX + 132, 11, Theme.ACCENT, false);
        int searchW = Math.min(240, panelW - 170);
        int searchX = panelX + panelW - searchW - 10;
        ctx.fill(searchX, 9, searchX + searchW, 31, 0xE9101118);
        ctx.fill(searchX, 30, searchX + searchW, 31, searchFocus ? Theme.ACCENT : Theme.ACCENT_DIM);
        String searchText = search.isEmpty() && !searchFocus ? "Module suchen..." : search + (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
        ctx.drawText(mc.textRenderer, searchText, searchX + 9, 17, search.isEmpty() ? Theme.TEXT_DIM : Theme.TEXT, false);
        if (clickL && in(mx, my, searchX, 9, searchW, 22)) { searchFocus = true; draggingPanel = false; clickL = false; }
        else if (clickL && in(mx, my, panelX, 5, panelW, 37)) { searchFocus = false; draggingPanel = true; clickL = false; }
        else if (clickL && searchFocus) searchFocus = false;

        if (!l) slider = null;
        if (slider != null && sliderW > 0) {
            double t = (mx - sliderX) / (double) sliderW;
            slider.set(slider.min + Math.max(0, Math.min(1, t)) * (slider.max - slider.min));
        }

        Module hovered = null;
        boolean modalOpen = editingMessages != null || editingString != null || editingColor != null;
        if (modalOpen) { clickL = false; clickM = false; }

        if (!search.isEmpty() && (selectedCategory == null || visible(selectedCategory).isEmpty())) {
            for (Category category : cats) {
                if (!visible(category).isEmpty()) { selectedCategory = category; break; }
            }
        }
        List<Module> displayedModules = selectedCategory == null ? List.of() : visible(selectedCategory);
        int contentX = panelX + SIDEBAR_W + 8;
        int contentW = Math.max(120, panelW - SIDEBAR_W - 16);
        int columns = contentW >= 500 ? 2 : 1;
        int cardW = (contentW - (columns - 1) * CARD_GAP) / columns;
        List<Float> cardHeights = new ArrayList<>();
        for (Module mod : displayedModules) {
            float mp = ease(MODULE_PROGRESS, mod, EXPANDED.contains(mod), delta);
            cardHeights.add(MODULE_H + mod.getSettings().size() * SETTING_H * mp);
        }
        List<Integer> rowStarts = new ArrayList<>();
        List<Integer> rowHeights = new ArrayList<>();
        float totalHeight = 0;
        for (int i = 0; i < displayedModules.size(); i += columns) {
            rowStarts.add(i);
            float rowH = cardHeights.get(i);
            if (i + 1 < displayedModules.size() && columns == 2) rowH = Math.max(rowH, cardHeights.get(i + 1));
            rowHeights.add((int) Math.ceil(rowH));
            totalHeight += rowH + CARD_GAP;
        }
        float maxScroll = Math.max(0, totalHeight - (viewportBottom - viewportTop));
        scroll = Math.max(0, Math.min(maxScroll, scroll));

        // Sidebar and module workspace: a fixed category rail plus independent glass cards.
        int sideX = panelX;
        ctx.fill(sideX - 1, viewportTop - 5, sideX + SIDEBAR_W + 2, viewportBottom + 1, Theme.withAlpha(Theme.ACCENT, 54));
        ctx.fill(sideX, viewportTop - 4, sideX + SIDEBAR_W, viewportBottom, Theme.HUD_PANEL);
        ctx.fill(sideX, viewportTop - 4, sideX + 2, viewportBottom, Theme.ACCENT_DIM);
        ctx.drawText(mc.textRenderer, "CATEGORIES", sideX + 11, viewportTop + 7, Theme.TEXT_DIM, true);
        int navY = viewportTop + 24;
        for (Category category : cats) {
            boolean active = category == selectedCategory;
            boolean over = in(mx, my, sideX + 5, navY, SIDEBAR_W - 10, 27);
            int navBg = active ? Theme.withAlpha(Theme.ACCENT, 76) : over ? Theme.ROW_HOVER : 0x22000000;
            ctx.fill(sideX + 5, navY, sideX + SIDEBAR_W - 5, navY + 25, navBg);
            if (active) ctx.fill(sideX + 5, navY, sideX + 7, navY + 25, Theme.ACCENT);
            ctx.fill(sideX + 12, navY + 6, sideX + 29, navY + 22, Theme.withAlpha(Theme.ACCENT, active ? 90 : 35));
            ctx.drawText(mc.textRenderer, categoryIcon(category), sideX + 18, navY + 11, active ? Theme.TEXT : Theme.ACCENT, true);
            String label = category.display.toUpperCase(Locale.ROOT);
            ctx.drawText(mc.textRenderer, label, sideX + 36, navY + 10, active ? Theme.TEXT : Theme.TEXT_DIM, active);
            String count = String.valueOf(visible(category).size());
            ctx.drawText(mc.textRenderer, count, sideX + SIDEBAR_W - 15 - mc.textRenderer.getWidth(count), navY + 10, Theme.TEXT_DIM, false);
            if (clickL && in(mx, my, sideX + 5, navY, SIDEBAR_W - 10, 27) && !modalOpen) {
                selectedCategory = category;
                scroll = 0;
                clickL = false;
            }
            navY += 30;
        }
        ctx.drawText(mc.textRenderer, "NIGHTFALL", sideX + 11, viewportBottom - 25, Theme.ACCENT, true);
        ctx.drawText(mc.textRenderer, "CLIENT UI", sideX + 11, viewportBottom - 14, Theme.TEXT_DIM, false);

        ctx.fill(contentX - 1, viewportTop - 5, contentX + contentW + 1, viewportBottom + 1, Theme.withAlpha(Theme.ACCENT, 48));
        ctx.fill(contentX, viewportTop - 4, contentX + contentW, viewportBottom, Theme.HUD_PANEL);
        ctx.fill(contentX, viewportTop - 4, contentX + contentW, viewportTop - 2, Theme.ACCENT_DIM);
        String categoryTitle = selectedCategory == null ? "MODULES" : selectedCategory.display.toUpperCase(Locale.ROOT);
        ctx.drawText(mc.textRenderer, categoryTitle, contentX + 10, viewportTop + 7, Theme.TEXT, true);
        ctx.drawText(mc.textRenderer, displayedModules.size() + " MODULE", contentX + contentW - 83, viewportTop + 7, Theme.TEXT_DIM, false);
        int cardsTop = viewportTop + 23;
        ctx.enableScissor(contentX, cardsTop, contentX + contentW, viewportBottom);
        float y = cardsTop - scroll;
        for (int ri = 0; ri < rowStarts.size(); ri++) {
            int first = rowStarts.get(ri);
            for (int col = 0; col < columns && first + col < displayedModules.size(); col++) {
                Module mod = displayedModules.get(first + col);
                int[] offset = CARD_OFFSETS.computeIfAbsent(mod, key -> new int[]{0, 0});
                if (draggingCard == mod && l) {
                    offset[0] += mx - dragMouseX;
                    offset[1] += my - dragMouseY;
                    dragMouseX = mx;
                    dragMouseY = my;
                }
                int cardX = contentX + col * (cardW + CARD_GAP) + offset[0];
                int cardY = Math.round(y) + offset[1];
                float mp = MODULE_PROGRESS.getOrDefault(mod, 0f);
                int cardH = (int) Math.ceil(cardHeights.get(first + col));
                boolean cardVisible = cardY + cardH >= cardsTop && cardY <= viewportBottom;
                if (!cardVisible) continue;
                boolean cardHovered = in(mx, my, cardX, cardY, cardW, cardH) && my >= cardsTop && my < viewportBottom;
                ctx.fill(cardX - 1, cardY - 1, cardX + cardW + 1, cardY + cardH + 1, Theme.withAlpha(Theme.ACCENT, cardHovered ? 112 : 52));
                ctx.fill(cardX, cardY, cardX + cardW, cardY + cardH, Theme.withAlpha(Theme.PANEL, 248));
                ctx.fill(cardX, cardY, cardX + cardW, cardY + 1, Theme.withAlpha(Theme.ACCENT, 165));
                int savedX = panelX, savedW = panelW;
                panelX = cardX;
                panelW = cardW;
                boolean rowHovered = in(mx, my, cardX, cardY, cardW, MODULE_H) && my >= cardsTop && my < viewportBottom;
                drawModuleRow(ctx, mod, cardY, mx, my, rowHovered, delta);
                ctx.fill(cardX + cardW - 19, cardY + 3, cardX + cardW - 8, cardY + 5, Theme.withAlpha(Theme.ACCENT, 190));
                if (clickL && in(mx, my, cardX, cardY, cardW, 7) && !modalOpen) {
                    draggingCard = mod;
                    dragMouseX = mx;
                    dragMouseY = my;
                    clickL = false;
                }
                if (rowHovered && !modalOpen) {
                    int toggleX = cardX + cardW - 47;
                    if (clickL && mx >= toggleX) { mod.toggle(); Config.save(); clickL = false; }
                    else if (clickL) { toggleExpanded(mod); clickL = false; }
                    else if (clickR) { toggleExpanded(mod); clickR = false; }
                    else if (clickM) { listening = mod; searchFocus = false; notice("Taste drücken für " + mod.getName() + " · Backspace löscht"); clickM = false; }
                }
                if (rowHovered) hovered = mod;
                float settingsH = mod.getSettings().size() * SETTING_H * mp;
                if (settingsH > 0.5f) {
                    visibleClipBottom = Math.min(cardY + cardH, viewportBottom);
                    ctx.enableScissor(cardX + 5, cardY + MODULE_H, cardX + cardW - 5, Math.min(viewportBottom, cardY + cardH));
                    for (int si = 0; si < mod.getSettings().size(); si++) {
                        int sy = cardY + MODULE_H + si * SETTING_H;
                        if (sy + SETTING_H >= cardsTop && sy < viewportBottom) {
                            drawSetting(ctx, mod.getSettings().get(si), sy, mx, my, cardX + 6, cardW - 12, modalOpen);
                        }
                    }
                    ctx.disableScissor();
                }
                panelX = savedX;
                panelW = savedW;
            }
            y += rowHeights.get(ri) + CARD_GAP;
        }
        ctx.disableScissor();

        if (totalHeight > viewportBottom - cardsTop + 1) {
            int trackX = contentX + contentW - 3;
            int trackH = viewportBottom - cardsTop;
            int thumbH = Math.max(24, (int) (trackH * (trackH / Math.max(1, totalHeight))));
            int thumbY = cardsTop + (int) ((trackH - thumbH) * (scroll / Math.max(1, maxScroll)));
            ctx.fill(trackX, cardsTop, trackX + 2, viewportBottom, 0x44FFFFFF);
            ctx.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, Theme.ACCENT);
        }

        if (hovered != null && !modalOpen) {
            String desc = hovered.getDescription();
            int max = panelW - SIDEBAR_W - 24;
            while (mc.textRenderer.getWidth(desc) > max && desc.length() > 8) desc = desc.substring(0, desc.length() - 2) + "…";
            ctx.drawText(mc.textRenderer, desc, panelX + SIDEBAR_W + 8, height - 17, Theme.TEXT_DIM, false);
        }

        if (modalOpen) drawTextModal(ctx, mx, my);
        if (listening != null) {
            String msg = "Taste für " + listening.getName() + " drücken · Backspace = löschen · Esc = abbrechen";
            drawNotice(ctx, msg, 0xFFFFD27A);
        } else if (System.currentTimeMillis() < noticeUntil) drawNotice(ctx, notice, 0xFFFFD27A);

        String footer = "Klick: Optionen  ·  Rechtsklick: Settings  ·  Mittelklick: Keybind  ·  Mausrad: Scroll";
        ctx.fill(panelX - 1, height - 23, panelX + panelW + 1, height - 3, Theme.withAlpha(Theme.ACCENT, 60));
        ctx.fill(panelX, height - 22, panelX + panelW, height - 4, Theme.HUD_PANEL);
        ctx.drawText(mc.textRenderer, footer, panelX + 10, height - 16, Theme.TEXT_DIM, false);
        ctx.drawText(mc.textRenderer, Nightfall.VERSION, panelX + panelW - 10 - mc.textRenderer.getWidth(Nightfall.VERSION), height - 16, Theme.ACCENT, false);
    }

    private void drawCategoryHeader(DrawContext ctx, Category c, int y, int mx, int my, float progress) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean open = categoryOpen(c);
        boolean hover = in(mx, my, panelX, y, panelW, CATEGORY_H);
        int top = Theme.DESIGN.equals("Flat") ? Theme.HEADER_BOT : Theme.HEADER_TOP;
        ctx.fillGradient(panelX, y, panelX + panelW, y + CATEGORY_H - 1, top, Theme.HEADER_BOT);
        if (hover) ctx.fill(panelX, y, panelX + panelW, y + CATEGORY_H - 1, Theme.ROW_HOVER);
        ctx.fill(panelX, y + CATEGORY_H - 2, panelX + panelW, y + CATEGORY_H - 1, Theme.withAlpha(Theme.ACCENT, open ? 235 : 105));
        if (open) ctx.fill(panelX, y, panelX + 2, y + CATEGORY_H - 1, Theme.ACCENT);
        String icon = categoryIcon(c);
        ctx.fill(panelX + 9, y + 8, panelX + 27, y + 25, Theme.withAlpha(Theme.ACCENT, 45));
        ctx.drawText(mc.textRenderer, icon, panelX + 15, y + 13, Theme.ACCENT, true);
        ctx.drawText(mc.textRenderer, c.display.toUpperCase(Locale.ROOT), panelX + 36, y + 13, Theme.TEXT, true);
        String count = String.valueOf(visible(c).size());
        int countW = mc.textRenderer.getWidth(count);
        ctx.drawText(mc.textRenderer, count, panelX + panelW - 38 - countW, y + 13, Theme.TEXT_DIM, false);
        String chevron = open ? "v" : ">";
        ctx.drawText(mc.textRenderer, chevron, panelX + panelW - 22, y + 13, Theme.ACCENT, true);
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

    private void drawModuleRow(DrawContext ctx, Module mod, int y, int mx, int my, boolean hover, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        float enabled = ease(ENABLE_PROGRESS, mod, mod.isEnabled(), delta);
        int rowBg = hover ? Theme.ROW_HOVER : (0x18000000);
        ctx.fill(panelX + 3, y, panelX + panelW - 3, y + MODULE_H - 1, rowBg);
        if (hover) ctx.fill(panelX + 3, y, panelX + panelW - 3, y + 1, Theme.withAlpha(Theme.ACCENT, 120));
        if (enabled > 0.01f) {
            int alpha = Math.max(0, Math.min(155, (int) (enabled * 155)));
            ctx.fill(panelX + 3, y, panelX + 5, y + MODULE_H - 1, Theme.withAlpha(Theme.ACCENT, alpha));
        }
        int iconX = panelX + 11;
        ctx.fill(iconX, y + 7, iconX + 17, y + 23, Theme.withAlpha(mod.isEnabled() ? Theme.ACCENT : Theme.TEXT_DIM, mod.isEnabled() ? 58 : 28));
        String icon = mod.getName().substring(0, 1).toUpperCase(Locale.ROOT);
        ctx.drawText(mc.textRenderer, icon, iconX + 5, y + 11, mod.isEnabled() ? Theme.ACCENT : Theme.TEXT_DIM, true);
        ctx.drawText(mc.textRenderer, mod.getName(), panelX + 38, y + 11, (0xFF000000 | Theme.lerp(Theme.TEXT_DIM, Theme.TEXT, enabled)), false);

        String key = listening == mod ? "..." : (mod.getKey() >= 0 ? Keys.name(mod.getKey()) : "");
        if (!key.isEmpty()) {
            int keyW = mc.textRenderer.getWidth(key);
            ctx.drawText(mc.textRenderer, key, panelX + panelW - 73 - keyW, y + 9, Theme.TEXT_DIM, false);
        }
        // Compact toggle at the far right: independent from the row's expand/collapse action.
        int tx = panelX + panelW - 43;
        int ty = y + 8;
        ctx.fill(tx - 1, ty - 1, tx + 30, ty + 14, Theme.withAlpha(mod.isEnabled() ? Theme.ACCENT : Theme.TEXT_DIM, 70));
        ctx.fill(tx, ty, tx + 29, ty + 13, mod.isEnabled() ? Theme.ACCENT : 0xFF343440);
        int knobX = mod.isEnabled() ? tx + 18 : tx + 3;
        ctx.fill(knobX, ty + 2, knobX + 8, ty + 11, 0xFFF7F7FA);
        if (!mod.getSettings().isEmpty()) {
            String expand = EXPANDED.contains(mod) ? "v" : ">";
            ctx.drawText(mc.textRenderer, expand, tx - 15, y + 11, Theme.ACCENT_DIM, false);
        }
    }

    private void toggleExpanded(Module mod) {
        if (!mod.getSettings().isEmpty()) {
            if (!EXPANDED.remove(mod)) EXPANDED.add(mod);
        }
    }

    private void drawSetting(DrawContext ctx, Setting<?> setting, int y, int mx, int my, int x, int w, boolean modalOpen) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean over = !modalOpen && my >= viewportTop && my < visibleClipBottom && in(mx, my, x, y, w, SETTING_H);
        ctx.fill(x, y, x + w, y + SETTING_H - 1, 0x35000000);
        if (over) ctx.fill(x, y, x + w, y + SETTING_H - 1, 0x24FFFFFF);

        if (setting instanceof BoolSetting b) {
            ctx.drawText(mc.textRenderer, b.getName(), x + 10, y + 8, Theme.TEXT_DIM, false);
            int bx = x + w - 31;
            ctx.fill(bx, y + 5, bx + 22, y + 15, b.get() ? Theme.ACCENT : 0xFF343440);
            ctx.fill(b.get() ? bx + 13 : bx + 2, y + 7, b.get() ? bx + 20 : bx + 9, y + 13, 0xFFF7F7FA);
            if (over && clickL) { b.toggle(); Config.save(); clickL = false; }
        } else if (setting instanceof NumberSetting n) {
            double t = n.max == n.min ? 0 : (n.get() - n.min) / (n.max - n.min);
            int barX = x + w - 112;
            ctx.fill(barX, y + 17, x + w - 8, y + 19, 0xFF343440);
            ctx.fill(barX, y + 17, barX + (int) ((w - 120) * t), y + 17, Theme.ACCENT);
            ctx.drawText(mc.textRenderer, n.getName(), x + 9, y + 6, Theme.TEXT_DIM, false);
            String val = n.display();
            ctx.drawText(mc.textRenderer, val, barX - 7 - mc.textRenderer.getWidth(val), y + 6, Theme.TEXT, false);
            if (over && clickL) { slider = n; sliderX = barX; sliderW = w - (barX - x) - 8; clickL = false; }
        } else if (setting instanceof ModeSetting mode) {
            ctx.drawText(mc.textRenderer, mode.getName(), x + 9, y + 6, Theme.TEXT_DIM, false);
            String value = mode.get();
            ctx.drawText(mc.textRenderer, "‹  " + value + "  ›", x + w - 112, y + 6, Theme.ACCENT, false);
            if (over && clickL) { mode.cycle(1); Config.save(); clickL = false; }
            else if (over && clickR) { mode.cycle(-1); Config.save(); clickR = false; }
        } else if (setting instanceof ColorSetting color) {
            ctx.drawText(mc.textRenderer, color.getName(), x + 9, y + 6, Theme.TEXT_DIM, false);
            ctx.fill(x + w - 34, y + 6, x + w - 9, y + 18, 0xFF000000 | color.rgb());
            if (over && clickL) {
                try { ModuleManager.get(GUITheme.class).prepareColorEdit(color); } catch (Throwable ignored) { }
                color.cycle(1); Config.save(); clickL = false;
            } else if (over && clickR) {
                editingColor = color; messageDraft = String.format(Locale.ROOT, "%06X", color.rgb());
                try { ModuleManager.get(GUITheme.class).prepareColorEdit(color); } catch (Throwable ignored) { }
                searchFocus = false; clickR = false;
            }
        } else if (setting instanceof StringSetting string) {
            String value = string.get();
            if (value.length() > 26) value = value.substring(0, 25) + "…";
            ctx.drawText(mc.textRenderer, string.getName(), x + 9, y + 6, Theme.TEXT_DIM, false);
            ctx.drawText(mc.textRenderer, value, x + w - 9 - mc.textRenderer.getWidth(value), y + 6, Theme.ACCENT, false);
            if (over && clickL) { editingString = string; messageDraft = string.get(); searchFocus = false; clickL = false; }
        } else if (setting instanceof MessageListSetting messages) {
            String value = messages.get().size() + " gespeichert · bearbeiten";
            ctx.drawText(mc.textRenderer, messages.getName(), x + 9, y + 6, Theme.TEXT_DIM, false);
            ctx.drawText(mc.textRenderer, value, x + w - 9 - mc.textRenderer.getWidth(value), y + 6, Theme.ACCENT, false);
            if (over && clickL) { editingMessages = messages; messageDraft = ""; searchFocus = false; clickL = false; }
        } else if (setting instanceof ListSetting list) {
            String value = list.get().size() + " Einträge";
            ctx.drawText(mc.textRenderer, list.getName(), x + 9, y + 6, Theme.TEXT_DIM, false);
            ctx.drawText(mc.textRenderer, value, x + w - 9 - mc.textRenderer.getWidth(value), y + 6, Theme.TEXT_DIM, false);
        }
    }

    private void drawTextModal(DrawContext ctx, int mx, int my) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int mw = Math.min(340, width - 24), mh = 122;
        int mx0 = (width - mw) / 2, my0 = (height - mh) / 2;
        ctx.fill(mx0 - 2, my0 - 2, mx0 + mw + 2, my0 + mh + 2, 0xA0000000);
        ctx.fill(mx0, my0, mx0 + mw, my0 + mh, 0xF0181822);
        ctx.fill(mx0, my0, mx0 + mw, my0 + 2, Theme.ACCENT);
        ctx.drawText(mc.textRenderer, editingColor != null ? "FARBE · #RRGGBB" : editingString != null ? "TEXT BEARBEITEN" : "AUTOTEXT · NACHRICHT HINZUFÜGEN", mx0 + 10, my0 + 10, Theme.TEXT, true);
        ctx.fill(mx0 + 10, my0 + 29, mx0 + mw - 10, my0 + 49, 0xFF08080D);
        String draftView = (editingColor != null ? "#" : "") + messageDraft + (((System.currentTimeMillis() / 500) % 2 == 0) ? "_" : "");
        ctx.drawText(mc.textRenderer, draftView, mx0 + 15, my0 + 36, Theme.ACCENT, false);
        String instruction = editingColor != null || editingString != null ? "Enter: speichern · Esc: abbrechen" : "Enter: hinzufügen · Esc: schließen";
        ctx.drawText(mc.textRenderer, instruction, mx0 + 10, my0 + 59, Theme.TEXT_DIM, false);
        List<String> existing = editingMessages == null ? List.of() : editingMessages.get();
        ctx.drawText(mc.textRenderer, editingColor != null || editingString != null ? "Änderungen werden lokal gespeichert" : "Gespeichert: " + existing.size() + " Nachrichten", mx0 + 10, my0 + 76, Theme.TEXT_DIM, false);
        if (editingMessages != null && !existing.isEmpty()) {
            String last = existing.get(existing.size() - 1);
            if (last.length() > 32) last = last.substring(0, 31) + "…";
            String remove = "Letzte: " + last + " · Rechtsklick löscht";
            ctx.drawText(mc.textRenderer, remove, mx0 + 10, my0 + 94, Theme.TEXT_DIM, false);
            if (clickR && in(mx, my, mx0, my0 + 88, mw, 24)) {
                editingMessages.removeMessage(existing.get(existing.size() - 1)); Config.save(); notice("Letzte Nachricht gelöscht"); clickR = false;
            }
        }
    }

    private void drawNotice(DrawContext ctx, String message, int color) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = Math.min(width - 24, mc.textRenderer.getWidth(message) + 20);
        int x = (width - w) / 2;
        int y = viewportBottom + 1;
        ctx.fill(x, y, x + w, y + 16, 0xE6111219);
        ctx.fill(x, y, x + 2, y + 16, color);
        ctx.drawText(mc.textRenderer, message, x + 8, y + 5, color, false);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
