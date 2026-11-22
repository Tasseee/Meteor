package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
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

public final class FakeName extends Module {
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

   public FakeName() {
      super("FakeName", "Lokaler Name mit HugoSMP-Rang, Clan-Tag und Farben (nur deine Ansicht).", Category.VISUALS);
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
      FakeName module = activeModule();
      return module != null && module.isEnabled()
         && (!module.fakeName.get().isBlank()
            || !module.rankPrefix.get().isBlank()
            || !module.clanTag.get().isBlank()
            || module.customNameColor.get()
            || !module.rank.is("PREMIUM"));
   }

   public static String activeName() {
      FakeName module = activeModule();
      if (module == null || !module.isEnabled()) return "";
      String value = module.fakeName.get();
      return value == null ? "" : value.trim();
   }

   private static FakeName activeModule() {
      try {
         return ModuleManager.get(FakeName.class);
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
      FakeName module = activeModule();
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
      String cleanedPrefix = removeExistingHugoRank(prefix);
      String rank = module.getRankPrefix();

      String desiredPrefix = cleanedPrefix;
      if (!rank.isBlank()) desiredPrefix += rank + " ";

      String rebuilt = desiredPrefix + replacement + plain.substring(nameIndex + accountName.length());
      return rebuildText(original, rebuilt, baseStyle(original), module);
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
      class_2561 original, String replacementText, class_2583 baseStyle, FakeName module
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
      class_5250 out, String value, class_2583 baseStyle, FakeName module
   ) {
      String clan = module.clanTag.get().trim();

      if (!clan.isEmpty()) {
         String formattedClan = module.clanFormat.is("[]") ? "[" + clan + "]"
            : module.clanFormat.is("<>") ? "<" + clan + ">"
            : module.clanFormat.is("()") ? "(" + clan + ")"
            : clan;
         append(out, formattedClan + " ", baseStyle.method_36139(module.clanColor.rgb()).method_10982(true));
      }

      class_2583 finalNameStyle = module.customNameColor.get()
         ? baseStyle.method_36139(module.nameColor.rgb())
         : baseStyle;
      append(out, value, finalNameStyle);
   }

   private static class_2583 baseStyle(class_2561 text) {
      return text.method_10866();
   }

   private static class_2561 replaceInTree(
      class_2561 text, String accountName, String replacement, FakeName module
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
      String raw, String accountName, String replacement, class_2583 baseStyle, FakeName module
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
      class_5250 out, String name, class_2583 baseStyle, FakeName module
   ) {
      String rank = module.getRankPrefix();
      if (!rank.isEmpty()) {
         int color = module.rankPrefix.get().isBlank()
            ? module.getRankDefaultColor()
            : module.rankColor.rgb();
         append(out, rank + " ", baseStyle.method_36139(color).method_10982(true));
      }

      String clan = module.clanTag.get().trim();
      if (!clan.isEmpty()) {
         String formattedClan = module.clanFormat.is("[]") ? "[" + clan + "]"
            : module.clanFormat.is("<>") ? "<" + clan + ">"
            : module.clanFormat.is("()") ? "(" + clan + ")"
            : clan;
         append(out, formattedClan + " ", baseStyle.method_36139(module.clanColor.rgb()).method_10982(true));
      }

      class_2583 finalNameStyle = module.customNameColor.get()
         ? baseStyle.method_36139(module.nameColor.rgb())
         : baseStyle;
      append(out, name, finalNameStyle);
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
}
