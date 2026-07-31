package com.pyure.gtrift.common.data;

import net.minecraft.resources.ResourceLocation;

/**
 * One independently-rolled possible output of a shard type's Centrifuge recipe. `chance` follows the
 * same convention as RiftDropEntry.chance but isn't capped at 1.0 — values past 100% are meant to be
 * expanded by the recipe-generation step (Phase 3) into guaranteed-plus-fractional outputs, the same
 * way GTRecipeModifiers.applyYieldBoost already handles overflow chance.
 */
public record ShardTypeOutput(ResourceLocation itemId, double chance) {
}
