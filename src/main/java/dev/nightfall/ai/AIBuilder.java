package dev.nightfall.ai;

import net.minecraft.block.BlockState;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * First deterministic Nightfall builder skill. It uses normal block interaction packets,
 * never writes directly to the client world, and lets the server accept or reject each placement.
 */
public final class AIBuilder {
    private static final int WIDTH = 4;
    private static final int REQUIRED_BLOCKS = 54;
    private static final double REACH_SQUARED = 20.25;

    private static final List<BlockPos> PLAN = new ArrayList<>();
    private static BlockItem material;
    private static Identifier dimension;
    private static int index;
    private static int delay;
    private static int originalSlot = -1;
    private static boolean prepared;
    private static boolean running;
    private static boolean waitingForReach;
    private static String materialName = "";
    private static String status = "Builder bereit";

    private AIBuilder() {}

    /** Builds and validates a local plan without changing the world. Called before confirmation. */
    public static String prepareStarterShelter() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (running) throw new IllegalArgumentException("Nightfall baut bereits. Stoppe den aktuellen Bau zuerst.");
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.getNetworkHandler() == null)
            throw new IllegalArgumentException("Du musst dich zuerst in einer verbundenen Welt befinden.");

        Map<BlockItem, Integer> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (!(stack.getItem() instanceof BlockItem blockItem)) continue;
            BlockState state = blockItem.getBlock().getDefaultState();
            BlockPos sample = mc.player.getBlockPos();
            if (blockItem.getBlock() instanceof FallingBlock
                    || state.getCollisionShape(mc.world, sample).isEmpty()
                    || !state.isFullCube(mc.world, sample)) continue;
            counts.merge(blockItem, stack.getCount(), Integer::sum);
        }

        BlockItem selected = null;
        int available = 0;
        for (Map.Entry<BlockItem, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > available) {
                selected = entry.getKey();
                available = entry.getValue();
            }
        }
        if (selected == null || available < REQUIRED_BLOCKS)
            throw new IllegalArgumentException("Für den ersten Bau brauche ich mindestens 54 volle Blöcke derselben Sorte in deiner Hotbar. Geeignet sind zum Beispiel Bruchstein oder Bretter. Aktuell habe ich keinen passenden Hotbar-Stapel mit genug Material gefunden.");

        ClientPlayerEntity player = mc.player;
        Direction forward = player.getHorizontalFacing();
        Direction right = forward.rotateYClockwise();
        BlockPos origin = player.getBlockPos().offset(forward, 1).offset(right, -1);
        int baseY = player.getBlockPos().getY();

        List<BlockPos> nextPlan = new ArrayList<>(REQUIRED_BLOCKS);
        int[] xOrder = {1, 2, 0, 3};
        for (int z = 0; z < WIDTH; z++) {
            for (int x : xOrder) nextPlan.add(at(origin, right, forward, x, z, baseY));
        }

        // Two wall courses; leave a two-block-high doorway in the middle of the front wall.
        for (int layer = 1; layer <= 2; layer++) {
            for (int z = 0; z < WIDTH; z++) {
                for (int x = 0; x < WIDTH; x++) {
                    boolean edge = x == 0 || x == WIDTH - 1 || z == 0 || z == WIDTH - 1;
                    if (!edge || (z == WIDTH - 1 && x == 1)) continue;
                    nextPlan.add(at(origin, right, forward, x, z, baseY + layer));
                }
            }
        }

        // Lay the perimeter roof first; interior roof blocks can then attach to it.
        List<int[]> roof = new ArrayList<>();
        for (int z = 0; z < WIDTH; z++) {
            for (int x = 0; x < WIDTH; x++) roof.add(new int[]{x, z});
        }
        roof.sort(Comparator.comparingInt((int[] p) ->
                Math.abs(p[0] - 1) + Math.abs(p[1] - 1)).reversed());
        for (int[] cell : roof)
            nextPlan.add(at(origin, right, forward, cell[0], cell[1], baseY + 3));

        if (nextPlan.size() != REQUIRED_BLOCKS)
            throw new IllegalStateException("Der interne Bauplan ist ungültig.");

        // Require a flat full-cube foundation and an entirely replaceable building volume.
        for (int z = 0; z < WIDTH; z++) {
            for (int x = 0; x < WIDTH; x++) {
                BlockPos floor = at(origin, right, forward, x, z, baseY);
                BlockPos support = floor.down();
                BlockState ground = mc.world.getBlockState(support);
                if (ground.getCollisionShape(mc.world, support).isEmpty()
                        || !ground.isFullCube(mc.world, support))
                    throw new IllegalArgumentException("Der Bauplatz ist nicht eben oder hat keinen durchgehend festen Boden. Stelle dich an eine freie, flache Stelle.");
            }
        }
        for (BlockPos target : nextPlan) {
            if (!mc.world.getBlockState(target).isReplaceable())
                throw new IllegalArgumentException("Der Bauplatz ist nicht frei: Bei " + target.getX() + ", "
                        + target.getY() + ", " + target.getZ() + " steht bereits ein Block oder eine Entity im Weg.");
            if (target.getY() < mc.world.getBottomY() || target.getY() >= mc.world.getBottomY() + mc.world.getHeight())
                throw new IllegalArgumentException("Der Bauplatz liegt außerhalb der Weltgrenzen.");
        }

        PLAN.clear();
        PLAN.addAll(nextPlan);
        material = selected;
        materialName = selected.getName().getString();
        dimension = mc.world.getRegistryKey().getValue();
        index = 0;
        delay = 0;
        prepared = true;
        status = "Bauplan geprüft";
        return "Bauplan geprüft: kleines 4x4-Schutzhaus mit 2 hohen Wänden und flachem Dach. Material: "
                + materialName + " × " + REQUIRED_BLOCKS + " (Hotbar: " + available + "). "
                + "Eingang: 1 Block breit und 2 Blöcke hoch; Tür, Fenster und Dekoration sind noch nicht enthalten. "
                + "Der Bauplatz ist flach und frei. Der Bau setzt echte Blöcke und kann pausieren, wenn du näher herangehen musst.";
    }

    private static BlockPos at(BlockPos origin, Direction right, Direction forward,
                               int x, int z, int yOffset) {
        return origin.add(right.getOffsetX() * x + forward.getOffsetX() * z,
                yOffset - origin.getY(), right.getOffsetZ() * x + forward.getOffsetZ() * z);
    }

    /** Revalidates the plan immediately before it begins. */
    public static String startStarterShelter() {
        if (!prepared) throw new IllegalArgumentException("Der Bauplan ist abgelaufen. Bitte fordere den Bau erneut an.");
        prepareStarterShelter();
        if (!prepared) return "Bau konnte nicht vorbereitet werden.";
        MinecraftClient mc = MinecraftClient.getInstance();
        originalSlot = mc.player.getInventory().getSelectedSlot();
        index = 0;
        delay = 0;
        waitingForReach = false;
        running = true;
        status = "Bau gestartet";
        notifyPlayer("Bau gestartet: 0/" + PLAN.size() + " Blöcke. Ich setze das Haus Schritt für Schritt.");
        return "Bau gestartet. " + materialName + " wird für " + PLAN.size()
                + " Blockplatzierungen verwendet. Ich pausiere automatisch, wenn du näher herangehen musst.";
    }

    /** One placement attempt at a time; called from the client tick. */
    public static void tick() {
        if (!running) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.interactionManager == null
                || mc.getNetworkHandler() == null || !mc.world.getRegistryKey().getValue().equals(dimension)) {
            stopInternal("Bau gestoppt: Welt oder Verbindung hat sich geändert.", true);
            return;
        }
        ClientPlayerEntity player = mc.player;
        if (delay > 0) {
            delay--;
            return;
        }
        if (index >= PLAN.size()) {
            stopInternal("Bau abgeschlossen: Das 4x4-Schutzhaus wurde platziert.", true);
            return;
        }

        BlockPos target = PLAN.get(index);
        BlockState present = mc.world.getBlockState(target);
        if (present.isOf(material.getBlock())) {
            index++;
            progressNotice();
            return;
        }
        if (!present.isReplaceable()) {
            stopInternal("Bau gestoppt: Bei " + target.getX() + ", " + target.getY() + ", "
                    + target.getZ() + " ist inzwischen ein anderer Block im Weg.", true);
            return;
        }

        int slot = findMaterialSlot(player);
        if (slot < 0) {
            stopInternal("Bau gestoppt: Die benötigten Blöcke sind nicht mehr in der Hotbar.", true);
            return;
        }
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
                notifyPlayer("Ich komme an den nächsten Block nicht heran. Geh bitte näher an den Bauplatz; der Bau wird dann automatisch fortgesetzt.");
            }
            return;
        }

        waitingForReach = false;
        status = "Baue " + (index + 1) + "/" + PLAN.size();
        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
        player.swingHand(Hand.MAIN_HAND);
        index++;
        delay = 3;
        progressNotice();
    }

    private static BlockHitResult findReachableSupport(MinecraftClient mc, ClientPlayerEntity player,
                                                        BlockPos target) {
        for (Direction face : Direction.values()) {
            BlockPos support = target.offset(face.getOpposite());
            if (mc.world.getBlockState(support).getCollisionShape(mc.world, support).isEmpty()) continue;
            Vec3d hit = support.toCenterPos().add(face.getOffsetX() * 0.5,
                    face.getOffsetY() * 0.5, face.getOffsetZ() * 0.5);
            if (player.getEyePos().squaredDistanceTo(hit) > REACH_SQUARED) continue;
            return new BlockHitResult(hit, face, support, false);
        }
        return null;
    }

    private static int findMaterialSlot(ClientPlayerEntity player) {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (!stack.isEmpty() && stack.isOf(material)) return slot;
        }
        return -1;
    }

    private static void progressNotice() {
        if (index == 1 || index % 10 == 0) {
            status = "Baue " + index + "/" + PLAN.size();
            notifyPlayer("Baufortschritt: " + index + "/" + PLAN.size() + " Blöcke.");
        }
    }

    private static void notifyPlayer(String message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Nightfall] " + message), false);
    }

    public static boolean isRunning() {
        return running;
    }

    public static String statusLabel() {
        return running ? "Builder · " + status : status;
    }

    /** Cancel only the unstarted plan when the user declines the confirmation. */
    public static void cancelPrepared() {
        if (running) return;
        prepared = false;
        PLAN.clear();
        material = null;
    }

    /** Stop the active queue. Blocks already accepted by the server are intentionally left in place. */
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
        if (originalSlot >= 0 && mc.player != null)
            mc.player.getInventory().setSelectedSlot(originalSlot);
        originalSlot = -1;
        running = false;
        waitingForReach = false;
        prepared = false;
        status = message;
        if (notify) notifyPlayer(message);
        PLAN.clear();
        material = null;
    }
}
