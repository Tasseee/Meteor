package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.util.InvUtil;
import dev.nightfall.util.Storage;
import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.state.property.Property;
import net.minecraft.block.BlockState;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Small, deterministic schematic assistant. It reads Nightfall's documented .nfbuild text format,
 * places only blocks close to the player, and deliberately does not claim to provide Baritone pathfinding.
 */
public final class SchematicBuilder extends Module {
    private record Entry(int x, int y, int z, BlockState state) {
        Block block() { return state.getBlock(); }
    }

    private final List<Entry> entries = new ArrayList<>();
    private String schematicName = "(keine)";
    private String lastError = "";
    private BlockPos origin = BlockPos.ORIGIN;
    private int index;
    private int delay;
    private int originalSlot = -1;
    private boolean previewEnabled = true;
    private List<BlockPos> plannedRoute = List.of();
    private String routeMessage = "Route nicht berechnet";
    private boolean followingRoute;
    private int routeCursor;

    public SchematicBuilder() {
        super("SchematicBuilder", "Baut lokale .nfbuild/.schem/.litematic-Vorlagen mit Ghost-Umrissen und lokaler Routenhilfe.", Category.WORLD);
    }

    public static Path schematicDir() {
        Path path = Storage.dir().resolve("schematics");
        try { Files.createDirectories(path); } catch (IOException ignored) { }
        return path;
    }

