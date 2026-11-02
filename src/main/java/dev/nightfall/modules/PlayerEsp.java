package dev.nightfall.modules;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public class PlayerEsp extends EntityScanEsp {
    public PlayerEsp() { super("PlayerESP", "Markiert andere Spieler.", 256, 512, 0xEF4444); }
    @Override protected boolean accept(Entity e) { return e instanceof PlayerEntity; }
}
