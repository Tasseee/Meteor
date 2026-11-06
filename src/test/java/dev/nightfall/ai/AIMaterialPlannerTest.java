package dev.nightfall.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AIMaterialPlannerTest {
    @Test
    void exactEnoughMaterialRanksBeforeLargeSurplus() {
        List<AIMaterialPlanner.Option> ranked = AIMaterialPlanner.rank(List.of(
                new AIMaterialPlanner.Candidate("minecraft:stone", "Stein", 130),
                new AIMaterialPlanner.Candidate("minecraft:oak_planks", "Eichenbretter", 54),
                new AIMaterialPlanner.Candidate("minecraft:birch_planks", "Birkenbretter", 53)
        ), 54);

        assertEquals("minecraft:oak_planks", ranked.get(0).candidate().itemId());
        assertTrue(ranked.get(0).sufficient());
        assertEquals(0, ranked.get(0).surplus());
        assertEquals("minecraft:stone", ranked.get(1).candidate().itemId());
        assertEquals("minecraft:birch_planks", ranked.get(2).candidate().itemId());
        assertEquals(1, ranked.get(2).missing());
    }

    @Test
    void shortageCandidatesRankBySmallestMissingAmount() {
        List<AIMaterialPlanner.Option> ranked = AIMaterialPlanner.rank(List.of(
                new AIMaterialPlanner.Candidate("minecraft:cobblestone", "Bruchstein", 20),
                new AIMaterialPlanner.Candidate("minecraft:stone", "Stein", 48),
                new AIMaterialPlanner.Candidate("minecraft:andesite", "Andesit", 48)
        ), 74);

        assertEquals("minecraft:andesite", ranked.get(0).candidate().itemId());
        assertEquals(26, ranked.get(0).missing());
        assertFalse(ranked.get(0).sufficient());
        assertEquals("minecraft:stone", ranked.get(1).candidate().itemId());
        assertEquals("minecraft:cobblestone", ranked.get(2).candidate().itemId());
    }

    @Test
    void exactMaterialIdProvidesStableTieBreaking() {
        List<AIMaterialPlanner.Option> ranked = AIMaterialPlanner.rank(List.of(
                new AIMaterialPlanner.Candidate("minecraft:stone", "Stein", 64),
                new AIMaterialPlanner.Candidate("minecraft:oak_planks", "Eichenbretter", 64)
        ), 54);

        assertEquals("minecraft:oak_planks", ranked.get(0).candidate().itemId());
    }

    @Test
    void craftableAlternativesPreferFewerRecipeBatchesThenSteps() {
        List<AIMaterialPlanner.CraftableCandidate> ranked = AIMaterialPlanner.rankCraftable(List.of(
                new AIMaterialPlanner.CraftableCandidate("minecraft:stone_bricks", "Steinziegel",
                        0, 54, 3, 18, true, "Kette A"),
                new AIMaterialPlanner.CraftableCandidate("minecraft:oak_planks", "Eichenbretter",
                        12, 42, 2, 11, false, "Kette B"),
                new AIMaterialPlanner.CraftableCandidate("minecraft:birch_planks", "Birkenbretter",
                        0, 54, 1, 11, false, "Kette C")
        ));

        assertEquals("minecraft:birch_planks", ranked.get(0).itemId());
        assertEquals("minecraft:oak_planks", ranked.get(1).itemId());
        assertEquals("minecraft:stone_bricks", ranked.get(2).itemId());
    }

    @Test
    void craftableAlternativesUseStableItemIdTieBreaking() {
        List<AIMaterialPlanner.CraftableCandidate> ranked = AIMaterialPlanner.rankCraftable(List.of(
                new AIMaterialPlanner.CraftableCandidate("minecraft:stone", "Stein", 0, 54, 2, 8, false, "Kette"),
                new AIMaterialPlanner.CraftableCandidate("minecraft:andesite", "Andesit", 0, 54, 2, 8, true, "Kette")
        ));

        assertEquals("minecraft:andesite", ranked.get(0).itemId());
    }

    @Test
    void craftableAlternativesRejectInvalidCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> new AIMaterialPlanner.CraftableCandidate("minecraft:stone", "Stein",
                        0, 0, 1, 1, false, "Kette"));
    }

    @Test
    void emptyCandidateListProducesEmptyPlan() {
        assertTrue(AIMaterialPlanner.rank(List.of(), 54).isEmpty());
    }

    @Test
    void invalidRequiredAmountIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> AIMaterialPlanner.rank(List.of(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> new AIMaterialPlanner.Candidate("minecraft:stone", "Stein", -1));
    }
}
