package com.pyure.gtrift.common.data;

import com.pyure.gtrift.common.item.RiftAffinity;
import com.pyure.gtrift.common.item.RiftRichness;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/**
 * Both weight tables are linearly interpolated per-category between a ULV baseline and a UHV
 * endpoint: weight(tier) = baseline + (endpoint - baseline) * (tier / 9.0). Both sets sum to 100 at
 * both endpoints (Ferrous+Conductive+Precious = 50+35+15 = 30+30+40 = 100; Sparse+Normal+Rich+
 * ExtremelyRich = 60+30+8+2 = 10+30+40+20 = 100), so linear interpolation keeps the sum at exactly
 * 100 at every intermediate tier too — no renormalization needed.
 */
public class RiftLootTable {

    private static final double[] AFFINITY_ULV = {50, 35, 15};
    private static final double[] AFFINITY_UHV = {30, 30, 40};

    private static final double[] RICHNESS_ULV = {60, 30, 8, 2};
    private static final double[] RICHNESS_UHV = {10, 30, 40, 20};

    private RiftLootTable() {}

    public static RiftAffinity rollAffinity(int difficultyTier, RandomSource random) {
        double[] weights = interpolate(AFFINITY_ULV, AFFINITY_UHV, difficultyTier);
        return RiftAffinity.values()[weightedPick(weights, random)];
    }

    public static RiftRichness rollRichness(int difficultyTier, RandomSource random) {
        double[] weights = interpolate(RICHNESS_ULV, RICHNESS_UHV, difficultyTier);
        return RiftRichness.values()[weightedPick(weights, random)];
    }

    /** Rolls rollRichness twice and returns the better (higher-ordinal) result. */
    public static RiftRichness rollRichnessForElite(int difficultyTier, RandomSource random) {
        RiftRichness first = rollRichness(difficultyTier, random);
        RiftRichness second = rollRichness(difficultyTier, random);
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    private static double[] interpolate(double[] baseline, double[] endpoint, int difficultyTier) {
        double t = Mth.clamp(difficultyTier, GTValues.ULV, GTValues.UHV) / (double) GTValues.UHV;
        double[] weights = new double[baseline.length];
        for (int i = 0; i < baseline.length; i++) {
            weights[i] = baseline[i] + (endpoint[i] - baseline[i]) * t;
        }
        return weights;
    }

    private static int weightedPick(double[] weights, RandomSource random) {
        double total = 0;
        for (double weight : weights) total += weight;
        double roll = random.nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) return i;
        }
        return weights.length - 1;
    }
}
