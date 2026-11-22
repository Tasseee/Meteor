package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.StringSetting;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_5250;
import net.minecraft.class_7417;
import net.minecraft.class_8828;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_310;

public final class Privat extends HudModule {
   private final StringSetting fakeName = this.add(new StringSetting("Name", "", 32));
   private final ModeSetting rank = this.add(new ModeSetting(
      "Rang", "PREMIUM", "MOD", "MEDIA", "FAMOUS"
   ));
   private final StringSetting rankPrefix = this.add(new StringSetting("HugoSMP Rang-Prefix", "", 24));
   private final StringSetting clanTag = this.add(new StringSetting("HugoSMP Clan-Tag", "", 16));
   private final ModeSetting clanFormat = this.add(new ModeSetting(
      "Clan-Format", "[]", "[]", "<>", "()", "Ohne Klammern"
   ));
   private final ColorSetting rankColor = this.add(new ColorSetting("Rang-Farbe", 0xFFFFFF));
   private final ColorSetting clanColor = this.add(new ColorSetting("Clan-Farbe", 0x5AE6FF));
   private final ColorSetting nameColor = this.add(new ColorSetting("Name-Farbe", 0xFFFFFF));
   private final BoolSetting customNameColor = this.add(new BoolSetting("Eigene Namensfarbe verwenden", false));

   public Privat() {
      super("Privat", "Privat und HugoSMP-Stats als ein lokales Privat-Modul.");
      this.scalable = true;
   }


   public String value() {
      return this.fakeName.get();
   }

   public String getRankPrefix() {
      String custom = this.rankPrefix.get();
      if (custom != null && !custom.isBlank()) return custom.trim();

      return switch (this.rank.get()) {
         case "MOD" -> "MOD";
         case "MEDIA" -> "MEDIA";
         case "FAMOUS" -> "FAMOUS";
         default -> "PREMIUM";
      };
   }

   public int getRankDefaultColor() {
      return switch (this.rank.get()) {
         case "MOD" -> 0xFFFFFF55;
         case "MEDIA" -> 0xFFAA00FF;
         case "FAMOUS" -> 0xFFFF55FF;
         default -> 0xFFFFAA00;
      };
   }

   public static boolean hasVisualOverride() {
      Privat module = activeModule();
      return module != null && module.isEnabled()
         && (!module.fakeName.get().isBlank()
            || !module.rankPrefix.get().isBlank()
            || !module.clanTag.get().isBlank()
            || module.customNameColor.get()
            || !module.rank.is("PREMIUM"));
   }

   public static String activeName() {
      Privat module = activeModule();
      if (module == null || !module.isEnabled()) return "";
      String value = module.fakeName.get();
      return value == null ? "" : value.trim();
   }

   private static Privat activeModule() {
      try {
         return ModuleManager.get(Privat.class);
      } catch (Throwable ignored) {
         return null;
      }
   }

   /**
    * Replaces the local name and the existing HugoSMP rank directly before it.
    * The server rank (e.g. PREMIUM) is removed instead of being left beside
    * the configured fake rank. No brackets are added.
    */
   public static class_2561 replaceName(class_2561 original, String accountName) {
      Privat module = activeModule();
      if (original == null || module == null || !module.isEnabled()
         || accountName == null || accountName.isBlank() || !hasVisualOverride()) {
         return original;
      }

      String replacement = module.fakeName.get().trim();
      if (replacement.isBlank()) replacement = accountName;

      String plain = original.getString();
      int nameIndex = plain.indexOf(accountName);
      if (nameIndex < 0) {
         return replaceInTree(original, accountName, replacement, module);
      }

      String prefix = plain.substring(0, nameIndex);
      String suffix = plain.substring(nameIndex + accountName.length());
      String cleanedPrefix = removeExistingClanToken(removeExistingHugoRank(prefix));
      String cleanedSuffix = removeExistingClanToken(suffix);
      String rank = module.getRankPrefix();

      String desiredPrefix = cleanedPrefix;
      if (!rank.isBlank()) desiredPrefix += rank + " ";

      String rebuilt = desiredPrefix + replacement + cleanedSuffix;
      return rebuildText(original, rebuilt, baseStyle(original), module);
   }

   private static String removeExistingClanToken(String text) {
      // Remove the old HugoSMP clan decoration from either side of the name.
      String value = text;
      value = value.replaceAll("\\s*\\[[^\\]]{1,16}\\]\\s*", " ");
      value = value.replaceAll("\\s*<[^<>]{1,16}>\\s*", " ");
      value = value.replaceAll("\\s*\\([^()]{1,16}\\)\\s*", " ");
      return value.replaceAll("\\s+", " ");
   }

   private static String removeExistingHugoRank(String prefix) {
      String trimmed = prefix.stripTrailing();
      String[] ranks = {
         "PREMIUM", "MOD", "MEDIA", "FAMOUS", "ADMIN", "OWNER", "HELPER",
         "SUPPORT", "BUILDER", "YOUTUBER", "TIKTOKER", "VIP"
      };

      for (String rank : ranks) {
         if (trimmed.equals(rank)) return "";
         if (trimmed.endsWith(rank)) {
            String before = trimmed.substring(0, trimmed.length() - rank.length()).stripTrailing();
            return before.isEmpty() ? "" : before + " ";
         }
      }
      return prefix;
   }

