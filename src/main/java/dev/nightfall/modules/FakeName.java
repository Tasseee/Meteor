package dev.nightfall.modules;

import dev.nightfall.config.Config;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.ModuleManager;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.StringSetting;
import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_5250;
import net.minecraft.class_11908;
import net.minecraft.class_437;
import net.minecraft.class_310;
import net.minecraft.class_332;
import org.lwjgl.glfw.GLFW;

/**
 * Local-only identity display filter. It changes only this client's rendered
 * chat, tab-list entry and own third-person nameplate; it does not change the
 * account name or identity seen by the server or other players.
 */
public final class FakeName extends HudModule {
   private final StringSetting fakeName = this.add(new StringSetting("Name", "", 32));
   private final BoolSetting showInChat = this.add(new BoolSetting("Im Chat anzeigen", true));
   private final BoolSetting showInTab = this.add(new BoolSetting("In Tab-Liste anzeigen", true));
   private final BoolSetting showInF5 = this.add(new BoolSetting("In F5 anzeigen", true));
   private final StringSetting rank = this.add(new StringSetting("F5-Rang", "Keiner", 16));
   private final ColorSetting nameColor = this.add(new ColorSetting("Name-Farbe", 0xFFFFFF));
   private final BoolSetting customNameColor = this.add(new BoolSetting("Eigene Namensfarbe verwenden", false));

   private final BoolSetting leaderboardEnabled = this.add(new BoolSetting("HugoSMP-Leaderboard", true));
   private final StringSetting geld = this.add(new StringSetting("Geld", "29.13M", 24));
   private final StringSetting gems = this.add(new StringSetting("Gems", "0", 24));
   private final StringSetting stars = this.add(new StringSetting("Stars", "918", 24));
   private final StringSetting kills = this.add(new StringSetting("Kills", "196", 24));
   private final StringSetting tode = this.add(new StringSetting("Tode", "1,583", 24));
   private final StringSetting clan = this.add(new StringSetting("Clan", "Luchs", 24));
   private final StringSetting ping = this.add(new StringSetting("Ping", "61ms", 24));
   private final StringSetting keyall = this.add(new StringSetting("Keyall", "59m 56s", 24));
   private final StringSetting spielzeit = this.add(new StringSetting("Spielzeit", "18d 20h", 24));
   private final StringSetting spielerzahl = this.add(new StringSetting("Spielerzahl", "9351/14500", 20));

   public FakeName() {
      super("FakeName", "Lokale FakeName-Anzeige und editierbares HugoSMP-Leaderboard.");
      this.scalable = true;
      this.w = 210;
      this.h = 266;
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
         case "rang", "rank", "name" -> "";
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
      FakeName module = activeModule();
      return module != null && module.isEnabled()
         && module.fakeName.get() != null && !module.fakeName.get().isBlank();
   }

   public static boolean shouldRenderCustomScoreboard() {
      FakeName module = activeModule();
      return module != null && module.isEnabled() && module.leaderboardEnabled.get();
   }

   public boolean isLeaderboardEnabled() {
      return this.leaderboardEnabled.get();
   }

   public void setLeaderboardEnabled(boolean enabled) {
      this.leaderboardEnabled.set(enabled);
      Config.save();
   }

   public static StringSetting[] leaderboardFields(FakeName module) {
      return new StringSetting[]{
         module.geld, module.gems, module.stars, module.kills, module.tode,
         module.clan, module.ping, module.keyall, module.spielzeit, module.spielerzahl,
         module.fakeName
      };
   }

   public static String[] leaderboardLabels() {
      return new String[]{
         "Geld", "Gems", "Stars", "Kills", "Tode", "Clan", "Ping",
         "Keyall", "Spielzeit", "Online", "F5-Name"
      };
   }

   public static boolean hasF5Override() {
      FakeName module = activeModule();
      return module != null && module.isEnabled() && module.showInF5.get();
   }

   public static boolean shouldRenderF5Nameplate() {
      return hasF5Override();
   }

