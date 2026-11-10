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

import java.util.List;
import java.util.Locale;

/**
 * Shows equipment that the client legitimately receives for the targeted player
 * and slots from the currently open server-provided menu. It does not inspect
 * another player's private inventory.
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
        super("InvSee", "HUD fuer sichtbare Spielerausruestung und aktuell geoeffnete Server-Menues.");
        w = 178;
        h = 108;
    }

    @Override
    protected int[] defaultPos(int sw, int sh) {
        return new int[]{Math.max(4, sw - 186), Math.max(42, sh / 2 - 54)};
    }

    @Override
    protected void draw(DrawContext ctx) {
        if (mc.player == null) return;

        PlayerEntity aimedPlayer = mc.targetedEntity instanceof PlayerEntity player
                && player != mc.player && player.isAlive() ? player : null;
        PlayerEntity target = aimedPlayer != null && aimedPlayer.distanceTo(mc.player) <= targetRange.get()
                ? aimedPlayer : null;

        boolean menuOpen = showMenuData.get() && mc.currentScreen instanceof HandledScreen<?>;
        String menuTitle = "";
        String menuStatus = !showMenuData.get() ? "Menue-Anzeige deaktiviert" : "Kein Server-Menue geoeffnet";
        List<ItemStack> menuStacks = List.of();
        boolean menuMatches = false;

        if (menuOpen) {
            HandledScreen<?> screen = (HandledScreen<?>) mc.currentScreen;
            menuTitle = screen.getTitle().getString();
            String filter = titleFilter.get().trim();
            menuMatches = filter.isEmpty() || menuTitle.toLowerCase(Locale.ROOT)
                    .contains(filter.toLowerCase(Locale.ROOT));
            if (menuMatches) {
                menuStacks = mc.player.currentScreenHandler.getStacks();
                menuStatus = menuTitle;
            } else {
                menuStatus = "Titel-Filter passt nicht";
            }
        }

        int screenHeight = mc.getWindow().getScaledHeight();
        int rows = menuOpen && menuMatches && !menuStacks.isEmpty()
                ? Math.min(maxEntries.getInt(), Math.max(1, (screenHeight - 132) / 12)) : 0;
        int menuHeight = showMenuData.get() ? 27 + rows * 12 : 0;
        h = 78 + menuHeight;

        ctx.fill(0, 0, w, h, 0xE510121B);
        ctx.fill(0, 0, w, 2, 0xFF65E6A5);

        String title = target != null ? "INVSEE · " + target.getName().getString() : "INVSEE · KEIN SPIELER";
        while (mc.textRenderer.getWidth(title) > w - 14 && title.length() > 12)
            title = title.substring(0, title.length() - 2) + "…";
        ctx.drawText(mc.textRenderer, title, 7, 7, 0xFF65E6A5, true);

        String targetStatus = target != null
                ? String.format(Locale.ROOT, "Ausrüstung · %.1f Blöcke", target.distanceTo(mc.player))
                : aimedPlayer != null ? "Spieler zu weit entfernt"
                : "Spieler im Fadenkreuz anvisieren";
        ctx.drawText(mc.textRenderer, targetStatus, 7, 20, 0xFFB8C0D5, false);

        for (int i = 0; i < SLOTS.length; i++) {
            int x = 7 + i * 28;
            ctx.drawText(mc.textRenderer, SLOT_LABELS[i], x + 7, 34, 0xFFB8C0D5, false);
            ctx.fill(x, 44, x + 24, 68, 0x66343B4D);
            ItemStack stack = target == null ? ItemStack.EMPTY : target.getEquippedStack(SLOTS[i]);
            if (stack.isEmpty()) {
                ctx.drawText(mc.textRenderer, target == null ? "?" : "–", x + 9, 52,
                        target == null ? 0xFF737B8F : 0xFFB0B5C5, false);
            } else {
                ctx.drawItem(stack, x + 4, 48);
                if (stack.getCount() > 1)
                    ctx.drawText(mc.textRenderer, Integer.toString(stack.getCount()), x + 13, 60, 0xFFFFFFFF, true);
            }
        }

        if (showMenuData.get()) {
            int top = 77;
            ctx.fill(5, top, w - 5, top + 18, 0x552E3948);
            String header = menuOpen && menuMatches ? "SERVER-MENUE · " + menuTitle : "SERVER-MENUE";
            while (mc.textRenderer.getWidth(header) > w - 16 && header.length() > 10)
                header = header.substring(0, header.length() - 2) + "…";
            ctx.drawText(mc.textRenderer, header, 8, top + 5, 0xFF65E6A5, false);

            if (!menuOpen || !menuMatches) {
                ctx.drawText(mc.textRenderer, menuStatus, 8, top + 23, 0xFFB0B5C5, false);
            } else {
                int row = 0;
                for (int i = 0; i < menuStacks.size() && row < rows; i++) {
                    ItemStack stack = menuStacks.get(i);
                    if (stack.isEmpty()) continue;
                    int y = top + 22 + row * 12;
                    String line = i + ": " + stack.getName().getString() + " x" + stack.getCount();
                    while (mc.textRenderer.getWidth(line) > w - 16 && line.length() > 8)
                        line = line.substring(0, line.length() - 2) + "…";
                    if ((row & 1) == 0) ctx.fill(7, y - 2, w - 7, y + 10, 0x301A1F2E);
                    ctx.drawText(mc.textRenderer, line, 9, y, 0xFFF0F2F7, false);
                    row++;
                }
                if (menuStacks.isEmpty())
                    ctx.drawText(mc.textRenderer, "Menue geoeffnet · keine Slots", 8, top + 23, 0xFFB0B5C5, false);
                else if (row == 0)
                    ctx.drawText(mc.textRenderer, "Keine belegten Slots", 8, top + 23, 0xFFB0B5C5, false);
                else if (menuStacks.stream().filter(stack -> !stack.isEmpty()).count() > row)
                    ctx.drawText(mc.textRenderer, "… weitere Slots vorhanden", 8, top + 22 + row * 12,
                            0xFFB0B5C5, false);
            }
        }
    }
}
