package dev.nightfall.gui;

import dev.nightfall.config.Config;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.util.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * HUD-Editor (H): Ziehen = verschieben, Rechtsklick = ein/aus, Mausrad = skalieren,
 * Mittelklick = sperren, R = Element unter Maus zuruecksetzen (ohne Element: alle), G = Raster-Snap, Esc/H = schliessen.
 */
public class HudEditorScreen extends Screen {
    private final List<HudModule> elements = ModuleManager.hudElements();
    private boolean prevL, prevR, prevM, prevR2, prevG, prevKey;
    private HudModule dragging;
    private int dragDx, dragDy;
    private HudModule hovered;

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
        long h = MinecraftClient.getInstance().getWindow().getHandle();
        prevL = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        prevR = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        prevM = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        prevR2 = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        prevG = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
        prevKey = GLFW.glfwGetKey(h, Config.hudKey) == GLFW.GLFW_PRESS;
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { Config.save(); }

    private static boolean hit(HudModule e, int mx, int my, int sw, int sh) {
        int[] p = e.pos(sw, sh);
        return mx >= p[0] && mx < p[0] + Math.max(8, e.width()) && my >= p[1] && my < p[1] + Math.max(8, e.height());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (hovered != null && hovered.isScalable() && !hovered.isLocked()) {
            int[] st = hovered.ensureState(width, height);
            st[2] = Math.max(50, Math.min(300, st[2] + (vertical > 0 ? 10 : -10)));
            return true;
        }
        return false;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long h = mc.getWindow().getHandle();
        boolean l = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        boolean md = GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) == GLFW.GLFW_PRESS;
        boolean rk = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        boolean gk = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_G) == GLFW.GLFW_PRESS;
        boolean hk = GLFW.glfwGetKey(h, Config.hudKey) == GLFW.GLFW_PRESS;
        boolean clickL = l && !prevL, clickR = r && !prevR, clickM = md && !prevM;
        boolean keyR = rk && !prevR2, keyG = gk && !prevG, keyH = hk && !prevKey;
        prevL = l; prevR = r; prevM = md; prevR2 = rk; prevG = gk; prevKey = hk;
        if (keyH) { close(); return; }
        if (keyG) Config.snapGrid = !Config.snapGrid;

        ctx.fill(0, 0, width, height, Theme.BG_DIM);
        ctx.fill(0, 0, width, 3, Theme.ACCENT);
        ctx.fill(0, height - 3, width, height, Theme.ACCENT_DIM);
        ctx.fill(12, 10, 238, 43, Theme.PANEL);
        ctx.fill(12, 10, 15, 43, Theme.ACCENT);
        ctx.drawText(mc.textRenderer, "NIGHTFALL  /  HUD STUDIO", 23, 17, Theme.TEXT, true);
        ctx.drawText(mc.textRenderer, "LAYOUT EDITOR", 23, 29, Theme.TEXT_DIM, false);
        ctx.fill(0, 0, width, 3, Theme.ACCENT);
        ctx.fill(0, height - 3, width, height, Theme.ACCENT_DIM);
        ctx.fill(12, 10, 238, 43, Theme.PANEL);
        ctx.fill(12, 10, 15, 43, Theme.ACCENT);
        ctx.drawText(mc.textRenderer, \"NIGHTFALL  /  HUD STUDIO\", 23, 17, Theme.TEXT, true);
        ctx.drawText(mc.textRenderer, \"LAYOUT EDITOR\", 23, 29, Theme.TEXT_DIM, false);
        // Subtle shared accent wash keeps the editor visually consistent with ClickGUI.
        ctx.fill(0, 0, width, 2, Theme.withAlpha(Theme.ACCENT, 110));
        if (Config.snapGrid) {
            int g = Math.max(2, Config.gridSize * 4);
            for (int x = 0; x < width; x += g) ctx.fill(x, 0, x + 1, height, 0x14FFFFFF);
            for (int y = 0; y < height; y += g) ctx.fill(0, y, width, y + 1, 0x14FFFFFF);
        }
        ctx.fill(width / 2, 0, width / 2 + 1, height, 0x22FFFFFF);
        ctx.fill(0, height / 2, width, height / 2 + 1, 0x22FFFFFF);

        // Element unter der Maus (zuletzt gezeichnetes = oben)
        hovered = null;
        for (HudModule e : elements) if ((e.isEnabled() || e.width() > 0) && hit(e, mx, my, width, height)) hovered = e;

        if (!l) dragging = null;
        if (clickL && hovered != null && !hovered.isLocked()) {
            int[] p = hovered.pos(width, height);
            hovered.ensureState(width, height);
            dragging = hovered; dragDx = mx - p[0]; dragDy = my - p[1];
        }
        if (dragging != null) {
            int[] st = dragging.ensureState(width, height);
            int nx = mx - dragDx, ny = my - dragDy;
            if (Config.snapGrid) { int g = Math.max(1, Config.gridSize); nx = Math.round(nx / (float) g) * g; ny = Math.round(ny / (float) g) * g; }
            st[0] = Math.max(0, Math.min(width - 8, nx));
            st[1] = Math.max(0, Math.min(height - 8, ny));
        }
        if (clickR && hovered != null) hovered.toggle();
        if (clickM && hovered != null) { int[] st = hovered.ensureState(width, height); st[3] = st[3] == 1 ? 0 : 1; }
        if (keyR) {
            if (hovered != null) hovered.resetState();
            else for (HudModule e : elements) e.resetState();
        }

        for (HudModule e : elements) {
            int[] p = e.pos(width, height);
            int ew = Math.max(24, e.width()), eh = Math.max(10, e.height());
            if (e.isEnabled()) e.drawForEditor(ctx);
            int baseCol = e.isEnabled() ? 0x88FFFFFF : 0x44FFFFFF;
            int col = e == hovered ? Theme.ACCENT : baseCol;
            ctx.fill(p[0] - 1, p[1] - 1, p[0] + ew + 1, p[1], col);
            ctx.fill(p[0] - 1, p[1] + eh, p[0] + ew + 1, p[1] + eh + 1, col);
            ctx.fill(p[0] - 1, p[1], p[0], p[1] + eh, col);
            ctx.fill(p[0] + ew, p[1], p[0] + ew + 1, p[1] + eh, col);
            if (!e.isEnabled()) ctx.drawText(mc.textRenderer, e.getName() + " (aus)", p[0] + 2, p[1] + 2, 0x88FFFFFF, false);
            if (e.isLocked()) ctx.drawText(mc.textRenderer, "L", p[0] + ew - 7, p[1] + 1, 0xFFFFD27A, false);
        }

        String help = "Ziehen: verschieben | Rechtsklick: ein/aus | Mausrad: Groesse | Mittelklick: sperren | R: reset | G: Raster "
                + (Config.snapGrid ? "an" : "aus");
        int helpW = Math.min(width - 24, mc.textRenderer.getWidth(help) + 22);
        int helpX = (width - helpW) / 2;
        ctx.fill(helpX, height - 28, helpX + helpW, height - 8, Theme.PANEL);
        ctx.fill(helpX, height - 28, helpX + 2, height - 8, Theme.ACCENT);
        ctx.drawText(mc.textRenderer, help, helpX + 10, height - 21, Theme.TEXT_DIM, false);
        if (hovered != null) ctx.drawText(mc.textRenderer, hovered.getName() + (hovered.isScalable() ? "  x" + String.format("%.1f", hovered.scale()) : ""),
                mx + 8, my + 8, Theme.TEXT, true);
    }
}