   public void cycleRank() {
      String[] ranks = new String[]{"Keiner", "MOD", "FAMOUS", "Media", "Premium", "Owner"};
      String current = this.rank.get();
      int index = 0;
      for (int i = 0; i < ranks.length; i++) {
         if (ranks[i].equalsIgnoreCase(current)) {
            index = i;
            break;
         }
      }
      this.rank.set(ranks[(index + 1) % ranks.length]);
      Config.save();
   }

   public String selectedRank() {
      String value = this.rank.get();
      for (String allowed : new String[]{"Keiner", "MOD", "FAMOUS", "Media", "Premium", "Owner"}) {
         if (allowed.equalsIgnoreCase(value)) return allowed;
      }
      return "Keiner";
   }

   private static int rankColor(String rank) {
      return switch (rank) {
         case "MOD" -> 0xFFFF55;
         case "FAMOUS" -> 0xFFAA00;
         case "Media" -> 0xAA00AA;
         case "Premium" -> 0xFF55FF;
         case "Owner" -> 0xAA0000;
         default -> 0xFFFFFF;
      };
   }

   /** Build one single F5 nameplate line: optional rank, name and optional clan. */
   public static class_2561 composeF5Name(class_2561 original, String accountName) {
      FakeName module = activeModule();
      if (module == null || !module.isEnabled()) return original;
      String shownName = module.fakeName.get() == null ? "" : module.fakeName.get().trim();
      if (shownName.isEmpty()) shownName = accountName == null ? "" : accountName.trim();
      String selectedRank = module.selectedRank();
      String selectedClan = module.clan.get() == null ? "" : module.clan.get().trim();

      class_2583 defaultStyle = original == null
         ? class_2561.method_43470("").method_10866()
         : original.method_10866();
      class_5250 result = class_2561.method_43470("");

      if (!selectedRank.equals("Keiner")) {
         append(result, "[" + selectedRank + "] ", defaultStyle.method_36139(rankColor(selectedRank)));
      }
      class_2583 shownNameStyle = module.customNameColor.get()
         ? defaultStyle.method_36139(module.nameColor.rgb())
         : defaultStyle;
      append(result, shownName, shownNameStyle);

      if (!selectedClan.isEmpty() && !selectedClan.equals("-")) {
         append(result, " [" + selectedClan + "]", defaultStyle.method_36139(0xB45CFF));
      }
      return result;
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
    * Chat format: remove the complete local identity prefix before the
    * account name, then show only the selected fake name and the message body.
    * If chat display is disabled, keep the body but omit the sender line.
    */
   public static class_2561 replaceChatName(class_2561 original, String accountName) {
      FakeName module = activeModule();
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
      FakeName module = activeModule();
      if (original == null || module == null || !module.isEnabled()) return original;
      return selectedNameText(original, module.showInTab.get(), module);
   }

   /** F5 nameplates are also replaced wholesale to remove all server decorations. */
   public static class_2561 replaceF5Name(class_2561 original, String accountName) {
      FakeName module = activeModule();
      if (module == null || !module.isEnabled() || !module.showInF5.get()) return original;
      return composeF5Name(original, accountName);
   }

   private static class_2561 selectedNameText(class_2561 original, boolean enabled, FakeName module) {
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
      return new int[]{Math.max(4, sw - this.w - 12), Math.max(4, (sh - this.h) / 2)};
   }

   private static final String[][] ICONS = {
      {"00100","01110","01000","11110","01001","01110","00100"},
      {"00100","01110","11111","11111","01110","00100","00000"},
      {"00100","10101","01110","11111","01110","10101","10001"},
      {"10001","01010","00100","01010","10001","01010","00100"},
      {"01110","11111","11111","11111","01110","00100","00000"},
      {"01110","11111","11011","10101","10001","01010","00100"},
      {"00100","01010","10001","00000","10001","01010","00100"},
      {"00110","01111","11110","01100","00100","00100","00100"},
      {"01110","10001","10101","10111","10001","10001","01110"}
   };

   private static final int[] ICON_COLORS = {
      0xFF80E64A, 0xFFFF3E9E, 0xFFFFD229, 0xFFC8B59A, 0xFFFF3B57,
      0xFFB68B59, 0xFFFF46C7, 0xFF45E8EF, 0xFF39E84E
   };

   private static final int[] VALUE_COLORS = {
      0xFFFFD84A, 0xFFFF35C6, 0xFFFFD82E, 0xFFFFD52D, 0xFFFF4C61,
      0xFFB45CFF, 0xFFFF4FE0, 0xFF39E7EC, 0xFF38E84D
   };

   private static void rect(class_332 ctx, int x1, int y1, int x2, int y2, int color) {
      if (x2 > x1 && y2 > y1) ctx.method_25294(x1, y1, x2, y2, color);
   }

   private static void drawText(class_332 ctx, String value, int x, int y, int color, boolean shadow) {
      if (value != null && !value.isEmpty()) {
         ctx.method_51433(mc.field_1772, value, x, y, color, shadow);
      }
   }

   private static void drawIcon(class_332 ctx, int x, int y, int index, int color) {
      String[] pixels = ICONS[index];
      for (int row = 0; row < pixels.length; row++) {
         for (int col = 0; col < pixels[row].length(); col++) {
            if (pixels[row].charAt(col) == '1') {
               rect(ctx, x + col * 2, y + row * 2, x + col * 2 + 2, y + row * 2 + 2, color);
            }
         }
      }
   }

   private void drawFrame(class_332 ctx) {
      // Deep black panel with the screenshot's stepped gold frame.
      rect(ctx, 8, 54, 203, 263, 0xFF160F08);
      rect(ctx, 10, 56, 201, 261, 0xFFD49A32);
      rect(ctx, 12, 58, 199, 259, 0xFF05090C);
      rect(ctx, 14, 61, 197, 256, 0xE9070D11);
      rect(ctx, 14, 61, 16, 255, 0xFF8A5B22);
      rect(ctx, 195, 61, 197, 255, 0xFF8A5B22);
      rect(ctx, 17, 63, 194, 65, 0xFF302012);
      rect(ctx, 17, 253, 194, 255, 0xFF5C3E1A);

      // Teal edge glints and faint diagonal texture.
      rect(ctx, 16, 68, 18, 83, 0xFF12413D);
      rect(ctx, 193, 76, 195, 93, 0xFF0C3937);
      rect(ctx, 18, 111, 19, 145, 0x5526A5A0);
      rect(ctx, 192, 162, 193, 194, 0x4426A5A0);
      for (int i = 0; i < 6; i++) {
         int x = 24 + i * 29;
         int y = 70 + (i % 3) * 23;
         rect(ctx, x, y, x + 10, y + 1, 0x180E6968);
         rect(ctx, x + 8, y + 1, x + 9, y + 8, 0x180E6968);
      }

      // Top ornament: stepped peaks, crossbar and central red/purple logo.
      rect(ctx, 15, 52, 67, 55, 0xFFB97724);
      rect(ctx, 143, 52, 196, 55, 0xFFB97724);
      rect(ctx, 64, 30, 68, 47, 0xFFD59A2F);
      rect(ctx, 69, 23, 73, 35, 0xFFD59A2F);
      rect(ctx, 73, 17, 77, 27, 0xFFFFD65B);
      rect(ctx, 77, 13, 81, 22, 0xFFFFD65B);
      rect(ctx, 81, 18, 85, 28, 0xFFD59A2F);
      rect(ctx, 85, 24, 89, 36, 0xFFD59A2F);
      rect(ctx, 130, 28, 134, 39, 0xFFD59A2F);
      rect(ctx, 134, 21, 138, 32, 0xFFFFD65B);
      rect(ctx, 138, 16, 142, 26, 0xFFFFD65B);
      rect(ctx, 142, 21, 146, 33, 0xFFD59A2F);
      rect(ctx, 146, 30, 150, 43, 0xFFD59A2F);
      rect(ctx, 111, 13, 115, 24, 0xFFFFD65B);
      rect(ctx, 115, 8, 119, 18, 0xFFFFD65B);
      rect(ctx, 119, 13, 123, 25, 0xFFD59A2F);
      rect(ctx, 31, 52, 63, 54, 0xFFB97724);
      rect(ctx, 151, 52, 184, 54, 0xFFB97724);

      // Header lettering, with dark offset pixels for a chunky logo feel.
      drawText(ctx, "HUGO", 59, 21, 0xFF70000A, true);
      drawText(ctx, "HUGO", 57, 19, 0xFFFF2530, true);
      drawText(ctx, "SMP", 82, 39, 0xFF4E176D, true);
      drawText(ctx, "SMP", 80, 37, 0xFFB75BEA, true);

      // Hanging gold badge on the upper-right edge.
      rect(ctx, 178, 48, 180, 56, 0xFFB98426);
      rect(ctx, 174, 55, 190, 75, 0xFF5D3614);
      rect(ctx, 176, 55, 188, 73, 0xFFFFC24C);
      rect(ctx, 178, 57, 186, 71, 0xFFD68A23);
      rect(ctx, 181, 60, 184, 66, 0xFFFFE47D);
      rect(ctx, 182, 67, 183, 69, 0xFF7C4214);

      // Bottom corner ornaments and pixel blocks.
      rect(ctx, 15, 246, 27, 258, 0xFF80604D);
      rect(ctx, 17, 248, 24, 255, 0xFFB6A49A);
      rect(ctx, 20, 249, 22, 251, 0xFFFFC65A);
      rect(ctx, 30, 252, 41, 260, 0xFF293943);
      rect(ctx, 32, 254, 38, 258, 0xFF6EA4B2);
      rect(ctx, 168, 249, 182, 261, 0xFF3A4650);
      rect(ctx, 171, 247, 179, 257, 0xFF8397A0);
      rect(ctx, 183, 253, 194, 261, 0xFF493A34);
      rect(ctx, 185, 255, 191, 259, 0xFF9C7654);
      rect(ctx, 48, 256, 68, 259, 0xFFB97826);
      rect(ctx, 134, 256, 157, 259, 0xFFB97826);
      rect(ctx, 44, 252, 50, 257, 0xFF53A92C);
      rect(ctx, 51, 250, 56, 254, 0xFF6EDD36);
      rect(ctx, 59, 253, 62, 256, 0xFF2B8A23);
   }

   @Override
   protected void draw(class_332 ctx) {
      if (!this.leaderboardEnabled.get()) return;

      this.drawFrame(ctx);
      String[] labels = leaderboardLabels();
      String[] values = new String[]{
         geld.get(), gems.get(), stars.get(), kills.get(), tode.get(),
         clan.get(), ping.get(), keyall.get(), spielzeit.get()
      };

      for (int i = 0; i < labels.length - 1; i++) {
         int y = 80 + i * 16;
         drawIcon(ctx, 33, y + 1, i, ICON_COLORS[i]);
         drawText(ctx, labels[i], 49, y + 2, 0xFFF5F5F5, true);
         drawText(ctx, values[i], 95, y + 2, VALUE_COLORS[i], true);
      }

      // Server footer and editable online-player count.
      drawText(ctx, "HUGOSMP.NET", 32, 231, 0xFFFF5266, true);
      drawText(ctx, spielerzahl.get(), 32, 244, 0xFF42E85A, true);
      drawIcon(ctx, 151, 238, 8, 0xFF24D94D);
   }

   public static class PrivatScreen extends class_437 {
      private final FakeName module;
      private final StringSetting[] fields;
      private final String[] labels;
      private int selectedField = -1;
      private String draft = "";
      private boolean prevLeft;

      public PrivatScreen(FakeName module) {
         super(class_2561.method_43470("Privat - HugoSMP"));
         this.module = module;
         this.fields = leaderboardFields(module);
         this.labels = leaderboardLabels();
         class_310 mc = class_310.method_1551();
         if (mc.method_22683() != null) {
            this.prevLeft = GLFW.glfwGetMouseButton(mc.method_22683().method_4490(), 0) == 1;
         }
      }

      public boolean method_25421() {
         return false;
      }

      public void method_25432() {
         Config.save();
      }

      public boolean method_25404(class_11908 input) {
         int key = input.comp_4795();
         if (key == 256) {
            Config.save();
            this.method_25419();
            return true;
         }
         if (this.selectedField < 0) return true;

         if (key == 259) {
            if (!this.draft.isEmpty()) this.draft = this.draft.substring(0, this.draft.length() - 1);
            return true;
         }
         if (key == 257 || key == 335) {
            this.fields[this.selectedField].set(this.draft);
            Config.save();
            this.selectedField = -1;
            this.draft = "";
            return true;
         }
         if (key == 261) {
            this.selectedField = -1;
            this.draft = "";
            return true;
         }

         String typed = keyText(key);
         if (typed != null && this.draft.length() < 24) {
            long handle = class_310.method_1551().method_22683().method_4490();
            boolean shift = GLFW.glfwGetKey(handle, 340) == 1 || GLFW.glfwGetKey(handle, 344) == 1;
            if (shift && typed.length() == 1 && Character.isLetter(typed.charAt(0))) {
               typed = typed.toUpperCase(java.util.Locale.ROOT);
            }
            this.draft += typed;
         }
         return true;
      }

      private static String keyText(int key) {
         if (key == 89) return "z";
         if (key == 90) return "y";
         if (key >= 65 && key <= 90) return String.valueOf((char)('a' + key - 65));
         if (key >= 48 && key <= 57) return String.valueOf((char)('0' + key - 48));
         return switch (key) {
            case 32 -> " ";
            case 44 -> ",";
            case 45 -> "-";
            case 46 -> ".";
            case 47 -> "/";
            case 59 -> ";";
            case 61 -> "=";
            case 258 -> " ";
            default -> null;
         };
      }

      private int panelWidth() {
         return Math.min(430, Math.max(250, this.field_22789 - 20));
      }

      private int panelX() {
         return (this.field_22789 - this.panelWidth()) / 2;
      }

      private int panelY() {
         return Math.max(8, (this.field_22790 - 450) / 2);
      }

      private void click(int mouseX, int mouseY) {
         int x = this.panelX();
         int y = this.panelY();
         int w = this.panelWidth();

         if (mouseY >= y + 55 && mouseY <= y + 81 && mouseX >= x + 14 && mouseX <= x + 174) {
            this.module.setLeaderboardEnabled(!this.module.isLeaderboardEnabled());
            return;
         }

         if (mouseY >= y + 87 && mouseY <= y + 115 && mouseX >= x + 14 && mouseX <= x + w - 14) {
            this.module.cycleRank();
            return;
         }

         if (mouseY >= y + 410 && mouseY <= y + 438 && mouseX >= x + w - 98 && mouseX <= x + w - 12) {
            Config.save();
            this.method_25419();
            return;
         }

         int colWidth = (w - 42) / 2;
         for (int i = 0; i < this.fields.length; i++) {
            int column = i < 6 ? 0 : 1;
            int row = i < 6 ? i : i - 6;
            int rowX = x + 14 + column * (colWidth + 14);
            int top = y + 130 + row * 43;
            if (mouseX >= rowX && mouseX <= rowX + colWidth && mouseY >= top && mouseY <= top + 34) {
               this.selectedField = i;
               this.draft = "";
               return;
            }
         }
      }

      public void method_25394(class_332 ctx, int mouseX, int mouseY, float delta) {
         class_310 mc = class_310.method_1551();
         long handle = mc.method_22683().method_4490();
         boolean left = GLFW.glfwGetMouseButton(handle, 0) == 1;
         if (left && !this.prevLeft) this.click(mouseX, mouseY);
         this.prevLeft = left;

         int x = this.panelX();
         int y = this.panelY();
         int w = this.panelWidth();
         rect(ctx, 0, 0, this.field_22789, this.field_22790, 0xA8000000);
         rect(ctx, x, y, x + w, y + 450, 0xFF080D11);
         rect(ctx, x, y, x + w, y + 3, 0xFFD69A37);
         rect(ctx, x, y + 3, x + 3, y + 447, 0xFF8B5D27);
         rect(ctx, x + w - 3, y + 3, x + w, y + 447, 0xFF8B5D27);
         rect(ctx, x + 3, y + 447, x + w - 3, y + 450, 0xFFD69A37);

         drawText(ctx, "HUGOSMP  /  PRIVAT", x + 16, y + 15, 0xFFFFC34C, true);
         drawText(ctx, "LEADERBOARD-WERTE BEARBEITEN", x + 16, y + 31, 0xFF9DAAB4, false);

         boolean enabled = this.module.isLeaderboardEnabled();
         rect(ctx, x + 14, y + 55, x + 174, y + 81, enabled ? 0xFF21452B : 0xFF3B2025);
         rect(ctx, x + 14, y + 55, x + 16, y + 81, enabled ? 0xFF56E46A : 0xFFE65E66);
         drawText(ctx, "Leaderboard: " + (enabled ? "AN" : "AUS"), x + 23, y + 64,
            enabled ? 0xFF8FFFA0 : 0xFFFF9292, true);

         String selectedRank = this.module.selectedRank();
         int rankColor = rankColor(selectedRank);
         rect(ctx, x + 14, y + 87, x + w - 14, y + 115, 0xFF111A20);
         rect(ctx, x + 14, y + 87, x + 16, y + 115, 0xFF76572D);
         drawText(ctx, "F5-RANG: " + selectedRank, x + 23, y + 97, rankColor, true);
         drawText(ctx, "ANKLICKEN ZUM WECHSELN", x + w - 154, y + 97, 0xFF8A9AA5, false);

         int colWidth = (w - 42) / 2;
         for (int i = 0; i < this.fields.length; i++) {
            int column = i < 6 ? 0 : 1;
            int row = i < 6 ? i : i - 6;
            int rowX = x + 14 + column * (colWidth + 14);
            int top = y + 130 + row * 43;
            boolean active = this.selectedField == i;
            rect(ctx, rowX, top, rowX + colWidth, top + 34, active ? 0xFF182A30 : 0xFF111A20);
            rect(ctx, rowX, top, rowX + 2, top + 34, active ? 0xFF4CE4D1 : 0xFF76572D);
            drawText(ctx, this.labels[i].toUpperCase(java.util.Locale.ROOT), rowX + 8, top + 2, 0xFFB8C4CA, false);
            rect(ctx, rowX + 7, top + 15, rowX + colWidth - 7, top + 30, active ? 0xFF263E43 : 0xFF080D11);
            String value = active ? this.draft : this.fields[i].get();
            if (value == null || value.isEmpty()) value = active ? "|" : "(leer)";
            int valueColor = i < VALUE_COLORS.length ? VALUE_COLORS[i] : (i == 10 ? 0xFFFFFFFF : 0xFF42E85A);
            drawText(ctx, value, rowX + 12, top + 19, active ? 0xFFFFFFFF : valueColor, true);
         }

         drawText(ctx, this.selectedField >= 0
            ? "Tippen, Backspace und ENTER zum Speichern  |  ESC: Schliessen"
            : "Feld anklicken zum Bearbeiten  |  Rang anklicken zum Wechseln",
            x + 16, y + 395, 0xFF8A9AA5, false);
         rect(ctx, x + w - 98, y + 410, x + w - 12, y + 438, 0xFF3B2817);
         rect(ctx, x + w - 98, y + 410, x + w - 96, y + 438, 0xFFFFC34C);
         drawText(ctx, "FERTIG", x + w - 75, y + 420, 0xFFFFD98A, true);
      }
   }

}
