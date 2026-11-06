package dev.nightfall.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure local survival-risk assessment based only on a bounded client snapshot.
 * It reports observable signals; it never starts or changes a player action.
 */
final class AILocalRiskAssessment {
    record Result(
            String level,
            List<String> immediateHazards,
            List<String> cautionSignals,
            long hostileMobsWithinEightBlocks) {

        Result {
            immediateHazards = List.copyOf(immediateHazards);
            cautionSignals = List.copyOf(cautionSignals);
        }

        String summary() {
            StringBuilder summary = new StringBuilder("Lokale Gefahreneinschätzung: ")
                    .append(level).append(". ");

            if (!immediateHazards.isEmpty()) {
                summary.append("Akute Hinweise: ")
                        .append(String.join("; ", immediateHazards))
                        .append(". ");
            }
            if (!cautionSignals.isEmpty()) {
                summary.append("Vorsichtshinweise: ")
                        .append(String.join("; ", cautionSignals))
                        .append(". ");
            }
            if (immediateHazards.isEmpty() && cautionSignals.isEmpty()) {
                summary.append("Keine der erfassten Prüfungen meldet aktuell eine akute Gefahr. ")
                        .append("Das ist keine Sicherheitsgarantie; nicht sichtbare oder nicht geladene ")
                        .append("Bereiche wurden nicht geprüft. ");
            }

            return summary.append("Erkannte feindliche Mobs im Umkreis von 8 Blöcken: ")
                    .append(hostileMobsWithinEightBlocks)
                    .append(".")
                    .toString();
        }
    }

    private AILocalRiskAssessment() {
    }

    static Result assess(
            boolean inLava,
            boolean onFire,
            double nearestHostileDistance,
            float health,
            float maxHealth,
            int hungerLevel,
            boolean submergedInWater,
            boolean nightTime,
            int lightLevel,
            long hostileMobsWithinEightBlocks) {

        List<String> immediateHazards = new ArrayList<>();
        List<String> cautionSignals = new ArrayList<>();

        if (inLava) {
            immediateHazards.add("Spieler befindet sich in Lava");
        }
        if (onFire) {
            immediateHazards.add("Spieler brennt");
        }
        if (Double.isFinite(nearestHostileDistance) && nearestHostileDistance >= 0.0
                && nearestHostileDistance <= 4.0) {
            immediateHazards.add("feindlicher Mob in unmittelbarer Nähe ("
                    + formatDistance(nearestHostileDistance) + " Blöcke)");
        } else if (Double.isFinite(nearestHostileDistance) && nearestHostileDistance > 4.0
                && nearestHostileDistance <= 8.0) {
            cautionSignals.add("feindlicher Mob in der Nähe ("
                    + formatDistance(nearestHostileDistance) + " Blöcke)");
        }

        if (health <= 6.0F) {
            cautionSignals.add("Gesundheit niedrig ("
                    + String.format(Locale.ROOT, "%.1f/%.1f", health, maxHealth) + ")");
        }
        if (hungerLevel <= 6) {
            cautionSignals.add("Hunger niedrig (" + hungerLevel + "/20)");
        }
        if (submergedInWater) {
            cautionSignals.add("Spieler ist unter Wasser; verbleibende Luft wird hier nicht bewertet");
        }
        if (nightTime && lightLevel <= 7) {
            cautionSignals.add("Nacht bei niedriger Lichtstufe (" + lightLevel + "/15)");
        }

        String level = !immediateHazards.isEmpty()
                ? "HOCH"
                : !cautionSignals.isEmpty()
                ? "ERHÖHT"
                : "KEINE AKUTE GEFAHR IN DEN ERFASSTEN DATEN ERKANNT";

        return new Result(level, immediateHazards, cautionSignals,
                Math.max(0L, hostileMobsWithinEightBlocks));
    }

    private static String formatDistance(double distance) {
        return String.format(Locale.ROOT, "%.1f", distance);
    }
}
