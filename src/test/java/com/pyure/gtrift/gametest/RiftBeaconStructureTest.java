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
 * Requires a structure template at src/test/resources/data/gtrift/structures/rift_beacon.nbt — a
 * fully formed Rift Beacon (fixed ULV-casing wall, HV-casing tier ring — see
 * plans/beacon-structure-redesign.md for the full shape), with the controller at relative
 * position (1,1,0) — build it via the in-game structure-block workflow documented in
 * src/test/README.md before running this test.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftBeaconStructureTest {

    @GameTest(template = "rift_beacon", timeoutTicks = 200)
    public static void beaconFormsAtTierDetectedFromRing(GameTestHelper helper) {
        // Structure blocks place content starting one block above their own Y position, and the
        // rift_beacon.nbt capture has the controller at local [1,0,0] (not the structure's own
        // corner) — so relative (1,1,0) resolves to the controller's position.
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

        // Polls rather than assuming a fixed tick count is always enough — formation timing varies
        // with server load (observed: a fixed 20-tick delay elsewhere in this suite passed
        // reliably at 8 concurrently-running tests but flaked at 12).
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(beacon.isFormed(), "Rift Beacon did not form"))
                // Tier ring in rift_beacon.nbt is built from HV casings — the wall itself is always
                // ULV and no longer informs tier at all (see RiftBeaconTierPredicate).
                .thenExecute(() -> helper.assertTrue(beacon.tier == GTValues.HV,
                        "expected tier HV (%d), got %d".formatted(GTValues.HV, beacon.tier)))
                .thenSucceed();
    }
}
