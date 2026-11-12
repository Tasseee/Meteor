package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.StringSetting;
import dev.nightfall.util.NotificationCenter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.class_1703;
import net.minecraft.class_1713;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2246;
import net.minecraft.class_2338;
import net.minecraft.class_2350;
import net.minecraft.class_243;
import net.minecraft.class_2561;
import net.minecraft.class_2680;
import net.minecraft.class_2960;
import net.minecraft.class_3965;
import net.minecraft.class_465;
import net.minecraft.class_7923;
import net.minecraft.class_9290;
import net.minecraft.class_9334;

public final class MacroManager extends Module {
   public final BoolSetting sellEnabled = this.add(new BoolSetting("Sell Macro", false));
   public final NumberSetting sellInterval = this.add(new NumberSetting("Sell Intervall (Sek.)", 5.0, 5.0, 300.0, 1.0));
   public final StringSetting sellItems = this.add(new StringSetting("Sell Items (IDs, Komma)", "", 256));
   public final StringSetting sellCommand = this.add(new StringSetting("Sell Befehl", "sell", 48));
   public final BoolSetting orderEnabled = this.add(new BoolSetting("Order Macro", false));
   public final NumberSetting orderInterval = this.add(new NumberSetting("Order Intervall (Sek.)", 10.0, 10.0, 600.0, 1.0));
   public final StringSetting orderItem = this.add(new StringSetting("Order Item-ID", "", 128));
   public final StringSetting orderCommand = this.add(new StringSetting("Order Befehl", "order", 48));
   public final BoolSetting spawnerEnabled = this.add(new BoolSetting("Spawner Macro", false));
   public final NumberSetting spawnerRadius = this.add(new NumberSetting("Spawner Suchradius", 6.0, 3.0, 10.0, 1.0));
   public final BoolSetting spawnerAutoBreak = this.add(new BoolSetting("Spawner abbauen (Fadenkreuz)", false));
   public final NumberSetting spawnerBreakDistance = this.add(new NumberSetting("Spawner-Abstand", 2.5, 1.5, 4.0, 0.5));
   public final BoolSetting spawnerAutoSellDrops = this.add(new BoolSetting("Aufgenommene Drops verkaufen", false));
   public final BoolSetting ahSniperEnabled = this.add(new BoolSetting("AH Sniper", false));
   public final BoolSetting ahAutoBuy = this.add(new BoolSetting("AH Auto-Buy", false));
   public final StringSetting ahItemFilter = this.add(new StringSetting("AH Item-Filter", "", 128));
   public final StringSetting ahMenuTitle = this.add(new StringSetting("AH Menütitel enthält", "auction", 32));
   public final NumberSetting ahMaxPrice = this.add(new NumberSetting("AH Maximalpreis", 1000.0, 1.0, 1.0E8, 1.0));
   public final NumberSetting ahMaxPurchases = this.add(new NumberSetting("AH Käufe pro Aktivierung", 1.0, 1.0, 20.0, 1.0));
   public final NumberSetting ahMaxTotalSpend = this.add(new NumberSetting("AH Gesamtbudget", 10000.0, 1.0, 1.0E9, 1.0));
   public final NumberSetting ahCooldownTicks = this.add(new NumberSetting("AH Kaufpause (Ticks)", 40.0, 20.0, 200.0, 5.0));
   public final BoolSetting macroCreator = this.add(new BoolSetting("Macro Creator aktiv", false));
   public final StringSetting customMacroName = this.add(new StringSetting("Eigenes Makro", "Neues Makro", 64));
   public final StringSetting macroSteps = this.add(
      new StringSetting("Schritte (wait:20,chat:/sell)", "wait:20,notify:Makro gestartet,chat:/sell,notify:Fertig", 256)
   );
   private static final Pattern LABELLED_PRICE = Pattern.compile(
      "(?i)(?:price|preis|kosten|cost|buy\\s*now|kaufpreis)\\D{0,18}([$€]?\\s*[0-9][0-9.,]*\\s*[kmb]?)"
   );
   private static final Pattern CURRENCY_PRICE = Pattern.compile("[$€]\\s*([0-9][0-9.,]*\\s*[kmb]?)");
   private int sellTicks;
   private int orderTicks;
   private int spawnerTicks;
   private int macroIndex;
   private int macroWait;
   private int macroCooldown;
   private int macroChatActions;
   private boolean macroRunning;
   private class_2338 spawnerBreaking;
   private class_2350 spawnerFace = class_2350.field_11036;
   private int spawnerBreakTicks;
   private int spawnerPickupTicks;
   private final Map<class_1792, Integer> spawnerCountsBefore = new HashMap<>();
   private int ahScanTicks;
   private int ahCooldown;
   private int ahPurchases;
   private long ahSpent;
   private boolean ahPending;
   private boolean ahConfirmClicked;
   private int ahPendingTicks;
   private int ahListingSyncId = -1;
   private class_1792 ahPendingItem;
   private String ahPendingName = "";
   private long ahPendingPrice;
   private int ahInventoryBefore;
   private boolean ahLastNotice;
   private int ahLastNoticeSlot = -1;

