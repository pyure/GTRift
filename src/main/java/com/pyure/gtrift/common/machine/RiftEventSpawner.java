package com.pyure.gtrift.common.machine;

import com.pyure.gtrift.common.config.GTRiftConfig;
import com.pyure.gtrift.common.data.RiftDropEntry;
import com.pyure.gtrift.common.data.RiftMobPool;
import com.pyure.gtrift.common.data.RiftMobPoolEntry;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
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

import java.util.List;

public class RiftEventSpawner {

    private static final int POSITION_CANDIDATES = 5;
    private static final ItemStack[] HELMET_CHOICES = {
            new ItemStack(Items.LEATHER_HELMET),
            new ItemStack(Items.IRON_HELMET),
            new ItemStack(Items.GOLDEN_HELMET)
    };

    /**
     * riftDirectionPos: one of the beacon's current rift-open columns (RiftBeaconMachine.columnPositions),
     * picked fresh per call via pickSlantTarget — used to slant spawn angles toward it — null is
     * valid (no rift open, or the column list happened to be empty) and simply disables the angular
     * slant for this call.
     */
    public static Mob trySpawnMob(ServerLevel level, BlockPos beaconPos, int difficultyTier,
                                   BlockPos riftDirectionPos) {
        BlockPos spawnPos = findSpawnPosition(level, beaconPos, level.getRandom(), riftDirectionPos);
        if (spawnPos == null) return null;
        return trySpawnMob(level, spawnPos, difficultyTier);
    }

    /**
     * Spawns at an already-chosen position rather than picking one itself — used by
     * RiftBeaconMachine's spawn-warning-flare delay, where the position is picked (and a particle
     * flare fired there) some ticks before the mob actually appears, so the flare and the eventual
     * spawn land at the exact same spot. The 4-arg overload above (the normal, atomic path) is just a
     * thin wrapper around this one, rolling elite/normal itself since it doesn't need to know in
     * advance which it'll be.
     */
    public static Mob trySpawnMob(ServerLevel level, BlockPos spawnPos, int difficultyTier) {
        return trySpawnMob(level, spawnPos, difficultyTier, rollIsElite(level.getRandom(), difficultyTier));
    }

    /**
     * Same elite-chance formula RiftBeaconMachine's spawn-warning-flare logic now rolls in advance
     * (at flare-decision time, not spawn time) so it knows whether to show the exaggerated elite flare
     * before the mob type itself has been chosen — pulled out to its own method so both that caller and
     * the 3-arg trySpawnMob above share exactly one formula.
     */
    public static boolean rollIsElite(RandomSource random, int difficultyTier) {
        double eliteChance = Math.min(0.25, 0.05 + 0.02 * difficultyTier);
        return random.nextDouble() < eliteChance;
    }

    /** Spawns at an already-chosen position with an already-decided elite/normal outcome — see rollIsElite's own doc comment for why the decision needs to happen earlier than this call for the elite-flare feature. */
    public static Mob trySpawnMob(ServerLevel level, BlockPos spawnPos, int difficultyTier, boolean isElite) {
        RandomSource random = level.getRandom();

        RiftMobPool pool = isElite ? RiftMobPool.ELITE : RiftMobPool.NORMAL;
        RiftMobPoolEntry entry = pool.pickRandom(random, level.dimension());
        if (entry == null) return null;

        Entity spawned = entry.entityType().spawn(level, spawnPos, MobSpawnType.EVENT);
        if (!(spawned instanceof Mob mob)) return null;

        applyDifficultyScaling(mob, difficultyTier, entry);
        applyDaylightImmunity(mob, random);
        applyAggro(level, mob);

        // The loot handler (RiftLootDrops) needs to know both "is this ours" and "at what
        // difficulty" without re-deriving it from anything beacon-related, since the mob may
        // outlive the beacon or the rift event entirely.
        mob.getPersistentData().putBoolean("gtrift_mob", true);
        mob.getPersistentData().putInt("gtrift_tier", difficultyTier);

        // Filtered once here (not at death) since difficultyTier is only known at spawn time and
        // the mob may outlive the beacon; the loot handler rolls exactly what's serialized below,
        // with no further tier check needed.
        List<RiftDropEntry> eligibleDrops = filterEligibleDrops(entry.drops(), difficultyTier);
        if (!eligibleDrops.isEmpty()) {
            ListTag dropsTag = new ListTag();
            for (RiftDropEntry drop : eligibleDrops) {
                dropsTag.add(drop.toNbt());
            }
            mob.getPersistentData().put("gtrift_drops", dropsTag);
        }

        if (isElite) {
            applyEliteTreatment(mob);
        }

        return mob;
    }

