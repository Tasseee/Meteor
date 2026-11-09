package dev.nightfall.modules;

import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/**
 * Displays replicated target equipment and, separately, slots in an open server-provided menu.
 * It never reads another player's private inventory.
 */
public final class InvSee extends HudModule {
    private final StringSetting titleFilter = add(new StringSetting("Titel-Filter (leer = alle)", "", 48));
    private final NumberSetting maxEntries = add(new NumberSetting("Max. Eintraege", 18, 6, 54, 1));
    private final BoolSetting showMenuData = add(new BoolSetting("Geoeffnete Menue-Slots", true));
    private final NumberSetting targetRange = add(new NumberSetting("Ziel-Reichweite", 8, 2, 32, 1));

    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final String[] SLOT_LABELS = {"MH", "OH", "K", "R", "H", "S"};

    public InvSee() {
        super("InvSee", "Verschiebbarer HUD-Blick auf sichtbare Spielerausruestung und Server-Menues.");
        w = 178;
        h = 54;
    }

    @Override
    protected int[] defaultPos(int sw, int sh) {
        return new int[]{Math.max(4, sw - 186), Math.max(42, sh / 2 - 27)};
    }

    @Override
    protected void draw(DrawContext ctx) {
        if (mc.player == null) return;

        PlayerEntity target = mc.targetedEntity instanceof PlayerEntity player
                && player != mc.player && player.isAlive()
                && player.distanceTo(mc.player) <= targetRange.get() ? player : null;

        String menuTitle = "";
        java.util.List<ItemStack> menuStacks = java.util.List.of();
        if (showMenuData.get() && mc.currentScreen instanceof HandledScreen<?> screen) {
            String filter = titleFilter.get().trim();
            menuTitle = screen.getTitle().getString();
            if (filter.isEmpty() || menuTitle.toLowerCase(java.util.Locale.ROOT)
                    .contains(filter.toLowerCase(java.util.Locale.ROOT))) {
                menuStacks = mc.player.currentScreenHandler.getStacks();
            } else menuTitle = "";
        }

        int screenHeight = mc.getWindow().getScaledHeight();
        int rows = menuStacks.isEmpty() ? 0
                : Math.min(maxEntries.getInt(), Math.max(1, (screenHeight - 112) / 12));
        h = 54 + (rows > 0 ? 17 + rows * 12 : 0);

        ctx.fill(0, 0, w, h, 0xD910121B);
        ctx.fill(0, 0, w, 2, 0xFF65E6A5);
        String title = target == null ? "INVSEE · KEIN SPIELER IM FADENKREUZ"
                : "INVSEE · " + target.getName().getString();
        while (mc.textRenderer.getWidth(title) > w - 14 && title.length() > 12)
            title = title.substring(0, title.length() - 2) + "…";
        ctx.drawText(mc.textRenderer, title, 7, 7, 0xFF65E6A5, true);

        for (int i = 0; i < SLOTS.length; i++) {
            int x = 7 + i * 28;
            ctx.drawText(mc.textRenderer, SLOT_LABELS[i], x + 7, 17, 0xFFB8C0D5, false);
            ctx.fill(x, 27, x + 24, 51, 0x66343B4D);
            ItemStack stack = target == null ? ItemStack.EMPTY : target.getEquippedStack(SLOTS[i]);
            if (stack.isEmpty()) {
                ctx.drawText(mc.textRenderer, "–", x + 9, 35, 0xFF737B8F, false);
            } else {
                // Equipment is the only per-player data exposed here; hidden inventory slots are not queried.
                ctx.drawItem(stack, x + 4, 31);
                if (stack.getCount() > 1) ctx.drawItemInSlot(mc.textRenderer, stack, x + 4, 31);
            }
        }

        if (rows > 0) {
            int top = 58;
            ctx.fill(5, top, w - 5, top + 15, 0x552E3948);
            ctx.drawText(mc.textRenderer, "SERVER-MENUE · " + menuTitle, 8, top + 4, 0xFF65E6A5, false);
            int row = 0;
            for (int i = 0; i < menuStacks.size() && row < rows; i++) {
                ItemStack stack = menuStacks.get(i);
                if (stack.isEmpty()) continue;
                int y = top + 19 + row * 12;
                String line = i + ": " + stack.getName().getString() + " x" + stack.getCount();
                while (mc.textRenderer.getWidth(line) > w - 16 && line.length() > 8)
                    line = line.substring(0, line.length() - 2) + "…";
                if ((row & 1) == 0) ctx.fill(7, y - 2, w - 7, y + 10, 0x301A1F2E);
                ctx.drawText(mc.textRenderer, line, 9, y, 0xFFF0F2F7, false);
                row++;
            }
            if (row == 0) ctx.drawText(mc.textRenderer, "Keine belegten Slots", 9, top + 21, 0xFFB0B5C5, false);
        }
    }
}
