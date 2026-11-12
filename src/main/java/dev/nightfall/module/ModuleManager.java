package dev.nightfall.module;

import dev.nightfall.Nightfall;
import dev.nightfall.config.Config;
import dev.nightfall.modules.AdvancedWorldOverlay;
import dev.nightfall.modules.AntiCheatMonitor;
import dev.nightfall.modules.AntiVanishHints;
import dev.nightfall.modules.ArmorHud;
import dev.nightfall.modules.Atmosphere;
import dev.nightfall.modules.AutoAnchor;
import dev.nightfall.modules.AutoBreed;
import dev.nightfall.modules.AutoCraftPlanner;
import dev.nightfall.modules.AutoFarm;
import dev.nightfall.modules.AutoFish;
import dev.nightfall.modules.AutoGap;
import dev.nightfall.modules.AutoPvp;
import dev.nightfall.modules.DTap;
import dev.nightfall.modules.AutoReconnect;
import dev.nightfall.modules.AutoRespawn;
import dev.nightfall.modules.AutoShield;
import dev.nightfall.modules.AutoSign;
import dev.nightfall.modules.AutoSmelter;
import dev.nightfall.modules.AutoText;
import dev.nightfall.modules.AutoTotem;
import dev.nightfall.modules.AutoTpa;
import dev.nightfall.modules.BaseFinder;
import dev.nightfall.modules.BlockESP;
import dev.nightfall.modules.BlockNotifier;
import dev.nightfall.modules.BoatFly;
import dev.nightfall.modules.ChunkEsp;
import dev.nightfall.modules.CombatStatusHud;
import dev.nightfall.modules.CooldownHud;
import dev.nightfall.modules.Coordinates;
import dev.nightfall.modules.CrystalAura;
import dev.nightfall.modules.CustomCrosshair;
import dev.nightfall.modules.DebugHud;
import dev.nightfall.modules.DroppedItemNotifier;
import dev.nightfall.modules.ElytraFly;
import dev.nightfall.modules.EntityEsp;
import dev.nightfall.modules.EspBase;
import dev.nightfall.modules.FakeBases;
import dev.nightfall.modules.FakeStats;
import dev.nightfall.modules.FavoriteChunk;
import dev.nightfall.modules.FirstPersonStyle;
import dev.nightfall.modules.Fly;
import dev.nightfall.modules.FreeLook;
import dev.nightfall.modules.Freecam;
import dev.nightfall.modules.FriendManager;
import dev.nightfall.modules.GUITheme;
import dev.nightfall.modules.GlowESP;
import dev.nightfall.modules.Hats;
import dev.nightfall.modules.HitParticles;
import dev.nightfall.modules.InvSee;
import dev.nightfall.modules.ItemEsp;
import dev.nightfall.modules.ItemGlint;
import dev.nightfall.modules.Jesus;
import dev.nightfall.modules.JumpCircles;
import dev.nightfall.modules.KillAura;
import dev.nightfall.modules.LongJump;
import dev.nightfall.modules.LootDropFinder;
import dev.nightfall.modules.LowHealthWarning;
import dev.nightfall.modules.MaceAssist;
import dev.nightfall.modules.MacroManager;
import dev.nightfall.modules.Minimap;
import dev.nightfall.modules.ModuleList;
import dev.nightfall.modules.MovementParticles;
import dev.nightfall.modules.NoFall;
import dev.nightfall.modules.NotificationsHud;
import dev.nightfall.modules.ObiTrapBot;
import dev.nightfall.modules.ParticleTrail;
import dev.nightfall.modules.PlayerEsp;
import dev.nightfall.modules.SafeWalk;
import dev.nightfall.modules.ScannerHud;
import dev.nightfall.modules.SeedCracker;
import dev.nightfall.modules.ShieldBreaker;
import dev.nightfall.modules.SkyVisuals;
import dev.nightfall.modules.SlowSwing;
import dev.nightfall.modules.SpawnerEsp;
import dev.nightfall.modules.SpawnerProtect;
import dev.nightfall.modules.Speed;
import dev.nightfall.modules.SpotifyLyricsHud;
import dev.nightfall.modules.Sprint;
import dev.nightfall.modules.StaffWatch;
import dev.nightfall.modules.Step;
import dev.nightfall.modules.StorageEsp;
import dev.nightfall.modules.StructureESP;
import dev.nightfall.modules.TargetMarker;
import dev.nightfall.modules.TargetStrafe;
import dev.nightfall.modules.TextHuds;
import dev.nightfall.modules.TrapCrit;
import dev.nightfall.modules.TrapperBot;
import dev.nightfall.modules.TriggerBot;
import dev.nightfall.modules.WaterWalk;
import dev.nightfall.modules.Watermark;
import dev.nightfall.modules.WaypointHud;
import dev.nightfall.modules.Waypoints;
import dev.nightfall.modules.WeaponAnimations;
import dev.nightfall.modules.WorldLyrics;
import dev.nightfall.util.Projection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.class_310;
import net.minecraft.class_332;
import org.lwjgl.glfw.GLFW;

public final class ModuleManager {
   private static final List<Module> MODULES = new ArrayList<>();
   private static final boolean[] KEY_DOWN = new boolean[349];
   public static int errorCount;

   private ModuleManager() {
   }

