package dev.nightfall.module;

import dev.nightfall.Nightfall;
import dev.nightfall.config.Config;
import dev.nightfall.modules.*;
import dev.nightfall.util.Projection;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    private static final boolean[] KEY_DOWN = new boolean[GLFW.GLFW_KEY_LAST + 1];
    public static int errorCount;

    private ModuleManager() {}

    public static void init() {
        MODULES.clear();
        // Combat
        MODULES.add(new TrapCrit());
        MODULES.add(new KillAura());
        MODULES.add(new TriggerBot());
        MODULES.add(new AutoPvp());
        MODULES.add(new TrapperBot());
        MODULES.add(new CrystalAura());
        MODULES.add(new AutoAnchor());
        MODULES.add(new MaceAssist());
        MODULES.add(new AutoTotem());
        MODULES.add(new AutoGap());
        // Movement
        MODULES.add(new Fly());
        MODULES.add(new ElytraFly());
        MODULES.add(new BoatFly());
        MODULES.add(new Speed());
        MODULES.add(new Sprint());
        MODULES.add(new Step());
        MODULES.add(new SafeWalk());
        MODULES.add(new LongJump());
        MODULES.add(new Jesus());
        MODULES.add(new WaterWalk());
        MODULES.add(new Freecam());
        // Visuals
        MODULES.add(new FreeLook());
        MODULES.add(new PlayerEsp());
        MODULES.add(new EntityEsp());
        MODULES.add(new ItemEsp());
        MODULES.add(new StorageEsp());
        MODULES.add(new BlockNotifier());
        MODULES.add(new ParticleTrail());
        MODULES.add(new HitParticles());
        MODULES.add(new JumpCircles());
        MODULES.add(new MovementParticles());
        MODULES.add(new TargetMarker());
        MODULES.add(new Atmosphere());
        MODULES.add(new SkyVisuals());
        MODULES.add(new FirstPersonStyle());
        MODULES.add(new WeaponAnimations());
        MODULES.add(new GlowESP());
        MODULES.add(new GUITheme());
        MODULES.add(new CustomCrosshair());
        MODULES.add(new SpawnerEsp());
        MODULES.add(new ChunkEsp());
        // World
        MODULES.add(new Waypoints());
        MODULES.add(new FakeBases());
        MODULES.add(new SeedCracker());
        // Basefinder
        MODULES.add(new BaseFinder());
        // Misc
        MODULES.add(new AutoTpa());
        MODULES.add(new AutoText());
        MODULES.add(new SpawnerProtect());
        MODULES.add(new StaffWatch());
        MODULES.add(new AntiVanishHints());
        // HUD
        MODULES.add(new Watermark());
        MODULES.add(new Coordinates());
        MODULES.add(new ModuleList());
        MODULES.add(new Minimap());
        MODULES.add(new ScannerHud());
        MODULES.add(new CombatStatusHud());
        MODULES.add(new ArmorHud());
        MODULES.add(new CooldownHud());
        MODULES.add(new LowHealthWarning());
        MODULES.add(new FakeStats());
        MODULES.add(new NotificationsHud());
        MODULES.add(new WaypointHud());
        MODULES.add(new DebugHud());
        MODULES.add(new TextHuds.Fps());
        MODULES.add(new TextHuds.Ping());
        MODULES.add(new TextHuds.Clock());
        MODULES.add(new TextHuds.Direction());
        MODULES.add(new TextHuds.BiomeHud());
    }

    public static List<Module> all() { return Collections.unmodifiableList(MODULES); }

    public static List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : MODULES) if (m.getCategory() == c) out.add(m);
        return out;
    }

    public static List<HudModule> hudElements() {
        List<HudModule> out = new ArrayList<>();
        for (Module m : MODULES) if (m instanceof HudModule hm) out.add(hm);
        return out;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Module> T get(Class<T> cls) {
        for (Module m : MODULES) if (m.getClass() == cls) return (T) m;
        throw new IllegalStateException("Modul nicht registriert: " + cls.getSimpleName());
    }

    /** @return Fehlertext bei Konflikt, sonst null (Tastenzuweisung erfolgreich). */
    public static String assignKey(Module m, int key) {
        if (key >= 0) {
            if (key == Config.guiKey || key == Config.hudKey || key == Config.mapKey) return "Taste ist bereits fuer GUI/HUD/Karte belegt";
            for (Module o : MODULES) if (o != m && o.getKey() == key) return "Taste bereits belegt von " + o.getName();
        }
        m.setKey(key);
        return null;
    }

    public static void pollKeys(MinecraftClient mc) {
        if (mc.getWindow() == null) return;
        long h = mc.getWindow().getHandle();
        for (Module m : MODULES) {
            int k = m.getKey();
            if (k < 0 || k > GLFW.GLFW_KEY_LAST) continue;
            boolean d = GLFW.glfwGetKey(h, k) == GLFW.GLFW_PRESS;
            if (d && !KEY_DOWN[k] && mc.currentScreen == null && mc.player != null) m.toggle();
            KEY_DOWN[k] = d;
        }
    }

    /** Clear transient module state when a world or server connection ends. */
    public static void onWorldLeave() {
        for (Module m : MODULES) {
            try {
                m.onWorldLeave();
            } catch (Throwable t) {
                Nightfall.LOGGER.error("Fehler beim Zuruecksetzen von Modul {}", m.getName(), t);
            }
        }
    }

    public static void tick() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try {
                m.onTick();
            } catch (Throwable t) {
                Nightfall.LOGGER.error("Fehler in Modul {} - Modul wird deaktiviert", m.getName(), t);
                errorCount++;
                m.setEnabled(false);
            }
        }
    }

    public static void renderHud(DrawContext ctx, float tickDelta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Projection.update(mc, tickDelta, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight());
        EspBase.drawn = 0;
        for (Module m : MODULES) {
            if (!m.isEnabled()) continue;
            try {
                m.renderHud(ctx);
            } catch (Throwable t) {
                Nightfall.LOGGER.error("Render-Fehler in Modul {} - Modul wird deaktiviert", m.getName(), t);
                errorCount++;
                m.setEnabled(false);
            }
        }
    }
}
