package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.StringSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory-based crafting checklist. Ingredients use item-id:amount pairs separated by commas.
 * Example: minecraft:oak_planks:4,minecraft:stick:2
 */
public final class AutoCraftPlanner extends Module {
    private final StringSetting ingredients = add(new StringSetting(
            "Zutaten (ID:Menge, Komma)", "minecraft:oak_planks:4,minecraft:stick:2", 256));
    private final BoolSetting showHud = add(new BoolSetting("HUD anzeigen", true));

    public AutoCraftPlanner() {
        super("AutoCraft Planner", "Prüft eine selbst definierte Zutatenliste gegen dein Inventar; craftet nicht automatisch.", Category.WORLD);
    }

    private record Requirement(String id, int amount, int owned) {}
    private List<Requirement> requirements() {
        List<Requirement> out = new ArrayList<>();
        if (mc.player == null) return out;
        for (String part : ingredients.get().split(",")) {
            String clean = part.trim();
            int cut = clean.lastIndexOf(':');
            if (cut <= 0) continue;
            String idText = clean.substring(0, cut);
            int amount;
            try { amount = Integer.parseInt(clean.substring(cut + 1)); }
            catch (NumberFormatException ignored) { continue; }
            Identifier id = Identifier.tryParse(idText);
            if (id == null || amount < 1 || amount > 64) continue;
            var item = Registries.ITEM.get(id);
            if (item == net.minecraft.item.Items.AIR) continue;
            int owned = 0;
            for (int slot = 0; slot < mc.player.getInventory().size(); slot++) {
                var stack = mc.player.getInventory().getStack(slot);
                if (stack.isOf(item)) owned += stack.getCount();
            }
            out.add(new Requirement(idText, amount, owned));
        }
        return out;
    }

    @Override public void renderHud(DrawContext ctx) {
        if (!showHud.get() || mc.player == null) return;
        List<Requirement> list = requirements();
        if (list.isEmpty()) return;
        int x = 8, y = 96;
        int ready = 0;
        for (Requirement r : list) if (r.owned() >= r.amount()) ready++;
        ctx.fill(x - 4, y - 4, x + 206, y + 17 + list.size() * 12, 0xAA10131D);
        ctx.drawText(mc.textRenderer, "CRAFT PLAN  " + ready + "/" + list.size(), x, y, 0xFF65E6BE, true);
        int row = 0;
        for (Requirement r : list) {
            String shortId = r.id().substring(r.id().indexOf(':') + 1);
            String line = shortId + "  " + r.owned() + "/" + r.amount();
            int color = r.owned() >= r.amount() ? 0xFF65E6BE : 0xFFFFC56E;
            ctx.drawText(mc.textRenderer, line, x, y + 13 + row++ * 12, color, false);
        }
    }
}
