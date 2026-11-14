package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraft.class_243;
import net.minecraft.class_2535;
import net.minecraft.class_2596;
import net.minecraft.class_2824;
import net.minecraft.class_2828;

/** Delays only outgoing player movement packets; all other traffic stays untouched. */
public final class FakeLag extends Module {
   private static final Queue<class_2596<?>> QUEUE = new ArrayDeque<>();
   private static class_2535 connection;
   private static boolean bypass;
   private static long holdStart;
   private static double serverX;
   private static double serverY;
   private static double serverZ;
   private static float serverYaw;
   private static float serverPitch;
   private static boolean serverPositionInitialized;
   private static long ghostHoldUntil;
   private static int lastHurtTime;
   private static float lastHealth = Float.NaN;

   private final NumberSetting maxDelay = this.add(new NumberSetting("Max. Delay (ms)", 100.0, 50.0, 250.0, 25.0));
   private final NumberSetting maxPackets = this.add(new NumberSetting("Max. Pakete", 8.0, 4.0, 20.0, 1.0));
   private final BoolSetting flushOnAttack = this.add(new BoolSetting("Bei Angriff senden", true));
   private final BoolSetting flushOnDamage = this.add(new BoolSetting("Bei Treffer senden", true));
   private final BoolSetting flushOnScreen = this.add(new BoolSetting("Bei GUI senden", true));

   public FakeLag() {
      super("FakeLag", "Verzoegert ausgehende Bewegungspakete.", Category.COMBAT);
   }

   /**
    * Called for every outgoing movement packet. The position is only advanced when the
    * packet is actually allowed through (including packets replayed by flush()).
    */
   private static void recordSentMovement(class_2828 packet) {
      if (!serverPositionInitialized && mc.field_1724 != null) {
         serverX = mc.field_1724.method_23317();
         serverY = mc.field_1724.method_23318();
         serverZ = mc.field_1724.method_23321();
         serverYaw = mc.field_1724.method_36454();
         serverPitch = mc.field_1724.method_36455();
         serverPositionInitialized = true;
      }

      if (!serverPositionInitialized) return;
      serverX = packet.method_12269(serverX);
      serverY = packet.method_12268(serverY);
      serverZ = packet.method_12274(serverZ);
      serverYaw = packet.method_12271(serverYaw);
      serverPitch = packet.method_12270(serverPitch);
   }

   public static boolean hasPendingServerPosition() {
      return serverPositionInitialized && (!QUEUE.isEmpty() || System.currentTimeMillis() < ghostHoldUntil);
   }

   public static double serverX() {
      return serverX;
   }

   public static double serverY() {
      return serverY;
   }

   public static double serverZ() {
      return serverZ;
   }

   public static float serverYaw() {
      return serverYaw;
   }

   public static float serverPitch() {
      return serverPitch;
   }

   /** Flush queued movement immediately before the client performs an entity attack. */
   public static void beforeAttack() {
      FakeLag module;
      try {
         module = dev.nightfall.module.ModuleManager.get(FakeLag.class);
      } catch (IllegalStateException ignored) {
         return;
      }
      if (!module.isEnabled() || !module.flushOnAttack.get() || QUEUE.isEmpty()) return;
      flush();
      ghostHoldUntil = System.currentTimeMillis() + 400L;
   }

   public static void intercept(class_2535 conn, class_2596<?> packet, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
      if (bypass) {
         if (packet instanceof class_2828 movement) recordSentMovement(movement);
         return;
      }

      FakeLag module;
      try {
         module = dev.nightfall.module.ModuleManager.get(FakeLag.class);
      } catch (IllegalStateException ignored) {
         if (packet instanceof class_2828 movement) recordSentMovement(movement);
         return;
      }

      if (!module.isEnabled()) {
         if (packet instanceof class_2828 movement) recordSentMovement(movement);
         return;
      }

      if (packet instanceof class_2824) {
         if (module.flushOnAttack.get() && !QUEUE.isEmpty()) {
            flush();
            // Keep the ghost visible briefly at the position just sent before the hit.
            ghostHoldUntil = System.currentTimeMillis() + 400L;
         }
         return;
      }
      if (!(packet instanceof class_2828 movement)) return;

      if (QUEUE.isEmpty()) holdStart = System.currentTimeMillis();
      connection = conn;
      QUEUE.add(packet);
      ci.cancel();

      if (QUEUE.size() >= module.maxPackets.getInt()) flush();
   }

   public static void flush() {
      if (connection == null) {
         QUEUE.clear();
         holdStart = 0L;
         return;
      }
      boolean old = bypass;
      bypass = true;
      try {
         class_2596<?> packet;
         while ((packet = QUEUE.poll()) != null) connection.method_10752(packet, null);
      } finally {
         bypass = old;
         if (QUEUE.isEmpty()) holdStart = 0L;
      }
   }

   @Override
   protected void onEnable() {
      lastHurtTime = mc.field_1724 != null ? mc.field_1724.field_6235 : 0;
      lastHealth = mc.field_1724 != null ? mc.field_1724.method_6032() : Float.NaN;
      if (!serverPositionInitialized && mc.field_1724 != null) {
         serverX = mc.field_1724.method_23317();
         serverY = mc.field_1724.method_23318();
         serverZ = mc.field_1724.method_23321();
         serverYaw = mc.field_1724.method_36454();
         serverPitch = mc.field_1724.method_36455();
         serverPositionInitialized = true;
      }
   }

   @Override
   protected void onDisable() {
      flush();
   }

   @Override
   public void onWorldLeave() {
      flush();
      QUEUE.clear();
      connection = null;
      holdStart = 0L;
      serverPositionInitialized = false;
      ghostHoldUntil = 0L;
      lastHurtTime = 0;
      lastHealth = Float.NaN;
      serverX = serverY = serverZ = 0.0;
      serverYaw = serverPitch = 0.0F;
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null || (flushOnScreen.get() && mc.field_1755 != null)) {
         flush();
         lastHurtTime = 0;
         lastHealth = Float.NaN;
         return;
      }

      // hurtTime is updated when the client receives a confirmed damage event.
      // Flush once when a new hurt animation starts, not on every tick while hurt.
      int hurtTime = mc.field_1724.field_6235;
      float health = mc.field_1724.method_6032();
      boolean hurtAnimationStarted = hurtTime > lastHurtTime;
      boolean healthDropped = !Float.isNaN(lastHealth) && health < lastHealth;
      if (flushOnDamage.get() && (hurtAnimationStarted || healthDropped) && !QUEUE.isEmpty()) {
         flush();
         ghostHoldUntil = System.currentTimeMillis() + 400L;
      }
      lastHurtTime = hurtTime;
      lastHealth = health;

      if (!QUEUE.isEmpty() && System.currentTimeMillis() - holdStart >= maxDelay.getInt()) flush();
   }

   @Override
   public String getDescription() {
      return "Verzoegert ausgehende Bewegungspakete.";
   }

   @Override
   public String toString() {
      return super.toString();
   }
}
