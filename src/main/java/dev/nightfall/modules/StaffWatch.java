package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.ListSetting;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Client-only presence monitor for names explicitly supplied by the user.
 *
 * Tab absence is never treated as proof of vanish or staff status. The client cannot
 * inspect server permissions, hidden player lists, or server-side vanish state.
 */
public final class StaffWatch extends Module {
    private static final int HISTORY_LIMIT = 12;
    private static final long SAMPLE_INTERVAL_MS = 1_000L;

    public final ListSetting staffNames = add(new ListSetting("Beobachtete Namen"));

    private final Map<String, Observation> observations = new HashMap<>();
    private ClientWorld observedWorld;
    private long lastSampleMillis;

    private static final class Observation {
        final ArrayDeque<Boolean> tabHistory = new ArrayDeque<>();
        final ArrayDeque<Boolean> entityHistory = new ArrayDeque<>();
        int tabAbsentStreak;
        int entityAbsentStreak;
        long lastTabSeen;
        long lastEntitySeen;
        boolean everSeenTab;
        boolean everSeenEntity;
        boolean inTab;
        boolean entityLoaded;
        boolean invisible;
        String gameMode = "";
        String tabDisplay = "";

        void addSample(boolean tab, boolean entity, long now) {
            inTab = tab;
            entityLoaded = entity;
            add(tabHistory, tab);
            add(entityHistory, entity);
            tabAbsentStreak = tab ? 0 : tabAbsentStreak + 1;
            entityAbsentStreak = entity ? 0 : entityAbsentStreak + 1;
            if (tab) { lastTabSeen = now; everSeenTab = true; }
            if (entity) { lastEntitySeen = now; everSeenEntity = true; }
        }

        private static void add(ArrayDeque<Boolean> history, boolean value) {
            history.addLast(value);
            while (history.size() > HISTORY_LIMIT) history.removeFirst();
        }

        int tabAbsentSamples() { return absent(tabHistory); }
        int entityAbsentSamples() { return absent(entityHistory); }
        private static int absent(ArrayDeque<Boolean> history) {
            int count = 0;
            for (boolean present : history) if (!present) count++;
            return count;
        }
    }

    public static final class ObservationView {
        public final int samples;
        public final int tabAbsentSamples;
        public final int entityAbsentSamples;
        public final int tabAbsentStreak;
        public final int entityAbsentStreak;
        public final boolean inTab;
        public final boolean entityLoaded;
        public final boolean invisible;
        public final boolean everSeenTab;
        public final boolean everSeenEntity;
        public final String gameMode;
        public final String tabDisplay;

        private ObservationView(Observation o) {
            samples = o.tabHistory.size();
            tabAbsentSamples = o.tabAbsentSamples();
            entityAbsentSamples = o.entityAbsentSamples();
            tabAbsentStreak = o.tabAbsentStreak;
            entityAbsentStreak = o.entityAbsentStreak;
            inTab = o.inTab;
            entityLoaded = o.entityLoaded;
            invisible = o.invisible;
            everSeenTab = o.everSeenTab;
            everSeenEntity = o.everSeenEntity;
            gameMode = o.gameMode;
            tabDisplay = o.tabDisplay;
        }
    }

    public StaffWatch() {
        super("StaffWatch", "Verfolgt Tab-/Entity-Präsenz und wiederholte Beobachtungen; erkennt keinen Vanish und keine Serverrechte.", Category.MISC);
    }

    @Override
    public void onTick() {
        sampleObservations();
    }

