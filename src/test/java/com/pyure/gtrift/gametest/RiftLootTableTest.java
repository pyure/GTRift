package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.RiftLootTable;
import com.pyure.gtrift.common.item.RiftAffinity;
import com.pyure.gtrift.common.item.RiftRichness;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Pure logic — no world state needed, template = "empty". Statistical checks with a large sample
 * and a generous tolerance band, not exact-value assertions (these are weighted-random rolls).
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftLootTableTest {

    private static final int TRIALS = 100_000;
    private static final double TOLERANCE_PERCENT = 3.0;

    private static void assertProportions(GameTestHelper helper, int[] counts, double[] expectedPercent,
                                            String label) {
        for (int i = 0; i < counts.length; i++) {
            double actualPercent = counts[i] * 100.0 / TRIALS;
            helper.assertTrue(Math.abs(actualPercent - expectedPercent[i]) < TOLERANCE_PERCENT,
                    "%s[%d]: expected ~%.1f%%, got %.1f%%".formatted(label, i, expectedPercent[i], actualPercent));
        }
    }

    @GameTest(template = "empty")
    public static void affinityWeightsTrendCorrectly(GameTestHelper helper) {
        RandomSource random = RandomSource.create();

        int[] ulvCounts = new int[RiftAffinity.values().length];
        for (int i = 0; i < TRIALS; i++) {
            ulvCounts[RiftLootTable.rollAffinity(GTValues.ULV, random).ordinal()]++;
        }
        assertProportions(helper, ulvCounts, new double[] {50, 35, 15}, "ULV affinity");

        int[] uhvCounts = new int[RiftAffinity.values().length];
        for (int i = 0; i < TRIALS; i++) {
            uhvCounts[RiftLootTable.rollAffinity(GTValues.UHV, random).ordinal()]++;
        }
        assertProportions(helper, uhvCounts, new double[] {30, 30, 40}, "UHV affinity");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void richnessWeightsTrendCorrectly(GameTestHelper helper) {
        RandomSource random = RandomSource.create();

        int[] ulvCounts = new int[RiftRichness.values().length];
        for (int i = 0; i < TRIALS; i++) {
            ulvCounts[RiftLootTable.rollRichness(GTValues.ULV, random).ordinal()]++;
        }
        assertProportions(helper, ulvCounts, new double[] {60, 30, 8, 2}, "ULV richness");

        int[] uhvCounts = new int[RiftRichness.values().length];
        for (int i = 0; i < TRIALS; i++) {
            uhvCounts[RiftLootTable.rollRichness(GTValues.UHV, random).ordinal()]++;
        }
        assertProportions(helper, uhvCounts, new double[] {10, 30, 40, 20}, "UHV richness");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eliteRichnessSkewsHigherThanNormal(GameTestHelper helper) {
        RandomSource random = RandomSource.create();

        long normalOrdinalSum = 0;
        long eliteOrdinalSum = 0;
        for (int i = 0; i < TRIALS; i++) {
            normalOrdinalSum += RiftLootTable.rollRichness(GTValues.LV, random).ordinal();
            eliteOrdinalSum += RiftLootTable.rollRichnessForElite(GTValues.LV, random).ordinal();
        }

        double normalAverage = normalOrdinalSum / (double) TRIALS;
        double eliteAverage = eliteOrdinalSum / (double) TRIALS;
        helper.assertTrue(eliteAverage > normalAverage,
                "expected elite richness average (%.3f) to exceed normal richness average (%.3f)"
                        .formatted(eliteAverage, normalAverage));

        helper.succeed();
    }
}
