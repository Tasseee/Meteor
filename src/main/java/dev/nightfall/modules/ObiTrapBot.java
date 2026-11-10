package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.util.InvUtil;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the full obsidian enclosure from the supplied Obitrap source,
 * then places cobwebs in the target's feet and head blocks.
 */
public final class ObiTrapBot extends Module {
    private PlayerEntity target;
    private BlockPos anchor;
    private String phase = "obsidian";
    private int idleTicks;
    private int previousSlot = -1;

    public ObiTrapBot() {
        super("ObiTrapBot", "Baut eine Obsidian-Box um den anvisierten Spieler und platziert Cobwebs.", Category.COMBAT);
    }

    @Override
    protected void onEnable() {
        start();
    }

    @Override
    protected void onDisable() {
        resetState();
    }

    @Override
    public void onWorldLeave() {
        resetState();
    }

    private void resetState() {
        restoreSlot();
        target = null;
        anchor = null;
        idleTicks = 0;
        phase = "obsidian";
    }

    private void start() {
        if (mc.player == null || mc.world == null) return;
        if (!(mc.crosshairTarget instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof PlayerEntity player)
                || player == mc.player) {
            mc.player.sendMessage(Text.literal("[Obitrap] Ziele mit deinem Fadenkreuz auf einen anderen Spieler und aktiviere den Bot."), false);
            setEnabled(false);
            return;
        }

        target = player;
        anchor = target.getBlockPos().toImmutable();
        phase = "obsidian";
        idleTicks = 0;
        mc.player.sendMessage(Text.literal("[Obitrap] Baue die Box um " + target.getName().getString() + "."), false);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null || mc.interactionManager == null || anchor == null || target == null) {
            stop("Abgebrochen: Kein gültiges Ziel oder keine Welt.");
            setEnabled(false);
            return;
        }

        if (!target.isAlive() || target.isRemoved()) {
            stop("Ziel nicht mehr verfügbar.");
            setEnabled(false);
            return;
        }

        List<BlockPos> positions = phase.equals("obsidian") ? getObsidianPositions() : getCobwebPositions();
        Item requiredItem = phase.equals("obsidian") ? Items.OBSIDIAN : Items.COBWEB;
        BlockPos next = null;
        for (BlockPos pos : positions) {
            var state = mc.world.getBlockState(pos);
            if (state.isAir() || state.isReplaceable()) {
                next = pos;
                break;
            }
        }

        if (next == null) {
            if (phase.equals("obsidian")) {
                phase = "cobweb";
                idleTicks = 0;
                player.sendMessage(Text.literal("[Obitrap] Box fertig. Platziere Cobwebs."), false);
            } else {
                stop("Fertig! Box und Cobwebs wurden platziert.");
                setEnabled(false);
            }
            return;
        }

        int slot = InvUtil.ensureHotbar(requiredItem);
        if (slot < 0) {
            stop("Abgebrochen: " + (phase.equals("obsidian") ? "Obsidian" : "Cobwebs") + " fehlen im Inventar.");
            setEnabled(false);
            return;
        }
        if (previousSlot < 0) previousSlot = InvUtil.selected();
        InvUtil.select(slot);

        if (tryPlace(next)) {
            idleTicks = 0;
        } else if (++idleTicks > 100) {
            stop("Abgebrochen: Die nächsten Blöcke sind nicht erreichbar oder können nicht platziert werden.");
            setEnabled(false);
        }
    }

    private List<BlockPos> getObsidianPositions() {
        List<BlockPos> result = new ArrayList<>();
        // Floor and ceiling: each a 3x3 square.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                result.add(anchor.add(x, -1, z));
                result.add(anchor.add(x, 2, z));
            }
        }
        // Four side walls, two blocks high; corners are only added once.
        for (int y = 0; y <= 1; y++) {
            for (int x = -1; x <= 1; x++) {
                result.add(anchor.add(x, y, -1));
                result.add(anchor.add(x, y, 1));
            }
            result.add(anchor.add(-1, y, 0));
            result.add(anchor.add(1, y, 0));
        }
        return result;
    }

    private List<BlockPos> getCobwebPositions() {
        return List.of(anchor, anchor.up());
    }

    private boolean tryPlace(BlockPos placePos) {
        for (Direction face : Direction.values()) {
            BlockPos neighbor = placePos.offset(face);
            if (mc.world.getBlockState(neighbor).isAir()) continue;

            Direction clickedSide = face.getOpposite();
            Vec3d hitPosition = Vec3d.ofCenter(neighbor).add(
                    clickedSide.getOffsetX() * 0.5,
                    clickedSide.getOffsetY() * 0.5,
                    clickedSide.getOffsetZ() * 0.5
            );
            BlockHitResult hit = new BlockHitResult(hitPosition, clickedSide, neighbor, false);
            var result = mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            if (result.isAccepted()) {
                mc.player.swingHand(Hand.MAIN_HAND);
                return true;
            }
        }
        return false;
    }

    private void restoreSlot() {
        if (previousSlot >= 0 && mc.player != null) InvUtil.select(previousSlot);
        previousSlot = -1;
    }

    private void stop(String message) {
        restoreSlot();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Obitrap] " + message), false);
        target = null;
        anchor = null;
        idleTicks = 0;
        phase = "obsidian";
    }
}
