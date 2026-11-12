package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ListSetting;

public final class FriendManager extends Module {
   public final ListSetting friends = this.add(new ListSetting("Freunde"));

   public FriendManager() {
      super("FriendManager", "Verwaltet Spieler, die von Combat-Modulen ausgeschlossen werden.", Category.PLAYER);
   }

   public boolean isFriend(String name) {
      return name != null && this.friends.has(name);
   }

   public boolean addFriend(String name) {
      return this.valid(name) && this.friends.add(name);
   }

   public boolean removeFriend(String name) {
      return name != null && this.friends.remove(name);
   }

   private boolean valid(String name) {
      return name != null && name.matches("[A-Za-z0-9_]{3,16}");
   }
}
