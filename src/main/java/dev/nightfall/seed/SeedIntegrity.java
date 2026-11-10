package dev.nightfall.seed;

import kaptainwutax.seedcrackerX.SeedCracker;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Replays the evidence already collected by SeedCrackerX before Nightfall
 * accepts a proposed seed. Reflection keeps SeedCrackerX's bundled libraries
 * out of Nightfall's compile-time dependency graph.
 */
public final class SeedIntegrity {
    public enum Level {
        HIGH("HOCH"), MEDIUM("MITTEL"), LOW("NIEDRIG"),
        REJECTED("WIDERSPRUCH"), INSUFFICIENT("ZU WENIG DATEN");

        private final String label;
        Level(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record Report(Level level, int structures, int decorators, int biomes,
                         int pillars, int verified, String reason) {
        public int independentSources() {
            return (structures > 0 ? 1 : 0) + (decorators > 0 ? 1 : 0)
                    + (biomes > 0 ? 1 : 0) + (pillars > 0 ? 1 : 0);
        }

        public boolean accepted() {
            return level != Level.REJECTED && level != Level.INSUFFICIENT;
        }

        public String summary() {
            return level.label() + " · " + independentSources() + "/4 Quellen";
        }
    }

    public Report verify(long worldSeed) {
        try {
            Object cracker = SeedCracker.get();
            if (cracker == null) return empty(Level.INSUFFICIENT, "SeedCrackerX nicht bereit");

            Object storage = cracker.getClass().getMethod("getDataStorage").invoke(cracker);
            Object scheduled = readField(storage, "baseSeedData");
            Object baseSet = scheduled.getClass().getMethod("getBaseSet").invoke(scheduled);

            int structures = 0, decorators = 0, biomes = 0, pillars = 0, verified = 0;
            if (baseSet instanceof Iterable<?> entries) {
                for (Object entry : entries) {
                    Object data = readField(entry, "data");
                    Object feature = readField(data, "feature");
                    String featureName = feature.getClass().getName();

                    // Match SeedCrackerX's own candidate-verification rules.
                    if (featureName.contains("PillagerOutpost")) continue;

                    boolean decorator = featureName.contains(".decorator.")
                            && !isPre118Feature(feature);
                    Method test = findMethod(data.getClass(), "testStart", 2);
                    Object random = createRandom(test.getParameterTypes()[1]);
                    boolean matches = (Boolean) test.invoke(data, worldSeed, random);

                    if (decorator) decorators++;
                    else structures++;
                    if (!matches) {
                        return new Report(Level.REJECTED, structures, decorators, biomes,
                                pillars, verified, "Struktur-/Dekorationsdaten widersprechen dem Seed");
                    }
                    verified++;
                }
            }

            Object configClass = Class.forName("kaptainwutax.seedcrackerX.config.Config");
            Object config = configClass.getMethod("get").invoke(null);
            Object version = config.getClass().getMethod("getVersion").invoke(config);
            Class<?> biomeSourceClass = Class.forName("com.seedfinding.mcbiome.source.OverworldBiomeSource");
            Object biomeSource = null;
            for (Constructor<?> constructor : biomeSourceClass.getConstructors()) {
                if (constructor.getParameterCount() == 2
                        && constructor.getParameterTypes()[0].isInstance(version)
                        && (constructor.getParameterTypes()[1] == long.class
                        || constructor.getParameterTypes()[1] == Long.class)) {
                    biomeSource = constructor.newInstance(version, worldSeed);
                    break;
                }
            }

            Object biomeScheduled = readField(storage, "biomeSeedData");
            Object biomeSet = biomeScheduled.getClass().getMethod("getBaseSet").invoke(biomeScheduled);
            if (biomeSet instanceof Iterable<?> biomeEntries && biomeSource != null) {
                for (Object entry : biomeEntries) {
                    Object biomeData = readField(entry, "data");
                    Method test = findCompatibleMethod(biomeData.getClass(), "test", biomeSource.getClass());
                    if (test == null) continue;
                    biomes++;
                    if (!(Boolean) test.invoke(biomeData, biomeSource)) {
                        return new Report(Level.REJECTED, structures, decorators, biomes,
                                pillars, verified, "Biome-Daten widersprechen dem Seed");
                    }
                    verified++;
                }
            }

            Object pillarData = readField(storage, "pillarData");
            if (pillarData != null) {
                pillars = 1;
                Method test = findMethod(pillarData.getClass(), "test", 1);
                if (!(Boolean) test.invoke(pillarData, (int) worldSeed)) {
                    return new Report(Level.REJECTED, structures, decorators, biomes,
                            pillars, verified, "End-Säulendaten widersprechen dem Seed");
                }
                verified++;
            }

            int sources = (structures > 0 ? 1 : 0) + (decorators > 0 ? 1 : 0)
                    + (biomes > 0 ? 1 : 0) + (pillars > 0 ? 1 : 0);
            if (verified == 0) return empty(Level.INSUFFICIENT, "Keine gespeicherten Hinweise zum Gegenprüfen");
            if (sources >= 3) return new Report(Level.HIGH, structures, decorators, biomes,
                    pillars, verified, "Mehrere unabhängige Datenquellen stimmen überein");
            if (sources == 2) return new Report(Level.MEDIUM, structures, decorators, biomes,
                    pillars, verified, "Zwei unabhängige Datenquellen stimmen überein");
            return new Report(Level.LOW, structures, decorators, biomes,
                    pillars, verified, "Nur eine unabhängige Datenquelle verfügbar");
        } catch (Throwable error) {
            return empty(Level.LOW, "Prüfung unvollständig: " + error.getClass().getSimpleName());
        }
    }

    private static boolean isPre118Feature(Object feature) throws ReflectiveOperationException {
        Object version = feature.getClass().getMethod("getVersion").invoke(feature);
        Class<?> versionClass = Class.forName("com.seedfinding.mccore.version.MCVersion");
        Object v118 = versionClass.getField("v1_18").get(null);
        return (Boolean) versionClass.getMethod("isOlderThan", versionClass).invoke(version, v118);
    }

    private static Object createRandom(Class<?> randomClass) throws ReflectiveOperationException {
        for (Constructor<?> constructor : randomClass.getDeclaredConstructors()) {
            constructor.setAccessible(true);
            if (constructor.getParameterCount() == 0) return constructor.newInstance();
            if (constructor.getParameterCount() == 1) {
                Class<?> sourceClass = constructor.getParameterTypes()[0];
                for (Constructor<?> sourceConstructor : sourceClass.getDeclaredConstructors()) {
                    sourceConstructor.setAccessible(true);
                    if (sourceConstructor.getParameterCount() == 1
                            && (sourceConstructor.getParameterTypes()[0] == long.class
                            || sourceConstructor.getParameterTypes()[0] == Long.class)) {
                        Object source = sourceConstructor.newInstance(0L);
                        return constructor.newInstance(source);
                    }
                }
            }
        }
        throw new NoSuchMethodException("Kein unterstützbarer RNG-Konstruktor: " + randomClass.getName());
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount)
            throws NoSuchMethodException {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == parameterCount) return method;
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Class<?> argumentType) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(argumentType)) return method;
        }
        return null;
    }

    private static Object readField(Object owner, String name) throws ReflectiveOperationException {
        if (owner == null) return null;
        Class<?> type = owner.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getClass().getName() + "." + name);
    }

    private static Report empty(Level level, String reason) {
        return new Report(level, 0, 0, 0, 0, 0, reason);
    }
}
