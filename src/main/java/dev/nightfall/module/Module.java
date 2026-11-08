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
    private boolean mouseKey;
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
    public void setKey(int k) { key = k; mouseKey = false; }
    public boolean isMouseKey() { return mouseKey; }
    public void setMouseKey(int button) { key = button; mouseKey = true; }
    public void clearKey() { key = -1; mouseKey = false; }

    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean e) {
        if (e == enabled) return;
        enabled = e;
        if (e) onEnable(); else onDisable();
        if (mc.player != null) NotificationCenter.push(name, e ? "Aktiviert" : "Deaktiviert");
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick() {}
    public void renderHud(DrawContext ctx) {}
}
