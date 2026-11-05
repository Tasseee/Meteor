package dev.nightfall.ai;

import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import dev.nightfall.mixin.BlockItemPlacementAccessor;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.fabricmc.loader.api.FabricLoader;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

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
    private static final int MATERIAL_SWAP_PENDING = -2;

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

    public record RelativeBlock(int x, int y, int z, String materialId, BlockState state) {
        public RelativeBlock(int x, int y, int z, String materialId) { this(x, y, z, materialId, null); }
        public RelativeBlock(int x, int y, int z) { this(x, y, z, null, null); }
    }

    private record PlacementCandidate(BlockHitResult hit, float yaw, float pitch) { }

    private static final List<BlockPos> PLAN = new ArrayList<>();
    private static final Map<BlockPos, BlockItem> PLAN_MATERIALS = new LinkedHashMap<>();
    private static final Map<BlockPos, BlockState> PLAN_STATES = new LinkedHashMap<>();
    private static final List<RelativeBlock> CUSTOM_OFFSETS = new ArrayList<>();
    private static final List<RelativeBlock> CUSTOM_CLEAR_OFFSETS = new ArrayList<>();
    private static final List<BlockPos> EXCAVATION_PLAN = new ArrayList<>();
    private static final Map<BlockPos, Identifier> EXCAVATION_EXPECTED_BLOCKS = new LinkedHashMap<>();
    private static int excavationIndex;
    private static int excavationRetries;
    private static BlockPos pendingExcavationTarget;
    private static boolean excavationMinePending;
    private static boolean excavationNavigationPending;
    private static int excavationApproachAttempts;
    private static boolean excavationWaitingNotice;
    private static final int MAX_EXCAVATION_BLOCKS = 20_000;
    private static BlockItem material;
    private static Blueprint blueprint;
    private static Identifier dimension;
    private static BlockPos pendingTarget;
    private static BlockItem pendingMaterial;
    private static BlockState pendingExpectedState;
    private static BlockHitResult queuedPlacementHit;
    private static BlockPos queuedPlacementTarget;
    private static BlockItem queuedPlacementMaterial;
    private static BlockState queuedPlacementState;
    private static float queuedPlacementYaw;
    private static float queuedPlacementPitch;
    private static float originalYaw;
    private static float originalPitch;
    private static boolean absoluteSchematicOrientation;
    private static BlockItem pendingInventoryMaterial;
    private static int pendingInventoryHotbarSlot = -1;
    private static int pendingInventorySwapTicks;
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
    private static boolean restoredCheckpoint;
    private static boolean preserveCheckpointOnStop;
    private static boolean keepCheckpointDuringCancel;
    private static boolean checkpointLoadAttempted;
    private static String savedServerKey = "";

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
        for (int slot = 0; slot < 36; slot++) {
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
                    ? "Lege mindestens einen passenden Full-Block in dein Inventar."
                    : "Für " + selectedBlueprint.label + " brauche ich " + selectedBlueprint.blocks
                    + " volle Blöcke derselben Sorte in deinem Inventar. Gefunden: " + available + ".");

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
        PLAN_STATES.clear();
        EXCAVATION_PLAN.clear();
        EXCAVATION_EXPECTED_BLOCKS.clear();
        excavationIndex = 0;
        excavationRetries = 0;
        for (BlockPos target : nextPlan) PLAN_STATES.put(target, selected.getBlock().getDefaultState());
        pendingMaterial = null;
        pendingExpectedState = null;
        queuedPlacementHit = null;
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
        saveCheckpoint();
        return "Bauplan geprüft: " + selectedBlueprint.label + " – " + layout + ". Material: "
                + materialName + " × " + (creative ? "unbegrenzt (Kreativmodus)" : selectedBlueprint.blocks + " (Inventar: " + available + ")") + ". "
                + "Fläche und Boden sind geprüft. Jede Platzierung wird erst nach Server-Weltupdate gezählt; bei fehlender Reichweite pausiere ich."
        saveCheckpoint();
                + fixedLayoutPreview(nextPlan, origin, right, forward, baseY, selected);
    }

    public static String prepareCustomStructure(String name, String materialId, List<RelativeBlock> offsets) {
        return prepareCustomStructure(name, null, materialId, offsets, List.of(), 200, 15, 10);
    }

    /** Prepares the safe subset and the air volume that must be cleared for an imported schematic. */
    public static String prepareImportedStructure(String name, List<RelativeBlock> offsets,
                                                  List<RelativeBlock> clearOffsets) {
        return prepareCustomStructure(name, null, null, offsets, clearOffsets, 5_000, 63, 63, true);
    }

    private static String prepareCustomStructure(String name, BlockItem preferredMaterial, List<RelativeBlock> offsets) {
        return prepareCustomStructure(name, preferredMaterial, null, offsets, List.of(), 200, 15, 10, false);
    }

    private static String prepareCustomStructure(String name, BlockItem preferredMaterial,
                                                 String preferredMaterialId, List<RelativeBlock> offsets,
                                                 List<RelativeBlock> clearOffsets,
                                                 int maxBlocks, int maxHorizontal, int maxVerticalLimit) {
        return prepareCustomStructure(name, preferredMaterial, preferredMaterialId, offsets, clearOffsets,
                maxBlocks, maxHorizontal, maxVerticalLimit, false);
    }

    private static String prepareCustomStructure(String name, BlockItem preferredMaterial,
                                                 String preferredMaterialId, List<RelativeBlock> offsets,
                                                 List<RelativeBlock> clearOffsets,
                                                 int maxBlocks, int maxHorizontal, int maxVerticalLimit,
                                                 boolean preserveSchematicOrientation) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (running) throw new IllegalArgumentException("Nightfall baut bereits. Stoppe den aktuellen Bau zuerst.");
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null)
            throw new IllegalArgumentException("Du musst dich zuerst in einer verbundenen Welt befinden.");
        if (name == null || name.isBlank() || !name.matches("[\\p{L}0-9 _.-]{1,48}"))
            throw new IllegalArgumentException("Der Bauplanname ist ungültig.");
        if (maxBlocks < 1 || maxHorizontal < 1 || maxVerticalLimit < 1
                || offsets == null || offsets.isEmpty() || offsets.size() > maxBlocks
                || clearOffsets == null || clearOffsets.size() > 262_144)
            throw new IllegalArgumentException("Der Bauplan oder sein Aushub überschreitet das sichere Volumenlimit.");

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
        Set<String> uniqueClearCoordinates = new HashSet<>();
        for (RelativeBlock clear : clearOffsets) {
            if (clear == null || clear.x() < 0 || clear.x() > maxHorizontal
                    || clear.y() < 0 || clear.y() > maxVerticalLimit
                    || clear.z() < 0 || clear.z() > maxHorizontal)
                throw new IllegalArgumentException("Der Aushub überschreitet das erlaubte Schematic-Raster.");
            String key = clear.x() + ":" + clear.y() + ":" + clear.z();
            if (!uniqueClearCoordinates.add(key) || uniqueCoordinates.contains(key))
                throw new IllegalArgumentException("Der Aushub enthält doppelte Positionen oder überschneidet sich mit Bau-Blöcken.");
            maxX = Math.max(maxX, clear.x());
            maxY = Math.max(maxY, clear.y());
            maxZ = Math.max(maxZ, clear.z());
        }

        Map<BlockItem, Integer> counts = collectInventoryMaterials(mc, false);
        BlockItem selected = preferredMaterial;
        if (selected == null && preferredMaterialId != null && !preferredMaterialId.isBlank())
            selected = resolveMaterial(counts, preferredMaterialId);
        if (selected == null) selected = mostAvailable(counts);

        Map<BlockItem, Integer> requiredCounts = new LinkedHashMap<>();
        Map<RelativeBlock, BlockItem> offsetMaterials = new LinkedHashMap<>();
        Map<RelativeBlock, BlockState> offsetStates = new LinkedHashMap<>();
        for (RelativeBlock offset : offsets) {
            BlockItem blockMaterial = offset.materialId() == null || offset.materialId().isBlank()
                    ? selected : resolveMaterial(counts, offset.materialId());
            if (blockMaterial == null)
                throw new IllegalArgumentException("Für jeden Block ohne eigenes Material muss ein passender Block im Spielerinventar liegen.");
            BlockState targetState = offset.state() == null ? blockMaterial.getBlock().getDefaultState() : offset.state();
            if (!targetState.isOf(blockMaterial.getBlock()))
                throw new IllegalArgumentException("BlockState und Material stimmen an Position "
                        + offset.x() + "/" + offset.y() + "/" + offset.z() + " nicht überein.");
            offsetMaterials.put(offset, blockMaterial);
            offsetStates.put(offset, targetState);
            requiredCounts.merge(blockMaterial, 1, Integer::sum);
        }
        boolean creative = playerIsCreative(mc);
        List<String> shortages = new ArrayList<>();
        for (Map.Entry<BlockItem, Integer> requirement : requiredCounts.entrySet()) {
            int found = counts.getOrDefault(requirement.getKey(), 0);
            int needed = creative ? 1 : requirement.getValue();
            if (found < needed) {
                String itemId = Registries.ITEM.getId(requirement.getKey()).toString();
                shortages.add(itemId + " (" + requirement.getKey().getName().getString()
                        + ": benötigt " + needed + ", vorhanden " + found + ", fehlen " + (needed - found) + ")");
            }
        }
        if (!shortages.isEmpty())
            throw new IllegalArgumentException("Fehlende Baumaterialien: " + String.join("; ", shortages)
                    + ". Nightfall hat die exakten Block-IDs erkannt, aber der Bau startet erst, wenn alle Mengen im Spielerinventar liegen. "
                    + "Unterstützte nächste Schritte: passende Vanilla-Holzstämme mit gather_wood sammeln und freigeschaltete 2×2-Rezepte mit craft_item herstellen. "
                    + "Andere Materialtypen müssen derzeit manuell beschafft werden.");
        if (selected == null) selected = offsetMaterials.values().iterator().next();

        ClientPlayerEntity player = mc.player;
        Direction playerForward = player.getHorizontalFacing();
        Direction forward = preserveSchematicOrientation ? Direction.SOUTH : playerForward;
        Direction right = preserveSchematicOrientation ? Direction.EAST : forward.rotateYClockwise();
        int forwardSpan = playerForward.getAxis() == Direction.Axis.X ? maxX : maxZ;
        BlockPos schematicCenter = player.getBlockPos().offset(playerForward, 1 + (forwardSpan + 1) / 2);
        BlockPos origin = preserveSchematicOrientation
                ? schematicCenter.offset(Direction.WEST, (maxX + 1) / 2)
                    .offset(Direction.NORTH, (maxZ + 1) / 2)
                : player.getBlockPos().offset(forward, 1).offset(right, -((maxX + 1) / 2));
        List<BlockPos> targets = new ArrayList<>(offsets.size());
        Set<BlockPos> targetSet = new HashSet<>();
        Map<BlockPos, BlockItem> targetMaterials = new LinkedHashMap<>();
        Set<BlockPos> excavationSet = new HashSet<>();
        for (RelativeBlock offset : offsets) {
            BlockPos target = origin.add(right.getOffsetX() * offset.x() + forward.getOffsetX() * offset.z(),
                    offset.y(), right.getOffsetZ() * offset.x() + forward.getOffsetZ() * offset.z());
            if (target.getY() < mc.world.getBottomY()
                    || target.getY() >= mc.world.getBottomY() + mc.world.getHeight())
                throw new IllegalArgumentException("Der Bauplan liegt außerhalb der Weltgrenzen.");
            if (!mc.world.isChunkLoaded(target))
                throw new IllegalArgumentException("Schematic-Ziel liegt in einem nicht geladenen Chunk bei " + target.toShortString() + ".");
            BlockState present = mc.world.getBlockState(target);
            BlockState desired = offsetStates.get(offset);
            if (!present.equals(desired) && !present.getFluidState().isEmpty())
                throw new IllegalArgumentException("Im Schematic-Ziel " + target.toShortString()
                        + " liegt Wasser oder Lava. Flüssigkeiten werden derzeit nicht automatisch entfernt.");
            if (!present.equals(desired) && !present.isReplaceable()) {
                if (present.hasBlockEntity() || present.getHardness(mc.world, target) < 0.0f)
                    throw new IllegalArgumentException("Nightfall darf das Hindernis bei " + target.toShortString()
                            + " nicht automatisch entfernen (Block-Entity, Flüssigkeit oder unzerstörbarer Block).");
                excavationSet.add(target.toImmutable());
            }
            targets.add(target);
            if (!targetSet.add(target))
                throw new IllegalArgumentException("Der Bauplan enthält doppelte Zielpositionen.");
            targetMaterials.put(target, offsetMaterials.get(offset));
        }

        // Air cells in the schematic volume are real clearance instructions: remove terrain
        // there before placing the blueprint. Never mine block entities, fluids or bedrock.
        for (RelativeBlock clear : clearOffsets) {
            BlockPos target = origin.add(right.getOffsetX() * clear.x() + forward.getOffsetX() * clear.z(),
                    clear.y(), right.getOffsetZ() * clear.x() + forward.getOffsetZ() * clear.z());
            if (target.getY() < mc.world.getBottomY()
                    || target.getY() >= mc.world.getBottomY() + mc.world.getHeight()
                    || !mc.world.isChunkLoaded(target))
                throw new IllegalArgumentException("Aushub-Ziel liegt außerhalb der Welt oder in einem nicht geladenen Chunk: " + target.toShortString());
            BlockState present = mc.world.getBlockState(target);
            if (present.isAir()) continue;
            if (present.hasBlockEntity() || !present.getFluidState().isEmpty() || present.getHardness(mc.world, target) < 0.0f)
                throw new IllegalArgumentException("Aushub bei " + target.toShortString()
                        + " würde eine Block-Entity, Flüssigkeit oder einen unzerstörbaren Block betreffen. Bitte zuerst manuell sichern/entfernen.");
            excavationSet.add(target.toImmutable());
        }
        if (excavationSet.size() > MAX_EXCAVATION_BLOCKS)
            throw new IllegalArgumentException("Nightfall müsste mehr als " + MAX_EXCAVATION_BLOCKS
                    + " Blöcke ausgraben. Teile die Schematic oder räume einen Teil manuell frei.");
        List<BlockPos> nextExcavation = new ArrayList<>(excavationSet);
        nextExcavation.sort(Comparator.comparingInt(BlockPos::getY).reversed()
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));

        // Breadth-first ordering starts at blocks attached to existing solid world blocks.
        // Every other block must be face-connected to one of these anchors.
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        for (BlockPos target : targets) {
            for (Direction face : Direction.values()) {
                BlockPos neighbor = target.offset(face);
                if (targetSet.contains(neighbor) || excavationSet.contains(neighbor)) continue;
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
        PLAN_STATES.clear();
        EXCAVATION_PLAN.clear();
        EXCAVATION_PLAN.addAll(nextExcavation);
        EXCAVATION_EXPECTED_BLOCKS.clear();
        for (BlockPos target : nextExcavation)
            EXCAVATION_EXPECTED_BLOCKS.put(target, Registries.BLOCK.getId(mc.world.getBlockState(target).getBlock()));
        excavationIndex = 0;
        excavationRetries = 0;
        pendingExcavationTarget = null;
        excavationMinePending = false;
        excavationNavigationPending = false;
        excavationApproachAttempts = 0;
        excavationWaitingNotice = false;
        CUSTOM_CLEAR_OFFSETS.clear();
        CUSTOM_CLEAR_OFFSETS.addAll(clearOffsets);
        preparedFacing = playerForward;
        for (RelativeBlock offset : offsets) {
            BlockPos target = origin.add(right.getOffsetX() * offset.x() + forward.getOffsetX() * offset.z(),
                    offset.y(), right.getOffsetZ() * offset.x() + forward.getOffsetZ() * offset.z());
            PLAN_MATERIALS.put(target, offsetMaterials.get(offset));
            PLAN_STATES.put(target, offsetStates.get(offset));
        }
        pendingMaterial = null;
        CUSTOM_OFFSETS.clear();
        CUSTOM_OFFSETS.addAll(offsets);
        material = selected;
        blueprint = Blueprint.CUSTOM;
        blueprintName = name.trim();
        materialName = describeMaterials(requiredCounts);
        dimension = mc.world.getRegistryKey().getValue();
        preparedPlayerPos = player.getBlockPos().toImmutable();
        preparedFacing = playerForward;
        index = 0;
        delay = 0;
        pendingTarget = null;
        pendingTicks = 0;
        placementRetries = 0;
        prepared = true;
        preparedMaxBlocks = maxBlocks;
        preparedMaxHorizontal = maxHorizontal;
        preparedMaxY = maxVerticalLimit;
        absoluteSchematicOrientation = preserveSchematicOrientation;
        status = "Eigener Bauplan geprüft";
        saveCheckpoint();
        return "Eigener Bauplan geprüft: „" + blueprintName + "“, " + offsets.size() + " Blöcke, "
                + (maxX + 1) + " × " + (maxY + 1) + " × " + (maxZ + 1)
                + " Raster, Materialien: " + materialName
                + ". Aushub: " + nextExcavation.size() + " vorhandene Blöcke werden vor dem Bau entfernt. "
                + "Block-Entities, Flüssigkeiten und unzerstörbare Blöcke bleiben geschützt."
                + " Teile sind mit festen vorhandenen Blöcken oder dem Boden verbunden."
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

    private static Map<BlockItem, Integer> collectInventoryMaterials(MinecraftClient mc) {
        return collectInventoryMaterials(mc, true);
    }

    private static Map<BlockItem, Integer> collectInventoryMaterials(MinecraftClient mc, boolean requireFullCube) {
        Map<BlockItem, Integer> counts = new LinkedHashMap<>();
        BlockPos sample = mc.player.getBlockPos();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!(stack.getItem() instanceof BlockItem item)) continue;
            BlockState state = item.getBlock().getDefaultState();
            if (item.getBlock() instanceof FallingBlock
                    || (requireFullCube && (state.getCollisionShape(mc.world, sample).isEmpty()
                    || !state.isFullCube(mc.world, sample)))) continue;
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
                            + "“ ist in deinem Inventar mehrdeutig. Bitte nenne die genaue Minecraft-Item-ID.");
                partialMatch = item;
            }
        }
        if (partialMatch != null) return partialMatch;

        // Resolve a precise registry ID even when the player currently owns zero stacks of this block.
        Identifier requestedIdentifier = Identifier.tryParse(requested);
        if (requestedIdentifier != null) {
            var registeredItem = Registries.ITEM.get(requestedIdentifier);
            if (registeredItem instanceof BlockItem blockItem
                    && Registries.ITEM.getId(registeredItem).equals(requestedIdentifier))
                return blockItem;
        }
        throw new IllegalArgumentException("Das gewünschte Baumaterial " + requestedId
                + " ist keine bekannte platzierbare Block-Item-ID.");
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
        if (!prepared || blueprint == null || (preparedPlayerPos == null && !restoredCheckpoint) || (!restoredCheckpoint && preparedFacing == null))
            throw new IllegalArgumentException("Der Bauplan ist abgelaufen. Bitte fordere ihn erneut an.");
        if (AIPlayerController.isBusy())
            throw new IllegalArgumentException("Nightfall führt gerade eine andere Bewegung oder Abbauaktion aus. Stoppe sie zuerst.");
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null
                || (!restoredCheckpoint && (!mc.player.getBlockPos().equals(preparedPlayerPos)
                || mc.player.getHorizontalFacing() != preparedFacing))
                || !mc.world.getRegistryKey().getValue().equals(dimension)
                || (restoredCheckpoint && !currentServerKey(mc).equals(savedServerKey))) {
            throw new IllegalArgumentException("Du hast dich seit der Vorschau bewegt oder die Welt gewechselt. Bitte erstelle den Bauplan an deinem neuen Standort erneut.");
        }
        if (restoredCheckpoint) {
            BlockPos next = excavationIndex < EXCAVATION_PLAN.size() ? EXCAVATION_PLAN.get(excavationIndex)
                    : index < PLAN.size() ? PLAN.get(index) : null;
            if (next != null && mc.player.getEyePos().squaredDistanceTo(next.toCenterPos())
                    > mc.player.getBlockInteractionRange() * mc.player.getBlockInteractionRange())
                throw new IllegalArgumentException("Der gespeicherte Bau ist bereit, aber du bist noch zu weit vom nächsten Arbeitsschritt entfernt. Geh bitte zum Bauplatz und sage danach erneut „Bau fortsetzen“.");
        }
        String id = blueprint.id;
        BlockItem approvedMaterial = material;
        if (!restoredCheckpoint) {
            if (blueprint == Blueprint.CUSTOM) {
                prepareCustomStructure(blueprintName, approvedMaterial, null, List.copyOf(CUSTOM_OFFSETS),
                        List.copyOf(CUSTOM_CLEAR_OFFSETS), preparedMaxBlocks, preparedMaxHorizontal, preparedMaxY,
                        absoluteSchematicOrientation);
            } else {
                prepareStructure(id, approvedMaterial, null);
            }
        }
        originalSlot = mc.player.getInventory().getSelectedSlot();
        originalYaw = mc.player.getYaw();
        originalPitch = mc.player.getPitch();
        queuedPlacementHit = null;
        queuedPlacementTarget = null;
        queuedPlacementMaterial = null;
        queuedPlacementState = null;
        pendingExpectedState = null;
        if (!restoredCheckpoint) index = 0;
        delay = 0;
        pendingTarget = null;
        pendingTicks = 0;
        placementRetries = 0;
        waitingForReach = false;
        pausedByUser = false;
        running = true;
        restoredCheckpoint = false;
        status = "Bau gestartet";
        saveCheckpoint();
        notifyPlayer("Bau gestartet: " + blueprintName + " · Aushub " + EXCAVATION_PLAN.size()
                + " · Bau 0/" + PLAN.size() + ".");
        String materialSummary = blueprint == Blueprint.CUSTOM
                ? "Materialien: " + materialName
                : "Material: " + materialName + " × " + PLAN.size();
        return "Bau gestartet: " + blueprintName + ". " + materialSummary
                + ". Zuerst entferne ich " + EXCAVATION_PLAN.size()
                + " Hindernis-/Luftzellen-Blöcke, danach setze ich den Bauplan. Jeder Schritt wird serverseitig geprüft.";
    }

    public static void tick() {
        if (!running) {
            if (!checkpointLoadAttempted) tryRestoreCheckpoint();
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null
                || !mc.world.getRegistryKey().getValue().equals(dimension)) {
            stopInternal("Bau gestoppt: Welt oder Verbindung hat sich geändert.", true);
            return;
        }
        ClientPlayerEntity player = mc.player;
        if (player.currentScreenHandler != player.playerScreenHandler) {
            status = "Warte auf Schließen des Containers";
            return;
        }
        if (delay > 0) { delay--; return; }

        if (pausedByUser) return;

        if (queuedPlacementHit != null) {
            BlockPos target = queuedPlacementTarget;
            if (target == null || queuedPlacementMaterial == null || queuedPlacementState == null
                    || !mc.world.getBlockState(target).isReplaceable()) {
                stopInternal("Bau gestoppt: Die vorbereitete Platzierung ist nicht mehr gültig.", true);
                return;
            }
            player.setYaw(queuedPlacementYaw);
            player.setPitch(queuedPlacementPitch);
            mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, queuedPlacementHit);
            player.swingHand(Hand.MAIN_HAND);
            pendingTarget = target.toImmutable();
            pendingMaterial = queuedPlacementMaterial;
            pendingExpectedState = queuedPlacementState;
            queuedPlacementHit = null;
            queuedPlacementTarget = null;
            queuedPlacementMaterial = null;
            queuedPlacementState = null;
            pendingTicks = 0;
            delay = 2;
            return;
        }

        if (pendingTarget != null) {
            BlockState pendingState = mc.world.getBlockState(pendingTarget);
            if (pendingExpectedState != null && pendingState.equals(pendingExpectedState)) {
                pendingMaterial = null;
                index++;
                saveCheckpoint();
                pendingTarget = null;
                pendingTicks = 0;
                placementRetries = 0;
                progressNotice();
                delay = 1;
                return;
            }
            if (!pendingState.isReplaceable()
                    && (pendingMaterial == null || !pendingState.isOf(pendingMaterial.getBlock()))) {
                stopInternal("Bau gestoppt: Der Server meldet an der Zielposition einen anderen Block. "
                        + "Erwartet: " + (pendingExpectedState == null ? "unbekannt" : pendingExpectedState)
                        + ", erhalten: " + pendingState + ".", true);
                return;
            }
            if (++pendingTicks < CONFIRMATION_TIMEOUT_TICKS) { delay = 1; return; }
            if (pendingMaterial != null && pendingState.isOf(pendingMaterial.getBlock())) {
                stopInternal("Bau gestoppt: Der Block wurde platziert, aber die vom Server bestätigte Ausrichtung/Zustandskombination stimmt nicht. "
                        + "Erwartet: " + pendingExpectedState + ", erhalten: " + pendingState + ".", true);
                return;
            }
            if (placementRetries >= MAX_PLACEMENT_RETRIES) {
                stopInternal("Bau gestoppt: Der Server hat die Platzierung wiederholt nicht bestätigt.", true);
                return;
            }
            placementRetries++;
            pendingTarget = null;
            pendingMaterial = null;
            pendingExpectedState = null;
            pendingTicks = 0;
            notifyPlayer("Platzierung nicht bestätigt – ich versuche es erneut (" + placementRetries + "/" + MAX_PLACEMENT_RETRIES + ").");
            delay = 1;
            return;
        }

        if (pausedByUser) return;

        if (excavationIndex < EXCAVATION_PLAN.size() || excavationMinePending || excavationNavigationPending) {
            tickExcavation(mc, player);
            return;
        }

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
        BlockState expectedState = PLAN_STATES.getOrDefault(target, targetMaterial.getBlock().getDefaultState());
        if (present.equals(expectedState)) { index++; saveCheckpoint(); progressNotice(); return; }
        if (!present.isReplaceable()) {
            stopInternal("Bau gestoppt: Bei X=" + target.getX() + ", Y=" + target.getY() + ", Z=" + target.getZ()
                    + " ist inzwischen ein anderer Block im Weg.", true);
            return;
        }

        int slot = findMaterialSlot(mc, player, targetMaterial);
        if (slot == MATERIAL_SWAP_PENDING) {
            delay = 1;
            status = "Hole " + targetMaterial.getName().getString() + " aus dem Inventar";
            return;
        }
        if (slot < 0) {
            stopInternal("Bau gestoppt: " + targetMaterial.getName().getString()
                    + " ist nicht mehr im Spielerinventar vorhanden.", true);
            return;
        }
        if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
            delay = 1;
            return;
        }
        PlacementCandidate candidate = findPlacementCandidate(mc, player, target, targetMaterial, expectedState);
        if (candidate == null) {
            stopInternal("Bau gestoppt: Für " + expectedState + " wurde mit normaler Minecraft-Platzierung keine sichere Ansetzfläche/Ausrichtung gefunden. "
                    + "Nightfall setzt den Block nicht falsch herum.", true);
            return;
        }

        waitingForReach = false;
        status = "Richte Block aus " + (index + 1) + "/" + PLAN.size();
        queuedPlacementHit = candidate.hit();
        queuedPlacementTarget = target.toImmutable();
        queuedPlacementMaterial = targetMaterial;
        queuedPlacementState = expectedState;
        queuedPlacementYaw = candidate.yaw();
        queuedPlacementPitch = candidate.pitch();
        player.setYaw(candidate.yaw());
        player.setPitch(candidate.pitch());
        delay = 1;
    }

    private static void tickExcavation(MinecraftClient mc, ClientPlayerEntity player) {
        if (AIPlayerController.isBusy()) return;
        if (excavationNavigationPending) excavationNavigationPending = false;

        if (excavationMinePending) {
            excavationMinePending = false;
            BlockPos mined = pendingExcavationTarget;
            pendingExcavationTarget = null;
            BlockState after = mined == null ? null : mc.world.getBlockState(mined);
            if (after != null && after.isAir()) {
                excavationIndex++;
                saveCheckpoint();
                excavationRetries = 0;
                excavationApproachAttempts = 0;
                excavationWaitingNotice = false;
                status = "Ausgraben " + excavationIndex + "/" + EXCAVATION_PLAN.size();
                if (excavationIndex == 1 || excavationIndex % 10 == 0 || excavationIndex == EXCAVATION_PLAN.size())
                    notifyPlayer("Aushub bestätigt: " + excavationIndex + "/" + EXCAVATION_PLAN.size() + " Blöcke.");
                delay = 1;
            } else {
                if (++excavationRetries > MAX_PLACEMENT_RETRIES) {
                    stopInternal("Bau gestoppt: Der Server hat den Aushub bei "
                            + (mined == null ? "unbekannt" : mined.toShortString()) + " nicht bestätigt.", true);
                    return;
                }
                notifyPlayer("Aushub nicht bestätigt – erneuter Versuch " + excavationRetries + "/" + MAX_PLACEMENT_RETRIES + ".");
                delay = 5;
            }
            return;
        }

        if (excavationIndex >= EXCAVATION_PLAN.size()) return;
        BlockPos target = EXCAVATION_PLAN.get(excavationIndex);
        BlockState state = mc.world.getBlockState(target);
        Identifier expectedBlock = EXCAVATION_EXPECTED_BLOCKS.get(target);
        if (!state.isAir() && expectedBlock != null
                && !Registries.BLOCK.getId(state.getBlock()).equals(expectedBlock)) {
            stopInternal("Bau gestoppt: Der Aushub-Block bei " + target.toShortString()
                    + " wurde seit der gespeicherten Vorschau verändert. Nightfall gräbt ihn nicht ungeprüft ab.", true);
            return;
        }
        if (state.isAir()) {
            excavationIndex++;
            saveCheckpoint();
            excavationRetries = 0;
            excavationApproachAttempts = 0;
            excavationWaitingNotice = false;
            return;
        }
        if (state.hasBlockEntity() || !state.getFluidState().isEmpty() || state.getHardness(mc.world, target) < 0.0f) {
            stopInternal("Bau gestoppt: Der Aushub würde bei " + target.toShortString()
                    + " eine geschützte Block-Entity, Flüssigkeit oder einen unzerstörbaren Block treffen.", true);
            return;
        }

        double reach = player.getBlockInteractionRange();
        if (player.getEyePos().squaredDistanceTo(target.toCenterPos()) > reach * reach) {
            approachExcavationTarget(target);
            return;
        }
        try {
            AIPlayerController.mineBlock(target.getX(), target.getY(), target.getZ());
            pendingExcavationTarget = target.toImmutable();
            excavationMinePending = true;
            status = "Ausgraben " + (excavationIndex + 1) + "/" + EXCAVATION_PLAN.size();
        } catch (IllegalArgumentException notReachable) {
            approachExcavationTarget(target);
        }
    }

    private static void approachExcavationTarget(BlockPos target) {
        if (excavationApproachAttempts >= 3) {
            if (!excavationWaitingNotice) {
                excavationWaitingNotice = true;
                notifyPlayer("Aushub pausiert: Ich erreiche " + target.toShortString()
                        + " nicht sicher. Geh näher an die Stelle oder schaffe freie Sicht; Nightfall setzt danach fort.");
            }
            return;
        }
        if (AIPlayerController.navigateNear(target, 2)) {
            excavationApproachAttempts++;
            excavationNavigationPending = true;
            status = "Gehe zum Aushub " + target.toShortString();
            return;
        }
        if (!excavationWaitingNotice) {
            excavationWaitingNotice = true;
            notifyPlayer("Aushub wartet auf deine Position bei " + target.toShortString()
                    + ". Geh näher heran; danach grabe ich automatisch weiter.");
        }
    }

    private static PlacementCandidate findPlacementCandidate(MinecraftClient mc, ClientPlayerEntity player,
                                                              BlockPos target, BlockItem item,
                                                              BlockState expectedState) {
        ItemStack stack = player.getMainHandStack();
        if (stack.isEmpty() || !stack.isOf(item)) return null;
        float savedYaw = player.getYaw();
        float savedPitch = player.getPitch();
        float[] yaws = {0.0f, 90.0f, 180.0f, -90.0f};
        float[] pitches = {0.0f, -90.0f, 90.0f};
        try {
            for (Direction face : Direction.values()) {
                BlockPos support = target.offset(face.getOpposite());
                BlockState supportState = mc.world.getBlockState(support);
                if (supportState.getCollisionShape(mc.world, support).isEmpty()) continue;
                Vec3d hitPos = support.toCenterPos().add(face.getOffsetX() * 0.5,
                        face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
                if (player.getEyePos().squaredDistanceTo(hitPos) > Math.min(REACH_SQUARED,
                        player.getBlockInteractionRange() * player.getBlockInteractionRange())) continue;
                BlockHitResult hit = new BlockHitResult(hitPos, face, support, false);
                for (float yaw : yaws) {
                    for (float pitch : pitches) {
                        player.setYaw(yaw);
                        player.setPitch(pitch);
                        ItemPlacementContext raw = new ItemPlacementContext(player, Hand.MAIN_HAND, stack, hit);
                        ItemPlacementContext context = item.getPlacementContext(raw);
                        if (context == null || !context.getBlockPos().equals(target)) continue;
                        BlockState predicted = ((BlockItemPlacementAccessor) (Object) item)
                                .nightfall$invokeGetPlacementState(context);
                        if (expectedState.equals(predicted))
                            return new PlacementCandidate(hit, yaw, pitch);
                    }
                }
            }
        } finally {
            player.setYaw(savedYaw);
            player.setPitch(savedPitch);
        }
        return null;
    }

    /**
     * Finds a block in the hotbar or moves one stack from the main inventory with a normal
     * vanilla SWAP click. No item is created or discarded; a displaced hotbar stack moves back
     * into the source inventory slot.
     */
    private static int findMaterialSlot(MinecraftClient mc, ClientPlayerEntity player,
                                        BlockItem expectedMaterial) {
        if (pendingInventoryMaterial != null) {
            if (pendingInventoryMaterial != expectedMaterial) {
                pendingInventoryMaterial = null;
                pendingInventoryHotbarSlot = -1;
                pendingInventorySwapTicks = 0;
            } else {
                ItemStack staged = player.getInventory().getStack(pendingInventoryHotbarSlot);
                if (!staged.isEmpty() && staged.isOf(expectedMaterial)) {
                    int readySlot = pendingInventoryHotbarSlot;
                    pendingInventoryMaterial = null;
                    pendingInventoryHotbarSlot = -1;
                        pendingInventorySwapTicks = 0;
                    return readySlot;
                }
                if (++pendingInventorySwapTicks < 20) return MATERIAL_SWAP_PENDING;
                pendingInventoryMaterial = null;
                pendingInventoryHotbarSlot = -1;
                pendingInventorySwapTicks = 0;
                return -1;
            }
        }

        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.isOf(expectedMaterial)) return slot;
        }

        int sourceSlot = -1;
        for (int slot = 9; slot < 36; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.isOf(expectedMaterial)) {
                sourceSlot = slot;
                break;
            }
        }
        if (sourceSlot < 0) return -1;
        if (player.currentScreenHandler != player.playerScreenHandler) {
            status = "Warte auf Schließen des Containers";
            return MATERIAL_SWAP_PENDING;
        }

        int hotbarSlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isEmpty()) {
                hotbarSlot = slot;
                break;
            }
        }
        if (hotbarSlot < 0) hotbarSlot = originalSlot >= 0 ? originalSlot : player.getInventory().getSelectedSlot();

        mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, sourceSlot, hotbarSlot,
                SlotActionType.SWAP, player);
        pendingInventoryMaterial = expectedMaterial;
        pendingInventoryHotbarSlot = hotbarSlot;
        pendingInventorySwapTicks = 0;
        notifyPlayer("Material aus dem Inventar geholt: " + expectedMaterial.getName().getString()
                + " (Inventarslot " + (sourceSlot + 1) + " → Hotbar " + (hotbarSlot + 1) + ").");
        return MATERIAL_SWAP_PENDING;
    }


    private static Path checkpointPath() {
        return FabricLoader.getInstance().getConfigDir().resolve("nightfall").resolve("builder_resume.json");
    }

    private static String currentServerKey(MinecraftClient mc) {
        if (mc.getCurrentServerEntry() != null)
            return "server:" + mc.getCurrentServerEntry().address.toLowerCase(Locale.ROOT);
        if (mc.isInSingleplayer() && mc.getServer() != null)
            return "singleplayer:" + mc.getServer().getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize();
        return "unknown";
    }

    private static void saveCheckpoint() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if ((!running && !prepared) || mc.world == null || blueprint == null) return;
        try {
            JsonObject root = new JsonObject();
            savedServerKey = currentServerKey(mc);
            root.addProperty("version", 2);
            root.addProperty("server", savedServerKey);
            root.addProperty("dimension", mc.world.getRegistryKey().getValue().toString());
            root.addProperty("name", blueprintName);
            root.addProperty("index", index);
            root.addProperty("excavationIndex", excavationIndex);
            root.addProperty("materialName", materialName);
            root.addProperty("material", material == null ? "" : Registries.ITEM.getId(material).toString());
            JsonArray blocks = new JsonArray();
            for (BlockPos pos : PLAN) {
                BlockItem item = PLAN_MATERIALS.getOrDefault(pos, material);
                if (item == null) throw new IllegalStateException("Material für gespeicherten Bauplan fehlt.");
                JsonObject block = new JsonObject();
                block.addProperty("x", pos.getX());
                block.addProperty("y", pos.getY());
                block.addProperty("z", pos.getZ());
                block.addProperty("item", Registries.ITEM.getId(item).toString());
                blocks.add(block);
            }
            root.add("blocks", blocks);
            JsonArray excavation = new JsonArray();
            for (BlockPos pos : EXCAVATION_PLAN) {
                JsonObject block = new JsonObject();
                block.addProperty("x", pos.getX());
                block.addProperty("y", pos.getY());
                block.addProperty("z", pos.getZ());
                Identifier expected = EXCAVATION_EXPECTED_BLOCKS.get(pos);
                if (expected != null) block.addProperty("expected", expected.toString());
                excavation.add(block);
            }
            root.add("excavation", excavation);
            Path file = checkpointPath();
            Files.createDirectories(file.getParent());
            Files.writeString(file, root.toString());
        } catch (Exception error) {
            status = "Baufortschritt konnte nicht lokal gespeichert werden: " + error.getMessage();
        }
    }

    private static void deleteCheckpoint() {
        try {
            Files.deleteIfExists(checkpointPath());
            checkpointLoadAttempted = true;
        } catch (Exception ignored) {
            status = "Gespeicherter Bauplan konnte nicht gelöscht werden.";
        }
    }

    private static void tryRestoreCheckpoint() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;
        checkpointLoadAttempted = true;
        Path file = checkpointPath();
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!root.has("version") || root.get("version").getAsInt() != 2
                    || !root.has("blocks") || !root.get("blocks").isJsonArray()
                    || root.getAsJsonArray("blocks").isEmpty() || root.getAsJsonArray("blocks").size() > 5_000
                    || !root.has("excavation") || !root.get("excavation").isJsonArray()
                    || root.getAsJsonArray("excavation").size() > MAX_EXCAVATION_BLOCKS)
                throw new IllegalArgumentException("Ungültiges oder nicht unterstütztes Speicherformat.");
            String storedServer = root.get("server").getAsString();
            if (!storedServer.equals(currentServerKey(mc))) {
                status = "Ein gespeicherter Bau für einen anderen Server/eine andere Welt wurde gefunden. Verbinde dich mit der ursprünglichen Welt, um ihn fortzusetzen.";
                return;
            }
            Identifier storedDimension = Identifier.tryParse(root.get("dimension").getAsString());
            if (storedDimension == null || !storedDimension.equals(mc.world.getRegistryKey().getValue())) {
                status = "Gespeicherter Bau gefunden, aber die Dimension stimmt nicht. Wechsle zuerst in die ursprüngliche Dimension.";
                checkpointLoadAttempted = false;
                return;
            }
            PLAN.clear(); PLAN_MATERIALS.clear(); PLAN_STATES.clear();
            for (var element : root.getAsJsonArray("blocks")) {
                JsonObject block = element.getAsJsonObject();
                BlockPos pos = new BlockPos(block.get("x").getAsInt(), block.get("y").getAsInt(), block.get("z").getAsInt());
                Identifier itemId = Identifier.tryParse(block.get("item").getAsString());
                if (itemId == null || !(Registries.ITEM.get(itemId) instanceof BlockItem item)
                        || !Registries.ITEM.getId(item).equals(itemId))
                    throw new IllegalArgumentException("Ein gespeichertes Baumaterial existiert in dieser Minecraft-Version nicht mehr.");
                PLAN.add(pos); PLAN_MATERIALS.put(pos, item); PLAN_STATES.put(pos, item.getBlock().getDefaultState());
            }
            EXCAVATION_PLAN.clear();
            EXCAVATION_EXPECTED_BLOCKS.clear();
            for (var element : root.getAsJsonArray("excavation")) {
                JsonObject block = element.getAsJsonObject();
                BlockPos excavationPos = new BlockPos(block.get("x").getAsInt(), block.get("y").getAsInt(), block.get("z").getAsInt());
                EXCAVATION_PLAN.add(excavationPos);
                if (block.has("expected")) {
                    Identifier expected = Identifier.tryParse(block.get("expected").getAsString());
                    if (expected == null || !Registries.BLOCK.getId(Registries.BLOCK.get(expected)).equals(expected))
                        throw new IllegalArgumentException("Ein gespeicherter Aushub-Block ist in dieser Version unbekannt.");
                    EXCAVATION_EXPECTED_BLOCKS.put(excavationPos, expected);
                }
            }
            index = Math.max(0, Math.min(PLAN.size(), root.get("index").getAsInt()));
            excavationIndex = Math.max(0, Math.min(EXCAVATION_PLAN.size(), root.get("excavationIndex").getAsInt()));
            blueprintName = root.get("name").getAsString();
            materialName = root.has("materialName") ? root.get("materialName").getAsString() : "Gespeicherte Materialien";
            Identifier primaryId = Identifier.tryParse(root.get("material").getAsString());
            BlockItem primary = primaryId != null && Registries.ITEM.get(primaryId) instanceof BlockItem item
                    ? item : PLAN_MATERIALS.values().iterator().next();
            material = primary;
            blueprint = Blueprint.CUSTOM;
            dimension = storedDimension;
            savedServerKey = storedServer;
            preparedPlayerPos = mc.player.getBlockPos().toImmutable();
            preparedFacing = mc.player.getHorizontalFacing();
            preparedMaxBlocks = 5_000; preparedMaxHorizontal = 63; preparedMaxY = 63;
            prepared = true; running = false; restoredCheckpoint = true;
            pendingTarget = null; pendingExcavationTarget = null; pendingMaterial = null; queuedPlacementHit = null;
            status = "Gespeicherter Bau wartet auf deine Bestätigung";
            notifyPlayer("Gespeicherter Bau gefunden: " + blueprintName + " · bestätigt " + index + "/" + PLAN.size()
                    + ". Sage „Bau fortsetzen“, um ihn nach einer Prüfung weiterzuführen.");
        } catch (Exception error) {
            status = "Gespeicherter Bau konnte nicht geladen werden: " + error.getMessage()
                    + ". Die Datei bleibt zur Prüfung erhalten.";
        }
    }

    public static boolean hasRestoredCheckpoint() { return restoredCheckpoint && prepared && !running; }

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
            String buildPhase = excavationIndex < EXCAVATION_PLAN.size() || excavationMinePending
                    ? "Ausgraben " + excavationIndex + "/" + EXCAVATION_PLAN.size()
                    : phase;
            return "Builder aktiv: " + blueprintName + ". Aushub: " + excavationIndex + "/"
                    + EXCAVATION_PLAN.size() + ". Bestätigte Blöcke: " + index + "/" + total
                    + " (" + percent + "%). Phase: " + buildPhase + ". Materialien: " + materialName
                    + ". Ein noch nicht bestätigter Schritt wird nicht zum Fortschritt gezählt.";
        }
        if (prepared) {
            return "Bauplan wartet auf Bestätigung: " + blueprintName + ", " + PLAN.size()
                    + " Blöcke. Materialien: " + materialName + ". Noch wurde kein Block gesetzt.";
        }
        return "Kein Bau läuft. Letzter Builder-Status: " + status;
    }

    public static void cancelPrepared() {
        if (running) return;
        if (!keepCheckpointDuringCancel) deleteCheckpoint();
        restoredCheckpoint = false;
        prepared = false;
        PLAN.clear();
        PLAN_MATERIALS.clear();
        PLAN_STATES.clear();
        pendingInventoryMaterial = null;
        pendingInventoryHotbarSlot = -1;
        pendingInventorySwapTicks = 0;
        material = null;
        pendingMaterial = null;
        blueprint = null;
        blueprintName = "";
        CUSTOM_OFFSETS.clear();
        CUSTOM_CLEAR_OFFSETS.clear();
        EXCAVATION_PLAN.clear();
        EXCAVATION_EXPECTED_BLOCKS.clear();
        excavationIndex = 0;
        excavationRetries = 0;
        pendingExcavationTarget = null;
        excavationMinePending = false;
        excavationNavigationPending = false;
        pendingTarget = null;
        pendingExpectedState = null;
        queuedPlacementHit = null;
        queuedPlacementTarget = null;
        queuedPlacementMaterial = null;
        queuedPlacementState = null;
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
        if (running || prepared) {
            saveCheckpoint();
            preserveCheckpointOnStop = true;
        }
        if (running) stopInternal("Bau bei Verbindungsabbruch gestoppt.", false);
        keepCheckpointDuringCancel = true;
        cancelPrepared();
        keepCheckpointDuringCancel = false;
        checkpointLoadAttempted = false;
        preserveCheckpointOnStop = false;
    }

    private static void stopInternal(String message, boolean notify) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (originalSlot >= 0 && mc.player != null) mc.player.getInventory().setSelectedSlot(originalSlot);
        if (mc.player != null) {
            mc.player.setYaw(originalYaw);
            mc.player.setPitch(originalPitch);
        }
        if (AIPlayerController.isBusy()) AIPlayerController.stop(null);
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
        if (!preserveCheckpointOnStop) deleteCheckpoint();
        preserveCheckpointOnStop = false;
        if (notify) notifyPlayer(message);
        PLAN.clear();
        PLAN_MATERIALS.clear();
        PLAN_STATES.clear();
        pendingInventoryMaterial = null;
        pendingInventoryHotbarSlot = -1;
        pendingInventorySwapTicks = 0;
        material = null;
        pendingMaterial = null;
        pendingExpectedState = null;
        queuedPlacementHit = null;
        queuedPlacementTarget = null;
        queuedPlacementMaterial = null;
        queuedPlacementState = null;
        blueprint = null;
        blueprintName = "";
        CUSTOM_OFFSETS.clear();
        CUSTOM_CLEAR_OFFSETS.clear();
        EXCAVATION_PLAN.clear();
        EXCAVATION_EXPECTED_BLOCKS.clear();
        preparedPlayerPos = null;
        preparedFacing = null;
    }
}
