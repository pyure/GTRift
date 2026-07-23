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
 * Requires the same structure template as RiftBeaconStructureTest
 * (src/test/resources/data/gtrift/structures/rift_beacon_lv.nbt). Does NOT depend on the captured
 * hatch already holding charge — the captured snapshot's energyStored is whatever it happened to
 * be at save time (confirmed to be 0 in practice), which would make charging tests depend on
 * re-capturing the structure at exactly the right moment. Instead, chargeEnergyHatch() sets the
 * hatch's stored energy directly via code, the same way the rest of this test manipulates beacon
 * state directly rather than relying on captured world state.
 *
 * Mutates public fields directly to simulate pressing Accept, rather than calling the private
 * tryAccept()/beaconTick() — the actual per-tick charging is exercised for real by waiting on
 * real game ticks, since beaconTick() is already subscribed automatically once the structure is
 * formed.
 *
 * Updated for Phase 5: CHARGED is now an instant same-tick pass-through into RIFT_OPEN (see
 * RiftEventStateTest for that transition specifically) — this test now expects RIFT_OPEN as the
 * terminal state reached, not CHARGED, since a real tick will always carry it straight through.
 *
 * Uses startSequence()/thenWaitUntil throughout (per-tick polling) rather than fixed-delay
 * checkpoints: a fixed "wait N ticks, assume formed/charged/transitioned by then" delay is
 * inherently racy under variable server load — confirmed in practice when a 20-tick formation
 * delay that passed reliably at 8 concurrent tests started intermittently failing at 12 (a
 * sibling test using the exact same structure file, under the same batch run). Polling for the
 * actual condition (isFormed(), chargeStored > 0, state transitions) has no such race.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftBeaconChargeTest {

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
    public static void chargingReachesTargetWithoutOvershooting(GameTestHelper helper) {
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
                    // Simulate pressing Accept with a small target so the test finishes quickly.
                    beacon.chargeTarget = 1000L;
                    beacon.chargeStored = 0L;
                    beacon.state = BeaconState.CHARGING;
                })
                .thenWaitUntil(() -> helper.assertTrue(beacon.chargeStored > 0,
                        "chargeStored did not increase after real ticks passed"))
                .thenExecute(() -> helper.assertTrue(beacon.chargeStored <= beacon.chargeTarget,
                        "chargeStored overshot chargeTarget"))
                .thenWaitUntil(() -> {
                    helper.assertTrue(beacon.state != BeaconState.CHARGING,
                            "beacon is still CHARGING after a long wait — charging never completed");
                    // Caught the same tick CHARGING ends, before RIFT_OPEN's depletion can reduce
                    // chargeStored — so this reliably observes RIFT_OPEN rather than racing past it.
                    helper.assertTrue(beacon.state == BeaconState.RIFT_OPEN,
                            "expected RIFT_OPEN immediately after CHARGING, got %s".formatted(beacon.state));
                })
                .thenSucceed();
    }
}