   private static class_2561 rebuildText(
      class_2561 original, String replacementText, class_2583 baseStyle, Privat module
   ) {
      class_5250 out = class_2561.method_43470("").method_10862(original.method_10866());

      String rank = module.getRankPrefix();
      int rankStart = replacementText.indexOf(rank);
      int nameStart = rankStart >= 0 ? rankStart + rank.length() : -1;

      if (!rank.isBlank() && rankStart >= 0) {
         append(out, replacementText.substring(0, rankStart), baseStyle);
         append(out, rank, baseStyle.method_36139(
            module.rankPrefix.get().isBlank() ? module.getRankDefaultColor() : module.rankColor.rgb()
         ).method_10982(true));
         if (nameStart < replacementText.length() && replacementText.charAt(nameStart) == ' ') {
            append(out, " ", baseStyle);
            nameStart++;
         }
         appendNameAndRest(out, replacementText.substring(nameStart), baseStyle, module);
      } else {
         appendNameAndRest(out, replacementText, baseStyle, module);
      }
      return out;
   }

   private static void appendNameAndRest(
      class_5250 out, String value, class_2583 baseStyle, Privat module
   ) {
      class_2583 finalNameStyle = module.customNameColor.get()
         ? baseStyle.method_36139(module.nameColor.rgb())
         : baseStyle;
      append(out, value, finalNameStyle);

      String clan = module.clanTag.get().trim();
      if (!clan.isEmpty()) {
         String formattedClan = module.clanFormat.is("[]") ? "[" + clan + "]"
            : module.clanFormat.is("<>") ? "<" + clan + ">"
            : module.clanFormat.is("()") ? "(" + clan + ")"
            : clan;
         append(out, " " + formattedClan, baseStyle.method_36139(module.clanColor.rgb()).method_10982(true));
      }
   }

   private static class_2583 baseStyle(class_2561 text) {
      return text.method_10866();
   }

   private static class_2561 replaceInTree(
      class_2561 text, String accountName, String replacement, Privat module
   ) {
      class_5250 result = text.method_27662().method_10862(text.method_10866());
      class_7417 content = text.method_10851();

      if (content instanceof class_8828 literal) {
         class_5250 changed = replaceLiteral(
            literal.comp_737(), accountName, replacement, text.method_10866(), module
         );
         if (changed != null) result = changed;
      }

      for (class_2561 sibling : text.method_10855()) {
         result.method_10852(replaceInTree(sibling, accountName, replacement, module));
      }
      return result;
   }

   private static class_5250 replaceLiteral(
      String raw, String accountName, String replacement, class_2583 baseStyle, Privat module
   ) {
      int from = 0;
      int found = raw.indexOf(accountName, from);
      if (found < 0) return null;

      class_5250 out = class_2561.method_43470("").method_10862(baseStyle);
      int copiedUntil = 0;
      boolean replaced = false;
      while (found >= 0) {
         int end = found + accountName.length();
         boolean leftBoundary = found == 0 || !isNameCharacter(raw.charAt(found - 1));
         boolean rightBoundary = end == raw.length() || !isNameCharacter(raw.charAt(end));

         if (leftBoundary && rightBoundary) {
            replaced = true;
            append(out, raw.substring(copiedUntil, found), baseStyle);
            appendDecoratedName(out, replacement, baseStyle, module);
            copiedUntil = end;
         }

         from = end;
         found = raw.indexOf(accountName, Math.max(from, found + 1));
      }

      if (!replaced) return null;
      append(out, raw.substring(copiedUntil), baseStyle);
      return out;
   }

   private static void appendDecoratedName(
      class_5250 out, String name, class_2583 baseStyle, Privat module
   ) {
      String rank = module.getRankPrefix();
      if (!rank.isEmpty()) {
         int color = module.rankPrefix.get().isBlank()
            ? module.getRankDefaultColor()
            : module.rankColor.rgb();
         append(out, rank + " ", baseStyle.method_36139(color).method_10982(true));
      }

      class_2583 finalNameStyle = module.customNameColor.get()
         ? baseStyle.method_36139(module.nameColor.rgb())
         : baseStyle;
      append(out, name, finalNameStyle);

      String clan = module.clanTag.get().trim();
      if (!clan.isEmpty()) {
         String formattedClan = module.clanFormat.is("[]") ? "[" + clan + "]"
            : module.clanFormat.is("<>") ? "<" + clan + ">"
            : module.clanFormat.is("()") ? "(" + clan + ")"
            : clan;
         append(out, " " + formattedClan, baseStyle.method_36139(module.clanColor.rgb()).method_10982(true));
      }
   }

   private static void append(class_5250 parent, String value, class_2583 style) {
      if (!value.isEmpty()) {
         parent.method_10852(class_2561.method_43470(value).method_10862(style));
      }
   }

