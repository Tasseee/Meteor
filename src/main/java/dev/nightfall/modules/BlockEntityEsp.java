package dev.nightfall.modules;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import dev.nightfall.util.Projection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** ESP fuer BlockEntities. Die Trefferliste wird nur alle 10 Ticks aus bereits geladenen Chunks neu aufgebaut. */
public abstract class BlockEntityEsp extends EspBase {
    private final List<BlockPos> cache = new ArrayList<>();
    private final List<Box> boxes = new ArrayList<>();
    private int timer;

    protected BlockEntityEsp(String n, String d, int r, int mr, int rgb) { super(n, d, r, mr, rgb); }

    protected abstract boolean accept(BlockEntity be);
    protected abstract String nameOf(BlockEntity be);

    @Override protected void onDisable() { cache.clear(); boxes.clear(); }
    @Override public void onWorldLeave() { cache.clear(); boxes.clear(); timer = 0; }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        if (timer-- > 0) return;
        timer = 10;
        cache.clear(); boxes.clear();
        int r = range.getInt();
        Camera camera = mc.gameRenderer.getCamera();
        Vec3d origin = camera != null && camera.isReady()
                ? camera.getCameraPos()
                : mc.player.getEyePos();
        double px = origin.x, py = origin.y, pz = origin.z;
        int pcx = ((int) Math.floor(px)) >> 4, pcz = ((int) Math.floor(pz)) >> 4;
        int cr = (r >> 4) + 1;
        double r2 = (double) r * r;
        for (int dz = -cr; dz <= cr; dz++) {
            for (int dx = -cr; dx <= cr; dx++) {
                WorldChunk ch = mc.world.getChunkManager().getWorldChunk(pcx + dx, pcz + dz);
                if (ch == null) continue;
                for (BlockEntity be : ch.getBlockEntities().values()) {
                    if (!accept(be)) continue;
                    BlockPos bp = be.getPos();
                    if (bp.getSquaredDistance(px, py, pz) > r2) continue;
                    cache.add(bp.toImmutable());
                    boxes.add(new Box(bp));
                }
            }
        }
        // Keep the nearest targets first so the object cap is useful rather than chunk-order dependent.
        List<Integer> order = new ArrayList<>(cache.size());
        for (int i = 0; i < cache.size(); i++) order.add(i);
        order.sort(Comparator.comparingDouble(i -> cache.get(i).getSquaredDistance(px, py, pz)));
        if (!order.isEmpty()) {
            List<BlockPos> sortedPositions = new ArrayList<>(cache.size());
            List<Box> sortedBoxes = new ArrayList<>(boxes.size());
            for (int i : order) { sortedPositions.add(cache.get(i)); sortedBoxes.add(boxes.get(i)); }
            cache.clear(); cache.addAll(sortedPositions);
            boxes.clear(); boxes.addAll(sortedBoxes);
        }
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (mc.world == null || mc.player == null || !Projection.valid()) return;
        int n = cache.size();
        int shown = 0;
        // naechste Objekte zuerst: Liste ist nach Chunk-Naehe nicht sortiert -> bei Ueberschreitung des Limits einfach kappen
        for (int i = 0; i < n && shown < maxObjects.getInt() && !capReached(); i++) {
            BlockPos bp = cache.get(i);
            BlockEntity be = mc.world.getBlockEntity(bp);
            if (be == null || !accept(be)) continue;
            double d = Math.sqrt(bp.getSquaredDistance(Projection.eyeX(), Projection.eyeY(), Projection.eyeZ()));
            drawTarget(ctx, boxes.get(i), label(nameOf(be), d), color.rgb());
            shown++;
        }
    }
}
