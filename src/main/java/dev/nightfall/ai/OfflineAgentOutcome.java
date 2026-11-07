package dev.nightfall.ai;

/**
 * Conservative classifier for terminal messages emitted by existing local
 * Minecraft controllers. Unknown, partial, and safety-stop outcomes are failures
 * for dependency scheduling; they are never silently promoted to success.
 */
public final class OfflineAgentOutcome {
    private OfflineAgentOutcome() {}

    public static boolean isSuccessfulImmediateControllerResult(String result) {
        return result != null && result.startsWith("Du bist bereits am gewünschten Ziel.");
    }

    public static boolean isSuccessfulControllerCompletion(String summary) {
        if (summary == null || summary.isBlank()) return false;
        String result = summary.trim();
        return result.equals("Ziel erreicht.")
                || result.equals("Bewegung abgeschlossen.")
                || result.startsWith("Blockänderung bestätigt · Abbau abgeschlossen.")
                || result.startsWith("Materialsammlung abgeschlossen:")
                || result.startsWith("Strukturhinweis erkannt:")
                || result.startsWith("Schmelzen abgeschlossen:")
                || result.startsWith("Crafting vollständig abgeschlossen:")
                || result.startsWith("Inventarsortierung verifiziert: Alle ")
                || result.startsWith("Essen abgeschlossen:")
                || result.startsWith("Bauabschnitt abgeschlossen:")
                || result.startsWith("Kein feindlicher Mob mehr in der Nähe. Kampf beendet.");
    }
}
