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

/**
 * Client-only nameplate customization. It never changes the authenticated
 * profile, UUID, permissions, rank, clan membership, or network identity.
 */
public final class FakeName extends Module {
   private final StringSetting fakeName = this.add(new StringSetting("Fake Name", "", 32));
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
      super("FakeName", "Lokaler Name mit frei anpassbarem Rang, Clan-Tag und Farben (nur deine Ansicht).", Category.VISUALS);
   }

   public String value() {
      return this.fakeName.get();
   }

   /** True while any local nameplate customization is configured and the module is enabled. */
   public static boolean hasVisualOverride() {
      FakeName module = activeModule();
      return module != null && module.isEnabled()
         && (!module.fakeName.get().isBlank()
            || !module.rankPrefix.get().isBlank()
            || !module.clanTag.get().isBlank());
   }

   /** Returns the configured replacement name, or an empty string when none is configured. */
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
    * Replaces only the local account-name token. Original sibling components
    * (including server-provided prefixes/suffixes) are kept, while the inserted
    * rank, clan and name parts receive independent RGB styles.
    */
   public static class_2561 replaceName(class_2561 original, String accountName) {
      FakeName module = activeModule();
      if (original == null || module == null || !module.isEnabled()
         || accountName == null || accountName.isBlank() || !hasVisualOverride()) {
         return original;
      }

      String replacement = module.fakeName.get().trim();
      if (replacement.isBlank()) replacement = accountName;
      return replaceInTree(original, accountName, replacement, module);
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
      String rank = module.rankPrefix.get().trim();
      if (!rank.isEmpty()) {
         append(out, rank + " ", baseStyle.method_36139(module.rankColor.rgb()).method_10982(true));
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
