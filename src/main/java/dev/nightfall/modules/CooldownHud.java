package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Items;

/** Shows item cooldowns using the local client's cooldown manager. */
public final class CooldownHud extends HudModule {
    private final BoolSetting pearls = add(new BoolSetting("Enderperle", true));
    private final BoolSetting windCharges = add(new BoolSetting("Wind Charge", true));
    private final BoolSetting chorus = add(new BoolSetting("Chorusfrucht", false));

    public CooldownHud() {
        super("CooldownHUD", "Zeigt relevante Item-Abklingzeiten.");
        setEnabled(true);
    }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{sw - 126, sh - 72}; }

    @Override protected void draw(DrawContext ctx) {
        if (mc.player == null) return;
        var items = new net.minecraft.item.ItemStack[]{Items.ENDER_PEARL.getDefaultStack(), Items.WIND_CHARGE.getDefaultStack(), Items.CHORUS_FRUIT.getDefaultStack()};
        String[] names = {"Pearl", "Wind", "Chorus"};
        boolean[] enabled = {pearls.get(), windCharges.get(), chorus.get()};
        int count = 0;
        for (boolean e : enabled) if (e) count++;
        w = 120;
        h = 7 + count * 16;
        ctx.fill(0, 0, w, h, 0xA0101014);
        ctx.fill(0, 0, 2, h, Theme.ACCENT);
        int line = 0;
        for (int i = 0; i < items.length; i++) {
            if (!enabled[i]) continue;
            float cooldown = mc.player.getItemCooldownManager().getCooldownProgress(items[i], 0.0f);
            int y = 4 + line * 16;
            String status = cooldown > 0.001f ? Math.round(cooldown * 100f) + "%" : "Ready";
            ctx.drawItem(items[i], 5, y - 2);
            ctx.drawText(mc.textRenderer, names[i], 25, y, Theme.TEXT, false);
            int right = w - 6;
            int labelWidth = mc.textRenderer.getWidth(status);
            ctx.drawText(mc.textRenderer, status, right - labelWidth, y, cooldown > 0.001f ? 0xFFFFC857 : 0xFF42D392, false);
            ctx.fill(25, y + 10, right, y + 12, 0xFF30303A);
            float remaining = Math.max(0f, Math.min(1f, cooldown));
            ctx.fill(25, y + 10, right - Math.round((right - 25) * (1f - remaining)), y + 12,
                    cooldown > 0.001f ? 0xFFFFC857 : 0xFF42D392);
            line++;
        }
    }
}
