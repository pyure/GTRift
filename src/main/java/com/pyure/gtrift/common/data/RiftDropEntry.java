package com.pyure.gtrift.common.data;

import com.pyure.gtrift.common.item.RiftRichness;

import net.minecraft.nbt.CompoundTag;

/**
 * One independently-rolled possible drop for a mob pool entry. `chance` is an absolute probability
 * (0.0-1.0), not a relative weight, so multiple entries on the same mob can each trigger on the same
 * kill — this is what makes "rare bonus drop on top of a common one" possible. `minTier` is a hard
 * gate (the entry cannot trigger below that difficulty tier at all), independent of and never
 * bypassed by elite status. `eliteChanceMultiplier`/`eliteAmountMultiplier` (default 1.0, a no-op)
 * scale chance/amount when the source mob is elite, without affecting `minTier` eligibility — elites
 * get more of what a tier already allows, not early access to the next tier's drops.
 *
 * `type` is a shard type's sanitizedId (ShardType.sanitizedId()), not an enum — the fixed-affinity
 * system this replaced is retired entirely, per specs/rift-shard-types.md.
 *
 * These multiplier fields are only reachable on an entry whose owning RiftMobPoolEntry lives in
 * RiftMobPool.ELITE — RiftEventSpawner.trySpawnMob decides "is this spawn elite" before picking which
 * pool to draw from, so a mob drawn from RiftMobPool.NORMAL can never be tagged gtrift_elite, and a
 * non-1.0 multiplier on one of its drops would simply never fire.
 */
public record RiftDropEntry(String type, RiftRichness richness, int minTier, double chance, int min,
                             int max, double eliteChanceMultiplier, double eliteAmountMultiplier) {

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type);
        tag.putString("Richness", richness.name());
        tag.putDouble("Chance", chance);
        tag.putInt("Min", min);
        tag.putInt("Max", max);
        tag.putDouble("EliteChanceMultiplier", eliteChanceMultiplier);
        tag.putDouble("EliteAmountMultiplier", eliteAmountMultiplier);
        return tag;
    }

    /** minTier is not restored (0/ULV) — the spawner already filtered to eligible entries before saving. */
    public static RiftDropEntry fromNbt(CompoundTag tag) {
        return new RiftDropEntry(
                tag.getString("Type"),
                RiftRichness.valueOf(tag.getString("Richness")),
                0,
                tag.getDouble("Chance"),
                tag.getInt("Min"),
                tag.getInt("Max"),
                tag.getDouble("EliteChanceMultiplier"),
                tag.getDouble("EliteAmountMultiplier"));
    }
}
