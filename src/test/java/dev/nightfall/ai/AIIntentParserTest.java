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
    void recognizesNaturalResumeRequests() {
        org.junit.jupiter.api.Assertions.assertTrue(AIChatClient.isResumeRequest("mach weiter"));
        org.junit.jupiter.api.Assertions.assertTrue(AIChatClient.isResumeRequest("Mach da weiter bitte"));
        org.junit.jupiter.api.Assertions.assertTrue(AIChatClient.isResumeRequest("setz fort"));
        org.junit.jupiter.api.Assertions.assertTrue(AIChatClient.isResumeRequest("continue"));
        org.junit.jupiter.api.Assertions.assertFalse(AIChatClient.isResumeRequest("weiter"));
        org.junit.jupiter.api.Assertions.assertFalse(AIChatClient.isResumeRequest(null));
    }

    @Test
    void recognizesExplicitSavedTaskDeletionRequests() {
        org.junit.jupiter.api.Assertions.assertTrue(
                AIChatClient.isClearSavedTaskRequest("Lösch bitte den gespeicherten Auftrag"));
        org.junit.jupiter.api.Assertions.assertTrue(
                AIChatClient.isClearSavedTaskRequest("delete the saved task"));
        org.junit.jupiter.api.Assertions.assertTrue(
                AIChatClient.isClearSavedTaskRequest("Vergiss den gespeicherten Sammelauftrag"));
        org.junit.jupiter.api.Assertions.assertFalse(
                AIChatClient.isClearSavedTaskRequest("Stoppe die aktuelle Aufgabe"));
        org.junit.jupiter.api.Assertions.assertFalse(
                AIChatClient.isClearSavedTaskRequest("Lösche alle Blöcke"));
        org.junit.jupiter.api.Assertions.assertFalse(
                AIChatClient.isClearSavedTaskRequest("Lösche den Bau-Checkpoint"));
        org.junit.jupiter.api.Assertions.assertFalse(
                AIChatClient.isClearSavedTaskRequest(null));
    }

    @Test
    void keepsUnknownBiomeLabelsReadable() {
        assertEquals("minecraft:unknown", AIIntentParser.biomeLabel("minecraft:unknown"));
        assertEquals("jungle", AIIntentParser.biomeLabel("minecraft:jungle"));
    }
}
