package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Projection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Renders the currently timed Spotify lyric line as a floating, camera-facing
 * world marker. It uses the shared world projection so the text is anchored
 * in world coordinates rather than placed in the normal HUD layout.
 */
public final class WorldLyrics extends Module {
    private final NumberSetting distance = add(new NumberSetting("Abstand", 5.0, 2.0, 12.0, 0.5));
    private final NumberSetting textScale = add(new NumberSetting("Textgroesse", 1.25, 0.75, 2.5, 0.05));
    private final BoolSetting wordReveal = add(new BoolSetting("Wort fuer Wort", true));
    private final BoolSetting floating = add(new BoolSetting("Schwebe-Animation", true));
    private final BoolSetting showNext = add(new BoolSetting("Naechste Zeile", true));
    private final BoolSetting background = add(new BoolSetting("Lesbarer Hintergrund", true));

    private final double[] projected = new double[2];

    public WorldLyrics() {
        super("WorldLyrics", "Laesst synchronisierte Spotify-Lyrics frei vor dir in der Welt schweben.");
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.world == null || mc.player == null || !Projection.valid()) return;

        SpotifyLyricsHud spotify;
        try {
            spotify = ModuleManager.get(SpotifyLyricsHud.class);
        } catch (Exception ignored) {
            return;
        }
        if (!spotify.isEnabled() || !spotify.hasWorldTrack()) return;

        String[] lines = spotify.worldLyricLines();
        long[] times = spotify.worldLyricTimes();
        if (lines.length == 0 || times.length != lines.length) return;

        long progress = spotify.worldProgressMs();
        int current = -1;
        for (int i = 0; i < times.length; i++) {
            if (times[i] <= progress) current = i;
            else break;
        }
        if (current < 0) current = 0;

        String active = lines[current];
        if (wordReveal.get() && current + 1 < times.length) {
            long span = Math.max(500, times[current + 1] - times[current]);
            double fraction = Math.max(0, Math.min(1, (progress - times[current]) / (double) span));
            String[] words = active.split("\\s+");
            int visible = Math.max(1, Math.min(words.length, (int) Math.ceil(words.length * fraction)));
            active = String.join(" ", java.util.Arrays.copyOf(words, visible));
        }

        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return;
        Vector3f forward = camera.getRotation().transform(new Vector3f(0, 0, -1));
        Vec3d cameraPos = camera.getCameraPos();
        long now = System.currentTimeMillis();
        double bob = floating.get() ? Math.sin(now / 520.0) * 0.12 : 0.0;
        double d = distance.get();
        double worldX = cameraPos.x + forward.x * d;
        double worldY = cameraPos.y + forward.y * d + bob;
        double worldZ = cameraPos.z + forward.z * d;

        if (!Projection.project(worldX, worldY, worldZ, projected)) return;
        float cx = (float) projected[0];
        float cy = (float) projected[1];
        float scale = (float) textScale.get();
        int color = spotify.isWorldTrackPlaying() ? 0xFF8BFFC0 : 0xFFB9C4CE;

        String title = active == null ? "" : active.trim();
        if (title.isEmpty()) return;
        float width = mc.textRenderer.getWidth(title) * scale;
        float maxWidth = Math.max(100, Math.min(Projection.width() - 24, 440));
        if (width > maxWidth) scale *= maxWidth / width;
        width = mc.textRenderer.getWidth(title) * scale;

        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(cx, cy);
        matrices.scale(scale, scale);
        float halfW = mc.textRenderer.getWidth(title) / 2f;
        if (background.get()) {
            int pad = 12;
            int boxW = (int) Math.ceil(halfW * 2) + pad * 2;
            ctx.fill(-boxW / 2, -13, boxW / 2, 13, 0xA9081015);
            ctx.fill(-boxW / 2, -13, boxW / 2, -11, 0xFF1ED760);
        }
        // Soft glow, then crisp centered lettering.
        ctx.drawText(mc.textRenderer, title, Math.round(-halfW - 1), -5, 0x5530FF80, false);
        ctx.drawText(mc.textRenderer, title, Math.round(-halfW + 1), -5, 0x5530FF80, false);
        ctx.drawText(mc.textRenderer, title, Math.round(-halfW), -5, color, true);

        if (showNext.get() && current + 1 < lines.length) {
            String next = lines[current + 1];
            if (next != null && !next.isBlank()) {
                String clipped = next.length() > 58 ? next.substring(0, 57) + "…" : next;
                float nextHalf = mc.textRenderer.getWidth(clipped) / 2f;
                ctx.drawText(mc.textRenderer, clipped, Math.round(-nextHalf), 16, 0xFF9AA7B4, false);
            }
        }
        matrices.popMatrix();
    }
}
