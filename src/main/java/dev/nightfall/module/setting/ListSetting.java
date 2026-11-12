package dev.nightfall.module.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class ListSetting extends Setting<Set<String>> {
   public ListSetting(String name) {
      super(name, new TreeSet<>());
   }

   public boolean add(String s) {
      return this.value.add(s.toLowerCase(Locale.ROOT));
   }

   public boolean remove(String s) {
      return this.value.remove(s.toLowerCase(Locale.ROOT));
   }

   public boolean has(String s) {
      return this.value.contains(s.toLowerCase(Locale.ROOT));
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

         for (JsonElement x : e.getAsJsonArray()) {
            this.value.add(x.getAsString().toLowerCase(Locale.ROOT));
         }
      }
   }
}
