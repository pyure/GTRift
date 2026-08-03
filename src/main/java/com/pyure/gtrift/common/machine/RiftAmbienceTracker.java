package com.pyure.gtrift.common.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.config.GTRiftConfig;
import com.pyure.gtrift.common.network.AmbienceSyncPacket;
import com.pyure.gtrift.common.network.GTRiftNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks per-beacon ambience state (charge ramp, Darkness, lightning) independently of any GUI or
 * player, self-polling the live {@link RiftBeaconMachine} each tick rather than being pushed
 * updates — same idiom as {@link RiftEliteTracker}. Survives the tracked beacon's own destruction
 * (multiblock invalidation or the controller block being broken outright) by fading its last-known
 * ramp to 0 over {@link #FADE_OUT_TICKS} instead of dropping to silence instantly, since ambience is
 * explicitly exempt from the base event's "no wind-down" behavior (see specs/ambience.md).
 */
@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RiftAmbienceTracker {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");

    // Public (not private) so RiftAmbienceTrackerTest can assert against the real values directly —
    // same reasoning as RiftEventSpawner's applyDifficultyScaling being made public for its own test —
    // rather than duplicating these magic numbers a second time in the test file.
    public static final int FADE_OUT_TICKS = 100; // 5s
    public static final double DARKNESS_RAMP_THRESHOLD = 0.5;
    public static final int DARKNESS_DURATION_TICKS = 100; // 5s
    public static final int DARKNESS_COOLDOWN_TICKS = 600; // 30s
    public static final int LIGHTNING_SURGE_STRIKE_COUNT = 3;
    public static final int LIGHTNING_SURGE_INTERVAL_TICKS = 5;
    public static final int LIGHTNING_BACKGROUND_CHANCE_DENOMINATOR = 400; // ~once per 20s

    // Deliberately the same RGB as RiftAmbienceRenderer.DOME_RED/GREEN/BLUE (client-side, can't be
    // referenced directly from this Dist-agnostic class) — kept in sync by hand so the ground motes
    // read as part of the same "the world itself is stained red" effect as the sky dome. If the dome
    // color is ever retuned again (it already was once, after the "pinkish" playtesting finding),
    // update this to match.
    private static final Vector3f GROUND_PARTICLE_COLOR = new Vector3f(0.4f, 0.03f, 0.04f);
    private static final float GROUND_PARTICLE_SCALE = 0.65f;
    private static final int GROUND_PARTICLE_COUNT = 2;
    private static final int GROUND_PARTICLE_MIN_INTERVAL_TICKS = 20; // 1s, at ramp = 1
    private static final int GROUND_PARTICLE_MAX_INTERVAL_TICKS = 60; // 3s, at ramp = 0

    // Spawn-warning flare — deliberately more pronounced than the passive ambient motes above
    // (brighter, larger, denser), since RiftBeaconMachine now fires this repeatedly across a ~2s
    // window rather than once: a single one-shot burst at the ambient tuning tested as "barely
    // noticeable" in real playtesting.
    private static final Vector3f SPAWN_FLARE_COLOR = new Vector3f(0.85f, 0.1f, 0.08f);
    private static final float SPAWN_FLARE_SCALE = 1.3f;
    private static final int SPAWN_FLARE_PARTICLE_COUNT = 10;

    // Elite spawn-warning flare — deliberately far more exaggerated than the regular one above.
    // "Particles actually going up a couple blocks" isn't achievable with DustParticleOptions
    // specifically: confirmed (this exact codebase, RiftBeaconMachine's own rift-visual crimson
    // accent) that dust particles don't respond to velocity, only position — so the "height" here is
    // real vertical SCATTER (a tall yOffset jitter puts particles anywhere within roughly that range
    // above the ground point, all at once) rather than particles individually climbing over time. That
    // still reads as "reaching up into the air," just via a different mechanism than true rise motion.
    private static final Vector3f ELITE_SPAWN_FLARE_RED_COLOR = new Vector3f(0.9f, 0.05f, 0.03f);
    // Not pure (0,0,0) — near-black stays reliably visible; true black risks blending into shadow/night.
    private static final Vector3f ELITE_SPAWN_FLARE_BLACK_COLOR = new Vector3f(0.05f, 0.05f, 0.05f);
    private static final float ELITE_SPAWN_FLARE_BLACK_FRACTION = 0.2f;
    private static final float ELITE_SPAWN_FLARE_SCALE = 1.6f;
    private static final int ELITE_SPAWN_FLARE_PARTICLE_COUNT = 25;
    private static final double ELITE_SPAWN_FLARE_HEIGHT_JITTER = 0.9; // ~2 blocks of scatter (roughly 2 std devs)

    private record BeaconKey(ResourceKey<Level> dimension, BlockPos pos) {}

    private static final class TrackedBeacon {
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        double ramp = 0.0;
        boolean fading = false;
        int fadeTicksRemaining = 0;
        double fadeStartRamp = 0.0;
        // Starts at 0 (not DARKNESS_COOLDOWN_TICKS) so the first pulse fires promptly the moment
        // ramp crosses DARKNESS_RAMP_THRESHOLD, rather than requiring a full 30s dwell above the
        // threshold first — confirmed via real playtesting that the latter reads as "Darkness doesn't
        // work during charging at all" when a charge cycle crosses 50% less than 30s before RIFT_OPEN.
        int darknessCooldownTicks = 0;
        BeaconState lastObservedState = BeaconState.IDLE;
        int surgeStrikesRemaining = 0;
        int surgeTicksUntilNext = 0;
        int groundParticleTicksUntilNext = 0;
        // Bookkeeping RiftEliteTracker gets for free from ServerBossEvent.getPlayers() — this
        // packet-based system has to track subscriber membership itself, so it knows who to send an
        // explicit clear packet to once they drop out (including after a dimension change, via UUID
        // lookup rather than re-checking this beacon's own dimension's player list).
        Set<UUID> subscribedPlayers = new HashSet<>();

        TrackedBeacon(ResourceKey<Level> dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }
    }

    private static final Map<BeaconKey, TrackedBeacon> TRACKED = new HashMap<>();

    /** Always starts fresh — used for a genuine new charge cycle (tryAccept), cancelling any fade-out already in progress for this position. */
    public static void register(ResourceKey<Level> dimension, BlockPos pos) {
        TRACKED.put(new BeaconKey(dimension, pos), new TrackedBeacon(dimension, pos));
    }

    /**
     * Restores tracking only if missing — used for reload recovery (onLoad), mirroring the existing
     * riftVisualPos safety net in RiftBeaconMachine. Seeds lastObservedState with the real current
     * state rather than defaulting to IDLE, so a beacon recovered while already RIFT_OPEN doesn't
     * spuriously look like it just transitioned into RIFT_OPEN on the next poll (which would
     * incorrectly re-fire the lightning surge).
     */
    public static void ensureTracked(ResourceKey<Level> dimension, BlockPos pos, BeaconState currentState) {
        TRACKED.computeIfAbsent(new BeaconKey(dimension, pos), key -> {
            TrackedBeacon beacon = new TrackedBeacon(dimension, pos);
            beacon.lastObservedState = currentState;
            return beacon;
        });
    }

    public static double computeRamp(BeaconState state, long chargeStored, long chargeTarget) {
        return switch (state) {
            case CHARGING -> chargeTarget > 0 ? Mth.clamp(chargeStored / (double) chargeTarget, 0.0, 1.0) : 0.0;
            case RIFT_OPEN -> 1.0;
            case IDLE, CHARGED -> 0.0;
        };
    }

    /** Two positions in different dimensions are never "within radius" of each other, regardless of coordinates. */
    public static boolean isWithinRadius(ResourceKey<Level> centerDimension, BlockPos center,
                                          ResourceKey<Level> otherDimension, BlockPos other, double radius) {
        if (!centerDimension.equals(otherDimension)) return false;
        return center.distSqr(other) <= radius * radius;
    }

    // 10 ticks (0.5s) between routine dispatches — a removal-triggered final clear (see below) always
    // fires immediately regardless of this cadence, since there's no later tick to catch it on once
    // the beacon's tracked entry is gone.
    private static final int DISPATCH_INTERVAL_TICKS = 10;
    private static int dispatchTickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || TRACKED.isEmpty()) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        boolean dispatchThisTick = ++dispatchTickCounter >= DISPATCH_INTERVAL_TICKS;
        if (dispatchThisTick) dispatchTickCounter = 0;

        Iterator<TrackedBeacon> iterator = TRACKED.values().iterator();
        while (iterator.hasNext()) {
            TrackedBeacon tracked = iterator.next();
            ServerLevel level = server.getLevel(tracked.dimension);
            if (level == null) {
                dispatchAmbiencePacket(server, null, tracked, false);
                iterator.remove();
                continue;
            }

            MetaMachine machine = MetaMachine.getMachine(level, tracked.pos);
            boolean isActive = machine instanceof RiftBeaconMachine beacon
                    && (beacon.state == BeaconState.CHARGING || beacon.state == BeaconState.RIFT_OPEN);

            boolean justRemoved = false;
            if (isActive) {
                RiftBeaconMachine beacon = (RiftBeaconMachine) machine;
                boolean enteringRiftOpen = tracked.lastObservedState != BeaconState.RIFT_OPEN
                        && beacon.state == BeaconState.RIFT_OPEN;
                tracked.lastObservedState = beacon.state;
                tracked.fading = false;
                tracked.ramp = computeRamp(beacon.state, beacon.chargeStored, beacon.chargeTarget);

                if (enteringRiftOpen) {
                    tracked.surgeStrikesRemaining = LIGHTNING_SURGE_STRIKE_COUNT;
                    tracked.surgeTicksUntilNext = 0;
                }

                tickDarkness(level, tracked);
                tickLightning(level, tracked);
                tickGroundParticles(level, tracked);
            } else {
                if (!tracked.fading) {
                    tracked.fading = true;
                    tracked.fadeTicksRemaining = FADE_OUT_TICKS;
                    tracked.fadeStartRamp = tracked.ramp;
                    LOGGER.debug("Beacon {} entering fade: machine={}, lastObservedState={}, startRamp={}",
                            tracked.pos, machine == null ? "null (block/BE gone)" : machine.getClass().getSimpleName(),
                            tracked.lastObservedState, tracked.fadeStartRamp);
                }
                tracked.fadeTicksRemaining--;
                tracked.ramp = tracked.fadeTicksRemaining > 0
                        ? tracked.fadeStartRamp * (tracked.fadeTicksRemaining / (double) FADE_OUT_TICKS)
                        : 0.0;
                if (tracked.fadeTicksRemaining <= 0) {
                    LOGGER.debug("Beacon {} fade complete, removing tracked entry (subscribedPlayers={})",
                            tracked.pos, tracked.subscribedPlayers.size());
                    iterator.remove();
                    justRemoved = true;
                }
            }

            if (dispatchThisTick || justRemoved) {
                dispatchAmbiencePacket(server, level, tracked, !justRemoved);
            }
        }
    }

    /**
     * Sends the current ramp to every player within Math.max(backdropRadius, spawnRadius) of this
     * beacon (the effective dispatch radius — clamped so a backdropRadius misconfigured smaller than
     * spawnRadius can never leave players in active danger without visual coverage), and an explicit
     * clear to anyone previously subscribed who's no longer in that set — looked up by UUID via the
     * server's own player list rather than this beacon's own level's player list, so the clear still
     * reaches someone who's already left the dimension by the time it's sent. beaconStillExists=false
     * (the beacon's tracked entry just finished fading and was removed, or its dimension vanished
     * outright) skips the "who's in range" scan entirely and clears every remaining subscriber.
     */
    private static void dispatchAmbiencePacket(MinecraftServer server, ServerLevel level, TrackedBeacon tracked,
                                                boolean beaconStillExists) {
        Set<UUID> currentlyInRange = new HashSet<>();
        boolean musicEnabled = false;

        if (beaconStillExists && level != null) {
            // Music is now routed through vanilla's MusicManager (flat volume, non-positional — see
            // RiftAmbienceRenderer's doc comment for why the original real-3D-falloff SoundInstance
            // approach was abandoned), so it's gated to the smaller encounter radius per-player here,
            // not just the beacon-wide backdrop-radius dispatch scope: without positional falloff to
            // quiet it with distance, playing it at full volume for anyone out to backdropRadius (200
            // blocks default) would mean someone nowhere near any danger still hearing full-volume
            // music. Encounter radius matches the original pre-positional design intent.
            musicEnabled = tracked.lastObservedState == BeaconState.RIFT_OPEN && GTRiftConfig.INSTANCE.enableRiftMusic;
            double backdropRadius = Math.max(GTRiftConfig.INSTANCE.backdropRadius, GTRiftConfig.INSTANCE.spawnRadius);
            double encounterRadius = GTRiftConfig.INSTANCE.spawnRadius;
            for (ServerPlayer player : level.players()) {
                if (isWithinRadius(tracked.dimension, tracked.pos, level.dimension(), player.blockPosition(), backdropRadius)) {
                    currentlyInRange.add(player.getUUID());
                    boolean playMusicForPlayer = musicEnabled && isWithinRadius(
                            tracked.dimension, tracked.pos, level.dimension(), player.blockPosition(), encounterRadius);
                    GTRiftNetworking.sendToPlayer(player,
                            new AmbienceSyncPacket(tracked.pos, (float) tracked.ramp, true, playMusicForPlayer));
                }
            }
        }

        Set<UUID> cleared = new HashSet<>();
        for (UUID uuid : tracked.subscribedPlayers) {
            if (currentlyInRange.contains(uuid)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);
            if (player != null) {
                GTRiftNetworking.sendToPlayer(player, new AmbienceSyncPacket(tracked.pos, 0f, false, false));
                cleared.add(uuid);
            }
        }

        LOGGER.debug("dispatchAmbiencePacket beacon={} beaconStillExists={} ramp={} musicEnabled={} sentActive={} sentCleared={}",
                tracked.pos, beaconStillExists, tracked.ramp, musicEnabled, currentlyInRange, cleared);

        tracked.subscribedPlayers = currentlyInRange;
    }

    private static void tickDarkness(ServerLevel level, TrackedBeacon tracked) {
        if (tracked.ramp < DARKNESS_RAMP_THRESHOLD) return;
        tracked.darknessCooldownTicks--;
        if (tracked.darknessCooldownTicks > 0) return;
        tracked.darknessCooldownTicks = DARKNESS_COOLDOWN_TICKS;

        double radius = GTRiftConfig.INSTANCE.spawnRadius;
        for (ServerPlayer player : level.players()) {
            if (isWithinRadius(tracked.dimension, tracked.pos, level.dimension(), player.blockPosition(), radius)) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, DARKNESS_DURATION_TICKS, 0));
            }
        }
    }

    private static void tickLightning(ServerLevel level, TrackedBeacon tracked) {
        RandomSource random = level.getRandom();
        if (tracked.surgeStrikesRemaining > 0) {
            tracked.surgeTicksUntilNext--;
            if (tracked.surgeTicksUntilNext <= 0) {
                spawnVisualLightning(level, tracked.pos, random);
                tracked.surgeStrikesRemaining--;
                tracked.surgeTicksUntilNext = LIGHTNING_SURGE_INTERVAL_TICKS;
            }
            return; // no background rolls while a surge is in progress
        }

        if (tracked.lastObservedState == BeaconState.RIFT_OPEN
                && random.nextInt(LIGHTNING_BACKGROUND_CHANCE_DENOMINATOR) == 0) {
            spawnVisualLightning(level, tracked.pos, random);
        }
    }

    private static void spawnVisualLightning(ServerLevel level, BlockPos beaconPos, RandomSource random) {
        BlockPos strikePos = RiftEventSpawner.findSpawnPosition(level, beaconPos, random);
        if (strikePos == null) return;
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(Vec3.atBottomCenterOf(strikePos));
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    /**
     * Subtle red dust motes scattered on the ground within spawnRadius — no alpha channel on
     * DustParticleOptions to fade with ramp, so "ramping in with charge" happens via frequency instead
     * (sparse during early charging, reaching a still-subtle steady rate by RIFT_OPEN).
     */
    private static void tickGroundParticles(ServerLevel level, TrackedBeacon tracked) {
        tracked.groundParticleTicksUntilNext--;
        if (tracked.groundParticleTicksUntilNext > 0) return;
        tracked.groundParticleTicksUntilNext = (int) Mth.lerp(
                tracked.ramp, GROUND_PARTICLE_MAX_INTERVAL_TICKS, GROUND_PARTICLE_MIN_INTERVAL_TICKS);

        BlockPos pos = RiftEventSpawner.findSpawnPosition(level, tracked.pos, level.getRandom());
        if (pos == null) return;
        emitGroundParticles(level, pos, GROUND_PARTICLE_COLOR, GROUND_PARTICLE_SCALE, GROUND_PARTICLE_COUNT, 0.0);
    }

    /**
     * One burst of the spawn-warning flare's own (more pronounced) visual — RiftBeaconMachine calls
     * this repeatedly across its ~2s lead+tail window, not once, so this method itself doesn't own any
     * timing/repetition, just a single emission at the given spot.
     */
    public static void emitSpawnWarningFlare(ServerLevel level, BlockPos pos) {
        emitGroundParticles(level, pos, SPAWN_FLARE_COLOR, SPAWN_FLARE_SCALE, SPAWN_FLARE_PARTICLE_COUNT, 0.0);
    }

    /**
     * One burst of the far-more-exaggerated elite variant — 80/20 red/black split (two separate
     * sendParticles calls, since a single DustParticleOptions instance is one fixed color), taller
     * vertical scatter than the regular flare. Same "called repeatedly by the caller" shape as
     * emitSpawnWarningFlare above.
     */
    public static void emitEliteSpawnWarningFlare(ServerLevel level, BlockPos pos) {
        int blackCount = Math.round(ELITE_SPAWN_FLARE_PARTICLE_COUNT * ELITE_SPAWN_FLARE_BLACK_FRACTION);
        int redCount = ELITE_SPAWN_FLARE_PARTICLE_COUNT - blackCount;
        emitGroundParticles(level, pos, ELITE_SPAWN_FLARE_RED_COLOR, ELITE_SPAWN_FLARE_SCALE, redCount,
                ELITE_SPAWN_FLARE_HEIGHT_JITTER);
        emitGroundParticles(level, pos, ELITE_SPAWN_FLARE_BLACK_COLOR, ELITE_SPAWN_FLARE_SCALE, blackCount,
                ELITE_SPAWN_FLARE_HEIGHT_JITTER);
    }

    private static void emitGroundParticles(ServerLevel level, BlockPos pos, Vector3f color, float scale,
                                             int count, double yOffset) {
        level.sendParticles(new DustParticleOptions(color, scale),
                pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, count, 0.3, yOffset, 0.3, 0.0);
    }
}
