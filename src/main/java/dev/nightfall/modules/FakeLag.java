package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.Deque;
import net.minecraft.class_2535;
import net.minecraft.class_2596;
import net.minecraft.class_2828;
import net.minecraft.class_9836;

/** Delays only outgoing player movement packets; all other traffic stays untouched. */
public final class FakeLag extends Module {
   private static final Queue<class_2596<?>> QUEUE = new ArrayDeque<>();
   private static final Deque<GhostPoint> GHOST_TRAIL = new ArrayDeque<>();
   private static class_2535 connection;
   private static boolean bypass;
   private static long holdStartNanos;
   private static double serverX;
   private static double serverY;
   private static double serverZ;
   private static float serverYaw;
   private static float serverPitch;
   private static boolean serverPositionInitialized;
   private static double queuedX;
   private static double queuedY;
   private static double queuedZ;
   private static float queuedYaw;
   private static float queuedPitch;

   private final NumberSetting maxDelay = this.add(new NumberSetting("Max. Delay (ms)", 350.0, 50.0, 1000.0, 25.0));
   private final NumberSetting maxPackets = this.add(new NumberSetting("Max. Pakete", 25.0, 5.0, 60.0, 1.0));
   private final BoolSetting flushOnScreen = this.add(new BoolSetting("Bei GUI senden", true));
   private final BoolSetting showGhost = this.add(new BoolSetting("Ghost anzeigen", true));
   private final ModeSetting ghostMode = this.add(new ModeSetting("Ghost-Modus", "Model", "Model", "Box", "Wireframe"));
   private final ColorSetting ghostColor = this.add(new ColorSetting("Ghost-Farbe", 0x33D1FF));
   private final NumberSetting ghostOpacity = this.add(new NumberSetting("Ghost-Transparenz", 0.78, 0.10, 1.00, 0.05));
   private final BoolSetting showGhostOutline = this.add(new BoolSetting("Ghost-Rahmen", true));
   private final BoolSetting showGhostLink = this.add(new BoolSetting("Verbindungslinie", true));
   private final BoolSetting showGhostTrail = this.add(new BoolSetting("Positionsverlauf", true));
   private final NumberSetting trailLength = this.add(new NumberSetting("Trail-Punkte", 24.0, 4.0, 64.0, 1.0));

   public FakeLag() {
      super("FakeLag", "Verzoegert Bewegungspakete mit konfigurierbarer Server-Ghost-Ansicht.", Category.COMBAT);
   }

   /** Updates the server-position tracker only when a packet is actually sent. */
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

   /** One immutable snapshot keeps rendering consistent with network-thread updates. */
   public static synchronized GhostSnapshot snapshot() {
      if (QUEUE.isEmpty() || !serverPositionInitialized) return null;
      long elapsedNanos = holdStartNanos > 0L ? Math.max(0L, System.nanoTime() - holdStartNanos) : 0L;
      return new GhostSnapshot(
         serverX, serverY, serverZ, serverYaw, serverPitch, QUEUE.size(),
         elapsedNanos / 1_000_000L, List.copyOf(GHOST_TRAIL)
      );
   }

   public boolean isGhostEnabled() { return this.isEnabled() && this.showGhost.get(); }
   public boolean isGhostOutlineEnabled() { return this.isGhostEnabled() && this.showGhostOutline.get(); }
   public boolean isGhostLinkEnabled() { return this.isGhostEnabled() && this.showGhostLink.get(); }
   public boolean isGhostTrailEnabled() { return this.isGhostEnabled() && this.showGhostTrail.get(); }
   public boolean isModelMode() { return this.ghostMode.is("Model"); }
   public boolean isBoxMode() { return this.ghostMode.is("Box"); }
   public boolean isWireframeMode() { return this.ghostMode.is("Wireframe"); }
   public String ghostModeName() { return this.ghostMode.get(); }
   public int ghostColor() { return this.ghostColor.rgb(); }
   public float ghostOpacity() { return this.ghostOpacity.get().floatValue(); }
   public int trailLength() { return this.trailLength.getInt(); }
   public int maxDelayMillis() { return this.maxDelay.getInt(); }

   public record GhostPoint(double x, double y, double z) { }

   public record GhostSnapshot(
      double x, double y, double z, float yaw, float pitch,
      int queuedPackets, long heldMillis, List<GhostPoint> trail
   ) { }

   public static synchronized double serverX() { return serverX; }
   public static synchronized double serverY() { return serverY; }
   public static synchronized double serverZ() { return serverZ; }
   public static synchronized float serverYaw() { return serverYaw; }
   public static synchronized float serverPitch() { return serverPitch; }

   public static synchronized void intercept(
      class_2535 conn, class_2596<?> packet,
      org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci
   ) {
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

      // Allow the end-of-tick marker through without draining delayed movement.
      if (packet instanceof class_9836) return;

      // Preserve packet ordering: movement must be sent before subsequent actions.
      if (!(packet instanceof class_2828 movement)) {
         flush();
         return;
      }
      if (connection != null && connection != conn) flush();

      if (!serverPositionInitialized && mc.field_1724 != null) {
         serverX = mc.field_1724.method_23317();
         serverY = mc.field_1724.method_23318();
         serverZ = mc.field_1724.method_23321();
         serverYaw = mc.field_1724.method_36454();
         serverPitch = mc.field_1724.method_36455();
         serverPositionInitialized = true;
      }

      if (QUEUE.isEmpty()) {
         holdStartNanos = System.nanoTime();
         queuedX = serverX;
         queuedY = serverY;
         queuedZ = serverZ;
         queuedYaw = serverYaw;
         queuedPitch = serverPitch;
         GHOST_TRAIL.clear();
         GHOST_TRAIL.addLast(new GhostPoint(serverX, serverY, serverZ));
      }

      connection = conn;
      QUEUE.add(packet);
      queuedX = movement.method_12269(queuedX);
      queuedY = movement.method_12268(queuedY);
      queuedZ = movement.method_12274(queuedZ);
      queuedYaw = movement.method_12271(queuedYaw);
      queuedPitch = movement.method_12270(queuedPitch);
      GHOST_TRAIL.addLast(new GhostPoint(queuedX, queuedY, queuedZ));
      while (GHOST_TRAIL.size() > module.trailLength()) GHOST_TRAIL.removeFirst();
      ci.cancel();

      if (QUEUE.size() >= module.maxPackets.getInt()) flush();
   }

   public static synchronized void flush() {
      if (connection == null) {
         QUEUE.clear();
         GHOST_TRAIL.clear();
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
            GHOST_TRAIL.clear();
            queuedX = serverX;
            queuedY = serverY;
            queuedZ = serverZ;
            queuedYaw = serverYaw;
            queuedPitch = serverPitch;
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
   protected void onDisable() { flush(); }

   @Override
   public void onWorldLeave() {
      synchronized (FakeLag.class) {
         flush();
         QUEUE.clear();
         GHOST_TRAIL.clear();
         connection = null;
         holdStartNanos = 0L;
         serverPositionInitialized = false;
         serverX = serverY = serverZ = 0.0;
         serverYaw = serverPitch = 0.0F;
         queuedX = queuedY = queuedZ = 0.0;
         queuedYaw = queuedPitch = 0.0F;
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
      return "Verzoegert Bewegungspakete mit konfigurierbarer Server-Ghost-Ansicht.";
   }

   @Override
   public String toString() { return super.toString(); }
}
