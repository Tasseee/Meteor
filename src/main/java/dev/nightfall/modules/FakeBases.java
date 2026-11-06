package dev.nightfall.modules;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.BoolSetting;
import dev.nightfall.module.setting.NumberSetting;
import dev.nightfall.scanner.Waypoint;
import dev.nightfall.scanner.WaypointManager;
import dev.nightfall.util.Draw;
import dev.nightfall.util.Projection;
import dev.nightfall.util.Storage;
import dev.nightfall.util.Theme;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Local fake-base training and recognition.
 * A manually confirmed decoy teaches a block-entity layout; later copies are matched
 * independent of the four horizontal chunk rotations. No world blocks are changed.
 */
public final class FakeBases extends Module {
    public static final String CATEGORY = "FakeBase";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type TEMPLATE_LIST = new TypeToken<List<Template>>() {}.getType();
    private static final List<Template> TEMPLATES = new ArrayList<>();
    private static final Map<Long, String> AUTO_MATCHES = new HashMap<>();
    private static String loadedFor;
    private static FakeBases instance;

    private final BoolSetting labels = add(new BoolSetting("Label", true));
    private final BoolSetting tracers = add(new BoolSetting("Tracer", false));
    private final NumberSetting maxDistance = add(new NumberSetting("Max Distanz", 3000, 50, 10000, 50));
    private final NumberSetting minBlockEntities = add(new NumberSetting("Min. BlockEntities", 4, 2, 32, 1));
    private final NumberSetting similarity = add(new NumberSetting("Template-Aehnlichkeit", 0.85, 0.50, 1.0, 0.05));
    private final double[] point = new double[2];

    private static final class Template {
        String name;
        List<String> signature = new ArrayList<>();
    }

    public FakeBases() {
        super("FakeBases", "Lernt bestaetigte Fake-Basen und erkennt gedrehte Kopien automatisch.", Category.WORLD);
        instance = this;
    }

    private static long key(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | ((long) cz << 32);
    }

    private static int minimumEntities() {
        return instance == null ? 4 : instance.minBlockEntities.getInt();
    }

    private static double threshold() {
        return instance == null ? 0.85 : instance.similarity.get();
    }

    private static String context() {
        return Storage.server() + "_" + Storage.dim();
    }

    private static Path templateFile() {
        return Storage.dir().resolve("fake_templates_" + context() + ".json");
    }

    private static void ensureLoaded() {
        String context = context();
        if (context.equals(loadedFor)) return;
        loadedFor = context;
        TEMPLATES.clear();
        AUTO_MATCHES.clear();
        Path file = templateFile();
        if (!Files.exists(file)) return;
        try {
            List<Template> loaded = GSON.fromJson(Files.readString(file), TEMPLATE_LIST);
            if (loaded != null) TEMPLATES.addAll(loaded);
        } catch (Exception e) {
            dev.nightfall.Nightfall.LOGGER.error("FakeBase-Templates konnten nicht geladen werden: {}", file, e);
        }
    }

    private static void saveTemplates() {
        try {
            Files.writeString(templateFile(), GSON.toJson(TEMPLATES));
        } catch (IOException e) {
            dev.nightfall.Nightfall.LOGGER.error("FakeBase-Templates konnten nicht gespeichert werden", e);
        }
    }

