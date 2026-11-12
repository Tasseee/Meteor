package dev.nightfall.seed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import kaptainwutax.seedcrackerX.SeedCracker;

public final class SeedIntegrity {
   private static volatile int rejectedIncoming;
   private static volatile String lastRejectedReason = "";

   public static boolean acceptIncoming(Object data, Object storage) {
      try {
         Object feature = readField(data, "feature");
         String featureName = feature.getClass().getName();
         if (!featureName.contains(".structure.")) {
            return true;
         } else {
            Integer spacing = readIntMethod(feature, "getSpacing");
            Integer separation = readIntMethod(feature, "getSeparation");
            if (spacing != null && separation != null && spacing > 1) {
               int x = readIntField(data, "chunkX");
               int z = readIntField(data, "chunkZ");
               Object scheduled = readField(storage, "baseSeedData");
               Object base = scheduled.getClass().getMethod("getBaseSet").invoke(scheduled);
               Object pending = readField(scheduled, "scheduledSet");
               if (!isSpatialConflict(base, feature.getClass(), x, z, spacing, separation)
                  && !isSpatialConflict(pending, feature.getClass(), x, z, spacing, separation)) {
                  return true;
               } else {
                  rejectedIncoming++;
                  lastRejectedReason = "Unplausibler Abstand bei " + feature.getClass().getSimpleName() + " an Chunk " + x + ", " + z;
                  return false;
               }
            } else {
               return true;
            }
         }
      } catch (Throwable var11) {
         return true;
      }
   }

