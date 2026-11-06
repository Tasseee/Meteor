package dev.nightfall.ai;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.FallingBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import dev.nightfall.mixin.BlockItemPlacementAccessor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

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
    private static final int CHECKPOINT_PROGRESS_INTERVAL = 25;
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
    private static final Map<BlockPos, Item> PLAN_MATERIALS = new LinkedHashMap<>();
    private static final Map<BlockPos, BlockState> PLAN_STATES = new LinkedHashMap<>();
    private static final List<RelativeBlock> CUSTOM_OFFSETS = new ArrayList<>();
    private static final List<RelativeBlock> CUSTOM_CLEAR_OFFSETS = new ArrayList<>();
    private static final List<BlockPos> EXCAVATION_PLAN = new ArrayList<>();
    private static final List<BlockPos> WATER_PLAN = new ArrayList<>();
    private static int waterIndex;
    private static BlockPos pendingWaterTarget;
    private static int pendingWaterTicks;
    private static final Map<BlockPos, Identifier> EXCAVATION_EXPECTED_BLOCKS = new LinkedHashMap<>();
    private static int excavationIndex;
    private static int excavationRetries;
    private static BlockPos pendingExcavationTarget;
    private static boolean excavationMinePending;
    private static boolean excavationNavigationPending;
    private static int excavationApproachAttempts;
    private static boolean excavationWaitingNotice;
    private static final int MAX_EXCAVATION_BLOCKS = 32_768;
    private static BlockItem material;
    private static Blueprint blueprint;
    private static Identifier dimension;
    private static BlockPos pendingTarget;
    private static Item pendingMaterial;
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
    private static Item pendingInventoryMaterial;
    private static int pendingInventoryHotbarSlot = -1;
    private static int pendingInventorySwapTicks;
    private static BlockPos preparedPlayerPos;
    private static Direction preparedFacing;
    private static int index;
    private static int delay;
    private static int pendingTicks;
    private static int placementRetries;
    private static BlockPos redstoneAdjustmentTarget;
    private static int redstoneAdjustmentAttempts;
    private static int originalSlot = -1;
    private static boolean prepared;
    private static boolean running;
    private static boolean waitingForReach;
    private static boolean placementNavigationPending;
    private static int placementApproachAttempts;
    private static boolean placementWaitingNotice;
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
    private static String checkpointScanKey = "";
    private static String savedServerKey = "";
    private static String schematicSourceFile = "";
    private static int schematicPartX = -1;
    private static int schematicPartY = -1;
    private static int schematicPartZ = -1;

    private AIBuilder() {}

    /**
     * Read-only inventory analysis for one of Nightfall's fixed building templates.
     * It does not prepare a build, write a checkpoint, change slots, or modify the world.
     */
    public static String analyzeStructureMaterials(String structure, String preferredMaterialId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null)
            throw new IllegalArgumentException("Du musst dich in einer Minecraft-Welt befinden, um Baumaterialien zu prüfen.");

        Blueprint selectedBlueprint = Blueprint.parse(structure);
        if (selectedBlueprint == Blueprint.CUSTOM)
            throw new IllegalArgumentException("Für eigene Baupläne nutze zuerst die Schematic-/Bauvorschau; diese Abfrage unterstützt die drei festen Vorlagen.");

        Map<BlockItem, Integer> inventoryMaterials = collectInventoryMaterials(mc);
        boolean creative = playerIsCreative(mc);
        int required = creative ? 1 : selectedBlueprint.blocks;
        List<AIMaterialPlanner.Candidate> candidates = new ArrayList<>();

        if (preferredMaterialId != null && !preferredMaterialId.isBlank()) {
            BlockItem requested = resolveMaterial(inventoryMaterials, preferredMaterialId);
            BlockState requestedState = requested.getBlock().getDefaultState();
            BlockPos sample = mc.player.getBlockPos();
            if (requested.getBlock() instanceof FallingBlock
                    || requestedState.getCollisionShape(mc.world, sample).isEmpty()
                    || !requestedState.isFullCube(mc.world, sample)) {
                throw new IllegalArgumentException("Das gewünschte Material ist kein unterstützter, fester Full-Block für diese Bauvorlage.");
            }
            candidates.add(new AIMaterialPlanner.Candidate(
                    Registries.ITEM.getId(requested).toString(),
                    requested.getName().getString(),
                    inventoryMaterials.getOrDefault(requested, 0)));
        } else {
            for (Map.Entry<BlockItem, Integer> entry : inventoryMaterials.entrySet()) {
                BlockItem item = entry.getKey();
                candidates.add(new AIMaterialPlanner.Candidate(
                        Registries.ITEM.getId(item).toString(),
                        item.getName().getString(),
                        entry.getValue()));
            }
        }

        List<AIMaterialPlanner.Option> ranked = AIMaterialPlanner.rank(candidates, required);
        if (ranked.isEmpty()) {
            return "Materialplanung für " + selectedBlueprint.label + ": Im normalen Inventar wurden keine "
                    + "geeigneten, nicht fallenden Full-Block-Materialien erkannt. "
                    + (creative ? "Lege einen passenden Full-Block als Muster ins Inventar."
                    : "Lege passende feste Blockmaterialien in dein Inventar.")
                    + " Diese Abfrage verändert weder Inventar noch Welt.";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Materialplanung für ").append(selectedBlueprint.label)
                .append(" (").append(selectedBlueprint.blocks).append(" Bau-Blöcke): ")
                .append(creative
                        ? "Kreativmodus – ein Musterblock genügt, die Platzierung nutzt danach den Kreativmodus. "
                        : "Survival – alle Bau-Blöcke müssen als dasselbe Material vorhanden sein. ")
                .append("Es werden nur die 36 normalen Inventar-/Hotbar-Slots gezählt.");

        int displayed = Math.min(6, ranked.size());
        for (int index = 0; index < displayed; index++) {
            AIMaterialPlanner.Option option = ranked.get(index);
            AIMaterialPlanner.Candidate candidate = option.candidate();
            summary.append("\n").append(index + 1).append(". ")
                    .append(candidate.displayName()).append(" [").append(candidate.itemId()).append("]: vorhanden ")
                    .append(candidate.available()).append(", benötigt ").append(option.required())
                    .append(", fehlen ").append(option.missing());
            if (option.sufficient()) {
                summary.append(", Überschuss ").append(option.surplus()).append(" – reicht aus.");
            } else {
                summary.append(" – reicht derzeit nicht.");
            }
        }
        if (ranked.size() > displayed)
            summary.append("\nWeitere geeignete Materialarten im Inventar: ")
                    .append(ranked.size() - displayed).append(".");

        AIMaterialPlanner.Option recommended = ranked.get(0);
        summary.append("\n")
                .append(recommended.sufficient()
                        ? "Beste passende Wahl: " + recommended.candidate().displayName() + "."
                        : "Aktuell kleinste Fehlmenge: " + recommended.candidate().displayName()
                                + "; es fehlen noch " + recommended.missing() + " Blöcke.");
        List<AIMaterialPlanner.CraftableCandidate> craftableAlternatives =
                AIPlayerController.findCraftableBlockMaterials(required);
        if (craftableAlternatives.isEmpty()) {
            summary.append("\nKeine weitere vollständige Crafting-Kette für Blockmaterialien wurde im freigeschalteten Rezeptbuch "
                    + "mit den aktuell vorhandenen Zutaten gefunden.");
        } else {
            summary.append("\nHerstellbare Materialalternativen aus dem freigeschalteten Rezeptbuch (höchstens fünf):");
            for (int index = 0; index < craftableAlternatives.size(); index++) {
                AIMaterialPlanner.CraftableCandidate option = craftableAlternatives.get(index);
                summary.append("\n").append(index + 1).append(". ")
                        .append(option.displayName()).append(" [").append(option.itemId()).append("]: Bestand ")
                        .append(option.owned()).append(", noch herzustellen ").append(option.missing())
                        .append(", Rezeptschritte ").append(option.recipeSteps())
                        .append(", Rezeptchargen ").append(option.recipeBatches())
                        .append(option.needsCraftingTable() ? ", benötigt 3×3-Crafting-Table" : ", 2×2 reicht")
                        .append(". ").append(option.recipeSummary());
            }
            summary.append("\nDiese Ketten sind nur Planvorschläge. Nightfall startet kein Crafting automatisch.");
        }
        List<AIMaterialPlanner.IncompleteCraftableCandidate> incompleteAlternatives =
                AIPlayerController.findIncompleteCraftableBlockMaterials(required);
        if (!incompleteAlternatives.isEmpty()) {
            summary.append("\\nNoch nicht vollständig herstellbare Materialalternativen (höchstens fünf):");
            for (int index = 0; index < incompleteAlternatives.size(); index++) {
                AIMaterialPlanner.IncompleteCraftableCandidate option = incompleteAlternatives.get(index);
                summary.append("\\n").append(index + 1).append(". ")
                        .append(option.displayName()).append(" [").append(option.itemId()).append("]: Bestand ")
                        .append(option.owned()).append(", noch benötigt ").append(option.missing())
                        .append(", Rezeptschritte ").append(option.recipeSteps())
                        .append(", Rezeptchargen ").append(option.recipeBatches())
                        .append(option.needsCraftingTable() ? ", benötigt 3×3-Crafting-Table" : ", 2×2 reicht")
                        .append(". Fehlende Zutaten: ").append(option.missingIngredients())
                        .append(". Unvollständige Rezeptkette: ").append(option.recipeSummary());
            }
            summary.append("\\nDie Fehlmengen sind eine Diagnose der ausgewählten Rezeptvarianten, keine Crafting-Freigabe.");
        }
        summary.append(" Das ist nur eine Bestands- und Rezeptbuchprüfung: Bauplatz, Untergrund, Hindernisse und Platzierungsrisiken "
                + "werden erst in der eigentlichen Bauvorschau geprüft. Es wurde kein Bauplan vorbereitet "
                + "und kein Checkpoint gespeichert.");
        return summary.toString();
    }

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
                    + " volle Blöcke derselben Sorte in deinem Inventar. Material-ID: "
                    + (selected == null ? "unbekannt" : Registries.ITEM.getId(selected))
                    + ". Gefunden: " + available + ", fehlen: "
                    + Math.max(0, selectedBlueprint.blocks - available) + ".");

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
        List<BlockPos> nextExcavation = new ArrayList<>();
        Map<BlockPos, Identifier> nextExcavationExpected = new LinkedHashMap<>();
        Map<Identifier, Integer> nextExcavationMaterials = new LinkedHashMap<>();
        BlockState desiredState = selected.getBlock().getDefaultState();
        for (BlockPos target : nextPlan) {
            if (target.getY() < mc.world.getBottomY()
                    || target.getY() >= mc.world.getBottomY() + mc.world.getHeight())
                throw new IllegalArgumentException("Der Bauplatz liegt außerhalb der Weltgrenzen.");
            BlockState present = mc.world.getBlockState(target);
            if (present.equals(desiredState) || present.isReplaceable()) continue;
            if (present.getBlock() instanceof FallingBlock || present.hasBlockEntity()
                    || !present.getFluidState().isEmpty() || present.getHardness(mc.world, target) < 0.0f)
                throw new IllegalArgumentException("Nightfall kann das Hindernis bei X=" + target.getX()
                        + ", Y=" + target.getY() + ", Z=" + target.getZ()
                        + " nicht sicher entfernen (fallender Block, Block-Entity, Flüssigkeit oder unzerstörbarer Block).");
            Identifier obstacleId = Registries.BLOCK.getId(present.getBlock());
            nextExcavation.add(target.toImmutable());
            nextExcavationExpected.put(target.toImmutable(), obstacleId);
            nextExcavationMaterials.merge(obstacleId, 1, Integer::sum);
        }
        if (nextExcavation.size() > MAX_EXCAVATION_BLOCKS)
            throw new IllegalArgumentException("Für diesen Bau müssten mehr als " + MAX_EXCAVATION_BLOCKS
                    + " Hindernisblöcke entfernt werden.");
        nextExcavation.sort(Comparator.comparingInt(BlockPos::getY).reversed()
                .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));

        PLAN.clear();
        PLAN.addAll(nextPlan);
        PLAN_MATERIALS.clear();
        PLAN_STATES.clear();
        EXCAVATION_PLAN.clear();
        EXCAVATION_PLAN.addAll(nextExcavation);
        EXCAVATION_EXPECTED_BLOCKS.clear();
        EXCAVATION_EXPECTED_BLOCKS.putAll(nextExcavationExpected);
        excavationIndex = 0;
        excavationRetries = 0;
        pendingExcavationTarget = null;
        excavationMinePending = false;
        excavationNavigationPending = false;
        excavationApproachAttempts = 0;
        excavationWaitingNotice = false;
        for (BlockPos target : nextPlan) PLAN_STATES.put(target, desiredState);
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
        restoredCheckpoint = false;
        prepared = true;
        status = "Bauplan geprüft";
        String layout = switch (selectedBlueprint) {
            case STARTER_SHELTER -> "4x4-Boden, zwei hohe Wände, Türöffnung und Dach";
            case LARGE_HOUSE -> "6x6-Boden, drei hohe Wände, Türöffnung und Dach";
            case WATCHTOWER -> "5x5-Plattform, vier sechs Blöcke hohe Eckpfeiler und Dachplattform";
            case CUSTOM -> throw new IllegalStateException("Eigener Bauplan darf nicht über Vorlagen-Planung laufen.");
        };
        saveCheckpoint();
        List<String> obstacleSummary = new ArrayList<>();
        for (Map.Entry<Identifier, Integer> obstacle : nextExcavationMaterials.entrySet()) {
            if (obstacleSummary.size() == 8) break;
            obstacleSummary.add(obstacle.getKey() + " × " + obstacle.getValue());
        }
        if (nextExcavationMaterials.size() > obstacleSummary.size())
            obstacleSummary.add("+" + (nextExcavationMaterials.size() - obstacleSummary.size()) + " weitere Blockarten");
        String obstacleDetails = nextExcavation.isEmpty() ? "keine" : String.join(", ", obstacleSummary);
        return "Bauplan geprüft: " + selectedBlueprint.label + " – " + layout + ". Material: "
                + materialName + " × " + (creative ? "unbegrenzt (Kreativmodus)" : selectedBlueprint.blocks + " (Inventar: " + available + ")") + ". "
                + "Ebener, fester Boden ist geprüft. Entfernbare Hindernisse im Bauvolumen: "
                + nextExcavation.size() + " (" + obstacleDetails + "). "
                + "Block-Entities, Flüssigkeiten, fallende und unzerstörbare Blöcke bleiben geschützt. "
                + "Jede Platzierung wird erst nach Server-Weltupdate gezählt; bei fehlender Reichweite pausiere ich.";
    }

    public static String prepareCustomStructure(String name, String materialId, List<RelativeBlock> offsets) {
        return prepareCustomStructure(name, null, materialId, offsets, List.of(), 512, 31, 31);
    }

    /** Prepares the safe subset and the air volume that must be cleared for an imported schematic. */
    public static String prepareImportedStructure(String name, List<RelativeBlock> offsets,
                                                  List<RelativeBlock> clearOffsets) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) throw new IllegalArgumentException("Du musst in einer Welt sein, um die Schematic vorzubereiten.");
        return prepareImportedStructure(name, offsets, clearOffsets, mc.player.getBlockPos(), 32, 32, 32);
    }

    public static String prepareImportedStructure(String name, List<RelativeBlock> offsets,
                                                  List<RelativeBlock> clearOffsets, BlockPos sectionCenter,
                                                  int sectionWidth, int sectionHeight, int sectionLength) {
        return prepareImportedStructure(name, offsets, clearOffsets, List.of(),
                sectionCenter, sectionWidth, sectionHeight, sectionLength);
    }

    public static String prepareImportedStructure(String name, List<RelativeBlock> offsets,
                                                  List<RelativeBlock> clearOffsets, List<RelativeBlock> waterOffsets,
                                                  BlockPos sectionCenter, int sectionWidth,
                                                  int sectionHeight, int sectionLength) {
        return prepareImportedStructure(name, offsets, clearOffsets, waterOffsets, sectionCenter,
                sectionWidth, sectionHeight, sectionLength, null, -1, -1, -1);
    }

    public static String prepareImportedStructure(String name, List<RelativeBlock> offsets,
                                                  List<RelativeBlock> clearOffsets, List<RelativeBlock> waterOffsets,
                                                  BlockPos sectionCenter, int sectionWidth, int sectionHeight,
                                                  int sectionLength, String sourceFile, int partX, int partY, int partZ) {
        if (sectionCenter == null || sectionWidth < 1 || sectionWidth > 32
                || sectionHeight < 1 || sectionHeight > 32 || sectionLength < 1 || sectionLength > 32)
            throw new IllegalArgumentException("Ungültiges Zentrum oder ungültige Abmessungen des Schematic-Abschnitts.");
        if (sourceFile != null && ((!sourceFile.toLowerCase(Locale.ROOT).endsWith(".schem")
                && !sourceFile.toLowerCase(Locale.ROOT).endsWith(".litematic"))
                || sourceFile.length() > 110 || partX < 0 || partX > 15 || partY < 0 || partY > 15
                || partZ < 0 || partZ > 15))
            throw new IllegalArgumentException("Ungültige Schematic-Datei oder Abschnittskennung.");

        // Clear stale identity before the shared preparation path writes its first checkpoint.
        schematicSourceFile = "";
        schematicPartX = schematicPartY = schematicPartZ = -1;
        String preview = prepareCustomStructure(name, null, null, offsets, clearOffsets,
                32_768, 31, 31, true, sectionCenter, sectionWidth, sectionHeight, sectionLength, waterOffsets);
        if (sourceFile != null) {
            schematicSourceFile = sourceFile;
            schematicPartX = partX;
            schematicPartY = partY;
            schematicPartZ = partZ;
            saveCheckpoint();
        }
        return preview;
    }

    private static String prepareCustomStructure(String name, BlockItem preferredMaterial, List<RelativeBlock> offsets) {
        return prepareCustomStructure(name, preferredMaterial, null, offsets, List.of(), 512, 31, 31, false);
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
        return prepareCustomStructure(name, preferredMaterial, preferredMaterialId, offsets, clearOffsets,
                maxBlocks, maxHorizontal, maxVerticalLimit, preserveSchematicOrientation, null, 0, 0, 0, List.of());
    }

    private static String prepareCustomStructure(String name, BlockItem preferredMaterial,
                                                 String preferredMaterialId, List<RelativeBlock> offsets,
                                                 List<RelativeBlock> clearOffsets,
                                                 int maxBlocks, int maxHorizontal, int maxVerticalLimit,
                                                 boolean preserveSchematicOrientation, BlockPos fixedSchematicCenter,
                                                 int sectionWidth, int sectionHeight, int sectionLength) {
        return prepareCustomStructure(name, preferredMaterial, preferredMaterialId, offsets, clearOffsets,
                maxBlocks, maxHorizontal, maxVerticalLimit, preserveSchematicOrientation,
                fixedSchematicCenter, sectionWidth, sectionHeight, sectionLength, List.of());
    }

    private static String prepareCustomStructure(String name, BlockItem preferredMaterial,
                                                 String preferredMaterialId, List<RelativeBlock> offsets,
                                                 List<RelativeBlock> clearOffsets,
                                                 int maxBlocks, int maxHorizontal, int maxVerticalLimit,
                                                 boolean preserveSchematicOrientation, BlockPos fixedSchematicCenter,
                                                 int sectionWidth, int sectionHeight, int sectionLength,
                                                 List<RelativeBlock> waterOffsets) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (running) throw new IllegalArgumentException("Nightfall baut bereits. Stoppe den aktuellen Bau zuerst.");
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null)
            throw new IllegalArgumentException("Du musst dich zuerst in einer verbundenen Welt befinden.");
        if (name == null || name.isBlank() || !name.matches("[\\p{L}0-9 _.-]{1,48}"))
            throw new IllegalArgumentException("Der Bauplanname ist ungültig.");
        if (maxBlocks < 1 || maxHorizontal < 1 || maxVerticalLimit < 1
                || offsets == null || offsets.size() > maxBlocks
                || clearOffsets == null || clearOffsets.size() > 262_144
                || waterOffsets == null || waterOffsets.size() > 32_768
                || (offsets.isEmpty() && waterOffsets.isEmpty() && clearOffsets.isEmpty()))
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
        Set<String> uniqueWaterCoordinates = new HashSet<>();
        for (RelativeBlock water : waterOffsets) {
            if (water == null || water.x() < 0 || water.x() > maxHorizontal
                    || water.y() < 0 || water.y() > maxVerticalLimit
                    || water.z() < 0 || water.z() > maxHorizontal)
                throw new IllegalArgumentException("Wasserquellen überschreiten das erlaubte Schematic-Raster.");
            String key = water.x() + ":" + water.y() + ":" + water.z();
            if (!uniqueWaterCoordinates.add(key) || uniqueCoordinates.contains(key))
                throw new IllegalArgumentException("Wasserquellen enthalten doppelte Positionen oder überschneiden sich mit Bau-Blöcken.");
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

        Map<Item, Integer> requiredCounts = new LinkedHashMap<>();
        Map<RelativeBlock, Item> offsetMaterials = new LinkedHashMap<>();
        Map<RelativeBlock, BlockState> offsetStates = new LinkedHashMap<>();
        for (RelativeBlock offset : offsets) {
            BlockState targetState = offset.state();
            Item blockMaterial;
            if ("minecraft:redstone".equals(offset.materialId()) && targetState != null
                    && targetState.isOf(Blocks.REDSTONE_WIRE)) {
                blockMaterial = Items.REDSTONE;
            } else {
                BlockItem blockItem = offset.materialId() == null || offset.materialId().isBlank()
                        ? selected : resolveMaterial(counts, offset.materialId());
                blockMaterial = blockItem;
            }
            if (blockMaterial == null)
                throw new IllegalArgumentException("Für jeden Block ohne eigenes Material muss ein passender Block im Spielerinventar liegen.");
            if (targetState == null) {
                if (!(blockMaterial instanceof BlockItem blockItem))
                    throw new IllegalArgumentException("Für Redstone-Staub fehlt der gespeicherte Redstone-Blockzustand.");
                targetState = blockItem.getBlock().getDefaultState();
            }
            if (blockMaterial instanceof BlockItem blockItem) {
                if (!targetState.isOf(blockItem.getBlock()))
                    throw new IllegalArgumentException("BlockState und Material stimmen an Position "
                            + offset.x() + "/" + offset.y() + "/" + offset.z() + " nicht überein.");
            } else if (blockMaterial != Items.REDSTONE || !targetState.isOf(Blocks.REDSTONE_WIRE)) {
                throw new IllegalArgumentException("Nur Redstone-Staub darf als Nicht-BlockItem in eine Schematic übernommen werden.");
            }
            offsetMaterials.put(offset, blockMaterial);
            offsetStates.put(offset, targetState);
            requiredCounts.merge(blockMaterial, 1, Integer::sum);
        }
        boolean creative = playerIsCreative(mc);
        List<String> shortages = new ArrayList<>();
        for (Map.Entry<Item, Integer> requirement : requiredCounts.entrySet()) {
            int found = countInventoryItem(mc, requirement.getKey());
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
        if (selected == null && !offsetMaterials.isEmpty()) {
            selected = offsetMaterials.values().stream()
                    .filter(BlockItem.class::isInstance)
                    .map(BlockItem.class::cast)
                    .findFirst().orElse(null);
        }

        ClientPlayerEntity player = mc.player;
        Direction playerForward = player.getHorizontalFacing();
        Direction forward = preserveSchematicOrientation ? Direction.SOUTH : playerForward;
        Direction right = preserveSchematicOrientation ? Direction.EAST : forward.rotateYClockwise();
        int forwardSpan = playerForward.getAxis() == Direction.Axis.X ? maxX : maxZ;
        BlockPos schematicCenter = player.getBlockPos().offset(playerForward, 1 + (forwardSpan + 1) / 2);
        BlockPos origin = preserveSchematicOrientation
                ? (fixedSchematicCenter == null
                    ? player.getBlockPos().add(-((maxX + 1) / 2), 0, -((maxZ + 1) / 2))
                    : fixedSchematicCenter.add(-(sectionWidth / 2), -(sectionHeight / 2), -(sectionLength / 2)))
                : player.getBlockPos().offset(forward, 1).offset(right, -((maxX + 1) / 2));
        List<BlockPos> targets = new ArrayList<>(offsets.size());
        Set<BlockPos> targetSet = new HashSet<>();
        Map<BlockPos, Item> targetMaterials = new LinkedHashMap<>();
        Set<BlockPos> excavationSet = new HashSet<>();
        List<BlockPos> nextWater = new ArrayList<>();
        boolean waterAutomationEnabled = playerIsCreative(mc)
                && countInventoryItem(mc, Items.WATER_BUCKET) > 0;
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

        // Water source cells are placed after solid blocks, and only in Creative mode where the
        // same bucket can be reused without consuming resources.
        for (RelativeBlock water : waterOffsets) {
            BlockPos target = origin.add(right.getOffsetX() * water.x() + forward.getOffsetX() * water.z(),
                    water.y(), right.getOffsetZ() * water.x() + forward.getOffsetZ() * water.z());
            if (target.getY() < mc.world.getBottomY()
                    || target.getY() >= mc.world.getBottomY() + mc.world.getHeight()
                    || !mc.world.isChunkLoaded(target))
                throw new IllegalArgumentException("Wasser-Ziel liegt außerhalb der Welt oder in einem nicht geladenen Chunk: " + target.toShortString());
            BlockState present = mc.world.getBlockState(target);
            if (present.isOf(Blocks.WATER) && present.get(FluidBlock.LEVEL) == 0) continue;
            if (present.getFluidState().isOf(Fluids.WATER))
                throw new IllegalArgumentException("Wasser-Ziel " + target.toShortString()
                        + " enthält nur fließendes Wasser statt einer Quellposition. Bitte diese Stelle vor dem Bau prüfen und leeren.");
            if (!present.getFluidState().isEmpty())
                throw new IllegalArgumentException("Wasser-Ziel " + target.toShortString() + " enthält bereits eine andere Flüssigkeit; Nightfall verändert sie nicht automatisch.");
            if (!present.isReplaceable()) {
                if (present.hasBlockEntity() || present.getHardness(mc.world, target) < 0.0f)
                    throw new IllegalArgumentException("Wasser-Ziel " + target.toShortString()
                            + " ist durch eine Block-Entity oder einen unzerstörbaren Block geschützt.");
                excavationSet.add(target.toImmutable());
            }
            if (waterAutomationEnabled) nextWater.add(target.toImmutable());
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

        // Complete the structural/support blocks first, then redstone devices, and dust last.
        // Dust derives its connection shape from neighboring blocks, so placing it after its
        // supports and devices avoids comparing against a deliberately incomplete wire shape.
        ordered.sort(Comparator.comparingInt(pos -> redstonePlacementPhase(targetMaterials.get(pos))));

        PLAN.clear();
        PLAN.addAll(ordered);
        PLAN_MATERIALS.clear();
        PLAN_STATES.clear();
        EXCAVATION_PLAN.clear();
        EXCAVATION_PLAN.addAll(nextExcavation);
        WATER_PLAN.clear();
        WATER_PLAN.addAll(nextWater);
        waterIndex = 0;
        pendingWaterTarget = null;
        pendingWaterTicks = 0;
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
        restoredCheckpoint = false;
        prepared = true;
        preparedMaxBlocks = maxBlocks;
        preparedMaxHorizontal = maxHorizontal;
        preparedMaxY = maxVerticalLimit;
        absoluteSchematicOrientation = preserveSchematicOrientation;
        status = "Eigener Bauplan geprüft";
        saveCheckpoint();
        int manualWaterSources = waterOffsets.size() - nextWater.size();
        return "Eigener Bauplan geprüft: „" + blueprintName + "“, " + offsets.size() + " Blöcke, "
                + (maxX + 1) + " × " + (maxY + 1) + " × " + (maxZ + 1)
                + " Raster, Materialien: " + materialName
                + ". Aushub: " + nextExcavation.size() + " vorhandene Blöcke werden vor dem Bau entfernt. "
                + "Wasserquellen: " + nextWater.size() + " werden nach den festen Blöcken mit einem wiederverwendeten Wassereimer gesetzt"
                + (manualWaterSources > 0 ? "; " + manualWaterSources + " bleiben manuell (Survival oder kein Wassereimer)." : ".")
                + " Block-Entities, andere Flüssigkeiten und unzerstörbare Blöcke bleiben geschützt."
                + " Redstone-Bauteile werden nach ihren Stützblöcken, Redstone-Staub zuletzt platziert. "
                + "Dynamische Verbindungs-/Signalwerte werden erst im entstandenen Weltkontext bewertet."
                + " Teile sind mit festen vorhandenen Blöcken oder dem Boden verbunden."
                + customLayoutPreview(offsets, offsetMaterials, maxX, maxY, maxZ);
    }

    private static String fixedLayoutPreview(List<BlockPos> positions, BlockPos origin,
                                               Direction right, Direction forward, int baseY,
                                               BlockItem blockMaterial) {
        List<RelativeBlock> offsets = new ArrayList<>(positions.size());
        Map<RelativeBlock, Item> materials = new LinkedHashMap<>();
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
                                               Map<RelativeBlock, Item> offsetMaterials,
                                               int maxX, int maxY, int maxZ) {
        Map<Item, Character> palette = new LinkedHashMap<>();
        Map<String, Character> groundPlan = new LinkedHashMap<>();
        Map<Integer, Integer> layerCounts = new LinkedHashMap<>();
        for (RelativeBlock offset : offsets) {
            Item blockMaterial = offsetMaterials.get(offset);
            Character symbol = palette.get(blockMaterial);
            if (symbol == null) {
                symbol = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".charAt(palette.size());
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
        for (Map.Entry<Item, Character> entry : palette.entrySet())
            legend.add(entry.getValue() + "=" + entry.getKey().getName().getString());
        preview.append(String.join(", ", legend));
        return preview.toString();
    }

    private static boolean playerIsCreative(MinecraftClient mc) {
        return mc.player != null && mc.player.getAbilities().creativeMode;
    }

    private static int countInventoryItem(MinecraftClient mc, Item item) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.isOf(item)) count += stack.getCount();
        }
        return count;
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

    private static String describeMaterials(Map<Item, Integer> requirements) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Item, Integer> entry : requirements.entrySet())
            parts.add(entry.getKey().getName().getString() + " × " + entry.getValue());
        return String.join(", ", parts);
    }

    private static int redstonePlacementPhase(Item item) {
        if (item == Items.REDSTONE) return 2;
        if (item == null) return 0;
        String path = Registries.ITEM.getId(item).getPath();
        return switch (path) {
            case "repeater", "comparator", "observer", "piston", "sticky_piston",
                    "dispenser", "dropper", "hopper", "redstone_torch",
                    "redstone_wall_torch", "redstone_lamp", "lever", "tripwire",
                    "tripwire_hook", "daylight_detector", "target", "tnt",
                    "lightning_rod" -> 1;
            default -> path.endsWith("_button") || path.endsWith("_pressure_plate") ? 1 : 0;
        };
    }

    private static boolean hasDynamicRedstoneProperty(BlockState state, String propertyName) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return switch (path) {
            case "redstone_wire" -> Set.of("power", "north", "east", "south", "west").contains(propertyName);
            case "repeater" -> Set.of("powered", "locked").contains(propertyName);
            case "comparator", "observer", "lever", "redstone_lamp",
                    "redstone_torch", "redstone_wall_torch", "dispenser", "dropper",
                    "lightning_rod" -> propertyName.equals("powered") || propertyName.equals("lit")
                    || propertyName.equals("triggered");
            case "piston", "sticky_piston" -> propertyName.equals("extended");
            case "hopper" -> propertyName.equals("enabled");
            case "tripwire" -> Set.of("powered", "attached", "disarmed").contains(propertyName);
            case "tripwire_hook" -> Set.of("powered", "attached").contains(propertyName);
            default -> (path.endsWith("_button") || path.endsWith("_pressure_plate"))
                    && propertyName.equals("powered");
        };
    }

    private static boolean isRedstonePlacementTunable(BlockState state, String propertyName) {
        String path = Registries.BLOCK.getId(state.getBlock()).getPath();
        return (path.equals("repeater") && propertyName.equals("delay"))
                || (path.equals("comparator") && propertyName.equals("mode"));
    }

    /** Compare stable schematic properties while allowing redstone's neighbor/tick-driven state to settle. */
    private static boolean matchesSchematicState(BlockState expected, BlockState actual) {
        if (expected == null || actual == null || !expected.isOf(actual.getBlock())) return false;
        if (expected.equals(actual)) return true;
        for (var entry : expected.getEntries().entrySet()) {
            var property = entry.getKey();
            if (hasDynamicRedstoneProperty(expected, property.getName())) continue;
            if (!entry.getValue().equals(actual.get(property))) return false;
        }
        return true;
    }

    /** Initial BlockItem placement cannot choose repeater delay or comparator mode; configure them by vanilla use. */
    private static boolean matchesPlacementState(BlockState expected, BlockState actual) {
        if (expected == null || actual == null || !expected.isOf(actual.getBlock())) return false;
        if (expected.equals(actual)) return true;
        for (var entry : expected.getEntries().entrySet()) {
            String name = entry.getKey().getName();
            if (hasDynamicRedstoneProperty(expected, name) || isRedstonePlacementTunable(expected, name)) continue;
            if (!entry.getValue().equals(actual.get(entry.getKey()))) return false;
        }
        return true;
    }

    private static String statePropertyValue(BlockState state, String propertyName) {
        for (var entry : state.getEntries().entrySet())
            if (entry.getKey().getName().equals(propertyName)) return String.valueOf(entry.getValue());
        return "";
    }

    private static boolean hasOnlyTunableRedstoneDifferences(BlockState expected, BlockState actual) {
        if (expected == null || actual == null || !expected.isOf(actual.getBlock())) return false;
        boolean needsAdjustment = false;
        for (var entry : expected.getEntries().entrySet()) {
            String name = entry.getKey().getName();
            if (hasDynamicRedstoneProperty(expected, name)) continue;
            if (entry.getValue().equals(actual.get(entry.getKey()))) continue;
            if (isRedstonePlacementTunable(expected, name)) {
                needsAdjustment = true;
                continue;
            }
            return false;
        }
        return needsAdjustment;
    }

    private static boolean adjustRedstoneState(MinecraftClient mc, ClientPlayerEntity player,
                                               BlockPos target, BlockState expected, BlockState actual) {
        if (!hasOnlyTunableRedstoneDifferences(expected, actual)) return false;
        double reach = player.getBlockInteractionRange();
        Vec3d hitPos = target.toCenterPos().add(0.0, 0.5, 0.0);
        if (player.getEyePos().squaredDistanceTo(hitPos) > Math.min(REACH_SQUARED, reach * reach)) {
            waitingForReach = true;
            status = "Warte auf Reichweite zum Einstellen eines Redstone-Bauteils bei " + target.toShortString();
            return true;
        }
        if (!target.equals(redstoneAdjustmentTarget)) {
            redstoneAdjustmentTarget = target.toImmutable();
            redstoneAdjustmentAttempts = 0;
        }
        if (redstoneAdjustmentAttempts >= 4) {
            stopInternal("Redstone-Bauteil bei " + target.toShortString()
                    + " ließ sich mit normaler Interaktion nicht auf den gespeicherten Verzögerungs-/Moduswert einstellen. "
                    + "Der Abschnitt bleibt unvollständig.", true);
            return true;
        }
        BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, target, false);
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.swingHand(Hand.MAIN_HAND);
        redstoneAdjustmentAttempts++;
        delay = 2;
        status = "Stelle Redstone-Bauteil ein " + (index + 1) + "/" + PLAN.size()
                + " · Versuch " + redstoneAdjustmentAttempts + "/4";
        return true;
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
        // The player confirmed this exact in-memory plan. Never silently rebuild or
        // expand its excavation list after confirmation; tick() validates each target.
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
        placementNavigationPending = false;
        placementApproachAttempts = 0;
        placementWaitingNotice = false;
        pausedByUser = false;
        running = true;
        restoredCheckpoint = false;
        status = "Bau gestartet";
        saveCheckpoint();
        notifyPlayer("Bau gestartet: " + blueprintName + " · Aushub "
                + excavationIndex + "/" + EXCAVATION_PLAN.size() + " · Bau "
                + index + "/" + PLAN.size() + ".");
        String materialSummary = blueprint == Blueprint.CUSTOM
                ? "Materialien: " + materialName
                : "Material: " + materialName + " × " + PLAN.size();
        return "Bau gestartet: " + blueprintName + ". " + materialSummary
                + ". Zuerst entferne ich " + EXCAVATION_PLAN.size()
                + " Hindernis-/Luftzellen-Blöcke, danach setze ich den Bauplan. Jeder Schritt wird serverseitig geprüft.";
    }

    public static void tick() {
        if (!running) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && mc.world != null && mc.getNetworkHandler() != null
                    && (!checkpointLoadAttempted || !checkpointScanKey.equals(currentCheckpointScanKey(mc))))
                tryRestoreCheckpoint();
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null
                || !mc.world.getRegistryKey().getValue().equals(dimension)) {
            // Keep the last checkpoint when the connection/world changes unexpectedly.
            preserveCheckpointOnStop = true;
            stopInternal("Bau gestoppt: Welt oder Verbindung hat sich geändert. Der gespeicherte Bauplan bleibt erhalten.", true);
            return;
        }
        ClientPlayerEntity player = mc.player;
        if (player.currentScreenHandler != player.playerScreenHandler) {
            status = "Warte auf Schließen des Containers";
            return;
        }
        if (delay > 0) { delay--; return; }

        if (placementNavigationPending) {
            if (AIPlayerController.isBusy()) {
                status = "Gehe zum nächsten Bauabschnitt";
                return;
            }
            placementNavigationPending = false;
        }
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
            if (pendingExpectedState != null && adjustRedstoneState(mc, player, pendingTarget,
                    pendingExpectedState, pendingState)) return;
            if (pendingExpectedState != null && matchesSchematicState(pendingExpectedState, pendingState)) {
                pendingMaterial = null;
                redstoneAdjustmentTarget = null;
                redstoneAdjustmentAttempts = 0;
                index++;
                saveCheckpointProgress(index, PLAN.size());
                pendingTarget = null;
                pendingTicks = 0;
                placementRetries = 0;
                progressNotice();
                delay = 1;
                return;
            }
            if (!pendingState.isReplaceable()
                    && (pendingExpectedState == null || !pendingState.isOf(pendingExpectedState.getBlock()))) {
                stopInternal("Bau gestoppt: Der Server meldet an der Zielposition einen anderen Block. "
                        + "Erwartet: " + (pendingExpectedState == null ? "unbekannt" : pendingExpectedState)
                        + ", erhalten: " + pendingState + ".", true);
                return;
            }
            if (++pendingTicks < CONFIRMATION_TIMEOUT_TICKS) { delay = 1; return; }
            if (pendingMaterial != null && pendingExpectedState != null
                    && pendingState.isOf(pendingExpectedState.getBlock())) {
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
            if (waterIndex < WATER_PLAN.size() || pendingWaterTarget != null) {
                tickWaterPlacement(mc, player);
                return;
            }
            stopInternal("Bauabschnitt abgeschlossen: " + blueprintName + " · " + PLAN.size()
                    + " feste Blöcke und " + waterIndex + "/" + WATER_PLAN.size()
                    + " Wasserquellen vom Client bestätigt. Nicht unterstützte Teile bleiben manuell.", true);
            return;
        }
        BlockPos target = PLAN.get(index);
        BlockState present = mc.world.getBlockState(target);
        Item targetMaterial = PLAN_MATERIALS.getOrDefault(target, material);
        if (targetMaterial == null) {
            stopInternal("Bau gestoppt: Für den nächsten Block ist kein Baumaterial zugeordnet.", true);
            return;
        }
        BlockState expectedState = PLAN_STATES.get(target);
        if (expectedState == null && targetMaterial instanceof BlockItem blockItem)
            expectedState = blockItem.getBlock().getDefaultState();
        if (expectedState == null) {
            stopInternal("Bau gestoppt: Für das nächste Material fehlt ein sicherer Blockzustand.", true);
            return;
        }
        if (matchesSchematicState(expectedState, present)) {
            redstoneAdjustmentTarget = null;
            redstoneAdjustmentAttempts = 0;
            index++;
            placementApproachAttempts = 0;
            placementWaitingNotice = false;
            saveCheckpointProgress(index, PLAN.size());
            progressNotice();
            return;
        }
        if (present.isOf(expectedState.getBlock())
                && adjustRedstoneState(mc, player, target, expectedState, present)) return;
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
        double placementReach = player.getBlockInteractionRange();
        if (player.getEyePos().squaredDistanceTo(target.toCenterPos()) > placementReach * placementReach) {
            waitingForReach = true;
            if (placementApproachAttempts >= 3) {
                if (!placementWaitingNotice) {
                    placementWaitingNotice = true;
                    notifyPlayer("Bau pausiert: Ich erreiche " + target.toShortString()
                            + " nicht sicher. Geh näher an die Stelle; Nightfall setzt danach fort.");
                }
                return;
            }
            if (AIPlayerController.navigateNear(target, 2)) {
                placementNavigationPending = true;
                placementApproachAttempts++;
                status = "Gehe zum Bauziel " + target.toShortString();
                return;
            }
            placementApproachAttempts++;
            status = "Warte auf erreichbaren Bauweg";
            return;
        }
        placementApproachAttempts = 0;
        placementWaitingNotice = false;
        waitingForReach = false;
        if (targetMaterial instanceof BlockItem blockItem) {
            PlacementCandidate candidate = findPlacementCandidate(mc, player, target, blockItem, expectedState);
            if (candidate == null) {
                stopInternal("Bau gestoppt: Für " + expectedState + " wurde mit normaler Minecraft-Platzierung keine sichere Ansetzfläche/Ausrichtung gefunden. "
                        + "Nightfall setzt den Block nicht falsch herum.", true);
                return;
            }
            status = "Richte Block aus " + (index + 1) + "/" + PLAN.size();
            queuedPlacementHit = candidate.hit();
            queuedPlacementTarget = target.toImmutable();
            queuedPlacementMaterial = blockItem;
            queuedPlacementState = expectedState;
            queuedPlacementYaw = candidate.yaw();
            queuedPlacementPitch = candidate.pitch();
            player.setYaw(candidate.yaw());
            player.setPitch(candidate.pitch());
            delay = 1;
            return;
        }

        if (targetMaterial == Items.REDSTONE && expectedState.isOf(Blocks.REDSTONE_WIRE)) {
            BlockPos support = target.down();
            BlockState supportState = mc.world.getBlockState(support);
            if (supportState.getCollisionShape(mc.world, support).isEmpty()) {
                stopInternal("Bau gestoppt: Redstone-Staub bei " + target.toShortString()
                        + " braucht einen gültigen Block direkt darunter.", true);
                return;
            }
            Vec3d hitPos = support.toCenterPos().add(0.0, 0.5, 0.0);
            if (player.getEyePos().squaredDistanceTo(hitPos)
                    > Math.min(REACH_SQUARED, player.getBlockInteractionRange() * player.getBlockInteractionRange())) {
                waitingForReach = true;
                status = "Warte auf Reichweite für Redstone-Staub";
                return;
            }
            BlockHitResult hit = new BlockHitResult(hitPos, Direction.UP, support, false);
            mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
            player.swingHand(Hand.MAIN_HAND);
            pendingTarget = target.toImmutable();
            pendingMaterial = Items.REDSTONE;
            pendingExpectedState = expectedState;
            pendingTicks = 0;
            delay = 2;
            status = "Setze Redstone-Staub " + (index + 1) + "/" + PLAN.size();
            return;
        }

        stopInternal("Bau gestoppt: Nicht unterstützter Nicht-BlockItem-Baustein "
                + targetMaterial.getName().getString() + ".", true);
    }

    private static void tickWaterPlacement(MinecraftClient mc, ClientPlayerEntity player) {
        if (!playerIsCreative(mc)) {
            preserveCheckpointOnStop = true;
            stopInternal("Wasserphase pausiert: Für das automatische Setzen der Wasserquellen musst du im Kreativmodus sein. Der gespeicherte Abschnitt kann später fortgesetzt werden.", true);
            return;
        }
        if (pendingWaterTarget != null) {
            BlockState current = mc.world.getBlockState(pendingWaterTarget);
            if (current.isOf(Blocks.WATER) && current.get(FluidBlock.LEVEL) == 0) {
                waterIndex++;
                pendingWaterTarget = null;
                pendingWaterTicks = 0;
                saveCheckpointProgress(waterIndex, WATER_PLAN.size());
                if (waterIndex == 1 || waterIndex % 25 == 0 || waterIndex == WATER_PLAN.size())
                    notifyPlayer("Wasserquellen bestätigt: " + waterIndex + "/" + WATER_PLAN.size() + ".");
                delay = 1;
                return;
            }
            if (!current.isReplaceable() && current.getFluidState().isEmpty()) {
                preserveCheckpointOnStop = true;
                stopInternal("Wasserplatzierung gestoppt: Das Ziel " + pendingWaterTarget.toShortString()
                        + " wurde durch " + current + " blockiert.", true);
                return;
            }
            if (++pendingWaterTicks < CONFIRMATION_TIMEOUT_TICKS) {
                delay = 1;
                return;
            }
            preserveCheckpointOnStop = true;
            stopInternal("Wasserplatzierung gestoppt: Der Server hat Wasser bei "
                    + pendingWaterTarget.toShortString() + " nicht bestätigt. Der Checkpoint bleibt für eine manuelle Prüfung erhalten.", true);
            return;
        }

        if (waterIndex >= WATER_PLAN.size()) return;
        BlockPos target = WATER_PLAN.get(waterIndex);
        BlockState present = mc.world.getBlockState(target);
        if (present.isOf(Blocks.WATER) && present.get(FluidBlock.LEVEL) == 0) {
            waterIndex++;
            saveCheckpointProgress(waterIndex, WATER_PLAN.size());
            return;
        }
        if (!present.isReplaceable() || !present.getFluidState().isEmpty()) {
            preserveCheckpointOnStop = true;
            stopInternal("Wasserplatzierung gestoppt: Das Ziel " + target.toShortString()
                    + " ist nicht mehr frei.", true);
            return;
        }

        int slot = findMaterialSlot(mc, player, Items.WATER_BUCKET);
        if (slot == MATERIAL_SWAP_PENDING) {
            status = "Hole Wassereimer aus dem Inventar";
            delay = 1;
            return;
        }
        if (slot < 0) {
            preserveCheckpointOnStop = true;
            stopInternal("Wasserplatzierung pausiert: Ein Wassereimer fehlt im Inventar. Der übrige Farmabschnitt bleibt gespeichert.", true);
            return;
        }
        if (player.getInventory().getSelectedSlot() != slot) {
            player.getInventory().setSelectedSlot(slot);
            delay = 1;
            return;
        }

        double reach = player.getBlockInteractionRange();
        BlockHitResult hit = null;
        for (Direction direction : Direction.values()) {
            BlockPos support = target.offset(direction);
            BlockState supportState = mc.world.getBlockState(support);
            if (supportState.getCollisionShape(mc.world, support).isEmpty()) continue;
            Direction face = direction.getOpposite();
            Vec3d hitPos = support.toCenterPos().add(face.getOffsetX() * 0.5,
                    face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (player.getEyePos().squaredDistanceTo(hitPos) > reach * reach) continue;
            hit = new BlockHitResult(hitPos, face, support, false);
            break;
        }
        if (hit == null) {
            if (placementApproachAttempts < 3 && AIPlayerController.navigateNear(target, 2)) {
                placementNavigationPending = true;
                placementApproachAttempts++;
                status = "Gehe zur Wasserstelle " + target.toShortString();
                return;
            }
            preserveCheckpointOnStop = true;
            stopInternal("Wasserplatzierung pausiert: Keine erreichbare Ansetzfläche bei "
                    + target.toShortString() + ". Geh näher heran oder setze diese Wasserstelle manuell.", true);
            return;
        }
        placementApproachAttempts = 0;
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.swingHand(Hand.MAIN_HAND);
        pendingWaterTarget = target.toImmutable();
        pendingWaterTicks = 0;
        status = "Setze Wasser " + (waterIndex + 1) + "/" + WATER_PLAN.size();
        delay = 2;
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
                saveCheckpointProgress(excavationIndex, EXCAVATION_PLAN.size());
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
            saveCheckpointProgress(excavationIndex, EXCAVATION_PLAN.size());
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
                        if (matchesPlacementState(expectedState, predicted))
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
                                        Item expectedMaterial) {
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

    private static String currentCheckpointScanKey(MinecraftClient mc) {
        return currentServerKey(mc) + "|" + mc.world.getRegistryKey().getValue();
    }

    private static void saveCheckpointProgress(int completed, int total) {
        int interval = Math.max(CHECKPOINT_PROGRESS_INTERVAL, total / 128);
        if (completed >= total || completed % interval == 0)
            saveCheckpoint();
    }

    private static void saveCheckpoint() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if ((!running && !prepared) || mc.world == null || blueprint == null) return;
        try {
            savedServerKey = currentServerKey(mc);
            if ("unknown".equals(savedServerKey)) {
                status = "Baufortschritt nicht gespeichert: Die Welt lässt sich nicht eindeutig zuordnen.";
                return;
            }
            JsonObject root = new JsonObject();
            root.addProperty("version", 6);
            root.addProperty("server", savedServerKey);
            root.addProperty("dimension", mc.world.getRegistryKey().getValue().toString());
            root.addProperty("name", blueprintName);
            root.addProperty("index", index);
            root.addProperty("excavationIndex", excavationIndex);
            root.addProperty("waterIndex", waterIndex);
            root.addProperty("materialName", materialName);
            root.addProperty("material", material == null ? "" : Registries.ITEM.getId(material).toString());
            root.addProperty("maxBlocks", preparedMaxBlocks);
            root.addProperty("maxHorizontal", preparedMaxHorizontal);
            root.addProperty("maxY", preparedMaxY);
            if (!schematicSourceFile.isBlank() && schematicPartX >= 0 && schematicPartY >= 0 && schematicPartZ >= 0) {
                root.addProperty("schematicFile", schematicSourceFile);
                root.addProperty("schematicPartX", schematicPartX);
                root.addProperty("schematicPartY", schematicPartY);
                root.addProperty("schematicPartZ", schematicPartZ);
            }
            JsonArray blocks = new JsonArray();
            for (BlockPos pos : PLAN) {
                Item item = PLAN_MATERIALS.getOrDefault(pos, material);
                if (item == null) throw new IllegalStateException("Material für gespeicherten Bauplan fehlt.");
                JsonObject block = new JsonObject();
                block.addProperty("x", pos.getX());
                block.addProperty("y", pos.getY());
                block.addProperty("z", pos.getZ());
                block.addProperty("item", Registries.ITEM.getId(item).toString());
                BlockState state = PLAN_STATES.get(pos);
                if (state == null && item instanceof BlockItem blockItem) state = blockItem.getBlock().getDefaultState();
                if (state == null) throw new IllegalStateException("Blockzustand für gespeicherten Bauplan fehlt.");
                block.addProperty("state", state.toString());
                blocks.add(block);
            }
            root.add("blocks", blocks);
            JsonArray water = new JsonArray();
            for (BlockPos pos : WATER_PLAN) {
                JsonObject block = new JsonObject();
                block.addProperty("x", pos.getX());
                block.addProperty("y", pos.getY());
                block.addProperty("z", pos.getZ());
                water.add(block);
            }
            root.add("water", water);
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
            Path temporaryFile = file.resolveSibling(file.getFileName().toString() + ".tmp");
            Files.writeString(temporaryFile, root.toString());
            try {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
            checkpointLoadAttempted = true;
            checkpointScanKey = currentCheckpointScanKey(mc);
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
        checkpointScanKey = currentCheckpointScanKey(mc);
        Path file = checkpointPath();
        if (!Files.exists(file)) return;
        try {
            if ("unknown".equals(currentServerKey(mc))) {
                status = "Gespeicherter Bau gefunden, aber diese Verbindung lässt sich nicht eindeutig zuordnen. Fortsetzen ist aus Sicherheitsgründen deaktiviert.";
                return;
            }
            if (Files.size(file) > 8L * 1024L * 1024L)
                throw new IllegalArgumentException("Die gespeicherte Bau-Datei überschreitet die erlaubte Größe.");
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            if (!root.has("version"))
                throw new IllegalArgumentException("Checkpoint-Version fehlt.");
            int checkpointVersion = root.get("version").getAsInt();
            if ((checkpointVersion != 2 && checkpointVersion != 3 && checkpointVersion != 4
                    && checkpointVersion != 5 && checkpointVersion != 6)
                    || !root.has("blocks") || !root.get("blocks").isJsonArray()
                    || root.getAsJsonArray("blocks").size() > 32_768
                    || !root.has("excavation") || !root.get("excavation").isJsonArray()
                    || root.getAsJsonArray("excavation").size() > MAX_EXCAVATION_BLOCKS
                    || (checkpointVersion >= 4 && (!root.has("water") || !root.get("water").isJsonArray()
                    || root.getAsJsonArray("water").size() > 32_768
                    || (root.getAsJsonArray("blocks").isEmpty() && root.getAsJsonArray("water").isEmpty()
                    && root.getAsJsonArray("excavation").isEmpty())))
                    || (checkpointVersion < 4 && root.getAsJsonArray("blocks").isEmpty()))
                throw new IllegalArgumentException("Ungültiges oder nicht unterstütztes Speicherformat.");
            String storedServer = root.get("server").getAsString();
            if (!storedServer.equals(currentServerKey(mc))) {
                status = "Ein gespeicherter Bau für einen anderen Server/eine andere Welt wurde gefunden. Verbinde dich mit der ursprünglichen Welt, um ihn fortzusetzen.";
                return;
            }
            Identifier storedDimension = Identifier.tryParse(root.get("dimension").getAsString());
            if (storedDimension == null || !storedDimension.equals(mc.world.getRegistryKey().getValue())) {
                status = "Gespeicherter Bau gefunden, aber die Dimension stimmt nicht. Wechsle zuerst in die ursprüngliche Dimension.";
                return;
            }
            PLAN.clear(); PLAN_MATERIALS.clear(); PLAN_STATES.clear();
            Set<BlockPos> restoredPlanPositions = new HashSet<>();
            for (var element : root.getAsJsonArray("blocks")) {
                JsonObject block = element.getAsJsonObject();
                BlockPos pos = new BlockPos(block.get("x").getAsInt(), block.get("y").getAsInt(), block.get("z").getAsInt());
                if (pos.getY() < mc.world.getBottomY()
                        || pos.getY() >= mc.world.getBottomY() + mc.world.getHeight()
                        || !restoredPlanPositions.add(pos))
                    throw new IllegalArgumentException("Der gespeicherte Bauplan enthält ungültige oder doppelte Blockpositionen.");
                Identifier itemId = Identifier.tryParse(block.get("item").getAsString());
                Item item = itemId == null ? Items.AIR : Registries.ITEM.get(itemId);
                if (itemId == null || item == Items.AIR || !Registries.ITEM.getId(item).equals(itemId))
                    throw new IllegalArgumentException("Ein gespeichertes Baumaterial existiert in dieser Minecraft-Version nicht mehr.");
                BlockState state;
                if (block.has("state")) {
                    state = BlockArgumentParser.block(mc.world.getRegistryManager().getOrThrow(RegistryKeys.BLOCK),
                            block.get("state").getAsString(), false).blockState();
                } else {
                    if (checkpointVersion >= 3 || !(item instanceof BlockItem blockItem)
                            || !blockItem.getBlock().getStateManager().getProperties().isEmpty())
                        throw new IllegalArgumentException("Der gespeicherte Bauplan enthält keinen sicheren Blockzustand für ein Material mit Eigenschaften.");
                    state = ((BlockItem) item).getBlock().getDefaultState();
                }
                if (item instanceof BlockItem blockItem) {
                    if (!state.isOf(blockItem.getBlock()))
                        throw new IllegalArgumentException("Ein gespeicherter BlockState passt nicht zum Baumaterial.");
                } else if (item != Items.REDSTONE || !state.isOf(Blocks.REDSTONE_WIRE)) {
                    throw new IllegalArgumentException("Ein gespeicherter Nicht-BlockItem-Baustein wird nicht unterstützt.");
                }
                PLAN.add(pos); PLAN_MATERIALS.put(pos, item); PLAN_STATES.put(pos, state);
            }
            WATER_PLAN.clear();
            Set<BlockPos> restoredWaterPositions = new HashSet<>();
            if (checkpointVersion >= 4) {
                for (var element : root.getAsJsonArray("water")) {
                    JsonObject block = element.getAsJsonObject();
                    BlockPos waterPos = new BlockPos(block.get("x").getAsInt(),
                            block.get("y").getAsInt(), block.get("z").getAsInt());
                    if (waterPos.getY() < mc.world.getBottomY()
                            || waterPos.getY() >= mc.world.getBottomY() + mc.world.getHeight()
                            || !restoredWaterPositions.add(waterPos) || restoredPlanPositions.contains(waterPos))
                        throw new IllegalArgumentException("Der gespeicherte Wasserplan enthält ungültige oder doppelte Positionen.");
                    WATER_PLAN.add(waterPos);
                }
            }
            EXCAVATION_PLAN.clear();
            EXCAVATION_EXPECTED_BLOCKS.clear();
            Set<BlockPos> restoredExcavationPositions = new HashSet<>();
            for (var element : root.getAsJsonArray("excavation")) {
                JsonObject block = element.getAsJsonObject();
                BlockPos excavationPos = new BlockPos(block.get("x").getAsInt(), block.get("y").getAsInt(), block.get("z").getAsInt());
                if (excavationPos.getY() < mc.world.getBottomY()
                        || excavationPos.getY() >= mc.world.getBottomY() + mc.world.getHeight()
                        || !restoredExcavationPositions.add(excavationPos)
                        || restoredPlanPositions.contains(excavationPos)
                        || restoredWaterPositions.contains(excavationPos))
                    throw new IllegalArgumentException("Der gespeicherte Aushub enthält ungültige, doppelte oder überlappende Positionen.");
                if (!block.has("expected"))
                    throw new IllegalArgumentException("Für einen gespeicherten Aushub-Block fehlt die erwartete Block-ID; aus Sicherheitsgründen wird nicht fortgesetzt.");
                Identifier expected = Identifier.tryParse(block.get("expected").getAsString());
                if (expected == null || !Registries.BLOCK.getId(Registries.BLOCK.get(expected)).equals(expected))
                    throw new IllegalArgumentException("Ein gespeicherter Aushub-Block ist in dieser Version unbekannt.");
                EXCAVATION_PLAN.add(excavationPos);
                EXCAVATION_EXPECTED_BLOCKS.put(excavationPos, expected);
            }
            if (!root.has("index") || !root.has("excavationIndex"))
                throw new IllegalArgumentException("Der gespeicherte Baufortschritt ist unvollständig.");
            int storedIndex = root.get("index").getAsInt();
            int storedExcavationIndex = root.get("excavationIndex").getAsInt();
            int storedWaterIndex = checkpointVersion >= 4 && root.has("waterIndex")
                    ? root.get("waterIndex").getAsInt() : 0;
            if (storedIndex < 0 || storedIndex > PLAN.size()
                    || storedExcavationIndex < 0 || storedExcavationIndex > EXCAVATION_PLAN.size()
                    || storedWaterIndex < 0 || storedWaterIndex > WATER_PLAN.size())
                throw new IllegalArgumentException("Der gespeicherte Baufortschritt liegt außerhalb des Bauplans.");
            index = storedIndex;
            excavationIndex = storedExcavationIndex;
            waterIndex = storedWaterIndex;
            pendingWaterTarget = null;
            pendingWaterTicks = 0;
            blueprintName = root.get("name").getAsString();
            if (blueprintName.isBlank() || blueprintName.length() > 48)
                throw new IllegalArgumentException("Der gespeicherte Bauplanname ist ungültig.");
            schematicSourceFile = "";
            schematicPartX = schematicPartY = schematicPartZ = -1;
            boolean hasSchematicIdentity = root.has("schematicFile") || root.has("schematicPartX")
                    || root.has("schematicPartY") || root.has("schematicPartZ");
            if (hasSchematicIdentity) {
                if (checkpointVersion < 5 || !root.has("schematicFile") || !root.has("schematicPartX")
                        || !root.has("schematicPartY") || !root.has("schematicPartZ"))
                    throw new IllegalArgumentException("Die gespeicherte Schematic-Abschnittskennung ist unvollständig.");
                schematicSourceFile = root.get("schematicFile").getAsString();
                schematicPartX = root.get("schematicPartX").getAsInt();
                schematicPartY = root.get("schematicPartY").getAsInt();
                schematicPartZ = root.get("schematicPartZ").getAsInt();
                String lowerFile = schematicSourceFile.toLowerCase(Locale.ROOT);
                if (schematicSourceFile.length() > 110
                        || (!lowerFile.endsWith(".schem") && !lowerFile.endsWith(".litematic"))
                        || schematicPartX < 0 || schematicPartX > 7 || schematicPartY < 0 || schematicPartY > 7
                        || schematicPartZ < 0 || schematicPartZ > 7)
                    throw new IllegalArgumentException("Die gespeicherte Schematic-Abschnittskennung ist ungültig.");
            }
            materialName = root.has("materialName") ? root.get("materialName").getAsString() : "Gespeicherte Materialien";
            Identifier primaryId = root.has("material") ? Identifier.tryParse(root.get("material").getAsString()) : null;
            BlockItem primary = primaryId != null && Registries.ITEM.get(primaryId) instanceof BlockItem item
                    ? item : PLAN_MATERIALS.values().stream()
                        .filter(BlockItem.class::isInstance).map(BlockItem.class::cast).findFirst().orElse(null);
            material = primary;
            blueprint = Blueprint.CUSTOM;
            dimension = storedDimension;
            savedServerKey = storedServer;
            preparedPlayerPos = mc.player.getBlockPos().toImmutable();
            preparedFacing = mc.player.getHorizontalFacing();
            preparedMaxBlocks = root.has("maxBlocks") ? root.get("maxBlocks").getAsInt() : 10_000;
            preparedMaxHorizontal = root.has("maxHorizontal") ? root.get("maxHorizontal").getAsInt() : 63;
            preparedMaxY = root.has("maxY") ? root.get("maxY").getAsInt() : 63;
            if (preparedMaxBlocks < 1 || preparedMaxBlocks > 32_768
                    || preparedMaxHorizontal < 1 || preparedMaxHorizontal > 255
                    || preparedMaxY < 1 || preparedMaxY > 255)
                throw new IllegalArgumentException("Die gespeicherten Baugrenzen sind ungültig.");
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
            int total = PLAN.size() + WATER_PLAN.size();
            int completed = index + waterIndex;
            int percent = total == 0 ? 100 : Math.min(100, completed * 100 / total);
            String phase = pendingWaterTarget != null ? "warte auf Wasser-Serverbestätigung"
                    : pendingTarget != null ? "warte auf Block-Serverbestätigung"
                    : pausedByUser ? "vom Spieler pausiert"
                    : waitingForReach ? "pausiert: nächstes Bauziel außerhalb der Reichweite"
                    : "läuft";
            String buildPhase = excavationIndex < EXCAVATION_PLAN.size() || excavationMinePending
                    ? "Ausgraben " + excavationIndex + "/" + EXCAVATION_PLAN.size()
                    : index < PLAN.size() ? "Feste Blöcke " + index + "/" + PLAN.size()
                    : waterIndex < WATER_PLAN.size() || pendingWaterTarget != null
                        ? "Wasserquellen " + waterIndex + "/" + WATER_PLAN.size()
                        : phase;
            return "Builder aktiv: " + blueprintName + ". Aushub: " + excavationIndex + "/"
                    + EXCAVATION_PLAN.size() + ". Feste Blöcke: " + index + "/" + PLAN.size()
                    + ". Wasserquellen: " + waterIndex + "/" + WATER_PLAN.size()
                    + (pendingWaterTarget != null ? " (eine Platzierung wird bestätigt)" : "")
                    + ". Gesamtfortschritt der Bau-/Wasserblöcke: " + completed + "/" + total
                    + " (" + percent + "%). Phase: " + buildPhase + ". Materialien: "
                    + (materialName.isBlank() ? "keine festen Baumaterialien" : materialName)
                    + ". Ein noch nicht bestätigter Schritt wird nicht zum Fortschritt gezählt.";
        }
        if (prepared) {
            return "Bauplan wartet auf Bestätigung: " + blueprintName + ", " + PLAN.size()
                    + " feste Blöcke und " + WATER_PLAN.size() + " Wasserquellen. Aushub: "
                    + excavationIndex + "/" + EXCAVATION_PLAN.size() + ". Materialien: "
                    + (materialName.isBlank() ? "keine festen Baumaterialien" : materialName)
                    + ". Noch wurde kein Schritt ausgeführt.";
        }
        return "Kein Bau läuft. Letzter Builder-Status: " + status;
    }

    public static void cancelPrepared() {
        if (running) return;
        if (!keepCheckpointDuringCancel) deleteCheckpoint();
        restoredCheckpoint = false;
        schematicSourceFile = "";
        schematicPartX = schematicPartY = schematicPartZ = -1;
        redstoneAdjustmentTarget = null;
        redstoneAdjustmentAttempts = 0;
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
        WATER_PLAN.clear();
        waterIndex = 0;
        pendingWaterTarget = null;
        pendingWaterTicks = 0;
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
        checkpointScanKey = "";
        preserveCheckpointOnStop = false;
    }

    private static void stopInternal(String message, boolean notify) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (message.startsWith("Bauabschnitt abgeschlossen:") && !schematicSourceFile.isBlank()
                && schematicPartX >= 0 && schematicPartY >= 0 && schematicPartZ >= 0) {
            if (!SchematicManager.markSectionComplete(schematicSourceFile, schematicPartX, schematicPartY, schematicPartZ))
                message += " WARNUNG: Die dauerhafte Abschnittsübersicht konnte nicht aktualisiert werden; prüfe get_schematic_progress.";
        }
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
        WATER_PLAN.clear();
        waterIndex = 0;
        pendingWaterTarget = null;
        pendingWaterTicks = 0;
        excavationIndex = 0;
        excavationRetries = 0;
        excavationApproachAttempts = 0;
        excavationMinePending = false;
        excavationNavigationPending = false;
        excavationWaitingNotice = false;
        pendingExcavationTarget = null;
        restoredCheckpoint = false;
        schematicSourceFile = "";
        schematicPartX = schematicPartY = schematicPartZ = -1;
        redstoneAdjustmentTarget = null;
        redstoneAdjustmentAttempts = 0;
        index = 0;
        preparedPlayerPos = null;
        preparedFacing = null;
    }
}
