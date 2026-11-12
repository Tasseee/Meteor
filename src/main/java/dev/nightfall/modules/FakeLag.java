package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import java.util.ArrayDeque;
import java.util.Queue;
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

   private final NumberSetting maxDelay = this.add(new NumberSetting("Max. Delay (ms)", 350.0, 50.0, 1000.0, 25.0));
   private final NumberSetting maxPackets = this.add(new NumberSetting("Max. Pakete", 25.0, 5.0, 60.0, 1.0));
   private final BoolSetting flushOnAttack = this.add(new BoolSetting("Bei Angriff senden", true));
   private final BoolSetting flushOnScreen = this.add(new BoolSetting("Bei GUI senden", true));

   public FakeLag() {
      super("FakeLag", "Verzoegert ausgehende Bewegungspakete.", Category.COMBAT);
   }

   public static void intercept(class_2535 conn, class_2596<?> packet, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
      if (bypass) return;
      FakeLag module;
      try {
         module = dev.nightfall.module.ModuleManager.get(FakeLag.class);
      } catch (IllegalStateException ignored) {
         return;
      }
      if (!module.isEnabled()) return;

      if (packet instanceof class_2824) {
         if (module.flushOnAttack.get()) flush();
         return;
      }
      if (!(packet instanceof class_2828)) return;

      if (QUEUE.isEmpty()) holdStart = System.currentTimeMillis();
      connection = conn;
      QUEUE.add(packet);
      ci.cancel();

      if (QUEUE.size() >= module.maxPackets.getInt()) flush();
   }

   public static void flush() {
      if (connection == null) {
         QUEUE.clear();
         return;
      }
      boolean old = bypass;
      bypass = true;
      try {
         class_2596<?> packet;
         while ((packet = QUEUE.poll()) != null) connection.method_10752(packet);
      } finally {
         bypass = old;
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
   }

   @Override
   public void onTick() {
      if (mc.field_1724 == null || mc.field_1687 == null || (flushOnScreen.get() && mc.field_1755 != null)) {
         flush();
         return;
      }
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
