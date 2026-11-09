package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;

/** Automatically respawns after the death screen appears. */
public final class AutoRespawn extends Module {
    private final BoolSetting chatNotice = add(new BoolSetting("Statusmeldung", false));
    private int delayTicks = 0;

    public AutoRespawn() {
        super("AutoRespawn", "Respawnt nach dem Tod automatisch.", Category.MISC);
    }

    @Override protected void onEnable() { delayTicks = 0; }
    @Override protected void onDisable() { delayTicks = 0; }
    @Override public void onTick() {
        if (mc.player == null || mc.currentScreen == null) { delayTicks = 0; return; }
        if (!(mc.currentScreen instanceof net.minecraft.client.gui.screen.DeathScreen)) return;
        if (++delayTicks < 20) return;
        mc.player.requestRespawn();
        mc.setScreen(null);
        delayTicks = 0;
        if (chatNotice.get()) mc.player.sendMessage(net.minecraft.text.Text.literal("[Nightfall] Respawn angefordert"), false);
    }
}
