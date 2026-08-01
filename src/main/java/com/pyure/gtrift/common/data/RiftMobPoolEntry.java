package com.pyure.gtrift.common.data;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * `dimensions` is a tri-state eligibility filter: `Optional.empty()` means unrestricted (eligible
 * everywhere — the default when the JSON field is omitted or set to `"all"`), `Optional.of(Set.of())`
 * means eligible nowhere (an explicit empty `"dimensions"` array), and `Optional.of(nonEmptySet)`
 * restricts eligibility to exactly those dimensions.
 */
public record RiftMobPoolEntry(EntityType<?> entityType, int weight, List<RiftDropEntry> drops,
                                Optional<Set<ResourceKey<Level>>> dimensions) {

    public boolean isEligibleFor(ResourceKey<Level> dimension) {
        return dimensions.map(eligible -> eligible.contains(dimension)).orElse(true);
    }
}
