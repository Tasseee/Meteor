package dev.nightfall.util;

import dev.nightfall.Nightfall;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_310;
import net.minecraft.class_642;

public final class Storage {
   private Storage() {
   }

   public static Path dir() {
      Path d = FabricLoader.getInstance().getConfigDir().resolve("nightfall");

      try {
         Files.createDirectories(d);
      } catch (IOException var2) {
         Nightfall.LOGGER.error("Ordner nicht erstellbar: {}", d, var2);
      }

      return d;
   }

   public static String server() {
      class_310 mc = class_310.method_1551();
      class_642 si = mc.method_1558();
      String s = si != null ? si.field_3761 : (mc.method_1542() ? "singleplayer" : "unknown");
      return s.replaceAll("[^A-Za-z0-9._-]", "_");
   }

   public static String dim() {
      class_310 mc = class_310.method_1551();
      return mc.field_1687 == null ? "none" : mc.field_1687.method_27983().method_29177().method_12832();
   }
}
