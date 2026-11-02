package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Quaternionf;

/** Changes the local first-person arm/item model transform. The arm itself remains Minecraft's 3D player model. */
public final class FirstPersonStyle extends Module {
    private final ModeSetting pose = add(new ModeSetting("Handhaltung", "Vanilla", "Vanilla", "Katana", "Heavy", "Floating", "Compact"));
    private final NumberSetting x = add(new NumberSetting("X-Position", 0, -0.35, 0.35, 0.01));
    private final NumberSetting y = add(new NumberSetting("Y-Position", 0, -0.35, 0.35, 0.01));
    private final NumberSetting z = add(new NumberSetting("Z-Position", 0, -0.35, 0.35, 0.01));
    private final NumberSetting pitch = add(new NumberSetting("X-Rotation", 0, -45, 45, 1));
    private final NumberSetting yaw = add(new NumberSetting("Y-Rotation", 0, -45, 45, 1));
    private final NumberSetting roll = add(new NumberSetting("Z-Rotation", 0, -90, 90, 1));
    private final NumberSetting scale = add(new NumberSetting("3D-Skalierung", 1.0, 0.65, 1.35, 0.01));

    public FirstPersonStyle() {
        super("FirstPersonStyle", "3D-Arm und gehaltenes Item lokal positionieren, drehen und skalieren.", Category.VISUALS);
    }

    public void transform(MatrixStack matrices, boolean leftHand) {
        float side = leftHand ? -1f : 1f;
        double tx = x.get(), ty = y.get(), tz = z.get();
        float rx = pitch.get().floatValue(), ry = yaw.get().floatValue(), rz = roll.get().floatValue();
        switch (pose.get()) {
            case "Katana" -> { tx += 0.06 * side; ty -= 0.035; rz -= 22 * side; ry += 12 * side; }
            case "Heavy" -> { tx -= 0.025 * side; ty += 0.035; rz += 15 * side; rx += 12; }
            case "Floating" -> { ty -= 0.07; rz += 8 * side; }
            case "Compact" -> { tx += 0.045 * side; ty += 0.025; }
        }
        matrices.translate(tx * side, ty, tz);
        matrices.multiply(new Quaternionf().rotateX((float)Math.toRadians(rx))
                .rotateY((float)Math.toRadians(ry * side)).rotateZ((float)Math.toRadians(rz * side)));
        float s = scale.get().floatValue();
        matrices.scale(s, s, s);
    }
}
