package dev.nightfall.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfflineAgentOutcomeTest {
    @Test
    void recognizesExplicitlyVerifiedControllerSuccesses() {
        assertTrue(OfflineAgentOutcome.isSuccessfulControllerCompletion("Ziel erreicht."));
        assertTrue(OfflineAgentOutcome.isSuccessfulControllerCompletion("Bewegung abgeschlossen."));
        assertTrue(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Materialsammlung abgeschlossen: 16/16 Eichenstämme im Inventar."));
        assertTrue(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Schmelzen abgeschlossen: 8/8 Eisenbarren liegen jetzt im Inventar."));
        assertTrue(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Crafting vollständig abgeschlossen: 32/32 zusätzliche Bretter hergestellt."));
        assertTrue(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Inventarsortierung verifiziert: Alle 28 Tauschaktionen bestätigt."));
        assertTrue(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Essen abgeschlossen: Brot wurde verwendet."));
        assertTrue(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Bauabschnitt abgeschlossen: starter_shelter · 54 Blöcke."));
        assertTrue(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Kein feindlicher Mob mehr in der Nähe. Kampf beendet."));
    }

    @Test
    void partialAndSafetyStopResultsAreNeverSuccess() {
        assertFalse(OfflineAgentOutcome.isSuccessfulControllerCompletion(null));
        assertFalse(OfflineAgentOutcome.isSuccessfulControllerCompletion(""));
        assertFalse(OfflineAgentOutcome.isSuccessfulControllerCompletion("Navigation gestoppt: Wasser erkannt."));
        assertFalse(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Materialsammlung beendet: 3/16 Eichenstämme im Inventar."));
        assertFalse(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Schmelzen beendet: 5/8 Eisenbarren wurden im Inventar bestätigt."));
        assertFalse(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Crafting teilweise abgeschlossen: 4/32 Bretter hergestellt."));
        assertFalse(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Inventarsortierung nicht vollständig verifiziert: 4/8 Aktionen."));
        assertFalse(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Bau gestoppt: Der Server hat die Platzierung nicht bestätigt."));
        assertFalse(OfflineAgentOutcome.isSuccessfulControllerCompletion(
                "Kampf beendet · Sicherheitslimit erreicht."));
    }
}
