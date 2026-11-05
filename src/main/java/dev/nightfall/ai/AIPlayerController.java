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
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
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
    private enum Mode { NONE, WALK, NAVIGATE, MINE, EAT, CRAFT, GATHER, GATHER_MINE, GATHER_TUNNEL, GATHER_TUNNEL_MINE, SMELT }

    private static final int MAX_PATH_RADIUS = 48;
    private static final int NAVIGATION_SEGMENT_LENGTH = 32;
    private static final int MAX_SEARCH_NODES = 8_000;
    private static final int MAX_NAVIGATION_TICKS = 20 * 60 * 5;
    private static final int MAX_MINING_TICKS = 20 * 30;
    private static final int MAX_EATING_TICKS = 80;
    private static final double MIN_PROGRESS = 0.025;
    private static final int MAX_CRAFTING_TICKS = 20 * 30;
    private static final int MAX_CRAFTING_IDLE_TICKS = 20;
    private static final int MAX_GATHER_BLOCKS = 32;
    private static final int MAX_SMELT_ITEMS = 16;
    private static final int MAX_SMELT_TICKS = 20 * 60 * 4;
    private static final int MAX_SMELT_OPEN_TICKS = 40;
    private static final int MAX_GATHER_TARGETS = 96;
    private static final int GATHER_HORIZONTAL_RADIUS = 16;
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
    private static final int MAX_ANCIENT_DEBRIS_RESCANS = 6;
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
    private static RecipeDisplayEntry craftingRecipe;
    private static Item gatherMaterial;
    private static boolean gatherRequiresSuitableTool;
    private static String gatherMaterialName = "";
    private static int gatherGoalCount;
    private static List<BlockPos> gatherTargets = List.of();
    private static int gatherIndex;
    private static Set<BlockPos> gatherVisitedTargets = new HashSet<>();
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
        return previewGatherResource(requestedItem, targetCount, true, false);
    }

    public static String gatherWood(String requestedItem, int targetCount) {
        return gatherResource(requestedItem, targetCount, true, false);
    }

    /** Preview a bounded collection request for a whitelisted vanilla self-dropping block. */
    public static String previewGatherBlock(String requestedItem, int targetCount) {
        return previewGatherResource(requestedItem, targetCount, false, false);
    }

    /** Start a confirmed, bounded collection task for a whitelisted vanilla self-dropping block. */
    public static String gatherBlock(String requestedItem, int targetCount) {
        return gatherResource(requestedItem, targetCount, false, false);
    }

    /** Preview collection of known ore drops using vanilla-correct tool requirements. */
    public static String previewGatherOre(String requestedItem, int targetCount) {
        return previewGatherResource(requestedItem, targetCount, false, true);
    }

    /** Start a confirmed, bounded collection task for a known ore drop. */
    public static String gatherOre(String requestedItem, int targetCount) {
        return gatherResource(requestedItem, targetCount, false, true);
    }

    private static String previewGatherResource(String requestedItem, int targetCount,
                                                boolean woodOnly, boolean oreOnly) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        if (targetCount < 1 || targetCount > 64)
            throw new IllegalArgumentException("Die Zielmenge muss zwischen 1 und 64 liegen.");
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
        if (missing > MAX_GATHER_BLOCKS)
            throw new IllegalArgumentException("Ein einzelner Sammelauftrag darf höchstens "
                    + MAX_GATHER_BLOCKS + " zusätzliche Blöcke abbauen.");
        int inventoryCapacity = availableInventoryCapacity(mc, item);
        if (inventoryCapacity <= 0)
            throw new IllegalArgumentException("Dein Inventar hat keinen freien Platz für "
                    + item.getName().getString() + ". Räume zuerst einen Slot frei.");

        List<BlockPos> candidates = findGatherableBlocks(mc, item, oreOnly);
        if (candidates.isEmpty())
            throw new IllegalArgumentException("Im geladenen Umkreis von " + GATHER_HORIZONTAL_RADIUS
                    + " Blöcken habe ich keine passenden " + item.getName().getString()
                    + " gefunden. Erkunde zuerst die Gegend oder gehe näher an passende Blöcke.");
        int toolEligible = countToolEligibleTargets(mc, candidates, oreOnly);
        if (toolEligible == 0)
            throw new IllegalArgumentException("Ich habe " + candidates.size() + " passende Blöcke gefunden, "
                    + "aber für diese Blöcke fehlt ein geeignetes Werkzeug. Lege das passende Werkzeug in dein Inventar.");
        int available = Math.min(Math.min(missing, toolEligible), inventoryCapacity);
        String kind = woodOnly ? "Holzblöcke" : oreOnly ? "Erzblöcke" : "freigegebene Rohstoffblöcke";
        String ancientNote = isAncientDebrisItem(item)
                ? " Für Ancient Debris prüft Nightfall zusätzlich kurze horizontale Stollen (max. "
                    + MAX_ANCIENT_TUNNEL_LENGTH + " Schritte) durch Netherrack, Basalt und Schwarzstein; "
                    + "nur mit passender Spitzhacke und nur wenn im Tunnel kein Lava-/Flüssigkeitsrisiko erkannt wird."
                : "";
        return "Nightfall sammelt nach deiner Bestätigung bis zu " + available + " zusätzliche "
                + item.getName().getString() + " (Ziel: " + targetCount + " im Inventar). "
                + "Gefunden: " + candidates.size() + " passende " + kind + " im bereits geladenen Umkreis; "
                + toolEligible + " davon haben ein verfügbares passendes Werkzeug. "
                + "Höchstens " + MAX_GATHER_BLOCKS + " zusätzliche Zielblöcke pro Auftrag. "
                + "Nightfall sucht sichere Standpunkte, nutzt bei toolpflichtigen Blöcken nur ein geeignetes Werkzeug "
                + "und wartet auf Block- und Item-Updates. Freier Inventarplatz für " + inventoryCapacity
                + " weitere Items. Es werden höchstens " + available + " Ziele eingeplant."
                + ancientNote;
    }

    private static String gatherResource(String requestedItem, int targetCount,
                                         boolean woodOnly, boolean oreOnly) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        previewGatherResource(requestedItem, targetCount, woodOnly, oreOnly);
        Item item = resolveGatherableItem(requestedItem, woodOnly, oreOnly);
        List<BlockPos> candidates = findGatherableBlocks(mc, item, oreOnly);
        List<BlockPos> usableTargets = new ArrayList<>();
        for (BlockPos pos : candidates) {
            BlockState state = mc.world.getBlockState(pos);
            if (hasSuitableMiningTool(mc, state, oreOnly))
                usableTargets.add(pos);
        }
        if (usableTargets.isEmpty())
            throw new IllegalArgumentException("Die passenden Rohstoffblöcke oder Werkzeuge sind inzwischen nicht mehr verfügbar.");

        stop(null);
        gatherMaterial = item;
        gatherRequiresSuitableTool = oreOnly;
        gatherMaterialName = item.getName().getString();
        gatherGoalCount = targetCount;
        gatherTargets = List.copyOf(usableTargets);
        gatherIndex = 0;
        gatherAwaitingPickup = false;
        gatherPickupTicks = 0;
        gatherCountBeforeBreak = 0;
        mode = Mode.GATHER;
        statusNotice(mc, "Sammle " + gatherMaterialName + " · Ziel " + targetCount
                + " im Inventar. Ich baue nur freigegebene Blöcke im geladenen Nahbereich ab.");
        return (woodOnly ? "Holzsammeln" : oreOnly ? "Erzabbau" : "Materialsammlung") + " gestartet. Nightfall sucht passende Blöcke, "
                + "navigiert über sichere geladene Wege, verwendet erforderliche Werkzeuge und wartet auf bestätigte Drops. „Stopp“ bricht ab.";
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
        if (gatherMaterial == null || gatherTargets.isEmpty()) {
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

        if (isAncientDebrisItem(gatherMaterial) && refreshAncientDebrisTargets(mc)) return;
        stop("Materialsammlung beendet: " + currentCount + "/" + gatherGoalCount + " "
                + gatherMaterialName + " im Inventar. Im geladenen Nahbereich sind keine weiteren passenden Rohstoffblöcke erreichbar.");
    }


    private static void advanceGatherTarget() {
        if (gatherIndex < gatherTargets.size())
            gatherVisitedTargets.add(gatherTargets.get(gatherIndex).toImmutable());
        gatherIndex++;
    }

    /** Refreshes a bounded local Ancient Debris scan around the player's updated position. */
    private static boolean refreshAncientDebrisTargets(MinecraftClient mc) {
        if (!isInNether(mc) || !isAncientDebrisItem(gatherMaterial)
                || gatherRescanCount >= MAX_ANCIENT_DEBRIS_RESCANS) return false;

        gatherRescanCount++;
        List<BlockPos> candidates = findGatherableBlocks(mc, gatherMaterial, true, gatherVisitedTargets);
        List<BlockPos> usableTargets = new ArrayList<>();
        for (BlockPos pos : candidates) {
            BlockState state = mc.world.getBlockState(pos);
            if (hasSuitableMiningTool(mc, state, true)) usableTargets.add(pos);
        }
        if (usableTargets.isEmpty()) return false;

        gatherTargets = List.copyOf(usableTargets);
        gatherIndex = 0;
        statusNotice(mc, "Suche weiter nach Ancient Debris rund um deine aktuelle Position (Nachsuche "
                + gatherRescanCount + "/" + MAX_ANCIENT_DEBRIS_RESCANS + ").");
        return true;
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
        gatherTargets = List.of();
        gatherIndex = 0;
        gatherVisitedTargets = new HashSet<>();
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
        if (mc.player == null || mc.world == null || mc.player.isDead()
                || (mode != Mode.SMELT && mc.currentScreen != null && !(mc.currentScreen instanceof AIChatScreen))) {
            stop("Aktion aus Sicherheitsgründen gestoppt.");
            return;
        }

        switch (mode) {
            case WALK -> tickWalk(mc);
            case NAVIGATE -> tickNavigation(mc);
            case MINE -> tickMining(mc);
            case EAT -> tickEating(mc);
            case CRAFT -> tickCrafting(mc);
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

    private static void tickNavigation(MinecraftClient mc) {
        if (++actionTicks > MAX_NAVIGATION_TICKS || mc.player.isSubmergedInWater() || mc.player.hasVehicle()) {
            stop("Navigation gestoppt: Zeitlimit, Wasser oder Fahrzeug erkannt.");
            return;
        }
        if (pathIndex >= path.size()) {
            if (navigationGoal == null || isAtGoal(mc, navigationGoal)) {
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
        mode = gatherTunnelOreTarget != null && gatherTunnelCellIndex < gatherTunnelCells.size()
                ? Mode.GATHER_TUNNEL : Mode.GATHER;
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
     * Returns a preview for one normal recipe-book crafting operation using only the
     * player's own 2x2 inventory crafting grid. The recipe and ingredient check are
     * supplied by the connected server's recipe book.
     */
    public static String previewCraftItem(String requestedItem, int additionalCount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        if (additionalCount < 1 || additionalCount > 256)
            throw new IllegalArgumentException("Die zusätzliche Craftingmenge muss zwischen 1 und 256 liegen.");
        String requested = validateCraftItemName(requestedItem);
        PlayerScreenHandler handler = requireEmptyPlayerCraftingGrid(mc);
        CraftingChoice choice = findCraftingChoice(mc, requested);
        int current = countInventoryItem(mc, choice.result().getItem());
        int batchSize = Math.max(1, choice.result().getCount());
        int estimatedBatches = (additionalCount + batchSize - 1) / batchSize;
        return "Nightfall stellt nach deiner Bestätigung mindestens " + additionalCount + " zusätzliche "
                + choice.result().getName().getString() + " her (derzeit im Inventar: " + current
                + ", Ziel: mindestens " + (current + additionalCount) + "). "
                + "Voraussichtlich mindestens " + estimatedBatches + " Rezept-Ausführungen à "
                + batchSize + " Stück; die letzte Ausgabe kann die Zielmenge um höchstens eine Rezeptcharge überschreiten. "
                + "Rezept: " + choice.recipe().id()
                + ". Es wird ausschließlich ein freigeschaltetes Vanilla-Rezept aus deinem Rezeptbuch verwendet; "
                + "Zutaten müssen im Inventar liegen. Verwendet wird das normale 2×2-Craftingfeld (Handler "
                + handler.syncId + ").";
    }

    /** Crafts one controlled recipe output per client tick until the requested additional count is reached. */
    public static String craftItem(String requestedItem, int additionalCount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        previewCraftItem(requestedItem, additionalCount);
        String requested = validateCraftItemName(requestedItem);
        PlayerScreenHandler handler = requireEmptyPlayerCraftingGrid(mc);
        CraftingChoice choice = findCraftingChoice(mc, requested);
        Item target = choice.result().getItem();
        int before = countInventoryItem(mc, target);

        stop(null);
        craftingTarget = target;
        craftingRecipe = choice.recipe();
        craftingName = choice.result().getName().getString();
        craftingCountBefore = before;
        craftingGoalCount = before + additionalCount;
        craftingTicks = 0;
        craftingIdleTicks = 0;
        craftingBatchesTaken = 0;
        craftingNeedsRefill = false;
        try {
            mc.interactionManager.clickRecipe(handler.syncId, choice.recipe().id(), false);
            mode = Mode.CRAFT;
        } catch (RuntimeException failure) {
            resetCraftingState();
            throw new IllegalArgumentException("Minecraft konnte das Rezept nicht in das 2×2-Craftingfeld legen. Prüfe Rezeptbuch und Inventar.");
        }
        notifyPlayer(mc, "Crafting gestartet: " + craftingName + " · +" + additionalCount
                + " (mindestens). Warte auf Serverbestätigungen.");
        return "Crafting gestartet. Nightfall nimmt jede Rezept-Ausgabe einzeln auf, füllt das Rezept bei Bedarf erneut und stoppt bei Zielmenge, fehlenden Zutaten oder vollem Inventar.";
    }

    private static String validateCraftItemName(String requestedItem) {
        if (requestedItem == null || requestedItem.isBlank() || requestedItem.length() > 64)
            throw new IllegalArgumentException("Gib eine gültige Item-ID oder einen Itemnamen an (maximal 64 Zeichen).");
        return requestedItem.trim();
    }

    private static PlayerScreenHandler requireEmptyPlayerCraftingGrid(MinecraftClient mc) {
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler)
            throw new IllegalArgumentException("Schließe zuerst den geöffneten Container. Nightfall craftet vorerst nur im normalen Spielerinventar.");
        PlayerScreenHandler handler = mc.player.playerScreenHandler;
        if (!handler.getCursorStack().isEmpty())
            throw new IllegalArgumentException("Dein Maus-Cursor hält noch einen Gegenstand. Lege ihn zuerst in dein Inventar.");
        if (!handler.getOutputSlot().getStack().isEmpty())
            throw new IllegalArgumentException("Im Crafting-Ausgabefeld liegt bereits ein Ergebnis. Räume es zuerst weg.");
        for (Slot slot : handler.getInputSlots()) {
            if (!slot.getStack().isEmpty())
                throw new IllegalArgumentException("Das 2×2-Craftingfeld ist nicht leer. Räume vorhandene Zutaten zuerst weg.");
        }
        return handler;
    }

    private static CraftingChoice findCraftingChoice(MinecraftClient mc, String requestedItem) {
        RecipeFinder finder = new RecipeFinder();
        mc.player.getInventory().populateRecipeFinder(finder);
        boolean matchingRecipeFound = false;
        boolean matchingRecipeNeedsTable = false;

        for (RecipeResultCollection collection : mc.player.getRecipeBook().getOrderedResults()) {
            for (RecipeDisplayEntry entry : collection.getAllRecipes()) {
                List<ItemStack> outputs;
                try {
                    outputs = entry.getStacks(EMPTY_RECIPE_CONTEXT);
                } catch (RuntimeException invalidDisplayContext) {
                    continue;
                }
                ItemStack matchingOutput = ItemStack.EMPTY;
                for (ItemStack output : outputs) {
                    if (output != null && !output.isEmpty() && matchesCraftItem(output, requestedItem)) {
                        matchingOutput = output;
                        break;
                    }
                }
                if (matchingOutput.isEmpty()) continue;
                matchingRecipeFound = true;

                if (!isInventoryCraftingRecipe(entry.display())) {
                    matchingRecipeNeedsTable = true;
                    continue;
                }
                if (!entry.isCraftable(finder)) continue;
                return new CraftingChoice(entry, matchingOutput.copy());
            }
        }

        if (matchingRecipeNeedsTable)
            throw new IllegalArgumentException("Das Rezept für " + requestedItem
                    + " benötigt einen anderen oder größeren Craftingplatz. Nightfall unterstützt hier zunächst nur 2×2-Rezepte.");
        if (matchingRecipeFound)
            throw new IllegalArgumentException("Das Rezept für " + requestedItem
                    + " ist freigeschaltet, aber die benötigten Zutaten fehlen in deinem Inventar.");
        throw new IllegalArgumentException("Ich finde für " + requestedItem
                + " kein passendes freigeschaltetes 2×2-Rezept in deinem Rezeptbuch.");
    }

    private static boolean isInventoryCraftingRecipe(RecipeDisplay display) {
        if (display instanceof ShapedCraftingRecipeDisplay shaped)
            return shaped.width() >= 1 && shaped.height() >= 1
                    && shaped.width() <= 2 && shaped.height() <= 2;
        if (display instanceof ShapelessCraftingRecipeDisplay shapeless)
            return !shapeless.ingredients().isEmpty() && shapeless.ingredients().size() <= 4;
        return false;
    }

    private static boolean matchesCraftItem(ItemStack stack, String requestedItem) {
        String requested = requestedItem.trim();
        var id = Registries.ITEM.getId(stack.getItem());
        return id.toString().equalsIgnoreCase(requested)
                || id.getPath().equalsIgnoreCase(requested)
                || stack.getName().getString().equalsIgnoreCase(requested);
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
            stop("Crafting gestoppt: Zeitlimit erreicht. Erreicht: "
                    + Math.max(0, countInventoryItem(mc, craftingTarget) - craftingCountBefore)
                    + " von mindestens " + (craftingGoalCount - craftingCountBefore) + ".");
            return;
        }
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            stop("Crafting gestoppt: Das Spielerinventar wurde durch einen anderen Container ersetzt.");
            return;
        }

        int currentCount = countInventoryItem(mc, craftingTarget);
        if (currentCount >= craftingGoalCount) {
            stop("Crafting abgeschlossen: " + (currentCount - craftingCountBefore) + " zusätzliche "
                    + craftingName + " im Inventar.");
            return;
        }

        PlayerScreenHandler handler = mc.player.playerScreenHandler;
        if (!handler.getCursorStack().isEmpty()) {
            stop("Crafting gestoppt: Der Maus-Cursor ist nicht leer. Lege den Gegenstand zuerst ins Inventar.");
            return;
        }

        if (craftingNeedsRefill) {
            try {
                mc.interactionManager.clickRecipe(handler.syncId, craftingRecipe.id(), false);
                craftingNeedsRefill = false;
                craftingIdleTicks = 0;
            } catch (RuntimeException failure) {
                stop("Crafting beendet: " + (currentCount - craftingCountBefore) + " zusätzliche "
                        + craftingName + " hergestellt; das Rezept ließ sich nicht erneut füllen.");
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
                        + (currentCount - craftingCountBefore) + " zusätzliche " + craftingName + ".");
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
                craftingNeedsRefill = true;
                craftingIdleTicks = 0;
            } catch (RuntimeException failure) {
                stop("Crafting gestoppt: Minecraft hat die Ausgabe nicht sicher ins Inventar übertragen.");
            }
            return;
        }

        if (++craftingIdleTicks > (craftingBatchesTaken == 0 ? MAX_CRAFTING_IDLE_TICKS * 4 : MAX_CRAFTING_IDLE_TICKS)) {
            stop("Crafting beendet: " + (currentCount - craftingCountBefore) + " zusätzliche "
                    + craftingName + " hergestellt. Weitere Zutaten fehlen oder das Rezeptbuch konnte es nicht erneut füllen.");
        }
    }

    private static void cleanupCraftingGrid(MinecraftClient mc) {
        if (mc.player == null || mc.interactionManager == null
                || mc.player.currentScreenHandler != mc.player.playerScreenHandler) return;
        PlayerScreenHandler handler = mc.player.playerScreenHandler;
        for (Slot slot : handler.getInputSlots()) {
            if (slot.getStack().isEmpty()) continue;
            try {
                mc.interactionManager.clickSlot(handler.syncId, slot.id, 0,
                        SlotActionType.QUICK_MOVE, mc.player);
            } catch (RuntimeException ignored) {
                // Leave server-owned crafting contents untouched if a normal return click fails.
            }
        }
    }

    private static void resetCraftingState() {
        craftingTarget = null;
        craftingRecipe = null;
        craftingName = "";
        craftingCountBefore = 0;
        craftingGoalCount = 0;
        craftingTicks = 0;
        craftingIdleTicks = 0;
        craftingBatchesTaken = 0;
        craftingNeedsRefill = false;
    }

    private record CraftingChoice(RecipeDisplayEntry recipe, ItemStack result) {}

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
        miningPos = null;
        miningState = null;
        foodSlot = -1;
        foodName = "";
        resetCraftingState();
        resetGatherState();
        resetSmeltingState();
        if (previous != Mode.NONE && mc.player != null && reason != null)
            notifyPlayer(mc, reason);
        return previous == Mode.NONE ? "Nightfall führt gerade keine Bewegung oder Interaktion aus." : "Nightfall-Aktion gestoppt.";
    }

    public static String stopMovement(String reason) {
        return stop(reason);
    }

    public static void onDisconnect() { stop(null); }
    public static boolean isWalking() { return mode == Mode.WALK; }
    public static boolean isNavigating() { return mode == Mode.NAVIGATE; }
    public static boolean isBusy() { return mode != Mode.NONE; }
    public static String status() {
        return switch (mode) {
            case NONE -> "Keine KI-Aktion aktiv";
            case WALK -> "Geradeausbewegung";
            case NAVIGATE -> "Navigation · " + Math.min(pathIndex + 1, path.size()) + "/" + path.size();
            case MINE -> "Blockabbau · " + (miningPos == null ? "" : miningPos.toShortString());
            case EAT -> "Nahrungsaufnahme · " + foodName;
            case CRAFT -> "Crafting · " + craftingName;
            case GATHER -> "Sammle " + gatherMaterialName + " · " + gatherIndex + "/" + gatherTargets.size();
            case GATHER_MINE -> "Baue Rohstoff ab · " + (miningPos == null ? "" : miningPos.toShortString());
            case GATHER_TUNNEL -> "Grabe Ancient-Debris-Stollen · " + gatherTunnelCellIndex + "/" + gatherTunnelCells.size();
            case GATHER_TUNNEL_MINE -> "Grabe Tunnelblock · " + (miningPos == null ? "" : miningPos.toShortString());
            case SMELT -> "Schmelze " + (smeltInput == null ? "" : smeltInput.getName().getString());
        };
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
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontal));
        player.setYaw(yaw);
        player.setPitch(pitch);
    }

    private static BlockHitResult raycastBlock(MinecraftClient mc, BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d end = pos.toCenterPos();
        return mc.world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE, mc.player)) instanceof BlockHitResult hit
                && hit.getBlockPos().equals(pos) ? hit : null;
    }

    private static int findBestMiningToolSlot(MinecraftClient mc, ClientPlayerEntity player,
                                              BlockState targetState) {
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
}
