package com.pyure.gtrift.common.data;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.List;

public class RiftMobPool {

    public static final RiftMobPool NORMAL = new RiftMobPool();
    public static final RiftMobPool ELITE = new RiftMobPool();

    private List<RiftMobPoolEntry> entries = List.of();
    private List<String> issues = List.of();

    public void setEntries(List<RiftMobPoolEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    /** Replaced wholesale on every reload (not accumulated) — reflects only the most recent load. */
    public void setIssues(List<String> issues) {
        this.issues = List.copyOf(issues);
    }

    public List<String> issues() {
        return issues;
    }

    public RiftMobPoolEntry pickRandom(RandomSource random) {
        return pickRandom(entries, random);
    }

    public RiftMobPoolEntry pickRandom(RandomSource random, ResourceKey<Level> dimension) {
        return pickRandom(eligibleEntries(dimension), random);
    }

    /**
     * Entries eligible for the given dimension, weight/RNG untouched — exposed separately (not just
     * inlined into pickRandom) so eligibility can be queried without a roll, both for pure-logic
     * testing and for the controller GUI's "any mobs configured here" check.
     */
    public List<RiftMobPoolEntry> eligibleEntries(ResourceKey<Level> dimension) {
        return entries.stream().filter(entry -> entry.isEligibleFor(dimension)).toList();
    }

    private static RiftMobPoolEntry pickRandom(List<RiftMobPoolEntry> candidates, RandomSource random) {
        if (candidates.isEmpty()) return null;
        int totalWeight = candidates.stream().mapToInt(RiftMobPoolEntry::weight).sum();
        if (totalWeight <= 0) return null;
        int roll = random.nextInt(totalWeight);
        for (RiftMobPoolEntry entry : candidates) {
            roll -= entry.weight();
            if (roll < 0) return entry;
        }
        return candidates.get(candidates.size() - 1);
    }
}
