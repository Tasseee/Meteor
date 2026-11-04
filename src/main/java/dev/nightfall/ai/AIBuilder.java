package dev.nightfall.ai;

import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;

public final class AIBuilder {
    private static final double REACH_SQUARED = 20.25;
    private static final int CONFIRMATION_TIMEOUT_TICKS = 30;
    private static final int MAX_PLACEMENT_RETRIES = 2;

    private enum Blueprint {
        STARTER_SHELTER("starter_shelter", "4x4-Schutzhaus", 4, 54),
        LARGE_HOUSE("large_house", "Großes Haus", 6, 130),
        WATCHTOWER("watchtower", "Wachturm", 5, 74),
        CUSTOM("custom", "Eigener Bauplan", 0, 0);

        final String id;
        final String label;
        final int width;
        final int blocks;

        Blueprint(String id, String label, int width, int blocks) {
            this.id = id;
            this.label = label;
            this.width = width;
            this.blocks = blocks;
        }

        static Blueprint parse(String id) {
            for (Blueprint value : values()) if (value.id.equals(id)) return value;
            throw new IllegalArgumentException("Unbekannte Bauvorlage. Verfügbar: starter_shelter, large_house, watchtower.");
        }
    }

    public record RelativeBlock(int x, int y, int z, String materialId) {
        public RelativeBlock(int x, int y, int z) { this(x, y, z, null); }
    }

    private static final List<BlockPos> PLAN = new ArrayList<>();
    private static final Map<BlockPos, BlockItem> PLAN_MATERIALS = new LinkedHashMap<>();
    private static final List<RelativeBlock> CUSTOM_OFFSETS = new ArrayList<>();
    private static BlockItem material;
    private static Blueprint blueprint;
    private static Identifier dimension;
    private static BlockPos pendingTarget;
    private static BlockItem pendingMaterial;
    private static BlockPos preparedPlayerPos;
    private static Direction preparedFacing;
    private static int index;
    private static int delay;
    private static int pendingTicks;
    private static int placementRetries;
    private static int originalSlot = -1;
    private static boolean prepared;
    private static boolean running;
    private static boolean waitingForReach;
    private static boolean pausedByUser;
    private static String materialName = "";
    private static String blueprintName = "";
    private static String status = "Builder bereit";
    private static int preparedMaxBlocks = 200;
    private static int preparedMaxHorizontal = 15;
    private static int preparedMaxY = 10;

    private AIBuilder() {}

    public static String prepareStarterShelter() { return prepareStructure("starter_shelter"); }

    public static String prepareStructure(String structure) {
        return prepareStructure(structure, null, null);
    }

    public static String prepareStructure(String structure, String preferredMaterialId) {
        return prepareStructure(structure, null, preferredMaterialId);
    }

    private static String prepareStructure(String structure, BlockItem preferredMaterial) {
        return prepareStructure(structure, preferredMaterial, null);
    }

    private static String prepareStructure(String structure, BlockItem preferredMaterial, String preferredMaterialId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (running) throw new IllegalArgumentException("Nightfall baut bereits. Stoppe den aktuellen Bau zuerst.");
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null)
            throw new IllegalArgumentException("Du musst dich zuerst in einer verbundenen Welt befinden.");

