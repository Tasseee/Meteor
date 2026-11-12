package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.Projection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import net.minecraft.class_243;
import net.minecraft.class_332;
import net.minecraft.class_3959;
import net.minecraft.class_4184;
import net.minecraft.class_239.class_240;
import net.minecraft.class_3959.class_242;
import net.minecraft.class_3959.class_3960;
import org.joml.Matrix3x2fStack;
import org.joml.Vector3f;

public final class WorldLyrics extends Module {
   private final ModeSetting mode = this.add(new ModeSetting("Anzeigemodus", "Floating", "Sky", "Floating", "Screen-Random"));
   private final NumberSetting textScale = this.add(new NumberSetting("Textgroesse", 1.25, 0.75, 2.5, 0.05));
   private final NumberSetting lifetime = this.add(new NumberSetting("Sichtdauer (Sek.)", 4.0, 1.0, 10.0, 0.5));
   private final BoolSetting wordReveal = this.add(new BoolSetting("Wort fuer Wort", true));
   private final BoolSetting floating = this.add(new BoolSetting("Schwebe-Animation", true));
   private final BoolSetting background = this.add(new BoolSetting("Neon-Glas-Karten", true));
   private final BoolSetting glow = this.add(new BoolSetting("Mehrfacher Neon-Glow", true));
   private final List<WorldLyrics.LyricWord> activeWords = new ArrayList<>();
   private final Random random = new Random();
   private final double[] projected = new double[2];
   private int lastLine = Integer.MIN_VALUE;
   private String lastTrackKey = "";
   private long lastProgress = -1L;

   public WorldLyrics() {
      super("WorldLyrics", "Lyrics erscheinen an wechselnden Orten, bleiben kurz stehen und verblassen.", Category.VISUALS);
   }

   @Override
   protected void onEnable() {
      this.activeWords.clear();
      this.lastLine = Integer.MIN_VALUE;

      try {
         SpotifyLyricsHud spotify = ModuleManager.get(SpotifyLyricsHud.class);
         if (!spotify.isEnabled()) {
            spotify.setEnabled(true);
         }
      } catch (Exception var2) {
      }
   }

   @Override
   protected void onDisable() {
      this.activeWords.clear();
      this.lastLine = Integer.MIN_VALUE;
   }

   @Override
   public void onWorldLeave() {
      this.activeWords.clear();
      this.lastLine = Integer.MIN_VALUE;
   }

   @Override
   public void renderHud(class_332 ctx) {
      if (mc.field_1687 != null && mc.field_1724 != null && Projection.valid()) {
         SpotifyLyricsHud spotify;
         try {
            spotify = ModuleManager.get(SpotifyLyricsHud.class);
         } catch (Exception var13) {
            return;
         }

         if (spotify.isEnabled() && spotify.hasWorldTrack()) {
            String[] lines = spotify.worldLyricLines();
            long[] times = spotify.worldLyricTimes();
            if (lines.length != 0 && times.length == lines.length) {
               long progress = spotify.worldProgressMs();
               int current = -1;
               int i = 0;

               while (i < times.length && times[i] <= progress) {
                  current = i++;
               }

               if (current < 0) {
                  current = 0;
               }

               String trackKey = lines.length + "|" + lines[0] + "|" + lines[lines.length - 1];
               if (!trackKey.equals(this.lastTrackKey) || progress + 1500L < this.lastProgress) {
                  this.activeWords.clear();
                  this.lastLine = Integer.MIN_VALUE;
                  this.lastTrackKey = trackKey;
               }

               this.lastProgress = progress;
               if (current != this.lastLine) {
                  long start = times[current];
                  long end = current + 1 < times.length ? times[current + 1] : start + 3500L;
                  if (end <= start) {
                     end = start + 2500L;
                  }

                  this.spawnLine(lines[current], start, end, progress);
                  this.lastLine = current;
               }

               long now = System.currentTimeMillis();
               Iterator<WorldLyrics.LyricWord> it = this.activeWords.iterator();

               while (it.hasNext()) {
                  WorldLyrics.LyricWord word = it.next();
                  if (now >= word.expireAt) {
                     it.remove();
                  } else if (now >= word.spawnAt) {
                     this.drawWord(ctx, word, now);
                  }
               }
            }
         }
      }
   }

