package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.class_1297;
import net.minecraft.class_1542;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_2561;
import net.minecraft.class_3417;

public final class DroppedItemNotifier extends Module {
   private final NumberSetting range = this.add(new NumberSetting("Reichweite", 48.0, 8.0, 128.0, 8.0));
   private final BoolSetting elytra = this.add(new BoolSetting("Elytra", true));
   private final BoolSetting mace = this.add(new BoolSetting("Mace", true));
   private final BoolSetting totem = this.add(new BoolSetting("Totem", true));
   private final BoolSetting heavyCore = this.add(new BoolSetting("Heavy Core", true));
   private final BoolSetting netherite = this.add(new BoolSetting("Netherite-Barren", true));
   private final BoolSetting diamond = this.add(new BoolSetting("Diamanten", true));
   private final BoolSetting emerald = this.add(new BoolSetting("Emeralds", true));
   private final BoolSetting enchantedApple = this.add(new BoolSetting("Verzauberter Goldapfel", true));
   private final BoolSetting sound = this.add(new BoolSetting("Sound bei Fund", true));
   private final BoolSetting chat = this.add(new BoolSetting("Chat-Nachricht", true));
   private final Set<UUID> seenItems = new HashSet<>();
   private boolean baselineReady;

   public DroppedItemNotifier() {
      super("DroppedItemNotifier", "Warnt, wenn ausgewaehlte wertvolle Items in der Naehe gedroppt werden.", Category.VISUALS);
   }

   @Override
   protected void onEnable() {
      this.seenItems.clear();
      this.baselineReady = false;
   }

   @Override
   protected void onDisable() {
      this.seenItems.clear();
      this.baselineReady = false;
   }

   @Override
   public void onWorldLeave() {
      this.seenItems.clear();
      this.baselineReady = false;
   }

   @Override
   public void onTick() {
      if (mc.field_1687 != null && mc.field_1724 != null) {
         Set<UUID> currentlyPresent = new HashSet<>();
         double maxDistanceSq = this.range.get() * this.range.get();

         for (class_1297 entity : mc.field_1687.method_18112()) {
            if (entity instanceof class_1542 itemEntity && !(itemEntity.method_5858(mc.field_1724) > maxDistanceSq)) {
               UUID id = itemEntity.method_5667();
               currentlyPresent.add(id);
               if (this.baselineReady && !this.seenItems.contains(id)) {
                  class_1799 stack = itemEntity.method_6983();
                  String label = this.selectedItemName(stack);
                  if (label != null) {
                     if (this.chat.get()) {
                        mc.field_1724.method_7353(class_2561.method_43470("[Nightfall] ITEM GEFUNDEN: " + label + " x" + stack.method_7947()), false);
                     }

                     if (this.sound.get()) {
                        mc.field_1724.method_5783(class_3417.field_14627, 0.9F, 1.2F);
                     }
                  }
               }
            }
         }

         this.seenItems.retainAll(currentlyPresent);
         this.seenItems.addAll(currentlyPresent);
         this.baselineReady = true;
      }
   }

   private String selectedItemName(class_1799 stack) {
      if (this.elytra.get() && stack.method_31574(class_1802.field_8833)) {
         return "Elytra";
      } else if (this.mace.get() && stack.method_31574(class_1802.field_49814)) {
         return "Mace";
      } else if (this.totem.get() && stack.method_31574(class_1802.field_8288)) {
         return "Totem";
      } else if (this.heavyCore.get() && stack.method_31574(class_1802.field_49813)) {
         return "Heavy Core";
      } else if (this.netherite.get() && stack.method_31574(class_1802.field_22020)) {
         return "Netherite-Barren";
      } else if (this.diamond.get() && stack.method_31574(class_1802.field_8477)) {
         return "Diamanten";
      } else if (this.emerald.get() && stack.method_31574(class_1802.field_8687)) {
         return "Emeralds";
      } else {
         return this.enchantedApple.get() && stack.method_31574(class_1802.field_8367) ? "Verzauberter Goldapfel" : null;
      }
   }
}
