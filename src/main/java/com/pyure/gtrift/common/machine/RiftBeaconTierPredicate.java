package com.pyure.gtrift.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.common.data.GTBlocks;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Matches any GTCEu tiered machine casing (ULV-UHV) and records which tier was used in the
 * match context under "RiftBeaconTier". Rejects the match if a different tier casing was
 * already recorded for this structure — all casings in one beacon must share a tier.
 */
public class RiftBeaconTierPredicate {

    public static final String MATCH_CONTEXT_KEY = "RiftBeaconTier";

    private static final Map<Block, Integer> TIER_BY_CASING = new LinkedHashMap<>();
    static {
        TIER_BY_CASING.put(GTBlocks.MACHINE_CASING_ULV.get(), GTValues.ULV);
        TIER_BY_CASING.put(GTBlocks.MACHINE_CASING_LV.get(), GTValues.LV);
        TIER_BY_CASING.put(GTBlocks.MACHINE_CASING_MV.get(), GTValues.MV);
        TIER_BY_CASING.put(GTBlocks.MACHINE_CASING_HV.get(), GTValues.HV);
        TIER_BY_CASING.put(GTBlocks.MACHINE_CASING_EV.get(), GTValues.EV);
        TIER_BY_CASING.put(GTBlocks.MACHINE_CASING_IV.get(), GTValues.IV);
        TIER_BY_CASING.put(GTBlocks.MACHINE_CASING_LuV.get(), GTValues.LuV);
        TIER_BY_CASING.put(GTBlocks.MACHINE_CASING_ZPM.get(), GTValues.ZPM);
        TIER_BY_CASING.put(GTBlocks.MACHINE_CASING_UV.get(), GTValues.UV);
        TIER_BY_CASING.put(GTBlocks.MACHINE_CASING_UHV.get(), GTValues.UHV);
    }

    public static TraceabilityPredicate riftCasings() {
        return Predicates.custom(
                state -> {
                    Integer tier = TIER_BY_CASING.get(state.getBlockState().getBlock());
                    if (tier == null) return false;
                    Integer existing = state.getMatchContext().get(MATCH_CONTEXT_KEY);
                    if (existing != null && !existing.equals(tier)) return false;
                    state.getMatchContext().set(MATCH_CONTEXT_KEY, tier);
                    return true;
                },
                () -> TIER_BY_CASING.keySet().stream().map(BlockInfo::new).toArray(BlockInfo[]::new));
    }
}
