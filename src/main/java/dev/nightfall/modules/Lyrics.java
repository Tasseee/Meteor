package dev.nightfall.modules;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.nightfall.Nightfall;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.ColorSetting;
import dev.nightfall.module.setting.ModeSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.mixin.LyricsTextDisplayAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Client-side synchronized lyrics. Playback metadata is read by the bundled local sidecar;
 * only track metadata is sent to LRCLIB by that sidecar. The Minecraft client only receives
 * timestamped lyric lines over a localhost WebSocket.
 */
public final class Lyrics extends Module {
    private static final URI SERVICE_URI = URI.create("ws://127.0.0.1:8765");
    private static final int FADE_IN_MS = 220;
    private static final int FADE_OUT_MS = 520;
    private static final int MAX_EVENTS_PER_TICK = 24;

    private final NumberSetting distance = add(new NumberSetting("Entfernung", 5.5, 2.0, 16.0, 0.5));
    private final NumberSetting yOffset = add(new NumberSetting("Hoehen-Offset", 0.35, -2.0, 3.0, 0.05));
    private final ColorSetting textColor = add(new ColorSetting("Textfarbe", 0xFFFFFF));
    private final ModeSetting displayMode = add(new ModeSetting("Anzeige", "Welt + HUD", "Nur Welt", "Nur HUD", "Welt + HUD"));
    private final BoolSetting followView = add(new BoolSetting("Text folgt Blickrichtung", true));
    private final BoolSetting bold = add(new BoolSetting("Fett", false));
    private final BoolSetting shadow = add(new BoolSetting("Textschatten", true));
    private final BoolSetting transparent = add(new BoolSetting("Transparenter Hintergrund", true));
    private final BoolSetting showTrack = add(new BoolSetting("Now-Playing HUD", true));
    private final BoolSetting autoStart = add(new BoolSetting("Lokalen Lyrics-Dienst starten", true));

    private final ConcurrentLinkedQueue<InboundEvent> incoming = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean connecting = new AtomicBoolean();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private ScheduledExecutorService reconnectExecutor;
    private volatile WebSocket webSocket;
    private volatile boolean stopping;
    private volatile String connectionStatus = "Warte auf lokalen Lyrics-Dienst";
    private Process sidecarProcess;
    private long lastSidecarAttempt;
    private DisplayEntity.TextDisplayEntity activeDisplay;
    private final List<FadingDisplay> fadingDisplays = new ArrayList<>();
    private long lyricStartedAt;
    private long lyricEndsAt;
    private long pausedRemaining;
    private boolean playbackPaused;
    private String currentLyric = "";
    private String trackTitle = "";
    private String trackArtist = "";
    private String lastServiceStatus = "";
    private net.minecraft.client.world.ClientWorld lastWorld;

    private record InboundEvent(String type, String text, String title, String artist,
                                int durationMs, boolean playing, String status) {}
    private record FadingDisplay(DisplayEntity.TextDisplayEntity entity, long startedAt, long duration) {}

    public Lyrics() {
        super("Lyrics", "Zeigt synchronisierte Liedtexte als schwebende 3D-Texte und im HUD.", Category.VISUALS);
        // Enabled by default; a saved Nightfall config can still turn it off.
        setEnabled(true);
    }