    /**
     * Shared, throttled sampler used by StaffWatch and AntiVanishHints.
     * Only the names from the user's watch list are tracked.
     */
    public void sampleObservations() {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        if (observedWorld != mc.world) {
            observedWorld = mc.world;
            observations.clear();
            lastSampleMillis = 0L;
        }

        long now = System.currentTimeMillis();
        if (now - lastSampleMillis < SAMPLE_INTERVAL_MS) return;
        lastSampleMillis = now;

        Map<String, PlayerListEntry> tab = new HashMap<>();
        for (PlayerListEntry entry : mc.getNetworkHandler().getPlayerList()) {
            if (entry.getProfile() == null || entry.getProfile().name() == null) continue;
            tab.put(normalize(entry.getProfile().name()), entry);
        }

        Map<String, PlayerEntity> loaded = new HashMap<>();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player.getGameProfile() != null && player.getGameProfile().name() != null)
                loaded.put(normalize(player.getGameProfile().name()), player);
        }

        Set<String> watched = new HashSet<>();
        for (String raw : staffNames.get()) {
            String name = normalize(raw);
            if (name.isEmpty()) continue;
            watched.add(name);

            PlayerListEntry tabEntry = tab.get(name);
            PlayerEntity entity = loaded.get(name);
            boolean inTab = tabEntry != null;
            boolean inWorld = entity != null;

            Observation observation = observations.computeIfAbsent(name, ignored -> new Observation());
            observation.addSample(inTab, inWorld, now);
            observation.invisible = inWorld && entity.isInvisible();
            observation.gameMode = inTab && tabEntry.getGameMode() != null
                    ? tabEntry.getGameMode().toString() : "";
            observation.tabDisplay = inTab && tabEntry.getDisplayName() != null
                    ? tabEntry.getDisplayName().getString() : "";
        }
        observations.keySet().retainAll(watched);
    }

    public ObservationView observation(String name) {
        Observation value = observations.get(normalize(name));
        return value == null || value.tabHistory.isEmpty() ? null : new ObservationView(value);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.player == null || mc.world == null || staffNames.get().isEmpty()) return;
        sampleObservations();

        int x = 8, y = 44;
        ctx.drawText(mc.textRenderer, "StaffWatch · Beobachtete Accounts (Clientdaten)", x, y, Theme.ACCENT, true);
        y += 12;
        int shown = 0;
        for (String raw : staffNames.get()) {
            if (shown++ >= 8) break;
            String name = raw.trim();
            if (name.isEmpty()) continue;
            ObservationView o = observation(name);
            String status;
            int color;
            if (o == null) {
                status = "warte auf erste Stichprobe";
                color = 0xFFB7B7C8;
            } else if (o.inTab && o.entityLoaded) {
                status = "Tab + Entity geladen";
                color = 0xFF6EE7A8;
            } else if (o.inTab) {
                status = "Tab vorhanden; Entity nicht im geladenen Bereich";
                color = 0xFFFFC857;
            } else if (o.entityLoaded) {
                status = "Entity geladen; Tab-Eintrag fehlt";
                color = 0xFFFFC857;
            } else if (o.everSeenTab && o.tabAbsentStreak >= 3) {
                status = "Tab nach vorheriger Sichtung " + o.tabAbsentStreak + " Samples weg; Ursache unbekannt";
                color = 0xFFFFC857;
            } else if (!o.everSeenTab && !o.everSeenEntity) {
                status = "bisher nie in Tab/als Entity beobachtet";
                color = 0xFFB7B7C8;
            } else {
                status = "aktuell keine sichtbaren Daten";
                color = 0xFFB7B7C8;
            }

            String extra = "";
            if (o != null) {
                if ("SPECTATOR".equalsIgnoreCase(o.gameMode)) extra += " · Spectator laut Tab";
                if (o.invisible) extra += " · Entity meldet Unsichtbarkeit";
                if (!o.tabDisplay.isBlank() && !o.tabDisplay.equalsIgnoreCase(name))
                    extra += " · Custom-Tab-Anzeige (kein Rechtebeweis)";
            }
            ctx.drawText(mc.textRenderer, name + " — " + status + extra, x, y, color, true);
            y += 11;
        }
        ctx.drawText(mc.textRenderer, "Keine Tab-Lücke beweist Vanish; Rang-/Permission-Daten sind clientseitig nicht verlässlich prüfbar.",
                x, y + 2, 0xFFFFC857, false);
    }
}