        Blueprint selectedBlueprint = Blueprint.parse(structure);
        if (selectedBlueprint == Blueprint.CUSTOM)
            throw new IllegalArgumentException("Eigene Pläne müssen über build_custom_structure validiert werden.");
        Map<BlockItem, Integer> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!(stack.getItem() instanceof BlockItem item)) continue;
            BlockState state = item.getBlock().getDefaultState();
            BlockPos sample = mc.player.getBlockPos();
            if (item.getBlock() instanceof FallingBlock || state.getCollisionShape(mc.world, sample).isEmpty()
                    || !state.isFullCube(mc.world, sample)) continue;
            counts.merge(item, stack.getCount(), Integer::sum);
        }
        BlockItem selected = preferredMaterial;
        if (selected == null && preferredMaterialId != null && !preferredMaterialId.isBlank())
            selected = resolveMaterial(counts, preferredMaterialId);
        int available = selected == null ? 0 : counts.getOrDefault(selected, 0);
        if (selected == null) {
            for (Map.Entry<BlockItem, Integer> entry : counts.entrySet()) {
                if (entry.getValue() > available) {
                    selected = entry.getKey();
                    available = entry.getValue();
                }
            }
        }
        boolean creative = playerIsCreative(mc);
        if (selected == null || available < (creative ? 1 : selectedBlueprint.blocks))
            throw new IllegalArgumentException(creative
                    ? "Lege mindestens einen passenden Full-Block in deine Hotbar."
                    : "Für " + selectedBlueprint.label + " brauche ich " + selectedBlueprint.blocks
                    + " volle Blöcke derselben Sorte in deiner Hotbar. Gefunden: " + available + ".");

        ClientPlayerEntity player = mc.player;
        Direction forward = player.getHorizontalFacing();
        Direction right = forward.rotateYClockwise();
        int width = selectedBlueprint.width;
        BlockPos origin = player.getBlockPos().offset(forward, 1).offset(right, -(width / 2));
        int baseY = player.getBlockPos().getY();
        List<BlockPos> nextPlan = new ArrayList<>(selectedBlueprint.blocks);

        for (int z = 0; z < width; z++) {
            for (int x = 0; x < width; x++) nextPlan.add(at(origin, right, forward, x, z, baseY));
        }

        if (selectedBlueprint == Blueprint.STARTER_SHELTER || selectedBlueprint == Blueprint.LARGE_HOUSE) {
            int wallHeight = selectedBlueprint == Blueprint.STARTER_SHELTER ? 2 : 3;
            for (int layer = 1; layer <= wallHeight; layer++) {
                for (int z = 0; z < width; z++) {
                    for (int x = 0; x < width; x++) {
                        boolean edge = x == 0 || x == width - 1 || z == 0 || z == width - 1;
                        boolean doorway = z == 0 && x == width / 2 - 1 && layer <= 2;
                        if (edge && !doorway) nextPlan.add(at(origin, right, forward, x, z, baseY + layer));
                    }
                }
            }
        } else {
            for (int y = 1; y <= 6; y++) {
                for (int z : new int[]{0, width - 1}) {
                    for (int x : new int[]{0, width - 1})
                        nextPlan.add(at(origin, right, forward, x, z, baseY + y));
                }
            }
        }

        List<int[]> roof = new ArrayList<>();
        for (int z = 0; z < width; z++) for (int x = 0; x < width; x++) roof.add(new int[]{x, z});
        roof.sort(Comparator.comparingInt((int[] p) -> Math.min(Math.min(p[0], width - 1 - p[0]),
                Math.min(p[1], width - 1 - p[1]))));
        int roofY = baseY + (selectedBlueprint == Blueprint.WATCHTOWER ? 7
                : selectedBlueprint == Blueprint.LARGE_HOUSE ? 4 : 3);
        for (int[] cell : roof) nextPlan.add(at(origin, right, forward, cell[0], cell[1], roofY));

        if (nextPlan.size() != selectedBlueprint.blocks)
            throw new IllegalStateException("Interner Bauplan ungültig: " + nextPlan.size() + "/" + selectedBlueprint.blocks + ".");

        for (int z = 0; z < width; z++) {
            for (int x = 0; x < width; x++) {
                BlockPos support = at(origin, right, forward, x, z, baseY).down();
                BlockState ground = mc.world.getBlockState(support);
                if (ground.getCollisionShape(mc.world, support).isEmpty() || !ground.isFullCube(mc.world, support))
                    throw new IllegalArgumentException("Der Bauplatz braucht einen durchgehend ebenen, festen Boden.");
            }
        }
        for (BlockPos target : nextPlan) {
            if (target.getY() < mc.world.getBottomY()
                    || target.getY() >= mc.world.getBottomY() + mc.world.getHeight())
                throw new IllegalArgumentException("Der Bauplatz liegt außerhalb der Weltgrenzen.");
            if (!mc.world.getBlockState(target).isReplaceable())
                throw new IllegalArgumentException("Der Bauplatz ist nicht frei bei X=" + target.getX()
                        + ", Y=" + target.getY() + ", Z=" + target.getZ() + ".");
        }

        PLAN.clear();
        PLAN.addAll(nextPlan);
        PLAN_MATERIALS.clear();
        pendingMaterial = null;
        material = selected;
        blueprint = selectedBlueprint;
        blueprintName = selectedBlueprint.label;
        CUSTOM_OFFSETS.clear();
        preparedPlayerPos = player.getBlockPos().toImmutable();
        preparedFacing = forward;
        materialName = selected.getName().getString();
        dimension = mc.world.getRegistryKey().getValue();
        index = 0;
        delay = 0;
        pendingTarget = null;
        pendingTicks = 0;
        placementRetries = 0;
        prepared = true;
        status = "Bauplan geprüft";
        String layout = switch (selectedBlueprint) {
            case STARTER_SHELTER -> "4x4-Boden, zwei hohe Wände, Türöffnung und Dach";
            case LARGE_HOUSE -> "6x6-Boden, drei hohe Wände, Türöffnung und Dach";
            case WATCHTOWER -> "5x5-Plattform, vier sechs Blöcke hohe Eckpfeiler und Dachplattform";
            case CUSTOM -> throw new IllegalStateException("Eigener Bauplan darf nicht über Vorlagen-Planung laufen.");
        };
        return "Bauplan geprüft: " + selectedBlueprint.label + " – " + layout + ". Material: "
                + materialName + " × " + (creative ? "unbegrenzt (Kreativmodus)" : selectedBlueprint.blocks + " (Hotbar: " + available + ")") + ". "
                + "Fläche und Boden sind geprüft. Jede Platzierung wird erst nach Server-Weltupdate gezählt; bei fehlender Reichweite pausiere ich."
                + fixedLayoutPreview(nextPlan, origin, right, forward, baseY, selected);
    }

    public static String prepareCustomStructure(String name, String materialId, List<RelativeBlock> offsets) {
        return prepareCustomStructure(name, null, materialId, offsets, 200, 15, 10);
    }

    /** Prepares the safe, automatically placeable subset of a locally imported schematic. */
    public static String prepareImportedStructure(String name, List<RelativeBlock> offsets) {
        return prepareCustomStructure(name, null, null, offsets, 5_000, 63, 63);
    }

    private static String prepareCustomStructure(String name, BlockItem preferredMaterial, List<RelativeBlock> offsets) {
        return prepareCustomStructure(name, preferredMaterial, null, offsets, 200, 15, 10);
    }

    private static String prepareCustomStructure(String name, BlockItem preferredMaterial,
                                                 String preferredMaterialId, List<RelativeBlock> offsets,
                                                 int maxBlocks, int maxHorizontal, int maxVerticalLimit) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (running) throw new IllegalArgumentException("Nightfall baut bereits. Stoppe den aktuellen Bau zuerst.");
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null)
            throw new IllegalArgumentException("Du musst dich zuerst in einer verbundenen Welt befinden.");
        if (name == null || name.isBlank() || !name.matches("[\\p{L}0-9 _.-]{1,48}"))
            throw new IllegalArgumentException("Der Bauplanname ist ungültig.");
        if (maxBlocks < 1 || maxHorizontal < 1 || maxVerticalLimit < 1
                || offsets == null || offsets.isEmpty() || offsets.size() > maxBlocks)
            throw new IllegalArgumentException("Der Bauplan überschreitet das sichere Blocklimit von " + maxBlocks + ".");

        Set<String> uniqueCoordinates = new HashSet<>();
        int maxX = 0, maxY = 0, maxZ = 0;
        for (RelativeBlock block : offsets) {
            if (block == null || block.x() < 0 || block.x() > maxHorizontal
                    || block.y() < 0 || block.y() > maxVerticalLimit
                    || block.z() < 0 || block.z() > maxHorizontal)
                throw new IllegalArgumentException("Der Bauplan überschreitet das erlaubte Raster (X/Z 0–"
                        + maxHorizontal + ", Y 0–" + maxVerticalLimit + ").");
            String coordinateKey = block.x() + ":" + block.y() + ":" + block.z();
            if (!uniqueCoordinates.add(coordinateKey))
                throw new IllegalArgumentException("Der Bauplan enthält doppelte Blockkoordinaten – auch mit unterschiedlichen Materialien sind doppelte Positionen nicht erlaubt.");
            maxX = Math.max(maxX, block.x());
            maxY = Math.max(maxY, block.y());
            maxZ = Math.max(maxZ, block.z());
        }

        Map<BlockItem, Integer> counts = collectHotbarMaterials(mc);
        BlockItem selected = preferredMaterial;
        if (selected == null && preferredMaterialId != null && !preferredMaterialId.isBlank())
            selected = resolveMaterial(counts, preferredMaterialId);
        if (selected == null) selected = mostAvailable(counts);

        Map<BlockItem, Integer> requiredCounts = new LinkedHashMap<>();
        Map<RelativeBlock, BlockItem> offsetMaterials = new LinkedHashMap<>();
        for (RelativeBlock offset : offsets) {
            BlockItem blockMaterial = offset.materialId() == null || offset.materialId().isBlank()
                    ? selected : resolveMaterial(counts, offset.materialId());
            if (blockMaterial == null)
                throw new IllegalArgumentException("Für jeden Block ohne eigenes Material muss ein passender Full-Block-Typ in der Hotbar liegen.");
            offsetMaterials.put(offset, blockMaterial);
            requiredCounts.merge(blockMaterial, 1, Integer::sum);
        }
        boolean creative = playerIsCreative(mc);
        for (Map.Entry<BlockItem, Integer> requirement : requiredCounts.entrySet()) {
            int found = counts.getOrDefault(requirement.getKey(), 0);
            if (found < (creative ? 1 : requirement.getValue()))
                throw new IllegalArgumentException(creative
                        ? "Lege mindestens einen Block von " + requirement.getKey().getName().getString() + " in die Hotbar."
                        : "Für " + requirement.getKey().getName().getString() + " brauche ich " + requirement.getValue()
                        + " Blöcke, in der Hotbar liegen aber nur " + found + ".");
        }
        if (selected == null) selected = offsetMaterials.values().iterator().next();

        ClientPlayerEntity player = mc.player;
        Direction forward = player.getHorizontalFacing();
        Direction right = forward.rotateYClockwise();
        BlockPos origin = player.getBlockPos().offset(forward, 1).offset(right, -((maxX + 1) / 2));
        List<BlockPos> targets = new ArrayList<>(offsets.size());
        Set<BlockPos> targetSet = new HashSet<>();
        Map<BlockPos, BlockItem> targetMaterials = new LinkedHashMap<>();
        for (RelativeBlock offset : offsets) {
            BlockPos target = origin.add(right.getOffsetX() * offset.x() + forward.getOffsetX() * offset.z(),
                    offset.y(), right.getOffsetZ() * offset.x() + forward.getOffsetZ() * offset.z());
            if (target.getY() < mc.world.getBottomY()
                    || target.getY() >= mc.world.getBottomY() + mc.world.getHeight())
                throw new IllegalArgumentException("Der Bauplan liegt außerhalb der Weltgrenzen.");
            if (!mc.world.getBlockState(target).isReplaceable())
                throw new IllegalArgumentException("Der Bauplatz ist nicht frei bei X=" + target.getX()
                        + ", Y=" + target.getY() + ", Z=" + target.getZ() + ".");
            targets.add(target);
            if (!targetSet.add(target))
                throw new IllegalArgumentException("Der Bauplan enthält doppelte Zielpositionen.");
            targetMaterials.put(target, offsetMaterials.get(offset));
        }

        // Breadth-first ordering starts at blocks attached to existing solid world blocks.
        // Every other block must be face-connected to one of these anchors.
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos target : targets) {
            for (Direction face : Direction.values()) {
                BlockPos neighbor = target.offset(face);
                if (targetSet.contains(neighbor)) continue;
                BlockState support = mc.world.getBlockState(neighbor);
                if (!support.getCollisionShape(mc.world, neighbor).isEmpty()) {
                    if (visited.add(target)) queue.addLast(target);
                    break;
                }
            }
        }
        List<BlockPos> ordered = new ArrayList<>(targets.size());
        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            ordered.add(current);
            for (Direction face : Direction.values()) {
                BlockPos neighbor = current.offset(face);
                if (targetSet.contains(neighbor) && visited.add(neighbor)) queue.addLast(neighbor);
            }
        }
        if (ordered.size() != targets.size())
            throw new IllegalArgumentException("Der Bauplan hat schwebende oder getrennte Teile. Alle Blöcke müssen über Seitenflächen mit dem Boden oder bestehenden festen Blöcken verbunden sein.");

        PLAN.clear();
        PLAN.addAll(ordered);
        PLAN_MATERIALS.clear();
        for (BlockPos position : ordered) PLAN_MATERIALS.put(position, targetMaterials.get(position));
        pendingMaterial = null;
        CUSTOM_OFFSETS.clear();
        CUSTOM_OFFSETS.addAll(offsets);
        material = selected;
        blueprint = Blueprint.CUSTOM;
        blueprintName = name.trim();
        materialName = describeMaterials(requiredCounts);
        dimension = mc.world.getRegistryKey().getValue();
        preparedPlayerPos = player.getBlockPos().toImmutable();
        preparedFacing = forward;
        index = 0;
        delay = 0;
        pendingTarget = null;
        pendingTicks = 0;
        placementRetries = 0;
        prepared = true;
        preparedMaxBlocks = maxBlocks;
        preparedMaxHorizontal = maxHorizontal;
        preparedMaxY = maxVerticalLimit;
        status = "Eigener Bauplan geprüft";
        return "Eigener Bauplan geprüft: „" + blueprintName + "“, " + offsets.size() + " Blöcke, "
                + (maxX + 1) + " × " + (maxY + 1) + " × " + (maxZ + 1)
                + " Raster, Materialien: " + materialName
                + ". Alle Zielpositionen sind frei und die Teile sind mit dem Boden oder bestehenden Blöcken verbunden."
                + customLayoutPreview(offsets, offsetMaterials, maxX, maxY, maxZ);
    }

    private static String fixedLayoutPreview(List<BlockPos> positions, BlockPos origin,
                                               Direction right, Direction forward, int baseY,
                                               BlockItem blockMaterial) {
        List<RelativeBlock> offsets = new ArrayList<>(positions.size());
        Map<RelativeBlock, BlockItem> materials = new LinkedHashMap<>();
        int maxX = 0, maxY = 0, maxZ = 0;
        for (BlockPos position : positions) {
            int deltaX = position.getX() - origin.getX();
            int deltaZ = position.getZ() - origin.getZ();
            int x = deltaX * right.getOffsetX() + deltaZ * right.getOffsetZ();
            int z = deltaX * forward.getOffsetX() + deltaZ * forward.getOffsetZ();
            int y = position.getY() - baseY;
            RelativeBlock offset = new RelativeBlock(x, y, z);
            offsets.add(offset);
            materials.put(offset, blockMaterial);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        return customLayoutPreview(offsets, materials, maxX, maxY, maxZ);
    }

    private static String customLayoutPreview(List<RelativeBlock> offsets,
                                               Map<RelativeBlock, BlockItem> offsetMaterials,
                                               int maxX, int maxY, int maxZ) {
        Map<BlockItem, Character> palette = new LinkedHashMap<>();
        Map<String, Character> groundPlan = new LinkedHashMap<>();
        Map<Integer, Integer> layerCounts = new LinkedHashMap<>();
        for (RelativeBlock offset : offsets) {
            BlockItem blockMaterial = offsetMaterials.get(offset);
            Character symbol = palette.get(blockMaterial);
            if (symbol == null) {
                symbol = (char) ('A' + palette.size());
                palette.put(blockMaterial, symbol);
            }
            if (offset.y() == 0) groundPlan.put(offset.x() + ":" + offset.z(), symbol);
            layerCounts.merge(offset.y(), 1, Integer::sum);
        }

        StringBuilder preview = new StringBuilder("\nGrundriss (y=0, vorne = deine Blickrichtung):");
        if (groundPlan.isEmpty()) {
            preview.append("\n(keine Blöcke auf y=0; der Plan ist an vorhandenen Blöcken verankert)");
        } else {
            for (int z = 0; z <= maxZ; z++) {
                preview.append("\n");
                for (int x = 0; x <= maxX; x++)
                    preview.append(groundPlan.getOrDefault(x + ":" + z, '.'));
            }
        }
        preview.append("\nSchichten: ");
        List<String> layers = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : layerCounts.entrySet())
            layers.add("y" + entry.getKey() + "=" + entry.getValue());
        preview.append(String.join(", ", layers));
        preview.append("\nLegende: ");
        List<String> legend = new ArrayList<>();
        for (Map.Entry<BlockItem, Character> entry : palette.entrySet())
            legend.add(entry.getValue() + "=" + entry.getKey().getName().getString());
        preview.append(String.join(", ", legend));
        return preview.toString();
    }

    private static boolean playerIsCreative(MinecraftClient mc) {
        return mc.player != null && mc.player.getAbilities().creativeMode;
    }

    private static Map<BlockItem, Integer> collectHotbarMaterials(MinecraftClient mc) {
        Map<BlockItem, Integer> counts = new LinkedHashMap<>();
        BlockPos sample = mc.player.getBlockPos();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!(stack.getItem() instanceof BlockItem item)) continue;
            BlockState state = item.getBlock().getDefaultState();
            if (item.getBlock() instanceof FallingBlock || state.getCollisionShape(mc.world, sample).isEmpty()
                    || !state.isFullCube(mc.world, sample)) continue;
            counts.merge(item, stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static BlockItem resolveMaterial(Map<BlockItem, Integer> counts, String requestedId) {
        String requested = requestedId.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        String displayQuery = requestedId.trim().toLowerCase(Locale.ROOT);
        BlockItem partialMatch = null;
        for (BlockItem item : counts.keySet()) {
            Identifier itemId = Registries.ITEM.getId(item);
            String displayName = item.getName().getString().toLowerCase(Locale.ROOT);
            if (itemId.toString().equals(requested) || itemId.getPath().equals(requested)
                    || displayName.equals(displayQuery)) return item;
            if (displayName.contains(displayQuery)) {
                if (partialMatch != null && partialMatch != item)
                    throw new IllegalArgumentException("Das Material „" + requestedId
                            + "“ ist in deiner Hotbar mehrdeutig. Bitte nenne die genaue Minecraft-Item-ID.");
                partialMatch = item;
            }
        }
        if (partialMatch != null) return partialMatch;
        throw new IllegalArgumentException("Das gewünschte Baumaterial " + requestedId
                + " befindet sich nicht als voller Block in deiner Hotbar.");
    }

    private static BlockItem mostAvailable(Map<BlockItem, Integer> counts) {
        BlockItem selected = null;
        int available = 0;
        for (Map.Entry<BlockItem, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > available) {
                selected = entry.getKey();
                available = entry.getValue();
            }
        }
        return selected;
    }

    private static String describeMaterials(Map<BlockItem, Integer> requirements) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<BlockItem, Integer> entry : requirements.entrySet())
            parts.add(entry.getKey().getName().getString() + " × " + entry.getValue());
        return String.join(", ", parts);
    }

    private static BlockPos at(BlockPos origin, Direction right, Direction forward, int x, int z, int y) {
        return origin.add(right.getOffsetX() * x + forward.getOffsetX() * z,
                y - origin.getY(), right.getOffsetZ() * x + forward.getOffsetZ() * z);
    }

    public static String startStarterShelter() { return startPreparedStructure(); }

    public static String startPreparedStructure() {
        if (!prepared || blueprint == null || preparedPlayerPos == null || preparedFacing == null)
            throw new IllegalArgumentException("Der Bauplan ist abgelaufen. Bitte fordere ihn erneut an.");
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null
                || !mc.player.getBlockPos().equals(preparedPlayerPos)
                || mc.player.getHorizontalFacing() != preparedFacing
                || !mc.world.getRegistryKey().getValue().equals(dimension)) {
            throw new IllegalArgumentException("Du hast dich seit der Vorschau bewegt oder die Welt gewechselt. Bitte erstelle den Bauplan an deinem neuen Standort erneut.");
        }
        String id = blueprint.id;
        BlockItem approvedMaterial = material;
        if (blueprint == Blueprint.CUSTOM) {
            prepareCustomStructure(blueprintName, approvedMaterial, null, List.copyOf(CUSTOM_OFFSETS),
                    preparedMaxBlocks, preparedMaxHorizontal, preparedMaxY);
        } else {
            prepareStructure(id, approvedMaterial, null);
        }
        originalSlot = mc.player.getInventory().getSelectedSlot();
        index = 0;
        delay = 0;
        pendingTarget = null;
        pendingTicks = 0;
        placementRetries = 0;
        waitingForReach = false;
        pausedByUser = false;
        running = true;
        status = "Bau gestartet";
        notifyPlayer("Bau gestartet: " + blueprintName + " · 0/" + PLAN.size() + ".");
        String materialSummary = blueprint == Blueprint.CUSTOM
                ? "Materialien: " + materialName
                : "Material: " + materialName + " × " + PLAN.size();
        return "Bau gestartet: " + blueprintName + ". " + materialSummary
                + ". Ich zähle einen Block erst nach bestätigtem Welt-Update.";
    }

    public static void tick() {
        if (!running) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null
                || !mc.world.getRegistryKey().getValue().equals(dimension)) {
            stopInternal("Bau gestoppt: Welt oder Verbindung hat sich geändert.", true);
            return;
        }
        ClientPlayerEntity player = mc.player;
        if (delay > 0) { delay--; return; }

        if (pendingTarget != null) {
            BlockState pendingState = mc.world.getBlockState(pendingTarget);
            if (pendingMaterial != null && pendingState.isOf(pendingMaterial.getBlock())) {
                pendingMaterial = null;
                index++;
                pendingTarget = null;
                pendingTicks = 0;
                placementRetries = 0;
                progressNotice();
                delay = 1;
                return;
            }
            if (!pendingState.isReplaceable()) {
                stopInternal("Bau gestoppt: Der Server meldet an der Zielposition einen anderen Block.", true);
                return;
            }
            if (++pendingTicks < CONFIRMATION_TIMEOUT_TICKS) { delay = 1; return; }
            if (placementRetries >= MAX_PLACEMENT_RETRIES) {
                stopInternal("Bau gestoppt: Der Server hat die Platzierung wiederholt nicht bestätigt.", true);
                return;
            }
            placementRetries++;
            pendingTarget = null;
            pendingMaterial = null;
            pendingTicks = 0;
            notifyPlayer("Platzierung nicht bestätigt – ich versuche es erneut (" + placementRetries + "/" + MAX_PLACEMENT_RETRIES + ").");
            delay = 1;
            return;
        }

        if (pausedByUser) return;

        if (index >= PLAN.size()) {
            stopInternal("Bau abgeschlossen: " + blueprintName + " wurde vom Server bestätigt.", true);
            return;
        }
        BlockPos target = PLAN.get(index);
        BlockState present = mc.world.getBlockState(target);
        BlockItem targetMaterial = PLAN_MATERIALS.getOrDefault(target, material);
        if (targetMaterial == null) {
            stopInternal("Bau gestoppt: Für den nächsten Block ist kein Baumaterial zugeordnet.", true);
            return;
        }
        if (present.isOf(targetMaterial.getBlock())) { index++; progressNotice(); return; }
        if (!present.isReplaceable()) {
            stopInternal("Bau gestoppt: Bei X=" + target.getX() + ", Y=" + target.getY() + ", Z=" + target.getZ()
                    + " ist inzwischen ein anderer Block im Weg.", true);
            return;
        }

        int slot = findMaterialSlot(player, targetMaterial);
        if (slot < 0) { stopInternal("Bau gestoppt: Das Baumaterial fehlt in der Hotbar.", true); return; }
        if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
            delay = 1;
            return;
        }
        BlockHitResult hit = findReachableSupport(mc, player, target);
        if (hit == null) {
            if (!waitingForReach) {
                waitingForReach = true;
                status = "Warte auf Reichweite · " + index + "/" + PLAN.size();
                notifyPlayer("Geh bitte näher an den Bauplatz. Ich setze den Bau automatisch fort, sobald der nächste Block erreichbar ist.");
            }
            return;
        }

        waitingForReach = false;
        status = "Warte auf Serverbestätigung " + (index + 1) + "/" + PLAN.size();
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.swingHand(Hand.MAIN_HAND);
        pendingTarget = target.toImmutable();
        pendingMaterial = targetMaterial;
        pendingTicks = 0;
        delay = 2;
    }

    private static BlockHitResult findReachableSupport(MinecraftClient mc, ClientPlayerEntity player, BlockPos target) {
        for (Direction face : Direction.values()) {
            BlockPos support = target.offset(face.getOpposite());
            if (mc.world.getBlockState(support).getCollisionShape(mc.world, support).isEmpty()) continue;
            Vec3d hit = support.toCenterPos().add(face.getOffsetX() * 0.5,
                    face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (player.getEyePos().squaredDistanceTo(hit) <= REACH_SQUARED)
                return new BlockHitResult(hit, face, support, false);
        }
        return null;
    }

    private static int findMaterialSlot(ClientPlayerEntity player, BlockItem expectedMaterial) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.isOf(expectedMaterial)) return slot;
        }
        return -1;
    }

    private static void progressNotice() {
        if (index == 1 || index % 10 == 0 || index == PLAN.size()) {
            status = "Baue " + index + "/" + PLAN.size();
            notifyPlayer("Baufortschritt: " + index + "/" + PLAN.size() + " bestätigte Blöcke.");
        }
    }

    private static void notifyPlayer(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Nightfall] " + message), false);
    }

    public static boolean isRunning() { return running; }
    public static String statusLabel() { return running ? "Builder · " + status : status; }

    public static String describeStatus() {
        if (running) {
            int total = PLAN.size();
            int percent = total == 0 ? 0 : Math.min(100, index * 100 / total);
            String phase = pendingTarget != null ? "warte auf Serverbestätigung"
                    : pausedByUser ? "vom Spieler pausiert"
                    : waitingForReach ? "pausiert: nächster Block außerhalb der Reichweite"
                    : "läuft";
            return "Builder aktiv: " + blueprintName + ". Bestätigte Blöcke: " + index + "/" + total
                    + " (" + percent + "%). Phase: " + phase + ". Materialien: " + materialName
                    + ". Ein noch nicht bestätigter Block wird nicht zum Fortschritt gezählt.";
        }
        if (prepared) {
            return "Bauplan wartet auf Bestätigung: " + blueprintName + ", " + PLAN.size()
                    + " Blöcke. Materialien: " + materialName + ". Noch wurde kein Block gesetzt.";
        }
        return "Kein Bau läuft. Letzter Builder-Status: " + status;
    }

    public static void cancelPrepared() {
        if (running) return;
        prepared = false;
        PLAN.clear();
        PLAN_MATERIALS.clear();
        material = null;
        pendingMaterial = null;
        blueprint = null;
        blueprintName = "";
        CUSTOM_OFFSETS.clear();
        pendingTarget = null;
        preparedPlayerPos = null;
        preparedFacing = null;
    }

    public static String pause() {
        if (!running) throw new IllegalArgumentException("Aktuell läuft kein Bau, den ich pausieren könnte.");
        if (pausedByUser) return "Der Bau ist bereits pausiert.";
        pausedByUser = true;
        status = "Vom Spieler pausiert · " + index + "/" + PLAN.size();
        notifyPlayer("Bau pausiert. Bereits gesetzte Blöcke bleiben bestehen.");
        return "Bau pausiert bei " + index + "/" + PLAN.size() + " bestätigten Blöcken. Bereits gesetzte Blöcke bleiben bestehen.";
    }

    public static String resume() {
        if (!running) throw new IllegalArgumentException("Es läuft kein pausierter Bau.");
        if (!pausedByUser) throw new IllegalArgumentException("Der Bau wurde nicht manuell pausiert. Bei fehlender Reichweite setzt er automatisch fort, sobald du näher kommst.");
        pausedByUser = false;
        waitingForReach = false;
        status = "Bau fortgesetzt · " + index + "/" + PLAN.size();
        notifyPlayer("Bau fortgesetzt.");
        return "Bau wird ab Block " + (index + 1) + "/" + PLAN.size() + " fortgesetzt.";
    }

    public static String stop() {
        if (!running) {
            cancelPrepared();
            return "Es lief kein Bau. Der vorbereitete Bauplan wurde verworfen.";
        }
        stopInternal("Bau vom Spieler gestoppt. Bereits gesetzte Blöcke bleiben stehen.", true);
        return "Der Bau wurde gestoppt. Bereits gesetzte Blöcke bleiben stehen.";
    }

    public static void onDisconnect() {
        if (running) stopInternal("Bau bei Verbindungsabbruch gestoppt.", false);
        cancelPrepared();
    }

    private static void stopInternal(String message, boolean notify) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (originalSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(originalSlot);
        originalSlot = -1;
        running = false;
        waitingForReach = false;
        pausedByUser = false;
        prepared = false;
        pendingTarget = null;
        pendingMaterial = null;
        pendingTicks = 0;
        placementRetries = 0;
        status = message;
        if (notify) notifyPlayer(message);
        PLAN.clear();
        PLAN_MATERIALS.clear();
        material = null;
        pendingMaterial = null;
        blueprint = null;
        blueprintName = "";
        CUSTOM_OFFSETS.clear();
        preparedPlayerPos = null;
        preparedFacing = null;
    }
}
