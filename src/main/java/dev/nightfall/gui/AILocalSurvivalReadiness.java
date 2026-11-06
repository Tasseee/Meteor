package dev.nightfall.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure readiness check for a bounded, client-visible Minecraft inventory snapshot.
 * It produces suggestions only and never changes inventory or world state.
 */
final class AILocalSurvivalReadiness {
    record Snapshot(
            String dimension,
            int armorPieces,
            int armorPiecesAtOrBelowTwentyPercent,
            int lowestArmorDurabilityPercent,
            int mainHandDurabilityPercent,
            int foodUnits,
            int torchUnits,
            boolean hasShield,
            boolean hasGoldArmorPiece,
            boolean hasWaterBucket,
            boolean hasEnderPearls,
            int lightLevel) {
        Snapshot {
            if (dimension == null || dimension.isBlank()) {
                throw new IllegalArgumentException("Dimension must not be blank.");
            }
            if (armorPieces < 0 || armorPieces > 4
                    || armorPiecesAtOrBelowTwentyPercent < 0
                    || armorPiecesAtOrBelowTwentyPercent > armorPieces
                    || lowestArmorDurabilityPercent < -1
                    || lowestArmorDurabilityPercent > 100
                    || mainHandDurabilityPercent < -1
                    || mainHandDurabilityPercent > 100
                    || foodUnits < 0 || torchUnits < 0
                    || lightLevel < 0 || lightLevel > 15) {
                throw new IllegalArgumentException("Survival readiness values are outside their valid ranges.");
            }
        }
    }

    record Result(String level, List<String> findings, List<String> recommendations) {
        Result {
            findings = List.copyOf(findings);
            recommendations = List.copyOf(recommendations);
        }

        String summary() {
            StringBuilder summary = new StringBuilder("Lokaler Ausrüstungs- und Vorratscheck: ")
                    .append(level).append(". ");
            if (findings.isEmpty()) {
                summary.append("In den geprüften Kategorien wurde keine grobe Lücke erkannt. ");
            } else {
                summary.append("Beobachtungen: ")
                        .append(String.join("; ", findings)).append(". ");
            }
            if (!recommendations.isEmpty()) {
                summary.append("Mögliche Vorbereitung: ")
                        .append(String.join("; ", recommendations)).append(". ");
            }
            return summary.append("Nur eine clientseitige Momentaufnahme der ausgerüsteten Rüstung, ")
                    .append("der Haupthand und der 36 normalen Inventar-/Hotbar-Slots; ")
                    .append("keine Sicherheitsgarantie und keine Prüfung nicht geladener Bereiche.")
                    .toString();
        }
    }

    private AILocalSurvivalReadiness() {
    }

    static Result assess(Snapshot snapshot) {
        List<String> findings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        boolean majorGap = false;

        if (snapshot.armorPieces() == 0) {
            findings.add("keine Rüstung ausgerüstet");
            recommendations.add("vor gefährlichen Erkundungen Rüstung anlegen");
            majorGap = true;
        } else if (snapshot.armorPieces() < 4) {
            findings.add(snapshot.armorPieces() + " von 4 Rüstungsteilen ausgerüstet");
            recommendations.add("fehlende Rüstungsslots prüfen");
        }

        if (snapshot.armorPiecesAtOrBelowTwentyPercent() > 0) {
            findings.add(snapshot.armorPiecesAtOrBelowTwentyPercent()
                    + " Rüstungsteil(e) haben höchstens 20% Resthaltbarkeit");
            recommendations.add("stark beschädigte Rüstung reparieren oder ersetzen");
            majorGap = true;
        }

        if (snapshot.mainHandDurabilityPercent() >= 0
                && snapshot.mainHandDurabilityPercent() <= 15) {
            findings.add("Gegenstand in der Haupthand hat nur "
                    + snapshot.mainHandDurabilityPercent() + "% Resthaltbarkeit");
            recommendations.add("Haupthand-Gegenstand vor längerer Nutzung reparieren oder ersetzen");
            majorGap = true;
        }

        if (snapshot.foodUnits() == 0) {
            findings.add("keine essbaren Gegenstände im normalen Inventar erkannt");
            recommendations.add("Nahrung mitnehmen");
        } else if (snapshot.foodUnits() < 8) {
            findings.add("nur " + snapshot.foodUnits() + " essbare Gegenstände im normalen Inventar erkannt");
            recommendations.add("einen größeren Nahrungsvorrat einplanen");
        }

        if (snapshot.lightLevel() <= 7 && snapshot.torchUnits() == 0) {
            findings.add("niedrige Lichtstufe und keine Fackeln im normalen Inventar erkannt");
            recommendations.add("Lichtquellen mitnehmen, bevor dunkle Bereiche erkundet werden");
        }

        if ("minecraft:the_nether".equals(snapshot.dimension())
                && !snapshot.hasGoldArmorPiece()) {
            findings.add("kein Goldrüstungsteil ausgerüstet");
            recommendations.add("Piglins berücksichtigen und bei Bedarf ein Goldrüstungsteil anlegen");
        }

        if ("minecraft:the_end".equals(snapshot.dimension())
                && !snapshot.hasWaterBucket() && !snapshot.hasEnderPearls()) {
            findings.add("weder Wassereimer noch Enderperlen im normalen Inventar erkannt");
            recommendations.add("eine passende Positions- oder Rettungshilfe für die geplante Erkundung erwägen");
        }

        if (!snapshot.hasShield()) {
            findings.add("kein Schild in den normalen Inventar-/Hotbar-Slots erkannt");
            recommendations.add("einen Schild als zusätzliche Verteidigungsoption erwägen");
        }

        String level = majorGap
                ? "VORBEREITUNG VERBESSERN"
                : !findings.isEmpty()
                ? "EINIGE LÜCKEN ERKANNT"
                : "KEINE GROBE LÜCKE ERKANNT";
        return new Result(level, findings, recommendations);
    }
}
