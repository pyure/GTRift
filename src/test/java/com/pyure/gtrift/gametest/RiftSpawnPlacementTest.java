package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.config.GTRiftConfig;
import com.pyure.gtrift.common.machine.RiftEventSpawner;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure logic against a real ServerLevel (needed for findSpawnPosition's heightmap query) — no
 * structure content, template = "empty", same pattern as RiftLootTableTest's statistical checks.
 *
 * Sets GTRiftConfig.INSTANCE's fields directly to known values rather than relying on whatever the
 * generated config file happens to contain, for a deterministic expected result.
 *
 * The "near band" and "full range" samples overlap: a full-range roll can land inside the near band
 * purely by chance (it samples uniformly across the whole range, which includes the near band as a
 * sub-range). The expected proportion landing in the near band is therefore not just
 * nearSpawnChancePercent — it's nearChance + (1 - nearChance) * bandFraction, accounting for that
 * overlap.
 *
 * Tolerance is wider than a naive uniform-sampling model would suggest: findSpawnPosition doesn't
 * return one raw distance sample, it evaluates POSITION_CANDIDATES=5 independently-rolled candidates
 * and returns whichever has the closest Y to the beacon — a pre-existing selection mechanism (not
 * introduced by the near-bias) that measurably skews the resulting distance distribution away from
 * the simple uniform model (confirmed: a 0%-near-chance run measured ~36% in a band whose own share
 * of the uniform range is 30%, well beyond sampling noise at 20k trials). The bias check here is
 * "does the near band's share move in the right direction and rough magnitude," not an exact match
 * to a theoretical model that doesn't account for that selection step.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftSpawnPlacementTest {

    private static final int TRIALS = 20_000;
    private static final double TOLERANCE_PERCENT = 10.0;

    @GameTest(template = "empty")
    public static void nearBiasMatchesExpectedProportionAndRespectsBuffer(GameTestHelper helper) {
        GTRiftConfig.INSTANCE.safeBufferDistance = 20;
        GTRiftConfig.INSTANCE.spawnRadius = 60;
        GTRiftConfig.INSTANCE.nearSpawnChancePercent = 50;
        GTRiftConfig.INSTANCE.nearSpawnBandPercent = 30;

        int buffer = GTRiftConfig.INSTANCE.safeBufferDistance;
        int radius = GTRiftConfig.INSTANCE.spawnRadius;
        double bandFraction = GTRiftConfig.INSTANCE.nearSpawnBandPercent / 100.0;
        double nearChance = GTRiftConfig.INSTANCE.nearSpawnChancePercent;
        double nearBandMax = buffer + (radius - buffer) * bandFraction;
        double expectedNearPercent = nearChance + (100.0 - nearChance) * bandFraction;

        ServerLevel level = helper.getLevel();
        BlockPos beaconPos = helper.absolutePos(new BlockPos(0, 1, 0));
        RandomSource random = RandomSource.create();

        int validCount = 0;
        int nearBandCount = 0;
        for (int i = 0; i < TRIALS; i++) {
            BlockPos pos = RiftEventSpawner.findSpawnPosition(level, beaconPos, random);
            if (pos == null) continue;
            validCount++;

            double dx = pos.getX() - beaconPos.getX();
            double dz = pos.getZ() - beaconPos.getZ();
            double xzDistance = Math.sqrt(dx * dx + dz * dz);

            // +-1 slack: findSpawnPosition rounds the sampled distance to integer block coordinates.
            helper.assertTrue(xzDistance >= buffer - 1,
                    "spawn position %s landed inside the safe buffer (xzDistance=%.2f, buffer=%d)"
                            .formatted(pos, xzDistance, buffer));
            helper.assertTrue(xzDistance <= radius + 1,
                    "spawn position %s landed outside the configured radius (xzDistance=%.2f, radius=%d)"
                            .formatted(pos, xzDistance, radius));

            if (xzDistance <= nearBandMax + 1) {
                nearBandCount++;
            }
        }

        helper.assertTrue(validCount > TRIALS / 2, "findSpawnPosition failed to resolve a position too often");

        double actualNearPercent = nearBandCount * 100.0 / validCount;
        helper.assertTrue(Math.abs(actualNearPercent - expectedNearPercent) < TOLERANCE_PERCENT,
                "expected ~%.1f%% of spawns within the near band (accounting for full-range overlap), got %.1f%%"
                        .formatted(expectedNearPercent, actualNearPercent));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroNearChanceMatchesOldUniformBehavior(GameTestHelper helper) {
        GTRiftConfig.INSTANCE.safeBufferDistance = 20;
        GTRiftConfig.INSTANCE.spawnRadius = 60;
        GTRiftConfig.INSTANCE.nearSpawnChancePercent = 0;
        GTRiftConfig.INSTANCE.nearSpawnBandPercent = 30;

        int buffer = GTRiftConfig.INSTANCE.safeBufferDistance;
        int radius = GTRiftConfig.INSTANCE.spawnRadius;
        double bandFraction = GTRiftConfig.INSTANCE.nearSpawnBandPercent / 100.0;
        double nearBandMax = buffer + (radius - buffer) * bandFraction;
        // With 0% near chance, every roll is full-range, so the near band should just reflect its
        // own share of that range — no bias contribution at all.
        double expectedNearPercent = bandFraction * 100.0;

        ServerLevel level = helper.getLevel();
        BlockPos beaconPos = helper.absolutePos(new BlockPos(0, 1, 0));
        RandomSource random = RandomSource.create();

        int validCount = 0;
        int nearBandCount = 0;
        for (int i = 0; i < TRIALS; i++) {
            BlockPos pos = RiftEventSpawner.findSpawnPosition(level, beaconPos, random);
            if (pos == null) continue;
            validCount++;

            double dx = pos.getX() - beaconPos.getX();
            double dz = pos.getZ() - beaconPos.getZ();
            double xzDistance = Math.sqrt(dx * dx + dz * dz);
            if (xzDistance <= nearBandMax + 1) {
                nearBandCount++;
            }
        }

        helper.assertTrue(validCount > TRIALS / 2, "findSpawnPosition failed to resolve a position too often");

        double actualNearPercent = nearBandCount * 100.0 / validCount;
        helper.assertTrue(Math.abs(actualNearPercent - expectedNearPercent) < TOLERANCE_PERCENT,
                "with nearSpawnChancePercent=0, expected ~%.1f%% in the near band (its natural share of a uniform range), got %.1f%%"
                        .formatted(expectedNearPercent, actualNearPercent));

        helper.succeed();
    }

    // Distance bias disabled (nearSpawnChancePercent=0) for both slant tests below, to isolate the
    // angular effect from the already-verified distance effect — they're independent axes.

    @GameTest(template = "empty")
    public static void slantBiasMatchesExpectedProportion(GameTestHelper helper) {
        GTRiftConfig.INSTANCE.safeBufferDistance = 20;
        GTRiftConfig.INSTANCE.spawnRadius = 60;
        GTRiftConfig.INSTANCE.nearSpawnChancePercent = 0;
        GTRiftConfig.INSTANCE.riftSlantChancePercent = 50;
        GTRiftConfig.INSTANCE.riftSlantArcDegrees = 90;

        double slantChance = GTRiftConfig.INSTANCE.riftSlantChancePercent;
        double arcFraction = GTRiftConfig.INSTANCE.riftSlantArcDegrees / 360.0;
        // Same overlap accounting as the distance-bias tests: a non-slant (uniform 0-360°) roll can
        // still land inside the target arc purely by chance, proportional to the arc's own share of
        // the full circle.
        double expectedInArcPercent = slantChance + (100.0 - slantChance) * arcFraction;

        ServerLevel level = helper.getLevel();
        BlockPos beaconPos = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos riftDirectionPos = beaconPos.offset(100, 0, 0); // due "+X", angle = 0 exactly
        RandomSource random = RandomSource.create();

        int validCount = 0;
        int inArcCount = 0;
        double halfArcRadians = Math.toRadians(GTRiftConfig.INSTANCE.riftSlantArcDegrees) / 2.0;
        for (int i = 0; i < TRIALS; i++) {
            BlockPos pos = RiftEventSpawner.findSpawnPosition(level, beaconPos, random, riftDirectionPos);
            if (pos == null) continue;
            validCount++;

            double angle = Math.atan2(pos.getZ() - beaconPos.getZ(), pos.getX() - beaconPos.getX());
            if (Math.abs(normalizeAngle(angle)) <= halfArcRadians) {
                inArcCount++;
            }
        }

        helper.assertTrue(validCount > TRIALS / 2, "findSpawnPosition failed to resolve a position too often");

        double actualInArcPercent = inArcCount * 100.0 / validCount;
        // Generous tolerance, same reasoning as the distance-bias tests: the closest-Y candidate
        // selection can skew results away from a naive theoretical model.
        helper.assertTrue(Math.abs(actualInArcPercent - expectedInArcPercent) < TOLERANCE_PERCENT,
                "expected ~%.1f%% of spawns within the %d° slant arc, got %.1f%%"
                        .formatted(expectedInArcPercent, GTRiftConfig.INSTANCE.riftSlantArcDegrees, actualInArcPercent));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroSlantChanceProducesUniformAngles(GameTestHelper helper) {
        GTRiftConfig.INSTANCE.safeBufferDistance = 20;
        GTRiftConfig.INSTANCE.spawnRadius = 60;
        GTRiftConfig.INSTANCE.nearSpawnChancePercent = 0;
        GTRiftConfig.INSTANCE.riftSlantChancePercent = 0;
        GTRiftConfig.INSTANCE.riftSlantArcDegrees = 90;

        double arcFraction = GTRiftConfig.INSTANCE.riftSlantArcDegrees / 360.0;
        // With 0% slant chance, every roll is uniform 0-360°, so the arc should just reflect its own
        // share of the full circle — no bias contribution at all.
        double expectedInArcPercent = arcFraction * 100.0;

        ServerLevel level = helper.getLevel();
        BlockPos beaconPos = helper.absolutePos(new BlockPos(0, 1, 0));
        BlockPos riftDirectionPos = beaconPos.offset(100, 0, 0);
        RandomSource random = RandomSource.create();

        int validCount = 0;
        int inArcCount = 0;
        double halfArcRadians = Math.toRadians(GTRiftConfig.INSTANCE.riftSlantArcDegrees) / 2.0;
        for (int i = 0; i < TRIALS; i++) {
            BlockPos pos = RiftEventSpawner.findSpawnPosition(level, beaconPos, random, riftDirectionPos);
            if (pos == null) continue;
            validCount++;

            double angle = Math.atan2(pos.getZ() - beaconPos.getZ(), pos.getX() - beaconPos.getX());
            if (Math.abs(normalizeAngle(angle)) <= halfArcRadians) {
                inArcCount++;
            }
        }

        helper.assertTrue(validCount > TRIALS / 2, "findSpawnPosition failed to resolve a position too often");

        double actualInArcPercent = inArcCount * 100.0 / validCount;
        helper.assertTrue(Math.abs(actualInArcPercent - expectedInArcPercent) < TOLERANCE_PERCENT,
                "with riftSlantChancePercent=0, expected ~%.1f%% in the arc (its natural share of a full circle), got %.1f%%"
                        .formatted(expectedInArcPercent, actualInArcPercent));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nullRiftDirectionDisablesSlantEntirely(GameTestHelper helper) {
        GTRiftConfig.INSTANCE.safeBufferDistance = 20;
        GTRiftConfig.INSTANCE.spawnRadius = 60;
        GTRiftConfig.INSTANCE.nearSpawnChancePercent = 0;
        GTRiftConfig.INSTANCE.riftSlantChancePercent = 100; // would always slant if a direction existed
        GTRiftConfig.INSTANCE.riftSlantArcDegrees = 90;

        double arcFraction = GTRiftConfig.INSTANCE.riftSlantArcDegrees / 360.0;
        double expectedInArcPercent = arcFraction * 100.0;

        ServerLevel level = helper.getLevel();
        BlockPos beaconPos = helper.absolutePos(new BlockPos(0, 1, 0));
        RandomSource random = RandomSource.create();

        int validCount = 0;
        int inArcCount = 0;
        double halfArcRadians = Math.toRadians(GTRiftConfig.INSTANCE.riftSlantArcDegrees) / 2.0;
        for (int i = 0; i < TRIALS; i++) {
            // No rift direction known (e.g. no rift open yet) — must behave as fully uniform,
            // never slanting, regardless of riftSlantChancePercent.
            BlockPos pos = RiftEventSpawner.findSpawnPosition(level, beaconPos, random, null);
            if (pos == null) continue;
            validCount++;

            double angle = Math.atan2(pos.getZ() - beaconPos.getZ(), pos.getX() - beaconPos.getX());
            if (Math.abs(normalizeAngle(angle)) <= halfArcRadians) {
                inArcCount++;
            }
        }

        helper.assertTrue(validCount > TRIALS / 2, "findSpawnPosition failed to resolve a position too often");

        double actualInArcPercent = inArcCount * 100.0 / validCount;
        helper.assertTrue(Math.abs(actualInArcPercent - expectedInArcPercent) < TOLERANCE_PERCENT,
                "with riftDirectionPos=null, expected ~%.1f%% in the arc (uniform, no slant applied), got %.1f%%"
                        .formatted(expectedInArcPercent, actualInArcPercent));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pickSlantTargetAlwaysReturnsAMemberOfTheList(GameTestHelper helper) {
        List<BlockPos> columns = List.of(
                new BlockPos(10, 64, 10), new BlockPos(-10, 64, 10),
                new BlockPos(10, 64, -10), new BlockPos(-10, 64, -10));
        RandomSource random = RandomSource.create();

        Set<BlockPos> picked = new HashSet<>();
        for (int i = 0; i < TRIALS; i++) {
            BlockPos target = RiftEventSpawner.pickSlantTarget(random, columns);
            helper.assertTrue(columns.contains(target),
                    "pickSlantTarget returned %s, which isn't one of the given columns".formatted(target));
            picked.add(target);
        }

        // Guards against an off-by-one that always returns the same index — over this many trials,
        // every one of the 4 columns should show up at least once.
        helper.assertTrue(picked.size() > 1,
                "expected more than one distinct column to be picked over %d trials, only saw %s"
                        .formatted(TRIALS, picked));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void pickSlantTargetReturnsNullOnAnEmptyList(GameTestHelper helper) {
        BlockPos target = RiftEventSpawner.pickSlantTarget(RandomSource.create(), List.of());
        helper.assertTrue(target == null, "expected null for an empty column list, got %s".formatted(target));
        helper.succeed();
    }

    /** Wraps an angle difference to (-PI, PI] so arc-membership checks handle the wraparound boundary. */
    private static double normalizeAngle(double angle) {
        while (angle > Math.PI) angle -= 2 * Math.PI;
        while (angle < -Math.PI) angle += 2 * Math.PI;
        return angle;
    }
}
