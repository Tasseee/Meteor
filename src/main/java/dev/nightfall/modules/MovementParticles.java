package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.BoolSetting;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;

/** Local-only movement and landing particle effects. */
public final class MovementParticles extends Module {
    private final ModeSetting effect = add(new ModeSetting("Effekt", "Totem", "Totem", "Soul", "Portal", "Herzen", "Wolke", "Flamme", "End Rod"));
    private final NumberSetting amount = add(new NumberSetting("Menge", 3, 1, 10, 1));
    private final BoolSetting running = add(new BoolSetting("Beim Laufen", true));
    private final BoolSetting jumping = add(new BoolSetting("Beim Springen/Landen", true));
    private boolean wasGrounded = true;
    private int ticks;
    @Override public void onWorldLeave() { wasGrounded = true; ticks = 0; }
    public MovementParticles() { super("MovementParticles", "Zusätzliche lokale Bewegungs-, Sprung- und Totem-Optik.", Category.VISUALS); }
    @Override public void onTick() {
        if (mc.player == null || mc.world == null) return;
        boolean grounded = mc.player.isOnGround();
        boolean moving = mc.player.getVelocity().horizontalLengthSquared() > 0.003;
        if (running.get() && moving && grounded && ++ticks % 3 == 0) burst(false);
        if (jumping.get() && grounded && !wasGrounded) burst(true);
        wasGrounded = grounded;
    }
    private void burst(boolean ring) {
        ParticleEffect p = switch (effect.get()) {
            case "Soul" -> ParticleTypes.SOUL; case "Portal" -> ParticleTypes.PORTAL;
            case "Herzen" -> ParticleTypes.HEART; case "Wolke" -> ParticleTypes.CLOUD;
            case "Flamme" -> ParticleTypes.FLAME; case "End Rod" -> ParticleTypes.END_ROD;
            default -> ParticleTypes.TOTEM_OF_UNDYING;
        };
        int n = amount.getInt() * (ring ? 4 : 1);
        for (int i=0;i<n;i++) {
            double a = Math.PI*2*i/n, r = ring ? 0.65 : 0.28;
            double x = mc.player.getX() + Math.cos(a)*r + (Math.random()-.5)*.12;
            double z = mc.player.getZ() + Math.sin(a)*r + (Math.random()-.5)*.12;
            mc.particleManager.addParticle(p,x,mc.player.getY()+0.1+Math.random()*.5,z,Math.cos(a)*.015,.025,Math.sin(a)*.015);
        }
    }
}
