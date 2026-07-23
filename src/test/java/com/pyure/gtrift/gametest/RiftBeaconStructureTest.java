package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.machine.RiftBeaconMachine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Requires a structure template at src/test/resources/data/gtrift/structures/rift_beacon_lv.nbt —
 * a fully formed Rift Beacon built with LV machine casings, with the controller at relative
 * position (0,1,0) — build it via the in-game structure-block workflow documented in
 * src/test/README.md before running this test.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftBeaconStructureTest {

    @GameTest(template = "rift_beacon_lv", timeoutTicks = 200)
    public static void beaconFormsAtLVTier(GameTestHelper helper) {
        // Structure blocks place content starting one block above their own Y position, and the
        // rift_beacon_lv.nbt capture has the controller at local [1,1,0] (not the structure's own
        // corner) — so (0,1,0) resolves to the energy hatch's position, not the controller's.
        BlockEntity holder = helper.getBlockEntity(new BlockPos(1, 2, 0));
        if (!(holder instanceof MetaMachineBlockEntity metaMachineBlockEntity)) {
            helper.fail("wrong block at relative pos [0,1,0]!");
            return;
        }
        MetaMachine machine = metaMachineBlockEntity.getMetaMachine();
        if (!(machine instanceof RiftBeaconMachine beacon)) {
            helper.fail("wrong machine in MetaMachineBlockEntity!");
            return;
        }

        // Polls rather than assuming a fixed tick count is always enough — formation timing varies
        // with server load (observed: a fixed 20-tick delay elsewhere in this suite passed
        // reliably at 8 concurrently-running tests but flaked at 12).
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(beacon.isFormed(), "Rift Beacon did not form"))
                .thenExecute(() -> helper.assertTrue(beacon.tier == GTValues.LV,
                        "expected tier LV (%d), got %d".formatted(GTValues.LV, beacon.tier)))
                .thenSucceed();
    }
}