   private static boolean isNameCharacter(char c) {
      return c >= 'A' && c <= 'Z'
         || c >= 'a' && c <= 'z'
         || c >= '0' && c <= '9'
         || c == '_';
   }


   private static final int W = 128;
   private static final int H = 160;
   private static final String[] LABELS = {
      "Geld", "Gems", "Stars", "Kills", "Tode", "Clan", "Ping", "Keyall", "Spielzeit"
   };
   private static final String[] VALUES = {
      "104,18M", "550", "898", "886", "1,138", "???", "46ms", "21m 4s", "60d 5h"
   };
   private static final int[] VALUE_COLORS = {
      0xFFE066, 0xFF55FF, 0xFFC825, 0xFFAA00, 0xFF5555,
      0xFFFFFF, 0x55FFFF, 0x55FF55, 0x55FF55
   };
   private static final int[] ICON_COLORS = {
      0xFF55FF55, 0xFFFF5555, 0xFFFFD700, 0xFFFFAA00,
      0xFFAA0000, 0xFF55FFFF, 0xFF55FF55, 0xFFAAAAAA, 0xFFAAAAAA
   };

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{sw - W - 3, (sh - H) / 2 + 8};
   }

   private static int argb(int rgb) { return 0xFF000000 | (rgb & 0xFFFFFF); }

   private static void drawText(class_332 ctx, class_327 tr, String text, int x, int y, int color, boolean shadow) {
      ctx.method_51433(tr, text, x, y, argb(color), shadow);
   }

   private static void drawCentered(class_332 ctx, class_327 tr, String s, int cx, int y, int color) {
      int w = tr.method_1727(s);
      drawText(ctx, tr, s, cx - w / 2, y, color, true);
   }

   private static void drawItemIcon(class_332 ctx, int color, int x, int y) {
      ctx.method_25294(x + 2, y + 2, x + 10, y + 10, color);
      ctx.method_25294(x + 1, y + 3, x + 11, y + 9, color);
   }

   private static void drawCrown(class_332 ctx, int cx, int top, int height) {
      int gold = 0xFFF5B82E, dark = 0xFF8A5A1E;
      for (int r = 0; r < height; r++) {
         ctx.method_25294(cx - r - 1, top + r, cx + r + 2, top + r + 1, dark);
         ctx.method_25294(cx - r, top + r, cx + r + 1, top + r + 1, gold);
      }
   }

   @Override
   protected void draw(class_332 ctx) {
      class_310 mc = class_310.method_1551();
      class_327 tr = mc.field_1772;
      int x = 0, y = 0, by = y + 30;

      ctx.method_25294(x, by, x + W, y + H, 0xFF6B3F14);
      ctx.method_25294(x + 1, by + 1, x + W - 1, y + H - 1, 0xFFC98B2E);
      ctx.method_25294(x + 2, by + 2, x + W - 2, y + H - 2, 0xFF8A5A1E);
      ctx.method_25294(x + 3, by + 3, x + W - 3, y + H - 3, 0xF01E140C);

      int acc = 0xFFF5B82E;
      ctx.method_25294(x, by, x + 4, by + 4, acc);
      ctx.method_25294(x + W - 4, by, x + W, by + 4, acc);
      ctx.method_25294(x, y + H - 4, x + 4, y + H, acc);
      ctx.method_25294(x + W - 4, y + H - 4, x + W, y + H, acc);
      ctx.method_25294(x + 4, by - 3, x + 22, by + 1, 0xFFC98B2E);
      ctx.method_25294(x + W - 22, by - 3, x + W - 4, by + 1, 0xFFC98B2E);

      int cx = x + W / 2;
      drawCrown(ctx, cx - 26, y + 2, 7);
      drawCrown(ctx, cx, y - 2, 9);
      drawCrown(ctx, cx + 26, y + 2, 7);
      drawCentered(ctx, tr, "HUGO", cx, y + 8, 0xFF2020);
      drawCentered(ctx, tr, "SMP", cx, y + 29, 0xFF8A55E0);

      for (int i = 0; i < LABELS.length; i++) {
         int ly = y + 46 + i * 9;
         drawItemIcon(ctx, ICON_COLORS[i], x + 5, ly - 1);
         int lx = x + 17;
         drawText(ctx, tr, LABELS[i], lx, ly, 0xFFFFFF, true);
         int vx = lx + tr.method_1727(LABELS[i]) + 4;
         drawText(ctx, tr, VALUES[i], vx, ly, VALUE_COLORS[i], true);
      }
      drawText(ctx, tr, "HUGOSMP.NET", x + 8, y + 131, 0xFF3030, true);
      drawText(ctx, tr, "10410/13750", x + 8, y + 141, 0x55FF55, true);
      int[] bottom = {0xFFFFD700, 0xFF55FFFF, 0xFFAAAAAA, 0xFFFFD700};
      for (int b = 0; b < bottom.length; b++) drawItemIcon(ctx, bottom[b], x + W - 50 + b * 11, y + H - 14);
   }
}
