package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.machine.RiftBeaconMachine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Confirms columnPositions (the multi-column rift visual/spawn-bias anchor list, replacing the old
 * single riftVisualPos — see plans/rift-multi-column.md Phase 1) survives a save/load round-trip,
 * using BlockEntity's own saveWithoutMetadata()/load() — the same vanilla mechanism a real world
 * reload goes through — rather than an actual close-and-reopen cycle, which isn't practical within a
 * single GameTest run.
 *
 * Best-effort: MetaMachineBlockEntity overrides load() but no save-side method is directly visible
 * on it (persistence may route partly through GTCEu's capability-attachment NBT mechanism, per
 * "ForgeCaps" appearing in captured structure NBT elsewhere in this project). If this test passes,
 * that's good evidence the field round-trips correctly; a real in-game save-and-reload mid-rift is
 * still the definitive check.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftBeaconColumnPersistenceTest {

    @GameTest(template = "rift_beacon", timeoutTicks = 200)
    public static void columnPositionsSurviveSaveLoadRoundTrip(GameTestHelper helper) {
        BlockEntity holder = helper.getBlockEntity(new BlockPos(1, 1, 0));
        if (!(holder instanceof MetaMachineBlockEntity metaMachineBlockEntity)) {
            helper.fail("wrong block at relative pos [1,1,0]!");
            return;
        }
        MetaMachine machine = metaMachineBlockEntity.getMetaMachine();
        if (!(machine instanceof RiftBeaconMachine beacon)) {
            helper.fail("wrong machine in MetaMachineBlockEntity!");
            return;
        }

        List<BlockPos> testPositions = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            testPositions.add(beacon.getPos().offset(i, 0, i * 2));
        }
        beacon.columnPositions.clear();
        beacon.columnPositions.addAll(testPositions);

        CompoundTag saved = metaMachineBlockEntity.saveWithoutMetadata();
        metaMachineBlockEntity.load(saved);

        // Re-fetch rather than reuse the old `beacon` reference — load() may replace the wrapped
        // MetaMachine instance rather than mutate it in place; re-fetching is correct either way.
        MetaMachine reloadedMachine = metaMachineBlockEntity.getMetaMachine();
        helper.assertTrue(reloadedMachine instanceof RiftBeaconMachine, "machine type changed after reload!");
        RiftBeaconMachine reloadedBeacon = (RiftBeaconMachine) reloadedMachine;

        helper.assertTrue(testPositions.equals(reloadedBeacon.columnPositions),
                "columnPositions did not survive the save/load round-trip (was %s, now %s)"
                        .formatted(testPositions, reloadedBeacon.columnPositions));

        helper.succeed();
    }
}
