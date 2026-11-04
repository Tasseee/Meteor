package dev.nightfall.ai;

import dev.nightfall.gui.AIChatScreen;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
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
    private enum Mode { NONE, WALK, NAVIGATE, MINE, EAT }

    private static final int MAX_PATH_RADIUS = 48;
    private static final int NAVIGATION_SEGMENT_LENGTH = 32;
    private static final int MAX_SEARCH_NODES = 8_000;
    private static final int MAX_NAVIGATION_TICKS = 20 * 60 * 5;
    private static final int MAX_MINING_TICKS = 20 * 30;
    private static final int MAX_EATING_TICKS = 80;
    private static final double MIN_PROGRESS = 0.025;

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
    private static int originalSlot = -1;
    private static int foodSlot = -1;
    private static int foodCountBefore;
    private static int hungerBefore;
    private static String foodName = "";

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
                + " bei X=" + x + ", Y=" + y + ", Z=" + z + " mit dem aktuell gehaltenen Werkzeug ab. "
                + "Der Abbau wird abgebrochen, falls der Server keine Blockänderung bestätigt.";
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
        aimAt(mc.player, pos.toCenterPos());
        BlockHitResult hit = raycastBlock(mc, pos);
        if (hit == null) throw new IllegalArgumentException(
                "Der Block ist nicht direkt sichtbar. Entferne das Hindernis oder stelle dich so hin, dass Nightfall ihn erreichen kann.");
        if (!mc.interactionManager.attackBlock(pos, hit.getSide()))
            throw new IllegalArgumentException("Der Minecraft-Client konnte den Abbau nicht starten.");
        miningPos = pos.toImmutable();
        miningState = mc.world.getBlockState(pos);
        miningSide = hit.getSide();
        actionTicks = 0;
        mode = Mode.MINE;
        mc.options.attackKey.setPressed(true);
        notifyPlayer(mc, "Baue " + miningState.getBlock().getName().getString() + " ab. „Stopp“ bricht ab.");
        return "Abbau gestartet. Nightfall wartet auf die bestätigte Blockänderung der Welt.";
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
        if (previous == Mode.MINE && mc.interactionManager != null) {
            try { mc.interactionManager.cancelBlockBreaking(); } catch (RuntimeException ignored) {}
        }
        if (previous == Mode.EAT && mc.player != null) {
            try { mc.player.stopUsingItem(); } catch (RuntimeException ignored) {}
            restoreHotbar(mc);
        }
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
