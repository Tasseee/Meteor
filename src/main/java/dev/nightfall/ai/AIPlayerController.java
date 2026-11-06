package dev.nightfall.ai;

import dev.nightfall.gui.AIChatScreen;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.RecipeFinder;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.SlotDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.screen.AbstractCraftingScreenHandler;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.registry.Registries;
import net.minecraft.util.context.ContextParameterMap;
import net.minecraft.util.context.ContextType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Bounded, client-side movement and interaction controller for Nightfall AI.
 *
 * Navigation uses an A* search over already-loaded client chunks and never sends
 * arbitrary movement packets. Mining and eating use normal vanilla interactions;
 * the server remains authoritative for every result.
 */
public final class AIPlayerController {
    private enum Mode { NONE, WALK, NAVIGATE, SEARCH_STRUCTURES, MINE, EAT, CRAFT, SORT, GATHER, GATHER_MINE, GATHER_TUNNEL, GATHER_TUNNEL_MINE, SMELT }

    private static final int MAX_PATH_RADIUS = 48;
    private static final int NAVIGATION_SEGMENT_LENGTH = 32;
    private static final int MAX_SEARCH_NODES = 8_000;
    private static final int MAX_NAVIGATION_TICKS = 20 * 60 * 20;
    private static final int MAX_MINING_TICKS = 20 * 30;
    private static final int MAX_EATING_TICKS = 80;
    private static final double MIN_PROGRESS = 0.025;
    private static final int MAX_CRAFTING_TICKS = 20 * 60 * 5;
    private static final int MAX_CRAFT_CHAIN_DEPTH = 8;
    private static final int MAX_CRAFT_CHAIN_STEPS = 32;
    private static final int MAX_CRAFT_CHAIN_BATCHES = 256;
    private static final int MAX_CRAFTING_IDLE_TICKS = 20;
    private static final int MAX_GATHER_BLOCKS = 2_048;
    private static final int MAX_SMELT_ITEMS = 16;
    private static final int MAX_SMELT_TICKS = 20 * 60 * 4;
    private static final int MAX_SMELT_OPEN_TICKS = 40;
    private static final int MAX_GATHER_TARGETS = 4_096;
    private static final int MAX_GATHER_EXPLORE_LEGS = 64;
    private static final int GATHER_HORIZONTAL_RADIUS = 32;
    private static final int GATHER_VERTICAL_BELOW = 4;
    private static final int GATHER_VERTICAL_ABOVE = 16;
    private static final Set<String> GATHERABLE_SELF_DROP_BLOCKS = Set.of(
            "dirt", "coarse_dirt", "rooted_dirt",
            "cobblestone", "mossy_cobblestone",
            "granite", "diorite", "andesite", "tuff", "calcite", "dripstone_block",
            "moss_block", "sandstone", "red_sandstone", "cut_sandstone", "cut_red_sandstone",
            "netherrack", "end_stone", "blackstone", "basalt", "smooth_basalt",
            "cobbled_deepslate", "stone_bricks", "mossy_stone_bricks",
            "cracked_stone_bricks", "chiseled_stone_bricks", "bricks", "mud_bricks",
            "nether_bricks", "red_nether_bricks", "end_stone_bricks",
            "terracotta", "purpur_block", "quartz_block", "quartz_bricks");
    private static final int GATHER_ORE_VERTICAL_BELOW = 24;
    private static final int GATHER_ORE_VERTICAL_ABOVE = 12;
    private static final int MAX_ANCIENT_TUNNEL_LENGTH = 8;
    private static final int MAX_ANCIENT_TUNNEL_BLOCKS = 16;
    private static final int MAX_ANCIENT_DEBRIS_RESCANS = 64;
    private static final Set<String> ANCIENT_TUNNEL_BLOCKS = Set.of("netherrack", "basalt", "blackstone");
    private static final Map<String, String> GATHERABLE_ORE_DROPS = Map.ofEntries(
            Map.entry("minecraft:coal_ore", "minecraft:coal"),
            Map.entry("minecraft:deepslate_coal_ore", "minecraft:coal"),
            Map.entry("minecraft:iron_ore", "minecraft:raw_iron"),
            Map.entry("minecraft:deepslate_iron_ore", "minecraft:raw_iron"),
            Map.entry("minecraft:copper_ore", "minecraft:raw_copper"),
            Map.entry("minecraft:deepslate_copper_ore", "minecraft:raw_copper"),
            Map.entry("minecraft:gold_ore", "minecraft:raw_gold"),
            Map.entry("minecraft:deepslate_gold_ore", "minecraft:raw_gold"),
            Map.entry("minecraft:redstone_ore", "minecraft:redstone"),
            Map.entry("minecraft:deepslate_redstone_ore", "minecraft:redstone"),
            Map.entry("minecraft:lapis_ore", "minecraft:lapis_lazuli"),
            Map.entry("minecraft:deepslate_lapis_ore", "minecraft:lapis_lazuli"),
            Map.entry("minecraft:diamond_ore", "minecraft:diamond"),
            Map.entry("minecraft:deepslate_diamond_ore", "minecraft:diamond"),
            Map.entry("minecraft:emerald_ore", "minecraft:emerald"),
            Map.entry("minecraft:deepslate_emerald_ore", "minecraft:emerald"),
            Map.entry("minecraft:nether_quartz_ore", "minecraft:quartz"),
            Map.entry("minecraft:nether_gold_ore", "minecraft:gold_nugget"),
            Map.entry("minecraft:ancient_debris", "minecraft:ancient_debris"));
    private static final Map<String, String> SMELT_INPUT_OUTPUT = Map.of(
            "minecraft:raw_iron", "minecraft:iron_ingot",
            "minecraft:raw_gold", "minecraft:gold_ingot",
            "minecraft:raw_copper", "minecraft:copper_ingot",
            "minecraft:ancient_debris", "minecraft:netherite_scrap");
    private static final ContextParameterMap EMPTY_RECIPE_CONTEXT =
            new ContextParameterMap.Builder().build(new ContextType.Builder().build());

    private static Mode mode = Mode.NONE;
    private static int forwardTicks;
    private static int actionTicks;
    private static int stuckTicks;
    private static int pathIndex;
    private static double lastWaypointDistance = Double.MAX_VALUE;
    private static List<BlockPos> path = List.of();
    private static BlockPos navigationGoal;
    private static boolean navigationIsUserGoal;
    private static String structureSearchFilter = "any";
    private static int structureSearchDirection;
    private static int structureSearchLegLength;
    private static int structureSearchLegProgress;
    private static int structureSearchLegsAtLength;
    private static int structureSearchScanCooldown;
    private static int structureSearchScannedBlocks;
    private static BlockPos miningPos;
    private static BlockState miningState;
    private static Direction miningSide = Direction.UP;
    private static int miningToolSourceSlot = -1;
    private static int miningToolHotbarSlot = -1;
    private static ItemStack miningDisplacedStack = ItemStack.EMPTY;
    private static int originalSlot = -1;
    private static int foodSlot = -1;
    private static int foodCountBefore;
    private static int hungerBefore;
    private static String foodName = "";
    private static Item craftingTarget;
    private static String craftingName = "";
    private static int craftingCountBefore;
    private static int craftingGoalCount;
    private static int craftingTicks;
    private static int craftingIdleTicks;
    private static int craftingBatchesTaken;
    private static boolean craftingNeedsRefill;
    private static int craftingPendingBatchExpectedCount;
    private static int craftingPendingBatchWaitTicks;
    private static RecipeDisplayEntry craftingRecipe;
    private static AbstractCraftingScreenHandler craftingHandler;
    private static List<CraftingPlanStep> craftingPlan = List.of();
    private static int craftingPlanIndex;
    private static Item craftingFinalTarget;
    private static int craftingFinalCountBefore;
    private static int craftingFinalGoalCount;
    private static List<InventorySwap> inventorySortPlan = List.of();
    private static int inventorySortIndex;
    private static InventorySwap pendingInventorySwap;
    private static ItemStack pendingFirstStack = ItemStack.EMPTY;
    private static ItemStack pendingSecondStack = ItemStack.EMPTY;
    private static int inventorySortWaitTicks;
    private static Item gatherMaterial;
    private static boolean gatherRequiresSuitableTool;
    private static String gatherMaterialName = "";
    private static int gatherGoalCount;
    private static int gatherMaxDurationSeconds;
    private static long gatherDeadlineNanos;
    private static int gatherTargetY = Integer.MIN_VALUE;
    private static List<BlockPos> gatherTargets = List.of();
    private static int gatherIndex;
    private static int gatherBlocksBrokenThisRequest;
    private static Set<BlockPos> gatherVisitedTargets = new HashSet<>();
    private static Set<BlockPos> gatherExploreVisited = new HashSet<>();
    private static BlockPos gatherExploreOrigin;
    private static int gatherExploreStepIndex;
    private static int gatherExploreLegs;
    private static int gatherRescanCount;
    private static boolean gatherAwaitingPickup;
    private static int gatherPickupTicks;
    private static int gatherCountBeforeBreak;
    private static List<BlockPos> gatherTunnelCells = List.of();
    private static int gatherTunnelCellIndex;
    private static BlockPos gatherTunnelOreTarget;
    private static BlockPos smeltFurnacePos;
    private static Item smeltInput;
    private static Item smeltOutput;
    private static Item smeltFuelItem;
    private static int smeltAmount;
    private static int smeltFuelRequired;
    private static int smeltOutputCountBefore;
    private static int smeltTicks;
    private static int smeltOpenTicks;
    private static boolean smeltItemsLoaded;
    private static boolean smeltOutputPending;
    private static int smeltOutputCountBeforeClick;
    private static int smeltOutputWaitTicks;
    private static String lastActionSummary = "";

    private AIPlayerController() {}

    public static String walkForward(int ticks) {
        if (ticks < 1 || ticks > 100)
            throw new IllegalArgumentException("Die Laufdauer muss zwischen 1 und 100 Ticks liegen.");
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        stop(null);
        mode = Mode.WALK;
        forwardTicks = ticks;
        notifyPlayer(mc, "Laufe geradeaus · " + ticks + " Ticks. „Stopp“ bricht ab.");
        return "Vorwärtsbewegung gestartet (" + ticks + " Ticks).";
    }