   private void spawnLine(String line, long lineStart, long lineEnd, long progress) {
      if (line != null && !line.isBlank()) {
         String[] words = line.trim().split("\\s+");
         long now = System.currentTimeMillis();
         long span = Math.max(700L, lineEnd - lineStart);
         long life = (long)(this.lifetime.get() * 1000.0);
         class_4184 camera = mc.field_1773.method_19418();
         if (camera != null && camera.method_19332()) {
            class_243 cameraPos = camera.method_71156();
            Vector3f forward = camera.method_23767().transform(new Vector3f(0.0F, 0.0F, -1.0F)).normalize();
            Vector3f right = camera.method_23767().transform(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
            Vector3f up = camera.method_23767().transform(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();

            for (int i = 0; i < words.length; i++) {
               String word = words[i].replaceAll("[\\p{Cntrl}]", "");
               if (!word.isBlank()) {
                  long delay = this.wordReveal.get() ? Math.min(1800L, 115L * i) : 0L;
                  long spawnAt = now + delay;
                  long expireAt = spawnAt + life;
                  float sx = 0.0F;
                  float sy = 0.0F;
                  float angle = 0.0F;
                  float scale = this.textScale.get().floatValue() * (0.85F + this.random.nextFloat() * 0.35F);
                  double x;
                  double y;
                  double z;
                  if (this.mode.is("Screen-Random")) {
                     sx = 48.0F + this.random.nextFloat() * Math.max(1, Projection.width() - 96);
                     sy = 48.0F + this.random.nextFloat() * Math.max(1, Projection.height() - 96);
                     angle = (this.random.nextFloat() - 0.5F) * 0.22F;
                     z = 0.0;
                     y = 0.0;
                     x = 0.0;
                  } else {
                     double spread = this.mode.is("Sky") ? 0.42 : 1.0;
                     double side = (this.random.nextDouble() - 0.5) * 9.0 * spread;
                     double vertical = this.mode.is("Sky") ? 7.0 + this.random.nextDouble() * 4.0 : (this.random.nextDouble() - 0.5) * 4.2;
                     double depth = this.mode.is("Sky") ? 22.0 + this.random.nextDouble() * 12.0 : 4.0 + this.random.nextDouble() * 5.0;
                     x = cameraPos.field_1352 + forward.x * depth + right.x * side + up.x * vertical;
                     y = cameraPos.field_1351 + forward.y * depth + right.y * side + up.y * vertical;
                     z = cameraPos.field_1350 + forward.z * depth + right.z * side + up.z * vertical;
                     angle = (this.random.nextFloat() - 0.5F) * (this.mode.is("Sky") ? 0.08F : 0.35F);
                  }

                  int[] colors = new int[]{-7602240, -4675585, -11143, -6362113};
                  int color = colors[this.random.nextInt(colors.length)];
                  this.activeWords.add(new WorldLyrics.LyricWord(word, spawnAt, expireAt, x, y, z, sx, sy, angle, scale, color));
               }
            }

            while (this.activeWords.size() > 180) {
               this.activeWords.remove(0);
            }
         }
      }
   }

   private boolean isOccluded(double x, double y, double z) {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         class_4184 camera = mc.field_1773.method_19418();
         if (camera != null && camera.method_19332()) {
            class_243 start = camera.method_71156();
            class_243 end = new class_243(x, y, z);
            return start.method_1025(end) < 0.01
               ? false
               : mc.field_1687.method_17742(new class_3959(start, end, class_3960.field_17558, class_242.field_1348, mc.field_1724)).method_17783()
                  != class_240.field_1333;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private void drawWord(class_332 ctx, WorldLyrics.LyricWord word, long now) {
      double remaining = (word.expireAt - now) / Math.max(1.0, (double)(word.expireAt - word.spawnAt));
      double age = (now - word.spawnAt) / 1000.0;
      float alpha = (float)Math.max(0.0, Math.min(1.0, Math.min(age / 0.22, remaining / 0.8)));
      if (!(alpha <= 0.01F)) {
         float x;
         float y;
         if (this.mode.is("Screen-Random")) {
            x = word.screenX;
            y = word.screenY;
         } else {
            double bob = this.floating.get() ? Math.sin(now / 360.0 + word.x * 0.17 + word.z * 0.13) * 0.12 : 0.0;
            if (!Projection.project(word.x, word.y + bob, word.z, this.projected)) {
               return;
            }

            if (this.mode.is("Floating") && this.isOccluded(word.x, word.y + bob, word.z)) {
               return;
            }

            x = (float)this.projected[0];
            y = (float)this.projected[1];
         }

         float entrance = (float)Math.min(1.0, age / 0.28);
         float pulse = (float)(1.0 + Math.sin(now / 190.0 + word.x * 2.1 + word.z) * 0.025);
         float scale = word.scale * (0.78F + 0.22F * entrance) * pulse;
         int rgb = word.color & 16777215;
         int color = (int)(alpha * 255.0F) << 24 | rgb;
         int halo = (int)(alpha * 42.0F) << 24 | rgb;
         int glowColor = (int)(alpha * 95.0F) << 24 | rgb;
         int shadowColor = (int)(alpha * 190.0F) << 24 | 1054752;
         int width = mc.field_1772.method_1727(word.text);
         Matrix3x2fStack matrices = ctx.method_51448();
         matrices.pushMatrix();
         matrices.translate(x, y);
         matrices.rotate(word.angle);
         matrices.scale(scale, scale);
         int left = -width / 2;
         if (this.background.get()) {
            int padX = 8;
            ctx.method_25294(left - padX - 2, -10, left + width + padX + 2, 11, halo);
            ctx.method_25294(left - padX, -8, left + width + padX, 9, shadowColor);
            ctx.method_25294(left - padX, -8, left - padX + 2, 9, color);
            ctx.method_25294(left - padX + 5, 8, left + width + padX - 5, 9, glowColor);
            ctx.method_25294(left - padX + 4, -6, left - padX + 5, -4, color);
            ctx.method_25294(left + width + padX - 5, 4, left + width + padX - 4, 6, color);
         }

         if (this.glow.get()) {
            ctx.method_51433(mc.field_1772, word.text, left - 2, -5, halo, false);
            ctx.method_51433(mc.field_1772, word.text, left + 2, -5, halo, false);
            ctx.method_51433(mc.field_1772, word.text, left, -6, glowColor, false);
            ctx.method_51433(mc.field_1772, word.text, left, -2, glowColor, false);
         }

         ctx.method_51433(mc.field_1772, word.text, left + 1, -3, -1426063361, false);
         ctx.method_51433(mc.field_1772, word.text, left, -4, color, true);
         if (this.glow.get()) {
            double phase = now / 260.0 + word.spawnAt * 0.001;
            int sparkle = (int)(alpha * (110.0 + 70.0 * Math.abs(Math.sin(phase)))) << 24 | 16777215;
            int sx = left - 4 + (int)((Math.sin(phase) + 1.0) * 2.0);
            int sy = -9 + (int)(Math.cos(phase * 0.8) * 2.0);
            ctx.method_25294(sx, sy, sx + 2, sy + 2, sparkle);
            ctx.method_25294(left + width + 2, 5 + (int)(Math.sin(phase * 1.2) * 2.0), left + width + 4, 7 + (int)(Math.sin(phase * 1.2) * 2.0), sparkle);
         }

         matrices.popMatrix();
      }
   }

   private static final class LyricWord {
      final String text;
      final long spawnAt;
      final long expireAt;
      final double x;
      final double y;
      final double z;
      final float screenX;
      final float screenY;
      final float angle;
      final float scale;
      final int color;

      LyricWord(String text, long spawnAt, long expireAt, double x, double y, double z, float screenX, float screenY, float angle, float scale, int color) {
         this.text = text;
         this.spawnAt = spawnAt;
         this.expireAt = expireAt;
         this.x = x;
         this.y = y;
         this.z = z;
         this.screenX = screenX;
         this.screenY = screenY;
         this.angle = angle;
         this.scale = scale;
         this.color = color;
      }
   }
}
