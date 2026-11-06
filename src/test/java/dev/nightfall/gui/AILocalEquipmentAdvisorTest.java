package dev.nightfall.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AILocalEquipmentAdvisorTest {
    private static AILocalEquipmentAdvisor.EnchantmentInfo enchantment(String id, String name, int level) {
        return new AILocalEquipmentAdvisor.EnchantmentInfo(id, name, level);
    }

    private static AILocalEquipmentAdvisor.GearInfo gear(
            String itemId,
            String name,
            String category,
            String location,
            boolean equipped,
            int durability,
            int armorPoints,
            float toughness,
            AILocalEquipmentAdvisor.EnchantmentInfo... enchantments) {
        return new AILocalEquipmentAdvisor.GearInfo(
                itemId, name, category, location, equipped,
                durability, armorPoints, toughness, List.of(enchantments));
    }

    @Test
    void summaryListsRealEnchantmentsAndDurability() {
        String summary = AILocalEquipmentAdvisor.summarize(List.of(
                gear("minecraft:diamond_pickaxe", "Diamantspitzhacke", "pickaxe",
                        "Hotbar-Slot 1", true, 82, 0, 0.0F,
                        enchantment("minecraft:efficiency", "Effizienz", 4),
                        enchantment("minecraft:unbreaking", "Haltbarkeit", 3))
        ), List.of());

        assertTrue(summary.contains("Diamantspitzhacke"));
        assertTrue(summary.contains("Haltbarkeit 82%"));
        assertTrue(summary.contains("Effizienz IV"));
        assertTrue(summary.contains("Haltbarkeit III"));
        assertTrue(summary.contains("Keine kompatiblen, höheren Verzauberungsbuch-Kandidaten"));
    }

    @Test
    void wornArmorCanBeComparedWithCarriedPieceByBaseStats() {
        String summary = AILocalEquipmentAdvisor.summarize(List.of(
                gear("minecraft:iron_helmet", "Eisenhelm", "armor:head",
                        "getragen", true, 90, 2, 0.0F),
                gear("minecraft:diamond_helmet", "Diamanthelm", "armor:head",
                        "Inventar-Slot 10", false, 74, 3, 2.0F)
        ), List.of());

        assertTrue(summary.contains("Diamanthelm im Inventar"));
        assertTrue(summary.contains("+1 Basis-Rüstungspunkt"));
        assertTrue(summary.contains("Verzauberungen und Haltbarkeit"));
    }

    @Test
    void equalProtectionWithHigherToughnessIsReportedSeparately() {
        String summary = AILocalEquipmentAdvisor.summarize(List.of(
                gear("minecraft:iron_chestplate", "Eisenbrustpanzer", "armor:chest",
                        "getragen", true, 90, 6, 0.0F),
                gear("minecraft:diamond_chestplate", "Diamantbrustpanzer", "armor:chest",
                        "Inventar-Slot 11", false, 65, 6, 2.0F)
        ), List.of());

        assertTrue(summary.contains("gleiche Basis-Rüstungspunkte, aber mehr Zähigkeit"));
    }

    @Test
    void pickaxeSuggestionsExplainFortuneAndSilkTouchChoice() {
        String summary = AILocalEquipmentAdvisor.summarize(List.of(
                gear("minecraft:iron_pickaxe", "Eisenspitzhacke", "pickaxe",
                        "Hotbar-Slot 2", true, 100, 0, 0.0F)
        ), List.of());

        assertTrue(summary.contains("Effizienz"));
        assertTrue(summary.contains("Haltbarkeit"));
        assertTrue(summary.contains("Reparatur"));
        assertTrue(summary.contains("Fortune oder Behutsamkeit"));
        assertTrue(summary.contains("nicht miteinander kombinierbar"));
    }

    @Test
    void existingFortuneSuggestsSeparateSilkTouchTool() {
        String summary = AILocalEquipmentAdvisor.summarize(List.of(
                gear("minecraft:diamond_pickaxe", "Diamantspitzhacke", "pickaxe",
                        "getragen", true, 100, 0, 0.0F,
                        enchantment("minecraft:fortune", "Glück", 3))
        ), List.of());

        assertTrue(summary.contains("zweite Spitzhacke mit Behutsamkeit"));
    }

    @Test
    void compatibleBookUpgradeIsIncludedAsAnOption() {
        String summary = AILocalEquipmentAdvisor.summarize(List.of(
                gear("minecraft:diamond_pickaxe", "Diamantspitzhacke", "pickaxe",
                        "Hotbar-Slot 1", true, 90, 0, 0.0F)
        ), List.of(new AILocalEquipmentAdvisor.BookOpportunity(
                "Verzaubertes Buch", "Diamantspitzhacke", "Effizienz", 4)));

        assertTrue(summary.contains("Verzaubertes Buch"));
        assertTrue(summary.contains("Diamantspitzhacke: Effizienz IV"));
    }

    @Test
    void emptyGearSnapshotIsHandledWithoutInventingItems() {
        String summary = AILocalEquipmentAdvisor.summarize(List.of(), List.of());

        assertTrue(summary.contains("keine unterstützten Rüstungsteile"));
    }

    @Test
    void invalidCategoriesDurabilityAndEnchantmentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> gear(
                "minecraft:unsupported_item", "Nicht unterstütztes Item", "unsupported",
                "Hotbar", false, 100, 0, 0.0F));
        assertThrows(IllegalArgumentException.class, () -> gear(
                "minecraft:diamond_helmet", "Diamanthelm", "armor:head",
                "Inventar", false, 101, 3, 2.0F));
        assertThrows(IllegalArgumentException.class,
                () -> enchantment("minecraft:efficiency", "Effizienz", 0));
    }
}
