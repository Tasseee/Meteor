package dev.nightfall.ai;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * Small, bounded checkpoint for resumable player intent only.
 * It deliberately stores no stale block targets, paths, inventory snapshots or credentials.
 */
final class AITaskCheckpoint {
    private static final int VERSION = 1;
    private static final long MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L;
    private static final long MAX_FILE_BYTES = 8L * 1_024L;

    record Task(String kind, String item, int count, int durationSeconds, int targetY,
                int x, int y, int z) {}

    private AITaskCheckpoint() {}

    static boolean save(MinecraftClient mc, Task task) {
        if (mc == null || mc.world == null || task == null) return false;
        String server = serverKey(mc);
        if ("unknown".equals(server) || !valid(task)) return false;
        try {
            Path path = checkpointPath();
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            root.addProperty("server", server);
            root.addProperty("dimension", mc.world.getRegistryKey().getValue().toString());
            root.addProperty("savedAt", System.currentTimeMillis());
            root.addProperty("kind", task.kind());
            root.addProperty("item", task.item() == null ? "" : task.item());
            root.addProperty("count", task.count());
            root.addProperty("durationSeconds", task.durationSeconds());
            root.addProperty("targetY", task.targetY());
            root.addProperty("x", task.x());
            root.addProperty("y", task.y());
            root.addProperty("z", task.z());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, root.toString());
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    static Task loadForCurrentWorld(MinecraftClient mc) {
        if (mc == null || mc.world == null) return null;
        Path path = checkpointPath();
        try {
            if (!Files.isRegularFile(path) || Files.size(path) > MAX_FILE_BYTES) return null;
            JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (root.get("version").getAsInt() != VERSION) return null;
            if (!serverKey(mc).equals(root.get("server").getAsString())
                    || !mc.world.getRegistryKey().getValue().toString().equals(root.get("dimension").getAsString()))
                return null;
            long savedAt = root.get("savedAt").getAsLong();
            long age = System.currentTimeMillis() - savedAt;
            if (age < 0L || age > MAX_AGE_MILLIS) {
                clear();
                return null;
            }
            Task task = new Task(root.get("kind").getAsString(), root.get("item").getAsString(),
                    root.get("count").getAsInt(), root.get("durationSeconds").getAsInt(),
                    root.get("targetY").getAsInt(), root.get("x").getAsInt(),
                    root.get("y").getAsInt(), root.get("z").getAsInt());
            return valid(task) ? task : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    static void clear() {
        try { Files.deleteIfExists(checkpointPath()); } catch (IOException ignored) {}
    }

    static String serverKey(MinecraftClient mc) {
        if (mc.getCurrentServerEntry() != null)
            return "server:" + mc.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
        if (mc.isInSingleplayer() && mc.getServer() != null)
            return "singleplayer:" + mc.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                    .toAbsolutePath().normalize();
        return "unknown";
    }

    private static boolean valid(Task task) {
        if (task == null || task.kind() == null || task.kind().length() > 24
                || task.item() == null || task.item().length() > 64
                || task.durationSeconds() < 0 || task.durationSeconds() > 1_200
                || task.count() < 1 || task.count() > 1_024) return false;
        if ("navigate".equals(task.kind()))
            return task.x() >= -30_000_000 && task.x() <= 30_000_000
                    && task.z() >= -30_000_000 && task.z() <= 30_000_000
                    && task.y() >= -2_048 && task.y() <= 2_048;
        if (!("gather_wood".equals(task.kind()) || "gather_block".equals(task.kind())
                || "gather_ore".equals(task.kind()))) return false;
        if (task.item().isBlank() || !task.item().startsWith("minecraft:")) return false;
        return !"gather_ore".equals(task.kind()) || task.count() <= 256;
    }

    private static Path checkpointPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("nightfall").resolve("task_resume.json");
    }
}
