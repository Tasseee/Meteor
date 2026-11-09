package dev.nightfall.modules;

import dev.nightfall.module.Category;
import dev.nightfall.module.Module;
import dev.nightfall.module.setting.*;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Greift automatisch das beste Ziel in Reichweite an. Haelt sich an Vanilla-Regeln: Reichweite (Standard 3.0),
 * voller Angriffscooldown, ein Angriff pro Tick, normale Angriffs-Pakete. Keine Tarnung, kein Anti-Cheat-Bypass -
 * Server mit Anti-Cheat koennen das trotzdem erkennen und ahnden.
 */
public class KillAura extends Module {
    private final NumberSetting reach     = add(new NumberSetting("Reichweite", 3.2, 1.0, 6.0, 0.1));
    private final NumberSetting fov       = add(new NumberSetting("FOV-Limit", 180, 30, 360, 10));
    private final ModeSetting   priority  = add(new ModeSetting("Prioritaet", "Distanz", "Distanz", "Health"));
    private final ModeSetting   rotate    = add(new ModeSetting("Rotation", "Aus", "Aus", "Sichtbar"));
    private final BoolSetting   cooldown  = add(new BoolSetting("Cooldown pruefen", true));
    private final BoolSetting   walls     = add(new BoolSetting("Durch Waende", false));
    private final BoolSetting   players   = add(new BoolSetting("Spieler", true));
    private final BoolSetting   hostile   = add(new BoolSetting("Feindliche Mobs", true));
    private final BoolSetting   passive   = add(new BoolSetting("Passive Mobs", false));
    public final ListSetting friends      = add(new ListSetting("Freunde"));

    public LivingEntity target;

    public KillAura() { super("KillAura", "Greift Ziele in Reichweite automatisch an (Cooldown, Freundesliste).", Category.COMBAT); }

    @Override protected void onDisable() { target = null; }

    private double reachDist(ClientPlayerEntity p, Entity e) {
        Box b = e.getBoundingBox();
        Vec3d eye = p.getEyePos();
        double x = Math.max(b.minX, Math.min(eye.x, b.maxX));
        double y = Math.max(b.minY, Math.min(eye.y, b.maxY));
        double z = Math.max(b.minZ, Math.min(eye.z, b.maxZ));
        return eye.distanceTo(new Vec3d(x, y, z));
    }

    private boolean valid(ClientPlayerEntity p, LivingEntity e) {
        if (e == p || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof PlayerEntity pe) {
            if (!players.get() || friends.has(pe.getName().getString())) return false;
        } else if (e.getType().getSpawnGroup() == net.minecraft.entity.SpawnGroup.MONSTER) {
            if (!hostile.get()) return false;
        } else if (!passive.get()) return false;
        if (reachDist(p, e) > reach.get()) return false;
        if (!walls.get() && !p.canSee(e)) return false;
        if (fov.get() < 360) {
            double dx = e.getX() - p.getX(), dz = e.getZ() - p.getZ();
            double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
            double diff = Math.abs(((yaw - p.getYaw()) % 360 + 540) % 360 - 180);
            if (diff > fov.get() / 2.0) return false;
        }
        return true;
    }

    @Override
    public void onTick() {
        target = null;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null || !p.isAlive()) return;

        LivingEntity best = null;
        double bestKey = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || !valid(p, le)) continue;
            double key = priority.is("Health") ? le.getHealth() : reachDist(p, le);
            if (key < bestKey) { bestKey = key; best = le; }
        }
        if (best == null) return;
        target = best;

        if (rotate.is("Sichtbar")) {
            Box b = best.getBoundingBox();
            Vec3d eye = p.getEyePos();
            double dx = (b.minX + b.maxX) / 2 - eye.x, dy = (b.minY + b.maxY) / 2 - eye.y, dz = (b.minZ + b.maxZ) / 2 - eye.z;
            p.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
            p.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
        }
        if (cooldown.get() && p.getAttackCooldownProgress(0.5f) < 0.95f) return;
        mc.interactionManager.attackEntity(p, best);
        p.swingHand(Hand.MAIN_HAND);
    }
}
