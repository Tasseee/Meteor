package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.StringSetting;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_5250;

/**
 * Local-only identity display filter. It changes only this client's rendered
 * chat, tab-list entry and own third-person nameplate; it does not change the
 * account name or identity seen by the server or other players.
 */
public final class Privat extends HudModule {
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
      super("FakeName", "Zeigt nur den ausgewaehlten Fake-Namen in Chat, Tab-Liste und F5; Rang- und Clan-Praefixe werden entfernt.");
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

   /**
    * Chat format: remove the complete local identity prefix before the
    * account name, then show only the selected fake name and the message body.
    * If chat display is disabled, keep the body but omit the sender line.
    */
   public static class_2561 replaceChatName(class_2561 original, String accountName) {
      Privat module = activeModule();
      if (original == null || module == null || !module.isEnabled()
         || accountName == null || accountName.isBlank()) {
         return original;
      }

      String raw = original.getString();
      int start = lastLineStart(raw);
      int nameStart = findNameToken(raw, accountName, start);
      if (nameStart < 0) return original;

      int nameEnd = nameStart + accountName.length();
      String afterName = raw.substring(nameEnd);
      if (!hasChatSpeakerSeparator(afterName)) return original;

      // Discard any extra rank/clan decoration between the account name and
      // the chat separator as well as the prefix that appeared before it.
      String messageBody = chatMessageBody(afterName);
      String selectedName = module.showInChat.get() ? module.fakeName.get().trim() : "";

      class_5250 result = class_2561.method_43470("").method_10862(original.method_10866());
      append(result, raw.substring(0, start), original.method_10866());
      if (!selectedName.isEmpty()) {
         class_2583 nameStyle = module.customNameColor.get()
            ? original.method_10866().method_36139(module.nameColor.rgb())
            : original.method_10866();
         append(result, selectedName, nameStyle);
      }
      append(result, messageBody, original.method_10866());
      return result;
   }

   /**
    * Tab names are replaced wholesale, not edited in-place. This discards any
    * server-supplied rank, clan, prefix, or suffix attached to the local entry.
    */
   public static class_2561 replaceTabName(class_2561 original, String accountName) {
      Privat module = activeModule();
      if (original == null || module == null || !module.isEnabled()) return original;
      return selectedNameText(original, module.showInTab.get(), module);
   }

   /** F5 nameplates are also replaced wholesale to remove all server decorations. */
   public static class_2561 replaceF5Name(class_2561 original, String accountName) {
      Privat module = activeModule();
      if (original == null || module == null || !module.isEnabled()) return original;
      return selectedNameText(original, module.showInF5.get(), module);
   }

   private static class_2561 selectedNameText(class_2561 original, boolean enabled, Privat module) {
      String text = enabled ? module.fakeName.get().trim() : "";
      class_5250 result = class_2561.method_43470(text).method_10862(original.method_10866());
      if (enabled && module.customNameColor.get() && !text.isEmpty()) {
         result.method_10862(original.method_10866().method_36139(module.nameColor.rgb()));
      }
      return result;
   }

   private static int lastLineStart(String text) {
      return Math.max(text.lastIndexOf('\n'), text.lastIndexOf('\r')) + 1;
   }

   private static int findNameToken(String text, String accountName, int from) {
      int found = text.indexOf(accountName, Math.max(0, from));
      while (found >= 0) {
         int end = found + accountName.length();
         boolean leftBoundary = found == 0 || !isNameCharacter(text.charAt(found - 1));
         boolean rightBoundary = end == text.length() || !isNameCharacter(text.charAt(end));
         if (leftBoundary && rightBoundary) return found;
         found = text.indexOf(accountName, end);
      }
      return -1;
   }

   private static boolean hasChatSpeakerSeparator(String suffix) {
      return chatSeparatorIndex(suffix) >= 0;
   }

   private static int chatSeparatorIndex(String suffix) {
      String value = suffix.stripLeading();
      for (int i = 0; i < value.length(); i++) {
         char c = value.charAt(i);
         if (c != ':' && c != '»' && c != '>') continue;

         String decoration = value.substring(0, i).trim();
         while (!decoration.isEmpty()) {
            if (decoration.startsWith("[")) {
               int end = decoration.indexOf(']');
               if (end < 0 || end > 25) return -1;
               decoration = decoration.substring(end + 1).trim();
            } else if (decoration.startsWith("<")) {
               int end = decoration.indexOf('>');
               if (end < 0 || end > 25) return -1;
               decoration = decoration.substring(end + 1).trim();
            } else if (decoration.startsWith("(")) {
               int end = decoration.indexOf(')');
               if (end < 0 || end > 25) return -1;
               decoration = decoration.substring(end + 1).trim();
            } else if (startsWithIgnoreCase(decoration, "whispers")) {
               decoration = decoration.substring(8).trim();
            } else if (startsWithIgnoreCase(decoration, "flüstert")) {
               decoration = decoration.substring(8).trim();
            } else if (startsWithIgnoreCase(decoration, "fluestert")) {
               decoration = decoration.substring(9).trim();
            } else {
               return -1;
            }
         }
         return i;
      }
      return -1;
   }

   private static boolean startsWithIgnoreCase(String text, String prefix) {
      return text.regionMatches(true, 0, prefix, 0, prefix.length());
   }

   private static String chatMessageBody(String suffix) {
      String value = suffix.stripLeading();
      int separator = chatSeparatorIndex(value);
      return separator >= 0 ? value.substring(separator) : value;
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
      // Intentionally empty: FakeName filters only local identity render paths
      // and selected sidebar values; it does not draw a duplicate HUD.
   }
}