   public static void init() {
      MODULES.clear();
      MODULES.add(new TrapCrit());
      MODULES.add(new KillAura());
      MODULES.add(new TriggerBot());
      MODULES.add(new FriendManager());
      MODULES.add(new AutoPvp());
      MODULES.add(new DTap());
      MODULES.add(new TrapperBot());
      MODULES.add(new ObiTrapBot());
      MODULES.add(new AutoShield());
      MODULES.add(new TargetStrafe());
      MODULES.add(new ShieldBreaker());
      MODULES.add(new CrystalAura());
      MODULES.add(new AutoAnchor());
      MODULES.add(new MaceAssist());
      MODULES.add(new AutoTotem());
      MODULES.add(new AutoGap());
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
      MODULES.add(new NoFall());
      MODULES.add(new FreeLook());
      MODULES.add(new PlayerEsp());
      MODULES.add(new EntityEsp());
      MODULES.add(new ItemEsp());
      MODULES.add(new DroppedItemNotifier());
      MODULES.add(new SpotifyLyricsHud());
      MODULES.add(new WorldLyrics());
      MODULES.add(new LootDropFinder());
      MODULES.add(new StorageEsp());
      MODULES.add(new BlockNotifier());
      MODULES.add(new ParticleTrail());
      MODULES.add(new HitParticles());
      MODULES.add(new JumpCircles());
      MODULES.add(new MovementParticles());
      MODULES.add(new TargetMarker());
      MODULES.add(new Atmosphere());
      MODULES.add(new SkyVisuals());
      MODULES.add(new Hats());
      MODULES.add(new FirstPersonStyle());
      MODULES.add(new WeaponAnimations());
      MODULES.add(new SlowSwing());
      MODULES.add(new InvSee());
      MODULES.add(new GlowESP());
      MODULES.add(new ItemGlint());
      MODULES.add(new GUITheme());
      MODULES.add(new CustomCrosshair());
      MODULES.add(new SpawnerEsp());
      MODULES.add(new ChunkEsp());
      MODULES.add(new BlockESP());
      MODULES.add(new StructureESP());
      MODULES.add(new Waypoints());
      MODULES.add(new FakeBases());
      MODULES.add(new SeedCracker());
      MODULES.add(new FavoriteChunk());
      MODULES.add(new AdvancedWorldOverlay());
      MODULES.add(new AutoFarm());
      MODULES.add(new AutoBreed());
      MODULES.add(new AutoSmelter());
      MODULES.add(new AutoCraftPlanner());
      MODULES.add(new BaseFinder());
      MODULES.add(new AutoReconnect());
      MODULES.add(new AutoSign());
      MODULES.add(new AutoTpa());
      MODULES.add(new AutoText());
      MODULES.add(new SpawnerProtect());
      MODULES.add(new StaffWatch());
      MODULES.add(new AntiVanishHints());
      MODULES.add(new AntiCheatMonitor());
      MODULES.add(new AutoRespawn());
      MODULES.add(new AutoFish());
      MODULES.add(new MacroManager());
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
      get(FirstPersonStyle.class).setEnabled(true);
   }

   public static List<Module> all() {
      return Collections.unmodifiableList(MODULES);
   }

   public static List<Module> byCategory(Category c) {
      List<Module> out = new ArrayList<>();

      for (Module m : MODULES) {
         if (m.getCategory() == c) {
            out.add(m);
         }
      }

      return out;
   }

   public static List<HudModule> hudElements() {
      List<HudModule> out = new ArrayList<>();

      for (Module m : MODULES) {
         if (m instanceof HudModule hm) {
            out.add(hm);
         }
      }

      return out;
   }

   public static <T extends Module> T get(Class<T> cls) {
      for (Module m : MODULES) {
         if (m.getClass() == cls) {
            return (T)m;
         }
      }

      throw new IllegalStateException("Modul nicht registriert: " + cls.getSimpleName());
   }

   public static String assignKey(Module m, int key) {
      if (key >= 0) {
         if (key == Config.guiKey || key == Config.hudKey || key == Config.mapKey) {
            return "Taste ist bereits fuer GUI/HUD/Karte belegt";
         }

         for (Module o : MODULES) {
            if (o != m && o.getKey() == key) {
               return "Taste bereits belegt von " + o.getName();
            }
         }
      }

      m.setKey(key);
      return null;
   }

   public static void pollKeys(class_310 mc) {
      if (mc.method_22683() != null) {
         long h = mc.method_22683().method_4490();

         for (Module m : MODULES) {
            int k = m.getKey();
            if (k >= 0 && k <= 348) {
               boolean d = GLFW.glfwGetKey(h, k) == 1;
               if (d && !KEY_DOWN[k] && mc.field_1755 == null && mc.field_1724 != null) {
                  m.toggle();
               }

               KEY_DOWN[k] = d;
            }
         }
      }
   }

   public static void onWorldLeave() {
      for (Module m : MODULES) {
         try {
            m.onWorldLeave();
         } catch (Throwable var3) {
            Nightfall.LOGGER.error("Fehler beim Zuruecksetzen von Modul {}", m.getName(), var3);
         }
      }
   }

   public static void tick() {
      class_310 mc = class_310.method_1551();
      if (mc.field_1724 != null && mc.field_1687 != null) {
         for (Module m : MODULES) {
            if (m.isEnabled()) {
               try {
                  m.onTick();
               } catch (Throwable var4) {
                  Nightfall.LOGGER.error("Fehler in Modul {} - Modul wird deaktiviert", m.getName(), var4);
                  errorCount++;
                  m.setEnabled(false);
               }
            }
         }
      }
   }

   public static void renderHud(class_332 ctx, float tickDelta) {
      class_310 mc = class_310.method_1551();
      Projection.update(mc, tickDelta, ctx.method_51421(), ctx.method_51443());
      EspBase.drawn = 0;

      for (Module m : MODULES) {
         if (m.isEnabled()) {
            try {
               m.renderHud(ctx);
            } catch (Throwable var6) {
               Nightfall.LOGGER.error("Render-Fehler in Modul {} - Modul wird deaktiviert", m.getName(), var6);
               errorCount++;
               m.setEnabled(false);
            }
         }
      }
   }
}
