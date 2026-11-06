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
