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
 *
 * `healthMultiplier`/`damageMultiplier`/`speedMultiplier` (default 1.0, a no-op) scale the mob's stats
 * on top of the beacon-tier scaling already applied in `RiftEventSpawner.applyDifficultyScaling` —
 * multiplying the final tier-adjusted value, not the vanilla base before the tier bonus. No
 * elite-specific variants: "elite" already means "drawn from the separate rift_elite_mobs pool," so a
 * pack dev wanting beefier elites just sets a higher multiplier directly in that pool's own file.
 */
public record RiftMobPoolEntry(EntityType<?> entityType, int weight, List<RiftDropEntry> drops,
                                Optional<Set<ResourceKey<Level>>> dimensions, double healthMultiplier,
                                double damageMultiplier, double speedMultiplier) {

    public boolean isEligibleFor(ResourceKey<Level> dimension) {
        return dimensions.map(eligible -> eligible.contains(dimension)).orElse(true);
    }
}
