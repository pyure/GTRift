package com.pyure.gtrift.common.machine;

import com.pyure.gtrift.common.config.GTRiftConfig;
import com.pyure.gtrift.common.data.RiftMobPool;
import com.pyure.gtrift.common.data.RiftMobPoolEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

public class RiftEventSpawner {

    private static final int POSITION_CANDIDATES = 5;
    private static final ItemStack[] HELMET_CHOICES = {
            new ItemStack(Items.LEATHER_HELMET),
            new ItemStack(Items.IRON_HELMET),
            new ItemStack(Items.GOLDEN_HELMET)
    };

    /**
     * riftDirectionPos: the beacon's current rift-visual anchor (RiftBeaconMachine.riftVisualPos),
     * used to slant spawn angles toward it — null is valid (no rift open, or Phase 8 visual disabled
     * some other way) and simply disables the angular slant for this call.
     */
    public static void trySpawnMob(ServerLevel level, BlockPos beaconPos, int difficultyTier,
                                    BlockPos riftDirectionPos) {
        RandomSource random = level.getRandom();

        double eliteChance = Math.min(0.25, 0.05 + 0.02 * difficultyTier);
        boolean isElite = random.nextDouble() < eliteChance;
        RiftMobPool pool = isElite ? RiftMobPool.ELITE : RiftMobPool.NORMAL;
        RiftMobPoolEntry entry = pool.pickRandom(random);
        if (entry == null) return;

        BlockPos spawnPos = findSpawnPosition(level, beaconPos, random, riftDirectionPos);
        if (spawnPos == null) return;

        Entity spawned = entry.entityType().spawn(level, spawnPos, MobSpawnType.EVENT);
        if (!(spawned instanceof Mob mob)) return;

        applyDifficultyScaling(mob, difficultyTier);
        applyDaylightImmunity(mob, random);
        applyAggro(level, mob);

        // The loot handler (RiftLootDrops) needs to know both "is this ours" and "at what
        // difficulty" without re-deriving it from anything beacon-related, since the mob may
        // outlive the beacon or the rift event entirely.
        mob.getPersistentData().putBoolean("gtrift_mob", true);
        mob.getPersistentData().putInt("gtrift_tier", difficultyTier);

        if (isElite) {
            applyEliteTreatment(mob);
        }
    }

    // No-slant overload — used for the initial riftVisualPos roll (that call is what DEFINES the
    // rift direction, so it can't slant toward a direction that doesn't exist yet) and by any
    // caller that only cares about the distance bias (e.g. RiftSpawnPlacementTest's existing
    // near/far checks, which keep working unchanged against this overload).
    public static BlockPos findSpawnPosition(ServerLevel level, BlockPos beaconPos, RandomSource random) {
        return findSpawnPosition(level, beaconPos, random, null);
    }

