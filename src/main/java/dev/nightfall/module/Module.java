package dev.nightfall.module;

import dev.nightfall.module.setting.Setting;
import dev.nightfall.util.NotificationCenter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.class_310;
import net.minecraft.class_332;

public abstract class Module {
   protected static final class_310 mc = class_310.method_1551();
   private final String name;
   private final String description;
   private final Category category;
   private boolean enabled;
   private boolean favorite;
   private int key = -1;
   private final List<Setting<?>> settings = new ArrayList<>();

   protected Module(String name, String description, Category category) {
      this.name = name;
      this.description = description;
      this.category = category;
   }

   protected <S extends Setting<?>> S add(S s) {
      this.settings.add(s);
      return s;
   }

   public String getName() {
      return this.name;
   }

   public String getDescription() {
      return this.description;
   }

   public Category getCategory() {
      return this.category;
   }

   public boolean isEnabled() {
      return this.enabled;
   }

   public boolean isFavorite() {
      return this.favorite;
   }

   public void setFavorite(boolean value) {
      this.favorite = value;
   }

   public List<Setting<?>> getSettings() {
      return Collections.unmodifiableList(this.settings);
   }

   public int getKey() {
      return this.key;
   }

   public void setKey(int k) {
      this.key = k;
   }

   public void toggle() {
      this.setEnabled(!this.enabled);
   }

   public void setEnabled(boolean e) {
      if (e != this.enabled) {
         this.enabled = e;
         if (e) {
            this.onEnable();
         } else {
            this.onDisable();
         }

         if (mc.field_1724 != null) {
            NotificationCenter.push(this.name, e ? "Aktiviert" : "Deaktiviert");
         }
      }
   }

   protected void onEnable() {
   }

   protected void onDisable() {
   }

   public void onTick() {
   }

   public void onWorldLeave() {
   }

   public void renderHud(class_332 ctx) {
   }
}