    @Override
    protected void onEnable() {
        stopping = false;
        ensureSidecarFiles();
        reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "nightfall-lyrics-connect");
            t.setDaemon(true);
            return t;
        });
        reconnectExecutor.scheduleWithFixedDelay(() -> {
            if (stopping) return;
            connect();
        }, 0, 3, TimeUnit.SECONDS);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) {
            clearDisplays();
            lastWorld = null;
            return;
        }
        if (lastWorld != mc.world) {
            clearDisplays();
            lastWorld = mc.world;
        }
        if (autoStart.get() && !sidecarAvailable()
                && (sidecarProcess == null || !sidecarProcess.isAlive())
                && System.currentTimeMillis() - lastSidecarAttempt > 10_000L) {
            startSidecarIfNeeded();
        }

        int consumed = 0;
        InboundEvent event;
        while (consumed++ < MAX_EVENTS_PER_TICK && (event = incoming.poll()) != null) {
            handleEvent(event);
        }
        updateActiveDisplay();
        updateFadingDisplays();
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (!showTrack.get() || trackTitle.isBlank() || mc.player == null) return;
        if (displayMode.is("Nur Welt")) return;
        String title = trackArtist.isBlank() ? trackTitle : trackArtist + " — " + trackTitle;
        String line = currentLyric;
        int width = Math.min(ctx.getScaledWindowWidth() - 16,
                Math.max(mc.textRenderer.getWidth(title), mc.textRenderer.getWidth(line)) + 16);
        int x = 8, y = 8;
        ctx.fill(x - 4, y - 4, x + width, y + (line.isBlank() ? 17 : 31), 0xA8111219);
        ctx.fill(x - 4, y - 4, x - 2, y + (line.isBlank() ? 17 : 31), 0xFF9D82FF);
        ctx.drawText(mc.textRenderer, "♫ " + title, x + 4, y, 0xFFFFFFFF, true);
        if (!line.isBlank()) {
            String shown = line;
            int max = width - 12;
            while (mc.textRenderer.getWidth(shown) > max && shown.length() > 4)
                shown = shown.substring(0, shown.length() - 2) + "…";
            ctx.drawText(mc.textRenderer, shown, x + 4, y + 14, 0xFFDDDDFF, true);
        }
    }

    public String status() {
        if (!lastServiceStatus.isBlank()) return connectionStatus + " | " + lastServiceStatus;
        return connectionStatus + (trackTitle.isBlank() ? "" : " | " + trackArtist + " — " + trackTitle);
    }

    public void reconnect() {
        WebSocket current = webSocket;
        webSocket = null;
        if (current != null) {
            try { current.sendClose(WebSocket.NORMAL_CLOSURE, "Nightfall reconnect"); }
            catch (Exception ignored) { }
        }
        connect();
    }

    private void handleEvent(InboundEvent event) {
        switch (event.type()) {
            case "TRACK" -> {
                trackTitle = safe(event.title());
                trackArtist = safe(event.artist());
                currentLyric = "";
                lastServiceStatus = "";
            }
            case "LYRIC_UPDATE" -> {
                if (event.text() == null || event.text().isBlank()) return;
                currentLyric = event.text().trim();
                lastServiceStatus = "";
                showLyric(currentLyric, Math.max(700, Math.min(30_000, event.durationMs())));
            }
            case "LYRIC_CLEAR" -> {
                currentLyric = "";
                trackTitle = "";
                trackArtist = "";
                fadeActive(System.currentTimeMillis());
            }
            case "PLAYBACK_STATE" -> {
                playbackPaused = !event.playing();
                long now = System.currentTimeMillis();
                if (playbackPaused && activeDisplay != null) {
                    pausedRemaining = Math.max(0, lyricEndsAt - now);
                } else if (!playbackPaused && activeDisplay != null) {
                    lyricEndsAt = now + pausedRemaining;
                }
            }
            case "STATUS" -> lastServiceStatus = safe(event.status());
            case "CONNECTED" -> {
                connectionStatus = "Lyrics-Dienst verbunden";
                lastServiceStatus = "";
            }
            default -> { }
        }
    }

    private void showLyric(String line, int durationMs) {
        long now = System.currentTimeMillis();
        fadeActive(now);
        if (displayMode.is("Nur HUD") || mc.world == null || mc.player == null) {
            activeDisplay = null;
            lyricStartedAt = now;
            lyricEndsAt = now + durationMs;
            return;
        }

        Vec3d position = findTextPosition();
        DisplayEntity.TextDisplayEntity display =
                new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY, mc.world);
        display.setNoGravity(true);
        display.setPosition(position.x, position.y, position.z);
        facePlayer(display, position);

        LyricsTextDisplayAccessor accessor = (LyricsTextDisplayAccessor) display;
        Text rendered = Text.literal(line).styled(style -> style
                .withColor(TextColor.fromRgb(textColor.rgb()))
                .withBold(bold.get()));
        accessor.nightfall$setText(rendered);
        accessor.nightfall$setBackground(transparent.get() ? 0x00000000 : 0x7F080812);
        accessor.nightfall$setTextOpacity((byte) 0);
        accessor.nightfall$setFlags((byte) (0x02 | (shadow.get() ? 0x01 : 0)));
        mc.world.addEntity(display);
        activeDisplay = display;
        lyricStartedAt = now;
        lyricEndsAt = now + durationMs;
        pausedRemaining = durationMs;
    }

    private Vec3d findTextPosition() {
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f).normalize();
        Vec3d destination = eye.add(look.multiply(distance.get()));
        var hit = mc.world.raycast(new RaycastContext(eye, destination,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() != HitResult.Type.MISS) {
            double clearDistance = Math.max(1.2, eye.distanceTo(hit.getPos()) - 0.55);
            destination = eye.add(look.multiply(clearDistance));
        }
        return destination.add(0.0, yOffset.get(), 0.0);
    }

    private void facePlayer(DisplayEntity.TextDisplayEntity display, Vec3d position) {
        Vec3d toPlayer = mc.player.getEyePos().subtract(position);
        double horizontal = Math.sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(toPlayer.z, toPlayer.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(toPlayer.y, horizontal)));
        display.setYaw(yaw);
        display.setPitch(pitch);
    }

    private void updateActiveDisplay() {
        if (activeDisplay == null || !activeDisplay.isAlive() || mc.player == null) return;
        long now = System.currentTimeMillis();
        if (playbackPaused) {
            if (followView.get()) {
                Vec3d position = findTextPosition();
                activeDisplay.setPosition(position.x, position.y, position.z);
                facePlayer(activeDisplay, position);
            }
            setOpacity(activeDisplay, 255);
            return;
        }
        if (now >= lyricEndsAt) {
            fadeActive(now);
            return;
        }
        if (followView.get()) {
            Vec3d position = findTextPosition();
            activeDisplay.setPosition(position.x, position.y, position.z);
            facePlayer(activeDisplay, position);
        }
        long elapsed = now - lyricStartedAt;
        long remaining = lyricEndsAt - now;
        int alpha = 255;
        if (elapsed < FADE_IN_MS) alpha = (int) Math.min(255, elapsed * 255.0 / FADE_IN_MS);
        else if (remaining < FADE_OUT_MS) alpha = (int) Math.max(0, remaining * 255.0 / FADE_OUT_MS);
        setOpacity(activeDisplay, alpha);

    }

    private void fadeActive(long now) {
        if (activeDisplay == null) return;
        fadingDisplays.add(new FadingDisplay(activeDisplay, now, FADE_OUT_MS));
        activeDisplay = null;
    }

    private void updateFadingDisplays() {
        long now = System.currentTimeMillis();
        var iterator = fadingDisplays.iterator();
        while (iterator.hasNext()) {
            FadingDisplay fading = iterator.next();
            if (!fading.entity().isAlive() || now - fading.startedAt() >= fading.duration()) {
                fading.entity().discard();
                iterator.remove();
                continue;
            }
            int alpha = (int) Math.max(0, 255 - (now - fading.startedAt()) * 255.0 / fading.duration());
            setOpacity(fading.entity(), alpha);
        }
    }

    private static void setOpacity(DisplayEntity.TextDisplayEntity display, int alpha) {
        ((LyricsTextDisplayAccessor) display).nightfall$setTextOpacity((byte) MathHelper.clamp(alpha, 0, 255));
    }

    private void clearDisplays() {
        if (activeDisplay != null) {
            activeDisplay.discard();
            activeDisplay = null;
        }
        for (FadingDisplay fading : fadingDisplays) fading.entity().discard();
        fadingDisplays.clear();
    }

    private void connect() {
        if (stopping || webSocket != null || !connecting.compareAndSet(false, true)) return;
        httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(3))
                .buildAsync(SERVICE_URI, new WebSocket.Listener() {
                    private final StringBuilder fragments = new StringBuilder();

                    @Override
                    public void onOpen(WebSocket webSocket) {
                        Lyrics.this.webSocket = webSocket;
                        connectionStatus = "Lyrics-Dienst verbunden";
                        incoming.offer(new InboundEvent("CONNECTED", "", "", "", 0, true, ""));
                        webSocket.request(1);
                    }

                    @Override
                    public CompletableFuture<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        fragments.append(data);
                        if (last) {
                            parseMessage(fragments.toString());
                            fragments.setLength(0);
                        }
                        webSocket.request(1);
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public CompletableFuture<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        if (Lyrics.this.webSocket == webSocket) Lyrics.this.webSocket = null;
                        connectionStatus = "Lyrics-Dienst getrennt";
                        return CompletableFuture.completedFuture(null);
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        if (Lyrics.this.webSocket == webSocket) Lyrics.this.webSocket = null;
                        connectionStatus = "Verbindung fehlgeschlagen (Sidecar pruefen)";
                    }
                }).whenComplete((ws, error) -> {
                    connecting.set(false);
                    if (error != null) connectionStatus = "Kein Lyrics-Dienst auf localhost:8765";
                });
    }

    private void parseMessage(String raw) {
        try {
            JsonObject root = JsonParser.parseString(raw).getAsJsonObject();
            String type = string(root, "type", "");
            JsonObject data = root.has("data") && root.get("data").isJsonObject()
                    ? root.getAsJsonObject("data") : new JsonObject();
            incoming.offer(new InboundEvent(type, string(data, "text", ""),
                    string(data, "title", ""), string(data, "artist", ""),
                    integer(data, "duration_ms", 0), bool(data, "playing", true),
                    string(data, "message", "")));
        } catch (Exception e) {
            Nightfall.LOGGER.debug("Ignoriere ungueltige Lyrics-Nachricht: {}", e.getMessage());
        }
    }

    private static String string(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsString();
    }

    private static int integer(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsInt();
    }

    private static boolean bool(JsonObject o, String key, boolean fallback) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsBoolean();
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private boolean sidecarAvailable() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", 8765), 250);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void startSidecarIfNeeded() {
        lastSidecarAttempt = System.currentTimeMillis();
        if (sidecarAvailable() || (sidecarProcess != null && sidecarProcess.isAlive())) return;
        Path script = sidecarDirectory().resolve("lyrics_sidecar.py");
        if (!Files.exists(script)) {
            connectionStatus = "Lyrics-Sidecar fehlt; Nightfall-Dateien pruefen";
            return;
        }
        Path log = sidecarDirectory().resolve("sidecar.log");
        List<List<String>> commands = new ArrayList<>();
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            commands.add(List.of("py", "-3", "-u", script.toString()));
            commands.add(List.of("python", "-u", script.toString()));
        } else {
            commands.add(List.of("python3", "-u", script.toString()));
            commands.add(List.of("python", "-u", script.toString()));
        }
        for (List<String> command : commands) {
            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.directory(sidecarDirectory().toFile());
                builder.redirectErrorStream(true);
                builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
                sidecarProcess = builder.start();
                connectionStatus = "Lyrics-Sidecar wird gestartet";
                return;
            } catch (IOException ignored) {
                sidecarProcess = null;
            }
        }
        connectionStatus = "Python fehlt; Setup in config/nightfall/lyrics lesen";
    }

    private Path sidecarDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("nightfall").resolve("lyrics");
    }

    private void ensureSidecarFiles() {
        try {
            Files.createDirectories(sidecarDirectory());
            copyBundled("/nightfall-lyrics/lyrics_sidecar.py", sidecarDirectory().resolve("lyrics_sidecar.py"));
            copyBundled("/nightfall-lyrics/requirements.txt", sidecarDirectory().resolve("requirements.txt"));
            copyBundled("/nightfall-lyrics/requirements-windows.txt", sidecarDirectory().resolve("requirements-windows.txt"));
            copyBundled("/nightfall-lyrics/README.txt", sidecarDirectory().resolve("README.txt"));
        } catch (IOException e) {
            Nightfall.LOGGER.warn("Lyrics-Sidecar-Dateien konnten nicht vorbereitet werden", e);
        }
    }

    private static void copyBundled(String resource, Path destination) throws IOException {
        if (Files.exists(destination)) return;
        try (InputStream in = Lyrics.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Resource fehlt: " + resource);
            Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    protected void onDisable() {
        stopping = true;
        if (reconnectExecutor != null) {
            reconnectExecutor.shutdownNow();
            reconnectExecutor = null;
        }
        WebSocket current = webSocket;
        webSocket = null;
        if (current != null) {
            try { current.sendClose(WebSocket.NORMAL_CLOSURE, "Lyrics disabled"); }
            catch (Exception ignored) { }
        }
        if (sidecarProcess != null && sidecarProcess.isAlive()) {
            sidecarProcess.destroy();
            sidecarProcess = null;
        }
        clearDisplays();
        incoming.clear();
        currentLyric = "";
    }
}
