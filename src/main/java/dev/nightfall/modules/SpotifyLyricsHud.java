package dev.nightfall.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.StringSetting;
import dev.nightfall.util.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Spotify now-playing HUD with optional timestamped lyrics from LRCLIB.
 * OAuth uses PKCE; no Spotify client secret is requested or stored.
 */
public final class SpotifyLyricsHud extends HudModule {
    private static final String REDIRECT_URI = "http://127.0.0.1:8888/callback";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    private final StringSetting clientId = add(new StringSetting("Spotify Client ID", "", 120));
    private final BoolSetting showLyrics = add(new BoolSetting("Synchronisierte Lyrics", true));
    private final BoolSetting showNextLine = add(new BoolSetting("Naechste Zeile anzeigen", true));
    private final BoolSetting showVisualizer = add(new BoolSetting("Animierter Equalizer", true));
    private final BoolSetting playerHudVisible = add(new BoolSetting("Player-HUD anzeigen", false));
    private final NumberSetting hudWidth = add(new NumberSetting("HUD-Breite", 220, 180, 320, 10));

    private volatile String accessToken;
    private volatile Track track;
    private volatile String status = "Spotify nicht verbunden";
    private volatile List<LyricLine> lyrics = List.of();
    private volatile String lyricTrackKey = "";
    private volatile long lastPollAt;
    private volatile long trackReceivedAt;
    private volatile long positionAtReceipt;
    private volatile boolean loginInProgress;
    private volatile boolean loginAttempted;
    private volatile boolean lyricsLoading;
    private volatile String oauthState;
    private volatile String verifier;
    private HttpServer callbackServer;
    private ScheduledExecutorService worker;

    private record Track(String title, String artist, String album, long progressMs, long durationMs, boolean playing) {}
    private record LyricLine(long timeMs, String text) {}

    public SpotifyLyricsHud() {
        super("SpotifyLyricsHUD", "Spotify-Player, Fortschrittsbalken und synchronisierte Liedtexte.");
    }

    @Override protected int[] defaultPos(int sw, int sh) { return new int[]{sw - 250, sh - 180}; }

    @Override protected void onEnable() {
        stopWorker();
        loginAttempted = false;
        if (clientId.get().isBlank()) {
            status = "Client ID in den Einstellungen eintragen";
            notifyPlayer("SpotifyLyricsHUD: Client ID fehlt. Spotify Developer Dashboard und Redirect-URI 127.0.0.1:8888/callback einrichten.");
            return;
        }
        startWorker();
        beginLogin();
    }

    @Override protected void onDisable() {
        stopWorker();
        accessToken = null;
        track = null;
        lyrics = List.of();
        lyricTrackKey = "";
        status = "Spotify getrennt";
    }

    @Override public void onWorldLeave() {
        // Keep the Spotify connection running across server/world changes.
    }

