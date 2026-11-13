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
import net.minecraft.class_9836;

/** Delays only outgoing player movement packets; all other traffic stays untouched. */
public final class FakeLag extends Module {
   private static final Queue<class_2596<?>> QUEUE = new ArrayDeque<>();
   private static class_2535 connection;
   private static boolean bypass;
   private static long holdStartNanos;
   private static double serverX;
   private static double serverY;
   private static double serverZ;
   private static float serverYaw;
   private static float serverPitch;
   private static boolean serverPositionInitialized;

   private final NumberSetting maxDelay = this.add(new NumberSetting("Max. Delay (ms)", 350.0, 50.0, 1000.0, 25.0));
   private final NumberSetting maxPackets = this.add(new NumberSetting("Max. Pakete", 25.0, 5.0, 60.0, 1.0));
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

   public static synchronized boolean hasPendingServerPosition() {
      return !QUEUE.isEmpty() && serverPositionInitialized;
   }

   public static synchronized double serverX() {
      return serverX;
   }

   public static synchronized double serverY() {
      return serverY;
   }

   public static synchronized double serverZ() {
      return serverZ;
   }

   public static synchronized float serverYaw() {
      return serverYaw;
   }

   public static synchronized float serverPitch() {
      return serverPitch;
   }

   public static synchronized void intercept(class_2535 conn, class_2596<?> packet, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
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

      // ClientTickEnd is sent once per client tick. Let it pass without draining the
      // movement queue, otherwise FakeLag would flush almost immediately every tick.
      if (packet instanceof class_9836) return;

      // Flush delayed movement before action/control packets (attacks, interactions,
      // slot changes, keep-alives, etc.) so those actions keep their expected ordering.
      if (!(packet instanceof class_2828 movement)) {
         flush();
         return;
      }

      // Flush the previous connection before starting the timer for the new one.
      // Otherwise flush() resets holdStartNanos and the next queue would appear overdue.
      if (connection != null && connection != conn) flush();
      if (QUEUE.isEmpty()) holdStartNanos = System.nanoTime();
      connection = conn;
      QUEUE.add(packet);
      ci.cancel();

      if (QUEUE.size() >= module.maxPackets.getInt()) flush();
   }

   public static synchronized void flush() {
      if (connection == null) {
         QUEUE.clear();
         holdStartNanos = 0L;
         return;
      }
      boolean old = bypass;
      bypass = true;
      try {
         class_2596<?> packet;
         while ((packet = QUEUE.poll()) != null) connection.method_10752(packet, null);
      } finally {
         bypass = old;
         if (QUEUE.isEmpty()) {
            holdStartNanos = 0L;
            connection = null;
         }
      }
   }

   @Override
   protected void onEnable() {
      synchronized (FakeLag.class) {
         if (!serverPositionInitialized && mc.field_1724 != null) {
            serverX = mc.field_1724.method_23317();
            serverY = mc.field_1724.method_23318();
            serverZ = mc.field_1724.method_23321();
            serverYaw = mc.field_1724.method_36454();
            serverPitch = mc.field_1724.method_36455();
            serverPositionInitialized = true;
         }
      }
   }

   @Override
   protected void onDisable() {
      flush();
   }

   @Override
   public void onWorldLeave() {
      synchronized (FakeLag.class) {
         flush();
         QUEUE.clear();
         connection = null;
         holdStartNanos = 0L;
         serverPositionInitialized = false;
         serverX = serverY = serverZ = 0.0;
         serverYaw = serverPitch = 0.0F;
      }
   }

   @Override
   public void onTick() {
      synchronized (FakeLag.class) {
         if (mc.field_1724 == null || mc.field_1687 == null || (flushOnScreen.get() && mc.field_1755 != null)) {
            flush();
            return;
         }
         if (!QUEUE.isEmpty()
            && System.nanoTime() - holdStartNanos >= (long)maxDelay.getInt() * 1_000_000L) {
            flush();
         }
      }
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
