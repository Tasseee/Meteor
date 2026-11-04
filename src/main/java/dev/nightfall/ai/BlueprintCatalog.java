package dev.nightfall.ai;

import java.util.Locale;

/**
 * Curated, version-scoped Minecraft build knowledge.
 * Entries distinguish tested build plans from reference guides; never present an untested
 * farm as an automatically placeable or guaranteed-working schematic.
 */
public final class BlueprintCatalog {
    private BlueprintCatalog() {}

    public static String search(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (normalized.isEmpty() || normalized.contains("dripstone") || normalized.contains("tropfstein")
                || normalized.contains("farm") || normalized.contains("lava")) {
            return """
                    NIGHTFALL KNOWLEDGE BASE — GROSSE TROPfSTEIN-FARM
                    Eintrag: farm.dripstone.megafarm.01
                    Zielversion: Minecraft Java 1.21.11
                    Typ: Bau- und Technikleitfaden; NICHT als automatisch platzierbarer Bauplan getestet.
                    Status: Mechanik dokumentiert, Farmaufbau noch nicht ingame validiert.

                    KONZEPT: modulare Großfarm mit 16 unabhängigen Wachstumszellen (4 × 4).
                    Jede Zelle besitzt einen Tropfsteinblock als Quelle, darüber einen Wasserquellblock,
                    darunter einen spitzen Tropfstein als Stalaktit und darunter einen freien Fall-/Sammelkanal.
                    Die Module werden in vier Reihen angeordnet und in einen gemeinsamen, zugänglichen
                    Sammelbereich geführt. Mehrere Etagen sind möglich, benötigen aber getrennte
                    Wasserführung und genügend Kopffreiheit.

                    WICHTIGE MECHANIK:
                    - Ein spitzer Tropfstein wächst nicht einfach beliebig: Wachstum ist zufalls-/tickbasiert
                      und benötigt die passende Tropfsteinblock-/Wasser-Konfiguration.
                    - Ein Tropfsteinblock allein erzeugt keine Items. Das Wachstum muss geerntet werden.
                    - Für eine automatische Ernte braucht jedes Modul eine getestete Erkennung und
                      Blockbruch-/Kolbenlogik. Eine pauschale Observer-Piston-Schaltung wird hier
                      bewusst NICHT als garantiert funktionierend ausgegeben.
                    - Wasser muss so eingefasst sein, dass es die Wachstumszelle nicht wegspült.
                    - Server-Tickrate, Random-Ticks und Farmregeln beeinflussen den Ertrag erheblich.

                    GROSSER AUSBAU — 16 ZELLEN:
                    - 4 × 4 Wachstumszellen, mit Wartungsgang an mindestens einer Seite.
                    - Pro Zelle: 1 Tropfsteinblock, 1 Wasserquelle oberhalb, 1 spitzer Tropfstein
                      nach unten, plus nicht-spülbare Einfassung.
                    - Unter den Zellen: getrennte Fall-/Sammelrinnen; Hopper und Kisten erst nach
                      dem tatsächlichen Erntepunkt einbauen.
                    - Baue zuerst eine einzelne Zelle und prüfe Wachstum und Ernte auf deinem Server.
                      Danach vervielfältigen. So vermeidest du, 16 fehlerhafte Module zu bauen.

                    MATERIAL-/VORBEREITUNGSLISTE (Richtwerte, kein fertiger Block-für-Block-Schematic):
                    - 16 Tropfsteinblöcke
                    - mindestens 16 spitze Tropfsteine als Startmaterial
                    - 16 Wasserquellen (Eimer/Quellblöcke)
                    - Baublöcke für Rahmen und Trennwände
                    - Trichter, Kisten und optional Trichterloren für die Itemsammlung
                    - Kolben/Beobachter/Redstone nur für eine separat getestete Ernteschaltung

                    NACHTFALL-BUILDER-GRENZE:
                    Der aktuelle automatische Builder platziert ausschließlich Full-Cube-BlockItems
                    aus der Hotbar und akzeptiert maximal 200 Positionen pro Plan. Er kann derzeit
                    keine Wasserquellen, spitzen Tropfsteine, Trichter, Kolben, Beobachter oder
                    Redstone-Schaltungen als funktionierende Farmkomponenten zuverlässig setzen.
                    Deshalb darf diese Datenbankvorlage NICHT an build_custom_structure weitergereicht
                    oder als fertig gebaute Farm behauptet werden. Sie dient als recherchierbare
                    Bauanleitung, bis ein eigener Block-/Flüssigkeits-/Redstone-Builder vorhanden ist.

                    QUELLE/PROVENIENZ:
                    Kuratierter interner Technikdatensatz auf Basis der bekannten Vanilla-Mechanik;
                    kein importierter Community-Schematic und keine behauptete Ingame-Testfreigabe.
                    """;
        }
        return "Noch kein passender kuratierter Datenbankeintrag für '" + query
                + "'. Die lokale Wissensdatenbank enthält aktuell den großen Tropfsteinfarm-Leitfaden. "
                + "Es wird kein ungeprüfter Farmplan als funktionierend ausgegeben.";
    }
}
