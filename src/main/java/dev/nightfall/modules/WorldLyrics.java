package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Projection;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Animated lyrics that are spawned at distinct positions and remain there
 * briefly before fading. World modes are projected from fixed world positions;
 * screen mode uses independent random screen positions.
 */
public final class WorldLyrics extends Module {
    private final ModeSetting mode = add(new ModeSetting("Anzeigemodus", "Floating", "Sky", "Floating", "Screen-Random"));
    private final NumberSetting textScale = add(new NumberSetting("Textgroesse", 1.25, 0.75, 2.5, 0.05));
    private final NumberSetting lifetime = add(new NumberSetting("Sichtdauer (Sek.)", 4.0, 1.0, 10.0, 0.5));
    private final BoolSetting wordReveal = add(new BoolSetting("Wort fuer Wort", true));
    private final BoolSetting floating = add(new BoolSetting("Schwebe-Animation", true));
    private final BoolSetting background = add(new BoolSetting("Lesbarer Hintergrund", false));
    private final BoolSetting glow = add(new BoolSetting("Spotify-Glow", true));

    private final List<LyricWord> activeWords = new ArrayList<>();
    private final Random random = new Random();
    private final double[] projected = new double[2];
    private int lastLine = Integer.MIN_VALUE;
    private String lastTrackKey = "";
    private long lastProgress = -1;

    private static final class LyricWord {
        final String text;
        final long spawnAt;
        final long expireAt;
        final double x, y, z;
        final float screenX, screenY, angle, scale;
        final int color;

        LyricWord(String text, long spawnAt, long expireAt, double x, double y, double z,
                  float screenX, float screenY, float angle, float scale, int color) {
            this.text = text;
            this.spawnAt = spawnAt;
            this.expireAt = expireAt;
            this.x = x; this.y = y; this.z = z;
            this.screenX = screenX; this.screenY = screenY;
            this.angle = angle; this.scale = scale; this.color = color;
        }
    }

    public WorldLyrics() {
        super("WorldLyrics", "Lyrics erscheinen an wechselnden Orten, bleiben kurz stehen und verblassen.", Category.VISUALS);
    }

    @Override protected void onEnable() {
        activeWords.clear();
        lastLine = Integer.MIN_VALUE;
        try {
            SpotifyLyricsHud spotify = ModuleManager.get(SpotifyLyricsHud.class);
            if (!spotify.isEnabled()) spotify.setEnabled(true);
        } catch (Exception ignored) { }
    }

    @Override protected void onDisable() {
        activeWords.clear();
        lastLine = Integer.MIN_VALUE;
    }

    @Override public void onWorldLeave() {
        activeWords.clear();
        lastLine = Integer.MIN_VALUE;
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.world == null || mc.player == null || !Projection.valid()) return;

        SpotifyLyricsHud spotify;
        try { spotify = ModuleManager.get(SpotifyLyricsHud.class); }
        catch (Exception ignored) { return; }
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

        String trackKey = lines.length + "|" + lines[0] + "|" + lines[lines.length - 1];
        if (!trackKey.equals(lastTrackKey) || progress + 1500 < lastProgress) {
            activeWords.clear();
            lastLine = Integer.MIN_VALUE;
            lastTrackKey = trackKey;
        }
        lastProgress = progress;

        if (current != lastLine) {
            long start = times[current];
            long end = current + 1 < times.length ? times[current + 1] : start + 3500;
            if (end <= start) end = start + 2500;
            spawnLine(lines[current], start, end, progress);
            lastLine = current;
        }

