package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ListSetting;

/** Shared persistent friend list for combat targeting and ESP filtering. */
public final class FriendManager extends Module {
    public final ListSetting friends = add(new ListSetting("Freunde"));
    public FriendManager() { super("FriendManager", "Verwaltet Spieler, die von Combat-Modulen ausgeschlossen werden.", Category.PLAYER); }
    public boolean isFriend(String name) { return name != null && friends.has(name); }
    public boolean addFriend(String name) { return valid(name) && friends.add(name); }
    public boolean removeFriend(String name) { return name != null && friends.remove(name); }
    private boolean valid(String name) { return name != null && name.matches("[A-Za-z0-9_]{3,16}"); }
}