   public MacroManager() {
      super("MacroManager", "Führt Sell-/Order-Makros, einen begrenzten AH-Sniper, Spawner-Abbau und eigene Schritt-Makros aus.", Category.MISC);
   }

   @Override
   protected void onEnable() {
      this.sellTicks = this.orderTicks = this.spawnerTicks = 0;
      this.macroIndex = this.macroWait = this.macroCooldown = this.macroChatActions = 0;
      this.macroRunning = false;
      this.ahPurchases = 0;
      this.ahSpent = 0L;
      this.resetAuctionPending();
      this.resetSpawner();
      if (this.macroCreator.get()) {
         String[] steps = this.parsedSteps();
         if (steps.length == 0) {
            NotificationCenter.push("Macro Creator", "Keine Schritte eingetragen.");
         } else {
            this.macroRunning = true;
            NotificationCenter.push("Macro Creator", this.customMacroName.get() + " gestartet.");
         }
      }
   }

   @Override
   protected void onDisable() {
      this.macroRunning = false;
      this.sellTicks = this.orderTicks = this.spawnerTicks = 0;
      this.macroWait = this.macroCooldown = 0;
      this.resetAuctionPending();
      this.resetSpawner();
   }

   @Override
   public void onWorldLeave() {
      this.macroRunning = false;
      this.sellTicks = this.orderTicks = this.spawnerTicks = 0;
      this.macroWait = this.macroCooldown = 0;
      this.resetAuctionPending();
      this.resetSpawner();
   }

   private void resetAuctionPending() {
      this.ahPending = false;
      this.ahConfirmClicked = false;
      this.ahPendingTicks = 0;
      this.ahListingSyncId = -1;
      this.ahPendingItem = null;
      this.ahPendingName = "";
      this.ahPendingPrice = 0L;
      this.ahInventoryBefore = 0;
   }

