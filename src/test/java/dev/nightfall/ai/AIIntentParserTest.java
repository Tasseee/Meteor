package dev.nightfall.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AIIntentParserTest {
    @Test
    void recognizesNaturalGermanSearchPhrases() {
        assertEquals("minecraft:jungle", AIIntentParser.findBiomeSearch("Kannst du mir einen Dschungel finden?"));
        assertEquals("minecraft:desert", AIIntentParser.findBiomeSearch("Wo gibt es eine Wüste?"));
        assertEquals("minecraft:cherry_grove", AIIntentParser.findBiomeSearch("Zeig mir einen Kirschwald"));
        assertEquals("minecraft:bamboo_jungle", AIIntentParser.findBiomeSearch("Bitte suche einen bamboo-jungle"));
    }

    @Test
    void recognizesEnglishSearchPhrasesWithoutCaseSensitivity() {
        assertEquals("minecraft:mushroom_fields", AIIntentParser.findBiomeSearch("Where can I FIND mushroom fields?"));
        assertEquals("minecraft:badlands", AIIntentParser.findBiomeSearch("Locate a mesa for me"));
    }

    @Test
    void doesNotTurnOrdinaryKnowledgeQuestionsIntoWorldSearches() {
        assertNull(AIIntentParser.findBiomeSearch("Wie finde ich in Minecraft Diamanten?"));
        assertNull(AIIntentParser.findBiomeSearch("Erzähl mir etwas über den Dschungel"));
        assertNull(AIIntentParser.findBiomeSearch(null));
        assertNull(AIIntentParser.findBiomeSearch("   "));
    }

    @Test
    void keepsUnknownBiomeLabelsReadable() {
        assertEquals("minecraft:unknown", AIIntentParser.biomeLabel("minecraft:unknown"));
        assertEquals("jungle", AIIntentParser.biomeLabel("minecraft:jungle"));
    }
}
