package dev.nightfall.ai;

import dev.nightfall.gui.AIChatScreen;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeDisplayEntry;
import net.minecraft.recipe.RecipeFinder;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.ShapedCraftingRecipeDisplay;
import net.minecraft.recipe.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
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
    private enum Mode { NONE, WALK, NAVIGATE, MINE, EAT, CRAFT, GATHER, GATHER_MINE }

    private static final int MAX_PATH_RADIUS = 48;
    private static final int NAVIGATION_SEGMENT_LENGTH = 32;
    private static final int MAX_SEARCH_NODES = 8_000;
    private static final int MAX_NAVIGATION_TICKS = 20 * 60 * 5;
    private static final int MAX_MINING_TICKS = 20 * 30;
    private static final int MAX_EATING_TICKS = 80;
    private static final double MIN_PROGRESS = 0.025;
    private static final int MAX_CRAFTING_TICKS = 60;
    private static final int MAX_GATHER_BLOCKS = 32;
    private static final int MAX_GATHER_TARGETS = 96;
    private static final int GATHER_HORIZONTAL_RADIUS = 16;
    private static final int GATHER_VERTICAL_BELOW = 4;
    private static final int GATHER_VERTICAL_ABOVE = 16;
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
    private static int craftingTicks;
    private static boolean craftingOutputTaken;
    private static BlockItem gatherMaterial;
    private static String gatherMaterialName = "";
    private static int gatherGoalCount;
    private static List<BlockPos> gatherTargets = List.of();
    private static int gatherIndex;
    private static boolean gatherAwaitingPickup;
    private static int gatherPickupTicks;
    private static int gatherCountBeforeBreak;

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
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        if (targetCount < 1 || targetCount > 64)
            throw new IllegalArgumentException("Die Zielmenge muss zwischen 1 und 64 liegen.");
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler)
            throw new IllegalArgumentException("Schließe zuerst den geöffneten Container.");
        BlockItem item = resolveGatherWoodItem(requestedItem);
        int current = countInventoryItem(mc, item);
        int missing = targetCount - current;
        if (missing <= 0)
            throw new IllegalArgumentException("Du hast bereits " + current + " × " + item.getName().getString()
                    + " im Inventar. Die gewünschte Zielmenge ist erreicht.");
        if (missing > MAX_GATHER_BLOCKS)
            throw new IllegalArgumentException("Ein einzelner Sammelauftrag darf höchstens "
                    + MAX_GATHER_BLOCKS + " zusätzliche Holzblöcke abbauen.");
        List<BlockPos> candidates = findGatherableWoodBlocks(mc, item.getBlock());
        if (candidates.isEmpty())
            throw new IllegalArgumentException("Im geladenen Umkreis von " + GATHER_HORIZONTAL_RADIUS
                    + " Blöcken habe ich keine passenden " + item.getName().getString()
                    + " gefunden. Erkunde zuerst die Gegend oder gehe näher an einen Baum.");
        int available = Math.min(missing, candidates.size());
        return "Nightfall sammelt nach deiner Bestätigung bis zu " + missing + " zusätzliche "
                + item.getName().getString() + " (Ziel: " + targetCount + " im Inventar). "
                + "Gefunden: " + candidates.size() + " passende Stämme/Holzblöcke im bereits geladenen "
                + "Umkreis. Höchstens " + MAX_GATHER_BLOCKS + " Blöcke pro Auftrag. "
                + "Nightfall sucht sichere Standpunkte, nutzt das passende Werkzeug und wartet auf "
                + "Block- und Item-Updates. Es werden höchstens " + available + " Ziele eingeplant.";
    }

    /** Start a confirmed, bounded wood-gathering task in loaded nearby terrain. */
    public static String gatherWood(String requestedItem, int targetCount) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        previewGatherWood(requestedItem, targetCount);
        BlockItem item = resolveGatherWoodItem(requestedItem);
        List<BlockPos> candidates = findGatherableWoodBlocks(mc, item.getBlock());
        if (candidates.isEmpty()) throw new IllegalArgumentException("Die gefundenen Holzblöcke sind inzwischen nicht mehr verfügbar.");

        stop(null);
        gatherMaterial = item;
        gatherMaterialName = item.getName().getString();
        gatherGoalCount = targetCount;
        gatherTargets = List.copyOf(candidates);
        gatherIndex = 0;
        gatherAwaitingPickup = false;
        gatherPickupTicks = 0;
        gatherCountBeforeBreak = 0;
        mode = Mode.GATHER;
        statusNotice(mc, "Sammle " + gatherMaterialName + " · Ziel " + targetCount
                + " im Inventar. Ich baue nur passende Holzblöcke im geladenen Nahbereich ab.");
        return "Holzsammeln gestartet. Nightfall sucht passende Stämme, navigiert über sichere geladene Wege und wartet auf bestätigte Drops. „Stopp“ bricht ab.";
    }

    private static BlockItem resolveGatherWoodItem(String requestedItem) {
        if (requestedItem == null || requestedItem.isBlank() || requestedItem.length() > 64)
            throw new IllegalArgumentException("Gib eine Minecraft-Item-ID wie minecraft:oak_log an.");
        Identifier id = Identifier.tryParse(requestedItem.trim());
        if (id == null || !id.getNamespace().equals("minecraft"))
            throw new IllegalArgumentException("Bitte gib eine gültige Vanilla-Holz-ID wie minecraft:oak_log an.");
        Item item = Registries.ITEM.get(id);
        if (!(item instanceof BlockItem blockItem) || !Registries.ITEM.getId(item).equals(id)
                || !Registries.BLOCK.getId(blockItem.getBlock()).equals(id)
                || !isGatherableWoodPath(id.getPath()))
            throw new IllegalArgumentException("Nightfall sammelt derzeit nur Vanilla-Holzblöcke, die als derselbe Itemtyp droppen "
                    + "(z. B. minecraft:oak_log, minecraft:birch_log, minecraft:crimson_stem oder minecraft:oak_wood).");
        return blockItem;
    }

    private static boolean isGatherableWoodPath(String path) {
        return path.endsWith("_log") || path.endsWith("_wood")
                || path.endsWith("_stem") || path.endsWith("_hyphae");
    }

    private static List<BlockPos> findGatherableWoodBlocks(MinecraftClient mc, Block targetBlock) {
        BlockPos center = mc.player.getBlockPos();
        int minX = center.getX() - GATHER_HORIZONTAL_RADIUS;
        int maxX = center.getX() + GATHER_HORIZONTAL_RADIUS;
        int minZ = center.getZ() - GATHER_HORIZONTAL_RADIUS;
        int maxZ = center.getZ() + GATHER_HORIZONTAL_RADIUS;
        int minY = Math.max(mc.world.getBottomY(), center.getY() - GATHER_VERTICAL_BELOW);
        int maxY = Math.min(mc.world.getBottomY() + mc.world.getHeight() - 1,
                center.getY() + GATHER_VERTICAL_ABOVE);
        List<BlockPos> found = new ArrayList<>();
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    if (!mc.world.isChunkLoaded(cursor)) continue;
                    BlockState state = mc.world.getBlockState(cursor);
                    if (!state.isOf(targetBlock) || state.hasBlockEntity()
                            || !state.getFluidState().isEmpty()
                            || state.getHardness(mc.world, cursor) < 0.0f) continue;
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

    private static void tickGather(MinecraftClient mc) {
        if (gatherMaterial == null || gatherTargets.isEmpty()) {
            stop("Holzsammeln gestoppt: Es gibt keinen gültigen Sammelplan mehr.");
            return;
        }
        int currentCount = countInventoryItem(mc, gatherMaterial);
        if (currentCount >= gatherGoalCount) {
            stop("Holzsammeln abgeschlossen: " + currentCount + "/" + gatherGoalCount
                    + " " + gatherMaterialName + " im Inventar.");
            return;
        }
        if (gatherAwaitingPickup) {
            if (currentCount > gatherCountBeforeBreak) {
                gatherAwaitingPickup = false;
                gatherPickupTicks = 0;
                gatherIndex++;
                return;
            }
            if (++gatherPickupTicks > 40) {
                gatherAwaitingPickup = false;
                gatherPickupTicks = 0;
                gatherIndex++;
                statusNotice(mc, "Der letzte Holzblock wurde abgebaut, aber sein Drop kam nicht im Inventar an. Ich überspringe ihn.");
            } else {
                return;
            }
        }

        while (gatherIndex < gatherTargets.size()) {
            BlockPos target = gatherTargets.get(gatherIndex);
            BlockState state = mc.world.getBlockState(target);
            if (!state.isOf(gatherMaterial.getBlock()) || state.hasBlockEntity()
                    || !state.getFluidState().isEmpty() || state.getHardness(mc.world, target) < 0.0f) {
                gatherIndex++;
                continue;
            }

            aimAt(mc.player, target.toCenterPos());
            double reach = mc.player.getBlockInteractionRange();
            boolean inReach = mc.player.getEyePos().squaredDistanceTo(target.toCenterPos()) <= reach * reach;
            BlockHitResult hit = inReach ? raycastBlock(mc, target) : null;
            if (hit == null) {
                if (startGatherApproach(mc, target)) return;
                gatherIndex++;
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
                    gatherIndex++;
                    continue;
                }
            } catch (RuntimeException failure) {
                restoreMiningTool(mc);
                restoreHotbar(mc);
                stop("Holzsammeln gestoppt: Passendes Werkzeug konnte nicht sicher gewechselt werden.");
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

        stop("Holzsammeln beendet: " + currentCount + "/" + gatherGoalCount + " "
                + gatherMaterialName + " im Inventar. Im geladenen Nahbereich sind keine weiteren passenden Stämme erreichbar.");
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
            stop("Holzsammeln gestoppt: Abbaureichweite oder Zeitlimit überschritten.");
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
            stop("Holzsammeln gestoppt: Der Stamm ist nicht mehr frei sichtbar.");
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
        gatherMaterialName = "";
        gatherGoalCount = 0;
        gatherTargets = List.of();
        gatherIndex = 0;
        gatherAwaitingPickup = false;
        gatherPickupTicks = 0;
        gatherCountBeforeBreak = 0;
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
                || (mc.currentScreen != null && !(mc.currentScreen instanceof AIChatScreen))) {
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
     * Returns a preview for one normal recipe-book crafting operation using only the
     * player's own 2x2 inventory crafting grid. The recipe and ingredient check are
     * supplied by the connected server's recipe book.
     */
    public static String previewCraftItem(String requestedItem) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        String requested = validateCraftItemName(requestedItem);
        PlayerScreenHandler handler = requireEmptyPlayerCraftingGrid(mc);
        CraftingChoice choice = findCraftingChoice(mc, requested);
        return "Nightfall stellt nach deiner Bestätigung einmal "
                + choice.result().getCount() + " × " + choice.result().getName().getString()
                + " her. Rezept: " + choice.recipe().id()
                + ". Es wird ausschließlich ein freigeschaltetes Vanilla-Rezept aus deinem Rezeptbuch verwendet, "
                + "und alle Zutaten müssen bereits in deinem Inventar liegen. "
                + "Verwendet wird das normale 2×2-Craftingfeld (Handler " + handler.syncId + ").";
    }

    /** Starts one confirmed recipe-book crafting operation and waits for server confirmation. */
    public static String craftItem(String requestedItem) {
        MinecraftClient mc = MinecraftClient.getInstance();
        requireWorld(mc);
        String requested = validateCraftItemName(requestedItem);
        PlayerScreenHandler handler = requireEmptyPlayerCraftingGrid(mc);
        CraftingChoice choice = findCraftingChoice(mc, requested);
        Item target = choice.result().getItem();
        int before = countInventoryItem(mc, target);

        stop(null);
        craftingTarget = target;
        craftingName = choice.result().getName().getString();
        craftingCountBefore = before;
        craftingTicks = 0;
        craftingOutputTaken = false;
        try {
            mc.interactionManager.clickRecipe(handler.syncId, choice.recipe().id(), false);
            mode = Mode.CRAFT;
        } catch (RuntimeException failure) {
            resetCraftingState();
            throw new IllegalArgumentException("Minecraft konnte das Rezept nicht in das 2×2-Craftingfeld legen. Prüfe Rezeptbuch und Inventar.");
        }
        notifyPlayer(mc, "Crafting gestartet: " + craftingName + ". Warte auf die Serverbestätigung.");
        return "Crafting gestartet. Nightfall verwendet ein freigeschaltetes Rezept und wartet auf das bestätigte Ergebnis im Inventar.";
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

    private static void tickCrafting(MinecraftClient mc) {
        if (++craftingTicks > MAX_CRAFTING_TICKS || mc.interactionManager == null) {
            stop("Crafting abgebrochen: Zeitlimit erreicht, ohne dass das Ergebnis bestätigt wurde.");
            return;
        }
        if (mc.player.currentScreenHandler != mc.player.playerScreenHandler) {
            stop("Crafting gestoppt: Das Spielerinventar wurde durch einen anderen Container ersetzt.");
            return;
        }

        if (countInventoryItem(mc, craftingTarget) > craftingCountBefore) {
            stop("Crafting bestätigt: " + craftingName + " liegt jetzt in deinem Inventar.");
            return;
        }

        PlayerScreenHandler handler = mc.player.playerScreenHandler;
        ItemStack output = handler.getOutputSlot().getStack();
        if (!craftingOutputTaken && !output.isEmpty()) {
            if (!output.isOf(craftingTarget)) {
                stop("Crafting gestoppt: Minecraft zeigte ein anderes Ergebnis als angefordert.");
                return;
            }
            mc.interactionManager.clickSlot(handler.syncId, handler.getOutputSlot().id,
                    0, SlotActionType.QUICK_MOVE, mc.player);
            craftingOutputTaken = true;
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
        craftingName = "";
        craftingCountBefore = 0;
        craftingTicks = 0;
        craftingOutputTaken = false;
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
        if ((previous == Mode.MINE || previous == Mode.GATHER_MINE) && mc.interactionManager != null) {
            try { mc.interactionManager.cancelBlockBreaking(); } catch (RuntimeException ignored) {}
        }
        if (previous == Mode.EAT && mc.player != null) {
            try { mc.player.stopUsingItem(); } catch (RuntimeException ignored) {}
            restoreHotbar(mc);
        }
        if ((previous == Mode.MINE || previous == Mode.GATHER_MINE) && mc.player != null) {
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
            case GATHER_MINE -> "Baue Holz ab · " + (miningPos == null ? "" : miningPos.toShortString());
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
