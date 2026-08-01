package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.RiftDropEntry;
import com.pyure.gtrift.common.item.RiftQuality;
import com.pyure.gtrift.common.machine.RiftEventSpawner;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Pure logic — no world state needed, template = "empty", same pattern as RiftSpawnPlacementTest.
 *
 * Exercises RiftEventSpawner.filterEligibleDrops directly rather than going through the full
 * RiftMobPool + trySpawnMob(...) spawn pipeline: an earlier version of this test swapped custom
 * entries into the global RiftMobPool.NORMAL/ELITE singletons and called trySpawnMob, which leaked
 * stray (occasionally elite) mobs into other concurrently-running tests that tick a real beacon
 * through RIFT_OPEN and therefore also read those same global pools (e.g. RiftEventStateTest) — those
 * strays were never cleaned up by the other test's own scenario, permanently corrupting
 * RiftEliteTracker's shared tracked-entity count and intermittently failing RiftEliteTrackerTest.
 * filterEligibleDrops has no dependency on RiftMobPool or entity spawning at all, so it can't leak.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftEventSpawnerDropTest {

    @GameTest(template = "empty")
    public static void onlyDropsAtOrBelowDifficultyTierSurvive(GameTestHelper helper) {
        RiftDropEntry ulvDrop = new RiftDropEntry(
                "ferrous", RiftQuality.NORMAL, GTValues.ULV, 1.0, 1, 1, 1.0, 1.0);
        RiftDropEntry lvDrop = new RiftDropEntry(
                "conductive", RiftQuality.NORMAL, GTValues.LV, 1.0, 1, 1, 1.0, 1.0);
        RiftDropEntry mvGatedDrop = new RiftDropEntry(
                "precious", RiftQuality.RICH, GTValues.MV, 1.0, 1, 1, 1.0, 1.0);
        List<RiftDropEntry> drops = List.of(ulvDrop, lvDrop, mvGatedDrop);

        List<RiftDropEntry> atUlv = RiftEventSpawner.filterEligibleDrops(drops, GTValues.ULV);
        helper.assertTrue(atUlv.equals(List.of(ulvDrop)),
                "expected only the ULV-gated drop to survive an ULV-difficulty spawn, got %s".formatted(atUlv));

        List<RiftDropEntry> atLv = RiftEventSpawner.filterEligibleDrops(drops, GTValues.LV);
        helper.assertTrue(atLv.equals(List.of(ulvDrop, lvDrop)),
                "expected the ULV- and LV-gated drops to survive an LV-difficulty spawn, got %s".formatted(atLv));

        List<RiftDropEntry> atMv = RiftEventSpawner.filterEligibleDrops(drops, GTValues.MV);
        helper.assertTrue(atMv.equals(drops),
                "expected every drop to survive an MV-difficulty spawn (all gates satisfied), got %s".formatted(atMv));

        helper.succeed();
    }
}
