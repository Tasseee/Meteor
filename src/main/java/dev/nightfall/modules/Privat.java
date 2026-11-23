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
   private final ColorSetting nameColor = this.add(new ColorSetting("Name-Farbe", 0xFFAAAAAA));
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

   public Privat() {
      super("Privat", "Privat und HugoSMP-Stats als ein lokales Privat-Modul.");
      this.scalable = true;
   }

   public String getScoreboardValue(String label, String original) {
      if (label == null) return original;

      return switch (normalizeScoreboardLabel(label)) {
         case "geld" -> geld.get();
         case "gems" -> gems.get();
         case "stars" -> stars.get();
         case "kills" -> kills.get();
         case "tode" -> tode.get();
         case "clan" -> clan.get();
         case "ping" -> ping.get();
         case "keyall" -> keyall.get();
         case "spielzeit" -> spielzeit.get();
         default -> original;
      };
   }

   private static String normalizeScoreboardLabel(String label) {
      String normalized = label.replaceAll("§[0-9A-FK-ORa-fk-or]", "")
         .replaceAll("\\s+", " ").trim().toLowerCase(java.util.Locale.ROOT);
      return normalized.replaceAll("[\\s:：]+$", "").trim();
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
         case "FAMOUS" -> 0xFFFFAA00;
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
    * Replaces only the local account name and clan decoration.
    * The real server rank stays visible.
    */
   public static class_2561 replaceName(class_2561 original, String accountName) {
      Privat module = activeModule();
      if (original == null || module == null || !module.isEnabled()
         || accountName == null || accountName.isBlank() || !hasVisualOverride()) {
         return original;
      }

      return replaceInTree(original, accountName, module.fakeName.get(), module);
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
      String cleaned = removeClanFromLiteral(raw);
      int from = 0;
      int found = cleaned.indexOf(accountName, from);

      if (found < 0) return null;

      class_5250 out = class_2561.method_43470("").method_10862(baseStyle);
      int copiedUntil = 0;
      boolean replaced = false;

      while (found >= 0) {
         int end = found + accountName.length();
         boolean leftBoundary = found == 0 || !isNameCharacter(cleaned.charAt(found - 1));
         boolean rightBoundary = end == cleaned.length() || !isNameCharacter(cleaned.charAt(end));

         if (leftBoundary && rightBoundary) {
            replaced = true;
            String beforeName = cleaned.substring(copiedUntil, found);
            append(out, beforeName, baseStyle);

            appendPlainName(
               out,
               replacement == null || replacement.isBlank() ? accountName : replacement.trim(),
               baseStyle,
               module
            );
            copiedUntil = end;
         }

         from = end;
         found = cleaned.indexOf(accountName, Math.max(from, found + 1));
      }

      if (!replaced) return null;
      append(out, cleaned.substring(copiedUntil), baseStyle);
      return out;
   }

   private static String removeClanFromLiteral(String text) {
      if (text == null || text.isEmpty()) return text;

      String result = text;
      result = stripRankOrClanTokens(result, "\\[([^\\]]{1,16})\\]");
      result = stripRankOrClanTokens(result, "<([^<>]{1,16})>");
      result = stripRankOrClanTokens(result, "\\(([^()]{1,16})\\)");
      return result.replaceAll("\\s+", " ").trim();
   }

   private static String stripRankOrClanTokens(String text, String regex) {
      java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(text);
      StringBuffer out = new StringBuffer();

      while (matcher.find()) {
         matcher.appendReplacement(out, " ");
      }

      matcher.appendTail(out);
      return out.toString();
   }

   private static void appendPlainName(
      class_5250 out, String name, class_2583 baseStyle, Privat module
   ) {
      String rank = module.getRankPrefix();
      if (rank != null && !rank.isBlank()) {
         int color = module.rankPrefix.get().isBlank()
            ? module.getRankDefaultColor() : module.rankColor.rgb();
         append(out, rank + " ", baseStyle.method_36139(color).method_10982(true));
      }

      class_2583 nameStyle = module.customNameColor.get()
         ? baseStyle.method_36139(module.nameColor.rgb())
         : baseStyle;
      append(out, name, nameStyle);

      String clan = module.clanTag.get() == null ? "" : module.clanTag.get().trim();
      if (!clan.isEmpty()) {
         String formattedClan = module.clanFormat.is("[]") ? "[" + clan + "]"
            : module.clanFormat.is("<>") ? "<" + clan + ">"
            : module.clanFormat.is("()") ? "(" + clan + ")"
            : clan;

         append(
            out,
            " " + formattedClan,
            baseStyle.method_36139(module.clanColor.rgb()).method_10982(true)
         );
      }
   }

   private static void append(class_5250 parent, String value, class_2583 style) {
      if (value != null && !value.isEmpty()) {
         parent.method_10852(class_2561.method_43470(value).method_10862(style));
      }
   }

   private static boolean isNameCharacter(char c) {
      return c >= 'A' && c <= 'Z'
         || c >= 'a' && c <= 'z'
         || c >= '0' && c <= '9'
         || c == '_';
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{sw - 131, (sh - 160) / 2 + 8};
   }

   @Override
   protected void draw(net.minecraft.class_332 ctx) {
      // Intentionally empty: Privat does not render a second fake scoreboard.
      // PrivatScoreboardMixin changes only the values of the real server sidebar.
   }
}
