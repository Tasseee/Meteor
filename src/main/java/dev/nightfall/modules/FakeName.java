package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.StringSetting;
import net.minecraft.class_2561;
import net.minecraft.class_5250;
import net.minecraft.class_7417;
import net.minecraft.class_8828;

/**
 * Client-only replacement for the local player's visible name.
 * It never changes the authenticated profile, UUID, or network identity.
 */
public final class FakeName extends Module {
   private final StringSetting fakeName = this.add(new StringSetting("Fake Name", "", 32));

   public FakeName() {
      super("FakeName", "Ersetzt deinen Namen lokal in Tab-Liste und eigener Namensanzeige.", Category.VISUALS);
   }

   public String value() {
      return this.fakeName.get();
   }

   /** Returns an active replacement, or an empty string when the module is inactive. */
   public static String activeName() {
      try {
         FakeName module = ModuleManager.get(FakeName.class);
         if (!module.isEnabled()) return "";
         String value = module.value();
         return value == null ? "" : value.trim();
      } catch (Throwable ignored) {
         return "";
      }
   }

   /**
    * Replaces only a literal occurrence of the original account name in the text tree.
    * Every text node keeps its original style and all rank/team prefix/suffix siblings.
    */
   public static class_2561 replaceName(class_2561 original, String accountName) {
      String replacement = activeName();
      if (original == null || accountName == null || accountName.isBlank()
         || replacement.isBlank() || accountName.equals(replacement)) {
         return original;
      }
      return replaceInTree(original, accountName, replacement);
   }

   private static class_2561 replaceInTree(class_2561 text, String accountName, String replacement) {
      class_5250 result = text.method_27662().method_10862(text.method_10866());
      class_7417 content = text.method_10851();

      if (content instanceof class_8828 literal) {
         String raw = literal.comp_737();
         String changed = replaceAccountToken(raw, accountName, replacement);
         if (!changed.equals(raw)) {
            result = class_2561.method_43470(changed).method_10862(text.method_10866());
         }
      }

      for (class_2561 sibling : text.method_10855()) {
         result.method_10852(replaceInTree(sibling, accountName, replacement));
      }
      return result;
   }

   private static String replaceAccountToken(String text, String accountName, String replacement) {
      int from = 0;
      int found = text.indexOf(accountName, from);
      if (found < 0) return text;

      StringBuilder out = null;
      int copiedUntil = 0;
      while (found >= 0) {
         int end = found + accountName.length();
         boolean leftBoundary = found == 0 || !isNameCharacter(text.charAt(found - 1));
         boolean rightBoundary = end == text.length() || !isNameCharacter(text.charAt(end));
         if (leftBoundary && rightBoundary) {
            if (out == null) out = new StringBuilder(text.length() + replacement.length());
            out.append(text, copiedUntil, found).append(replacement);
            copiedUntil = end;
         }
         from = end;
         found = text.indexOf(accountName, Math.max(from, found + 1));
      }
      if (out == null) return text;
      return out.append(text, copiedUntil, text.length()).toString();
   }

   private static boolean isNameCharacter(char c) {
      return c >= 'A' && c <= 'Z'
         || c >= 'a' && c <= 'z'
         || c >= '0' && c <= '9'
         || c == '_';
   }
}