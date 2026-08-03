package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.client.ClientAmbienceState;
import com.pyure.gtrift.common.network.AmbienceSyncPacket;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Covers the two genuinely pure pieces of Phase 2: AmbienceSyncPacket's encode/decode round trip
 * (no real network connection needed) and ClientAmbienceState's map logic (no Minecraft-client-only
 * imports, so it's safe to exercise directly here). The actual rendering/fog output and real
 * client-server packet delivery are interactive-only, per plans/ambience.md's Testing section — same
 * documented gap as everywhere else real players/rendering are involved.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class AmbienceNetworkingTest {

    @GameTest(template = "empty")
    public static void ambienceSyncPacketRoundTripsAnActiveUpdate(GameTestHelper helper) {
        AmbienceSyncPacket original = new AmbienceSyncPacket(new BlockPos(12, 64, -34), 0.75f, true, true);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);
        AmbienceSyncPacket decoded = new AmbienceSyncPacket(buf);

        helper.assertTrue(decoded.beaconPos().equals(original.beaconPos()), "expected beaconPos to survive the round trip");
        helper.assertTrue(decoded.ramp() == original.ramp(), "expected ramp to survive the round trip");
        helper.assertTrue(decoded.active() == original.active(), "expected active to survive the round trip");
        helper.assertTrue(decoded.playMusic() == original.playMusic(), "expected playMusic to survive the round trip");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ambienceSyncPacketRoundTripsAClearUpdate(GameTestHelper helper) {
        AmbienceSyncPacket original = new AmbienceSyncPacket(new BlockPos(0, 0, 0), 0f, false, false);

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        original.encode(buf);
        AmbienceSyncPacket decoded = new AmbienceSyncPacket(buf);

        helper.assertTrue(!decoded.active(), "expected a clear packet's active flag to survive as false");
        helper.assertTrue(!decoded.playMusic(), "expected a clear packet's playMusic to survive as false");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clientAmbienceStateReflectsTheMaxAcrossMultipleBeacons(GameTestHelper helper) {
        ClientAmbienceState.clear();
        BlockPos beaconA = new BlockPos(1, 64, 1);
        BlockPos beaconB = new BlockPos(2, 64, 2);

        ClientAmbienceState.put(beaconA, 0.3f, false);
        ClientAmbienceState.put(beaconB, 0.9f, false);
        helper.assertTrue(ClientAmbienceState.effectiveRamp() == 0.9f,
                "expected effectiveRamp to reflect the higher of two active beacons");

        ClientAmbienceState.remove(beaconB);
        helper.assertTrue(ClientAmbienceState.effectiveRamp() == 0.3f,
                "expected effectiveRamp to fall back to the remaining beacon once the higher one is removed");

        ClientAmbienceState.clear();
        helper.assertTrue(ClientAmbienceState.effectiveRamp() == 0f,
                "expected effectiveRamp to be 0 once every beacon is cleared");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clientAmbienceStateEntriesSelfExpireWithoutRefresh(GameTestHelper helper) {
        ClientAmbienceState.clear();
        ClientAmbienceState.put(new BlockPos(5, 64, 5), 1.0f, false);

        for (int i = 0; i < 29; i++) {
            ClientAmbienceState.tick(false);
        }
        helper.assertTrue(ClientAmbienceState.trackedCount() == 1,
                "expected the entry to still be tracked just before its 30-tick expiry");

        ClientAmbienceState.tick(false);
        helper.assertTrue(ClientAmbienceState.trackedCount() == 0,
                "expected the entry to self-expire once 30 ticks pass without a refresh");

        ClientAmbienceState.clear();
        helper.succeed();
    }

    // Regression test for a real playtesting find: a singleplayer pause keeps Forge's client tick
    // firing (so the expiry countdown would keep counting down) while the paused integrated server
    // stops sending refresh packets — without the isPaused guard, valid ambience state would wrongly
    // self-expire after a few seconds of pause.
    @GameTest(template = "empty")
    public static void clientAmbienceStateDoesNotExpireWhilePaused(GameTestHelper helper) {
        ClientAmbienceState.clear();
        ClientAmbienceState.put(new BlockPos(5, 64, 5), 1.0f, false);

        for (int i = 0; i < 100; i++) {
            ClientAmbienceState.tick(true);
        }
        helper.assertTrue(ClientAmbienceState.trackedCount() == 1,
                "expected an entry to survive well past its normal 30-tick expiry while paused");

        for (int i = 0; i < 30; i++) {
            ClientAmbienceState.tick(false);
        }
        helper.assertTrue(ClientAmbienceState.trackedCount() == 0,
                "expected normal expiry to resume once unpaused");

        ClientAmbienceState.clear();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smoothedRampGraduallyApproachesTheRawTarget(GameTestHelper helper) {
        ClientAmbienceState.clear();
        BlockPos pos = new BlockPos(9, 64, 9);
        ClientAmbienceState.put(pos, 1.0f, false);

        ClientAmbienceState.tick(false);
        float afterOneTick = ClientAmbienceState.smoothedRamp();
        helper.assertTrue(afterOneTick > 0f && afterOneTick < 1f,
                "expected smoothedRamp to move partway toward the target on the first tick, not jump instantly: got %f"
                        .formatted(afterOneTick));

        // Refresh every 10 ticks, matching the real server's send cadence, so the entry doesn't hit
        // its own 30-tick expiry mid-loop — a real client would keep receiving updates the whole time
        // an event is genuinely still active.
        for (int i = 0; i < 40; i++) {
            if (i % 10 == 0) ClientAmbienceState.put(pos, 1.0f, false);
            ClientAmbienceState.tick(false);
        }
        helper.assertTrue(Math.abs(ClientAmbienceState.smoothedRamp() - 1.0f) < 0.01f,
                "expected smoothedRamp to have converged on the target after many ticks");

        ClientAmbienceState.clear();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void musicSourceBeaconClaimsTheSlotOnFirstPlayMusicTrue(GameTestHelper helper) {
        ClientAmbienceState.clear();
        BlockPos beacon = new BlockPos(3, 64, 3);

        helper.assertTrue(ClientAmbienceState.musicSourceBeacon() == null,
                "expected no music source before any playMusic=true update");

        ClientAmbienceState.put(beacon, 1.0f, true);
        helper.assertTrue(beacon.equals(ClientAmbienceState.musicSourceBeacon()),
                "expected the beacon to claim the music slot on its first playMusic=true update");
        helper.assertTrue(ClientAmbienceState.musicSourceRamp() == 1.0f,
                "expected musicSourceRamp to reflect the claiming beacon's ramp");

        ClientAmbienceState.clear();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void musicSourceBeaconIsFirstWinsAgainstASecondClaimant(GameTestHelper helper) {
        ClientAmbienceState.clear();
        BlockPos first = new BlockPos(1, 64, 1);
        BlockPos second = new BlockPos(2, 64, 2);

        ClientAmbienceState.put(first, 1.0f, true);
        ClientAmbienceState.put(second, 1.0f, true);
        helper.assertTrue(first.equals(ClientAmbienceState.musicSourceBeacon()),
                "expected the first beacon to keep the music slot despite a second claimant");

        ClientAmbienceState.clear();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void musicSourceBeaconReleasesAsSoonAsItsOwnPlayMusicGoesFalse(GameTestHelper helper) {
        ClientAmbienceState.clear();
        BlockPos beacon = new BlockPos(4, 64, 4);

        ClientAmbienceState.put(beacon, 1.0f, true);
        helper.assertTrue(beacon.equals(ClientAmbienceState.musicSourceBeacon()),
                "expected the beacon to hold the music slot after a playMusic=true update");

        // Simulates a player walking out of the encounter radius while the beacon is still fully
        // RIFT_OPEN: RiftAmbienceTracker sends playMusic=false immediately (it's gated per-player on
        // encounter radius, independent of the beacon's own ramp/fade state) — confirmed via real
        // playtesting that the slot must release right here, not linger until an explicit remove(),
        // otherwise music kept playing until the player left the much larger backdrop radius instead.
        ClientAmbienceState.put(beacon, 1.0f, false);
        helper.assertTrue(ClientAmbienceState.musicSourceBeacon() == null,
                "expected the music slot to release as soon as its own playMusic update goes false");

        ClientAmbienceState.clear();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void musicSourceBeaconCanReclaimTheSlotAfterReleasingIt(GameTestHelper helper) {
        ClientAmbienceState.clear();
        BlockPos beacon = new BlockPos(4, 64, 4);

        // Walk out (releases), then walk back in (reclaims) — the same beacon, not a different one.
        ClientAmbienceState.put(beacon, 1.0f, true);
        ClientAmbienceState.put(beacon, 1.0f, false);
        ClientAmbienceState.put(beacon, 1.0f, true);
        helper.assertTrue(beacon.equals(ClientAmbienceState.musicSourceBeacon()),
                "expected the same beacon to be able to reclaim the music slot after releasing it");

        ClientAmbienceState.clear();
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void musicSourceBeaconSlotFreesUpOnRemoval(GameTestHelper helper) {
        ClientAmbienceState.clear();
        BlockPos first = new BlockPos(1, 64, 1);
        BlockPos second = new BlockPos(2, 64, 2);

        ClientAmbienceState.put(first, 1.0f, true);
        ClientAmbienceState.remove(first);
        helper.assertTrue(ClientAmbienceState.musicSourceBeacon() == null,
                "expected the music slot to free up once its holder is removed");

        ClientAmbienceState.put(second, 1.0f, true);
        helper.assertTrue(second.equals(ClientAmbienceState.musicSourceBeacon()),
                "expected a new beacon to be able to claim the now-free music slot");

        ClientAmbienceState.clear();
        helper.succeed();
    }
}
