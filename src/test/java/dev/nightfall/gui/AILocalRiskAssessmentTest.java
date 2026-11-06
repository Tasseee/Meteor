package dev.nightfall.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AILocalRiskAssessmentTest {
    @Test
    void lavaAndFireAreImmediateHazards() {
        AILocalRiskAssessment.Result result = AILocalRiskAssessment.assess(
                true, true, Double.POSITIVE_INFINITY,
                20.0F, 20.0F, 20, false, false, 15, 0);

        assertEquals("HOCH", result.level());
        assertEquals(2, result.immediateHazards().size());
        assertTrue(result.summary().contains("Lava"));
        assertTrue(result.summary().contains("brennt"));
    }

    @Test
    void veryCloseHostileMobRaisesRiskToHigh() {
        AILocalRiskAssessment.Result result = AILocalRiskAssessment.assess(
                false, false, 3.5,
                20.0F, 20.0F, 20, false, false, 15, 1);

        assertEquals("HOCH", result.level());
        assertTrue(result.immediateHazards().get(0).contains("3.5"));
        assertEquals(1, result.hostileMobsWithinEightBlocks());
    }

    @Test
    void lowHealthHungerAndNightDarknessProduceCaution() {
        AILocalRiskAssessment.Result result = AILocalRiskAssessment.assess(
                false, false, Double.POSITIVE_INFINITY,
                5.0F, 20.0F, 5, false, true, 4, 0);

        assertEquals("ERHÖHT", result.level());
        assertEquals(3, result.cautionSignals().size());
        assertTrue(result.summary().contains("Gesundheit niedrig"));
        assertTrue(result.summary().contains("Hunger niedrig"));
        assertTrue(result.summary().contains("Nacht bei niedriger Lichtstufe"));
    }

    @Test
    void underwaterAndNearbyHostileAreCautionSignals() {
        AILocalRiskAssessment.Result result = AILocalRiskAssessment.assess(
                false, false, 6.0,
                20.0F, 20.0F, 20, true, false, 12, 1);

        assertEquals("ERHÖHT", result.level());
        assertEquals(2, result.cautionSignals().size());
        assertFalse(result.cautionSignals().isEmpty());
        assertTrue(result.summary().contains("verbleibende Luft wird hier nicht bewertet"));
    }

    @Test
    void noObservedHazardsIsNeverDescribedAsGuaranteedSafe() {
        AILocalRiskAssessment.Result result = AILocalRiskAssessment.assess(
                false, false, Double.POSITIVE_INFINITY,
                20.0F, 20.0F, 20, false, false, 15, 0);

        assertEquals("KEINE AKUTE GEFAHR IN DEN ERFASSTEN DATEN ERKANNT", result.level());
        assertTrue(result.summary().contains("keine Sicherheitsgarantie"));
        assertTrue(result.summary().contains("nicht sichtbare oder nicht geladene Bereiche"));
    }
}
