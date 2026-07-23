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

/**
 * Confirms riftVisualPos (the Phase 8 visual anchor, made @Persisted after a reload-mid-rift bug)
 * survives a save/load round-trip, using BlockEntity's own saveWithoutMetadata()/load() — the same
 * vanilla mechanism a real world reload goes through — rather than an actual close-and-reopen
 * cycle, which isn't practical within a single GameTest run.
 *
 * Best-effort: MetaMachineBlockEntity overrides load() but no save-side method is directly visible
 * on it (persistence may route partly through GTCEu's capability-attachment NBT mechanism, per
 * "ForgeCaps" appearing in captured structure NBT elsewhere in this project). If this test passes,
 * that's good evidence the field round-trips correctly; a real in-game save-and-reload mid-rift is
 * still the definitive check for the actual reported bug.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftBeaconVisualPersistenceTest {

    @GameTest(template = "rift_beacon_lv", timeoutTicks = 200)
    public static void riftVisualPosSurvivesSaveLoadRoundTrip(GameTestHelper helper) {
        BlockEntity holder = helper.getBlockEntity(new BlockPos(1, 2, 0));
        if (!(holder instanceof MetaMachineBlockEntity metaMachineBlockEntity)) {
            helper.fail("wrong block at relative pos [1,2,0]!");
            return;
        }
        MetaMachine machine = metaMachineBlockEntity.getMetaMachine();
        if (!(machine instanceof RiftBeaconMachine beacon)) {
            helper.fail("wrong machine in MetaMachineBlockEntity!");
            return;
        }

        BlockPos testPos = beacon.getPos().offset(5, 0, 7);
        beacon.riftVisualPos = testPos;

        CompoundTag saved = metaMachineBlockEntity.saveWithoutMetadata();
        metaMachineBlockEntity.load(saved);

        // Re-fetch rather than reuse the old `beacon` reference — load() may replace the wrapped
        // MetaMachine instance rather than mutate it in place; re-fetching is correct either way.
        MetaMachine reloadedMachine = metaMachineBlockEntity.getMetaMachine();
        helper.assertTrue(reloadedMachine instanceof RiftBeaconMachine, "machine type changed after reload!");
        RiftBeaconMachine reloadedBeacon = (RiftBeaconMachine) reloadedMachine;

        helper.assertTrue(testPos.equals(reloadedBeacon.riftVisualPos),
                "riftVisualPos did not survive the save/load round-trip (was %s, now %s)"
                        .formatted(testPos, reloadedBeacon.riftVisualPos));

        helper.succeed();
    }
}
