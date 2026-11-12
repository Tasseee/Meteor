package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.MessageListSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.util.NotificationCenter;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_2561;

public final class AutoText extends Module {
   public final MessageListSetting messages = this.add(new MessageListSetting("Nachrichten"));
   private final NumberSetting interval = this.add(new NumberSetting("Intervall (Sek.)", 30.0, 5.0, 600.0, 5.0));
   private final BoolSetting rotate = this.add(new BoolSetting("Nachrichten rotieren", true));
   private final BoolSetting showInfo = this.add(new BoolSetting("Statusmeldung", true));
   private int ticks;
   private int nextIndex;

   public AutoText() {
      super("AutoText", "Sendet eigene Chatnachrichten in einem einstellbaren Intervall (mind. 5 Sekunden).", Category.MISC);
   }

   public int intervalSeconds() {
      return this.interval.getInt();
   }

   public boolean addMessage(String message) {
      String clean = message == null ? "" : message.trim();
      return !clean.isEmpty() && clean.length() <= 256 ? this.messages.addMessage(clean) : false;
   }

   public boolean removeMessage(String message) {
      return this.messages.removeMessage(message);
   }

   public List<String> messageList() {
      return new ArrayList<>(this.messages.get());
   }

   @Override
   protected void onEnable() {
      this.ticks = 0;
      this.nextIndex = 0;
      if (this.messages.get().isEmpty()) {
         NotificationCenter.push("AutoText", "Zuerst /nf autotext add <Nachricht> verwenden");
      }
   }

   @Override
   protected void onDisable() {
      this.ticks = 0;
   }

   @Override
   public void onWorldLeave() {
      this.ticks = 0;
      this.nextIndex = 0;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 != null && mc.field_1724.field_3944 != null) {
         List<String> list = this.messageList();
         if (!list.isEmpty()) {
            this.ticks++;
            if (this.ticks >= this.interval.getInt() * 20) {
               this.ticks = 0;
               String message = list.get(Math.floorMod(this.nextIndex, list.size()));
               mc.field_1724.field_3944.method_45729(message);
               if (this.rotate.get()) {
                  this.nextIndex++;
               }

               if (this.showInfo.get()) {
                  mc.field_1724
                     .method_7353(
                        class_2561.method_43470("[Nightfall] AutoText gesendet (" + (this.nextIndex % Math.max(1, list.size()) + 1) + "/" + list.size() + ")"),
                        true
                     );
               }
            }
         }
      }
   }
}
