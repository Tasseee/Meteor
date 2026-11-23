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

   private final StringSetting geld = this.add(new StringSetting("Geld", "0M", 24));
   private final StringSetting gems = this.add(new StringSetting("Gems", "0", 24));
   private final StringSetting stars = this.add(new StringSetting("Stars", "0", 24));
   private final StringSetting kills = this.add(new StringSetting("Kills", "0", 24));
   private final StringSetting tode = this.add(new StringSetting("Tode", "0", 24));
   private final StringSetting clan = this.add(new StringSetting("Clan", "???", 24));
   private final StringSetting ping = this.add(new StringSetting("Ping", "0ms", 24));
   private final StringSetting keyall = this.add(new StringSetting("Keyall", "0m 0s", 24));
   private final StringSetting spielzeit = this.add(new StringSetting("Spielzeit", "0d 0h", 24));

   public String getScoreboardValue(int index, String original) {
      return switch (index) {
         case 0 -> geld.get();
         case 1 -> gems.get();
         case 2 -> stars.get();
         case 3 -> kills.get();
         case 4 -> tode.get();
         case 5 -> clan.get();
         case 6 -> ping.get();
         case 7 -> keyall.get();
         case 8 -> spielzeit.get();
         default -> original;
      };
   }

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

      String plain = original.getString();
      // Only alter text components which actually represent this local account.
      // The original prefix/suffix is deliberately discarded: it may contain the
      // server-provided rank and clan tag, which must not leak into the fake display.
      if (plain == null || !plain.contains(accountName)) {
         return original;
      }

      String replacement = module.fakeName.get();
      if (replacement == null || replacement.isBlank()) replacement = accountName;
      else replacement = replacement.trim();

      // Build one clean component containing only our configured rank, name and clan.
      // appendDecoratedName applies the selected colors and clan bracket style.
      class_5250 out = class_2561.method_43470("").method_10862(original.method_10866());
      appendDecoratedName(out, replacement, original.method_10866(), module);
      return out;
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

   private static class_2561 rebuildPlainName(
      class_2561 original, String replacementText, class_2583 baseStyle, Privat module
   ) {
      class_5250 out = class_2561.method_43470("").method_10862(original.method_10866());
      String name = module.customNameColor.get()
         ? replacementText
         : replacementText;
      class_2583 nameStyle = module.customNameColor.get()
         ? baseStyle.method_36139(module.nameColor.rgb())
         : baseStyle;
      append(out, name, nameStyle);
      return out;
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
         : baseStyle.method_36139(
            module.rankPrefix.get().isBlank() ? module.getRankDefaultColor() : module.rankColor.rgb()
         );
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
            appendPlainName(out, replacement, baseStyle, module);
            copiedUntil = end;
         }

         from = end;
         found = raw.indexOf(accountName, Math.max(from, found + 1));
      }

      if (!replaced) return null;
      append(out, raw.substring(copiedUntil), baseStyle);
      return out;
   }

   private static void appendPlainName(
      class_5250 out, String name, class_2583 baseStyle, Privat module
   ) {
      class_2583 style = module.customNameColor.get()
         ? baseStyle.method_36139(module.nameColor.rgb())
         : baseStyle;
      append(out, name, style);
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
      // Privat no longer renders a separate fake-stats panel.
      // The real server scoreboard remains visible; its numeric values are masked by PrivatScoreboardMixin.
   }
}
