package com.pyure.gtrift.common.data;

import net.minecraft.util.RandomSource;

import java.util.List;

public class RiftMobPool {

    public static final RiftMobPool NORMAL = new RiftMobPool();
    public static final RiftMobPool ELITE = new RiftMobPool();

    private List<RiftMobPoolEntry> entries = List.of();

    public void setEntries(List<RiftMobPoolEntry> entries) {
        this.entries = List.copyOf(entries);
    }

    public RiftMobPoolEntry pickRandom(RandomSource random) {
        if (entries.isEmpty()) return null;
        int totalWeight = entries.stream().mapToInt(RiftMobPoolEntry::weight).sum();
        if (totalWeight <= 0) return null;
        int roll = random.nextInt(totalWeight);
        for (RiftMobPoolEntry entry : entries) {
            roll -= entry.weight();
            if (roll < 0) return entry;
        }
        return entries.get(entries.size() - 1);
    }
}
