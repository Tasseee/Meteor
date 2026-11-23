package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.StringSetting;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_5250;
import net.minecraft.class_7417;
import net.minecraft.class_8828;

/**
 * Local-only identity display filter. It changes only this client's rendered
 * chat, tab-list entry and own third-person nameplate; it does not change the
 * account name or identity seen by the server or other players.
 */
public final class Privat extends HudModule {
   private static final Pattern IDENTITY_TOKEN_AT_END = Pattern.compile(
      "(?:\\s*\\[[^\\]]{1,24}\\]|\\s*<[^<>]{1,24}>|\\s*\\([^()]{1,24}\\))\\s*$"
   );
   private static final Pattern IDENTITY_TOKEN_ONLY = Pattern.compile(
      "^(?:\\[[^\\]]{1,24}\\]|<[^<>]{1,24}>|\\([^()]{1,24}\\))$"
   );

   private final StringSetting fakeName = this.add(new StringSetting("Name", "", 32));
   private final BoolSetting showInChat = this.add(new BoolSetting("Im Chat anzeigen", true));
   private final BoolSetting showInTab = this.add(new BoolSetting("In Tab-Liste anzeigen", true));
   private final BoolSetting showInF5 = this.add(new BoolSetting("In F5 anzeigen", true));
   private final ColorSetting nameColor = this.add(new ColorSetting("Name-Farbe", 0xFFFFFF));
   private final BoolSetting customNameColor = this.add(new BoolSetting("Eigene Namensfarbe verwenden", false));

   private final StringSetting geld = this.add(new StringSetting("Geld", "0M", 24));
   private final StringSetting gems = this.add(new StringSetting("Gems", "0", 24));
   private final StringSetting stars = this.add(new StringSetting("Stars", "0", 24));
   private final StringSetting kills = this.add(new StringSetting("Kills", "0", 24));
   private final StringSetting tode = this.add(new StringSetting("Tode", "0", 24));
   private final StringSetting ping = this.add(new StringSetting("Ping", "0ms", 24));
   private final StringSetting keyall = this.add(new StringSetting("Keyall", "0m 0s", 24));
   private final StringSetting spielzeit = this.add(new StringSetting("Spielzeit", "0d 0h", 24));

   public Privat() {
      super("FakeName", "Ersetzt den eigenen Namen lokal in Chat, Tab-Liste und F5; echte Rang- und Clan-Tags werden ausgeblendet.");
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
         case "clan", "rang", "rank", "name" -> "";
         case "ping" -> ping.get();
         case "keyall" -> keyall.get();
         case "spielzeit" -> spielzeit.get();
         default -> original;
      };
   }

   private static String normalizeScoreboardLabel(String label) {
      return label.trim().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
   }

   public String value() {
      return this.fakeName.get();
   }

   public static boolean hasVisualOverride() {
      Privat module = activeModule();
      return module != null && module.isEnabled();
   }

   public static boolean shouldRenderF5Nameplate() {
      Privat module = activeModule();
      return module != null && module.isEnabled()
         && module.showInF5.get()
         && module.fakeName.get() != null
         && !module.fakeName.get().isBlank();
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

   public static class_2561 replaceChatName(class_2561 original, String accountName) {
      Privat module = activeModule();
      return replaceForSurface(original, accountName, module, module != null && module.showInChat.get());
   }

   public static class_2561 replaceTabName(class_2561 original, String accountName) {
      Privat module = activeModule();
      return replaceForSurface(original, accountName, module, module != null && module.showInTab.get());
   }

   public static class_2561 replaceF5Name(class_2561 original, String accountName) {
      Privat module = activeModule();
      return replaceForSurface(original, accountName, module, module != null && module.showInF5.get());
   }

   private static class_2561 replaceForSurface(
      class_2561 original, String accountName, Privat module, boolean showFakeName
   ) {
      if (original == null || module == null || !module.isEnabled()
         || accountName == null || accountName.isBlank()) {
         return original;
      }

      String replacement = showFakeName ? module.fakeName.get() : "";
      if (replacement == null) replacement = "";
      return replaceInTree(original, accountName, replacement.trim(), module);
   }

   private static class_2561 replaceInTree(
      class_2561 text, String accountName, String replacement, Privat module
   ) {
      class_5250 result = text.method_27662().method_10862(text.method_10866());
      class_7417 content = text.method_10851();
      List<class_2561> siblings = text.method_10855();

      if (content instanceof class_8828 literal) {
         String raw = literal.comp_737();
         boolean standalonePrefix = isIdentityToken(raw)
            && containsAccountNameInSiblings(siblings, 0, accountName);
         if (!standalonePrefix) {
            class_5250 changed = replaceLiteral(raw, accountName, replacement, text.method_10866(), module);
            if (changed != null) result = changed;
         }
      }

      for (int i = 0; i < siblings.size(); i++) {
         class_2561 sibling = siblings.get(i);
         if (isIdentityToken(sibling.getString())
            && containsAccountNameInSiblings(siblings, i + 1, accountName)) {
            continue;
         }
         result.method_10852(replaceInTree(sibling, accountName, replacement, module));
      }
      return result;
   }

   private static boolean containsAccountNameInSiblings(
      List<class_2561> siblings, int start, String accountName
   ) {
      if (siblings == null || start >= siblings.size()) return false;
      StringBuilder remaining = new StringBuilder();
      for (int i = start; i < siblings.size(); i++) {
         remaining.append(siblings.get(i).getString());
      }
      return containsNameToken(remaining.toString(), accountName);
   }

   private static boolean containsNameToken(String text, String accountName) {
      int from = 0;
      int found = text.indexOf(accountName, from);
      while (found >= 0) {
         int end = found + accountName.length();
         boolean leftBoundary = found == 0 || !isNameCharacter(text.charAt(found - 1));
         boolean rightBoundary = end == text.length() || !isNameCharacter(text.charAt(end));
         if (leftBoundary && rightBoundary) return true;
         from = Math.max(end, found + 1);
         found = text.indexOf(accountName, from);
      }
      return false;
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
            String beforeName = stripIdentityTagsAtEnd(raw.substring(copiedUntil, found));
            append(out, beforeName, baseStyle);

            if (!replacement.isBlank()) {
               class_2583 nameStyle = module.customNameColor.get()
                  ? baseStyle.method_36139(module.nameColor.rgb())
                  : baseStyle;
               append(out, replacement, nameStyle);
            }
            copiedUntil = end;
         }

         from = end;
         found = raw.indexOf(accountName, Math.max(from, found + 1));
      }

      if (!replaced) return null;
      append(out, raw.substring(copiedUntil), baseStyle);
      return out;
   }

   /** Removes only bracketed tags immediately before the matched local name. */
   private static String stripIdentityTagsAtEnd(String prefix) {
      String result = prefix;
      while (true) {
         Matcher matcher = IDENTITY_TOKEN_AT_END.matcher(result);
         if (!matcher.find()) return result;
         result = result.substring(0, matcher.start());
      }
   }

   private static boolean isIdentityToken(String text) {
      return text != null && IDENTITY_TOKEN_ONLY.matcher(text.trim()).matches();
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
      // Intentionally empty: this module filters local identity and selected
      // sidebar values; it does not draw a duplicate HUD.
   }
}