    public static String previewStructureSearch(String structure) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        String filter = structure == null ? "any" : structure.trim().toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("any", "ancient_city", "trial_chambers", "fortress", "bastion", "stronghold").contains(filter))
            throw new IllegalArgumentException("Unterstützt werden any, ancient_city, trial_chambers, fortress, bastion und stronghold.");
        return "Nightfall erkundet nach deiner Bestätigung selbstständig die Umgebung zu Fuß und prüft geladene Blöcke auf typische Struktur-Baustoffmuster. "
                + "Suche: " + filter + ". Keine Serverbefehle, kein Teleport. Maximal fünf Minuten pro Suchlauf; bei Gefahr, Hindernissen oder Stopp wird abgebrochen. "
                + "Treffer sind Hinweise anhand von Blockmustern, keine garantierte Struktur-Bestätigung.";
    }

    public static String startStructureSearch(String structure) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        String filter = structure == null ? "any" : structure.trim().toLowerCase(java.util.Locale.ROOT);
        previewStructureSearch(filter);
        stop(null);
        structureSearchFilter = filter;
        structureSearchDirection = 0;
        structureSearchLegLength = 16;
        structureSearchLegProgress = 0;
        structureSearchLegsAtLength = 0;
        structureSearchScanCooldown = 0;
        structureSearchScannedBlocks = 0;
        actionTicks = 0;
        mode = Mode.SEARCH_STRUCTURES;
        if (!startNextStructureSearchLeg(mc)) {
            stop("Struktursuche gestoppt: Kein sicherer erster Wegabschnitt in der geladenen Umgebung.");
            return "Kein sicherer erster Erkundungsweg gefunden.";
        }
        notifyPlayer(mc, "Struktursuche gestartet · " + filter + " · du kannst jederzeit „Stopp“ sagen.");
        return "Selbstständige Strukturerkundung gestartet. Nightfall läuft in einer wachsenden Quadratspirale und meldet verdächtige Blockmuster.";
    }

    private static boolean startNextStructureSearchLeg(MinecraftClient mc) {
        int[][] directions = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        int[] direction = directions[structureSearchDirection & 3];
        BlockPos target = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                .add(direction[0] * structureSearchLegLength, 0, direction[1] * structureSearchLegLength);
        List<BlockPos> planned;
        try {
            planned = planNavigationSegment(mc, target);
        } catch (RuntimeException invalid) {
            return false;
        }
        if (planned.isEmpty()) return false;
        path = List.copyOf(planned);
        pathIndex = 0;
        navigationGoal = planned.get(planned.size() - 1).toImmutable();
        stuckTicks = 0;
        lastWaypointDistance = Double.MAX_VALUE;
        structureSearchLegProgress = 0;
        structureSearchDirection = (structureSearchDirection + 1) & 3;
        if (++structureSearchLegsAtLength >= 2) {
            structureSearchLegsAtLength = 0;
            structureSearchLegLength = Math.min(256, structureSearchLegLength + 16);
        }
        return true;
    }

    private static void scanForStructureHints(MinecraftClient mc) {
        if (structureSearchScanCooldown-- > 0) return;
        structureSearchScanCooldown = 40;
        BlockPos center = mc.player.getBlockPos();
        int minX = center.getX() - 8, maxX = center.getX() + 8;
        int minZ = center.getZ() - 8, maxZ = center.getZ() + 8;
        int minY = mc.world.getBottomY();
        int maxY = mc.world.getBottomY() + mc.world.getHeight() - 1;
        Map<String, Integer> counts = new HashMap<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc.world.isChunkLoaded(pos)) continue;
                    String id = Registries.BLOCK.getId(mc.world.getBlockState(pos).getBlock()).toString();
                    if (id.equals("minecraft:sculk") || id.equals("minecraft:sculk_catalyst")
                            || id.equals("minecraft:sculk_sensor") || id.equals("minecraft:sculk_shrieker")
                            || id.equals("minecraft:sculk_vein")) counts.merge("ancient_city", 1, Integer::sum);
                    if (id.equals("minecraft:sculk_catalyst") || id.equals("minecraft:sculk_shrieker"))
                        counts.merge("ancient_anchor", 1, Integer::sum);
                    if (id.equals("minecraft:polished_tuff") || id.equals("minecraft:chiseled_tuff")
                            || id.equals("minecraft:polished_tuff_slab") || id.equals("minecraft:polished_tuff_stairs")
                            || id.equals("minecraft:tuff_bricks") || id.equals("minecraft:chiseled_tuff_bricks")
                            || id.equals("minecraft:tuff_brick_slab") || id.equals("minecraft:tuff_brick_stairs")
                            || id.equals("minecraft:tuff_brick_wall")) counts.merge("trial_tuff", 1, Integer::sum);
                    if (id.equals("minecraft:copper_block") || id.equals("minecraft:cut_copper")
                            || id.equals("minecraft:exposed_copper") || id.equals("minecraft:weathered_copper")
                            || id.equals("minecraft:oxidized_copper") || id.equals("minecraft:copper_bulb")
                            || id.equals("minecraft:exposed_copper_bulb") || id.equals("minecraft:weathered_copper_bulb")
                            || id.equals("minecraft:oxidized_copper_bulb") || id.equals("minecraft:copper_grate")
                            || id.equals("minecraft:exposed_copper_grate") || id.equals("minecraft:weathered_copper_grate")
                            || id.equals("minecraft:oxidized_copper_grate") || id.equals("minecraft:copper_door")
                            || id.equals("minecraft:copper_trapdoor") || id.equals("minecraft:lightning_rod"))
                        counts.merge("trial_copper", 1, Integer::sum);
                    if (id.equals("minecraft:nether_bricks") || id.equals("minecraft:nether_brick_fence")
                            || id.equals("minecraft:nether_brick_stairs") || id.equals("minecraft:nether_brick_wall"))
                        counts.merge("fortress", 1, Integer::sum);
                    if (id.equals("minecraft:polished_blackstone") || id.equals("minecraft:polished_blackstone_bricks")
                            || id.equals("minecraft:cracked_polished_blackstone_bricks")
                            || id.equals("minecraft:chiseled_polished_blackstone") || id.equals("minecraft:gilded_blackstone")
                            || id.equals("minecraft:polished_blackstone_brick_slab")
                            || id.equals("minecraft:polished_blackstone_brick_stairs")
                            || id.equals("minecraft:polished_blackstone_brick_wall"))
                        counts.merge("bastion", 1, Integer::sum);
                    if (id.equals("minecraft:stone_bricks") || id.equals("minecraft:mossy_stone_bricks")
                            || id.equals("minecraft:cracked_stone_bricks") || id.equals("minecraft:chiseled_stone_bricks")
                            || id.equals("minecraft:iron_bars")) counts.merge("stronghold", 1, Integer::sum);
                    structureSearchScannedBlocks++;
                }
            }
        }
        String[] types = {"ancient_city", "trial_chambers", "fortress", "bastion", "stronghold"};
        int[] thresholds = {10, 24, 12, 16, 28};
        for (int i = 0; i < types.length; i++) {
            String type = types[i];
            if (!"any".equals(structureSearchFilter) && !structureSearchFilter.equals(type)) continue;
            boolean supportedPattern = type.equals("ancient_city")
                    ? counts.getOrDefault(type, 0) >= thresholds[i] && counts.getOrDefault("ancient_anchor", 0) >= 1
                    : type.equals("trial_chambers")
                    ? counts.getOrDefault("trial_tuff", 0) >= 10 && counts.getOrDefault("trial_copper", 0) >= 8
                    : counts.getOrDefault(type, 0) >= thresholds[i];
            if (supportedPattern) {
                BlockPos found = center.toImmutable();
                stop(null);
                notifyPlayer(mc, "Möglicher Strukturhinweis: " + type + " nahe X=" + found.getX()
                        + ", Y=" + found.getY() + ", Z=" + found.getZ()
                        + " (Blockmuster erkannt). Bitte vor Ort prüfen; keine sichere Bestätigung.");
                return;
            }
        }
    }

    /** Previews a coordinate request without starting movement. Y is the player's feet block. */
    public static String previewNavigation(int x, int y, int z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        BlockPos target = new BlockPos(x, y, z);
        validateNavigationRange(mc, target);
        return "Nightfall plant nach deiner Bestätigung einen lokalen Weg zum Standpunkt X=" + x
                + ", Y=" + y + ", Z=" + z + ". A*-Suche: nur geladene Chunks, sichere feste Standflächen, "
                + "maximal " + MAX_PATH_RADIUS + " Blöcke Suchradius. Bei fehlendem Weg wird gestoppt.";
    }

    public static String navigateTo(int x, int y, int z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        BlockPos target = new BlockPos(x, y, z);
        validateNavigationTarget(mc, target);
        if (isAtGoal(mc, target)) return "Du bist bereits am gewünschten Ziel.";
        List<BlockPos> planned = planNavigationSegment(mc, target);
        if (planned.isEmpty()) throw new IllegalArgumentException(
                "Kein sicherer erster Wegabschnitt gefunden. Die Umgebung muss geladen sein und eine begehbare Route enthalten.");
        stop(null);
        navigationGoal = target.toImmutable();
        navigationIsUserGoal = true;
        path = List.copyOf(planned);
        pathIndex = 0;
        actionTicks = 0;
        stuckTicks = 0;
        lastWaypointDistance = Double.MAX_VALUE;
        mode = Mode.NAVIGATE;
        notifyPlayer(mc, "Erster Wegabschnitt gefunden · " + path.size()
                + " Wegpunkte. Weitere Abschnitte werden automatisch geplant; „Stopp“ bricht ab.");
        return "Erster Wegabschnitt gefunden (" + path.size() + " Wegpunkte). Nightfall navigiert in sicheren, "
                + "nachgeladenen Abschnitten zum Ziel. Gesamtrouten dürfen länger als 48 Blöcke sein.";
    }

    /** Moves to a safe adjacent stand position; used for approaching a moving hostile. */
    public static boolean navigateNear(BlockPos target, int radius) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || target == null) return false;
        int boundedRadius = Math.max(1, Math.min(3, radius));
        List<BlockPos> candidates = new ArrayList<>();
        for (int dy = 1; dy >= -1; dy--) {
            for (int dx = -boundedRadius; dx <= boundedRadius; dx++) {
                for (int dz = -boundedRadius; dz <= boundedRadius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != boundedRadius) continue;
                    candidates.add(target.add(dx, dy, dz));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)));
        for (BlockPos candidate : candidates) {
            try {
                validateNavigationRange(mc, candidate);
                List<BlockPos> planned = findPath(mc, candidate);
                if (planned.isEmpty()) continue;
                stop(null);
                path = List.copyOf(planned);
                navigationGoal = candidate.toImmutable();
                navigationIsUserGoal = false;
                pathIndex = 0;
                actionTicks = 0;
                stuckTicks = 0;
                lastWaypointDistance = Double.MAX_VALUE;
                mode = Mode.NAVIGATE;
                return true;
            } catch (IllegalArgumentException ignored) {
                // Try the next adjacent stand position.
            }
        }
        return false;
    }

    public static String previewMine(int x, int y, int z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) throw new IllegalArgumentException("An dieser Position ist kein Block.");
        if (state.getHardness(mc.world, pos) < 0.0f)
            throw new IllegalArgumentException("Dieser Block ist nicht abbaubar.");
        if (state.hasBlockEntity())
            throw new IllegalArgumentException("Blöcke mit Inventar oder Block-Entity werden derzeit aus Sicherheitsgründen nicht automatisch abgebaut.");
        double reach = mc.player.getBlockInteractionRange();
        if (mc.player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > reach * reach)
            throw new IllegalArgumentException("Der Block liegt außerhalb deiner Abbaureichweite. Gehe näher heran.");
        return "Nightfall baut nach deiner Bestätigung " + state.getBlock().getName().getString()
                + " bei X=" + x + ", Y=" + y + ", Z=" + z + " mit dem geeignetsten Werkzeug aus deinem Inventar ab. "
                + "Es bevorzugt ein Werkzeug, das die Blockdrops korrekt ernten kann, holt es bei Bedarf per normalem Inventar-SWAP in die Hotbar "
                + "und stellt danach die vorherige Belegung wieder her. Der Abbau wird abgebrochen, falls der Server keine Blockänderung bestätigt.";
    }

    public static String previewLookedBlock() {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        if (!(mc.crosshairTarget instanceof BlockHitResult hit))
            throw new IllegalArgumentException("Du schaust gerade keinen Block direkt an. Richte das Fadenkreuz auf einen Block.");
        BlockPos pos = hit.getBlockPos();
        return previewMine(pos.getX(), pos.getY(), pos.getZ());
    }

    public static String mineLookedBlock() {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        if (!(mc.crosshairTarget instanceof BlockHitResult hit))
            throw new IllegalArgumentException("Du schaust gerade keinen Block direkt an. Richte das Fadenkreuz auf einen Block.");
        BlockPos pos = hit.getBlockPos();
        return mineBlock(pos.getX(), pos.getY(), pos.getZ());
    }

    public static String mineBlock(int x, int y, int z) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        BlockPos pos = new BlockPos(x, y, z);
        previewMine(x, y, z);
        stop(null);
        originalSlot = mc.player.getInventory().getSelectedSlot();
        int toolSlot = findBestMiningToolSlot(mc, mc.player, mc.world.getBlockState(pos));
        mc.player.getInventory().setSelectedSlot(toolSlot);
        aimAt(mc.player, pos.toCenterPos());
        BlockHitResult hit = raycastBlock(mc, pos);
        if (hit == null) {
            restoreMiningTool(mc);
            restoreHotbar(mc);
            throw new IllegalArgumentException(
                    "Der Block ist nicht direkt sichtbar. Entferne das Hindernis oder stelle dich so hin, dass Nightfall ihn erreichen kann.");
        }
        if (!mc.interactionManager.attackBlock(pos, hit.getSide())) {
            restoreMiningTool(mc);
            restoreHotbar(mc);
            throw new IllegalArgumentException("Der Minecraft-Client konnte den Abbau nicht starten.");
        }
        miningPos = pos.toImmutable();
        miningState = mc.world.getBlockState(pos);
        miningSide = hit.getSide();
        actionTicks = 0;
        mode = Mode.MINE;
        mc.options.attackKey.setPressed(true);
        notifyPlayer(mc, "Baue " + miningState.getBlock().getName().getString() + " ab. „Stopp“ bricht ab.");
        return "Abbau gestartet. Nightfall wartet auf die bestätigte Blockänderung der Welt.";
    }


    /**
     * Preview a bounded request to collect naturally occurring wood blocks nearby.
     * Only vanilla log/wood/stem/hyphae block-items whose block and item IDs match are accepted.
     */
    public static String previewGatherWood(String requestedItem, int targetCount) {
        return previewGatherWood(requestedItem, targetCount, 0, Integer.MIN_VALUE);
    }

    public static String previewGatherWood(String requestedItem, int targetCount, int durationSeconds, int targetY) {
        return previewGatherResource(requestedItem, targetCount, true, false, durationSeconds, targetY);
    }

    public static String gatherWood(String requestedItem, int targetCount) {
        return gatherWood(requestedItem, targetCount, 0, Integer.MIN_VALUE);
    }

    public static String gatherWood(String requestedItem, int targetCount, int durationSeconds, int targetY) {
        return gatherResource(requestedItem, targetCount, true, false, durationSeconds, targetY);
    }

    /** Preview a collection request for a whitelisted vanilla self-dropping block. */
    public static String previewGatherBlock(String requestedItem, int targetCount) {
        return previewGatherBlock(requestedItem, targetCount, 0);
    }

    public static String previewGatherBlock(String requestedItem, int targetCount, int durationSeconds) {
        return previewGatherResource(requestedItem, targetCount, false, false, durationSeconds, Integer.MIN_VALUE);
    }

    /** Start a confirmed collection task for a whitelisted vanilla self-dropping block. */
    public static String gatherBlock(String requestedItem, int targetCount) {
        return gatherBlock(requestedItem, targetCount, 0);
    }

    public static String gatherBlock(String requestedItem, int targetCount, int durationSeconds) {
        return gatherResource(requestedItem, targetCount, false, false, durationSeconds, Integer.MIN_VALUE);
    }

    /** Preview collection of known ore drops using vanilla-correct tool requirements. */
    public static String previewGatherOre(String requestedItem, int targetCount) {
        return previewGatherOre(requestedItem, targetCount, 0, Integer.MIN_VALUE);
    }

    public static String previewGatherOre(String requestedItem, int targetCount, int durationSeconds, int targetY) {
        return previewGatherResource(requestedItem, targetCount, false, true, durationSeconds, targetY);
    }

    /** Start a confirmed collection task for a known ore drop. */
    public static String gatherOre(String requestedItem, int targetCount) {
        return gatherOre(requestedItem, targetCount, 0, Integer.MIN_VALUE);
    }

    public static String gatherOre(String requestedItem, int targetCount, int durationSeconds, int targetY) {
        return gatherResource(requestedItem, targetCount, false, true, durationSeconds, targetY);
    }

    private static String previewGatherResource(String requestedItem, int targetCount,
                                                boolean woodOnly, boolean oreOnly) {
        return previewGatherResource(requestedItem, targetCount, woodOnly, oreOnly, 0, Integer.MIN_VALUE);
    }

    private static String previewGatherResource(String requestedItem, int targetCount,
                                                boolean woodOnly, boolean oreOnly,
                                                int durationSeconds, int targetY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        int maxTargetCount = oreOnly ? 256 : 1_024;
        if (targetCount < 1 || targetCount > maxTargetCount)
            throw new IllegalArgumentException("Die Zielmenge muss zwischen 1 und " + maxTargetCount + " liegen.");
        if (durationSeconds < 0 || durationSeconds > 1_200)
            throw new IllegalArgumentException("Die Laufzeit muss zwischen 0 und 1.200 Sekunden (20 Minuten) liegen.");
        if (targetY != Integer.MIN_VALUE) {
            if (!oreOnly) throw new IllegalArgumentException("Eine Zielhöhe wird nur bei Erz-Sammelaufträgen unterstützt.");
            if (targetY < mc.world.getBottomY() + 1 || targetY + 2 >= mc.world.getBottomY() + mc.world.getHeight())
                throw new IllegalArgumentException("Die gewünschte Zielhöhe liegt außerhalb der Weltgrenzen.");
            if (isAncientDebrisItem(resolveGatherableItem(requestedItem, woodOnly, oreOnly)) && !isInNether(mc))
                throw new IllegalArgumentException("Ancient Debris kann nur im Nether gesammelt werden.");
        }
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler)
            throw new IllegalArgumentException("Schließe zuerst den geöffneten Container.");
        Item item = resolveGatherableItem(requestedItem, woodOnly, oreOnly);
        if (isAncientDebrisItem(item) && !isInNether(mc))
            throw new IllegalArgumentException("Ancient Debris kann nur im Nether gefunden werden. Wechsle zuerst in den Nether und gehe in die Tiefen, die du absuchen möchtest.");
        int current = countInventoryItem(mc, item);
        int missing = targetCount - current;
        if (missing <= 0)
            throw new IllegalArgumentException("Du hast bereits " + current + " × " + item.getName().getString()
                    + " im Inventar. Die gewünschte Zielmenge ist erreicht.");
        int inventoryCapacity = availableInventoryCapacity(mc, item);
        if (inventoryCapacity <= 0)
            throw new IllegalArgumentException("Dein Inventar hat keinen freien Platz für "
                    + item.getName().getString() + ". Räume zuerst einen Slot frei.");

        boolean needsHeightMove = targetY != Integer.MIN_VALUE && mc.player.getBlockY() != targetY;
        List<BlockPos> candidates = needsHeightMove ? List.of() : findGatherableBlocks(mc, item, oreOnly);
        if (needsHeightMove) {
            if (findSafeGatherHeightPlan(mc, targetY) == null)
                throw new IllegalArgumentException("Ich finde keinen sicheren Weg zu einer begehbaren Position auf Y=" + targetY
                        + " in den aktuell geladenen Chunks. Gehe näher an einen sicheren Abstieg oder lade die Umgebung.");
        } else if (candidates.isEmpty() && durationSeconds <= 0) {
            throw new IllegalArgumentException("Im geladenen Umkreis von " + GATHER_HORIZONTAL_RADIUS
                    + " Blöcken habe ich keine passenden " + item.getName().getString()
                    + " gefunden. Für einen zeitbegrenzten Erkundungsauftrag kannst du stattdessen eine Laufzeit angeben.");
        }
        int toolEligible = needsHeightMove || candidates.isEmpty() ? 0 : countToolEligibleTargets(mc, candidates, oreOnly);
        if (!needsHeightMove && !candidates.isEmpty() && toolEligible == 0)
            throw new IllegalArgumentException("Ich habe " + candidates.size() + " passende Blöcke gefunden, "
                    + "aber für diese Blöcke fehlt ein geeignetes Werkzeug. Lege das passende Werkzeug in dein Inventar.");
        int available = Math.min(missing, inventoryCapacity);
        if (!needsHeightMove && !candidates.isEmpty())
            available = Math.min(available, Math.min(MAX_GATHER_BLOCKS, toolEligible));
        String kind = woodOnly ? "Holzblöcke" : oreOnly ? "Erzblöcke" : "freigegebene Rohstoffblöcke";
        String ancientNote = isAncientDebrisItem(item)
                ? " Für Ancient Debris prüft Nightfall zusätzlich kurze horizontale Stollen (max. "
                    + MAX_ANCIENT_TUNNEL_LENGTH + " Schritte) durch Netherrack, Basalt und Schwarzstein; "
                    + "nur mit passender Spitzhacke und nur wenn im Tunnel kein Lava-/Flüssigkeitsrisiko erkannt wird."
                : "";
        String runtimeNote = durationSeconds > 0
                ? " Laufzeitlimit: " + durationSeconds + " Sekunden; die Aktion endet früher, wenn das Mengenziel erreicht ist."
                : " Ohne Laufzeitlimit endet die Aktion beim Mengenziel oder wenn keine sicheren Ziele/Inventarplätze mehr verfügbar sind.";
        String heightNote = needsHeightMove ? " Nightfall sucht zuerst einen sicheren Weg zu Y=" + targetY + " und beginnt dort mit dem Scannen."
                : targetY != Integer.MIN_VALUE ? " Sammelhöhe: Y=" + targetY + "." : "";
        String scanNote = candidates.isEmpty() && durationSeconds > 0
                ? " Im aktuellen Scan gibt es noch keine passenden Ziele; Nightfall versucht zuerst einen sicheren Erkundungsweg und scannt nach neu geladenen Chunks erneut."
                : "";
        return "Nightfall sammelt nach deiner Bestätigung bis zu " + available + " zusätzliche "
                + item.getName().getString() + " (Ziel: " + targetCount + " im Inventar). "
                + (needsHeightMove ? "Nach dem sicheren Höhenwechsel wird neu gescannt." :
                    "Gefunden: " + candidates.size() + " passende " + kind + " im bereits geladenen Umkreis; "
                    + toolEligible + " davon haben ein verfügbares passendes Werkzeug. ")
                + "Bis zu " + MAX_GATHER_BLOCKS + " abgebauten Zielblöcken pro Langauftrag und höchstens 20 Minuten. "
                + "Nightfall sucht sichere Standpunkte, nutzt bei toolpflichtigen Blöcken nur ein geeignetes Werkzeug "
                + "und wartet auf Block- und Item-Updates. Freier Inventarplatz für " + inventoryCapacity
                + " weitere Items." + runtimeNote + heightNote + scanNote + ancientNote;
    }

    private static String gatherResource(String requestedItem, int targetCount,
                                         boolean woodOnly, boolean oreOnly) {
        return gatherResource(requestedItem, targetCount, woodOnly, oreOnly, 0, Integer.MIN_VALUE);
    }

    private static String gatherResource(String requestedItem, int targetCount,
                                         boolean woodOnly, boolean oreOnly,
                                         int durationSeconds, int targetY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        previewGatherResource(requestedItem, targetCount, woodOnly, oreOnly, durationSeconds, targetY);
        Item item = resolveGatherableItem(requestedItem, woodOnly, oreOnly);
        HeightNavigationPlan heightPlan = targetY != Integer.MIN_VALUE && mc.player.getBlockY() != targetY
                ? findSafeGatherHeightPlan(mc, targetY) : null;

        stop(null);
        gatherMaterial = item;
        gatherRequiresSuitableTool = oreOnly;
        gatherMaterialName = item.getName().getString();
        gatherGoalCount = targetCount;
        gatherMaxDurationSeconds = durationSeconds;
        gatherDeadlineNanos = durationSeconds > 0
                ? System.nanoTime() + durationSeconds * 1_000_000_000L : 0L;
        gatherTargetY = targetY;
        gatherTargets = List.of();
        gatherIndex = 0;
        gatherBlocksBrokenThisRequest = 0;
        gatherAwaitingPickup = false;
        gatherPickupTicks = 0;
        gatherCountBeforeBreak = 0;

        if (heightPlan != null && !heightPlan.path().isEmpty()) {
            path = List.copyOf(heightPlan.path());
            pathIndex = 0;
            navigationGoal = heightPlan.goal().toImmutable();
            actionTicks = 0;
            stuckTicks = 0;
            lastWaypointDistance = Double.MAX_VALUE;
            mode = Mode.NAVIGATE;
            statusNotice(mc, "Sammelauftrag gestartet · gehe sicher zu Y=" + targetY
                    + " und beginne dort mit der Suche. Du kannst jederzeit „Stopp“ sagen.");
        } else {
            if (!refreshGatherTargets(mc) && !startGatherExploration(mc)) {
                stop(null);
                throw new IllegalArgumentException("Im geladenen Bereich wurden keine Ressourcen gefunden und es gibt keinen sicheren, geladenen Weg zu einem weiteren Suchabschnitt.");
            }
            if (mode != Mode.NAVIGATE) mode = Mode.GATHER;
            statusNotice(mc, "Sammle " + gatherMaterialName + " · Ziel " + targetCount
                    + " im Inventar. Nachscans bleiben auf geladene Chunks und sichere Wege begrenzt.");
        }
        return (woodOnly ? "Holzsammeln" : oreOnly ? "Erzabbau" : "Materialsammlung") + " gestartet. "
                + "Ziel: " + targetCount + " im Inventar."
                + (durationSeconds > 0 ? " Maximal " + durationSeconds + " Sekunden." : "")
                + (targetY != Integer.MIN_VALUE ? " Zielhöhe Y=" + targetY + "." : "")
                + " Nightfall scannt wiederholt geladene Chunks, navigiert über sichere Wege und wartet auf bestätigte Drops. „Stopp“ bricht ab.";
    }

    private static Item resolveGatherableItem(String requestedItem, boolean woodOnly, boolean oreOnly) {
        if (requestedItem == null || requestedItem.isBlank() || requestedItem.length() > 64)
            throw new IllegalArgumentException("Gib eine exakte Vanilla-Item-ID wie minecraft:oak_log oder minecraft:cobblestone an.");
        Identifier id = Identifier.tryParse(requestedItem.trim());
        if (id == null || !id.getNamespace().equals("minecraft"))
            throw new IllegalArgumentException("Bitte gib eine gültige Vanilla-Item-ID an.");
        Item item = Registries.ITEM.get(id);
        if (!Registries.ITEM.getId(item).equals(id))
            throw new IllegalArgumentException("Diese Item-ID ist in Minecraft nicht registriert.");
        if (oreOnly) {
            if (!GATHERABLE_ORE_DROPS.containsValue(id.toString()))
                throw new IllegalArgumentException("Diese Erz-Drop-ID wird noch nicht unterstützt. Nutze z. B. minecraft:coal, minecraft:raw_iron, minecraft:raw_copper, minecraft:raw_gold, minecraft:redstone, minecraft:lapis_lazuli, minecraft:diamond, minecraft:emerald, minecraft:quartz, minecraft:gold_nugget oder minecraft:ancient_debris.");
            return item;
        }
        if (!(item instanceof BlockItem blockItem) || !Registries.BLOCK.getId(blockItem.getBlock()).equals(id)
                || !isGatherableResourcePath(id.getPath(), woodOnly))
            throw new IllegalArgumentException(woodOnly
                    ? "Nightfall sammelt derzeit nur Vanilla-Holzblöcke, die als derselbe Itemtyp droppen (z. B. minecraft:oak_log oder minecraft:crimson_stem)."
                    : "Dieser Block ist nicht in der sicheren Selbst-Drop-Liste. Unterstützt werden Holzblöcke und ausgewählte Vanilla-Blöcke wie dirt, cobblestone, granite, tuff, sandstone, netherrack und stone_bricks.");
        return blockItem;
    }

    private static boolean isGatherableResourcePath(String path, boolean woodOnly) {
        if (isGatherableWoodPath(path)) return true;
        return !woodOnly && GATHERABLE_SELF_DROP_BLOCKS.contains(path);
    }

    private static boolean isGatherableWoodPath(String path) {
        return path.endsWith("_log") || path.endsWith("_wood")
                || path.endsWith("_stem") || path.endsWith("_hyphae");
    }

    private static boolean hasSuitableMiningTool(MinecraftClient mc, BlockState state, boolean enforceTool) {
        if (!enforceTool && !state.isToolRequired()) return true;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.isSuitableFor(state)) return true;
        }
        return false;
    }

    private static int countToolEligibleTargets(MinecraftClient mc, List<BlockPos> candidates, boolean enforceTool) {
        int count = 0;
        for (BlockPos pos : candidates) {
            BlockState state = mc.world.getBlockState(pos);
            if (hasSuitableMiningTool(mc, state, enforceTool)) count++;
        }
        return count;
    }

    private static List<BlockPos> findGatherableBlocks(MinecraftClient mc, Item targetItem, boolean oreOnly) {
        return findGatherableBlocks(mc, targetItem, oreOnly, Set.of());
    }

    private static List<BlockPos> findGatherableBlocks(MinecraftClient mc, Item targetItem, boolean oreOnly,
                                                        Set<BlockPos> excludedTargets) {
        BlockPos center = mc.player.getBlockPos();
        int minX = center.getX() - GATHER_HORIZONTAL_RADIUS;
        int maxX = center.getX() + GATHER_HORIZONTAL_RADIUS;
        int minZ = center.getZ() - GATHER_HORIZONTAL_RADIUS;
        int maxZ = center.getZ() + GATHER_HORIZONTAL_RADIUS;
        int minY = Math.max(mc.world.getBottomY(), center.getY() - (oreOnly ? GATHER_ORE_VERTICAL_BELOW : GATHER_VERTICAL_BELOW));
        int maxY = Math.min(mc.world.getBottomY() + mc.world.getHeight() - 1,
                center.getY() + (oreOnly ? GATHER_ORE_VERTICAL_ABOVE : GATHER_VERTICAL_ABOVE));
        List<BlockPos> found = new ArrayList<>();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (excludedTargets.contains(cursor) || !mc.world.isChunkLoaded(cursor)) continue;
                    BlockState state = mc.world.getBlockState(cursor);
                    if (!isGatherableSourceBlock(state, targetItem, oreOnly) || state.hasBlockEntity()
                            || state.getBlock() instanceof net.minecraft.block.FallingBlock
                            || !state.getFluidState().isEmpty()
                            || state.getHardness(mc.world, cursor) < 0.0f
                            || (isAncientDebrisItem(targetItem) && !isSafeAncientDebrisTarget(mc, cursor))) continue;
                    found.add(cursor.toImmutable());
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
        if (found.size() > MAX_GATHER_TARGETS)
            return new ArrayList<>(found.subList(0, MAX_GATHER_TARGETS));
        return found;
    }

    /**
     * Resolves only explicitly supported drops. Ore targets are items (not necessarily blocks)
     * and are matched against a fixed source-block to drop-item table.
     */
    private static boolean isGatherableSourceBlock(BlockState state, Item targetItem, boolean oreOnly) {
        String itemId = Registries.ITEM.getId(targetItem).toString();
        if (oreOnly) {
            String sourceId = Registries.BLOCK.getId(state.getBlock()).toString();
            return itemId.equals(GATHERABLE_ORE_DROPS.get(sourceId));
        }
        if (!(targetItem instanceof BlockItem blockItem)) return false;
        if ("minecraft:cobblestone".equals(itemId)
                && (state.isOf(Blocks.STONE) || state.isOf(Blocks.COBBLESTONE))) return true;
        if ("minecraft:cobbled_deepslate".equals(itemId)
                && (state.isOf(Blocks.DEEPSLATE) || state.isOf(Blocks.COBBLED_DEEPSLATE))) return true;
        return state.isOf(blockItem.getBlock());
    }

    private static void tickGather(MinecraftClient mc) {
        if (gatherMaterial == null) {
            stop("Materialsammlung gestoppt: Es gibt keinen gültigen Sammelplan mehr.");
            return;
        }
        int currentCount = countInventoryItem(mc, gatherMaterial);
        if (currentCount >= gatherGoalCount) {
            stop("Materialsammlung abgeschlossen: " + currentCount + "/" + gatherGoalCount
                    + " " + gatherMaterialName + " im Inventar.");
            return;
        }
        if (availableInventoryCapacity(mc, gatherMaterial) <= 0) {
            stop("Materialsammlung gestoppt: Dein Inventar ist voll. Im Inventar liegen "
                    + currentCount + "/" + gatherGoalCount + " " + gatherMaterialName + ".");
            return;
        }
        if (gatherAwaitingPickup) {
            if (currentCount > gatherCountBeforeBreak) {
                gatherAwaitingPickup = false;
                gatherPickupTicks = 0;
                advanceGatherTarget();
                return;
            }
            if (++gatherPickupTicks > 40) {
                gatherAwaitingPickup = false;
                gatherPickupTicks = 0;
                advanceGatherTarget();
                statusNotice(mc, "Der letzte Holzblock wurde abgebaut, aber sein Drop kam nicht im Inventar an. Ich überspringe ihn.");
            } else {
                return;
            }
        }

        if (gatherBlocksBrokenThisRequest >= MAX_GATHER_BLOCKS) {
            stop("Sammelauftrag sicher begrenzt: " + currentCount + "/" + gatherGoalCount + " "
                    + gatherMaterialName + " im Inventar. Das Langauftragslimit von "
                    + MAX_GATHER_BLOCKS + " abgebauten Zielblöcken ist erreicht.");
            return;
        }

        while (gatherIndex < gatherTargets.size()) {
            BlockPos target = gatherTargets.get(gatherIndex);
            BlockState state = mc.world.getBlockState(target);
            if (!isGatherableSourceBlock(state, gatherMaterial, gatherRequiresSuitableTool) || state.hasBlockEntity()
                    || state.getBlock() instanceof net.minecraft.block.FallingBlock
                    || !state.getFluidState().isEmpty() || state.getHardness(mc.world, target) < 0.0f
                    || (isAncientDebrisItem(gatherMaterial) && !isSafeAncientDebrisTarget(mc, target))
                    || !hasSuitableMiningTool(mc, state, gatherRequiresSuitableTool)) {
                advanceGatherTarget();
                continue;
            }

            aimAt(mc.player, target.toCenterPos());
            double reach = mc.player.getBlockInteractionRange();
            boolean inReach = mc.player.getEyePos().squaredDistanceTo(target.toCenterPos()) <= reach * reach;
            BlockHitResult hit = inReach ? raycastBlock(mc, target) : null;
            if (hit == null) {
                if (startGatherApproach(mc, target)) return;
                if (isAncientDebrisItem(gatherMaterial) && startAncientDebrisTunnel(mc, target)) return;
                advanceGatherTarget();
                continue;
            }

            try {
                originalSlot = mc.player.getInventory().getSelectedSlot();
                int toolSlot = findBestMiningToolSlot(mc, mc.player, state);
                mc.player.getInventory().setSelectedSlot(toolSlot);
                aimAt(mc.player, target.toCenterPos());
                hit = raycastBlock(mc, target);
                if (hit == null || !mc.interactionManager.attackBlock(target, hit.getSide())) {
                    restoreMiningTool(mc);
                    restoreHotbar(mc);
                    advanceGatherTarget();
                    continue;
                }
            } catch (RuntimeException failure) {
                restoreMiningTool(mc);
                restoreHotbar(mc);
                stop("Materialsammlung gestoppt: Passendes Werkzeug konnte nicht sicher gewechselt werden.");
                return;
            }

            miningPos = target.toImmutable();
            miningState = state;
            miningSide = hit.getSide();
            actionTicks = 0;
            gatherCountBeforeBreak = currentCount;
            mode = Mode.GATHER_MINE;
            mc.options.attackKey.setPressed(true);
            statusNotice(mc, "Baue " + gatherMaterialName + " bei " + target.toShortString() + " ab.");
            return;
        }

        if (refreshGatherTargets(mc)) return;
        if (startGatherExploration(mc)) return;
        stop("Materialsammlung beendet: " + currentCount + "/" + gatherGoalCount + " "
                + gatherMaterialName + " im Inventar. Es gibt im geladenen Bereich keine weiteren sicheren Ziele oder Suchwege.");
    }


    private static void advanceGatherTarget() {
        if (gatherIndex < gatherTargets.size())
            gatherVisitedTargets.add(gatherTargets.get(gatherIndex).toImmutable());
        gatherIndex++;
    }

    /** Re-scan loaded nearby chunks so long gathering tasks can process more than one snapshot. */
    private static boolean refreshGatherTargets(MinecraftClient mc) {
        if (gatherMaterial == null || gatherRescanCount >= MAX_ANCIENT_DEBRIS_RESCANS) return false;
        gatherRescanCount++;
        List<BlockPos> candidates = findGatherableBlocks(mc, gatherMaterial, gatherRequiresSuitableTool, gatherVisitedTargets);
        List<BlockPos> usableTargets = new ArrayList<>();
        for (BlockPos pos : candidates) {
            BlockState state = mc.world.getBlockState(pos);
            if (hasSuitableMiningTool(mc, state, gatherRequiresSuitableTool)) usableTargets.add(pos);
        }
        if (usableTargets.isEmpty()) return false;
        gatherTargets = List.copyOf(usableTargets);
        gatherIndex = 0;
        if (gatherRescanCount > 1) {
            statusNotice(mc, "Nightfall scannt erneut um deine aktuelle Position (" + gatherRescanCount
                    + "/" + MAX_ANCIENT_DEBRIS_RESCANS + ") und nimmt nur neue, geladene Ziele auf.");
        }
        return true;
    }

    /**
     * Systematic square-spiral exploration around the task's starting area.
     * Only already-loaded, walkable positions are eligible; reaching the next
     * tile loads nearby chunks so the following rescan can inspect more terrain.
     */
    private static boolean startGatherExploration(MinecraftClient mc) {
        if (gatherMaterial == null || gatherRescanCount >= MAX_ANCIENT_DEBRIS_RESCANS
                || gatherExploreLegs >= MAX_GATHER_EXPLORE_LEGS
                || mc.player == null || mc.world == null) return false;
        if (gatherExploreOrigin == null)
            gatherExploreOrigin = mc.player.getBlockPos().toImmutable();

        int searchedCells = 0;
        while (searchedCells < 8 && gatherExploreStepIndex < MAX_GATHER_EXPLORE_LEGS * 8) {
            int[] offset = squareSpiralOffset(gatherExploreStepIndex++);
            searchedCells++;
            int centerX = gatherExploreOrigin.getX() + offset[0] * NAVIGATION_SEGMENT_LENGTH;
            int centerZ = gatherExploreOrigin.getZ() + offset[1] * NAVIGATION_SEGMENT_LENGTH;
            int baseY = gatherTargetY != Integer.MIN_VALUE ? gatherTargetY : gatherExploreOrigin.getY();
            int minDeltaY = gatherTargetY != Integer.MIN_VALUE ? 0 : -2;
            int maxDeltaY = gatherTargetY != Integer.MIN_VALUE ? 0 : 2;
            List<BlockPos> candidates = new ArrayList<>();

            for (int radius = 0; radius <= 3; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                        for (int deltaY = minDeltaY; deltaY <= maxDeltaY; deltaY++) {
                            BlockPos candidate = new BlockPos(centerX + dx, baseY + deltaY, centerZ + dz);
                            if (gatherExploreVisited.contains(candidate)
                                    || !canStandAt(mc, candidate, mc.player)) continue;
                            candidates.add(candidate);
                        }
                    }
                }
                if (!candidates.isEmpty()) break;
            }

            candidates.sort(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(
                    pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)));
            int routeAttempts = 0;
            for (BlockPos candidate : candidates) {
                gatherExploreVisited.add(candidate.toImmutable());
                List<BlockPos> planned = planNavigationSegment(mc, candidate);
                if (planned.isEmpty()) {
                    if (++routeAttempts >= 12) break;
                    continue;
                }
                gatherExploreLegs++;
                path = List.copyOf(planned);
                pathIndex = 0;
                navigationGoal = candidate.toImmutable();
                actionTicks = 0;
                stuckTicks = 0;
                lastWaypointDistance = Double.MAX_VALUE;
                mode = Mode.NAVIGATE;
                statusNotice(mc, "Systematische Rohstoffsuche: sicherer Spiral-Suchabschnitt "
                        + gatherExploreLegs + "/" + MAX_GATHER_EXPLORE_LEGS
                        + ". Nach dem Laden der Umgebung scanne ich neue Chunks erneut.");
                return true;
            }
        }
        return false;
    }

    /** Clockwise square spiral: first tile east of the origin, then expanding rings. */
    private static int[] squareSpiralOffset(int step) {
        long number = (long) step + 2L; // Skip the origin tile.
        int ring = (int) Math.ceil((Math.sqrt(number) - 1.0) / 2.0);
        int side = ring * 2;
        long firstInRing = (long) (2 * ring - 1) * (2 * ring - 1) + 1L;
        int offset = (int) (number - firstInRing);

        if (offset < side) return new int[]{ring, -ring + 1 + offset};
        offset -= side;
        if (offset < side) return new int[]{ring - 1 - offset, ring};
        offset -= side;
        if (offset < side) return new int[]{-ring, ring - 1 - offset};
        offset -= side;
        return new int[]{-ring + 1 + offset, -ring};
    }

    /** Compatibility helper retained for the Ancient Debris tunnel flow. */
    private static boolean refreshAncientDebrisTargets(MinecraftClient mc) {
        return isInNether(mc) && isAncientDebrisItem(gatherMaterial) && refreshGatherTargets(mc);
    }

    /** Plan a safe route to a nearby loaded, walkable position at the exact requested feet-level Y. */
    private static HeightNavigationPlan findSafeGatherHeightPlan(MinecraftClient mc, int targetY) {
        BlockPos origin = mc.player.getBlockPos();
        if (origin.getY() == targetY) return new HeightNavigationPlan(origin, List.of());
        List<BlockPos> candidates = new ArrayList<>();
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    BlockPos feet = new BlockPos(origin.getX() + dx, targetY, origin.getZ() + dz);
                    if (canStandAt(mc, feet, mc.player)) candidates.add(feet);
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(p -> mc.player.squaredDistanceTo(
                p.getX() + 0.5, p.getY(), p.getZ() + 0.5)));
        int attempts = 0;
        for (BlockPos candidate : candidates) {
            List<BlockPos> planned = planNavigationSegment(mc, candidate);
            if (!planned.isEmpty()) return new HeightNavigationPlan(candidate.toImmutable(), List.copyOf(planned));
            if (++attempts >= 24) break;
        }
        return null;
    }

    private static boolean isAncientDebrisItem(Item item) {
        return item != null && "minecraft:ancient_debris".equals(Registries.ITEM.getId(item).toString());
    }

    private static boolean isInNether(MinecraftClient mc) {
        return mc.world != null && "the_nether".equals(mc.world.getRegistryKey().getValue().getPath());
    }

    /** Starts a short, level 1x2 tunnel to one side of an Ancient Debris block. */
    private static boolean startAncientDebrisTunnel(MinecraftClient mc, BlockPos oreTarget) {
        if (!isInNether(mc) || !isAncientDebrisItem(gatherMaterial)) return false;
        BlockState ore = mc.world.getBlockState(oreTarget);
        if (!isSafeAncientDebrisTarget(mc, oreTarget) || !hasSuitableMiningTool(mc, ore, true)) return false;
        List<BlockPos> route = findAncientDebrisTunnelRoute(mc, oreTarget);
        if (route.isEmpty()) return false;
        gatherTunnelOreTarget = oreTarget.toImmutable();
        gatherTunnelCells = List.copyOf(route);
        gatherTunnelCellIndex = 0;
        mode = Mode.GATHER_TUNNEL;
        statusNotice(mc, "Ancient Debris liegt verdeckt. Grabe einen geprüften Nether-Stollen ("
                + route.size() + " Schritte) bis an das Erz heran.");
        return true;
    }

    private static List<BlockPos> findAncientDebrisTunnelRoute(MinecraftClient mc, BlockPos oreTarget) {
        BlockPos start = mc.player.getBlockPos();
        int verticalOffset = oreTarget.getY() - start.getY();
        if (verticalOffset != 0 && verticalOffset != 1) return List.of();
        List<List<BlockPos>> routes = new ArrayList<>();
        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos stand = verticalOffset == 1 ? oreTarget.offset(side).down() : oreTarget.offset(side);
            if (stand.getY() != start.getY()) continue;
            List<BlockPos> xFirst = buildAncientTunnelRoute(mc, start, stand, true);
            List<BlockPos> zFirst = buildAncientTunnelRoute(mc, start, stand, false);
            if (!xFirst.isEmpty()) routes.add(xFirst);
            if (!zFirst.isEmpty() && !zFirst.equals(xFirst)) routes.add(zFirst);
        }
        routes.sort(Comparator
                .comparingInt((List<BlockPos> route) -> countAncientTunnelBlocks(mc, route))
                .thenComparingInt(List::size));
        return routes.isEmpty() ? List.of() : routes.get(0);
    }

    private static List<BlockPos> buildAncientTunnelRoute(MinecraftClient mc, BlockPos start,
                                                            BlockPos stand, boolean xFirst) {
        int dx = stand.getX() - start.getX();
        int dz = stand.getZ() - start.getZ();
        int length = Math.abs(dx) + Math.abs(dz);
        if (length < 1 || length > MAX_ANCIENT_TUNNEL_LENGTH) return List.of();
        List<BlockPos> route = new ArrayList<>(length);
        int x = start.getX();
        int z = start.getZ();
        int xStep = Integer.compare(dx, 0);
        int zStep = Integer.compare(dz, 0);
        if (xFirst) {
            for (int i = 0; i < Math.abs(dx); i++) {
                x += xStep;
                route.add(new BlockPos(x, start.getY(), z));
            }
            for (int i = 0; i < Math.abs(dz); i++) {
                z += zStep;
                route.add(new BlockPos(x, start.getY(), z));
            }
        } else {
            for (int i = 0; i < Math.abs(dz); i++) {
                z += zStep;
                route.add(new BlockPos(x, start.getY(), z));
            }
            for (int i = 0; i < Math.abs(dx); i++) {
                x += xStep;
                route.add(new BlockPos(x, start.getY(), z));
            }
        }
        for (BlockPos cell : route) if (!isSafeAncientTunnelCell(mc, cell)) return List.of();
        if (countAncientTunnelBlocks(mc, route) > MAX_ANCIENT_TUNNEL_BLOCKS) return List.of();
        return route;
    }

    private static int countAncientTunnelBlocks(MinecraftClient mc, List<BlockPos> route) {
        int count = 0;
        for (BlockPos cell : route) {
            if (!mc.world.getBlockState(cell).isAir()) count++;
            if (!mc.world.getBlockState(cell.up()).isAir()) count++;
        }
        return count;
    }

    private static boolean isSafeAncientDebrisTarget(MinecraftClient mc, BlockPos pos) {
        if (!isInNether(mc) || !mc.world.isChunkLoaded(pos)) return false;
        BlockState state = mc.world.getBlockState(pos);
        if (!state.isOf(Blocks.ANCIENT_DEBRIS) || state.hasBlockEntity()
                || !state.getFluidState().isEmpty() || state.getHardness(mc.world, pos) < 0.0f) return false;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            if (!mc.world.isChunkLoaded(neighbor) || !mc.world.getBlockState(neighbor).getFluidState().isEmpty())
                return false;
        }
        return true;
    }

    private static boolean isAtExactTunnelCell(MinecraftClient mc, BlockPos cell) {
        double horizontal = Math.hypot(cell.getX() + 0.5 - mc.player.getX(),
                cell.getZ() + 0.5 - mc.player.getZ());
        return horizontal < 0.28 && Math.abs(mc.player.getY() - cell.getY()) < 0.65;
    }

    private static boolean isSafeAncientTunnelCell(MinecraftClient mc, BlockPos feet) {
        BlockPos head = feet.up();
        BlockPos floor = feet.down();
        if (!mc.world.isChunkLoaded(feet) || !mc.world.isChunkLoaded(head) || !mc.world.isChunkLoaded(floor))
            return false;
        BlockState support = mc.world.getBlockState(floor);
        var supportShape = support.getCollisionShape(mc.world, floor);
        if (!support.getFluidState().isEmpty() || support.hasBlockEntity()
                || support.getBlock() instanceof net.minecraft.block.FallingBlock || isHazard(support)
                || supportShape.isEmpty() || supportShape.getMax(Direction.Axis.Y) < 0.95) return false;

        for (BlockPos clearPos : new BlockPos[]{feet, head}) {
            BlockState state = mc.world.getBlockState(clearPos);
            if (!state.getFluidState().isEmpty() || state.hasBlockEntity()
                    || state.getBlock() instanceof net.minecraft.block.FallingBlock
                    || state.getHardness(mc.world, clearPos) < 0.0f || isHazard(state)) return false;
            if (!state.isAir() && !ANCIENT_TUNNEL_BLOCKS.contains(Registries.BLOCK.getId(state.getBlock()).getPath()))
                return false;
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = clearPos.offset(direction);
                if (!mc.world.isChunkLoaded(neighbor) || !mc.world.getBlockState(neighbor).getFluidState().isEmpty())
                    return false;
            }
        }
        return true;
    }

    private static boolean startAncientTunnelMining(MinecraftClient mc, BlockPos pos, BlockState state) {
        if (!isSafeAncientTunnelCell(mc, gatherTunnelCells.get(gatherTunnelCellIndex))
                || !ANCIENT_TUNNEL_BLOCKS.contains(Registries.BLOCK.getId(state.getBlock()).getPath())
                || !hasSuitableMiningTool(mc, state, true)
                || availableInventoryCapacity(mc, state.getBlock().asItem()) <= 0) return false;
        try {
            originalSlot = mc.player.getInventory().getSelectedSlot();
            int toolSlot = findBestMiningToolSlot(mc, mc.player, state);
            mc.player.getInventory().setSelectedSlot(toolSlot);
            aimAt(mc.player, pos.toCenterPos());
            BlockHitResult hit = raycastBlock(mc, pos);
            if (hit == null || !mc.interactionManager.attackBlock(pos, hit.getSide())) {
                restoreMiningTool(mc);
                restoreHotbar(mc);
                return false;
            }
            miningPos = pos.toImmutable();
            miningState = state;
            miningSide = hit.getSide();
            actionTicks = 0;
            mode = Mode.GATHER_TUNNEL_MINE;
            mc.options.attackKey.setPressed(true);
            statusNotice(mc, "Grabe sicheren Tunnelblock " + Registries.BLOCK.getId(state.getBlock())
                    + " bei " + pos.toShortString() + " ab.");
            return true;
        } catch (RuntimeException failure) {
            restoreMiningTool(mc);
            restoreHotbar(mc);
            return false;
        }
    }

    private static void tickGatherTunnel(MinecraftClient mc) {
        if (gatherMaterial == null || gatherTunnelOreTarget == null || gatherTunnelCells.isEmpty()) {
            stop("Ancient-Debris-Abbau gestoppt: Der Tunnelplan ist nicht mehr gültig.");
            return;
        }
        if (!isSafeAncientDebrisTarget(mc, gatherTunnelOreTarget)) {
            gatherTunnelCells = List.of();
            gatherTunnelOreTarget = null;
            gatherTunnelCellIndex = 0;
            mode = Mode.GATHER;
            return;
        }
        while (gatherTunnelCellIndex < gatherTunnelCells.size()) {
            BlockPos cell = gatherTunnelCells.get(gatherTunnelCellIndex);
            if (!isSafeAncientTunnelCell(mc, cell)) {
                skipAncientTunnelTarget(mc, "Lava/Flüssigkeit, unsicherer Untergrund oder ungeeigneter Tunnelblock erkannt.");
                return;
            }
            // Clear headroom first; otherwise the player's eye ray hits the upper block.
            for (BlockPos clearPos : new BlockPos[]{cell.up(), cell}) {
                BlockState state = mc.world.getBlockState(clearPos);
                if (state.isAir()) continue;
                if (!startAncientTunnelMining(mc, clearPos, state))
                    skipAncientTunnelTarget(mc, "Der nächste Tunnelblock konnte nicht sicher abgebaut werden.");
                return;
            }
            if (isAtExactTunnelCell(mc, cell)) {
                gatherTunnelCellIndex++;
                continue;
            }
            if (!canStandAt(mc, cell, mc.player)) {
                skipAncientTunnelTarget(mc, "Der geöffnete Tunnelabschnitt ist kein sicherer Standpunkt.");
                return;
            }
            List<BlockPos> planned = findPath(mc, cell);
            if (planned.isEmpty()) {
                skipAncientTunnelTarget(mc, "Kein sicherer Laufweg durch den geöffneten Tunnelabschnitt.");
                return;
            }
            path = List.copyOf(planned);
            navigationGoal = cell.toImmutable();
            pathIndex = 0;
            actionTicks = 0;
            stuckTicks = 0;
            lastWaypointDistance = Double.MAX_VALUE;
            mode = Mode.NAVIGATE;
            statusNotice(mc, "Gehe durch den freigeräumten Nether-Stollen weiter.");
            return;
        }
        gatherTunnelCells = List.of();
        gatherTunnelOreTarget = null;
        gatherTunnelCellIndex = 0;
        mode = Mode.GATHER;
    }

    private static void skipAncientTunnelTarget(MinecraftClient mc, String reason) {
        releaseKeys(mc);
        if (mc.interactionManager != null) {
            try { mc.interactionManager.cancelBlockBreaking(); } catch (RuntimeException ignored) {}
        }
        restoreMiningTool(mc);
        restoreHotbar(mc);
        miningPos = null;
        miningState = null;
        advanceGatherTarget();
        gatherTunnelCells = List.of();
        gatherTunnelOreTarget = null;
        gatherTunnelCellIndex = 0;
        mode = Mode.GATHER;
        statusNotice(mc, "Überspringe dieses Ancient-Debris-Vorkommen: " + reason);
    }

    private static void tickGatherTunnelMining(MinecraftClient mc) {
        if (++actionTicks > MAX_MINING_TICKS || mc.interactionManager == null || miningPos == null
                || gatherTunnelCellIndex >= gatherTunnelCells.size()
                || mc.player.getEyePos().squaredDistanceTo(miningPos.toCenterPos())
                    > mc.player.getBlockInteractionRange() * mc.player.getBlockInteractionRange()) {
            skipAncientTunnelTarget(mc, "Reichweite oder Abbauzeitlimit überschritten.");
            return;
        }
        BlockPos cell = gatherTunnelCells.get(gatherTunnelCellIndex);
        BlockState current = mc.world.getBlockState(miningPos);
        if (current.isAir() || !current.getBlock().equals(miningState.getBlock())) {
            if (mc.interactionManager != null) {
                try { mc.interactionManager.cancelBlockBreaking(); } catch (RuntimeException ignored) {}
            }
            releaseKeys(mc);
            restoreMiningTool(mc);
            restoreHotbar(mc);
            miningPos = null;
            miningState = null;
            mode = Mode.GATHER_TUNNEL;
            return;
        }
        if (!isSafeAncientTunnelCell(mc, cell)
                || !ANCIENT_TUNNEL_BLOCKS.contains(Registries.BLOCK.getId(current.getBlock()).getPath())
                || !hasSuitableMiningTool(mc, current, true)) {
            skipAncientTunnelTarget(mc, "Tunnelbedingungen haben sich während des Abbaus geändert.");
            return;
        }
        BlockHitResult hit = raycastBlock(mc, miningPos);
        if (hit == null || !hit.getBlockPos().equals(miningPos)) {
            skipAncientTunnelTarget(mc, "Der nächste Tunnelblock ist nicht mehr frei sichtbar.");
            return;
        }
        mc.options.attackKey.setPressed(true);
        mc.interactionManager.updateBlockBreakingProgress(miningPos, miningSide);
    }

    private static boolean startGatherApproach(MinecraftClient mc, BlockPos target) {
        List<BlockPos> stands = new ArrayList<>();
        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction side : sides) {
            stands.add(target.offset(side).down());
            stands.add(target.offset(side));
        }
        stands.sort(Comparator.comparingDouble(pos -> mc.player.squaredDistanceTo(
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)));
        for (BlockPos stand : stands) {
            if (!mc.world.isChunkLoaded(stand) || isAtGoal(mc, stand)) continue;
            List<BlockPos> planned = findPath(mc, stand);
            if (planned.isEmpty()) continue;
            path = List.copyOf(planned);
            navigationGoal = stand.toImmutable();
            pathIndex = 0;
            actionTicks = 0;
            stuckTicks = 0;
            lastWaypointDistance = Double.MAX_VALUE;
            mode = Mode.NAVIGATE;
            statusNotice(mc, "Gehe zu einem sicheren Standpunkt neben " + gatherMaterialName + " bei " + target.toShortString() + ".");
            return true;
        }
        return false;
    }

    private static void tickGatherMining(MinecraftClient mc) {
        if (++actionTicks > MAX_MINING_TICKS || mc.interactionManager == null
                || miningPos == null || mc.player.getEyePos().squaredDistanceTo(miningPos.toCenterPos())
                > mc.player.getBlockInteractionRange() * mc.player.getBlockInteractionRange()) {
            stop("Materialsammlung gestoppt: Abbaureichweite oder Zeitlimit überschritten.");
            return;
        }
        BlockState current = mc.world.getBlockState(miningPos);
        if (current.isAir() || !current.getBlock().equals(miningState.getBlock())) {
            BlockPos broken = miningPos.toImmutable();
            try { mc.interactionManager.cancelBlockBreaking(); } catch (RuntimeException ignored) {}
            restoreMiningTool(mc);
            restoreHotbar(mc);
            miningPos = null;
            miningState = null;
            gatherBlocksBrokenThisRequest++;
            gatherAwaitingPickup = true;
            gatherPickupTicks = 0;

            // Step into the cleared trunk cell when it is safe, so the dropped log can be picked up.
            if (canStandAt(mc, broken, mc.player) && !isAtGoal(mc, broken)) {
                List<BlockPos> planned = findPath(mc, broken);
                if (!planned.isEmpty()) {
                    path = List.copyOf(planned);
                    navigationGoal = broken;
                    pathIndex = 0;
                    actionTicks = 0;
                    stuckTicks = 0;
                    lastWaypointDistance = Double.MAX_VALUE;
                    mode = Mode.NAVIGATE;
                    return;
                }
            }
            mode = Mode.GATHER;
            return;
        }

        BlockHitResult hit = raycastBlock(mc, miningPos);
        if (hit == null || !hit.getBlockPos().equals(miningPos)) {
            stop("Materialsammlung gestoppt: Der Stamm ist nicht mehr frei sichtbar.");
            return;
        }
        mc.options.attackKey.setPressed(true);
        mc.interactionManager.updateBlockBreakingProgress(miningPos, miningSide);
    }

    private static void statusNotice(MinecraftClient mc, String message) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Nightfall] " + message), false);
    }

    private static void resetGatherState() {
        gatherMaterial = null;
        gatherRequiresSuitableTool = false;
        gatherMaterialName = "";
        gatherGoalCount = 0;
        gatherMaxDurationSeconds = 0;
        gatherDeadlineNanos = 0L;
        gatherTargetY = Integer.MIN_VALUE;
        gatherTargets = List.of();
        gatherIndex = 0;
        gatherBlocksBrokenThisRequest = 0;
        gatherVisitedTargets = new HashSet<>();
        gatherExploreVisited = new HashSet<>();
        gatherExploreOrigin = null;
        gatherExploreStepIndex = 0;
        gatherExploreLegs = 0;
        gatherRescanCount = 0;
        gatherAwaitingPickup = false;
        gatherPickupTicks = 0;
        gatherCountBeforeBreak = 0;
        gatherTunnelCells = List.of();
        gatherTunnelCellIndex = 0;
        gatherTunnelOreTarget = null;
    }


    /** Preview a bounded raw-metal/ancient-debris smelt in the furnace under the crosshair. */
    public static String previewSmeltItem(String requestedItem, int inputCount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        if (inputCount < 1 || inputCount > MAX_SMELT_ITEMS)
            throw new IllegalArgumentException("Ein Schmelzauftrag verarbeitet 1 bis " + MAX_SMELT_ITEMS + " Rohmaterialien.");
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler)
            throw new IllegalArgumentException("Schließe zuerst den geöffneten Container.");
        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty())
            throw new IllegalArgumentException("Dein Maus-Cursor hält noch einen Gegenstand. Lege ihn zuerst ins Inventar.");
        if (!(mc.crosshairTarget instanceof BlockHitResult hit))
            throw new IllegalArgumentException("Sieh direkt auf einen vorhandenen Ofen oder Hochofen.");
        BlockState furnaceState = mc.world.getBlockState(hit.getBlockPos());
        if (!(furnaceState.getBlock() instanceof AbstractFurnaceBlock) || furnaceState.isOf(Blocks.SMOKER))
            throw new IllegalArgumentException("Nightfall benötigt einen normalen Ofen oder Hochofen direkt unter deinem Fadenkreuz. Räucheröfen werden nicht unterstützt.");
        Item input = resolveSmeltItem(requestedItem);
        Item output = resolveSmeltOutput(input);
        int availableInput = countInventoryItem(mc, input);
        if (availableInput < inputCount)
            throw new IllegalArgumentException("Du hast nur " + availableInput + " × " + input.getName().getString()
                    + " im Inventar; angefordert sind " + inputCount + ".");
        int fuelRequired = requiredSmeltFuel(inputCount, furnaceState);
        Item fuelItem = resolveSmeltFuel(mc, fuelRequired);
        if (availableInventoryCapacity(mc, output) < inputCount)
            throw new IllegalArgumentException("Dein Inventar hat nicht genug Platz für " + inputCount
                    + " × " + output.getName().getString() + ". Räume zuerst Platz frei.");
        return "Nach deiner Bestätigung öffnet Nightfall den "
                + (furnaceState.isOf(Blocks.BLAST_FURNACE) ? "Hochofen" : "Ofen")
                + " bei " + hit.getBlockPos().toShortString() + ", legt " + inputCount + " × "
                + input.getName().getString() + " und " + fuelRequired + " × "
                + fuelItem.getName().getString() + " ein, "
                + "wartet auf das echte Schmelzergebnis und sammelt bis zu " + inputCount + " × "
                + output.getName().getString() + " ein. Der Ofen muss leer sein; vorhandene Ofeninhalte werden nicht angerührt. "
                + "Pro Auftrag höchstens " + MAX_SMELT_ITEMS + " Eingabestücke.";
    }

    /** Starts the confirmed normal furnace interaction and then manages only the selected input/fuel batch. */
    public static String smeltItem(String requestedItem, int inputCount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        previewSmeltItem(requestedItem, inputCount);
        BlockHitResult hit = (BlockHitResult) mc.crosshairTarget;
        BlockState furnaceState = mc.world.getBlockState(hit.getBlockPos());
        Item input = resolveSmeltItem(requestedItem);
        Item output = resolveSmeltOutput(input);
        int fuelRequired = requiredSmeltFuel(inputCount, furnaceState);
        Item fuelItem = resolveSmeltFuel(mc, fuelRequired);

        stop(null);
        smeltFurnacePos = hit.getBlockPos().toImmutable();
        smeltInput = input;
        smeltOutput = output;
        smeltFuelItem = fuelItem;
        smeltAmount = inputCount;
        smeltFuelRequired = fuelRequired;
        smeltOutputCountBefore = countInventoryItem(mc, output);
        smeltTicks = 0;
        smeltOpenTicks = 0;
        smeltItemsLoaded = false;
        smeltOutputPending = false;
        smeltOutputCountBeforeClick = 0;
        smeltOutputWaitTicks = 0;

        try {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        } catch (RuntimeException failure) {
            resetSmeltingState();
            throw new IllegalArgumentException("Minecraft konnte den Ofen nicht normal öffnen. Prüfe Reichweite und Serverrechte.");
        }
        mode = Mode.SMELT;
        notifyPlayer(mc, "Öffne " + (furnaceState.isOf(Blocks.BLAST_FURNACE) ? "Hochofen" : "Ofen")
                + " und bereite " + inputCount + " × " + input.getName().getString() + " zum Schmelzen vor.");
        return "Schmelzen gestartet. Nightfall öffnet den anvisierten Ofen, lädt nur die bestätigte Charge und wartet auf serverseitig bestätigte Ausgaben.";
    }

    private static Item resolveSmeltItem(String requestedItem) {
        if (requestedItem == null || requestedItem.isBlank() || requestedItem.length() > 64)
            throw new IllegalArgumentException("Gib eine exakte Item-ID wie minecraft:raw_iron an.");
        Identifier id = Identifier.tryParse(requestedItem.trim());
        if (id == null || !id.getNamespace().equals("minecraft")
                || !SMELT_INPUT_OUTPUT.containsKey(id.toString()))
            throw new IllegalArgumentException("Nightfall schmilzt derzeit nur minecraft:raw_iron, minecraft:raw_gold, minecraft:raw_copper und minecraft:ancient_debris.");
        Item item = Registries.ITEM.get(id);
        if (!Registries.ITEM.getId(item).equals(id))
            throw new IllegalArgumentException("Diese Schmelz-Item-ID ist nicht in Minecraft registriert.");
        return item;
    }

    private static Item resolveSmeltOutput(Item input) {
        String outputId = SMELT_INPUT_OUTPUT.get(Registries.ITEM.getId(input).toString());
        Identifier id = Identifier.tryParse(outputId);
        Item output = Registries.ITEM.get(id);
        if (!Registries.ITEM.getId(output).equals(id))
            throw new IllegalArgumentException("Das erwartete Schmelzergebnis ist in dieser Minecraft-Version nicht registriert.");
        return output;
    }

    private static int requiredSmeltFuel(int inputCount, BlockState furnaceState) {
        int itemsPerFuel = furnaceState.isOf(Blocks.BLAST_FURNACE) ? 16 : 8;
        return (inputCount + itemsPerFuel - 1) / itemsPerFuel;
    }

    private static Item resolveSmeltFuel(MinecraftClient mc, int required) {
        if (countInventoryItem(mc, Items.COAL) >= required) return Items.COAL;
        if (countInventoryItem(mc, Items.CHARCOAL) >= required) return Items.CHARCOAL;
        throw new IllegalArgumentException("Für diese Schmelzcharge brauche ich entweder "
                + required + " Kohle oder " + required + " Holzkohle am Stück. Gemischte Brennstoffe "
                + "werden im Ofenslot nicht kombiniert; vorhanden sind "
                + countInventoryItem(mc, Items.COAL) + " Kohle und "
                + countInventoryItem(mc, Items.CHARCOAL) + " Holzkohle.");
    }

    private static void tickSmelting(MinecraftClient mc) {
        if (++smeltTicks > MAX_SMELT_TICKS || mc.interactionManager == null) {
            int produced = Math.max(0, countInventoryItem(mc, smeltOutput) - smeltOutputCountBefore);
            stop("Schmelzen gestoppt: Zeitlimit erreicht. Hergestellt: " + produced + "/" + smeltAmount + " "
                    + smeltOutput.getName().getString() + ". Bereits eingelegte Ofeninhalte können im Ofen weiterverarbeiten.");
            return;
        }
        if (smeltFurnacePos == null || !(mc.world.getBlockState(smeltFurnacePos).getBlock() instanceof AbstractFurnaceBlock)
                || mc.world.getBlockState(smeltFurnacePos).isOf(Blocks.SMOKER)) {
            stop("Schmelzen gestoppt: Der anvisierte Ofen ist nicht mehr vorhanden.");
            return;
        }
        if (!(mc.player.currentScreenHandler instanceof AbstractFurnaceScreenHandler handler)) {
            if (!smeltItemsLoaded && ++smeltOpenTicks <= MAX_SMELT_OPEN_TICKS) return;
            stop(smeltItemsLoaded
                    ? "Schmelzen unterbrochen: Das Ofenfenster wurde geschlossen. Bereits eingelegte Stücke können im Ofen weiterbrennen."
                    : "Schmelzen gestoppt: Der Ofen hat sich nicht geöffnet. Prüfe Reichweite und Serverrechte.");
            return;
        }
        if (!handler.getCursorStack().isEmpty()) {
            stop("Schmelzen gestoppt: Der Maus-Cursor ist nicht leer. Lege den Gegenstand zuerst ins Inventar.");
            return;
        }

        if (!smeltItemsLoaded) {
            if (!handler.getSlot(0).getStack().isEmpty()
                    || !handler.getSlot(1).getStack().isEmpty()
                    || !handler.getOutputSlot().getStack().isEmpty()) {
                stop("Schmelzen abgebrochen: Der Ofen enthält bereits Gegenstände. Nightfall hat nichts daran verändert.");
                return;
            }
            int inputMoved = moveItemsToFurnaceSlot(mc, handler, smeltInput, 0, smeltAmount);
            if (inputMoved != smeltAmount) {
                stop("Schmelzen gestoppt: Es konnten nur " + inputMoved + "/" + smeltAmount
                        + " Eingabestücke sicher in den Ofen gelegt werden.");
                return;
            }
            int fuelMoved = moveItemsToFurnaceSlot(mc, handler, smeltFuelItem, 1, smeltFuelRequired);
            if (fuelMoved < smeltFuelRequired) {
                stop("Schmelzen gestoppt: Es konnten nur " + fuelMoved + "/" + smeltFuelRequired
                        + " Brennstoffstücke sicher eingelegt werden. Die Eingabe kann im Ofen liegen bleiben.");
                return;
            }
            smeltItemsLoaded = true;
            statusNotice(mc, "Ofen befüllt: " + smeltAmount + " Eingabestücke, " + fuelMoved + " Brennstoffstücke. Warte auf Schmelzergebnisse.");
            return;
        }

        if (smeltOutputPending) {
            int currentOutputCount = countInventoryItem(mc, smeltOutput);
            if (currentOutputCount > smeltOutputCountBeforeClick) {
                smeltOutputPending = false;
                smeltOutputWaitTicks = 0;
                return;
            }
            if (++smeltOutputWaitTicks > 40) {
                stop("Schmelzen gestoppt: Das Ofenergebnis wurde nicht im Inventar bestätigt. Prüfe den Ofenausgabeslot manuell.");
            }
            return;
        }

        ItemStack outputStack = handler.getOutputSlot().getStack();
        if (!outputStack.isEmpty()) {
            if (!outputStack.isOf(smeltOutput)) {
                stop("Schmelzen gestoppt: Der Ofen zeigte ein anderes Ergebnis als erwartet.");
                return;
            }
            if (availableInventoryCapacity(mc, smeltOutput) < outputStack.getCount()) {
                stop("Schmelzen pausiert: Dein Inventar hat keinen Platz für das Ofenergebnis. Räume Platz frei und entnimm es manuell.");
                return;
            }
            smeltOutputCountBeforeClick = countInventoryItem(mc, smeltOutput);
            smeltOutputWaitTicks = 0;
            smeltOutputPending = true;
            mc.interactionManager.clickSlot(handler.syncId, handler.getOutputSlot().id,
                    0, SlotActionType.QUICK_MOVE, mc.player);
            return;
        }

        int produced = Math.max(0, countInventoryItem(mc, smeltOutput) - smeltOutputCountBefore);
        boolean inputEmpty = handler.getSlot(0).getStack().isEmpty();
        if (produced >= smeltAmount && inputEmpty && !handler.isBurning()) {
            stop("Schmelzen abgeschlossen: " + produced + "/" + smeltAmount + " "
                    + smeltOutput.getName().getString() + " liegen jetzt im Inventar.");
            return;
        }
        if (inputEmpty && !handler.isBurning() && handler.getOutputSlot().getStack().isEmpty()) {
            stop("Schmelzen beendet: " + produced + "/" + smeltAmount + " "
                    + smeltOutput.getName().getString() + " wurden im Inventar bestätigt.");
        }
    }

    /**
     * Moves exactly the requested number of a matching player-inventory item into a furnace slot.
     * Left-picks the original stack, right-clicks the requested units into the machine, and returns
     * the remaining cursor stack to the same inventory slot.
     */
    private static int moveItemsToFurnaceSlot(MinecraftClient mc, AbstractFurnaceScreenHandler handler,
                                               Item item, int furnaceSlot, int amount) {
        int remaining = amount;
        int moved = 0;
        for (int inventoryIndex = 0; inventoryIndex < 36 && remaining > 0; inventoryIndex++) {
            ItemStack source = mc.player.getInventory().getStack(inventoryIndex);
            if (source.isEmpty() || !source.isOf(item)) continue;
            int requestedFromStack = Math.min(remaining, source.getCount());
            int screenSlot = inventoryIndex < 9 ? 30 + inventoryIndex : inventoryIndex - 6;
            int before = handler.getSlot(furnaceSlot).getStack().getCount();
            mc.interactionManager.clickSlot(handler.syncId, screenSlot, 0, SlotActionType.PICKUP, mc.player);
            if (!handler.getCursorStack().isOf(item)) return moved;
            for (int unit = 0; unit < requestedFromStack; unit++) {
                mc.interactionManager.clickSlot(handler.syncId, furnaceSlot, 1, SlotActionType.PICKUP, mc.player);
            }
            if (!handler.getCursorStack().isEmpty()) {
                mc.interactionManager.clickSlot(handler.syncId, screenSlot, 0, SlotActionType.PICKUP, mc.player);
            }
            if (!handler.getCursorStack().isEmpty()) return moved;
            int deposited = handler.getSlot(furnaceSlot).getStack().getCount() - before;
            if (deposited <= 0) return moved;
            moved += deposited;
            remaining -= deposited;
        }
        return moved;
    }

    private static void resetSmeltingState() {
        smeltFurnacePos = null;
        smeltInput = null;
        smeltOutput = null;
        smeltFuelItem = null;
        smeltAmount = 0;
        smeltFuelRequired = 0;
        smeltOutputCountBefore = 0;
        smeltTicks = 0;
        smeltOpenTicks = 0;
        smeltItemsLoaded = false;
        smeltOutputPending = false;
        smeltOutputCountBeforeClick = 0;
        smeltOutputWaitTicks = 0;
    }

    public static String previewEat(String preferredItem) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        if (!mc.player.getHungerManager().isNotFull())
            throw new IllegalArgumentException("Deine Hungerleiste ist bereits voll.");
        int slot = findFoodSlot(mc.player, preferredItem);
        if (slot < 0) throw new IllegalArgumentException(preferredItem == null || preferredItem.isBlank()
                ? "Kein essbares Nahrungsmittel in deiner Hotbar. Lege Essen in einen der Slots 1–9."
                : "Das gewünschte Essen liegt nicht essbar in deiner Hotbar.");
        ItemStack stack = mc.player.getInventory().getStack(slot);
        return "Nightfall isst nach deiner Bestätigung " + stack.getName().getString()
                + " aus Hotbar-Slot " + (slot + 1) + ". Der ursprüngliche Slot wird danach wiederhergestellt.";
    }

    public static String eatFood(String preferredItem) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        previewEat(preferredItem);
        stop(null);
        int slot = findFoodSlot(mc.player, preferredItem);
        if (slot < 0) throw new IllegalArgumentException("Das Essen ist nicht mehr verfügbar.");
        originalSlot = mc.player.getInventory().getSelectedSlot();
        foodSlot = slot;
        mc.player.getInventory().setSelectedSlot(slot);
        ItemStack stack = mc.player.getInventory().getStack(slot);
        foodCountBefore = stack.getCount();
        hungerBefore = mc.player.getHungerManager().getFoodLevel();
        foodName = stack.getName().getString();
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        if (!mc.player.isUsingItem()) {
            restoreHotbar(mc);
            throw new IllegalArgumentException("Minecraft hat die Nahrungsaufnahme nicht gestartet.");
        }
        mode = Mode.EAT;
        actionTicks = 0;
        mc.options.useKey.setPressed(true);
        notifyPlayer(mc, "Esse " + foodName + ".");
        return "Essen gestartet. Nightfall wartet auf den Abschluss der normalen Minecraft-Itembenutzung.";
    }

    public static void tick() {
        if (mode == Mode.NONE) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean activeCraftingScreen = mode == Mode.CRAFT && craftingHandler != null
                && mc.player != null && mc.player.currentScreenHandler == craftingHandler;
        if (mc.player == null || mc.world == null || mc.player.isDead()
                || (mode != Mode.SMELT && !activeCraftingScreen
                && mc.currentScreen != null && !(mc.currentScreen instanceof AIChatScreen))) {
            stop("Aktion aus Sicherheitsgründen gestoppt.");
            return;
        }

        if (gatherMaterial != null && gatherDeadlineNanos > 0L && System.nanoTime() >= gatherDeadlineNanos) {
            int collected = mc.player == null ? 0 : countInventoryItem(mc, gatherMaterial);
            stop("Zeitlimit erreicht: " + collected + "/" + gatherGoalCount + " " + gatherMaterialName
                    + " befinden sich jetzt im Inventar.");
            return;
        }

        switch (mode) {
            case WALK -> tickWalk(mc);
            case NAVIGATE -> tickNavigation(mc);
            case SEARCH_STRUCTURES -> tickStructureSearch(mc);
            case MINE -> tickMining(mc);
            case EAT -> tickEating(mc);
            case CRAFT -> tickCrafting(mc);
            case SORT -> tickInventorySort(mc);
            case GATHER -> tickGather(mc);
            case GATHER_MINE -> tickGatherMining(mc);
            case GATHER_TUNNEL -> tickGatherTunnel(mc);
            case GATHER_TUNNEL_MINE -> tickGatherTunnelMining(mc);
            case SMELT -> tickSmelting(mc);
            case NONE -> { }
        }
    }

    private static void tickWalk(MinecraftClient mc) {
        if (mc.player.isSubmergedInWater() || mc.player.hasVehicle()) {
            stop("Bewegung gestoppt: Wasser oder Fahrzeug erkannt.");
            return;
        }
        mc.options.forwardKey.setPressed(true);
        if (--forwardTicks <= 0) stop("Bewegung abgeschlossen.");
    }

    private static void tickStructureSearch(MinecraftClient mc) {
        if (mc.player.isSubmergedInWater() || mc.player.hasVehicle()
                || mc.player.isOnFire() || mc.player.isInLava()) {
            stop("Struktursuche aus Sicherheitsgründen beendet (Zeitlimit oder Gefahr erkannt).");
            return;
        }
        scanForStructureHints(mc);
        if (mode != Mode.SEARCH_STRUCTURES) return;
        if (pathIndex >= path.size()) {
            if (!startNextStructureSearchLeg(mc)) {
                stop("Struktursuche gestoppt: Kein sicherer nächster Wegabschnitt verfügbar.");
                return;
            }
        }
        tickNavigation(mc);
    }

    private static void tickNavigation(MinecraftClient mc) {
        if (++actionTicks > MAX_NAVIGATION_TICKS || mc.player.isSubmergedInWater() || mc.player.hasVehicle()) {
            stop("Navigation gestoppt: Zeitlimit, Wasser oder Fahrzeug erkannt.");
            return;
        }
        if (pathIndex >= path.size()) {
            if (navigationGoal == null || isAtGoal(mc, navigationGoal)) {
                if (mode == Mode.SEARCH_STRUCTURES) {
                    if (!startNextStructureSearchLeg(mc)) stop("Struktursuche gestoppt: Kein sicherer nächster Wegabschnitt verfügbar.");
                    return;
                }
                if (gatherMaterial != null) {
                    resumeGatheringAfterNavigation(mc);
                    return;
                }
                stop("Ziel erreicht.");
                return;
            }
            List<BlockPos> nextSegment = planNavigationSegment(mc, navigationGoal);
            if (nextSegment.isEmpty()) {
                stop("Navigation gestoppt: Der nächste sichere Wegabschnitt ist noch nicht geladen oder nicht begehbar.");
                return;
            }
            path = List.copyOf(nextSegment);
            pathIndex = 0;
            stuckTicks = 0;
            lastWaypointDistance = Double.MAX_VALUE;
        }
        BlockPos waypoint = path.get(pathIndex);
        Vec3d destination = new Vec3d(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5);
        double distance = Math.sqrt(mc.player.squaredDistanceTo(destination.x, destination.y, destination.z));
        if (distance < 0.28 && Math.abs(mc.player.getY() - waypoint.getY()) < 0.65) {
            pathIndex++;
            stuckTicks = 0;
            lastWaypointDistance = Double.MAX_VALUE;
            if (pathIndex >= path.size()) {
                if (mode == Mode.SEARCH_STRUCTURES) {
                    if (!startNextStructureSearchLeg(mc)) stop("Struktursuche gestoppt: Kein sicherer nächster Wegabschnitt verfügbar.");
                    return;
                }
                if (gatherMaterial != null) {
                    resumeGatheringAfterNavigation(mc);
                    return;
                }
                stop("Ziel erreicht.");
                return;
            }
            waypoint = path.get(pathIndex);
            destination = new Vec3d(waypoint.getX() + 0.5, waypoint.getY(), waypoint.getZ() + 0.5);
            distance = Math.sqrt(mc.player.squaredDistanceTo(destination.x, destination.y, destination.z));
        }

        if (distance < lastWaypointDistance - MIN_PROGRESS) {
            lastWaypointDistance = distance;
            stuckTicks = 0;
        } else if (++stuckTicks > 50) {
            stop("Navigation gestoppt: Kein Fortschritt oder Hindernis. Bitte gib ein neues Ziel an.");
            return;
        }

        aimAt(mc.player, destination);
        mc.options.forwardKey.setPressed(true);
        mc.options.jumpKey.setPressed(waypoint.getY() > mc.player.getBlockY() && mc.player.isOnGround());
        mc.options.sprintKey.setPressed(false);
    }

    private static void resumeGatheringAfterNavigation(MinecraftClient mc) {
        releaseKeys(mc);
        path = List.of();
        pathIndex = 0;
        navigationGoal = null;
        actionTicks = 0;
        stuckTicks = 0;
        lastWaypointDistance = Double.MAX_VALUE;
        if (gatherTunnelOreTarget != null && gatherTunnelCellIndex < gatherTunnelCells.size()) {
            mode = Mode.GATHER_TUNNEL;
            return;
        }
        if ((gatherTargets.isEmpty() || gatherIndex >= gatherTargets.size()) && !refreshGatherTargets(mc)) {
            if (startGatherExploration(mc)) return;
            stop("Sammeln gestoppt: Es gibt im geladenen Bereich keine weiteren sicheren Ziele oder Suchwege.");
            return;
        }
        mode = Mode.GATHER;
    }

    private static void tickMining(MinecraftClient mc) {
        if (++actionTicks > MAX_MINING_TICKS || mc.interactionManager == null
                || mc.player.getEyePos().squaredDistanceTo(miningPos.toCenterPos())
                > mc.player.getBlockInteractionRange() * mc.player.getBlockInteractionRange()) {
            stop("Abbau gestoppt: Zeitlimit oder Reichweite überschritten.");
            return;
        }
        BlockState current = mc.world.getBlockState(miningPos);
        if (current.isAir() || !current.getBlock().equals(miningState.getBlock())) {
            stop("Blockänderung bestätigt · Abbau abgeschlossen.");
            return;
        }
        BlockHitResult hit = raycastBlock(mc, miningPos);
        if (hit == null || !hit.getBlockPos().equals(miningPos)) {
            stop("Abbau gestoppt: Der Zielblock ist nicht mehr frei sichtbar.");
            return;
        }
        mc.options.attackKey.setPressed(true);
        mc.interactionManager.updateBlockBreakingProgress(miningPos, miningSide);
    }


    /**
     * Plans a bounded dependency chain using only recipes currently present in the
     * server-synchronized recipe book. A 3x3 crafting table must already be open when
     * any step needs it; Nightfall never places or opens a table on its own.
     */
    public static String previewCraftItem(String requestedItem, int additionalCount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.getNetworkHandler() == null)
            throw new IllegalArgumentException("Du musst dich in einer verbundenen Minecraft-Welt befinden.");
        if (additionalCount < 1 || additionalCount > 256)
            throw new IllegalArgumentException("Die zusätzliche Craftingmenge muss zwischen 1 und 256 liegen.");
        String requested = validateCraftItemName(requestedItem);
        AbstractCraftingScreenHandler handler = requireEmptyCraftingGrid(mc);
        Item target = resolveCraftItem(requested);
        List<CraftingPlanStep> plan = createCraftingPlan(mc, target, additionalCount);
        boolean needsTable = plan.stream().anyMatch(step -> step.gridWidth() > 2 || step.gridHeight() > 2);
        if (needsTable && (handler.getWidth() < 3 || handler.getHeight() < 3))
            throw new IllegalArgumentException("Diese Rezeptkette enthält mindestens ein 3×3-Rezept. Öffne zuerst eine Crafting-Table und frage danach erneut; das 2×2-Inventarfeld reicht dafür nicht.");
        int current = countInventoryItem(mc, target);
        int batches = plan.stream().mapToInt(CraftingPlanStep::batches).sum();
        StringBuilder details = new StringBuilder();
        for (int i = 0; i < plan.size(); i++) {
            CraftingPlanStep step = plan.get(i);
            if (i > 0) details.append("\n");
            details.append(i + 1).append(". ")
                    .append(step.resultStack().getName().getString())
                    .append(" × ").append(step.batches())
                    .append(" Rezeptcharge(n) · ")
                    .append(step.gridWidth()).append("×").append(step.gridHeight())
                    .append(" · ").append(step.recipe().id());
        }
        return "Nightfall plant nach deiner Bestätigung eine Crafting-Rezeptkette für "
                + (current + additionalCount) + " " + plan.get(plan.size() - 1).resultStack().getName().getString()
                + " (aktuell " + current + ", zusätzlich mindestens " + additionalCount + "). "
                + "Geplante Schritte: " + plan.size() + "; insgesamt " + batches + " Rezeptchargen.\n"
                + details + "\nVerwendet werden ausschließlich Rezepte aus deinem freigeschalteten Server-Rezeptbuch. "
                + "Zwischenprodukte werden einzeln hergestellt und serverbestätigt, bevor der nächste Schritt beginnt. "
                + (needsTable ? "Eine geöffnete 3×3-Crafting-Table wird benötigt. " : "Die 2×2-Inventarfläche reicht für diese Kette. ")
                + "Maximal " + MAX_CRAFT_CHAIN_STEPS + " Rezeptschritte, Tiefe " + MAX_CRAFT_CHAIN_DEPTH
                + " und " + MAX_CRAFT_CHAIN_BATCHES + " Chargen. Jede Ausgabe muss in dein Inventar passen. "
                + "Handler " + handler.syncId + ".";
    }

    /** Start the fully previewed recipe dependency chain after explicit player confirmation. */
    public static String craftItem(String requestedItem, int additionalCount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        previewCraftItem(requestedItem, additionalCount);
        String requested = validateCraftItemName(requestedItem);
        AbstractCraftingScreenHandler handler = requireEmptyCraftingGrid(mc);
        Item target = resolveCraftItem(requested);
        List<CraftingPlanStep> plan = createCraftingPlan(mc, target, additionalCount);
        stop(null);
        craftingHandler = handler;
        craftingPlan = List.copyOf(plan);
        craftingPlanIndex = 0;
        craftingFinalTarget = target;
        craftingFinalCountBefore = countInventoryItem(mc, target);
        craftingFinalGoalCount = craftingFinalCountBefore + additionalCount;
        craftingTicks = 0;
        craftingIdleTicks = 0;
        craftingBatchesTaken = 0;
        craftingNeedsRefill = false;
        craftingPendingBatchExpectedCount = 0;
        craftingPendingBatchWaitTicks = 0;
        if (!startCraftingPlanStep(mc)) {
            resetCraftingState();
            throw new IllegalArgumentException("Minecraft konnte den ersten Schritt der Rezeptkette nicht in die geöffnete Craftingfläche legen.");
        }
        mode = Mode.CRAFT;
        notifyPlayer(mc, "Crafting-Rezeptkette gestartet · " + craftingPlan.size()
                + " Schritte. Zwischenprodukte werden einzeln geprüft; „Stopp“ bricht ab.");
        return "Crafting-Rezeptkette gestartet. Nightfall stellt Zwischenprodukte nacheinander her, bestätigt jede Ausgabe und fährt erst dann mit dem nächsten Rezept fort.";
    }

    private static String validateCraftItemName(String requestedItem) {
        if (requestedItem == null || requestedItem.isBlank() || requestedItem.length() > 64)
            throw new IllegalArgumentException("Gib eine gültige Item-ID oder einen Itemnamen an (maximal 64 Zeichen).");
        return requestedItem.trim();
    }

    private static AbstractCraftingScreenHandler requireEmptyCraftingGrid(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.getNetworkHandler() == null)
            throw new IllegalArgumentException("Du musst dich in einer verbundenen Minecraft-Welt befinden.");
        if (!(mc.player.currentScreenHandler instanceof AbstractCraftingScreenHandler handler))
            throw new IllegalArgumentException("Öffne zuerst dein Spielerinventar oder eine Crafting-Table. Andere Container werden nicht zum Crafting verwendet.");
        if (handler.getWidth() < 2 || handler.getHeight() < 2)
            throw new IllegalArgumentException("Die geöffnete Craftingfläche ist zu klein.");
        if (!handler.getCursorStack().isEmpty())
            throw new IllegalArgumentException("Dein Maus-Cursor hält noch einen Gegenstand. Lege ihn zuerst in dein Inventar.");
        if (!handler.getOutputSlot().getStack().isEmpty())
            throw new IllegalArgumentException("Im Crafting-Ausgabefeld liegt bereits ein Ergebnis. Räume es zuerst weg.");
        for (Slot slot : handler.getInputSlots()) {
            if (!slot.getStack().isEmpty())
                throw new IllegalArgumentException("Das Craftingfeld ist nicht leer. Räume vorhandene Zutaten zuerst weg.");
        }
        return handler;
    }

    private static Item resolveCraftItem(String requestedItem) {
        String requested = validateCraftItemName(requestedItem);
        Identifier id = Identifier.tryParse(requested.contains(":") ? requested : "minecraft:" + requested);
        Item item = id == null ? null : Registries.ITEM.get(id);
        if (item == null || item == Items.AIR) {
            for (Item candidate : Registries.ITEM) {
                if (candidate.getName().getString().equalsIgnoreCase(requested)) {
                    if (item != null) throw new IllegalArgumentException("Der Itemname ist nicht eindeutig. Nutze eine vollständige Item-ID.");
                    item = candidate;
                }
            }
        }
        if (item == null || item == Items.AIR)
            throw new IllegalArgumentException("Ich kenne kein Item mit der ID oder dem Namen „" + requested + "“.");
        if (!"minecraft".equals(Registries.ITEM.getId(item).getNamespace()))
            throw new IllegalArgumentException("Die KI-Crafting-Automation unterstützt derzeit nur Vanilla-Minecraft-Rezepte.");
        if (isRestrictedCraftItem(item))
            throw new IllegalArgumentException("Diese Gegenstandskategorie ist für die KI-Crafting-Automation nicht freigegeben.");
        return item;
    }

    private static boolean isRestrictedCraftItem(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        if (!"minecraft".equals(id.getNamespace())) return true;
        String path = id.getPath();
        return path.endsWith("_sword") || path.endsWith("_axe") || path.equals("bow")
                || path.equals("crossbow") || path.equals("trident") || path.equals("mace")
                || path.equals("shield") || path.equals("arrow") || path.equals("spectral_arrow")
                || path.equals("tipped_arrow") || path.equals("firework_rocket")
                || path.equals("firework_star") || path.equals("wind_charge")
                || path.equals("breeze_rod") || path.equals("heavy_core") || path.contains("potion")
                || path.equals("tnt") || path.equals("end_crystal") || path.equals("fire_charge");
    }

    private record CraftingPlanStep(RecipeDisplayEntry recipe, Item resultItem, ItemStack resultStack,
                                    int batches, int gridWidth, int gridHeight) {}
    private record CraftingRecipeOption(RecipeDisplayEntry recipe, ItemStack resultStack,
                                        List<List<ItemStack>> ingredientChoices, int gridWidth, int gridHeight) {}

    private static List<CraftingPlanStep> createCraftingPlan(MinecraftClient mc, Item target, int additionalCount) {
        Map<Item, Integer> simulated = new HashMap<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty()) simulated.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        List<CraftingPlanStep> steps = new ArrayList<>();
        if (!planCraftingItem(mc, target, simulated.getOrDefault(target, 0) + additionalCount,
                simulated, steps, new HashSet<>(), 0))
            throw new IllegalArgumentException("Für „" + target.getName().getString()
                    + "“ finde ich mit deinen freigeschalteten Rezepten und Zutaten keine vollständige Crafting-Kette. "
                    + "Prüfe Rezeptbuch, fehlende Rohstoffe und ob alle Zwischenrezepte freigeschaltet sind.");
        if (steps.isEmpty())
            throw new IllegalArgumentException("Du hast das gewünschte Item bereits in ausreichender Menge.");
        return List.copyOf(steps);
    }

    private static boolean planCraftingItem(MinecraftClient mc, Item target, int requiredTotal,
                                            Map<Item, Integer> available, List<CraftingPlanStep> steps,
                                            Set<Item> visiting, int depth) {
        if (isRestrictedCraftItem(target)) return false;
        if (available.getOrDefault(target, 0) >= requiredTotal) return true;
        if (depth > MAX_CRAFT_CHAIN_DEPTH || steps.size() >= MAX_CRAFT_CHAIN_STEPS
                || !visiting.add(target)) return false;

        int deficit = requiredTotal - available.getOrDefault(target, 0);
        for (CraftingRecipeOption option : findCraftingRecipeOptions(mc, target)) {
            int outputCount = Math.max(1, option.resultStack().getCount());
            int batches = (deficit + outputCount - 1) / outputCount;
            if (batches < 1 || batches > MAX_CRAFT_CHAIN_BATCHES) continue;

            Map<Item, Integer> trialAvailable = new HashMap<>(available);
            List<CraftingPlanStep> trialSteps = new ArrayList<>(steps);
            boolean ingredientsResolved = true;
            for (List<ItemStack> alternatives : option.ingredientChoices()) {
                if (alternatives.isEmpty()) continue;
                List<ItemStack> ordered = new ArrayList<>(alternatives);
                Map<Item, Integer> availableSnapshot = trialAvailable;
                ordered.sort(Comparator
                        .comparingInt((ItemStack stack) -> availableSnapshot.getOrDefault(stack.getItem(), 0)).reversed()
                        .thenComparing(stack -> Registries.ITEM.getId(stack.getItem()).toString()));
                boolean slotResolved = false;
                for (ItemStack ingredient : ordered) {
                    int ingredientCount = Math.max(1, ingredient.getCount()) * batches;
                    Map<Item, Integer> branchAvailable = new HashMap<>(trialAvailable);
                    List<CraftingPlanStep> branchSteps = new ArrayList<>(trialSteps);
                    if (!planCraftingItem(mc, ingredient.getItem(), branchAvailable.getOrDefault(ingredient.getItem(), 0)
                            + ingredientCount, branchAvailable, branchSteps, visiting, depth + 1)) continue;
                    int present = branchAvailable.getOrDefault(ingredient.getItem(), 0);
                    if (present < ingredientCount) continue;
                    branchAvailable.put(ingredient.getItem(), present - ingredientCount);
                    trialAvailable = branchAvailable;
                    trialSteps = branchSteps;
                    slotResolved = true;
                    break;
                }
                if (!slotResolved) {
                    ingredientsResolved = false;
                    break;
                }
            }
            if (!ingredientsResolved || trialSteps.size() >= MAX_CRAFT_CHAIN_STEPS
                    || trialSteps.stream().mapToInt(CraftingPlanStep::batches).sum() + batches > MAX_CRAFT_CHAIN_BATCHES)
                continue;

            trialAvailable.merge(target, outputCount * batches, Integer::sum);
            trialSteps.add(new CraftingPlanStep(option.recipe(), target, option.resultStack().copy(),
                    batches, option.gridWidth(), option.gridHeight()));
            available.clear();
            available.putAll(trialAvailable);
            steps.clear();
            steps.addAll(trialSteps);
            visiting.remove(target);
            return true;
        }
        visiting.remove(target);
        return false;
    }

    private static List<CraftingRecipeOption> findCraftingRecipeOptions(MinecraftClient mc, Item target) {
        List<CraftingRecipeOption> options = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RecipeResultCollection collection : mc.player.getRecipeBook().getOrderedResults()) {
            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                if (!seen.add(entry.id().toString())) continue;
                ItemStack output = ItemStack.EMPTY;
                try {
                    for (ItemStack candidate : entry.getStacks(EMPTY_RECIPE_CONTEXT)) {
                        if (candidate != null && !candidate.isEmpty() && candidate.isOf(target)) {
                            output = candidate.copy();
                            break;
                        }
                    }
                } catch (RuntimeException ignored) {
                    continue;
                }
                if (output.isEmpty()) continue;

                List<SlotDisplay> displays;
                int width, height;
                if (entry.display() instanceof ShapedCraftingRecipeDisplay shaped) {
                    width = shaped.width();
                    height = shaped.height();
                    if (width < 1 || height < 1 || width > 3 || height > 3) continue;
                    displays = shaped.ingredients();
                } else if (entry.display() instanceof ShapelessCraftingRecipeDisplay shapeless) {
                    if (shapeless.ingredients().isEmpty() || shapeless.ingredients().size() > 9) continue;
                    displays = shapeless.ingredients();
                    width = shapeless.ingredients().size() > 4 ? 3 : 2;
                    height = shapeless.ingredients().size() > 4 ? 3 : 2;
                } else {
                    continue;
                }

                List<List<ItemStack>> ingredientChoices = new ArrayList<>();
                boolean valid = true;
                for (SlotDisplay display : displays) {
                    List<ItemStack> candidates;
                    try {
                        candidates = display.getStacks(EMPTY_RECIPE_CONTEXT).stream()
                                .filter(stack -> stack != null && !stack.isEmpty())
                                .map(ItemStack::copy).toList();
                    } catch (RuntimeException unresolvedDisplay) {
                        valid = false;
                        break;
                    }
                    if (candidates.isEmpty()) continue; // Empty shaped grid cell.
                    ingredientChoices.add(candidates);
                }
                if (valid && !ingredientChoices.isEmpty()) {
                    options.add(new CraftingRecipeOption(entry, output, List.copyOf(ingredientChoices), width, height));
                }
            }
        }
        options.sort(Comparator.comparing(option -> option.recipe().id().toString()));
        return options;
    }

    private static boolean startCraftingPlanStep(MinecraftClient mc) {
        if (craftingHandler == null || mc.player.currentScreenHandler != craftingHandler
                || craftingPlanIndex < 0 || craftingPlanIndex >= craftingPlan.size()) return false;
        CraftingPlanStep step = craftingPlan.get(craftingPlanIndex);
        craftingTarget = step.resultItem();
        craftingRecipe = step.recipe();
        craftingName = step.resultStack().getName().getString();
        craftingCountBefore = countInventoryItem(mc, craftingTarget);
        craftingGoalCount = craftingCountBefore + step.resultStack().getCount() * step.batches();
        craftingIdleTicks = 0;
        craftingBatchesTaken = 0;
        craftingNeedsRefill = false;
        craftingPendingBatchExpectedCount = 0;
        craftingPendingBatchWaitTicks = 0;
        try {
            mc.interactionManager.clickRecipe(craftingHandler.syncId, craftingRecipe.id(), false);
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    private static boolean matchesCraftItem(ItemStack stack, String requestedItem) {
        String requested = requestedItem.trim();
        var id = Registries.ITEM.getId(stack.getItem());
        return id.toString().equalsIgnoreCase(requested)
                || id.getPath().equalsIgnoreCase(requested)
                || stack.getName().getString().equalsIgnoreCase(requested);
    }

    /**
     * Build a stable sort plan for the player's 36 normal inventory/hotbar slots.
     * Armor, offhand, crafting grid and cursor are deliberately never touched.
     */
    public static String previewInventorySort() {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        if (mode != Mode.NONE)
            throw new IllegalArgumentException("Beende zuerst die laufende Nightfall-Aktion.");
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler)
            throw new IllegalArgumentException("Schließe zuerst den geöffneten Container.");
        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty())
            throw new IllegalArgumentException("Dein Maus-Cursor hält noch einen Gegenstand. Lege ihn zuerst ins Inventar.");

        List<InventorySwap> plan = createInventorySortPlan(mc);
        if (plan.isEmpty()) return "Dein normales Inventar ist bereits sortiert.";
        int filled = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (!mc.player.getInventory().getStack(slot).isEmpty()) filled++;
        }
        int protectedSlots = 0;
        for (int slot = 0; slot < 36; slot++) {
            if (isProtectedInventoryItem(mc.player.getInventory().getStack(slot))) protectedSlots++;
        }
        return "Nightfall sortiert nach deiner Bestätigung normale Gegenstände in den 36 Inventar- und Hotbar-Slots "
                + "nach Kategorie und Item-ID. Geplant sind " + plan.size()
                + " bestätigte Slot-Tausche. Stackgrößen und Komponenten bleiben exakt erhalten; "
                + protectedSlots + " geschützte Slots bleiben unverändert. Rüstung, Offhand, Crafting-Felder "
                + "und Maus-Cursor werden nicht berührt. Die Aktion wartet auf normale Serverbestätigungen.";
    }

    /** Start sorting only after the player accepted the preview. */
    public static String sortInventory() {
        MinecraftClient mc = MinecraftClient.getInstance();
        previewInventorySort();
        List<InventorySwap> plan = createInventorySortPlan(mc);
        if (plan.isEmpty()) return "Dein normales Inventar ist bereits sortiert.";
        inventorySortPlan = List.copyOf(plan);
        inventorySortIndex = 0;
        mode = Mode.SORT;
        notifyPlayer(mc, "Sortiere dein Inventar · " + plan.size() + " Slot-Tausche. Du kannst jederzeit „Stopp“ sagen.");
        return "Inventarsortierung gestartet. Nightfall ordnet die 36 normalen Inventar-/Hotbar-Slots Schritt für Schritt.";
    }

    private static List<InventorySwap> createInventorySortPlan(MinecraftClient mc) {
        List<Integer> sortableSlots = new ArrayList<>();
        List<ItemStack> working = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot).copy();
            if (stack.isEmpty() || !isProtectedInventoryItem(stack)) {
                sortableSlots.add(slot);
                working.add(stack);
            }
        }
        List<ItemStack> desired = new ArrayList<>();
        for (ItemStack stack : working) if (!stack.isEmpty()) desired.add(stack.copy());
        desired.sort(Comparator.comparingInt(AIPlayerController::inventorySortCategory)
                .thenComparing(stack -> Registries.ITEM.getId(stack.getItem()).toString())
                .thenComparing(stack -> stack.getName().getString())
                .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed()));
        while (desired.size() < sortableSlots.size()) desired.add(ItemStack.EMPTY);

        List<InventorySwap> swaps = new ArrayList<>();
        for (int target = 0; target < sortableSlots.size(); target++) {
            ItemStack wanted = desired.get(target);
            if (ItemStack.areEqual(working.get(target), wanted)) continue;
            int source = -1;
            for (int candidate = target + 1; candidate < sortableSlots.size(); candidate++) {
                if (ItemStack.areEqual(working.get(candidate), wanted)) {
                    source = candidate;
                    break;
                }
            }
            if (source < 0)
                throw new IllegalStateException("Das Inventar hat sich während der Sortiervorschau geändert. Bitte erneut versuchen.");
            swaps.add(new InventorySwap(sortableSlots.get(target), sortableSlots.get(source)));
            ItemStack temporary = working.get(target);
            working.set(target, working.get(source));
            working.set(source, temporary);
        }
        return swaps;
    }

    /** Keep combat-oriented items in their original slots instead of rearranging them. */
    private static boolean isProtectedInventoryItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        return path.endsWith("_sword") || path.endsWith("_axe")
                || path.equals("bow") || path.equals("crossbow") || path.equals("trident")
                || path.equals("mace") || path.equals("shield")
                || path.equals("arrow") || path.equals("spectral_arrow") || path.equals("tipped_arrow")
                || path.equals("firework_rocket") || path.equals("firework_star")
                || path.equals("wind_charge") || path.equals("breeze_rod") || path.equals("heavy_core")
                || path.contains("potion") || path.equals("tnt") || path.equals("end_crystal")
                || path.equals("fire_charge");
    }

    /** Stable, broad categories; every normal inventory item participates in sorting. */
    private static int inventorySortCategory(ItemStack stack) {
        if (stack.isEmpty()) return 8;
        if (stack.getItem() instanceof BlockItem) return 0;
        String path = Registries.ITEM.getId(stack.getItem()).getPath();
        if (path.contains("ore") || path.startsWith("raw_") || path.contains("ingot")
                || path.contains("nugget") || path.contains("diamond") || path.contains("emerald")
                || path.contains("lapis") || path.contains("redstone") || path.contains("coal")
                || path.contains("quartz") || path.contains("ancient_debris")
                || path.contains("amethyst")) return 1;
        if (stack.contains(DataComponentTypes.FOOD) || stack.contains(DataComponentTypes.CONSUMABLE)) return 2;
        if (stack.getMaxDamage() > 0) return 3;
        if (path.contains("potion") || path.contains("splash") || path.contains("lingering")
                || path.contains("book") || path.contains("enchanted_book")) return 4;
        if (path.contains("music_disc") || path.contains("banner") || path.contains("dye")
                || path.contains("shulker_box") || path.contains("bundle")) return 5;
        if (path.contains("arrow") || path.equals("firework_rocket") || path.equals("fire_charge")
                || path.equals("tnt") || path.equals("end_crystal")) return 6;
        return 7;
    }

    private static int inventorySlotToPlayerHandlerSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    private static void tickInventorySort(MinecraftClient mc) {
        PlayerScreenHandler handler = mc.player.playerScreenHandler;
        if (mc.player.currentScreenHandler != handler || !handler.getCursorStack().isEmpty()) {
            stop("Inventarsortierung gestoppt: Containerwechsel oder belegter Maus-Cursor erkannt.");
            return;
        }
        if (inventorySortIndex >= inventorySortPlan.size()) {
            int completed = inventorySortPlan.size();
            List<InventorySwap> remainingChanges = createInventorySortPlan(mc);
            if (!remainingChanges.isEmpty()) {
                stop("Inventarsortierung nicht vollständig verifiziert: " + completed
                        + " Slot-Tausche wurden serverseitig bestätigt, aber die Abschlussprüfung findet noch "
                        + remainingChanges.size() + " notwendige Sortierschritte. Es wurde nichts weiter verändert.");
                return;
            }
            stop("Inventarsortierung verifiziert: Alle " + completed
                    + " Slot-Tausche wurden bestätigt und das normale Inventar entspricht jetzt der Sortierreihenfolge.");
            return;
        }

        if (pendingInventorySwap != null) {
            inventorySortWaitTicks++;
            if (inventorySortWaitTicks < 2) return;
            ItemStack actualFirst = mc.player.getInventory().getStack(pendingInventorySwap.firstSlot());
            ItemStack actualSecond = mc.player.getInventory().getStack(pendingInventorySwap.secondSlot());
            if (!ItemStack.areEqual(actualFirst, pendingFirstStack)
                    || !ItemStack.areEqual(actualSecond, pendingSecondStack)) {
                if (inventorySortWaitTicks > 20)
                    stop("Inventarsortierung gestoppt: Der Server hat einen Slot-Tausch nicht bestätigt.");
                return;
            }
            pendingInventorySwap = null;
            pendingFirstStack = ItemStack.EMPTY;
            pendingSecondStack = ItemStack.EMPTY;
            inventorySortWaitTicks = 0;
            inventorySortIndex++;
            return;
        }

        InventorySwap swap = inventorySortPlan.get(inventorySortIndex);
        int first = inventorySlotToPlayerHandlerSlot(swap.firstSlot());
        int second = inventorySlotToPlayerHandlerSlot(swap.secondSlot());
        ItemStack firstBefore = mc.player.getInventory().getStack(swap.firstSlot()).copy();
        ItemStack secondBefore = mc.player.getInventory().getStack(swap.secondSlot()).copy();
        try {
            // Three normal pickup clicks swap two unlike stacks, or move a stack into an empty slot.
            mc.interactionManager.clickSlot(handler.syncId, first, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(handler.syncId, second, 0, SlotActionType.PICKUP, mc.player);
            if (!handler.getCursorStack().isEmpty())
                mc.interactionManager.clickSlot(handler.syncId, first, 0, SlotActionType.PICKUP, mc.player);
            if (!handler.getCursorStack().isEmpty()) {
                stop("Inventarsortierung gestoppt: Der Maus-Cursor konnte nicht sicher zurückgelegt werden.");
                return;
            }
            pendingInventorySwap = swap;
            pendingFirstStack = secondBefore;
            pendingSecondStack = firstBefore;
            inventorySortWaitTicks = 0;
        } catch (RuntimeException failure) {
            // The first slot is empty after the first pickup, so return any held stack there.
            try {
                if (!handler.getCursorStack().isEmpty())
                    mc.interactionManager.clickSlot(handler.syncId, first, 0, SlotActionType.PICKUP, mc.player);
            } catch (RuntimeException ignored) {
                // Keep the cursor contents visible rather than dropping them.
            }
            stop("Inventarsortierung gestoppt: Minecraft hat einen Slot-Tausch abgelehnt.");
        }
    }

    private static void resetInventorySortState() {
        inventorySortPlan = List.of();
        inventorySortIndex = 0;
        pendingInventorySwap = null;
        pendingFirstStack = ItemStack.EMPTY;
        pendingSecondStack = ItemStack.EMPTY;
        inventorySortWaitTicks = 0;
    }

    private static int countInventoryItem(MinecraftClient mc, Item item) {
        int count = 0;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.isOf(item)) count += stack.getCount();
        }
        return count;
    }

    /** Counts how many additional items of the requested type fit in the 36 normal inventory slots. */
    private static int availableInventoryCapacity(MinecraftClient mc, Item item) {
        int capacity = 0;
        int emptySlotCapacity = new ItemStack(item).getMaxCount();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty()) {
                capacity += emptySlotCapacity;
            } else if (stack.isOf(item)) {
                capacity += Math.max(0, stack.getMaxCount() - stack.getCount());
            }
        }
        return capacity;
    }

    /** Returns a PlayerScreenHandler slot ID able to store the whole output stack, or -1. */
    private static int findCraftOutputStorageSlot(MinecraftClient mc, Item item, int outputCount) {
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            ItemStack stack = mc.player.getInventory().getStack(inventorySlot);
            if (!stack.isEmpty() && stack.isOf(item)
                    && stack.getCount() + outputCount <= stack.getMaxCount()) {
                return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
            }
        }
        for (int inventorySlot = 0; inventorySlot < 36; inventorySlot++) {
            if (mc.player.getInventory().getStack(inventorySlot).isEmpty())
                return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        }
        return -1;
    }

    private static void tickCrafting(MinecraftClient mc) {
        if (++craftingTicks > MAX_CRAFTING_TICKS || mc.interactionManager == null) {
            stop("Crafting-Rezeptkette gestoppt: Zeitlimit erreicht. Der letzte bestätigte Zwischenschritt bleibt erhalten.");
            return;
        }
        if (craftingHandler == null || mc.player.currentScreenHandler != craftingHandler) {
            stop("Crafting-Rezeptkette gestoppt: Die benötigte Craftingfläche wurde geschlossen oder gewechselt.");
            return;
        }

        AbstractCraftingScreenHandler handler = craftingHandler;
        if (!handler.getCursorStack().isEmpty()) {
            stop("Crafting gestoppt: Der Maus-Cursor ist nicht leer. Lege den Gegenstand zuerst ins Inventar.");
            return;
        }

        int currentCount = countInventoryItem(mc, craftingTarget);
        if (currentCount >= craftingGoalCount) {
            if (craftingPlanIndex + 1 < craftingPlan.size()) {
                cleanupCraftingGrid(mc);
                craftingPlanIndex++;
                if (!startCraftingPlanStep(mc)) {
                    stop("Crafting-Kette angehalten: Das nächste freigeschaltete Rezept konnte nicht in die Craftingfläche gelegt werden.");
                } else {
                    notifyPlayer(mc, "Crafting-Zwischenschritt bestätigt · "
                            + (craftingPlanIndex + 1) + "/" + craftingPlan.size()
                            + " · " + craftingName + ".");
                }
                return;
            }
            int finalCount = countInventoryItem(mc, craftingFinalTarget);
            int produced = Math.max(0, finalCount - craftingFinalCountBefore);
            int requested = Math.max(0, craftingFinalGoalCount - craftingFinalCountBefore);
            String resultLabel = finalCount >= craftingFinalGoalCount
                    ? "Crafting vollständig abgeschlossen"
                    : "Crafting teilweise abgeschlossen";
            stop(resultLabel + ": " + produced + "/" + requested + " zusätzlich gewünschte "
                    + craftingFinalTarget.getName().getString() + " hergestellt. "
                    + "Bestätigter Inventarbestand: " + finalCount + ".");
            return;
        }

        if (craftingNeedsRefill) {
            // Do not prepare the next batch until the server-synchronized inventory
            // count proves that the previous output actually arrived.
            if (currentCount < craftingPendingBatchExpectedCount) {
                if (++craftingPendingBatchWaitTicks > MAX_CRAFTING_IDLE_TICKS) {
                    stop("Crafting gestoppt: Der Server hat die letzte Ausgabe nicht im Inventar bestätigt. "
                            + "Bestätigt hergestellt: " + Math.max(0, currentCount - craftingCountBefore)
                            + " zusätzliche " + craftingName + ".");
                }
                return;
            }
            craftingNeedsRefill = false;
            craftingPendingBatchExpectedCount = 0;
            craftingPendingBatchWaitTicks = 0;
            try {
                mc.interactionManager.clickRecipe(handler.syncId, craftingRecipe.id(), false);
                craftingIdleTicks = 0;
            } catch (RuntimeException failure) {
                stop("Crafting beendet: " + Math.max(0, currentCount - craftingCountBefore)
                        + " zusätzliche " + craftingName + " serverbestätigt hergestellt; weitere Zutaten konnten nicht eingesetzt werden.");
            }
            return;
        }

        ItemStack output = handler.getOutputSlot().getStack();
        if (!output.isEmpty()) {
            if (!output.isOf(craftingTarget)) {
                stop("Crafting gestoppt: Minecraft zeigte ein anderes Ergebnis als angefordert.");
                return;
            }
            int destinationSlot = findCraftOutputStorageSlot(mc, craftingTarget, output.getCount());
            if (destinationSlot < 0) {
                stop("Crafting gestoppt: Dein Inventar ist voll. Hergestellt: "
                        + Math.max(0, currentCount - craftingCountBefore) + " zusätzliche " + craftingName + ".");
                return;
            }
            try {
                mc.interactionManager.clickSlot(handler.syncId, handler.getOutputSlot().id,
                        0, SlotActionType.PICKUP, mc.player);
                if (!handler.getCursorStack().isOf(craftingTarget)) {
                    stop("Crafting gestoppt: Die Rezept-Ausgabe konnte nicht sicher aufgenommen werden.");
                    return;
                }
                mc.interactionManager.clickSlot(handler.syncId, destinationSlot,
                        0, SlotActionType.PICKUP, mc.player);
                if (!handler.getCursorStack().isEmpty()) {
                    stop("Crafting gestoppt: Die Ausgabe konnte nicht vollständig ins Inventar gelegt werden.");
                    return;
                }
                craftingBatchesTaken++;
                craftingPendingBatchExpectedCount = currentCount + output.getCount();
                craftingPendingBatchWaitTicks = 0;
                craftingNeedsRefill = true;
                craftingIdleTicks = 0;
            } catch (RuntimeException failure) {
                stop("Crafting gestoppt: Minecraft hat die Ausgabe nicht sicher ins Inventar übertragen.");
            }
            return;
        }

        if (++craftingIdleTicks > (craftingBatchesTaken == 0 ? MAX_CRAFTING_IDLE_TICKS * 4 : MAX_CRAFTING_IDLE_TICKS)) {
            stop("Crafting beendet: " + Math.max(0, currentCount - craftingCountBefore) + " zusätzliche "
                    + craftingName + " hergestellt. Zutaten fehlen, das Rezept ist nicht craftbar oder der Server hat keine Ausgabe bestätigt.");
        }
    }

    private static void cleanupCraftingGrid(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null || craftingHandler == null
                || mc.player.currentScreenHandler != craftingHandler) return;
        for (Slot slot : craftingHandler.getInputSlots()) {
            if (slot.getStack().isEmpty()) continue;
            try {
                mc.interactionManager.clickSlot(craftingHandler.syncId, slot.id, 0,
                        SlotActionType.QUICK_MOVE, mc.player);
            } catch (RuntimeException ignored) {
                // Leave server-owned crafting contents untouched if a normal return click fails.
            }
        }
    }

    private static void resetCraftingState() {
        craftingTarget = null;
        craftingRecipe = null;
        craftingHandler = null;
        craftingPlan = List.of();
        craftingPlanIndex = 0;
        craftingFinalTarget = null;
        craftingFinalCountBefore = 0;
        craftingFinalGoalCount = 0;
        craftingName = "";
        craftingCountBefore = 0;
        craftingGoalCount = 0;
        craftingTicks = 0;
        craftingIdleTicks = 0;
        craftingBatchesTaken = 0;
        craftingNeedsRefill = false;
        craftingPendingBatchExpectedCount = 0;
        craftingPendingBatchWaitTicks = 0;
    }

    private static void tickEating(MinecraftClient mc) {
        if (++actionTicks > MAX_EATING_TICKS) {
            stop("Essen gestoppt: Zeitlimit erreicht.");
            return;
        }
        ItemStack stack = mc.player.getInventory().getStack(foodSlot);
        int hungerNow = mc.player.getHungerManager().getFoodLevel();
        if (!mc.player.isUsingItem()) {
            if (stack.getCount() < foodCountBefore || hungerNow > hungerBefore)
                stop("Essen abgeschlossen: " + foodName + " wurde verwendet.");
            else
                stop("Essen beendet, aber der Server hat keine Nahrungsaufnahme bestätigt.");
            return;
        }
        mc.options.useKey.setPressed(true);
    }

    public static String stop(String reason) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Mode previous = mode;
        if (previous != Mode.NONE && reason != null && !reason.isBlank()) {
            lastActionSummary = reason.length() > 240 ? reason.substring(0, 237) + "..." : reason;
        }
        releaseKeys(mc);
        if ((previous == Mode.MINE || previous == Mode.GATHER_MINE || previous == Mode.GATHER_TUNNEL_MINE)
                && mc.interactionManager != null) {
            try { mc.interactionManager.cancelBlockBreaking(); } catch (RuntimeException ignored) {}
        }
        if (previous == Mode.EAT && mc.player != null) {
            try { mc.player.stopUsingItem(); } catch (RuntimeException ignored) {}
            restoreHotbar(mc);
        }
        if ((previous == Mode.MINE || previous == Mode.GATHER_MINE || previous == Mode.GATHER_TUNNEL_MINE)
                && mc.player != null) {
            restoreMiningTool(mc);
            restoreHotbar(mc);
        }
        if (previous == Mode.CRAFT) cleanupCraftingGrid(mc);
        mode = Mode.NONE;
        forwardTicks = 0;
        actionTicks = 0;
        stuckTicks = 0;
        pathIndex = 0;
        path = List.of();
        navigationGoal = null;
        navigationIsUserGoal = false;
        structureSearchFilter = "any";
        structureSearchDirection = 0;
        structureSearchLegLength = 0;
        structureSearchLegProgress = 0;
        structureSearchLegsAtLength = 0;
        structureSearchScanCooldown = 0;
        structureSearchScannedBlocks = 0;
        miningPos = null;
        miningState = null;
        foodSlot = -1;
        foodName = "";
        resetCraftingState();
        resetInventorySortState();
        resetGatherState();
        resetSmeltingState();
        if (previous != Mode.NONE && mc.player != null && reason != null)
            notifyPlayer(mc, reason);
        return previous == Mode.NONE ? "Nightfall führt gerade keine Bewegung oder Interaktion aus." : "Nightfall-Aktion gestoppt.";
    }

    public static String stopMovement(String reason) {
        return stop(reason);
    }

    private static void saveResumableTaskBeforeDisconnect(MinecraftClient mc) {
        if (mc == null || mc.player == null || mc.world == null) return;
        AITaskCheckpoint.Task task = null;
        if (gatherMaterial != null && (mode == Mode.GATHER || mode == Mode.GATHER_MINE
                || mode == Mode.GATHER_TUNNEL || mode == Mode.GATHER_TUNNEL_MINE
                || mode == Mode.NAVIGATE)) {
            int remainingSeconds = gatherDeadlineNanos > 0L
                    ? (int) Math.max(1L, Math.min(1_200L,
                    (gatherDeadlineNanos - System.nanoTime() + 999_999_999L) / 1_000_000_000L))
                    : 0;
            if (gatherDeadlineNanos == 0L || gatherDeadlineNanos > System.nanoTime()) {
                String itemId = Registries.ITEM.getId(gatherMaterial).toString();
                String kind = gatherRequiresSuitableTool ? "gather_ore"
                        : isGatherableWoodPath(Registries.ITEM.getId(gatherMaterial).getPath())
                        ? "gather_wood" : "gather_block";
                task = new AITaskCheckpoint.Task(kind, itemId, gatherGoalCount,
                        remainingSeconds, gatherTargetY, 0, 0, 0);
            }
        } else if (mode == Mode.NAVIGATE && navigationIsUserGoal && navigationGoal != null) {
            task = new AITaskCheckpoint.Task("navigate", "", 1, 0,
                    Integer.MIN_VALUE, navigationGoal.getX(), navigationGoal.getY(), navigationGoal.getZ());
        }
        if (task == null || !AITaskCheckpoint.save(mc, task)) AITaskCheckpoint.clear();
    }

    /** Read-only preview of a same-server, same-dimension task intent saved at disconnect. */
    public static String previewSavedTask() {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        AITaskCheckpoint.Task task = AITaskCheckpoint.loadForCurrentWorld(mc);
        if (task == null)
            return "Kein gültiger gespeicherter Sammel- oder Navigationsauftrag für diese Welt und Dimension vorhanden.";
        return switch (task.kind()) {
            case "gather_wood" -> previewGatherWood(task.item(), task.count(), task.durationSeconds(), task.targetY())
                    + " Gespeicherter Auftrag; Ziele werden nach Bestätigung neu gesucht.";
            case "gather_block" -> previewGatherBlock(task.item(), task.count(), task.durationSeconds())
                    + " Gespeicherter Auftrag; Ziele werden nach Bestätigung neu gesucht.";
            case "gather_ore" -> previewGatherOre(task.item(), task.count(), task.durationSeconds(), task.targetY())
                    + " Gespeicherter Auftrag; Ziele werden nach Bestätigung neu gesucht.";
            case "navigate" -> {
                BlockPos target = new BlockPos(task.x(), task.y(), task.z());
                validateNavigationTarget(mc, target);
                yield "Gespeichertes Navigationsziel X=" + task.x() + ", Y=" + task.y() + ", Z=" + task.z()
                        + ". Nach deiner Bestätigung plant Nightfall die gesamte Route in sicheren, neu geprüften Abschnitten durch geladene Chunks.";
            }
            default -> "Der gespeicherte Auftragstyp wird nicht unterstützt.";
        };
    }

    /** Revalidates and starts a fresh scan/path only after the chat action is confirmed. */
    public static String resumeSavedTask() {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        AITaskCheckpoint.Task task = AITaskCheckpoint.loadForCurrentWorld(mc);
        if (task == null)
            throw new IllegalArgumentException("Kein gültiger gespeicherter Auftrag für diese Welt und Dimension vorhanden.");
        String result = switch (task.kind()) {
            case "gather_wood" -> gatherWood(task.item(), task.count(), task.durationSeconds(), task.targetY());
            case "gather_block" -> gatherBlock(task.item(), task.count(), task.durationSeconds());
            case "gather_ore" -> gatherOre(task.item(), task.count(), task.durationSeconds(), task.targetY());
            case "navigate" -> navigateTo(task.x(), task.y(), task.z());
            default -> throw new IllegalArgumentException("Der gespeicherte Auftragstyp wird nicht unterstützt.");
        };
        AITaskCheckpoint.clear();
        return "Gespeicherter Auftrag nach erneuter Weltprüfung gestartet. " + result;
    }

    public static void onDisconnect() {
        MinecraftClient mc = MinecraftClient.getInstance();
        saveResumableTaskBeforeDisconnect(mc);
        stop(null);
    }
    public static boolean isWalking() { return mode == Mode.WALK; }
    public static boolean isNavigating() { return mode == Mode.NAVIGATE; }
    public static boolean isBusy() { return mode != Mode.NONE; }
    public static String status() {
        String current = switch (mode) {
            case NONE -> "Keine KI-Aktion aktiv";
            case WALK -> "Geradeausbewegung";
            case NAVIGATE -> "Navigation · " + Math.min(pathIndex + 1, path.size()) + "/" + path.size();
            case SEARCH_STRUCTURES -> "Struktursuche · " + Math.min(pathIndex + 1, path.size()) + "/" + path.size();
            case MINE -> "Blockabbau · " + (miningPos == null ? "" : miningPos.toShortString());
            case EAT -> "Nahrungsaufnahme · " + foodName;
            case CRAFT -> "Crafting · " + (craftingPlan.isEmpty() ? 0 : craftingPlanIndex + 1) + "/" + craftingPlan.size() + " · " + craftingName;
            case SORT -> "Inventar sortieren · " + inventorySortIndex + "/" + inventorySortPlan.size();
            case GATHER -> "Sammle " + gatherMaterialName + " · " + gatherIndex + "/" + gatherTargets.size()
                    + (isAncientDebrisItem(gatherMaterial) ? " · Spiralabschnitte " + gatherExploreLegs + "/" + MAX_GATHER_EXPLORE_LEGS : "")
                    + (gatherDeadlineNanos > 0L ? " · Zeitlimit " + gatherMaxDurationSeconds + "s" : "")
                    + (gatherTargetY != Integer.MIN_VALUE ? " · Y=" + gatherTargetY : "");
            case GATHER_MINE -> "Baue Rohstoff ab · " + (miningPos == null ? "" : miningPos.toShortString());
            case GATHER_TUNNEL -> "Grabe Ancient-Debris-Stollen · " + gatherTunnelCellIndex + "/" + gatherTunnelCells.size();
            case GATHER_TUNNEL_MINE -> "Grabe Tunnelblock · " + (miningPos == null ? "" : miningPos.toShortString());
            case SMELT -> "Schmelze " + (smeltInput == null ? "" : smeltInput.getName().getString());
        };
        if (mode == Mode.NONE && !lastActionSummary.isBlank())
            return current + "\nLetztes Aktionsergebnis: " + lastActionSummary;
        return current;
    }

    private static void validateNavigationRange(MinecraftClient mc, BlockPos target) {
        validateNavigationTarget(mc, target);
        double horizontal = Math.hypot(target.getX() - mc.player.getX(), target.getZ() - mc.player.getZ());
        if (horizontal > MAX_PATH_RADIUS)
            throw new IllegalArgumentException("Der lokale Annäherungspunkt liegt außerhalb des Suchradius.");
    }

    private static void validateNavigationTarget(MinecraftClient mc, BlockPos target) {
        int topExclusive = mc.world.getBottomY() + mc.world.getHeight();
        if (target.getY() < mc.world.getBottomY() || target.getY() >= topExclusive)
            throw new IllegalArgumentException("Das Ziel liegt außerhalb der Weltgrenzen.");
        if (Math.abs((long) target.getX()) > 30_000_000L || Math.abs((long) target.getZ()) > 30_000_000L)
            throw new IllegalArgumentException("Das Ziel liegt außerhalb der Minecraft-Weltkoordinaten.");
    }

    private static boolean isAtGoal(MinecraftClient mc, BlockPos goal) {
        double horizontal = Math.hypot(goal.getX() + 0.5 - mc.player.getX(),
                goal.getZ() + 0.5 - mc.player.getZ());
        if (gatherMaterial != null && gatherTargetY != Integer.MIN_VALUE)
            return horizontal <= 1.0 && mc.player.getBlockY() == gatherTargetY;
        return horizontal <= 1.0 && Math.abs(mc.player.getY() - goal.getY()) <= 1.25;
    }

    private static List<BlockPos> planNavigationSegment(MinecraftClient mc, BlockPos finalGoal) {
        double dx = finalGoal.getX() + 0.5 - mc.player.getX();
        double dz = finalGoal.getZ() + 0.5 - mc.player.getZ();
        double horizontal = Math.hypot(dx, dz);
        boolean segmented = horizontal > MAX_PATH_RADIUS - 4;
        double step = segmented ? NAVIGATION_SEGMENT_LENGTH : horizontal;
        double ratio = horizontal < 0.001 ? 0.0 : Math.min(1.0, step / horizontal);
        int anchorX = (int) Math.floor(mc.player.getX() + dx * ratio);
        int anchorZ = (int) Math.floor(mc.player.getZ() + dz * ratio);
        int anchorY = segmented
                ? (int) Math.round(mc.player.getY() + (finalGoal.getY() - mc.player.getY()) * ratio)
                : finalGoal.getY();
        BlockPos anchor = new BlockPos(anchorX, anchorY, anchorZ);
        BlockPos start = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        List<BlockPos> candidates = new ArrayList<>();
        int maxOffset = segmented ? 3 : 2;
        for (int dy = -2; dy <= 2; dy++) {
            for (int x = -maxOffset; x <= maxOffset; x++) {
                for (int z = -maxOffset; z <= maxOffset; z++) {
                    if (Math.abs(x) + Math.abs(z) > maxOffset * 2) continue;
                    BlockPos candidate = anchor.add(x, dy, z);
                    if (candidate.equals(start) || !canStandAt(mc, candidate, mc.player)) continue;
                    candidates.add(candidate);
                }
            }
        }
        candidates.sort(Comparator
                .comparingDouble((BlockPos p) -> p.getSquaredDistance(anchor))
                .thenComparingDouble(p -> p.getSquaredDistance(finalGoal)));
        int attempts = 0;
        for (BlockPos candidate : candidates) {
            List<BlockPos> planned = findPath(mc, candidate);
            if (!planned.isEmpty()) return planned;
            if (++attempts >= 16) break;
        }
        return List.of();
    }

    private static List<BlockPos> findPath(MinecraftClient mc, BlockPos requestedTarget) {
        PlayerEntity player = mc.player;
        BlockPos start = BlockPos.ofFloored(player.getX(), player.getY(), player.getZ());
        if (!canStandAt(mc, start, player)) {
            BlockPos lower = start.down();
            if (canStandAt(mc, lower, player)) start = lower;
            else return List.of();
        }

        List<BlockPos> goals = new ArrayList<>();
        for (int dy : new int[]{0, 1, -1}) {
            BlockPos goal = requestedTarget.add(0, dy, 0);
            if (canStandAt(mc, goal, player)) goals.add(goal);
        }
        if (goals.isEmpty()) return List.of();
        Set<BlockPos> goalSet = Set.copyOf(goals);
        if (goalSet.contains(start)) return List.of();

        Comparator<SearchNode> order = Comparator.comparingDouble(SearchNode::estimatedTotal);
        PriorityQueue<SearchNode> open = new PriorityQueue<>(order);
        Map<BlockPos, Double> costs = new HashMap<>();
        Map<BlockPos, BlockPos> parents = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        costs.put(start, 0.0);
        open.add(new SearchNode(start, 0.0, heuristic(start, goals)));

        int visited = 0;
        while (!open.isEmpty() && visited < MAX_SEARCH_NODES) {
            SearchNode current = open.poll();
            if (!closed.add(current.position())) continue;
            visited++;
            if (goalSet.contains(current.position())) return reconstruct(current.position(), start, parents);

            for (Direction direction : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                for (int dy : new int[]{1, 0, -1}) {
                    BlockPos next = current.position().offset(direction).add(0, dy, 0);
                    if (Math.abs(next.getX() - start.getX()) > MAX_PATH_RADIUS
                            || Math.abs(next.getZ() - start.getZ()) > MAX_PATH_RADIUS
                            || Math.abs(next.getY() - start.getY()) > 12
                            || closed.contains(next) || !canStandAt(mc, next, player)) continue;
                    double nextCost = current.cost() + 1.0 + Math.abs(dy) * 0.35;
                    if (nextCost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                    costs.put(next, nextCost);
                    parents.put(next, current.position());
                    open.add(new SearchNode(next, nextCost, nextCost + heuristic(next, goals)));
                }
            }
        }
        return List.of();
    }

    private static double heuristic(BlockPos from, List<BlockPos> goals) {
        double best = Double.MAX_VALUE;
        for (BlockPos goal : goals) {
            double value = Math.abs(from.getX() - goal.getX())
                    + Math.abs(from.getZ() - goal.getZ())
                    + Math.abs(from.getY() - goal.getY()) * 1.5;
            best = Math.min(best, value);
        }
        return best;
    }

    private static List<BlockPos> reconstruct(BlockPos goal, BlockPos start, Map<BlockPos, BlockPos> parents) {
        List<BlockPos> reversed = new ArrayList<>();
        BlockPos current = goal;
        while (!current.equals(start)) {
            reversed.add(current);
            current = parents.get(current);
            if (current == null || reversed.size() > MAX_SEARCH_NODES) return List.of();
        }
        java.util.Collections.reverse(reversed);
        return reversed;
    }

    private static boolean canStandAt(MinecraftClient mc, BlockPos feet, PlayerEntity player) {
        if (feet.getY() <= mc.world.getBottomY() || feet.getY() + 1 >= mc.world.getBottomY() + mc.world.getHeight()
                || !mc.world.isChunkLoaded(feet) || !mc.world.isChunkLoaded(feet.down())
                || !mc.world.isChunkLoaded(feet.up())) return false;

        BlockPos supportPos = feet.down();
        BlockState support = mc.world.getBlockState(supportPos);
        if (!support.getFluidState().isEmpty() || isHazard(support)) return false;
        var supportShape = support.getCollisionShape(mc.world, supportPos);
        if (supportShape.isEmpty() || supportShape.getMax(Direction.Axis.Y) < 0.95) return false;

        double dx = feet.getX() + 0.5 - player.getX();
        double dy = feet.getY() - player.getY();
        double dz = feet.getZ() + 0.5 - player.getZ();
        Box targetBox = player.getBoundingBox().offset(dx, dy, dz);
        int minX = (int) Math.floor(targetBox.minX);
        int minY = (int) Math.floor(targetBox.minY);
        int minZ = (int) Math.floor(targetBox.minZ);
        int maxX = (int) Math.floor(targetBox.maxX - 1.0E-6);
        int maxY = (int) Math.floor(targetBox.maxY - 1.0E-6);
        int maxZ = (int) Math.floor(targetBox.maxZ - 1.0E-6);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!mc.world.isChunkLoaded(pos)) return false;
                    BlockState state = mc.world.getBlockState(pos);
                    if (!state.getFluidState().isEmpty() || isHazard(state)) return false;
                    for (Box local : state.getCollisionShape(mc.world, pos).getBoundingBoxes()) {
                        Box worldBox = local.offset(x, y, z);
                        if (worldBox.intersects(targetBox)) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isHazard(BlockState state) {
        String id = net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).getPath();
        return id.equals("cactus") || id.equals("fire") || id.equals("soul_fire")
                || id.equals("magma_block") || id.equals("campfire") || id.equals("soul_campfire")
                || id.equals("powder_snow") || id.equals("sweet_berry_bush")
                || id.equals("wither_rose") || id.equals("pointed_dripstone");
    }

    private static void aimAt(ClientPlayerEntity player, Vec3d target) {
        Vec3d delta = target.subtract(player.getEyePos());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        // Ease the camera toward the next waypoint instead of snapping instantly.
        float yawDelta = wrapDegrees(targetYaw - player.getYaw());
        float pitchDelta = targetPitch - player.getPitch();
        player.setYaw(player.getYaw() + clampAngleStep(yawDelta, 9.0f));
        player.setPitch(player.getPitch() + clampAngleStep(pitchDelta, 7.0f));
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }

    private static float clampAngleStep(float delta, float maximumStep) {
        return Math.max(-maximumStep, Math.min(maximumStep, delta));
    }

    private static BlockHitResult raycastBlock(MinecraftClient mc, BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = pos.toCenterPos();
        return mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, mc.player)) instanceof BlockHitResult hit
                && hit.getBlockPos().equals(pos) ? hit : null;
    }

    /**
     * Read-only description of the best available tool for a target block.
     * It uses the same ranking as mining, but never swaps inventory slots.
     */
    public static String describeMiningTool(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        BlockState targetState = mc.world.getBlockState(pos);
        int bestSlot = findBestMiningToolInventorySlot(mc.player, targetState);
        ItemStack bestTool = mc.player.getInventory().getStack(bestSlot);
        ItemStack heldTool = mc.player.getMainHandStack();
        boolean bestSuitable = !bestTool.isEmpty() && bestTool.isSuitableFor(targetState);
        boolean heldSuitable = !heldTool.isEmpty() && heldTool.isSuitableFor(targetState);
        float speed = bestTool.isEmpty() ? 1.0f : bestTool.getMiningSpeedMultiplier(targetState);
        float hardness = targetState.getHardness(mc.world, pos);

        String heldDescription = heldTool.isEmpty()
                ? "leer"
                : heldTool.getName().getString() + (heldSuitable ? " (geeignet)" : " (nicht als geeignetes Erntewerkzeug erkannt)");
        String bestDescription = bestTool.isEmpty()
                ? "kein Werkzeug im Inventar"
                : bestTool.getName().getString()
                        + (bestSuitable ? " (geeignet)" : " (nicht als geeignetes Erntewerkzeug erkannt)")
                        + ", Slotindex " + bestSlot + " (0-basiert; 0–8 = Hotbar)";
        String harvestNote = bestSuitable
                ? "Ein passendes Erntewerkzeug wurde gefunden."
                : targetState.isToolRequired()
                        ? "Kein passendes Erntewerkzeug wurde im normalen 36-Slot-Inventar erkannt."
                        : "Der Blockstate meldet keine zwingende Werkzeugpflicht; ein geeignetes Werkzeug wurde nicht erkannt.";

        return "Anvisierter Block: " + targetState.getBlock().getName().getString()
                + " bei " + pos.toShortString()
                + "; Härte laut lokalem Blockstate " + String.format(java.util.Locale.ROOT, "%.2f", hardness)
                + "; Werkzeugpflicht laut Blockstate: " + (targetState.isToolRequired() ? "ja" : "nein")
                + "; aktuell gehalten: " + heldDescription
                + "; beste lokale Inventarwahl: " + bestDescription
                + "; Mining-Speed-Multiplikator des Kandidaten: "
                + String.format(java.util.Locale.ROOT, "%.2f", speed)
                + ". " + harvestNote
                + " Das ist eine clientseitige Momentaufnahme, keine garantierte Abbauzeit oder Server-Drop-Zusage. "
                + "Verzauberungen, Effekte, Haltbarkeit, Serverregeln und Verzögerungen können das Ergebnis beeinflussen.";
    }

    private static int findBestMiningToolInventorySlot(ClientPlayerEntity player, BlockState targetState) {
        int selectedSlot = player.getInventory().getSelectedSlot();
        int bestSlot = selectedSlot;
        ItemStack held = player.getInventory().getStack(selectedSlot);
        boolean bestSuitable = !held.isEmpty() && held.isSuitableFor(targetState);
        float bestSpeed = held.isEmpty() ? 1.0f : held.getMiningSpeedMultiplier(targetState);

        for (int slot = 0; slot < 36; slot++) {
            ItemStack candidate = player.getInventory().getStack(slot);
            if (candidate.isEmpty()) continue;
            boolean suitable = candidate.isSuitableFor(targetState);
            float speed = candidate.getMiningSpeedMultiplier(targetState);
            if ((suitable && !bestSuitable) || (suitable == bestSuitable && speed > bestSpeed)) {
                bestSlot = slot;
                bestSuitable = suitable;
                bestSpeed = speed;
            }
        }
        return bestSlot;
    }

    private static int findBestMiningToolSlot(MinecraftClient mc, ClientPlayerEntity player,
                                              BlockState targetState) {
        int bestSlot = findBestMiningToolInventorySlot(player, targetState);
        if (bestSlot < 9) return bestSlot;
        if (player.currentScreenHandler != player.playerScreenHandler)
            throw new IllegalArgumentException("Schließe zuerst den geöffneten Container, damit Nightfall das passende Werkzeug wechseln kann.");

        int hotbarSlot = -1;
        for (int slot = 0; slot < 9; slot++) {
            if (player.getInventory().getStack(slot).isEmpty()) {
                hotbarSlot = slot;
                break;
            }
        }
        if (hotbarSlot < 0) hotbarSlot = originalSlot >= 0 ? originalSlot : selectedSlot;
        miningToolSourceSlot = bestSlot;
        miningToolHotbarSlot = hotbarSlot;
        miningDisplacedStack = player.getInventory().getStack(hotbarSlot).copy();
        mc.interactionManager.clickSlot(player.playerScreenHandler.syncId, bestSlot, hotbarSlot,
                SlotActionType.SWAP, player);
        notifyPlayer(mc, "Passendes Werkzeug aus dem Inventar geholt: "
                + player.getInventory().getStack(hotbarSlot).getName().getString() + ".");
        return hotbarSlot;
    }

    private static void restoreMiningTool(MinecraftClient mc) {
        if (mc.player != null && miningToolSourceSlot >= 9 && miningToolHotbarSlot >= 0
                && mc.player.currentScreenHandler == mc.player.playerScreenHandler
                && ItemStack.areEqual(mc.player.getInventory().getStack(miningToolSourceSlot), miningDisplacedStack)) {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId, miningToolSourceSlot,
                    miningToolHotbarSlot, SlotActionType.SWAP, mc.player);
        }
        miningToolSourceSlot = -1;
        miningToolHotbarSlot = -1;
        miningDisplacedStack = ItemStack.EMPTY;
    }

    private static int findFoodSlot(ClientPlayerEntity player, String preferredItem) {
        int selected = -1;
        int bestNutrition = -1;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isEmpty() || !stack.contains(DataComponentTypes.CONSUMABLE)
                    || !stack.contains(DataComponentTypes.FOOD)) continue;
            if (preferredItem != null && !preferredItem.isBlank()) {
                String id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
                String path = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
                String name = stack.getName().getString();
                if (!id.equalsIgnoreCase(preferredItem) && !path.equalsIgnoreCase(preferredItem)
                        && !name.equalsIgnoreCase(preferredItem)) continue;
            }
            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            int nutrition = food == null ? 0 : food.nutrition();
            if (nutrition > bestNutrition) {
                bestNutrition = nutrition;
                selected = slot;
            }
        }
        return selected;
    }

    private static void requireWorld(MinecraftClient mc) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null)
            throw new IllegalArgumentException("Du musst dich in einer verbundenen Minecraft-Welt befinden.");
        if (mc.currentScreen != null && !(mc.currentScreen instanceof AIChatScreen))
            throw new IllegalArgumentException("Schließe zuerst andere Menüs.");
    }

    private static void restoreHotbar(MinecraftClient mc) {
        if (originalSlot >= 0 && originalSlot < 9 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(originalSlot);
        originalSlot = -1;
    }

    private static void releaseKeys(MinecraftClient mc) {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        mc.options.sprintKey.setPressed(false);
        mc.options.attackKey.setPressed(false);
        mc.options.useKey.setPressed(false);
    }

    private static void notifyPlayer(MinecraftClient mc, String message) {
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Nightfall] " + message), false);
    }

    private record SearchNode(BlockPos position, double cost, double estimatedTotal) {}
    private record HeightNavigationPlan(BlockPos goal, List<BlockPos> path) {}
    private record InventorySwap(int firstSlot, int secondSlot) {}
}