   private void resetSpawner() {
      this.spawnerBreaking = null;
      this.spawnerBreakTicks = 0;
      this.spawnerPickupTicks = 0;
      this.spawnerCountsBefore.clear();
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1687 != null && mc.method_1562() != null) {
         if (this.ahCooldown > 0) {
            this.ahCooldown--;
         }

         if (this.macroRunning) {
            this.tickCustomMacro();
         }

         if (this.sellEnabled.get()) {
            if (++this.sellTicks >= this.sellInterval.getInt() * 20) {
               this.sellTicks = 0;
               this.sellOneConfiguredItem();
            }
         } else {
            this.sellTicks = 0;
         }

         if (this.orderEnabled.get()) {
            if (++this.orderTicks >= this.orderInterval.getInt() * 20) {
               this.orderTicks = 0;
               this.orderConfiguredItem();
            }
         } else {
            this.orderTicks = 0;
         }

         if (this.spawnerEnabled.get()) {
            this.tickSpawnerMacro();
         } else {
            this.resetSpawner();
         }

         if (this.ahSniperEnabled.get()) {
            this.tickAuctionSniper();
         } else {
            this.resetAuctionPending();
         }
      }
   }

   private void tickAuctionSniper() {
      if (this.ahPending) {
         this.tickPendingAuctionPurchase();
      } else if (this.ahCooldown <= 0 && ++this.ahScanTicks >= 10) {
         this.ahScanTicks = 0;
         if (mc.field_1755 instanceof class_465<?> screen) {
            String var10 = mc.field_1755.method_25440().getString();
            if (this.isAuctionBrowseTitle(var10)) {
               String filter = this.ahItemFilter.get().trim();
               if (!filter.isEmpty()) {
                  class_1703 handler = screen.method_17577();
                  int containerSlots = Math.max(0, handler.method_7602().size() - 36);

                  for (int slot = 0; slot < containerSlots; slot++) {
                     class_1799 stack = handler.method_7611(slot).method_7677();
                     if (!stack.method_7960() && matchesAuctionFilter(stack, filter)) {
                        long price = findPrice(stack);
                        if (price > 0L && price <= this.ahMaxPrice.getInt() && price <= this.ahMaxTotalSpend.getInt() - this.ahSpent) {
                           if (this.ahAutoBuy.get()) {
                              if (this.ahPurchases >= this.ahMaxPurchases.getInt()) {
                                 NotificationCenter.push("AH Sniper", "Kauflimit dieser Aktivierung erreicht.");
                                 return;
                              }

                              this.ahPending = true;
                              this.ahPendingTicks = 0;
                              this.ahListingSyncId = handler.field_7763;
                              this.ahPendingItem = stack.method_7909();
                              this.ahPendingName = stack.method_7964().getString();
                              this.ahPendingPrice = price;
                              this.ahInventoryBefore = this.countInventory(this.ahPendingItem);
                              this.ahConfirmClicked = false;
                              mc.field_1761.method_2906(handler.field_7763, slot, 0, class_1713.field_7790, mc.field_1724);
                              this.ahCooldown = this.ahCooldownTicks.getInt();
                              NotificationCenter.push("AH Sniper", "Preis geprüft; Angebot wird verifiziert: " + this.ahPendingName + " · " + price);
                              return;
                           }

                           if (!this.ahLastNotice || this.ahLastNoticeSlot != slot) {
                              NotificationCenter.push("AH Sniper", "Treffer: " + stack.method_7964().getString() + " für " + price + ".");
                              this.ahLastNotice = true;
                              this.ahLastNoticeSlot = slot;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean isAuctionBrowseTitle(String title) {
      String keyword = this.ahMenuTitle.get().trim().toLowerCase(Locale.ROOT);
      if (keyword.isEmpty()) {
         return false;
      } else {
         String normalized = title.toLowerCase(Locale.ROOT);
         return normalized.contains(keyword) && !isConfirmationTitle(normalized);
      }
   }

   private static boolean isConfirmationTitle(String title) {
      return title.contains("confirm")
         || title.contains("purchase")
         || title.contains("bestätig")
         || title.contains("bestaetig")
         || title.contains("kauf bestätigen")
         || title.contains("purchase");
   }

   private static boolean matchesAuctionFilter(class_1799 stack, String filter) {
      String name = stack.method_7964().getString().toLowerCase(Locale.ROOT);
      String id = class_7923.field_41178.method_10221(stack.method_7909()).toString().toLowerCase(Locale.ROOT);

      for (String part : filter.split("[,;]")) {
         String token = part.trim().toLowerCase(Locale.ROOT);
         if (!token.isEmpty() && (name.contains(token) || id.contains(token))) {
            return true;
         }
      }

      return false;
   }

   private static long findPrice(class_1799 stack) {
      class_9290 lore = (class_9290)stack.method_58694(class_9334.field_49632);
      if (lore == null) {
         return -1L;
      } else {
         StringBuilder text = new StringBuilder();

         for (class_2561 line : lore.comp_2400()) {
            text.append(line.getString()).append(' ');
         }

         Matcher labelled = LABELLED_PRICE.matcher(text);
         if (labelled.find()) {
            return parsePriceToken(labelled.group(1));
         } else {
            Matcher currency = CURRENCY_PRICE.matcher(text);
            return currency.find() ? parsePriceToken(currency.group(1)) : -1L;
         }
      }
   }

   private static long parsePriceToken(String token) {
      if (token == null) {
         return -1L;
      } else {
         String value = token.toLowerCase(Locale.ROOT).replace("$", "").replace("€", "").replace(" ", "");
         double multiplier = 1.0;
         if (value.endsWith("k")) {
            multiplier = 1000.0;
            value = value.substring(0, value.length() - 1);
         } else if (value.endsWith("m")) {
            multiplier = 1000000.0;
            value = value.substring(0, value.length() - 1);
         } else if (value.endsWith("b")) {
            multiplier = 1.0E9;
            value = value.substring(0, value.length() - 1);
         }

         int comma = value.lastIndexOf(44);
         int dot = value.lastIndexOf(46);
         if (comma >= 0 && dot >= 0) {
            if (dot > comma) {
               value = value.replace(",", "");
            } else {
               value = value.replace(".", "").replace(',', '.');
            }
         } else if (comma >= 0 || dot >= 0) {
            int separator = Math.max(comma, dot);
            int decimals = value.length() - separator - 1;
            if (multiplier == 1.0 && decimals != 1 && decimals != 2) {
               value = value.replace(",", "").replace(".", "");
            } else {
               value = value.replace(',', '.');
            }
         }

         try {
            double parsed = Double.parseDouble(value) * multiplier;
            return Double.isFinite(parsed) && !(parsed < 1.0) && !(parsed > 9.223372E18F) ? Math.round(parsed) : -1L;
         } catch (NumberFormatException var8) {
            return -1L;
         }
      }
   }

   private void tickPendingAuctionPurchase() {
      if (this.ahPendingItem == null) {
         this.resetAuctionPending();
      } else {
         this.ahPendingTicks++;
         int currentCount = this.countInventory(this.ahPendingItem);
         if (currentCount > this.ahInventoryBefore) {
            this.finishAuctionPurchase(true, "Inventar-Zuwachs erkannt");
         } else {
            if (mc.field_1755 instanceof class_465<?> screen) {
               class_1703 handler = screen.method_17577();
               String title = mc.field_1755.method_25440().getString().toLowerCase(Locale.ROOT);
               if (handler.field_7763 != this.ahListingSyncId && isConfirmationTitle(title)) {
                  if (!this.ahConfirmClicked) {
                     this.verifyAndConfirmAuction(handler);
                  }
               } else if (handler.field_7763 != this.ahListingSyncId && !this.isAuctionBrowseTitle(title)) {
                  this.finishAuctionPurchase(false, "Unbekanntes Menü; Kauf abgebrochen.");
                  return;
               }
            }

            if (this.ahPendingTicks > 80) {
               this.finishAuctionPurchase(false, "Kein bestätigter Kauf erkannt; Sniper pausiert.");
            }
         }
      }
   }

   private void verifyAndConfirmAuction(class_1703 handler) {
      boolean matchingItem = false;
      long visiblePrice = -1L;
      int containerSlots = Math.max(0, handler.method_7602().size() - 36);

      for (int slot = 0; slot < containerSlots; slot++) {
         class_1799 stack = handler.method_7611(slot).method_7677();
         if (!stack.method_7960()) {
            if (stack.method_7909() == this.ahPendingItem && matchesAuctionFilter(stack, this.ahItemFilter.get())) {
               matchingItem = true;
               long stackPrice = findPrice(stack);
               if (stackPrice > 0L) {
                  visiblePrice = stackPrice;
               }
            }

            if (visiblePrice <= 0L) {
               long otherPrice = findPrice(stack);
               if (otherPrice > 0L) {
                  visiblePrice = otherPrice;
               }
            }
         }
      }

      if (matchingItem
         && visiblePrice > 0L
         && visiblePrice == this.ahPendingPrice
         && visiblePrice <= this.ahMaxPrice.getInt()
         && visiblePrice <= this.ahMaxTotalSpend.getInt() - this.ahSpent) {
         for (int slotx = 0; slotx < containerSlots; slotx++) {
            class_1799 button = handler.method_7611(slotx).method_7677();
            if (!button.method_7960()) {
               String label = stackText(button).toLowerCase(Locale.ROOT);
               boolean confirm = containsAny(label, "confirm", "purchase", "buy now", "kaufen", "bestätigen", "bestaetigen");
               boolean cancel = containsAny(label, "cancel", "abbrechen", "zurück", "zurueck", "decline");
               if (confirm && !cancel) {
                  mc.field_1761.method_2906(handler.field_7763, slotx, 0, class_1713.field_7790, mc.field_1724);
                  this.ahConfirmClicked = true;
                  this.ahCooldown = this.ahCooldownTicks.getInt();
                  NotificationCenter.push("AH Sniper", "Kauf bestätigt: " + this.ahPendingName + " · " + this.ahPendingPrice);
                  return;
               }
            }
         }

         this.finishAuctionPurchase(false, "Kein eindeutiger Kaufen-Button gefunden.");
      } else {
         this.finishAuctionPurchase(false, "Bestätigungsdaten stimmen nicht exakt überein.");
      }
   }

   private static boolean containsAny(String text, String... terms) {
      for (String term : terms) {
         if (text.contains(term)) {
            return true;
         }
      }

      return false;
   }

   private static String stackText(class_1799 stack) {
      StringBuilder text = new StringBuilder(stack.method_7964().getString());
      class_9290 lore = (class_9290)stack.method_58694(class_9334.field_49632);
      if (lore != null) {
         for (class_2561 line : lore.comp_2400()) {
            text.append(' ').append(line.getString());
         }
      }

      return text.toString();
   }

   private void finishAuctionPurchase(boolean success, String reason) {
      if (success) {
         this.ahPurchases++;
         this.ahSpent = this.ahSpent + this.ahPendingPrice;
         NotificationCenter.push("AH Sniper", "Kauf erkannt: " + this.ahPendingName + " (" + this.ahPendingPrice + ").");
      } else {
         NotificationCenter.push("AH Sniper", reason);
      }

      this.resetAuctionPending();
      this.ahCooldown = Math.max(this.ahCooldown, this.ahCooldownTicks.getInt());
   }

   private int countInventory(class_1792 item) {
      if (mc.field_1724 == null) {
         return 0;
      } else {
         int count = 0;

         for (int slot = 0; slot < mc.field_1724.method_31548().method_5439(); slot++) {
            class_1799 stack = mc.field_1724.method_31548().method_5438(slot);
            if (stack.method_31574(item)) {
               count += stack.method_7947();
            }
         }

         return count;
      }
   }

   private void sellOneConfiguredItem() {
      for (String token : this.sellItems.get().split(",")) {
         class_1792 item = this.resolveItem(token);
         if (item != null && this.hasItem(item, 1)) {
            String path = class_7923.field_41178.method_10221(item).method_12832();
            if (this.sendConfiguredCommand(this.sellCommand.get(), path)) {
               return;
            }
         }
      }
   }

   private void orderConfiguredItem() {
      class_1792 item = this.resolveItem(this.orderItem.get());
      if (item != null && this.hasItem(item, 1)) {
         this.sendConfiguredCommand(this.orderCommand.get(), class_7923.field_41178.method_10221(item).method_12832());
      }
   }

   private boolean sendConfiguredCommand(String prefix, String itemPath) {
      if (prefix != null && !prefix.isBlank() && itemPath != null && !itemPath.isBlank()) {
         String command = (prefix.trim() + " " + itemPath).trim();
         if (command.length() > 160 || command.contains("\n") || command.contains("\r")) {
            return false;
         } else if (mc.field_1724 != null && mc.field_1724.field_3944 != null) {
            mc.field_1724.field_3944.method_45730(command.startsWith("/") ? command.substring(1) : command);
            return true;
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private class_1792 resolveItem(String text) {
      if (text != null && !text.isBlank()) {
         String clean = text.trim().toLowerCase(Locale.ROOT);
         if (!clean.contains(":")) {
            clean = "minecraft:" + clean;
         }

         class_2960 id = class_2960.method_12829(clean);
         if (id == null) {
            return null;
         } else {
            class_1792 item = (class_1792)class_7923.field_41178.method_63535(id);
            return item == class_1802.field_8162 ? null : item;
         }
      } else {
         return null;
      }
   }

   private boolean hasItem(class_1792 item, int needed) {
      return this.countInventory(item) >= needed;
   }

   private void tickSpawnerMacro() {
      if (mc.field_1724 != null && mc.field_1687 != null && mc.field_1761 != null) {
         if (this.spawnerBreaking != null) {
            if (mc.field_1687.method_8320(this.spawnerBreaking).method_26215()) {
               this.spawnerBreaking = null;
               this.spawnerBreakTicks = 0;
               this.spawnerPickupTicks = 100;
               NotificationCenter.push("Spawner Macro", "Spawner abgebaut; prüfe aufgenommene Drops.");
            } else if (!(mc.field_1765 instanceof class_3965 hit && hit.method_17777().equals(this.spawnerBreaking))) {
               this.spawnerBreaking = null;
               this.spawnerBreakTicks = 0;
               NotificationCenter.push("Spawner Macro", "Abbau gestoppt: Ziel nicht mehr im Fadenkreuz.");
            } else if (++this.spawnerBreakTicks > 160) {
               this.spawnerBreaking = null;
               this.spawnerBreakTicks = 0;
               NotificationCenter.push("Spawner Macro", "Abbau nach Zeitlimit gestoppt.");
            } else {
               mc.field_1761.method_2902(this.spawnerBreaking, this.spawnerFace);
            }
         } else {
            if (this.spawnerPickupTicks > 0) {
               this.spawnerPickupTicks--;
               if (this.spawnerAutoSellDrops.get() && this.spawnerPickupTicks % 20 == 0) {
                  boolean newConfiguredDrop = false;

                  for (Entry<class_1792, Integer> entry : this.spawnerCountsBefore.entrySet()) {
                     if (this.countInventory(entry.getKey()) > entry.getValue()) {
                        newConfiguredDrop = true;
                        break;
                     }
                  }

                  if (newConfiguredDrop) {
                     this.sellOneConfiguredItem();
                     this.spawnerPickupTicks = 0;
                     this.spawnerCountsBefore.clear();
                     NotificationCenter.push("Spawner Macro", "Aufgenommene konfigurierte Drops zum Verkauf gesendet.");
                  }
               }

               if (this.spawnerPickupTicks == 0) {
                  this.spawnerCountsBefore.clear();
               }
            }

            if (this.spawnerAutoBreak.get() && mc.field_1765 instanceof class_3965 hit) {
               class_2338 var12 = hit.method_17777();
               class_2680 state = mc.field_1687.method_8320(var12);
               if (state.method_27852(class_2246.field_10260)) {
                  if (!(
                     mc.field_1724.method_33571().method_1025(class_243.method_24953(var12))
                        > this.spawnerBreakDistance.get() * this.spawnerBreakDistance.get()
                  )) {
                     if (mc.field_1724.method_6047().method_7951(state)) {
                        if (this.nearSpawner(this.spawnerRadius.getInt())) {
                           this.spawnerCountsBefore.clear();

                           for (String token : this.sellItems.get().split(",")) {
                              class_1792 item = this.resolveItem(token);
                              if (item != null) {
                                 this.spawnerCountsBefore.put(item, this.countInventory(item));
                              }
                           }

                           this.spawnerBreaking = var12.method_10062();
                           this.spawnerFace = hit.method_17780();
                           this.spawnerBreakTicks = 0;
                           mc.field_1761.method_2910(var12, this.spawnerFace);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private boolean nearSpawner(int radius) {
      class_2338 center = mc.field_1724.method_24515();
      int r2 = radius * radius;

      for (int x = -radius; x <= radius; x++) {
         for (int y = -radius; y <= radius; y++) {
            for (int z = -radius; z <= radius; z++) {
               if (x * x + y * y + z * z <= r2) {
                  class_2338 pos = center.method_10069(x, y, z);
                  if (mc.field_1687.method_22340(pos) && mc.field_1687.method_8320(pos).method_27852(class_2246.field_10260)) {
                     return true;
                  }
               }
            }
         }
      }

      return false;
   }

   private String[] parsedSteps() {
      List<String> out = new ArrayList<>();

      for (String step : this.macroSteps.get().split(",")) {
         String clean = step.trim();
         if (!clean.isEmpty()) {
            out.add(clean);
         }

         if (out.size() >= 32) {
            break;
         }
      }

      return out.toArray(String[]::new);
   }

   private void tickCustomMacro() {
      if (this.macroWait > 0) {
         this.macroWait--;
      } else if (this.macroCooldown > 0) {
         this.macroCooldown--;
      } else {
         String[] steps = this.parsedSteps();
         if (this.macroIndex >= steps.length) {
            this.macroRunning = false;
            NotificationCenter.push("Macro Creator", this.customMacroName.get() + " abgeschlossen.");
         } else {
            String step = steps[this.macroIndex++];
            int split = step.indexOf(58);
            if (split < 1) {
               this.stopMacro("Ungültiger Schritt: " + step);
            } else {
               String action = step.substring(0, split).trim().toLowerCase(Locale.ROOT);
               String value = step.substring(split + 1).trim();
               switch (action) {
                  case "wait":
                     try {
                        int ticks = Integer.parseInt(value);
                        if (ticks >= 1 && ticks <= 12000) {
                           this.macroWait = ticks;
                           break;
                        }

                        this.stopMacro("wait muss zwischen 1 und 12000 Ticks liegen.");
                        return;
                     } catch (NumberFormatException var13) {
                        this.stopMacro("Ungültiger wait-Wert.");
                        break;
                     }
                  case "notify":
                     NotificationCenter.push(this.customMacroName.get(), value.isBlank() ? "Schritt ausgeführt." : value);
                     break;
                  case "chat":
                     if (value.isBlank() || value.length() > 160 || value.contains("\n") || value.contains("\r")) {
                        this.stopMacro("Ungültige Chat-Aktion.");
                        return;
                     }

                     if (++this.macroChatActions > 20) {
                        this.stopMacro("Sicherheitslimit: maximal 20 Chat-Aktionen pro Lauf.");
                        return;
                     }

                     if (value.startsWith("/")) {
                        mc.field_1724.field_3944.method_45730(value.substring(1));
                     } else {
                        mc.field_1724.field_3944.method_45729(value);
                     }

                     this.macroCooldown = 10;
                     break;
                  case "sell":
                     class_1792 item = this.resolveItem(value);
                     if (item == null || !this.hasItem(item, 1)) {
                        this.stopMacro("Sell-Schritt abgebrochen: Item fehlt.");
                        return;
                     }

                     if (++this.macroChatActions > 20) {
                        this.stopMacro("Sicherheitslimit: maximal 20 Chat-Aktionen pro Lauf.");
                        return;
                     }

                     this.sendConfiguredCommand(this.sellCommand.get(), class_7923.field_41178.method_10221(item).method_12832());
                     this.macroCooldown = 10;
                     break;
                  case "order":
                     class_1792 itemx = this.resolveItem(value);
                     if (itemx == null || !this.hasItem(itemx, 1)) {
                        this.stopMacro("Order-Schritt abgebrochen: Item fehlt.");
                        return;
                     }

                     if (++this.macroChatActions > 20) {
                        this.stopMacro("Sicherheitslimit: maximal 20 Chat-Aktionen pro Lauf.");
                        return;
                     }

                     this.sendConfiguredCommand(this.orderCommand.get(), class_7923.field_41178.method_10221(itemx).method_12832());
                     this.macroCooldown = 10;
                     break;
                  case "has":
                     int lastColon = value.lastIndexOf(58);
                     if (lastColon < 1) {
                        this.stopMacro("has-Format: minecraft:diamond:2");
                        return;
                     }

                     class_1792 itemxx = this.resolveItem(value.substring(0, lastColon));

                     int needed;
                     try {
                        needed = Integer.parseInt(value.substring(lastColon + 1));
                     } catch (NumberFormatException var12) {
                        this.stopMacro("Ungültige Anzahl bei has.");
                        return;
                     }

                     if (itemxx == null || needed < 1 || needed > 64 || !this.hasItem(itemxx, needed)) {
                        this.stopMacro("Makro gestoppt: benötigte Items fehlen.");
                        return;
                     }
                     break;
                  default:
                     this.stopMacro("Unbekannte Aktion: " + action);
               }
            }
         }
      }
   }

   private void stopMacro(String reason) {
      this.macroRunning = false;
      NotificationCenter.push("Macro Creator", reason);
   }
}
