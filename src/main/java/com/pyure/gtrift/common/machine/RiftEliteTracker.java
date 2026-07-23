package com.pyure.gtrift.common.machine;

import com.pyure.gtrift.GTRift;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks elite mobs' boss bars independently of any beacon's lifecycle — per spec, mobs already
 * spawned when a beacon is destroyed are left alone, so their boss bar must keep working
 * regardless of beacon state. Also drives the elite particle trail, reusing the same per-tick pass.
 *
 * Player visibility is synced by manual distance check each tick rather than the vanilla
 * startSeenByPlayer/stopSeenByPlayer entity hooks (what WitherBoss/EndDragonFight use) because
 * those require overriding methods on the entity's own class — not possible here, since rift mobs
 * are plain vanilla/modded EntityTypes from the JSON pool, not a GTRift-owned entity class.
 */
@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RiftEliteTracker {

    private static final double PLAYER_VISIBILITY_RADIUS = 64.0;
    private static final double PLAYER_VISIBILITY_RADIUS_SQR = PLAYER_VISIBILITY_RADIUS * PLAYER_VISIBILITY_RADIUS;
    private static final int PARTICLE_INTERVAL_TICKS = 10;

    private record Tracked(LivingEntity entity, ServerBossEvent bossEvent) {}

    private static final Map<UUID, Tracked> TRACKED = new HashMap<>();
    private static int particleTimer = 0;

    public static void track(LivingEntity entity, ServerBossEvent bossEvent) {
        TRACKED.put(entity.getUUID(), new Tracked(entity, bossEvent));
    }

    public static int trackedCount() {
        return TRACKED.size();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRACKED.isEmpty()) return;

        boolean emitParticles = ++particleTimer >= PARTICLE_INTERVAL_TICKS;
        if (emitParticles) particleTimer = 0;

        Iterator<Tracked> iterator = TRACKED.values().iterator();
        while (iterator.hasNext()) {
            Tracked tracked = iterator.next();
            LivingEntity entity = tracked.entity();
            ServerBossEvent bossEvent = tracked.bossEvent();

            if (!entity.isAlive()) {
                bossEvent.removeAllPlayers();
                iterator.remove();
                continue;
            }

            bossEvent.setProgress(Mth.clamp(entity.getHealth() / entity.getMaxHealth(), 0.0F, 1.0F));
            syncPlayers(entity, bossEvent);

            if (emitParticles && entity.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                        3, 0.3, 0.3, 0.3, 0.01);
            }
        }
    }

    private static void syncPlayers(LivingEntity entity, ServerBossEvent bossEvent) {
        if (!(entity.level() instanceof ServerLevel serverLevel)) return;

        for (ServerPlayer player : serverLevel.players()) {
            boolean withinRange = player.distanceToSqr(entity) <= PLAYER_VISIBILITY_RADIUS_SQR;
            boolean currentlyShown = bossEvent.getPlayers().contains(player);
            if (withinRange && !currentlyShown) {
                bossEvent.addPlayer(player);
            } else if (!withinRange && currentlyShown) {
                bossEvent.removePlayer(player);
            }
        }
    }
}
