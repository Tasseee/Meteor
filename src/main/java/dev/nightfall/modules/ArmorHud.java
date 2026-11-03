package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.EquipmentSlot;

/** Compact armor row with durability bars and percentage labels. */
public final class ArmorHud extends HudModule {
    private final BoolSetting durabilityText = add(new BoolSetting("Haltbarkeit anzeigen", true));

    public ArmorHud() {
        super("ArmorHUD", "Ruestung mit Haltbarkeit und Status anzeigen.");
        setEnabled(true);
    }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{sw / 2 - 72, sh - 58}; }

    @Override protected void draw(DrawContext ctx) {
        if (mc.player == null) return;
        w = 144;
        h = 38;
        ctx.fill(0, 0, w, h, 0xA0101014);
        ctx.fill(0, 0, w, 2, Theme.ACCENT);
        // Minecraft armor inventory order: boots, leggings, chestplate, helmet.
        for (int i = 0; i < 4; i++) {
            ItemStack stack = switch (i) { case 0 -> mc.player.getEquippedStack(EquipmentSlot.FEET); case 1 -> mc.player.getEquippedStack(EquipmentSlot.LEGS); case 2 -> mc.player.getEquippedStack(EquipmentSlot.CHEST); default -> mc.player.getEquippedStack(EquipmentSlot.HEAD); };
            int x = 6 + i * 35;
            int y = 4;
            if (!stack.isEmpty()) {
                ctx.drawItem(stack, x + 7, y);
                if (stack.isDamageable()) {
                    int max = stack.getMaxDamage();
                    int remaining = Math.max(0, max - stack.getDamage());
                    float ratio = max <= 0 ? 1f : remaining / (float) max;
                    int color = ratio > .5f ? 0xFF42D392 : ratio > .2f ? 0xFFFFC857 : 0xFFFF5D6C;
                    ctx.fill(x + 2, 24, x + 31, 27, 0xFF30303A);
                    ctx.fill(x + 2, 24, x + 2 + Math.round(29 * ratio), 27, color);
                    if (durabilityText.get()) {
                        String pct = Math.round(ratio * 100) + "%";
                        ctx.drawText(mc.textRenderer, pct, x + (35 - mc.textRenderer.getWidth(pct)) / 2, 28, color, false);
                    }
                } else {
                    ctx.drawText(mc.textRenderer, "OK", x + 12, 28, Theme.TEXT_DIM, false);
                }
            } else {
                ctx.drawText(mc.textRenderer, "-", x + 14, 12, Theme.TEXT_DIM, false);
                ctx.drawText(mc.textRenderer, "Leer", x + 6, 28, Theme.TEXT_DIM, false);
            }
        }
    }
}
