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

    private AIMaterialPlanner() {}

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
