package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;

/** Local-only first-person inspection and swing transforms. No packets or attack timing are modified. */
public final class WeaponAnimations extends Module {
    private final ModeSetting swing = add(new ModeSetting("Swing-Stil", "Vanilla+", "Vanilla+", "Punch", "Sweep", "Spin", "Snap"));
    private final BoolSetting inspect = add(new BoolSetting("Inspektion aktiv", false));
    private final NumberSetting inspectSpeed = add(new NumberSetting("Inspektionsgeschwindigkeit", 1.0, 0.25, 2.5, 0.05));
    private final NumberSetting swingPower = add(new NumberSetting("Swing-Stärke", 1.0, 0.25, 2.0, 0.05));

    public WeaponAnimations() {
        super("WeaponAnimations", "Waffeninspektion und lokale First-Person-Swing-Animationen.", Category.VISUALS);
    }

    public void transform(MatrixStack matrices, float swingProgress, boolean leftHand) {
        float side = leftHand ? -1f : 1f;
        if (inspect.get()) {
            double phase = (System.currentTimeMillis() * 0.0015 * inspectSpeed.get()) % (Math.PI * 2.0);
            float lift = (float)(Math.sin(phase) * 0.045);
            matrices.translate(0.0, -0.11 + lift, -0.08);
            matrices.multiply(new Quaternionf().rotateX((float)Math.toRadians(-28 + Math.sin(phase) * 8))
                    .rotateY((float)Math.toRadians(side * (32 + Math.cos(phase) * 14)))
                    .rotateZ((float)Math.toRadians(side * 24)));
            return;
        }
        if (swingProgress <= 0f) return;
        float p = Math.max(0f, Math.min(1f, swingProgress));
        float wave = (float)Math.sin(Math.PI * p) * swingPower.get().floatValue();
        switch (swing.get()) {
            case "Punch" -> {
                matrices.translate(side * -0.08 * wave, 0.045 * wave, -0.06 * wave);
                matrices.multiply(new Quaternionf().rotateX((float)Math.toRadians(-22 * wave)).rotateZ((float)Math.toRadians(side * -13 * wave)));
            }
            case "Sweep" -> {
                matrices.translate(side * -0.13 * wave, -0.025 * wave, 0.02 * wave);
                matrices.multiply(new Quaternionf().rotateY((float)Math.toRadians(side * 48 * wave)).rotateZ((float)Math.toRadians(side * 18 * wave)));
            }
            case "Spin" -> matrices.multiply(new Quaternionf().rotateZ((float)Math.toRadians(side * 360 * p * swingPower.get().floatValue())));
            case "Snap" -> {
                float snap = (float)Math.pow(p, 0.35);
                matrices.translate(side * -0.04 * snap, 0.025 * wave, -0.09 * snap);
                matrices.multiply(new Quaternionf().rotateX((float)Math.toRadians(-38 * snap)).rotateY((float)Math.toRadians(side * 20 * snap)));
            }
            default -> matrices.translate(0, 0.012 * wave, 0);
        }
    }
}
