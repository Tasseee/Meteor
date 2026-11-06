package dev.nightfall.ai;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure ranking logic for read-only material availability plans.
 */
final class AIMaterialPlanner {
    record Candidate(String itemId, String displayName, int available) {
        Candidate {
            if (itemId == null || itemId.isBlank())
                throw new IllegalArgumentException("Material-ID darf nicht leer sein.");
            if (displayName == null || displayName.isBlank())
                throw new IllegalArgumentException("Materialname darf nicht leer sein.");
            if (available < 0)
                throw new IllegalArgumentException("Materialbestand darf nicht negativ sein.");
        }
    }

    record Option(Candidate candidate, int required) {
        Option {
            Objects.requireNonNull(candidate, "candidate");
            if (required < 1)
                throw new IllegalArgumentException("Benötigte Materialmenge muss mindestens eins sein.");
        }

        int missing() {
            return Math.max(0, required - candidate.available());
        }

        int surplus() {
            return Math.max(0, candidate.available() - required);
        }

        boolean sufficient() {
            return missing() == 0;
        }
    }

    record CraftableCandidate(String itemId, String displayName, int owned, int missing,
                              int recipeSteps, int recipeBatches, boolean needsCraftingTable,
                              String recipeSummary) {
        CraftableCandidate {
            if (itemId == null || itemId.isBlank())
                throw new IllegalArgumentException("Material-ID darf nicht leer sein.");
            if (displayName == null || displayName.isBlank())
                throw new IllegalArgumentException("Materialname darf nicht leer sein.");
            if (owned < 0 || missing < 1 || recipeSteps < 1 || recipeBatches < 1)
                throw new IllegalArgumentException("Ungültige Werte für eine Crafting-Alternative.");
            if (recipeSummary == null || recipeSummary.isBlank())
                throw new IllegalArgumentException("Rezeptübersicht darf nicht leer sein.");
        }
    }

    record IncompleteCraftableCandidate(String itemId, String displayName, int owned, int missing,
                                         int missingIngredientUnits, int recipeSteps, int recipeBatches,
                                         boolean needsCraftingTable, String missingIngredients,
                                         String recipeSummary) {
        IncompleteCraftableCandidate {
            if (itemId == null || itemId.isBlank())
                throw new IllegalArgumentException("Material-ID darf nicht leer sein.");
            if (displayName == null || displayName.isBlank())
                throw new IllegalArgumentException("Materialname darf nicht leer sein.");
            if (owned < 0 || missing < 1 || missingIngredientUnits < 1
                    || recipeSteps < 1 || recipeBatches < 1)
                throw new IllegalArgumentException("Ungültige Werte für eine unvollständige Crafting-Kette.");
            if (missingIngredients == null || missingIngredients.isBlank())
                throw new IllegalArgumentException("Fehlende Zutaten müssen angegeben werden.");
            if (recipeSummary == null || recipeSummary.isBlank())
                throw new IllegalArgumentException("Rezeptübersicht darf nicht leer sein.");
        }
    }

    private AIMaterialPlanner() {}

    static List<IncompleteCraftableCandidate> rankIncomplete(List<IncompleteCraftableCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        return candidates.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(IncompleteCraftableCandidate::missingIngredientUnits)
                        .thenComparingInt(IncompleteCraftableCandidate::recipeBatches)
                        .thenComparingInt(IncompleteCraftableCandidate::recipeSteps)
                        .thenComparing(IncompleteCraftableCandidate::itemId))
                .toList();
    }

    static List<CraftableCandidate> rankCraftable(List<CraftableCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        return candidates.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(CraftableCandidate::recipeBatches)
                        .thenComparingInt(CraftableCandidate::recipeSteps)
                        .thenComparingInt(CraftableCandidate::missing)
                        .thenComparing(CraftableCandidate::needsCraftingTable)
                        .thenComparing(CraftableCandidate::itemId))
                .toList();
    }

    static List<Option> rank(List<Candidate> candidates, int required) {
        if (required < 1)
            throw new IllegalArgumentException("Benötigte Materialmenge muss mindestens eins sein.");
        if (candidates == null || candidates.isEmpty()) return List.of();

        return candidates.stream()
                .filter(Objects::nonNull)
                .map(candidate -> new Option(candidate, required))
                .sorted(Comparator
                        .comparing((Option option) -> !option.sufficient())
                        .thenComparingInt(option -> option.sufficient()
                                ? option.surplus() : option.missing())
                        .thenComparing(option -> option.candidate().itemId()))
                .toList();
    }
}
