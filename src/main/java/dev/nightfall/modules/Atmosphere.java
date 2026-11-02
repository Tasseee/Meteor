package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.ColorSetting;
import net.minecraft.client.gui.DrawContext;

/** Optional subtle top-of-screen atmospheric tint; does not replace the 3D sky renderer. */
public final class Atmosphere extends Module {
    private final ModeSetting style = add(new ModeSetting("Stil", "Nordlicht", "Weltraum", "Nordlicht", "Planet"));
    private final NumberSetting strength = add(new NumberSetting("Intensität", 18, 4, 45, 1));
    private final ColorSetting custom = add(new ColorSetting("Farbe", 0x45D7C8));
    public Atmosphere() { super("Atmosphere", "Dezente optionale Farbatmosphäre am oberen Bildschirmrand (kein echter Himmel-Shader).", Category.VISUALS); }
    @Override public void renderHud(DrawContext ctx) {
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
        int w=ctx.getScaledWindowWidth(), h=ctx.getScaledWindowHeight();
        int base = style.is("Weltraum") ? 0x342A3F : style.is("Planet") ? 0xB85B30 : custom.rgb();
        int alpha = strength.getInt();
        for (int i=0;i<8;i++) {
            int a = Math.max(0, alpha - i*alpha/8);
            ctx.fill(0,i*h/12,w,(i+1)*h/12,(a<<24)|(base&0xFFFFFF));
        }
    }
}
