package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.machine.BeaconState;
import com.pyure.gtrift.common.machine.RiftAmbienceTracker;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Pure-logic coverage only — mirrors RiftEliteTrackerTest's own documented gap: GameTest can't fake a
 * real connected ServerPlayer, so the tracker's actual per-tick Darkness/lightning application (which
 * needs real ServerLevel#players()) is interactive-only, verified per plans/ambience.md's Testing
 * section rather than here. This covers what's genuinely pure: computeRamp, isWithinRadius, and the
 * named threshold constants both of those rely on.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftAmbienceTrackerTest {

    private static final ResourceKey<Level> OVERWORLD = Level.OVERWORLD;
    private static final ResourceKey<Level> NETHER = Level.NETHER;

    @GameTest(template = "empty")
    public static void computeRampScalesWithStoredFractionDuringCharging(GameTestHelper helper) {
        helper.assertTrue(RiftAmbienceTracker.computeRamp(BeaconState.CHARGING, 0L, 1000L) == 0.0,
                "expected 0%% charge to ramp to 0.0");
        helper.assertTrue(RiftAmbienceTracker.computeRamp(BeaconState.CHARGING, 500L, 1000L) == 0.5,
                "expected 50%% charge to ramp to 0.5");
        helper.assertTrue(RiftAmbienceTracker.computeRamp(BeaconState.CHARGING, 1000L, 1000L) == 1.0,
                "expected 100%% charge to ramp to 1.0");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void computeRampHandlesZeroChargeTargetWithoutDividingByZero(GameTestHelper helper) {
        helper.assertTrue(RiftAmbienceTracker.computeRamp(BeaconState.CHARGING, 0L, 0L) == 0.0,
                "expected a zero charge target to ramp to 0.0, not NaN/infinity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void computeRampIsFullAtRiftOpenAndZeroForIdleAndCharged(GameTestHelper helper) {
        helper.assertTrue(RiftAmbienceTracker.computeRamp(BeaconState.RIFT_OPEN, 1L, 1000L) == 1.0,
                "expected RIFT_OPEN to always ramp to 1.0 regardless of remaining charge");
        helper.assertTrue(RiftAmbienceTracker.computeRamp(BeaconState.IDLE, 0L, 0L) == 0.0,
                "expected IDLE to ramp to 0.0");
        helper.assertTrue(RiftAmbienceTracker.computeRamp(BeaconState.CHARGED, 1000L, 1000L) == 0.0,
                "expected the instantaneous CHARGED pass-through state to ramp to 0.0");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void isWithinRadiusRejectsDifferentDimensionsRegardlessOfCoordinates(GameTestHelper helper) {
        BlockPos samePos = new BlockPos(100, 64, 100);
        boolean withinRadius = RiftAmbienceTracker.isWithinRadius(
                OVERWORLD, samePos, NETHER, samePos, 1000.0);
        helper.assertTrue(!withinRadius,
                "expected identical coordinates in different dimensions to never be \"within radius\"");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void isWithinRadiusComparesRealDistanceWithinTheSameDimension(GameTestHelper helper) {
        BlockPos center = new BlockPos(0, 64, 0);
        BlockPos near = new BlockPos(10, 64, 0);
        BlockPos far = new BlockPos(1000, 64, 0);

        helper.assertTrue(
                RiftAmbienceTracker.isWithinRadius(OVERWORLD, center, OVERWORLD, near, 60.0),
                "expected a position 10 blocks away to be within a 60-block radius");
        helper.assertTrue(
                !RiftAmbienceTracker.isWithinRadius(OVERWORLD, center, OVERWORLD, far, 60.0),
                "expected a position 1000 blocks away to be outside a 60-block radius");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void thresholdConstantsMatchTheDocumentedTuning(GameTestHelper helper) {
        helper.assertTrue(RiftAmbienceTracker.FADE_OUT_TICKS == 100, "expected a 5s (100-tick) fade-out");
        helper.assertTrue(RiftAmbienceTracker.DARKNESS_RAMP_THRESHOLD == 0.5, "expected Darkness to gate at 50%% ramp");
        helper.assertTrue(RiftAmbienceTracker.DARKNESS_DURATION_TICKS == 100, "expected a 5s (100-tick) Darkness dose");
        helper.assertTrue(RiftAmbienceTracker.DARKNESS_COOLDOWN_TICKS == 600, "expected a 30s (600-tick) Darkness cooldown");
        helper.assertTrue(RiftAmbienceTracker.LIGHTNING_SURGE_STRIKE_COUNT == 3, "expected a 3-bolt surge");
        helper.assertTrue(RiftAmbienceTracker.LIGHTNING_SURGE_INTERVAL_TICKS == 5, "expected surge bolts ~5 ticks apart");
        helper.assertTrue(RiftAmbienceTracker.LIGHTNING_BACKGROUND_CHANCE_DENOMINATOR == 400,
                "expected a 1/400 per-tick background lightning chance (~once per 20s)");
        helper.succeed();
    }
}
