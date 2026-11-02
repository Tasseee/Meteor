package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/** Lightweight target card for the entity currently under the crosshair. */
public final class TargetMarker extends Module {
    private final ModeSetting style = add(new ModeSetting("Stil", "Karte", "Karte", "Kompakt", "Fadenkreuz"));
    private final ColorSetting color = add(new ColorSetting("Akzentfarbe", 0x8B5CF6));
    private final BoolSetting distance = add(new BoolSetting("Distanz anzeigen", true));
    private final BoolSetting health = add(new BoolSetting("Leben anzeigen", true));
    public TargetMarker() { super("TargetMarker", "Zusätzliche Markierung für das Ziel unter dem Fadenkreuz.", Category.VISUALS); }
    @Override public void renderHud(DrawContext ctx) {
        if (mc.player == null || mc.world == null || mc.options.hudHidden || mc.currentScreen != null) return;
        Entity e = mc.targetedEntity;
        if (e == null || e == mc.player || !e.isAlive()) return;
        int cx = ctx.getScaledWindowWidth()/2, cy = ctx.getScaledWindowHeight()/2;
        int c = 0xFF000000 | color.rgb();
        if (style.is("Fadenkreuz")) {
            ctx.fill(cx-7,cy-7,cx+8,cy-5,c); ctx.fill(cx-7,cy+5,cx+8,cy+7,c);
            ctx.fill(cx-7,cy-5,cx-5,cy+5,c); ctx.fill(cx+5,cy-5,cx+7,cy+5,c);
        }
        String label = e.getName().getString();
        if (health.get() && e instanceof LivingEntity le) label += "  " + Math.round(le.getHealth()) + " HP";
        if (distance.get()) label += "  " + String.format(java.util.Locale.ROOT, "%.1fm", Math.sqrt(e.squaredDistanceTo(mc.player)));
        int w = mc.textRenderer.getWidth(label);
        int x = (ctx.getScaledWindowWidth()-w)/2, y = cy+18;
        if (style.is("Karte")) ctx.fill(x-5,y-3,x+w+5,y+mc.textRenderer.fontHeight+4,0xB9000000);
        ctx.fill(x-3,y-2,x-1,y+mc.textRenderer.fontHeight+2,c);
        ctx.drawText(mc.textRenderer,label,x,y,0xFFFFFFFF,false);
    }
}