    private static List<String> fingerprint(WorldChunk chunk) {
        Collection<BlockEntity> entities = chunk.getBlockEntities().values();
        if (entities.size() < minimumEntities()) return List.of();

        List<String> best = null;
        String bestKey = null;
        for (int rotation = 0; rotation < 4; rotation++) {
            List<int[]> transformed = new ArrayList<>(entities.size());
            int minX = 16, minY = Integer.MAX_VALUE, minZ = 16;
            for (BlockEntity be : entities) {
                BlockPos pos = be.getPos();
                int x = pos.getX() & 15;
                int z = pos.getZ() & 15;
                int rx, rz;
                switch (rotation) {
                    case 1 -> { rx = 15 - z; rz = x; }
                    case 2 -> { rx = 15 - x; rz = 15 - z; }
                    case 3 -> { rx = z; rz = 15 - x; }
                    default -> { rx = x; rz = z; }
                }
                transformed.add(new int[]{rx, pos.getY(), rz, be.getClass().getSimpleName().hashCode()});
                minX = Math.min(minX, rx);
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, rz);
            }

            List<String> tokens = new ArrayList<>(transformed.size());
            for (int[] pos : transformed) {
                String type = Integer.toString(pos[3], 36);
                tokens.add(type + ":" + (pos[0] - minX) + "," + (pos[1] - minY) + "," + (pos[2] - minZ));
            }
            Collections.sort(tokens);
            String joined = String.join("|", tokens);
            if (bestKey == null || joined.compareTo(bestKey) < 0) {
                bestKey = joined;
                best = tokens;
            }
        }
        return best == null ? List.of() : best;
    }

    private static void upsertTemplate(String name, List<String> signature) {
        if (name == null || name.isBlank() || signature.size() < minimumEntities()) return;
        Template found = null;
        for (Template template : TEMPLATES) {
            if (template.name.equalsIgnoreCase(name)) { found = template; break; }
        }
        if (found == null) {
            found = new Template();
            found.name = name;
            TEMPLATES.add(found);
        }
        if (!found.signature.equals(signature)) {
            found.signature = new ArrayList<>(signature);
            saveTemplates();
        }
    }

    /** Learn a template from the currently loaded, user-confirmed decoy chunk. */
    public static String learn(String name, WorldChunk chunk) {
        ensureLoaded();
        if (chunk == null) return "Chunk ist nicht geladen.";
        List<String> signature = fingerprint(chunk);
        if (signature.size() < minimumEntities())
            return "Zu wenig BlockEntities zum Lernen (" + signature.size() + "/" + minimumEntities() + ").";
        upsertTemplate(name, signature);
        return "FakeBase-Template '" + name + "' gelernt (" + signature.size() + " BlockEntities).";
    }

    /** Called by BaseFinder once per scan; returns the matched template label, or null. */
    public static String processChunk(WorldChunk chunk, int cx, int cz) {
        ensureLoaded();
        Waypoint manual = null;
        for (Waypoint waypoint : WaypointManager.all()) {
            if (CATEGORY.equalsIgnoreCase(waypoint.category)
                    && waypoint.dim.equals(Storage.dim())
                    && (waypoint.x >> 4) == cx && (waypoint.z >> 4) == cz) {
                manual = waypoint;
                break;
            }
        }

        List<String> candidate = fingerprint(chunk);
        if (manual != null) {
            if (!candidate.isEmpty()) upsertTemplate(manual.name, candidate);
            AUTO_MATCHES.put(key(cx, cz), manual.name + " (manuell/trainiert)");
            return manual.name + " (manuell/trainiert)";
        }

        if (candidate.isEmpty() || TEMPLATES.isEmpty()) {
            AUTO_MATCHES.remove(key(cx, cz));
            return null;
        }

        Set<String> candidateSet = new HashSet<>(candidate);
        for (Template template : TEMPLATES) {
            if (template.signature == null || template.signature.size() < minimumEntities()) continue;
            Set<String> templateSet = new HashSet<>(template.signature);
            int intersection = 0;
            for (String token : candidateSet) if (templateSet.contains(token)) intersection++;
            int union = candidateSet.size() + templateSet.size() - intersection;
            double score = union == 0 ? 0 : (double) intersection / union;
            if (score >= threshold()) {
                AUTO_MATCHES.put(key(cx, cz), template.name + " (" + Math.round(score * 100) + "%, rotiert/verschoben)");
                return template.name;
            }
        }
        AUTO_MATCHES.remove(key(cx, cz));
        return null;
    }

    public static boolean isMarkedChunk(int cx, int cz) {
        ensureLoaded();
        String dimension = Storage.dim();
        for (Waypoint waypoint : WaypointManager.all()) {
            if (!CATEGORY.equalsIgnoreCase(waypoint.category) || !dimension.equals(waypoint.dim)) continue;
            if ((waypoint.x >> 4) == cx && (waypoint.z >> 4) == cz) return true;
        }
        return false;
    }

    public static boolean isKnownFakeChunk(int cx, int cz) {
        ensureLoaded();
        return isMarkedChunk(cx, cz) || AUTO_MATCHES.containsKey(key(cx, cz));
    }

    public static String matchLabel(int cx, int cz) {
        ensureLoaded();
        return AUTO_MATCHES.get(key(cx, cz));
    }

    public static List<String> templateNames() {
        ensureLoaded();
        List<String> names = new ArrayList<>();
        for (Template template : TEMPLATES) names.add(template.name);
        return names;
    }

    public static boolean forgetTemplate(String name) {
        ensureLoaded();
        boolean removed = TEMPLATES.removeIf(t -> t.name.equalsIgnoreCase(name));
        if (removed) saveTemplates();
        return removed;
    }

    @Override public void renderHud(DrawContext ctx) {
        if (mc.player == null || !Projection.valid()) return;
        for (Waypoint w : WaypointManager.all()) {
            if (!CATEGORY.equalsIgnoreCase(w.category) || !w.dim.equals(Storage.dim())) continue;
            double x = w.x + 0.5, y = w.y + 0.5, z = w.z + 0.5;
            double dx = x - mc.player.getX(), dy = y - mc.player.getY(), dz = z - mc.player.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > maxDistance.get() || !Projection.project(x, y, z, point)) continue;
            int color = Theme.withAlpha(0xFFFF5F75, 0xE8);
            ctx.fill((int) point[0] - 3, (int) point[1] - 3, (int) point[0] + 4, (int) point[1] + 4, color);
            if (labels.get()) Draw.centeredText(ctx, mc.textRenderer, "[DECOY] " + w.name, (float) point[0], (float) point[1] - 14, 0xFFFF8797);
            if (tracers.get()) Draw.line(ctx, Projection.width() / 2f, Projection.height() / 2f, (float) point[0], (float) point[1], color, 1);
        }
    }
}
