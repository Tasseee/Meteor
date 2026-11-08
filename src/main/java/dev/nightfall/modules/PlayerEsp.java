package dev.nightfall.modules;

import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Projection;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.Set;

public class PlayerEsp extends EntityScanEsp {
    private final BoolSetting autoInvsee = add(new BoolSetting("Auto Invsee", true));
    private final Set<String> inspectedPlayers = new HashSet<>();
    private net.minecraft.client.world.ClientWorld trackedWorld;

    public PlayerEsp() {
        super("PlayerESP", "Markiert andere Spieler und kann sie automatisch mit /invsee öffnen.", 256, 512, 0xEF4444);
    }

    @Override
    protected boolean accept(Entity e) {
        return e instanceof PlayerEntity;
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null || mc.getNetworkHandler() == null) {
            inspectedPlayers.clear();
            trackedWorld = null;
            return;
        }

        if (trackedWorld != mc.world) {
            inspectedPlayers.clear();
            trackedWorld = mc.world;
        }

        if (!autoInvsee.get()) return;

        double rangeSquared = range.get() * range.get();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            String name = player.getGameProfile().getName();
            if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) continue;
            String key = name.toLowerCase(java.util.Locale.ROOT);
            if (inspectedPlayers.contains(key)) continue;
            if (player.squaredDistanceTo(mc.player) > rangeSquared) continue;
            if (visibleOnly.get() && !Projection.canSeeFromCamera(mc, player)) continue;

            inspectedPlayers.add(key);
            mc.getNetworkHandler().sendChatCommand("invsee " + name);
        }
    }

    @Override
    protected void onDisable() {
        inspectedPlayers.clear();
        trackedWorld = null;
    }
}
