package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.*;
import dev.nightfall.scanner.ChunkResult;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Box;

/** Zeichnet SUS-/SUS-WEG-Chunks des BaseFinders. Modi: Surface (flach), Frames (Saeule), Pulse (animiert). */
public class ChunkEsp extends Module {
    private final ModeSetting mode      = add(new ModeSetting("Modus", "Frames", "Surface", "Frames", "Pulse"));
    private final NumberSetting dist    = add(new NumberSetting("Max Distanz (Chunks)", 12, 2, 48, 1));
    private final NumberSetting height  = add(new NumberSetting("Rahmenhoehe", 24, 4, 128, 2));
    private final NumberSetting speed   = add(new NumberSetting("Pulse-Speed", 1.0, 0.2, 4.0, 0.1));
    private final NumberSetting thick   = add(new NumberSetting("Linienstaerke", 1, 1, 4, 1));
    private final NumberSetting opacity = add(new NumberSetting("Transparenz", 0.85, 0.2, 1.0, 0.05));
    private final BoolSetting showScore = add(new BoolSetting("Score anzeigen", true));
    private final BoolSetting showGone  = add(new BoolSetting("SUS WEG anzeigen", true));
    private final NumberSetting maxObj  = add(new NumberSetting("Max Objekte", 48, 4, 256, 4));

    public ChunkEsp() { super("ChunkESP", "Markiert verdaechtige Chunks (SUS) in der Welt.", Category.VISUALS); }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.player == null || !Projection.valid()) return;
        BaseFinder bf = ModuleManager.get(BaseFinder.class);
        if (!bf.isEnabled()) return;
        int pcx = ((int) Math.floor(Projection.eyeX())) >> 4;
        int pcz = ((int) Math.floor(Projection.eyeZ())) >> 4;
        int d = dist.getInt();
        double t = (System.currentTimeMillis() % 100000L) / 1000.0 * speed.get();
        double pulse = 0.5 + 0.5 * Math.sin(t * Math.PI * 2);
        int drawnHere = 0;
        double py = Projection.eyeY();

        for (ChunkResult r : bf.marks()) {
            if (drawnHere >= maxObj.getInt() || EspBase.drawn >= 2048) break;
            boolean gone = r.status == ChunkResult.Status.GONE;
            if (gone && !showGone.get()) continue;
            int dx = r.cx - pcx, dz = r.cz - pcz;
            if (dx * dx + dz * dz > d * d) continue;

            float scoreT = (float) Math.max(0.0, Math.min(1.0, r.score / (bf.thresholdValue() * 3.0)));
            int scoreColor = Theme.lerp(0xFF9B5CF6, 0xFFFF3B5C, scoreT);
            float depthT = (float) Math.max(0.0, Math.min(1.0, Math.sqrt(dx * dx + dz * dz) / Math.max(1.0, d)));
            int rgb = gone ? 0xFF2F6FDB : Theme.lerp(scoreColor, 0xFF35E0D0, depthT * 0.55f);
            double x0 = r.cx * 16.0, z0 = r.cz * 16.0;
            double h = height.get(), y0 = py - h / 2.0;
            int alpha = (int) (opacity.get() * 255);
            Box box;
            switch (mode.get()) {
                case "Surface" -> box = new Box(x0, py - 1.0, z0, x0 + 16, py - 0.95, z0 + 16);
                case "Pulse" -> {
                    double hh = h * (0.6 + 0.4 * pulse);
                    box = new Box(x0, py - hh / 2.0, z0, x0 + 16, py + hh / 2.0, z0 + 16);
                    alpha = (int) (alpha * (0.45 + 0.55 * pulse));
                }
                default -> box = new Box(x0, y0, z0, x0 + 16, y0 + h, z0 + 16);
            }
            if (Draw.box(ctx, box, Theme.withAlpha(rgb, alpha), thick.getInt(), true)) {
                String label = gone ? "SUS WEG" : "SUS" + (showScore.get() ? " " + String.format("%.1f", r.score) : "");
                Draw.centeredText(ctx, mc.textRenderer, label, Draw.topX, Draw.topY - 10, 0xFF000000 | rgb);
                drawnHere++;
                EspBase.drawn++;
            }
        }
    }
}