        long now = System.currentTimeMillis();
        Iterator<LyricWord> it = activeWords.iterator();
        while (it.hasNext()) {
            LyricWord word = it.next();
            if (now >= word.expireAt) { it.remove(); continue; }
            if (now < word.spawnAt) continue;
            drawWord(ctx, word, now);
        }
    }

    private void spawnLine(String line, long lineStart, long lineEnd, long progress) {
        if (line == null || line.isBlank()) return;
        String[] words = line.trim().split("\\s+");
        long now = System.currentTimeMillis();
        long span = Math.max(700, lineEnd - lineStart);
        long life = (long) (lifetime.get() * 1000);
        Camera camera = mc.gameRenderer.getCamera();
        if (camera == null || !camera.isReady()) return;

        Vec3d cameraPos = camera.getCameraPos();
        Vector3f forward = camera.getRotation().transform(new Vector3f(0, 0, -1)).normalize();
        Vector3f right = camera.getRotation().transform(new Vector3f(1, 0, 0)).normalize();
        Vector3f up = camera.getRotation().transform(new Vector3f(0, 1, 0)).normalize();

        for (int i = 0; i < words.length; i++) {
            String word = words[i].replaceAll("[\\p{Cntrl}]", "");
            if (word.isBlank()) continue;
            long intendedAt = lineStart + (wordReveal.get() ? span * i / Math.max(1, words.length) : 0);
            long delay = Math.max(0, intendedAt - progress);
            long spawnAt = now + delay;
            long expireAt = spawnAt + life;
            double x, y, z;
            float sx = 0, sy = 0, angle = 0;
            float scale = (float) textScale.get() * (0.85f + random.nextFloat() * 0.35f);

            if (mode.is("Screen-Random")) {
                sx = 48 + random.nextFloat() * Math.max(1, Projection.width() - 96);
                sy = 48 + random.nextFloat() * Math.max(1, Projection.height() - 96);
                angle = (random.nextFloat() - 0.5f) * 0.22f;
                x = y = z = 0;
            } else {
                double spread = mode.is("Sky") ? 0.42 : 1.0;
                double side = (random.nextDouble() - 0.5) * 9.0 * spread;
                double vertical = mode.is("Sky")
                        ? 7.0 + random.nextDouble() * 4.0
                        : (random.nextDouble() - 0.5) * 4.2;
                double depth = mode.is("Sky") ? 22.0 + random.nextDouble() * 12.0 : 4.0 + random.nextDouble() * 5.0;
                x = cameraPos.x + forward.x * depth + right.x * side + up.x * vertical;
                y = cameraPos.y + forward.y * depth + right.y * side + up.y * vertical;
                z = cameraPos.z + forward.z * depth + right.z * side + up.z * vertical;
                angle = (random.nextFloat() - 0.5f) * (mode.is("Sky") ? 0.08f : 0.35f);
            }

            int[] colors = {0xFF8BFFC0, 0xFFB8A7FF, 0xFFFFD479, 0xFF9EEBFF};
            int color = colors[random.nextInt(colors.length)];
            activeWords.add(new LyricWord(word, spawnAt, expireAt, x, y, z, sx, sy, angle, scale, color));
        }
        // Keep the list bounded if a long song contains many short lines.
        while (activeWords.size() > 180) activeWords.remove(0);
    }

    private void drawWord(DrawContext ctx, LyricWord word, long now) {
        double remaining = (word.expireAt - now) / Math.max(1.0, (double) (word.expireAt - word.spawnAt));
        double age = (now - word.spawnAt) / 1000.0;
        float alpha = (float) Math.max(0, Math.min(1, Math.min(age / 0.22, remaining / 0.8)));
        if (alpha <= 0.01f) return;

        float x, y;
        if (mode.is("Screen-Random")) {
            x = word.screenX;
            y = word.screenY;
        } else {
            double bob = floating.get() ? Math.sin(now / 360.0 + word.x * 0.17 + word.z * 0.13) * 0.12 : 0;
            if (!Projection.project(word.x, word.y + bob, word.z, projected)) return;
            x = (float) projected[0];
            y = (float) projected[1];
        }

        float scale = word.scale * (float) (0.88 + 0.12 * Math.min(1, age / 0.25));
        int rgb = word.color & 0x00FFFFFF;
        int color = ((int) (alpha * 255) << 24) | rgb;
        int glowColor = ((int) (alpha * 70) << 24) | 0x00FFFFFF;
        int shadowColor = ((int) (alpha * 170) << 24);
        int width = mc.textRenderer.getWidth(word.text);

        var matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(x, y);
        matrices.rotate(word.angle);
        matrices.scale(scale, scale);
        int left = -width / 2;
        if (background.get()) {
            int pad = 5;
            ctx.fill(left - pad, -7, left + width + pad, 8, shadowColor | 0x00101820);
        }
        if (glow.get()) {
            ctx.drawText(mc.textRenderer, word.text, left - 1, -4, glowColor, false);
            ctx.drawText(mc.textRenderer, word.text, left + 1, -4, glowColor, false);
        }
        ctx.drawText(mc.textRenderer, word.text, left, -4, color, true);
        matrices.popMatrix();
    }
}
