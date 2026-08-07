package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.config.GTRiftConfig;
import com.pyure.gtrift.common.machine.RiftBeaconMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Pure logic against a real ServerLevel (needed for findSpawnPosition's heightmap query, same
 * template/reasoning as RiftSpawnPlacementTest) — covers RiftBeaconMachine.generateColumnPositions,
 * the multi-column replacement for the old single-anchor riftVisualPos roll (see
 * plans/rift-multi-column.md Phase 1).
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftColumnGenerationTest {

    @GameTest(template = "empty")
    public static void generatesExactlyThirtyPositionsOnTheHappyPath(GameTestHelper helper) {
        GTRiftConfig.INSTANCE.safeBufferDistance = 20;
        GTRiftConfig.INSTANCE.spawnRadius = 60;

        ServerLevel level = helper.getLevel();
        BlockPos beaconPos = helper.absolutePos(new BlockPos(0, 1, 0));
        RandomSource random = RandomSource.create();

        List<BlockPos> positions = RiftBeaconMachine.generateColumnPositions(level, beaconPos, random);

        helper.assertTrue(positions.size() == 30,
                "expected exactly 30 column positions, got %d".formatted(positions.size()));

        helper.succeed();
    }

    /**
     * Forces every RiftEventSpawner.findSpawnPosition roll to fail (radius <= buffer returns null
     * immediately, per its own documented guard) and confirms generateColumnPositions still returns
     * a full 30-entry list, with every entry falling back to beaconPos individually rather than the
     * list coming back shorter or empty.
     */
    @GameTest(template = "empty")
    public static void fallsBackToBeaconPosForEveryColumnWhenNoValidPositionExists(GameTestHelper helper) {
        GTRiftConfig.INSTANCE.safeBufferDistance = 20;
        GTRiftConfig.INSTANCE.spawnRadius = 20; // radius <= buffer: findSpawnPosition always returns null

        ServerLevel level = helper.getLevel();
        BlockPos beaconPos = helper.absolutePos(new BlockPos(0, 1, 0));
        RandomSource random = RandomSource.create();

        List<BlockPos> positions = RiftBeaconMachine.generateColumnPositions(level, beaconPos, random);

        helper.assertTrue(positions.size() == 30,
                "expected exactly 30 column positions even on the all-null-rolls path, got %d".formatted(positions.size()));
        for (BlockPos pos : positions) {
            helper.assertTrue(pos.equals(beaconPos),
                    "expected every column to fall back to beaconPos %s, found %s".formatted(beaconPos, pos));
        }

        helper.succeed();
    }
}