    private synchronized void startWorker() {
        if (worker != null && !worker.isShutdown()) return;
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Nightfall-Spotify");
            t.setDaemon(true);
            return t;
        });
        worker.scheduleWithFixedDelay(this::pollSafely, 1, 3, TimeUnit.SECONDS);
    }

    private synchronized void stopWorker() {
        if (worker != null) worker.shutdownNow();
        worker = null;
        loginInProgress = false;
        loginAttempted = false;
        closeCallbackServer();
    }

    @Override public void onTick() {
        if (!isEnabled() || clientId.get().isBlank() || accessToken != null || loginInProgress || loginAttempted) return;
        loginAttempted = true;
        startWorker();
        beginLogin();
    }

    private void beginLogin() {
        if (loginInProgress) return;
        loginAttempted = true;
        loginInProgress = true;
        status = "Spotify-Anmeldung wird geoeffnet";
        try {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            oauthState = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(24));
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
            callbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 8888), 0);
            callbackServer.createContext("/callback", exchange -> {
                try {
                    String query = exchange.getRequestURI().getRawQuery();
                    Map<String, String> params = parseQuery(query == null ? "" : query);
                    String returnedState = params.getOrDefault("state", "");
                    String code = params.get("code");
                    if (!oauthState.equals(returnedState) || code == null) {
                        byte[] body = "Spotify-Verbindung fehlgeschlagen. Du kannst dieses Fenster schliessen.".getBytes(StandardCharsets.UTF_8);
                        exchange.sendResponseHeaders(400, body.length);
                        exchange.getResponseBody().write(body);
                        return;
                    }
                    byte[] body = "Spotify verbunden! Du kannst zu Minecraft zurueckkehren.".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                    exchangeCode(code);
                } catch (Throwable t) {
                    status = "Spotify-Anmeldung fehlgeschlagen";
                    loginInProgress = false;
                } finally {
                    closeCallbackServer();
                }
            });
            callbackServer.setExecutor(Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Nightfall-Spotify-OAuth");
                t.setDaemon(true);
                return t;
            }));
            callbackServer.start();
            String url = "https://accounts.spotify.com/authorize?response_type=code&client_id="
                    + enc(clientId.get()) + "&redirect_uri=" + enc(REDIRECT_URI)
                    + "&scope=" + enc("user-read-currently-playing user-read-playback-state")
                    + "&code_challenge_method=S256&code_challenge=" + enc(challenge)
                    + "&state=" + enc(oauthState);
            if (!Desktop.isDesktopSupported()) throw new IOException("Browser konnte nicht automatisch geoeffnet werden");
            Desktop.getDesktop().browse(URI.create(url));
        } catch (Exception e) {
            status = "Login fehlgeschlagen: Port 8888/Browser pruefen";
            loginInProgress = false;
            closeCallbackServer();
            notifyPlayer("Spotify Login fehlgeschlagen. Ist Port 8888 frei und die Redirect-URI korrekt?");
        }
    }

    private static byte[] randomBytes(int count) {
        byte[] b = new byte[count];
        new SecureRandom().nextBytes(b);
        return b;
    }

    private void exchangeCode(String code) {
        try {
            String body = "client_id=" + enc(clientId.get()) + "&grant_type=authorization_code"
                    + "&code=" + enc(code) + "&redirect_uri=" + enc(REDIRECT_URI)
                    + "&code_verifier=" + enc(verifier);
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() < 200 || response.statusCode() >= 300 || !json.has("access_token")) {
                status = "Token abgelehnt - Client ID/Redirect pruefen";
                loginInProgress = false;
                notifyPlayer("Spotify konnte nicht verbunden werden. Client ID und Redirect-URI kontrollieren.");
                return;
            }
            accessToken = json.get("access_token").getAsString();
            status = "Spotify verbunden";
            loginInProgress = false;
            notifyPlayer("Spotify verbunden! Spiele einen Song ab.");
            pollSafely();
        } catch (Exception e) {
            status = "Spotify-Verbindung fehlgeschlagen";
            loginInProgress = false;
        }
    }

    private void pollSafely() {
        try {
            if (!isEnabled() || accessToken == null) return;
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
                    .timeout(Duration.ofSeconds(8)).header("Authorization", "Bearer " + accessToken).GET().build();
            HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            lastPollAt = System.currentTimeMillis();
            if (response.statusCode() == 204 || response.body().isBlank()) {
                track = null;
                status = "Spotify verbunden · nichts wird abgespielt";
                return;
            }
            if (response.statusCode() == 401) {
                accessToken = null;
                status = "Spotify-Login abgelaufen · Modul aus/an";
                return;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                status = "Spotify API: HTTP " + response.statusCode();
                return;
            }
            JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!root.has("item") || root.get("item").isJsonNull()) {
                track = null;
                status = "Spotify verbunden · kein Titel";
                return;
            }
            JsonObject item = root.getAsJsonObject("item");
            String title = str(item, "name", "Unbekannter Titel");
            String album = item.has("album") ? str(item.getAsJsonObject("album"), "name", "") : "";
            StringBuilder artist = new StringBuilder();
            if (item.has("artists") && item.get("artists").isJsonArray()) {
                item.getAsJsonArray("artists").forEach(a -> {
                    if (artist.length() > 0) artist.append(", ");
                    artist.append(str(a.getAsJsonObject(), "name", ""));
                });
            }
            long progress = root.has("progress_ms") && !root.get("progress_ms").isJsonNull() ? root.get("progress_ms").getAsLong() : 0;
            long duration = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0;
            boolean playing = root.has("is_playing") && root.get("is_playing").getAsBoolean();
            Track next = new Track(title, artist.toString(), album, progress, duration, playing);
            String key = title + "|" + artist + "|" + album;
            track = next;
            positionAtReceipt = progress;
            trackReceivedAt = System.currentTimeMillis();
            status = playing ? "NOW PLAYING" : "PAUSED";
            if (showLyrics.get() && !key.equals(lyricTrackKey) && !lyricsLoading) fetchLyrics(next, key);
        } catch (Exception e) {
            status = "Spotify-Verbindung pruefen";
        }
    }

    private void fetchLyrics(Track t, String key) {
        lyricsLoading = true;
        lyricTrackKey = key;
        lyrics = List.of();
        try {
            String url = "https://lrclib.net/api/get?track_name=" + enc(t.title())
                    + "&artist_name=" + enc(t.artist()) + "&album_name=" + enc(t.album())
                    + "&duration=" + Math.max(0, t.durationMs() / 1000);
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "NightfallClient/0.5.15 (SpotifyLyricsHUD)").GET().build();
            HttpResponse<String> response = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                if (json.has("syncedLyrics") && !json.get("syncedLyrics").isJsonNull()) {
                    lyrics = parseLrc(json.get("syncedLyrics").getAsString());
                } else if (json.has("plainLyrics") && !json.get("plainLyrics").isJsonNull()) {
                    List<LyricLine> plain = new ArrayList<>();
                    for (String line : json.get("plainLyrics").getAsString().split("\\R")) {
                        if (!line.isBlank()) plain.add(new LyricLine(0, line.trim()));
                    }
                    lyrics = List.copyOf(plain);
                }
            }
        } catch (Exception ignored) {
            lyrics = List.of();
        } finally {
            lyricsLoading = false;
        }
    }

    private static List<LyricLine> parseLrc(String text) {
        List<LyricLine> out = new ArrayList<>();
        for (String line : text.split("\\R")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\](.*)").matcher(line);
            while (m.find()) {
                long minutes = Long.parseLong(m.group(1));
                long seconds = Long.parseLong(m.group(2));
                String fraction = m.group(3) == null ? "0" : m.group(3);
                long millis = fraction.length() == 1 ? Long.parseLong(fraction) * 100
                        : fraction.length() == 2 ? Long.parseLong(fraction) * 10 : Long.parseLong(fraction);
                String lyric = m.group(4).trim();
                if (!lyric.isEmpty()) out.add(new LyricLine((minutes * 60 + seconds) * 1000 + millis, lyric));
            }
        }
        out.sort(java.util.Comparator.comparingLong(LyricLine::timeMs));
        return List.copyOf(out);
    }

    @Override protected void draw(DrawContext ctx) {
        if (!playerHudVisible.get()) { w = 0; h = 0; return; }
        long now = System.currentTimeMillis();
        int width = hudWidth.getInt();
        w = width;
        Track current = track;
        boolean hasTrack = current != null;
        List<LyricLine> lines = lyrics;
        long progress = 0;
        int lyricIndex = -1;
        if (hasTrack) {
            progress = current.progressMs();
            if (current.playing()) progress += Math.max(0, now - trackReceivedAt);
            progress = Math.min(current.durationMs(), progress);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).timeMs() <= progress) lyricIndex = i;
                else break;
            }
        }

        boolean showLyricPanel = showLyrics.get();
        boolean visualizer = showVisualizer.get();
        h = 82 + (visualizer ? 18 : 0) + (showLyricPanel ? (showNextLine.get() ? 48 : 32) : 0);

        // Glass-like dark card with a moving Spotify-green accent.
        ctx.fill(0, 0, w, h, 0xE810131B);
        ctx.fill(0, 0, w, 2, 0xFF1ED760);
        ctx.fill(0, 2, 3, h, 0xFF16853D);
        ctx.fill(8, 7, 31, 29, 0xFF172B22);
        ctx.drawText(mc.textRenderer, "♫", 14, 12, 0xFF1ED760, true);
        ctx.drawText(mc.textRenderer, "NIGHTFALL  /  SPOTIFY", 37, 9, 0xFF1ED760, true);

        String state = hasTrack ? (current.playing() ? "● LIVE" : "Ⅱ PAUSED") : status;
        String stateText = trim(state, 22);
        ctx.drawText(mc.textRenderer, stateText, w - 9 - mc.textRenderer.getWidth(stateText), 10,
                hasTrack && current.playing() ? 0xFF8BFFC0 : Theme.TEXT_DIM, false);

        if (hasTrack) {
            ctx.drawText(mc.textRenderer, trim(current.title(), 34), 10, 32, Theme.TEXT, true);
            ctx.drawText(mc.textRenderer, trim(current.artist(), 38), 10, 45, 0xFFB4BBC8, false);
            long duration = Math.max(1, current.durationMs());
            int barX = 10, barY = 62, barW = w - 20;
            ctx.fill(barX, barY, barX + barW, barY + 4, 0xFF303743);
            int filled = (int) (barW * Math.min(1.0, progress / (double) duration));
            if (filled > 0) {
                ctx.fill(barX, barY, barX + filled, barY + 4, 0xFF1ED760);
                ctx.fill(Math.max(barX, barX + filled - 2), barY - 1, barX + filled, barY + 5, 0xFF9BFFC0);
            }
            String times = fmt(progress) + "  /  " + fmt(duration);
            ctx.drawText(mc.textRenderer, times, 10, 69, 0xFF929BAA, false);
        } else {
            ctx.drawText(mc.textRenderer, trim(status, 34), 10, 34, Theme.TEXT, false);
            ctx.drawText(mc.textRenderer, "Setze deine Spotify Client ID in den Einstellungen", 10, 49, 0xFF929BAA, false);
        }

        int contentY = 82;
        if (visualizer) {
            int bars = Math.min(36, Math.max(20, (w - 20) / 5));
            int gap = 2;
            int totalWidth = bars * 3 + (bars - 1) * gap;
            int startX = Math.max(10, (w - totalWidth) / 2);
            int baseY = contentY + 13;
            for (int i = 0; i < bars; i++) {
                double wave = Math.abs(Math.sin(now / 180.0 + i * 0.62));
                double pulse = Math.abs(Math.sin(now / 420.0 - i * 0.21));
                int barHeight = hasTrack && current.playing() ? 3 + (int) (wave * (4 + pulse * 8)) : 3;
                int x = startX + i * (3 + gap);
                int color = i % 4 == 0 ? 0xFF8BFFC0 : 0xFF1ED760;
                ctx.fill(x, baseY - barHeight, x + 3, baseY, color);
            }
            contentY += 19;
        }

        if (showLyricPanel) {
            int lyricY = contentY + 2;
            int lyricHeight = showNextLine.get() ? 43 : 27;
            ctx.fill(8, lyricY, w - 8, lyricY + lyricHeight, 0xAA080B10);
            ctx.fill(8, lyricY, 10, lyricY + lyricHeight, 0xFF1ED760);
            if (!hasTrack || lines.isEmpty()) {
                String msg = lyricsLoading ? "Liedtext wird geladen..." : "Keine synchronisierten Lyrics gefunden";
                ctx.drawText(mc.textRenderer, trim(msg, 36), 14, lyricY + 10, 0xFF929BAA, false);
            } else {
                String prev = lyricIndex > 0 ? lines.get(lyricIndex - 1).text() : "";
                String line = lyricIndex >= 0 ? lines.get(lyricIndex).text() : "♪  …  ♪";
                String next = lyricIndex + 1 < lines.size() ? lines.get(lyricIndex + 1).text() : "";
                if (!prev.isBlank()) ctx.drawText(mc.textRenderer, trim(prev, 38), 14, lyricY + 3, 0xFF7F8998, false);
                int pulseColor = 0xFF1ED760;
                ctx.drawText(mc.textRenderer, trim(line, 38), 14, lyricY + (showNextLine.get() ? 15 : 9), pulseColor, true);
                if (showNextLine.get() && !next.isBlank()) ctx.drawText(mc.textRenderer, trim(next, 38), 14, lyricY + 29, 0xFF9AA3B1, false);
            }
        }
    }

    /** Read-only snapshot for the in-world lyrics renderer. */
    public String[] worldLyricLines() {
        List<LyricLine> snapshot = lyrics;
        String[] out = new String[snapshot.size()];
        for (int i = 0; i < snapshot.size(); i++) out[i] = snapshot.get(i).text();
        return out;
    }

    public long[] worldLyricTimes() {
        List<LyricLine> snapshot = lyrics;
        long[] out = new long[snapshot.size()];
        for (int i = 0; i < snapshot.size(); i++) out[i] = snapshot.get(i).timeMs();
        return out;
    }

    public long worldProgressMs() {
        Track t = track;
        if (t == null) return 0;
        long p = t.progressMs();
        if (t.playing()) p += Math.max(0, System.currentTimeMillis() - trackReceivedAt);
        return Math.min(t.durationMs(), p);
    }

    public boolean hasWorldTrack() { return track != null; }
    public boolean isWorldTrackPlaying() { return track != null && track.playing(); }

    private void notifyPlayer(String message) {
        mc.execute(() -> {
            if (mc.player != null) mc.player.sendMessage(Text.literal("[Nightfall] " + message), false);
        });
    }

    private void closeCallbackServer() {
        HttpServer server = callbackServer;
        callbackServer = null;
        if (server != null) server.stop(0);
    }

    private static Map<String, String> parseQuery(String query) {
        java.util.HashMap<String, String> map = new java.util.HashMap<>();
        for (String part : query.split("&")) {
            int i = part.indexOf('=');
            if (i > 0) map.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8),
                    URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
        }
        return map;
    }

    private static String str(JsonObject object, String key, String fallback) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
    }
    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static String fmt(long ms) { long s = Math.max(0, ms / 1000); return String.format("%d:%02d", s / 60, s % 60); }
    private static String trim(String s, int max) { return s == null ? "" : s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…"; }
}
