package dev.nightfall.module.setting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.class_1299;
import net.minecraft.class_2378;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

public final class EntityTypeSetting extends Setting<Set<String>> {
   public EntityTypeSetting(String name) {
      super(name, new TreeSet<>());
   }

   public boolean add(String id) {
      return this.value.add(normalize(id));
   }

   public boolean remove(String id) {
      return this.value.remove(normalize(id));
   }

   public boolean has(String id) {
      return this.value.contains(normalize(id));
   }

   public boolean isEmpty() {
      return this.value.isEmpty();
   }

   public List<String> allTypes() {
      List<String> ids = new ArrayList<>();
      try {
         class_2378<class_1299<?>> registry = (class_2378<class_1299<?>>) class_7923.field_41177;
         for (class_1299<?> type : registry) {
            class_2960 id = registry.method_10221(type);
            if (id != null) ids.add(id.toString().toLowerCase(Locale.ROOT));
         }
      } catch (Throwable ignored) {
      }
      ids.sort(String::compareTo);
      return ids;
   }

   private static String normalize(String id) {
      return id == null ? "" : id.toLowerCase(Locale.ROOT);
   }

   @Override
   public com.google.gson.JsonElement save() {
      com.google.gson.JsonArray a = new com.google.gson.JsonArray();
      for (String id : this.value) a.add(id);
      return a;
   }

   @Override
   public void load(com.google.gson.JsonElement e) {
      this.value.clear();
      if (e != null && e.isJsonArray()) {
         for (com.google.gson.JsonElement x : e.getAsJsonArray()) {
            if (!x.isJsonNull()) this.value.add(normalize(x.getAsString()));
         }
      }
   }
}
