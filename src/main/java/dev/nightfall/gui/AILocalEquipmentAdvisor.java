package dev.nightfall.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Read-only summary and improvement hints for armor and utility mining tools.
 * All inputs are bounded snapshots produced by the local Minecraft client.
 */
final class AILocalEquipmentAdvisor {
    record EnchantmentInfo(String id, String name, int level) {
        EnchantmentInfo {
            if (id == null || id.isBlank() || name == null || name.isBlank()
                    || level < 1 || level > 255) {
                throw new IllegalArgumentException("Invalid enchantment snapshot.");
            }
        }
    }

    record GearInfo(
            String itemId,
            String name,
            String category,
            String location,
            boolean equipped,
            int durabilityPercent,
            int armorPoints,
            float armorToughness,
            List<EnchantmentInfo> enchantments) {
        GearInfo {
            if (itemId == null || itemId.isBlank() || name == null || name.isBlank()
                    || category == null || category.isBlank()
                    || location == null || location.isBlank()
                    || durabilityPercent < -1 || durabilityPercent > 100
                    || armorPoints < 0 || !Float.isFinite(armorToughness)
                    || armorToughness < 0.0F) {
                throw new IllegalArgumentException("Invalid equipment snapshot.");
            }
            enchantments = List.copyOf(enchantments == null ? List.of() : enchantments);
            if (enchantments.size() > 32) {
                throw new IllegalArgumentException("Too many enchantments in equipment snapshot.");
            }
        }
    }

    record BookOpportunity(String bookName, String targetName, String enchantmentName, int level) {
        BookOpportunity {
            if (bookName == null || bookName.isBlank()
                    || targetName == null || targetName.isBlank()
                    || enchantmentName == null || enchantmentName.isBlank()
                    || level < 1 || level > 255) {
                throw new IllegalArgumentException("Invalid enchanted-book opportunity.");
            }
        }
    }

    private AILocalEquipmentAdvisor() {
    }

    static String summarize(List<GearInfo> gear, List<BookOpportunity> bookOpportunities) {
        List<GearInfo> equipment = List.copyOf(gear == null ? List.of() : gear);
        List<BookOpportunity> bookMatches = List.copyOf(
                bookOpportunities == null ? List.of() : bookOpportunities);

        if (equipment.isEmpty()) {
            return "Equipment- und Verzauberungscheck: In den ausgerüsteten Rüstungsslots und den "
                    + "36 normalen Inventar-/Hotbar-Slots wurden keine unterstützten Rüstungsteile, "
                    + "Spitzhacken, Schaufeln oder Hacken erkannt.";
        }

        StringBuilder out = new StringBuilder("Lokaler Equipment- und Verzauberungsberater:");
        out.append("\nErfasste Rüstung und Werkzeuge (höchstens 12 Einträge):");
        int shown = 0;
        for (GearInfo item : equipment) {
            if (shown++ >= 12) {
                out.append("\n- Weitere Ausrüstung wurde aus Platzgründen ausgelassen.");
                break;
            }
            out.append("\n- ").append(item.name())
                    .append(" [").append(categoryLabel(item.category()))
                    .append(", ").append(item.location());
            if (item.durabilityPercent() >= 0) {
                out.append(", Haltbarkeit ").append(item.durabilityPercent()).append("%");
            }
            if (item.category().startsWith("armor:")) {
                out.append(", Basis-Rüstung ").append(item.armorPoints())
                        .append(", Zähigkeit ").append(formatNumber(item.armorToughness()));
            }
            out.append("]");
            if (item.enchantments().isEmpty()) {
                out.append(" · keine Verzauberungen erkannt");
            } else {
                out.append(" · Verzauberungen: ");
                out.append(item.enchantments().stream()
                        .limit(8)
                        .map(enchantment -> enchantment.name() + " " + romanLevel(enchantment.level()))
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("keine"));
                if (item.enchantments().size() > 8) out.append(" (weitere ausgelassen)");
            }
        }

        List<String> comparisons = compareArmor(equipment);
        if (!comparisons.isEmpty()) {
            out.append("\nVergleich getragener Rüstung mit Inventar-Alternativen:");
            for (String comparison : comparisons) out.append("\n- ").append(comparison);
        }

        List<String> suggestions = improvementSuggestions(equipment);
        if (!suggestions.isEmpty()) {
            out.append("\nMögliche Verbesserungen (aufgabenabhängige Hinweise, keine automatische Auswahl):");
            for (String suggestion : suggestions) out.append("\n- ").append(suggestion);
        }

        if (!bookMatches.isEmpty()) {
            out.append("\nErkannte kompatible Verzauberungsbuch-Kandidaten (höchstens 10):");
            for (int i = 0; i < Math.min(10, bookMatches.size()); i++) {
                BookOpportunity opportunity = bookMatches.get(i);
                out.append("\n- ").append(opportunity.bookName())
                        .append(" → ").append(opportunity.targetName())
                        .append(": ").append(opportunity.enchantmentName())
                        .append(" ").append(romanLevel(opportunity.level()));
            }
        } else {
            out.append("\nKeine kompatiblen, höheren Verzauberungsbuch-Kandidaten in den erfassten Slots erkannt.");
        }

        return out.append("\nGrenzen: Der Vergleich zeigt Basis-Rüstungspunkte/Zähigkeit getrennt von "
                        + "Verzauberungen und Haltbarkeit. Er berechnet keine Anvil-XP-/Materialkosten, "
                        + "keine zufälligen Verzauberungstisch-Angebote und nimmt keine Änderungen vor. "
                        + "Nur getragene Rüstung sowie Spitzhacken, Schaufeln und Hacken im normalen "
                        + "Inventar/Hotbar-Bereich werden bewertet.")
                .toString();
    }

