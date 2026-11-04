package dev.nightfall.module;

import dev.nightfall.module.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import dev.nightfall.util.NotificationCenter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class Module {
    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String name, description;
    private final Category category;
    private boolean enabled;
    private int key = -1;
    private final List<Setting<?>> settings = new ArrayList<>();

    protected Module(String name, String description, Category category) {
        this.name = name; this.description = description; this.category = category;
    }

    protected <S extends Setting<?>> S add(S s) { settings.add(s); return s; }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public Category getCategory() { return category; }
    public boolean isEnabled() { return enabled; }
    public List<Setting<?>> getSettings() { return Collections.unmodifiableList(settings); }

    public int getKey() { return key; }
    public void setKey(int k) { key = k; }

    public void toggle() { setEnabled(!enabled); }

    /**
     * Changes the enabled state only after the corresponding lifecycle hook succeeds.
     * If activation fails, the module is marked disabled and its cleanup hook is attempted.
     * A failed deactivation still leaves the module disabled so it cannot keep ticking.
     */
    public void setEnabled(boolean e) {
        if (e == enabled) return;

        if (e) {
            enabled = true;
            try {
                onEnable();
            } catch (RuntimeException | Error failure) {
                enabled = false;
                try {
                    onDisable();
                } catch (RuntimeException | Error cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                throw failure;
            }
        } else {
            // Mark disabled before cleanup: even a failing cleanup must not leave the
            // module eligible for another tick or render callback.
            enabled = false;
            onDisable();
        }

        if (mc.player != null) NotificationCenter.push(name, e ? "Aktiviert" : "Deaktiviert");
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick() {}
    public void renderHud(DrawContext ctx) {}
}
