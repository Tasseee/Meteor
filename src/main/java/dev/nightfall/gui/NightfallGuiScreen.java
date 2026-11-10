package dev.nightfall.gui;

import dev.nightfall.Nightfall;
import dev.nightfall.config.Config;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.*;
import dev.nightfall.modules.GUITheme;
import dev.nightfall.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

/** Floating Nightfall dashboard inspired by the supplied reference. */
public final class NightfallGuiScreen extends Screen {
    private static final int SIDE = 154, HEAD = 25, ROW = 22;
    private static final List<String> TABS = new ArrayList<>(List.of("Combat","Movement","Visuals","Player","World","Misc","HUD","Config"));
    private static final Map<String, Set<String>> EXPANDED = new HashMap<>();
    private boolean prevLeft, prevRight, draggingWindow, draggingTab;
    private String dragPanel, dragTab;
    private int dragX, dragY, tabStart;
    private String activeTab = "Combat";
    private String slider = "";
    private int sliderX, sliderW;
    private int scroll;
    private long tabChangedAt = System.nanoTime();

    public NightfallGuiScreen() {
        super(Text.literal("Nightfall"));
        long h = MinecraftClient.getInstance().getWindow().getHandle();
        prevLeft = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        prevRight = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        restoreTabs();
        for (String tab : TABS) panel(tab, SIDE + 34, 105)[2] = tab.equals(activeTab) ? 1 : 0;
    }
    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Config.save(); }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(5000, scroll - (int)Math.round(verticalAmount * 28.0)));
        return true;
    }
    @Override public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if (input.key() == Config.guiKey || input.key() == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        return super.keyPressed(input);
    }

    private static void restoreTabs() {
        if (Config.categoryOrder == null || Config.categoryOrder.isBlank()) return;
        List<String> saved = new ArrayList<>(Arrays.asList(Config.categoryOrder.split(",")));
        if (saved.size() == 8 && new HashSet<>(saved).equals(new HashSet<>(TABS))) { TABS.clear(); TABS.addAll(saved); }
    }
    private static void saveTabs() { Config.categoryOrder = String.join(",", TABS); Config.save(); }

    private List<Module> modules(String tab) {
        List<Module> out = new ArrayList<>();
        for (Module m : ModuleManager.all()) {
            String c = switch (m.getCategory()) {
                case COMBAT -> "Combat"; case MOVEMENT -> "Movement"; case VISUALS -> "Visuals";
                case PLAYER -> "Player"; case WORLD, BASEFINDER -> "World"; case MISC -> "Misc"; case HUD -> "HUD";
            };
            if (tab.equals(c)) out.add(m);
        }
        return out;
    }
    private int[] panel(String id, int defaultX, int defaultY) {
        return Config.panels.computeIfAbsent("window:" + id, k -> new int[]{defaultX, defaultY, 1});
    }
    private boolean opened(String id) { return panel(id, 0, 0)[2] != 0; }
    private void setOpen(String id, boolean v) { panel(id, 0, 0)[2] = v ? 1 : 0; Config.save(); }
    private void selectTab(String tab) {
        if (tab.equals(activeTab)) return;
        activeTab = tab;
        for (String name : TABS) panel(name, 0, 0)[2] = name.equals(tab) ? 1 : 0;
        tabChangedAt = System.nanoTime();
        scroll = 0;
        Config.save();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long h = mc.getWindow().getHandle();
        boolean left = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean right = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean click = left && !prevLeft, release = !left && prevLeft, rightClick = right && !prevRight;
        prevLeft = left; prevRight = right;

        ctx.fill(0, 0, width, height, 0xA9080910);
        int blurShade = Math.max(0, Math.min(150, Config.blurStrength));
        ctx.fill(0, 0, width, height, (blurShade << 24) | 0x08070F);
        ctx.fill(0, 0, width, 3, Theme.ACCENT);

        // Brand header
        panel(ctx, 12, 10, SIDE + 10, 78);
        ctx.drawText(mc.textRenderer, "NIGHTFALL", 29, 24, Theme.TEXT, true);
        ctx.drawText(mc.textRenderer, "CLIENT  /  CONTROL CENTER", 29, 43, Theme.TEXT_DIM, false);
        ctx.drawText(mc.textRenderer, "FPS " + mc.getCurrentFps(), width - 190, 24, Theme.ACCENT, true);
        ctx.drawText(mc.textRenderer, "TPS 20.0", width - 100, 24, Theme.TEXT, false);

        // Sidebar and draggable category order
        int sy = 100;
        for (int i = 0; i < TABS.size(); i++) {
            String tab = TABS.get(i);
            int y = sy + i * 38;
            boolean over = inside(mx, my, 14, y, SIDE, 32);
            if (tab.equals(activeTab)) {
                ctx.fill(14, y, SIDE + 14, y + 32, 0xD52C1552);
                ctx.fill(14, y, 17, y + 32, Theme.ACCENT);
            } else if (over) ctx.fill(14, y, SIDE + 14, y + 32, 0x553B2A59);
            ctx.drawText(mc.textRenderer, icon(tab) + "  " + tab, 27, y + 11, tab.equals(activeTab) ? Theme.TEXT : Theme.TEXT_DIM, true);
            if (click && over) {
                if (draggingTab && dragTab != null) {
                    int from = TABS.indexOf(dragTab), to = i;
                    if (from >= 0) { TABS.remove(from); TABS.add(Math.max(0, Math.min(TABS.size(), to)), dragTab); saveTabs(); }
                } else { selectTab(tab); }
                click = false;
            }
            if (left && over && dragTab == null && !draggingWindow) { dragTab = tab; tabStart = i; }
        }
        if (left && dragTab != null) {
            int overIndex = (my - sy) / 38;
            if (my >= sy && overIndex >= 0 && overIndex < TABS.size()) {
                String overTab = TABS.get(overIndex);
                if (!overTab.equals(dragTab)) {
                    int from = TABS.indexOf(dragTab);
                    if (from >= 0) { TABS.remove(from); TABS.add(Math.max(0, Math.min(TABS.size(), overIndex)), dragTab); saveTabs(); }
                }
            }
        }
        if (release) dragTab = null;

        // A single selected tab owns the workspace; switching tabs closes the previous panel.
        if (!opened(activeTab)) {
            for (String name : TABS) panel(name, 0, 0)[2] = name.equals(activeTab) ? 1 : 0;
        }
        int defaultX = Math.max(SIDE + 20, (width + SIDE - 270) / 2);
        int defaultY = Math.max(82, (height - Math.min(520, height - 110)) / 2);
        int[] p = panel(activeTab, defaultX, defaultY);
        int minX = Math.min(SIDE + 18, Math.max(8, width - 220));
        int maxX = Math.max(minX, width - 220);
        int maxY = Math.max(72, height - HEAD - 36);
        int x = Math.max(minX, Math.min(maxX, p[0]));
        int y = Math.max(72, Math.min(maxY, p[1]));
        int w = Math.max(210, Math.min(300, width - x - 10));
        float anim = 1.0f;
        if (Config.guiAnimations) {
            float duration = Math.max(50, Config.guiAnimationDuration) / Math.max(0.1f, Config.guiAnimationSpeed);
            float t = Math.max(0.0f, Math.min(1.0f, (System.nanoTime() - tabChangedAt) / 1_000_000.0f / duration));
            anim = switch (Config.guiEasing) {
                case "Linear" -> t;
                case "Sine" -> (float)(1.0 - Math.cos(t * Math.PI * 0.5));
                default -> 1.0f - (float)Math.pow(1.0f - t, 3.0);
            };
        }
        int offsetX = 0, offsetY = 0;
        if (Config.guiAnimations && Config.guiAnimationType.equals("Slide")) {
            int distance = Math.round((1.0f - anim) * 24.0f);
            switch (Config.guiAnimationDirection) {
                case "Right" -> offsetX = distance;
                case "Up" -> offsetY = -distance;
                case "Down" -> offsetY = distance;
                default -> offsetX = -distance;
            }
        }
        drawWindow(ctx, activeTab, x + offsetX, y + offsetY, w, mx, my, click, rightClick, mc);
        if (draggingWindow && activeTab.equals(dragPanel)) {
            if (left) {
                p[0] = Math.max(minX, Math.min(maxX, mx - dragX));
                p[1] = Math.max(72, Math.min(maxY, my - dragY));
            } else { draggingWindow = false; dragPanel = null; Config.save(); }
        }
        if (!left && slider.isEmpty()) { draggingWindow = false; dragPanel = null; }
        if (!slider.isEmpty() && left) {
            float t = Math.max(0, Math.min(1, (mx - sliderX) / (float)Math.max(1, sliderW)));
            if (slider.equals("hue")) { Config.accentHue = t * 360f; applyHue(); }
            else if (slider.equals("blur")) { Config.blurStrength = Math.round(t * 150); }
        }
        if (!left) slider = "";
        ctx.drawText(mc.textRenderer, "Nightfall  •  Ziehen: Fenster | Tabs per Drag & Drop sortieren | Rechtsklick: Einstellungen", SIDE + 28, height - 22, Theme.TEXT_DIM, false);
    }

    private void drawWindow(DrawContext ctx, String tab, int x, int y, int w, int mx, int my,
                            boolean click, boolean rightClick, MinecraftClient mc) {
        List<Module> mods = modules(tab);
        Set<String> expanded = EXPANDED.computeIfAbsent(tab, k -> new HashSet<>());
        int visible = 0;
        for (Module m : mods) visible += ROW + (expanded.contains(m.getName()) ? m.getSettings().size() * ROW : 0);
        int bodyH = pMinimized(tab) ? 0 : Math.max(0, Math.min(Math.max(64, height - y - HEAD - 12), tab.equals("Config") ? 340 : Math.max(50, visible + 8)));
        panel(ctx, x, y, w, HEAD + bodyH);
        ctx.fill(x, y, x + w, y + HEAD, 0xEE100D19);
        ctx.fill(x, y, x + 2, y + HEAD, Theme.ACCENT);
        ctx.drawText(mc.textRenderer, icon(tab) + "  " + tab, x + 10, y + 9, Theme.TEXT, true);
        ctx.drawText(mc.textRenderer, "_", x + w - 35, y + 8, Theme.TEXT_DIM, true);
        ctx.drawText(mc.textRenderer, "×", x + w - 17, y + 8, Theme.TEXT, true);
        if (click && inside(mx,my,x,y,w,HEAD)) {
            if (mx >= x+w-22) { setOpen(tab,false); return; }
            if (mx >= x+w-43) { pMin(tab); return; }
            draggingWindow = true; dragPanel = tab; dragX = mx-x; dragY = my-y;
        }
        if (bodyH == 0) return;
        if (tab.equals("Config")) { drawConfig(ctx,x,y,w,mx,my,click); return; }

        int rowY = y + HEAD + 5 - scroll;
        ctx.enableScissor(x + 3, y + HEAD, x + w - 3, y + HEAD + bodyH);
        for (Module mod : mods) {
            if (rowY > y + HEAD + bodyH - ROW) { rowY += ROW; continue; }
            boolean over = inside(mx,my,x+4,rowY,w-8,ROW);
            if (over) ctx.fill(x+4,rowY,x+w-4,rowY+ROW-1,0x283F3B50);
            ctx.drawText(mc.textRenderer, mod.getName(), x+10,rowY+7,mod.isEnabled()?Theme.TEXT:Theme.TEXT_DIM,false);
            int tx=x+w-34, ty=rowY+6;
            ctx.fill(tx,ty,tx+24,ty+12,mod.isEnabled()?Theme.ACCENT:0xFF45434E);
            ctx.fill(mod.isEnabled()?tx+14:tx+2,ty+2,mod.isEnabled()?tx+22:tx+10,ty+10,0xFFF5F4FA);
            if (click && over) {
                if (mx >= tx-3) { mod.toggle(); Config.save(); }
                else if (!mod.getSettings().isEmpty()) { if(!expanded.add(mod.getName())) expanded.remove(mod.getName()); }
            }
            if (rightClick && over && !mod.getSettings().isEmpty()) { if(!expanded.add(mod.getName())) expanded.remove(mod.getName()); }
            rowY += ROW;
            if (expanded.contains(mod.getName())) for (Setting<?> setting : mod.getSettings()) {
                if (rowY > y + HEAD + bodyH - ROW) { rowY += ROW; continue; }
                drawSetting(ctx,setting,x+8,rowY,w-16,mx,my,click);
                rowY += ROW;
            }
        }
        ctx.disableScissor();
    }

    private void drawSetting(DrawContext ctx, Setting<?> s, int x, int y, int w, int mx, int my, boolean click) {
        MinecraftClient mc=MinecraftClient.getInstance();
        boolean over=inside(mx,my,x,y,w,ROW);
        ctx.fill(x,y,x+w,y+ROW-1,0x261B1924);
        ctx.drawText(mc.textRenderer,s.getName(),x+5,y+7,Theme.TEXT_DIM,false);
        if(s instanceof BoolSetting b) {
            int bx=x+w-28; ctx.fill(bx,y+5,bx+22,y+16,b.get()?Theme.ACCENT:0xFF45434E);
            ctx.fill(b.get()?bx+13:bx+2,y+7,b.get()?bx+20:bx+9,y+14,0xFFFFFFFF);
            if(click&&over){b.toggle();Config.save();}
        } else if(s instanceof ModeSetting m) {
            String v="‹ "+m.get()+" ›"; ctx.drawText(mc.textRenderer,v,x+w-8-mc.textRenderer.getWidth(v),y+7,Theme.ACCENT,false);
            if(click&&over){m.cycle(1);Config.save();}
        } else if(s instanceof NumberSetting n) {
            String v=n.display(); ctx.drawText(mc.textRenderer,v,x+w-8-mc.textRenderer.getWidth(v),y+7,Theme.ACCENT,false);
            if(click&&over){n.set(n.get()+n.step);Config.save();}
        } else if(s instanceof ColorSetting c) {
            ctx.fill(x+w-26,y+5,x+w-8,y+17,0xFF000000|c.rgb());
        } else if(s instanceof StringSetting st) {
            String v=st.get(); if(v.length()>14)v=v.substring(0,13)+"…";
            ctx.drawText(mc.textRenderer,v,x+w-8-mc.textRenderer.getWidth(v),y+7,Theme.ACCENT,false);
        }
    }

    private void drawConfig(DrawContext ctx,int x,int y,int w,int mx,int my,boolean click) {
        MinecraftClient mc=MinecraftClient.getInstance();
        ctx.drawText(mc.textRenderer,"TAB-ANIMATION",x+10,y+37,Theme.ACCENT,true);
        drawConfigOption(ctx,mc,"Enabled",Config.guiAnimations?"AN":"AUS",x+10,y+53,w-20);
        drawConfigOption(ctx,mc,"Style",Config.guiAnimationType,x+10,y+73,w-20);
        drawConfigOption(ctx,mc,"Richtung",Config.guiAnimationDirection,x+10,y+93,w-20);
        drawConfigOption(ctx,mc,"Easing",Config.guiEasing,x+10,y+113,w-20);
        drawConfigOption(ctx,mc,"Dauer",Config.guiAnimationDuration+" ms",x+10,y+133,w-20);
        drawConfigOption(ctx,mc,"Geschwindigkeit",String.format(Locale.ROOT,"%.1fx",Config.guiAnimationSpeed),x+10,y+153,w-20);
        ctx.drawText(mc.textRenderer,"Akzentfarbe · Farbton",x+10,y+185,Theme.TEXT,false);
        drawSlider(ctx,x+10,y+207,w-20,Config.accentHue/360f);
        ctx.drawText(mc.textRenderer,String.format(Locale.ROOT,"%.0f°",Config.accentHue),x+w-42,y+192,Theme.ACCENT,false);
        ctx.drawText(mc.textRenderer,"Hintergrund · Blur-Stärke",x+10,y+232,Theme.TEXT,false);
        drawSlider(ctx,x+10,y+254,w-20,Config.blurStrength/150f);
        ctx.drawText(mc.textRenderer,Config.blurStrength==0?"Aus":Config.blurStrength<60?"Leicht":Config.blurStrength<110?"Mittel":"Stark",x+w-48,y+239,Theme.ACCENT,false);
        ctx.fill(x+10,y+278,x+w-10,y+307,Theme.withAlpha(Theme.ACCENT,40));
        ctx.drawText(mc.textRenderer,"Vorschau · Änderungen live",x+18,y+289,Theme.TEXT,true);
        if(click&&inside(mx,my,x+10,y+202,w-20,12)){slider="hue";sliderX=x+10;sliderW=w-20;Config.accentHue=Math.max(0,Math.min(360,(mx-sliderX)/(float)sliderW*360));applyHue();}
        if(click&&inside(mx,my,x+10,y+249,w-20,12)){slider="blur";sliderX=x+10;sliderW=w-20;Config.blurStrength=Math.round(Math.max(0,Math.min(1,(mx-sliderX)/(float)sliderW))*150);}
        if(click&&inside(mx,my,x+10,y+53,w-20,18)) Config.guiAnimations=!Config.guiAnimations;
        if(click&&inside(mx,my,x+10,y+73,w-20,18)) Config.guiAnimationType=cycle(Config.guiAnimationType,List.of("Fade","Slide","Scale","Zoom"));
        if(click&&inside(mx,my,x+10,y+93,w-20,18)) Config.guiAnimationDirection=cycle(Config.guiAnimationDirection,List.of("Left","Right","Up","Down"));
        if(click&&inside(mx,my,x+10,y+113,w-20,18)) Config.guiEasing=cycle(Config.guiEasing,List.of("Linear","Sine","Cubic"));
        if(click&&inside(mx,my,x+10,y+133,w-20,18)) Config.guiAnimationDuration=Config.guiAnimationDuration>=1000?50:Math.min(1000,Config.guiAnimationDuration+50);
        if(click&&inside(mx,my,x+10,y+153,w-20,18)) Config.guiAnimationSpeed=Config.guiAnimationSpeed>=5.0f?0.1f:Math.min(5.0f,Config.guiAnimationSpeed+0.1f);
        if(click) Config.save();
    }
    private void drawConfigOption(DrawContext ctx,MinecraftClient mc,String label,String value,int x,int y,int w) {
        ctx.fill(x,y,x+w,y+18,0x3820182B);
        ctx.drawText(mc.textRenderer,label,x+5,y+5,Theme.TEXT_DIM,false);
        int vw=mc.textRenderer.getWidth(value);
        ctx.drawText(mc.textRenderer,value,x+w-vw-5,y+5,Theme.ACCENT,true);
    }
    private static String cycle(String current,List<String> values) {
        int index=values.indexOf(current);
        return values.get((index+1+values.size())%values.size());
    }
    private void drawSlider(DrawContext ctx,int x,int y,int w,float t){ctx.fill(x,y,x+w,y+4,0xFF403B4A);ctx.fill(x,y,x+(int)(w*Math.max(0,Math.min(1,t))),y+4,Theme.ACCENT);ctx.fill(x+(int)(w*t)-3,y-3,x+(int)(w*t)+3,y+7,0xFFFFFFFF);}
    private void applyHue() {
        int rgb=java.awt.Color.HSBtoRGB((Config.accentHue%360f)/360f,0.78f,1f)&0xFFFFFF;
        Theme.apply(rgb,Theme.ACCENT_DIM&0xFFFFFF,Theme.PANEL&0xFFFFFF,Theme.TEXT&0xFFFFFF,Theme.TEXT_DIM&0xFFFFFF,Theme.BG_DIM&0xFFFFFF,Theme.DESIGN);
    }
    private boolean pMinimized(String tab) { return panel(tab, 0, 0)[2] == 2; }
    private void pMin(String tab){int[] p=panel(tab,0,0);p[2]=p[2]==2?1:2;Config.save();}
    private void panel(DrawContext c,int x,int y,int w,int h){c.fill(x-1,y-1,x+w+1,y+h+1,Theme.withAlpha(Theme.ACCENT,190));c.fill(x,y,x+w,y+h,0xD90B0A11);}
    private static String icon(String s){return switch(s){case "Combat"->"⚔";case "Movement"->"⌁";case "Visuals"->"◉";case "Player"->"●";case "World"->"◇";case "Misc"->"⚙";case "HUD"->"▣";default->"☷";};}
    private static boolean inside(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
}