    private static List<String> compareArmor(List<GearInfo> equipment) {
        List<String> comparisons = new ArrayList<>();
        for (GearInfo worn : equipment) {
            if (!worn.equipped() || !worn.category().startsWith("armor:")) continue;

            List<GearInfo> alternatives = equipment.stream()
                    .filter(candidate -> !candidate.equipped()
                            && candidate.category().equals(worn.category())
                            && isBaseArmorUpgrade(candidate, worn))
                    .sorted(Comparator.comparingInt(GearInfo::armorPoints).reversed()
                            .thenComparing(Comparator.comparingDouble(GearInfo::armorToughness).reversed()))
                    .limit(2)
                    .toList();
            for (GearInfo alternative : alternatives) {
                String difference;
                if (alternative.armorPoints() > worn.armorPoints()) {
                    difference = "+" + (alternative.armorPoints() - worn.armorPoints())
                            + " Basis-Rüstungspunkt(e)";
                } else {
                    difference = "gleiche Basis-Rüstungspunkte, aber mehr Zähigkeit";
                }
                comparisons.add(alternative.name() + " im Inventar hat " + difference
                        + " als " + worn.name() + " (getragen). Verzauberungen und Haltbarkeit "
                        + "sind in diesem Basiswertvergleich nicht eingerechnet.");
            }
            if (comparisons.size() >= 6) break;
        }
        return comparisons;
    }

    private static boolean isBaseArmorUpgrade(GearInfo candidate, GearInfo worn) {
        return candidate.armorPoints() > worn.armorPoints()
                || (candidate.armorPoints() == worn.armorPoints()
                && candidate.armorToughness() > worn.armorToughness());
    }

    private static List<String> improvementSuggestions(List<GearInfo> equipment) {
        Set<String> suggestions = new LinkedHashSet<>();
        for (GearInfo item : equipment) {
            Set<String> enchantmentIds = item.enchantments().stream()
                    .map(EnchantmentInfo::id)
                    .collect(java.util.stream.Collectors.toSet());

            if (item.category().startsWith("armor:")) {
                if (!hasAny(enchantmentIds, "minecraft:protection", "minecraft:fire_protection",
                        "minecraft:blast_protection", "minecraft:projectile_protection")) {
                    suggestions.add("Für " + item.name() + " eine passende Schutz-Verzauberung erwägen.");
                }
                if (!enchantmentIds.contains("minecraft:unbreaking")) {
                    suggestions.add("Haltbarkeit für " + item.name() + " mit Haltbarkeit verbessern.");
                }
                if (!enchantmentIds.contains("minecraft:mending")) {
                    suggestions.add("Reparatur für " + item.name() + " mit Reparatur erwägen.");
                }
                if ("armor:feet".equals(item.category())
                        && !enchantmentIds.contains("minecraft:feather_falling")) {
                    suggestions.add("Für " + item.name() + " Federfall als spezialisierte Stiefel-Verzauberung prüfen.");
                }
                if ("armor:head".equals(item.category())
                        && !enchantmentIds.contains("minecraft:respiration")) {
                    suggestions.add("Für " + item.name() + " Atmung erwägen, falls du oft unter Wasser bist.");
                }
            } else if (isUtilityTool(item.category())) {
                if (!enchantmentIds.contains("minecraft:efficiency")) {
                    suggestions.add("Effizienz kann die Nutzung von " + item.name() + " beschleunigen.");
                }
                if (!enchantmentIds.contains("minecraft:unbreaking")) {
                    suggestions.add("Haltbarkeit kann die Lebensdauer von " + item.name() + " verbessern.");
                }
                if (!enchantmentIds.contains("minecraft:mending")) {
                    suggestions.add("Reparatur kann " + item.name() + " mit Erfahrungspunkten instand halten.");
                }
                if ("pickaxe".equals(item.category())) {
                    boolean fortune = enchantmentIds.contains("minecraft:fortune");
                    boolean silkTouch = enchantmentIds.contains("minecraft:silk_touch");
                    if (!fortune && !silkTouch) {
                        suggestions.add("Bei Spitzhacken je nach Ziel Fortune oder Behutsamkeit wählen; "
                                + "beide Verzauberungen sind nicht miteinander kombinierbar.");
                    } else if (fortune) {
                        suggestions.add("Eine zweite Spitzhacke mit Behutsamkeit kann für Blöcke sinnvoll sein, "
                                + "die unverändert abgebaut werden sollen.");
                    } else {
                        suggestions.add("Eine zweite Spitzhacke mit Glück kann für zusätzliche Erz-Drops "
                                + "sinnvoll sein.");
                    }
                }
            }
            if (suggestions.size() >= 12) break;
        }
        return List.copyOf(suggestions);
    }

    private static boolean isUtilityTool(String category) {
        return "pickaxe".equals(category) || "shovel".equals(category) || "hoe".equals(category);
    }

    private static boolean hasAny(Set<String> values, String... candidates) {
        for (String candidate : candidates) {
            if (values.contains(candidate)) return true;
        }
        return false;
    }

    private static String categoryLabel(String category) {
        return switch (category) {
            case "armor:head" -> "Helm";
            case "armor:chest" -> "Brust-Rüstung";
            case "armor:legs" -> "Bein-Rüstung";
            case "armor:feet" -> "Stiefel";
            case "pickaxe" -> "Spitzhacke";
            case "shovel" -> "Schaufel";
            case "hoe" -> "Hacke";
            default -> category;
        };
    }

    private static String formatNumber(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String romanLevel(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(level);
        };
    }
}
