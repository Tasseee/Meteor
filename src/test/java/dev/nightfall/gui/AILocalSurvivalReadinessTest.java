package dev.nightfall.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AILocalSurvivalReadinessTest {
    private static AILocalSurvivalReadiness.Snapshot snapshot(
            String dimension,
            int armorPieces,
            int weakArmorPieces,
            int lowestArmorDurability,
            int mainHandDurability,
            int foodUnits,
            int torchUnits,
            boolean hasShield,
            boolean hasGoldArmor,
            boolean hasWaterBucket,
            boolean hasEnderPearls,
            int lightLevel) {
        return new AILocalSurvivalReadiness.Snapshot(
                dimension, armorPieces, weakArmorPieces, lowestArmorDurability,
                mainHandDurability, foodUnits, torchUnits, hasShield, hasGoldArmor,
                hasWaterBucket, hasEnderPearls, lightLevel);
    }

    @Test
    void completeBasicLoadoutHasNoMajorReadinessGaps() {
        AILocalSurvivalReadiness.Result result = AILocalSurvivalReadiness.assess(snapshot(
                "minecraft:overworld", 4, 0, 65, 72, 24, 32,
                true, false, true, true, 12));

        assertEquals("KEINE GROBE LÜCKE ERKANNT", result.level());
        assertTrue(result.findings().isEmpty());
        assertTrue(result.recommendations().isEmpty());
        assertTrue(result.summary().contains("keine Sicherheitsgarantie"));
    }

    @Test
    void missingArmorIsReportedAsMajorGap() {
        AILocalSurvivalReadiness.Result result = AILocalSurvivalReadiness.assess(snapshot(
                "minecraft:overworld", 0, 0, -1, -1, 16, 16,
                true, false, false, false, 12));

        assertEquals("VORBEREITUNG VERBESSERN", result.level());
        assertTrue(result.findings().contains("keine Rüstung ausgerüstet"));
        assertTrue(result.recommendations().contains("vor gefährlichen Erkundungen Rüstung anlegen"));
    }

    @Test
    void criticallyDamagedArmorAndHeldItemAreHighlighted() {
        AILocalSurvivalReadiness.Result result = AILocalSurvivalReadiness.assess(snapshot(
                "minecraft:overworld", 4, 2, 8, 10, 16, 16,
                true, false, false, false, 12));

        assertEquals("VORBEREITUNG VERBESSERN", result.level());
        assertTrue(result.findings().stream().anyMatch(value -> value.contains("höchstens 20%")));
        assertTrue(result.findings().stream().anyMatch(value -> value.contains("10% Resthaltbarkeit")));
    }

    @Test
    void lowFoodAndDarknessWithoutTorchesAreReported() {
        AILocalSurvivalReadiness.Result result = AILocalSurvivalReadiness.assess(snapshot(
                "minecraft:overworld", 4, 0, 80, 80, 3, 0,
                true, false, false, false, 4));

        assertEquals("EINIGE LÜCKEN ERKANNT", result.level());
        assertTrue(result.findings().stream().anyMatch(value -> value.contains("nur 3 essbare")));
        assertTrue(result.findings().stream().anyMatch(value -> value.contains("keine Fackeln")));
    }

    @Test
    void netherReadinessMentionsPiglinGoldArmorWithoutAssumingPiglinsAreNearby() {
        AILocalSurvivalReadiness.Result result = AILocalSurvivalReadiness.assess(snapshot(
                "minecraft:the_nether", 4, 0, 80, 80, 16, 16,
                true, false, false, false, 12));

        assertTrue(result.findings().contains("kein Goldrüstungsteil ausgerüstet"));
        assertTrue(result.recommendations().stream().anyMatch(value -> value.contains("Piglins")));
        assertTrue(result.summary().contains("clientseitige Momentaufnahme"));
    }

    @Test
    void endReadinessSuggestsARecoveryOptionOnlyWhenBothKnownOptionsAreMissing() {
        AILocalSurvivalReadiness.Result noOptions = AILocalSurvivalReadiness.assess(snapshot(
                "minecraft:the_end", 4, 0, 80, 80, 16, 16,
                true, false, false, false, 12));
        AILocalSurvivalReadiness.Result withPearls = AILocalSurvivalReadiness.assess(snapshot(
                "minecraft:the_end", 4, 0, 80, 80, 16, 16,
                true, false, false, true, 12));

        assertTrue(noOptions.findings().stream().anyMatch(value -> value.contains("weder Wassereimer noch Enderperlen")));
        assertTrue(withPearls.findings().stream().noneMatch(value -> value.contains("weder Wassereimer")));
    }

    @Test
    void partialArmorAndMissingShieldRemainAdvisory() {
        AILocalSurvivalReadiness.Result result = AILocalSurvivalReadiness.assess(snapshot(
                "minecraft:overworld", 2, 0, 50, 60, 12, 16,
                false, false, false, false, 12));

        assertEquals("EINIGE LÜCKEN ERKANNT", result.level());
        assertTrue(result.findings().stream().anyMatch(value -> value.contains("2 von 4")));
        assertTrue(result.findings().stream().anyMatch(value -> value.contains("kein Schild")));
    }

    @Test
    void invalidSnapshotValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                "minecraft:overworld", 5, 0, 100, 50, 10, 10,
                true, false, false, false, 12));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                "minecraft:overworld", 4, 0, 100, 101, 10, 10,
                true, false, false, false, 12));
    }
}
