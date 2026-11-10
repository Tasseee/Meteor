package dev.nightfall.seed;

import com.seedfinding.mcbiome.source.OverworldBiomeSource;
import com.seedfinding.mccore.rand.ChunkRand;
import com.seedfinding.mcfeature.Feature;
import com.seedfinding.mccore.version.MCVersion;
import kaptainwutax.seedcrackerX.config.Config;
import kaptainwutax.seedcrackerX.cracker.BiomeData;
import kaptainwutax.seedcrackerX.cracker.PillarData;
import kaptainwutax.seedcrackerX.cracker.decorator.Decorator;
import kaptainwutax.seedcrackerX.cracker.storage.DataStorage;
import kaptainwutax.seedcrackerX.SeedCracker;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

import java.lang.reflect.Field;

/**
 * Independently replays SeedCrackerX's collected evidence against a proposed
 * world seed before Nightfall displays it as a valid result.
 */
public final class SeedIntegrity {
    public enum Level {
        HIGH("HOCH"),
        MEDIUM("MITTEL"),
        LOW("NIEDRIG"),
        REJECTED("WIDERSPRUCH"),
        INSUFFICIENT("ZU WENIG DATEN");

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
        var cracker = SeedCracker.get();
        if (cracker == null) {
            return empty(Level.INSUFFICIENT, "SeedCrackerX nicht bereit");
        }

        DataStorage storage = cracker.getDataStorage();
        int structures = 0;
        int decorators = 0;
        int biomes = 0;
        int pillars = 0;
        int verified = 0;

        try {
            ChunkRand structureRand = new ChunkRand();
            WorldgenRandom decoratorRand = new WorldgenRandom(new XoroshiroRandomSource(0L));

            for (DataStorage.Entry<Feature.Data<?>> entry : storage.baseSeedData) {
                Feature.Data<?> data = entry.data;
                boolean matches;
                if (data.feature instanceof Decorator) {
                    matches = ((Decorator.Data<?>) data).testStart(worldSeed, decoratorRand);
                    decorators++;
                } else {
                    matches = data.testStart(worldSeed, structureRand);
                    structures++;
                }
                if (!matches) {
                    return new Report(Level.REJECTED, structures, decorators, biomes,
                            pillars, verified, "Struktur-/Dekorationsdaten widersprechen dem Seed");
                }
                verified++;
            }

            MCVersion version = Config.get().getVersion();
            OverworldBiomeSource biomeSource = new OverworldBiomeSource(version, worldSeed);
            Iterable<?> biomeEntries = readIterable(storage, "biomeSeedData");
            if (biomeEntries != null) {
                for (Object value : biomeEntries) {
                    if (!(value instanceof DataStorage.Entry<?> entry)
                            || !(entry.data instanceof BiomeData biomeData)) continue;
                    biomes++;
                    if (!biomeData.test(biomeSource)) {
                        return new Report(Level.REJECTED, structures, decorators, biomes,
                                pillars, verified, "Biome-Daten widersprechen dem Seed");
                    }
                    verified++;
                }
            }

            Object pillarValue = readField(storage, "pillarData");
            if (pillarValue instanceof PillarData pillarData) {
                pillars = 1;
                if (!pillarData.test((int) worldSeed)) {
                    return new Report(Level.REJECTED, structures, decorators, biomes,
                            pillars, verified, "End-Säulendaten widersprechen dem Seed");
                }
                verified++;
            }
        } catch (Throwable error) {
            return new Report(Level.LOW, structures, decorators, biomes, pillars,
                    verified, "Prüfung unvollständig: " + error.getClass().getSimpleName());
        }

        int sources = (structures > 0 ? 1 : 0) + (decorators > 0 ? 1 : 0)
                + (biomes > 0 ? 1 : 0) + (pillars > 0 ? 1 : 0);
        if (verified == 0) {
            return empty(Level.INSUFFICIENT, "Keine gespeicherten Hinweise zum Gegenprüfen");
        }
        if (sources >= 3) {
            return new Report(Level.HIGH, structures, decorators, biomes, pillars,
                    verified, "Mehrere unabhängige Datenquellen stimmen überein");
        }
        if (sources == 2) {
            return new Report(Level.MEDIUM, structures, decorators, biomes, pillars,
                    verified, "Zwei unabhängige Datenquellen stimmen überein");
        }
        return new Report(Level.LOW, structures, decorators, biomes, pillars,
                verified, "Nur eine unabhängige Datenquelle verfügbar");
    }

    private static Object readField(Object owner, String name) throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static Iterable<?> readIterable(Object owner, String name) throws ReflectiveOperationException {
        Object value = readField(owner, name);
        return value instanceof Iterable<?> iterable ? iterable : null;
    }

    private static Report empty(Level level, String reason) {
        return new Report(level, 0, 0, 0, 0, 0, reason);
    }
}
