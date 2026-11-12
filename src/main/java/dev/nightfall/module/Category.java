package dev.nightfall.module;

public enum Category {
   COMBAT("Combat"),
   MOVEMENT("Movement"),
   VISUALS("Visuals"),
   WORLD("World"),
   BASEFINDER("Basefinder"),
   PLAYER("Player"),
   HUD("HUD"),
   MISC("Misc");

   public final String display;

   private Category(String d) {
      this.display = d;
   }
}
