package dev.nightfall.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import dev.nightfall.module.HudModule;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.module.setting.StringSetting;
import dev.nightfall.util.Theme;
import java.awt.Desktop;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.class_2561;
import net.minecraft.class_332;

public final class SpotifyLyricsHud extends HudModule {
   private static final String REDIRECT_URI = "http://127.0.0.1:8888/callback";
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5L)).followRedirects(Redirect.NEVER).build();
   private final StringSetting clientId = this.add(new StringSetting("Spotify Client ID", "", 120));
   private final BoolSetting showLyrics = this.add(new BoolSetting("Synchronisierte Lyrics", true));
   private final BoolSetting showNextLine = this.add(new BoolSetting("Naechste Zeile anzeigen", true));
   private final BoolSetting showVisualizer = this.add(new BoolSetting("Animierter Equalizer", true));
   private final BoolSetting playerHudVisible = this.add(new BoolSetting("Player-HUD anzeigen", false));
   private final NumberSetting hudWidth = this.add(new NumberSetting("HUD-Breite", 220.0, 180.0, 320.0, 10.0));
   private volatile String accessToken;
   private volatile SpotifyLyricsHud.Track track;
   private volatile String status = "Spotify nicht verbunden";
   private volatile List<SpotifyLyricsHud.LyricLine> lyrics = List.of();
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

   public SpotifyLyricsHud() {
      super("SpotifyLyricsHUD", "Spotify-Player, Fortschrittsbalken und synchronisierte Liedtexte.");
   }

   @Override
   protected int[] defaultPos(int sw, int sh) {
      return new int[]{sw - 250, sh - 180};
   }

   @Override
   protected void onEnable() {
      this.stopWorker();
      this.loginAttempted = false;
      if (this.clientId.get().isBlank()) {
         this.status = "Client ID in den Einstellungen eintragen";
         this.notifyPlayer("SpotifyLyricsHUD: Client ID fehlt. Spotify Developer Dashboard und Redirect-URI 127.0.0.1:8888/callback einrichten.");
      } else {
         this.startWorker();
         this.beginLogin();
      }
   }

   @Override
   protected void onDisable() {
      this.stopWorker();
      this.accessToken = null;
      this.track = null;
      this.lyrics = List.of();
      this.lyricTrackKey = "";
      this.status = "Spotify getrennt";
   }

   @Override
   public void onWorldLeave() {
   }

   private synchronized void startWorker() {
      if (this.worker == null || this.worker.isShutdown()) {
         this.worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Nightfall-Spotify");
            t.setDaemon(true);
            return t;
         });
         this.worker.scheduleWithFixedDelay(this::pollSafely, 1L, 3L, TimeUnit.SECONDS);
      }
   }

   private synchronized void stopWorker() {
      if (this.worker != null) {
         this.worker.shutdownNow();
      }

      this.worker = null;
      this.loginInProgress = false;
      this.loginAttempted = false;
      this.closeCallbackServer();
   }

   @Override
   public void onTick() {
      if (this.isEnabled() && !this.clientId.get().isBlank() && this.accessToken == null && !this.loginInProgress && !this.loginAttempted) {
         this.loginAttempted = true;
         this.startWorker();
         this.beginLogin();
      }
   }

   private void beginLogin() {
      if (!this.loginInProgress) {
         this.loginAttempted = true;
         this.loginInProgress = true;
         this.status = "Spotify-Anmeldung wird geoeffnet";

         try {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            this.verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
            this.oauthState = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(24));
            String challenge = Base64.getUrlEncoder()
               .withoutPadding()
               .encodeToString(MessageDigest.getInstance("SHA-256").digest(this.verifier.getBytes(StandardCharsets.US_ASCII)));
            this.callbackServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 8888), 0);
            this.callbackServer.createContext("/callback", exchange -> {
               try {
                  String query = exchange.getRequestURI().getRawQuery();
                  Map<String, String> params = parseQuery(query == null ? "" : query);
                  String returnedState = params.getOrDefault("state", "");
                  String code = params.get("code");
                  if (this.oauthState.equals(returnedState) && code != null) {
                     byte[] body = "Spotify verbunden! Du kannst zu Minecraft zurueckkehren.".getBytes(StandardCharsets.UTF_8);
                     exchange.sendResponseHeaders(200, body.length);
                     exchange.getResponseBody().write(body);
                     exchange.close();
                     this.exchangeCode(code);
                     return;
                  }

                  byte[] body = "Spotify-Verbindung fehlgeschlagen. Du kannst dieses Fenster schliessen.".getBytes(StandardCharsets.UTF_8);
                  exchange.sendResponseHeaders(400, body.length);
                  exchange.getResponseBody().write(body);
               } catch (Throwable var10) {
                  this.status = "Spotify-Anmeldung fehlgeschlagen";
                  this.loginInProgress = false;
                  return;
               } finally {
                  this.closeCallbackServer();
               }
            });
            this.callbackServer.setExecutor(Executors.newSingleThreadExecutor(r -> {
               Thread t = new Thread(r, "Nightfall-Spotify-OAuth");
               t.setDaemon(true);
               return t;
            }));
            this.callbackServer.start();
            String url = "https://accounts.spotify.com/authorize?response_type=code&client_id="
               + enc(this.clientId.get())
               + "&redirect_uri="
               + enc("http://127.0.0.1:8888/callback")
               + "&scope="
               + enc("user-read-currently-playing user-read-playback-state")
               + "&code_challenge_method=S256&code_challenge="
               + enc(challenge)
               + "&state="
               + enc(this.oauthState);
            if (!Desktop.isDesktopSupported()) {
               throw new IOException("Browser konnte nicht automatisch geoeffnet werden");
            }

            Desktop.getDesktop().browse(URI.create(url));
         } catch (Exception var4) {
            this.status = "Login fehlgeschlagen: Port 8888/Browser pruefen";
            this.loginInProgress = false;
            this.closeCallbackServer();
            this.notifyPlayer("Spotify Login fehlgeschlagen. Ist Port 8888 frei und die Redirect-URI korrekt?");
         }
      }
   }

   private static byte[] randomBytes(int count) {
      byte[] b = new byte[count];
      new SecureRandom().nextBytes(b);
      return b;
   }

   private void exchangeCode(String code) {
      try {
         String body = "client_id="
            + enc(this.clientId.get())
            + "&grant_type=authorization_code&code="
            + enc(code)
            + "&redirect_uri="
            + enc("http://127.0.0.1:8888/callback")
            + "&code_verifier="
            + enc(this.verifier);
         HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
            .timeout(Duration.ofSeconds(10L))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(BodyPublishers.ofString(body))
            .build();
         HttpResponse<String> response = HTTP.send(req, BodyHandlers.ofString());
         JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
         if (response.statusCode() < 200 || response.statusCode() >= 300 || !json.has("access_token")) {
            this.status = "Token abgelehnt - Client ID/Redirect pruefen";
            this.loginInProgress = false;
            this.notifyPlayer("Spotify konnte nicht verbunden werden. Client ID und Redirect-URI kontrollieren.");
            return;
         }

         this.accessToken = json.get("access_token").getAsString();
         this.status = "Spotify verbunden";
         this.loginInProgress = false;
         this.notifyPlayer("Spotify verbunden! Spiele einen Song ab.");
         this.pollSafely();
      } catch (Exception var6) {
         this.status = "Spotify-Verbindung fehlgeschlagen";
         this.loginInProgress = false;
      }
   }

   private void pollSafely() {
      try {
         if (!this.isEnabled() || this.accessToken == null) {
            return;
         }

         HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
            .timeout(Duration.ofSeconds(8L))
            .header("Authorization", "Bearer " + this.accessToken)
            .GET()
            .build();
         HttpResponse<String> response = HTTP.send(req, BodyHandlers.ofString());
         this.lastPollAt = System.currentTimeMillis();
         if (response.statusCode() == 204 || response.body().isBlank()) {
            this.track = null;
            this.status = "Spotify verbunden · nichts wird abgespielt";
            return;
         }

         if (response.statusCode() == 401) {
            this.accessToken = null;
            this.status = "Spotify-Login abgelaufen · Modul aus/an";
            return;
         }

         if (response.statusCode() < 200 || response.statusCode() >= 300) {
            this.status = "Spotify API: HTTP " + response.statusCode();
            return;
         }

         JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
         if (!root.has("item") || root.get("item").isJsonNull()) {
            this.track = null;
            this.status = "Spotify verbunden · kein Titel";
            return;
         }

         JsonObject item = root.getAsJsonObject("item");
         String title = str(item, "name", "Unbekannter Titel");
         String album = item.has("album") ? str(item.getAsJsonObject("album"), "name", "") : "";
         StringBuilder artist = new StringBuilder();
         if (item.has("artists") && item.get("artists").isJsonArray()) {
            item.getAsJsonArray("artists").forEach(a -> {
               if (artist.length() > 0) {
                  artist.append(", ");
               }

               artist.append(str(a.getAsJsonObject(), "name", ""));
            });
         }

         long progress = root.has("progress_ms") && !root.get("progress_ms").isJsonNull() ? root.get("progress_ms").getAsLong() : 0L;
         long duration = item.has("duration_ms") ? item.get("duration_ms").getAsLong() : 0L;
         boolean playing = root.has("is_playing") && root.get("is_playing").getAsBoolean();
         SpotifyLyricsHud.Track next = new SpotifyLyricsHud.Track(title, artist.toString(), album, progress, duration, playing);
         String key = title + "|" + artist + "|" + album;
         this.track = next;
         this.positionAtReceipt = progress;
         this.trackReceivedAt = System.currentTimeMillis();
         this.status = playing ? "NOW PLAYING" : "PAUSED";
         if (this.showLyrics.get() && !key.equals(this.lyricTrackKey) && !this.lyricsLoading) {
            this.fetchLyrics(next, key);
         }
      } catch (Exception var15) {
         this.status = "Spotify-Verbindung pruefen";
      }
   }

   private void fetchLyrics(SpotifyLyricsHud.Track t, String key) {
      this.lyricsLoading = true;
      this.lyricTrackKey = key;
      this.lyrics = List.of();

      try {
         String url = "https://lrclib.net/api/get?track_name="
            + enc(t.title())
            + "&artist_name="
            + enc(t.artist())
            + "&album_name="
            + enc(t.album())
            + "&duration="
            + Math.max(0L, t.durationMs() / 1000L);
         HttpRequest req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8L))
            .header("User-Agent", "NightfallClient/0.5.15 (SpotifyLyricsHUD)")
            .GET()
            .build();
         HttpResponse<String> response = HTTP.send(req, BodyHandlers.ofString());
         if (response.statusCode() >= 200 && response.statusCode() < 300) {
            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (json.has("syncedLyrics") && !json.get("syncedLyrics").isJsonNull()) {
               this.lyrics = parseLrc(json.get("syncedLyrics").getAsString());
            } else if (json.has("plainLyrics") && !json.get("plainLyrics").isJsonNull()) {
               List<SpotifyLyricsHud.LyricLine> plain = new ArrayList<>();

               for (String line : json.get("plainLyrics").getAsString().split("\\R")) {
                  if (!line.isBlank()) {
                     plain.add(new SpotifyLyricsHud.LyricLine(0L, line.trim()));
                  }
               }

               long step = plain.isEmpty() ? 0L : Math.max(1000L, t.durationMs() / plain.size());

               for (int i = 0; i < plain.size(); i++) {
                  plain.set(i, new SpotifyLyricsHud.LyricLine(step * i, plain.get(i).text()));
               }

               this.lyrics = List.copyOf(plain);
            }
         }
      } catch (Exception var15) {
         this.lyrics = List.of();
      } finally {
         this.lyricsLoading = false;
      }
   }

   private static List<SpotifyLyricsHud.LyricLine> parseLrc(String text) {
      List<SpotifyLyricsHud.LyricLine> out = new ArrayList<>();

      for (String line : text.split("\\R")) {
         Matcher m = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\](.*)").matcher(line);

         while (m.find()) {
            long minutes = Long.parseLong(m.group(1));
            long seconds = Long.parseLong(m.group(2));
            String fraction = m.group(3) == null ? "0" : m.group(3);
            long millis = fraction.length() == 1
               ? Long.parseLong(fraction) * 100L
               : (fraction.length() == 2 ? Long.parseLong(fraction) * 10L : Long.parseLong(fraction));
            String lyric = m.group(4).trim();
            if (!lyric.isEmpty()) {
               out.add(new SpotifyLyricsHud.LyricLine((minutes * 60L + seconds) * 1000L + millis, lyric));
            }
         }
      }

      out.sort(Comparator.comparingLong(SpotifyLyricsHud.LyricLine::timeMs));
      return List.copyOf(out);
   }

   @Override
   protected void draw(class_332 ctx) {
      if (!this.playerHudVisible.get()) {
         this.w = 0;
         this.h = 0;
      } else {
         long now = System.currentTimeMillis();
         int width = this.hudWidth.getInt();
         this.w = width;
         SpotifyLyricsHud.Track current = this.track;
         boolean hasTrack = current != null;
         List<SpotifyLyricsHud.LyricLine> lines = this.lyrics;
         long progress = 0L;
         int lyricIndex = -1;
         if (hasTrack) {
            progress = current.progressMs();
            if (current.playing()) {
               progress += Math.max(0L, now - this.trackReceivedAt);
            }

            progress = Math.min(current.durationMs(), progress);
            int i = 0;

            while (i < lines.size() && lines.get(i).timeMs() <= progress) {
               lyricIndex = i++;
            }
         }

         boolean showLyricPanel = this.showLyrics.get();
         boolean visualizer = this.showVisualizer.get();
         this.h = 82 + (visualizer ? 18 : 0) + (showLyricPanel ? (this.showNextLine.get() ? 48 : 32) : 0);
         ctx.method_25294(0, 0, this.w, this.h, -401599717);
         ctx.method_25294(0, 0, this.w, 2, -14756000);
         ctx.method_25294(0, 2, 3, this.h, -15301315);
         ctx.method_25294(8, 7, 31, 29, -15258846);
         ctx.method_51433(mc.field_1772, "♫", 14, 12, -14756000, true);
         ctx.method_51433(mc.field_1772, "NIGHTFALL  /  SPOTIFY", 37, 9, -14756000, true);
         String state = hasTrack ? (current.playing() ? "● LIVE" : "Ⅱ PAUSED") : this.status;
         String stateText = trim(state, 22);
         ctx.method_51433(
            mc.field_1772, stateText, this.w - 9 - mc.field_1772.method_1727(stateText), 10, hasTrack && current.playing() ? -7602240 : Theme.TEXT_DIM, false
         );
         if (hasTrack) {
            ctx.method_51433(mc.field_1772, trim(current.title(), 34), 10, 32, Theme.TEXT, true);
            ctx.method_51433(mc.field_1772, trim(current.artist(), 38), 10, 45, -4932664, false);
            long duration = Math.max(1L, current.durationMs());
            int barX = 10;
            int barY = 62;
            int barW = this.w - 20;
            ctx.method_25294(barX, barY, barX + barW, barY + 4, -13617341);
            int filled = (int)(barW * Math.min(1.0, (double)progress / duration));
            if (filled > 0) {
               ctx.method_25294(barX, barY, barX + filled, barY + 4, -14756000);
               ctx.method_25294(Math.max(barX, barX + filled - 2), barY - 1, barX + filled, barY + 5, -6553664);
            }

            String times = fmt(progress) + "  /  " + fmt(duration);
            ctx.method_51433(mc.field_1772, times, 10, 69, -7169110, false);
         } else {
            ctx.method_51433(mc.field_1772, trim(this.status, 34), 10, 34, Theme.TEXT, false);
            ctx.method_51433(mc.field_1772, "Setze deine Spotify Client ID in den Einstellungen", 10, 49, -7169110, false);
         }

         int contentY = 82;
         if (visualizer) {
            int bars = Math.min(36, Math.max(20, (this.w - 20) / 5));
            int gap = 2;
            int totalWidth = bars * 3 + (bars - 1) * gap;
            int startX = Math.max(10, (this.w - totalWidth) / 2);
            int baseY = contentY + 13;

            for (int i = 0; i < bars; i++) {
               double wave = Math.abs(Math.sin(now / 180.0 + i * 0.62));
               double pulse = Math.abs(Math.sin(now / 420.0 - i * 0.21));
               int barHeight = hasTrack && current.playing() ? 3 + (int)(wave * (4.0 + pulse * 8.0)) : 3;
               int x = startX + i * (3 + gap);
               int color = i % 4 == 0 ? -7602240 : -14756000;
               ctx.method_25294(x, baseY - barHeight, x + 3, baseY, color);
            }

            contentY += 19;
         }

         if (showLyricPanel) {
            int lyricY = contentY + 2;
            int lyricHeight = this.showNextLine.get() ? 43 : 27;
            ctx.method_25294(8, lyricY, this.w - 8, lyricY + lyricHeight, -1442313456);
            ctx.method_25294(8, lyricY, 10, lyricY + lyricHeight, -14756000);
            if (hasTrack && !lines.isEmpty()) {
               String prev = lyricIndex > 0 ? lines.get(lyricIndex - 1).text() : "";
               String line = lyricIndex >= 0 ? lines.get(lyricIndex).text() : "♪  …  ♪";
               String next = lyricIndex + 1 < lines.size() ? lines.get(lyricIndex + 1).text() : "";
               if (!prev.isBlank()) {
                  ctx.method_51433(mc.field_1772, trim(prev, 38), 14, lyricY + 3, -8418920, false);
               }

               int pulseColor = -14756000;
               ctx.method_51433(mc.field_1772, trim(line, 38), 14, lyricY + (this.showNextLine.get() ? 15 : 9), pulseColor, true);
               if (this.showNextLine.get() && !next.isBlank()) {
                  ctx.method_51433(mc.field_1772, trim(next, 38), 14, lyricY + 29, -6642767, false);
               }
            } else {
               String msg = this.lyricsLoading ? "Liedtext wird geladen..." : "Keine synchronisierten Lyrics gefunden";
               ctx.method_51433(mc.field_1772, trim(msg, 36), 14, lyricY + 10, -7169110, false);
            }
         }
      }
   }

   public String[] worldLyricLines() {
      List<SpotifyLyricsHud.LyricLine> snapshot = this.lyrics;
      String[] out = new String[snapshot.size()];

      for (int i = 0; i < snapshot.size(); i++) {
         out[i] = snapshot.get(i).text();
      }

      return out;
   }

   public long[] worldLyricTimes() {
      List<SpotifyLyricsHud.LyricLine> snapshot = this.lyrics;
      long[] out = new long[snapshot.size()];

      for (int i = 0; i < snapshot.size(); i++) {
         out[i] = snapshot.get(i).timeMs();
      }

      return out;
   }

   public long worldProgressMs() {
      SpotifyLyricsHud.Track t = this.track;
      if (t == null) {
         return 0L;
      } else {
         long p = t.progressMs();
         if (t.playing()) {
            p += Math.max(0L, System.currentTimeMillis() - this.trackReceivedAt);
         }

         return Math.min(t.durationMs(), p);
      }
   }

   public boolean hasWorldTrack() {
      return this.track != null;
   }

   public boolean isWorldTrackPlaying() {
      return this.track != null && this.track.playing();
   }

   private void notifyPlayer(String message) {
      mc.execute(() -> {
         if (mc.field_1724 != null) {
            mc.field_1724.method_7353(class_2561.method_43470("[Nightfall] " + message), false);
         }
      });
   }

   private void closeCallbackServer() {
      HttpServer server = this.callbackServer;
      this.callbackServer = null;
      if (server != null) {
         server.stop(0);
      }
   }

   private static Map<String, String> parseQuery(String query) {
      HashMap<String, String> map = new HashMap<>();

      for (String part : query.split("&")) {
         int i = part.indexOf(61);
         if (i > 0) {
            map.put(URLDecoder.decode(part.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(part.substring(i + 1), StandardCharsets.UTF_8));
         }
      }

      return map;
   }

   private static String str(JsonObject object, String key, String fallback) {
      return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : fallback;
   }

   private static String enc(String s) {
      return URLEncoder.encode(s, StandardCharsets.UTF_8);
   }

   private static String fmt(long ms) {
      long s = Math.max(0L, ms / 1000L);
      return String.format("%d:%02d", s / 60L, s % 60L);
   }

   private static String trim(String s, int max) {
      return s == null ? "" : (s.length() <= max ? s : s.substring(0, Math.max(1, max - 1)) + "…");
   }

   private record LyricLine(long timeMs, String text) {
   }

   private record Track(String title, String artist, String album, long progressMs, long durationMs, boolean playing) {
   }
}