   private static boolean isSpatialConflict(Object entries, Class<?> featureType, int x, int z, int spacing, int separation) throws ReflectiveOperationException {
      if (entries instanceof Iterable<?> iterable) {
         int regionX = Math.floorDiv(x, spacing);
         int regionZ = Math.floorDiv(z, spacing);

         for (Object entry : iterable) {
            Object oldData = readField(entry, "data");
            Object oldFeature = readField(oldData, "feature");
            if (oldFeature.getClass() == featureType) {
               int oldX = readIntField(oldData, "chunkX");
               int oldZ = readIntField(oldData, "chunkZ");
               if (Math.floorDiv(oldX, spacing) == regionX && Math.floorDiv(oldZ, spacing) == regionZ) {
                  return true;
               }

               if (Math.max(Math.abs((long)x - oldX), Math.abs((long)z - oldZ)) <= separation) {
                  return true;
               }
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static int rejectedIncomingCount() {
      return rejectedIncoming;
   }

   public static String lastRejectedReason() {
      return lastRejectedReason;
   }

   public static void resetIncomingStats() {
      rejectedIncoming = 0;
      lastRejectedReason = "";
   }

   private static Integer readIntMethod(Object owner, String name) {
      try {
         return owner.getClass().getMethod(name).invoke(owner) instanceof Number number ? number.intValue() : null;
      } catch (Throwable var4) {
         return null;
      }
   }

   private static int readIntField(Object owner, String name) throws ReflectiveOperationException {
      if (readField(owner, name) instanceof Number number) {
         return number.intValue();
      } else {
         throw new NoSuchFieldException(name);
      }
   }

   public SeedIntegrity.Report verify(long worldSeed) {
      try {
         Object cracker = SeedCracker.get();
         if (cracker == null) {
            return empty(SeedIntegrity.Level.INSUFFICIENT, "SeedCrackerX nicht bereit");
         } else {
            Object storage = cracker.getClass().getMethod("getDataStorage").invoke(cracker);
            Object scheduled = readField(storage, "baseSeedData");
            Object baseSet = scheduled.getClass().getMethod("getBaseSet").invoke(scheduled);
            int structures = 0;
            int decorators = 0;
            int biomes = 0;
            int pillars = 0;
            int verified = 0;
            if (baseSet instanceof Iterable) {
               for (Object entry : (Iterable)baseSet) {
                  Object data = readField(entry, "data");
                  Object feature = readField(data, "feature");
                  String featureName = feature.getClass().getName();
                  if (!featureName.contains("PillagerOutpost")) {
                     boolean decorator = featureName.contains(".decorator.") && !isPre118Feature(feature);
                     Method test = findMethod(data.getClass(), "testStart", 2);
                     Object random = createRandom(test.getParameterTypes()[1]);
                     boolean matches = (Boolean)test.invoke(data, worldSeed, random);
                     if (decorator) {
                        decorators++;
                     } else {
                        structures++;
                     }

                     if (!matches) {
                        return new SeedIntegrity.Report(
                           SeedIntegrity.Level.REJECTED, structures, decorators, biomes, pillars, verified, "Struktur-/Dekorationsdaten widersprechen dem Seed"
                        );
                     }

                     verified++;
                  }
               }
            }

            Class<?> configClass = Class.forName("kaptainwutax.seedcrackerX.config.Config");
            Object config = configClass.getMethod("get").invoke(null);
            Object version = config.getClass().getMethod("getVersion").invoke(config);
            Class<?> biomeSourceClass = Class.forName("com.seedfinding.mcbiome.source.OverworldBiomeSource");
            Object biomeSource = null;

            for (Constructor<?> constructor : biomeSourceClass.getConstructors()) {
               if (constructor.getParameterCount() == 2
                  && constructor.getParameterTypes()[0].isInstance(version)
                  && (constructor.getParameterTypes()[1] == long.class || constructor.getParameterTypes()[1] == Long.class)) {
                  biomeSource = constructor.newInstance(version, worldSeed);
                  break;
               }
            }

            Object biomeScheduled = readField(storage, "biomeSeedData");
            if (biomeScheduled.getClass().getMethod("getBaseSet").invoke(biomeScheduled) instanceof Iterable<?> biomeEntries && biomeSource != null) {
               for (Object entryx : biomeEntries) {
                  Object biomeData = readField(entryx, "data");
                  Method testx = findCompatibleMethod(biomeData.getClass(), "test", biomeSource.getClass());
                  if (testx != null) {
                     biomes++;
                     if (!(Boolean)testx.invoke(biomeData, biomeSource)) {
                        return new SeedIntegrity.Report(
                           SeedIntegrity.Level.REJECTED, structures, decorators, biomes, pillars, verified, "Biome-Daten widersprechen dem Seed"
                        );
                     }

                     verified++;
                  }
               }
            }

            Object pillarData = readField(storage, "pillarData");
            if (pillarData != null) {
               pillars = 1;
               Method testx = findMethod(pillarData.getClass(), "test", 1);
               if (!(Boolean)testx.invoke(pillarData, (int)worldSeed)) {
                  return new SeedIntegrity.Report(
                     SeedIntegrity.Level.REJECTED, structures, decorators, biomes, pillars, verified, "End-Säulendaten widersprechen dem Seed"
                  );
               }

               verified++;
            }

            int sources = (structures > 0 ? 1 : 0) + (decorators > 0 ? 1 : 0) + (biomes > 0 ? 1 : 0) + (pillars > 0 ? 1 : 0);
            if (verified == 0) {
               return empty(SeedIntegrity.Level.INSUFFICIENT, "Keine gespeicherten Hinweise zum Gegenprüfen");
            } else if (sources >= 3) {
               return new SeedIntegrity.Report(
                  SeedIntegrity.Level.HIGH, structures, decorators, biomes, pillars, verified, "Mehrere unabhängige Datenquellen stimmen überein"
               );
            } else {
               return sources == 2
                  ? new SeedIntegrity.Report(
                     SeedIntegrity.Level.MEDIUM, structures, decorators, biomes, pillars, verified, "Zwei unabhängige Datenquellen stimmen überein"
                  )
                  : new SeedIntegrity.Report(
                     SeedIntegrity.Level.LOW, structures, decorators, biomes, pillars, verified, "Nur eine unabhängige Datenquelle verfügbar"
                  );
            }
         }
      } catch (Throwable var24) {
         return empty(SeedIntegrity.Level.LOW, "Prüfung unvollständig: " + var24.getClass().getSimpleName());
      }
   }

   private static boolean isPre118Feature(Object feature) throws ReflectiveOperationException {
      Object version = feature.getClass().getMethod("getVersion").invoke(feature);
      Class<?> versionClass = Class.forName("com.seedfinding.mccore.version.MCVersion");
      Object v118 = versionClass.getField("v1_18").get(null);
      return (Boolean)versionClass.getMethod("isOlderThan", versionClass).invoke(version, v118);
   }

   private static Object createRandom(Class<?> randomClass) throws ReflectiveOperationException {
      for (Constructor<?> constructor : randomClass.getDeclaredConstructors()) {
         constructor.setAccessible(true);
         if (constructor.getParameterCount() == 0) {
            return constructor.newInstance();
         }

         if (constructor.getParameterCount() == 1) {
            Class<?> sourceClass = constructor.getParameterTypes()[0];

            for (Constructor<?> sourceConstructor : sourceClass.getDeclaredConstructors()) {
               sourceConstructor.setAccessible(true);
               if (sourceConstructor.getParameterCount() == 1
                  && (sourceConstructor.getParameterTypes()[0] == long.class || sourceConstructor.getParameterTypes()[0] == Long.class)) {
                  Object source = sourceConstructor.newInstance(0L);
                  return constructor.newInstance(source);
               }
            }
         }
      }

      throw new NoSuchMethodException("Kein unterstützbarer RNG-Konstruktor: " + randomClass.getName());
   }

   private static Method findMethod(Class<?> type, String name, int parameterCount) throws NoSuchMethodException {
      for (Method method : type.getMethods()) {
         if (method.getName().equals(name) && method.getParameterCount() == parameterCount) {
            return method;
         }
      }

      throw new NoSuchMethodException(type.getName() + "." + name);
   }

   private static Method findCompatibleMethod(Class<?> type, String name, Class<?> argumentType) {
      for (Method method : type.getMethods()) {
         if (method.getName().equals(name) && method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(argumentType)) {
            return method;
         }
      }

      return null;
   }

   private static Object readField(Object owner, String name) throws ReflectiveOperationException {
      if (owner == null) {
         return null;
      } else {
         for (Class<?> type = owner.getClass(); type != null; type = type.getSuperclass()) {
            try {
               Field field = type.getDeclaredField(name);
               field.setAccessible(true);
               return field.get(owner);
            } catch (NoSuchFieldException var4) {
            }
         }

         throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
      }
   }

   private static SeedIntegrity.Report empty(SeedIntegrity.Level level, String reason) {
      return new SeedIntegrity.Report(level, 0, 0, 0, 0, 0, reason);
   }

   public static enum Level {
      HIGH("HOCH"),
      MEDIUM("MITTEL"),
      LOW("NIEDRIG"),
      REJECTED("WIDERSPRUCH"),
      INSUFFICIENT("ZU WENIG DATEN");

      private final String label;

      private Level(String label) {
         this.label = label;
      }

      public String label() {
         return this.label;
      }
   }

   public record Report(SeedIntegrity.Level level, int structures, int decorators, int biomes, int pillars, int verified, String reason) {
      public int independentSources() {
         return (this.structures > 0 ? 1 : 0) + (this.decorators > 0 ? 1 : 0) + (this.biomes > 0 ? 1 : 0) + (this.pillars > 0 ? 1 : 0);
      }

      public boolean accepted() {
         return this.level != SeedIntegrity.Level.REJECTED && this.level != SeedIntegrity.Level.INSUFFICIENT;
      }

      public String summary() {
         return this.level.label() + " · " + this.independentSources() + "/4 Quellen";
      }
   }
}