    // Public (not private) so RiftBeaconMachine can reuse the same ring-position logic for its
    // rift-visual anchor point, and RiftSpawnPlacementTest (a different package, per this
    // project's gametest convention) can exercise it directly.
    public static BlockPos findSpawnPosition(ServerLevel level, BlockPos beaconPos, RandomSource random,
                                              BlockPos riftDirectionPos) {
        int buffer = GTRiftConfig.INSTANCE.safeBufferDistance;
        int radius = GTRiftConfig.INSTANCE.spawnRadius;
        if (radius <= buffer) return null;

        // One coin flip per call, not per candidate, so the configured percentage describes actual
        // spawns rather than being diluted across a mixed-band candidate set. "Not near" samples the
        // existing full range unchanged — never an exclusive far-only band — so uniform ring coverage
        // is preserved on non-near rolls. Both bands keep `buffer` as their floor either way, so the
        // safe-buffer guarantee (never spawn on/inside the beacon) is unaffected by this bias.
        boolean nearRoll = random.nextInt(100) < GTRiftConfig.INSTANCE.nearSpawnChancePercent;
        double maxDistance = nearRoll
                ? buffer + (radius - buffer) * (GTRiftConfig.INSTANCE.nearSpawnBandPercent / 100.0)
                : radius;

        // Independent of, and stacks with, the distance bias above — a separate coin flip on a
        // different axis (angle vs. distance), only meaningful once a rift direction actually
        // exists. slantCenterAngle == null means "sample the angle uniformly", same as before this
        // feature existed.
        boolean slantRoll = riftDirectionPos != null
                && random.nextInt(100) < GTRiftConfig.INSTANCE.riftSlantChancePercent;
        Double slantCenterAngle = slantRoll
                ? Math.atan2(riftDirectionPos.getZ() - beaconPos.getZ(), riftDirectionPos.getX() - beaconPos.getX())
                : null;
        double arcRadians = Math.toRadians(GTRiftConfig.INSTANCE.riftSlantArcDegrees);

        BlockPos best = null;
        int bestYDiff = Integer.MAX_VALUE;
        for (int i = 0; i < POSITION_CANDIDATES; i++) {
            double angle = slantCenterAngle != null
                    ? slantCenterAngle + (random.nextDouble() - 0.5) * arcRadians
                    : random.nextDouble() * Math.PI * 2;
            double distance = buffer + random.nextDouble() * (maxDistance - buffer);
            int x = beaconPos.getX() + (int) Math.round(Math.cos(angle) * distance);
            int z = beaconPos.getZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) continue;

            int yDiff = Math.abs(y - beaconPos.getY());
            if (yDiff < bestYDiff) {
                bestYDiff = yDiff;
                best = new BlockPos(x, y, z);
            }
        }
        return best;
    }

    private static void applyDifficultyScaling(Mob mob, int difficultyTier) {
        double multiplier = 1.0 + 0.5 * difficultyTier;

        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * multiplier);
            mob.setHealth(mob.getMaxHealth());
        }

        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * multiplier);
        }

        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() * multiplier);
        }
    }

    private static void applyDaylightImmunity(Mob mob, RandomSource random) {
        if (!(mob instanceof Zombie) && !(mob instanceof Skeleton)) return;
        ItemStack helmet = HELMET_CHOICES[random.nextInt(HELMET_CHOICES.length)].copy();
        mob.setItemSlot(EquipmentSlot.HEAD, helmet);
    }

    /**
     * "Much larger" elites (the spec's original wording) isn't achievable without a mixin or a
     * custom renderer — explicitly dropped per plan. Elites are instead distinguished by an
     * infinite glow effect, a distinct name, and a boss bar; size can come back later via a mixin
     * if ever judged worth the tradeoff. gtrift_elite is persistent-data, not a capability, since
     * it's a single ad-hoc boolean flag.
     */
    private static void applyEliteTreatment(Mob mob) {
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, -1, 0, false, false));

        Component eliteName = Component.literal("Elite ").append(mob.getType().getDescription());
        mob.setCustomName(eliteName);
        mob.setCustomNameVisible(true);

        mob.getPersistentData().putBoolean("gtrift_elite", true);

        ServerBossEvent bossEvent =
                new ServerBossEvent(eliteName, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        RiftEliteTracker.track(mob, bossEvent);
    }

    /**
     * Resets noActionTime (see Mob.checkDespawn()) to 0 for every Mob in one quadrant of the
     * beacon's spawn ring, so a full 4-quadrant sweep covers the whole area without scanning it
     * all in a single tick. quadrant: 0=(+X,+Z), 1=(+X,-Z), 2=(-X,+Z), 3=(-X,-Z) relative to
     * beaconPos. Deliberately untargeted — resets any Mob found, not just rift-spawned ones, since
     * tracking individual spawns would miss anything an entity summons on its own.
     */
    public static void refreshQuadrant(ServerLevel level, BlockPos beaconPos, int quadrant) {
        int radius = GTRiftConfig.INSTANCE.spawnRadius;
        boolean positiveX = (quadrant & 1) == 0;
        boolean positiveZ = (quadrant & 2) == 0;

        double minX = beaconPos.getX() + (positiveX ? 0 : -radius);
        double maxX = beaconPos.getX() + (positiveX ? radius : 0);
        double minZ = beaconPos.getZ() + (positiveZ ? 0 : -radius);
        double maxZ = beaconPos.getZ() + (positiveZ ? radius : 0);

        AABB quadrantBounds = new AABB(minX, level.getMinBuildHeight(), minZ, maxX, level.getMaxBuildHeight(), maxZ);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, quadrantBounds)) {
            mob.setNoActionTime(0);
        }
    }

    private static void applyAggro(ServerLevel level, Mob mob) {
        AttributeInstance followRange = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange != null) {
            followRange.addPermanentModifier(new AttributeModifier(
                    "gtrift_aggro_boost", 32.0, AttributeModifier.Operation.ADDITION));
        }

        // The trailing boolean is "ignoreCreative" (EntityGetter#getNearestPlayer) — true excludes
        // creative/spectator players from targeting, matching vanilla monster AI. Passing false (the
        // original mistake here) only excludes spectators, so creative players were valid targets.
        Player nearest = level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), -1.0, true);
        if (nearest != null) {
            mob.setTarget(nearest);
        }
    }
}