    public boolean load(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,48}")) {
            lastError = "Name ungueltig (nur A-Z, a-z, 0-9, _ und -)";
            return false;
        }
        Path base = schematicDir().normalize();
        Path file = null;
        for (String ext : List.of(".nfbuild", ".schem", ".litematic")) {
            Path candidate = base.resolve(name + ext).normalize();
            if (candidate.getParent().equals(base) && Files.isRegularFile(candidate)) { file = candidate; break; }
        }
        if (file == null) { lastError = "Vorlage nicht gefunden (.nfbuild, .schem oder .litematic)"; return false; }
        List<Entry> parsed = new ArrayList<>();
        try {
            if (file.getFileName().toString().endsWith(".schem")) {
                parsed.addAll(readSpongeSchematic(file));
            } else if (file.getFileName().toString().endsWith(".litematic")) {
                parsed.addAll(readLitematic(file));
            } else {
            int lineNo = 0;
            for (String raw : Files.readAllLines(file)) {
                lineNo++;
                String line = raw.split("#", 2)[0].trim();
                if (line.isEmpty() || line.startsWith("//")) continue;
                String[] p = line.split("\\s+");
                if (p.length != 4) { lastError = "Zeile " + lineNo + ": erwartet x y z minecraft:block_id"; return false; }
                int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
                if (Math.abs(x) > 128 || Math.abs(y) > 128 || Math.abs(z) > 128) {
                    lastError = "Zeile " + lineNo + ": Koordinatenlimit +/-128 ueberschritten"; return false;
                }
                Identifier id = Identifier.tryParse(p[3]);
                if (id == null || !Registries.BLOCK.containsId(id)) { lastError = "Zeile " + lineNo + ": unbekannter Block " + p[3]; return false; }
                Block block = Registries.BLOCK.get(id);
                if (block == net.minecraft.block.Blocks.AIR || block.asItem() == Items.AIR) {
                    lastError = "Zeile " + lineNo + ": Luft/Block ohne Item kann nicht gebaut werden"; return false;
                }
                parsed.add(new Entry(x, y, z, block.getDefaultState()));
                if (parsed.size() > 32768) { lastError = "Vorlage zu gross (max. 32768 Bloecke)"; return false; }
            }
            }
        } catch (IOException | RuntimeException e) {
            lastError = "Import fehlgeschlagen: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return false;
        }
        if (parsed.isEmpty()) { lastError = "Vorlage ist leer"; return false; }
        entries.clear();
        entries.addAll(parsed);
        schematicName = name;
        index = 0;
        delay = 0;
        lastError = "";
        plannedRoute = List.of();
        routeMessage = "Route nicht berechnet";
        if (mc.player != null) origin = mc.player.getBlockPos();
        return true;
    }

    /** Returns a bounded snapshot of remaining target positions for the world-space ghost renderer. */
    public List<BlockPos> ghostPositions(int limit) {
        if (!previewEnabled || entries.isEmpty() || mc.world == null) return List.of();
        ArrayList<BlockPos> out = new ArrayList<>();
        for (int i = index; i < entries.size() && out.size() < Math.max(0, Math.min(limit, 2048)); i++) {
            Entry e = entries.get(i);
            BlockPos pos = origin.add(e.x, e.y, e.z);
            if (!mc.world.getBlockState(pos).isOf(e.block())) out.add(pos);
        }
        return List.copyOf(out);
    }

    private static List<Entry> readSpongeSchematic(Path file) throws IOException {
        NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.of(64L * 1024 * 1024));
        NbtCompound data = root.contains("Schematic", NbtElement.COMPOUND_TYPE) ? root.getCompound("Schematic") : root;
        int width = Short.toUnsignedInt(data.getShort("Width"));
        int height = Short.toUnsignedInt(data.getShort("Height"));
        int length = Short.toUnsignedInt(data.getShort("Length"));
        validateDimensions(width, height, length);
        NbtCompound paletteTag = data.getCompound("Palette");
        Map<Integer, BlockState> palette = new HashMap<>();
        for (String key : paletteTag.getKeys()) {
            String blockName = key;
            int propsStart = key.indexOf('[');
            if (propsStart >= 0) blockName = key.substring(0, propsStart);
            Identifier id = Identifier.tryParse(blockName);
            if (id == null || !Registries.BLOCK.containsId(id)) continue;
            BlockState state = Registries.BLOCK.get(id).getDefaultState();
            if (propsStart >= 0 && key.endsWith("]")) {
                String props = key.substring(propsStart + 1, key.length() - 1);
                for (String pair : props.split(",")) {
                    String[] kv = pair.split("=", 2);
                    if (kv.length == 2) state = applyProperty(state, kv[0].trim(), kv[1].trim());
                }
            }
            // Sponge palette ids map to states; keep the state in a side table below.
            palette.put(paletteTag.getInt(key), state);
        }
        byte[] packed = data.getByteArray("BlockData");
        ArrayList<Entry> out = new ArrayList<>();
        int[] cursor = {0};
        int total = width * height * length;
        for (int i = 0; i < total; i++) {
            int paletteIndex = readVarInt(packed, cursor);
            BlockState state = palette.get(paletteIndex);
            if (state == null || state.getBlock() == net.minecraft.block.Blocks.AIR || state.getBlock().asItem() == Items.AIR) continue;
            int x = i % width;
            int z = (i / width) % length;
            int y = i / (width * length);
            out.add(new Entry(x, y, z, state));
            if (out.size() > 32768) throw new IOException("Vorlage zu gross (max. 32768 Bloecke)");
        }
        if (out.isEmpty()) throw new IOException("Schematic enthaelt keine baubaren Bloecke");
        return out;
    }

    private static List<Entry> readLitematic(Path file) throws IOException {
        NbtCompound root = NbtIo.readCompressed(file, NbtSizeTracker.of(64L * 1024 * 1024));
        NbtCompound regions = root.getCompound("Regions");
        ArrayList<Entry> out = new ArrayList<>();
        for (String regionName : regions.getKeys()) {
            NbtCompound region = regions.getCompound(regionName);
            NbtCompound sizeTag = region.getCompound("Size");
            int sx = sizeTag.getInt("x"), sy = sizeTag.getInt("y"), sz = sizeTag.getInt("z");
            int width = Math.abs(sx), height = Math.abs(sy), length = Math.abs(sz);
            validateDimensions(width, height, length);
            NbtList paletteTag = region.getList("BlockStatePalette", NbtElement.COMPOUND_TYPE);
            ArrayList<BlockState> palette = new ArrayList<>();
            for (int p = 0; p < paletteTag.size(); p++) {
                NbtCompound stateTag = paletteTag.getCompound(p);
                Identifier id = Identifier.tryParse(stateTag.getString("Name"));
                Block block = id != null && Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id) : net.minecraft.block.Blocks.AIR;
                BlockState state = block.getDefaultState();
                if (stateTag.contains("Properties", NbtElement.COMPOUND_TYPE)) {
                    NbtCompound properties = stateTag.getCompound("Properties");
                    for (String propName : properties.getKeys()) state = applyProperty(state, propName, properties.getString(propName));
                }
                palette.add(state);
            }
            long[] packed = region.getLongArray("BlockStates");
            if (palette.isEmpty() || packed.length == 0) continue;
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            int total = width * height * length;
            int entriesPerLong = 64 / bits;
            long mask = (1L << bits) - 1L;
            NbtCompound posTag = region.getCompound("Position");
            int ox = posTag.getInt("x"), oy = posTag.getInt("y"), oz = posTag.getInt("z");
            for (int i = 0; i < total; i++) {
                int word = i / entriesPerLong;
                int shift = (i % entriesPerLong) * bits;
                if (word >= packed.length) break;
                int paletteIndex = (int) ((packed[word] >>> shift) & mask);
                if (paletteIndex >= palette.size()) continue;
                BlockState state = palette.get(paletteIndex);
                Block block = state.getBlock();
                if (block == net.minecraft.block.Blocks.AIR || block.asItem() == Items.AIR) continue;
                int x = i % width, z = (i / width) % length, y = i / (width * length);
                // Normalize negative region sizes into a positive local coordinate system.
                out.add(new Entry(ox + (sx < 0 ? -x - 1 : x), oy + (sy < 0 ? -y - 1 : y), oz + (sz < 0 ? -z - 1 : z), state));
                if (out.size() > 32768) throw new IOException("Vorlage zu gross (max. 32768 Bloecke)");
            }
        }
        if (out.isEmpty()) throw new IOException("Litematic enthaelt keine baubaren Bloecke");
        // Litematic region positions are absolute offsets; the builder anchors imported structures at player position.
        int minX = out.stream().mapToInt(e -> e.x).min().orElse(0);
        int minY = out.stream().mapToInt(e -> e.y).min().orElse(0);
        int minZ = out.stream().mapToInt(e -> e.z).min().orElse(0);
        ArrayList<Entry> normalized = new ArrayList<>(out.size());
        for (Entry e : out) normalized.add(new Entry(e.x - minX, e.y - minY, e.z - minZ, e.state));
        return normalized;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState applyProperty(BlockState state, String name, String value) {
        for (Property property : state.getProperties()) {
            if (!property.getName().equals(name)) continue;
            var parsed = property.parse(value);
            if (parsed.isPresent()) return state.with(property, (Comparable) parsed.get());
        }
        return state;
    }

    private static void validateDimensions(int width, int height, int length) throws IOException {
        if (width < 1 || height < 1 || length < 1 || width > 128 || height > 128 || length > 128
                || (long) width * height * length > 2_000_000L) throw new IOException("Ungueltige oder zu grosse Schematic-Abmessungen");
    }

    private static int readVarInt(byte[] data, int[] cursor) throws IOException {
        int result = 0, shift = 0;
        while (cursor[0] < data.length && shift < 35) {
            int b = data[cursor[0]++] & 0xff;
            result |= (b & 0x7f) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
        }
        throw new IOException("Ungueltige oder abgeschnittene Sponge BlockData");
    }

    public void stopBuild() {
        setEnabled(false);
        entries.clear();
        index = 0;
        schematicName = "(keine)";
        restoreSlot();
        plannedRoute = List.of();
        routeMessage = "Route nicht berechnet";
        followingRoute = false;
        releaseForward();
    }

    public void setPreviewEnabled(boolean enabled) { previewEnabled = enabled; }
    public boolean isBuildPaused() { return buildPaused; }
    public void setBuildPaused(boolean paused) {
        buildPaused = paused;
        if (paused) releaseForward();
    }
    public boolean isPreviewEnabled() { return previewEnabled; }

    /** Plans a bounded, local ground route for navigation guidance only; it never moves the player. */
    public String planRoute() {
        if (mc.player == null || mc.world == null || entries.isEmpty() || index >= entries.size()) {
            plannedRoute = List.of(); routeMessage = "Keine aktive Vorlage / kein naechster Block"; return routeMessage;
        }
        Entry next = entries.get(index);
        BlockPos target = origin.add(next.x, next.y, next.z);
        BlockPos start = mc.player.getBlockPos();
        int y = start.getY();
        BlockPos goal = new BlockPos(target.getX(), y, target.getZ());
        int radius = 24;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(start); seen.add(start);
        BlockPos found = null;
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        while (!queue.isEmpty() && seen.size() < 6000) {
            BlockPos cur = queue.removeFirst();
            if (Math.abs(cur.getX() - goal.getX()) + Math.abs(cur.getZ() - goal.getZ()) <= 1) { found = cur; break; }
            for (Direction d : dirs) {
                BlockPos n = cur.offset(d);
                if (Math.abs(n.getX() - start.getX()) > radius || Math.abs(n.getZ() - start.getZ()) > radius || seen.contains(n)) continue;
                if (!walkable(n)) continue;
                seen.add(n); parent.put(n, cur); queue.addLast(n);
            }
        }
        if (found == null) { plannedRoute = List.of(); routeMessage = "Keine sichere Bodenroute im 24-Block-Radius gefunden"; return routeMessage; }
        ArrayList<BlockPos> reverse = new ArrayList<>();
        for (BlockPos at = found; at != null && !at.equals(start); at = parent.get(at)) reverse.add(at);
        java.util.Collections.reverse(reverse);
        plannedRoute = List.copyOf(reverse);
        routeCursor = 0;
        routeMessage = "Bodenroute: " + plannedRoute.size() + " Schritte (Follow ist opt-in)";
        return routeMessage;
    }

    private boolean walkable(BlockPos feet) {
        if (mc.world == null) return false;
        BlockPos head = feet.up();
        BlockPos floor = feet.down();
        return mc.world.getBlockState(floor).getCollisionShape(mc.world, floor).isEmpty() == false
                && mc.world.getBlockState(feet).getCollisionShape(mc.world, feet).isEmpty()
                && mc.world.getBlockState(head).getCollisionShape(mc.world, head).isEmpty();
    }

    public String setFollowRoute(boolean enabled) {
        if (!enabled) {
            followingRoute = false;
            releaseForward();
            return "Automatische Routenfolge AUS";
        }
        if (plannedRoute.isEmpty()) return "Zuerst /nf build route ausfuehren";
        if (mc.player == null || mc.world == null) return "Keine aktive Welt";
        routeCursor = Math.min(routeCursor, plannedRoute.size() - 1);
        followingRoute = true;
        return "Automatische Routenfolge AN (nur lokale Bodenroute; jederzeit /nf build follow off)";
    }

    private void releaseForward() {
        try { if (mc.options != null) mc.options.forwardKey.setPressed(false); } catch (RuntimeException ignored) { }
    }

    private void tickRouteFollow() {
        if (!followingRoute || mc.player == null || mc.world == null || mc.options == null) return;
        if (routeCursor >= plannedRoute.size()) {
            followingRoute = false;
            releaseForward();
            routeMessage = "Route abgeschlossen";
            return;
        }
        BlockPos waypoint = plannedRoute.get(routeCursor);
        double dx = waypoint.getX() + 0.5 - mc.player.getX();
        double dz = waypoint.getZ() + 0.5 - mc.player.getZ();
        if (dx * dx + dz * dz < 0.20) {
            routeCursor++;
            if (routeCursor >= plannedRoute.size()) {
                followingRoute = false;
                releaseForward();
                routeMessage = "Route abgeschlossen";
                return;
            }
            waypoint = plannedRoute.get(routeCursor);
            dx = waypoint.getX() + 0.5 - mc.player.getX();
            dz = waypoint.getZ() + 0.5 - mc.player.getZ();
        }
        // Route planner only emits same-height walkable cells; no jumping, digging or swimming.
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        mc.player.setYaw(yaw);
        mc.options.forwardKey.setPressed(true);
    }

    public String status() {
        if (!lastError.isEmpty()) return lastError;
        return schematicName + " · " + index + "/" + entries.size() + (buildPaused ? " · PAUSIERT" : "") + " · Ursprung " + origin.getX() + " " + origin.getY() + " " + origin.getZ();
    }

    @Override protected void onDisable() { followingRoute = false; buildPaused = false; releaseForward(); restoreSlot(); }

    @Override public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || entries.isEmpty()) { releaseForward(); return; }
        if (buildPaused) { releaseForward(); return; }
        tickRouteFollow();
        if (delay > 0) { delay--; return; }
        int examined = 0;
        while (index < entries.size() && examined++ < 64) {
            Entry e = entries.get(index);
            BlockPos target = origin.add(e.x, e.y, e.z);
            if (mc.world.getBlockState(target).isOf(e.block())) { index++; continue; }
            if (!mc.world.getBlockState(target).isReplaceable()) { index++; continue; }
            if (mc.player.getPos().squaredDistanceTo(Vec3d.ofCenter(target)) > 4.5 * 4.5) return;
            int slot = InvUtil.findHotbar(e.block().asItem());
            if (slot < 0) { lastError = "Material fehlt in Hotbar: " + Registries.BLOCK.getId(e.block()); return; }
            if (originalSlot < 0) originalSlot = InvUtil.selected();
            InvUtil.select(slot);
            if (placeAgainstNeighbour(target)) {
                index++;
                delay = 3;
                lastError = "";
            } else {
                lastError = "Kein stuetzender Nachbar fuer " + target.toShortString() + " · naeher herangehen oder Reihenfolge anpassen";
                delay = 10;
                return;
            }
            return;
        }
        if (index >= entries.size()) {
            restoreSlot();
            setEnabled(false);
            if (mc.player != null) mc.player.sendMessage(Text.literal("[Nightfall] Vorlage '" + schematicName + "' abgeschlossen."), false);
        }
    }

    private boolean placeAgainstNeighbour(BlockPos target) {
        for (Direction face : Direction.values()) {
            BlockPos support = target.offset(face.getOpposite());
            if (mc.world.getBlockState(support).getCollisionShape(mc.world, support).isEmpty()) continue;
            Vec3d hit = support.toCenterPos().add(face.getOffsetX() * 0.5, face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            BlockHitResult result = new BlockHitResult(hit, face, support, false);
            var action = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, result);
            if (action.isAccepted()) {
                mc.player.swingHand(Hand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    private void restoreSlot() {
        if (originalSlot >= 0 && originalSlot <= 8 && mc.player != null) InvUtil.select(originalSlot);
        originalSlot = -1;
    }

    @Override public void renderHud(DrawContext ctx) {
        if (entries.isEmpty() || mc.player == null) return;
        int x = 8, y = 110, width = 224;
        int rows = previewEnabled ? Math.min(6, entries.size() - Math.min(index, entries.size())) : 0;
        int height = 28 + rows * 12 + (lastError.isEmpty() ? 0 : 12) + (plannedRoute.isEmpty() ? 0 : 12);
        ctx.fill(x, y, x + width, y + height, 0xD8091018);
        ctx.fill(x, y, x + 2, y + height, 0xFF35E0D0);
        ctx.fill(x + 2, y, x + width, y + 1, 0xFF6941C6);
        String title = "NIGHTFALL  /  BLUEPRINT";
        ctx.drawText(mc.textRenderer, title, x + 8, y + 5, 0xFF35E0D0, true);
        String progress = schematicName + "  " + Math.min(index, entries.size()) + "/" + entries.size() + (buildPaused ? "  PAUSED" : "");
        ctx.drawText(mc.textRenderer, progress, x + 8, y + 16, 0xFFE8E8F0, false);
        int rowY = y + 29;
        if (previewEnabled) {
            for (int i = index, shown = 0; i < entries.size() && shown < 6; i++, shown++) {
                Entry e = entries.get(i);
                BlockPos target = origin.add(e.x, e.y, e.z);
                String line = (i == index ? "> " : "  ") + target.toShortString() + "  " + Registries.BLOCK.getId(e.block()).getPath();
                ctx.drawText(mc.textRenderer, line, x + 8, rowY, i == index ? 0xFF35E0D0 : 0xFFB9B9C9, false);
                rowY += 12;
            }
        }
        if (!plannedRoute.isEmpty()) {
            ctx.drawText(mc.textRenderer, "Route: " + plannedRoute.size() + " Schritte (manuell folgen)", x + 8, rowY, 0xFFB8A0FF, false);
            rowY += 12;
        }
        if (!lastError.isEmpty()) ctx.drawText(mc.textRenderer, lastError, x + 8, rowY, 0xFFFFC857, false);
    }
}
