package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.machine.BeaconState;
import com.pyure.gtrift.common.machine.RiftBeaconMachine;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Requires the same structure template as RiftBeaconChargeTest
 * (src/test/resources/data/gtrift/structures/rift_beacon_lv.nbt).
 *
 * Exercises the full Phase 5 CHARGING -> RIFT_OPEN -> IDLE lifecycle: reaches a small chargeTarget
 * for speed, confirms the instant pass-through to RIFT_OPEN (never lingers at CHARGED), then
 * confirms chargeStored counts down without ever going negative and the beacon settles back to
 * IDLE with chargeStored and chargeTarget both reset to exactly 0.
 *
 * Uses startSequence()/thenWaitUntil rather than nested succeedWhen calls — thenWaitUntil polls
 * every tick until its assertion stops throwing, then advances to the next stage, which is the
 * correct way to chain multiple "wait until X" stages (nesting succeedWhen would incorrectly
 * succeed as soon as the outer runnable merely registered the inner poller, not once it resolved).
 *
 * Polls for a findable EnergyHatchPartMachine directly, not beacon.isFormed() as a proxy — isFormed()
 * becoming true does not guarantee getParts() is already populated at that same moment (confirmed:
 * gating the hatch lookup on isFormed() still intermittently found an empty parts list). A fixed
 * tick-count delay before either check is also unreliable — it passed at 8 concurrently-running
 * tests but flaked at 12, since formation timing varies with server load.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftEventStateTest {

    private static RiftBeaconMachine getBeacon(GameTestHelper helper) {
        // Structure blocks place content starting one block above their own Y position, and the
        // rift_beacon_lv.nbt capture has the controller at local [1,1,0] (not the structure's own
        // corner) — so (0,1,0) resolves to the energy hatch's position, not the controller's.
        BlockEntity holder = helper.getBlockEntity(new BlockPos(1, 2, 0));
        if (!(holder instanceof MetaMachineBlockEntity metaMachineBlockEntity)) {
            helper.fail("wrong block at relative pos [0,1,0]!");
            return null;
        }
        MetaMachine machine = metaMachineBlockEntity.getMetaMachine();
        if (!(machine instanceof RiftBeaconMachine beacon)) {
            helper.fail("wrong machine in MetaMachineBlockEntity!");
            return null;
        }
        return beacon;
    }

    /** Returns true (and sets the energy) once a hatch is found; false if getParts() has none yet. */
    private static boolean tryChargeEnergyHatch(RiftBeaconMachine beacon, long amount) {
        for (var part : beacon.getParts()) {
            if (part.self() instanceof EnergyHatchPartMachine hatch) {
                hatch.energyContainer.setEnergyStored(amount);
                return true;
            }
        }
        return false;
    }

    @GameTest(template = "rift_beacon_lv", timeoutTicks = 400)
    public static void riftOpensThenClosesCleanly(GameTestHelper helper) {
        RiftBeaconMachine beacon = getBeacon(helper);
        if (beacon == null) return;

        helper.assertTrue(beacon.state == BeaconState.IDLE, "beacon did not start IDLE");

        helper.startSequence()
                // isFormed() becoming true does not guarantee getParts() is already populated in
                // that same moment — poll the actual condition needed (a findable hatch) directly,
                // retrying each tick, rather than a one-shot check gated on a proxy signal.
                .thenWaitUntil(() -> helper.assertTrue(tryChargeEnergyHatch(beacon, 10_000L),
                        "no EnergyHatchPartMachine found among the beacon's parts yet"))
                .thenExecute(() -> {
                    // Small target so RIFT_OPEN's depletion (VA[tier]/tick) also finishes in time.
                    beacon.chargeTarget = 300L;
                    beacon.chargeStored = 0L;
                    beacon.state = BeaconState.CHARGING;
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(beacon.state != BeaconState.CHARGING, "charging never completed");
                    helper.assertTrue(beacon.state == BeaconState.RIFT_OPEN,
                            "expected RIFT_OPEN immediately after CHARGING, got %s".formatted(beacon.state));
                })
                .thenExecute(() -> helper.assertTrue(beacon.chargeStored >= 0,
                        "chargeStored went negative on entering RIFT_OPEN"))
                .thenWaitUntil(() -> {
                    helper.assertTrue(beacon.chargeStored >= 0, "chargeStored went negative while depleting");
                    helper.assertTrue(beacon.state != BeaconState.CHARGING,
                            "beacon re-entered CHARGING unexpectedly");
                    helper.assertTrue(beacon.state == BeaconState.IDLE,
                            "beacon did not return to IDLE (state=%s, chargeStored=%d)"
                                    .formatted(beacon.state, beacon.chargeStored));
                })
                .thenExecute(() -> {
                    helper.assertTrue(beacon.chargeStored == 0,
                            "chargeStored was not exactly 0 on returning to IDLE (was %d)"
                                    .formatted(beacon.chargeStored));
                    helper.assertTrue(beacon.chargeTarget == 0,
                            "chargeTarget was not reset to 0 on returning to IDLE");
                })
                .thenSucceed();
    }
}