    // Public (not private), pure, and independent of RiftMobPool/entity spawning, so
    // RiftEventSpawnerDropTest can exercise the minTier gate directly without mutating the global
    // RiftMobPool singletons — doing that from a test previously leaked stray (occasionally elite)
    // mobs into other concurrently-running tests that tick a real beacon through RIFT_OPEN (e.g.
    // RiftEventStateTest), since trySpawnMob reads those pools live and nothing in this class knows
    // which caller is "just testing".
    public static List<RiftDropEntry> filterEligibleDrops(List<RiftDropEntry> drops, int difficultyTier) {
        return drops.stream().filter(drop -> drop.minTier() <= difficultyTier).toList();
    }

    // No-slant overload — used for each of RiftBeaconMachine.generateColumnPositions' 30 independent
    // column rolls (those calls are what DEFINE the possible rift directions, so they can't slant
    // toward a direction that doesn't exist yet) and by any caller that only cares about the
    // distance bias (e.g. RiftSpawnPlacementTest's existing near/far checks, which keep working
    // unchanged against this overload).
    public static BlockPos findSpawnPosition(ServerLevel level, BlockPos beaconPos, RandomSource random) {
        return findSpawnPosition(level, beaconPos, random, null);
    }

    // Public (not private) so RiftBeaconMachine can reuse the same ring-position logic for its
    // column anchors, and RiftSpawnPlacementTest (a different package, per this
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

    /**
     * Picks one column uniformly at random (with replacement — every call is an independent pick,
     * so the same column can come up again on the very next call) to pass as findSpawnPosition's
     * riftDirectionPos. Returns null on a null/empty list — degrades to findSpawnPosition's existing
     * "no slant, sample the angle uniformly" behavior rather than a special case.
     */
    public static BlockPos pickSlantTarget(RandomSource random, List<BlockPos> columns) {
        if (columns == null || columns.isEmpty()) return null;
        return columns.get(random.nextInt(columns.size()));
    }

    // Flat additive bonus per tier, not a shared multiplier — deliberately so stats with very
    // different base magnitudes scale asymmetrically (health/damage grow much more noticeably than
    // speed, which has a small vanilla base of ~0.2-0.3).
    private static final double HEALTH_BONUS_PER_TIER = 5.0;
    private static final double DAMAGE_BONUS_PER_TIER = 2.0;
    private static final double SPEED_BONUS_PER_TIER = 0.1;

    // entry's healthMultiplier/damageMultiplier/speedMultiplier (default 1.0, a no-op) scale the
    // FINAL tier-adjusted value — (vanillaBase + perTierBonus) * multiplier — not the vanilla base
    // before the tier bonus. No elite-specific variant: "elite" already means "drawn from the separate
    // rift_elite_mobs pool," so a pack dev wanting beefier elites sets a higher multiplier directly in
    // that pool's own file.
    //
    // Public (not private) so RiftDifficultyScalingTest can exercise it directly against a real spawned
    // mob without needing the full trySpawnMob pipeline (which would mean touching the global
    // RiftMobPool singletons — see RiftEventSpawnerDropTest's own doc comment for why that's avoided).
    public static void applyDifficultyScaling(Mob mob, int difficultyTier, RiftMobPoolEntry entry) {
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            double tierAdjusted = health.getBaseValue() + HEALTH_BONUS_PER_TIER * difficultyTier;
            health.setBaseValue(tierAdjusted * entry.healthMultiplier());
            mob.setHealth(mob.getMaxHealth());
        }

        AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            double tierAdjusted = speed.getBaseValue() + SPEED_BONUS_PER_TIER * difficultyTier;
            speed.setBaseValue(tierAdjusted * entry.speedMultiplier());
        }

        AttributeInstance damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            double tierAdjusted = damage.getBaseValue() + DAMAGE_BONUS_PER_TIER * difficultyTier;
            damage.setBaseValue(tierAdjusted * entry.damageMultiplier());
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
