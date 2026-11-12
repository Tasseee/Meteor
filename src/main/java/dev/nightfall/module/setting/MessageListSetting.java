package dev.nightfall.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;

public final class MessageListSetting extends Setting<List<String>> {
   public MessageListSetting(String name) {
      super(name, new ArrayList<>());
   }

   public boolean addMessage(String message) {
      if (message != null && !message.isBlank() && !this.value.contains(message)) {
         this.value.add(message);
         return true;
      } else {
         return false;
      }
   }

   public boolean removeMessage(String message) {
      return this.value.remove(message);
   }

   @Override
   public JsonElement save() {
      JsonArray a = new JsonArray();

      for (String s : this.value) {
         a.add(s);
      }

      return a;
   }

   @Override
   public void load(JsonElement e) {
      if (e != null && e.isJsonArray()) {
         this.value.clear();

         for (JsonElement item : e.getAsJsonArray()) {
            if (item.isJsonPrimitive()) {
               String s = item.getAsString();
               if (!s.isBlank() && !this.value.contains(s)) {
                  this.value.add(s);
               }
            }
         }
      }
   }
}
