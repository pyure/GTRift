package com.pyure.gtrift.common.data;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A pack-dev-defined shard type, loaded from config/gtrift/rift_shard_types/*.json (see
 * ShardTypeLoader). `rawType` is free text with no connection to GT's material system; `sanitizedId`
 * is the resource-path-safe id derived from it, used for the registered item's id.
 *
 * `dimensions`/`veinWeight` are populated only when this type was generated from an ore vein
 * (RiftShardOreDatagen, from the vein's own GTOreDefinition.dimensionFilter()/weight()) — always
 * absent on a hand-authored type, and never required. Stored as raw ResourceLocation ids, not
 * ResourceKey&lt;Level&gt;, and deliberately unvalidated against any dimension registry —
 * ShardTypeLoader runs at mod construction, before any registry access is reliably available (the
 * same timing gap RiftMobPoolLoader hit and fixed via AddReloadListenerEvent's RegistryAccess, which
 * ShardTypeLoader has no equivalent hook for). An unresolvable/garbage value here is simply inert
 * metadata forever, consumed only by mob-pool datagen (which runs later, with real registry access,
 * and treats an unresolvable dimension as "doesn't belong to any bucket," not an error).
 */
public record ShardType(String rawType, String sanitizedId, int color, List<ShardTypeOutput> outputs,
                         Optional<Set<ResourceLocation>> dimensions, OptionalInt veinWeight) {

    private static final Pattern INVALID_ID_CHARS = Pattern.compile("[^a-z0-9_.\\-]");

    /** Lowercases and replaces any character outside [a-z0-9_.-] with '_'. Never throws. */
    public static String sanitize(String rawType) {
        return INVALID_ID_CHARS.matcher(rawType.toLowerCase(java.util.Locale.ROOT)).replaceAll("_");
    }

    /** Parses a literal "#RRGGBB" hex string into a packed 0xRRGGBB int. Throws on any other shape. */
    public static int parseColor(String hex) {
        if (hex.length() != 7 || hex.charAt(0) != '#') {
            throw new IllegalArgumentException("Expected a '#RRGGBB' hex color, got '" + hex + "'");
        }
        return Integer.parseInt(hex.substring(1), 16);
    }

    /**
     * Splits rawType on '_'/whitespace and capitalizes each word, e.g. "raw_copper" -> "Raw Copper".
     * Used as the fallback text for a shard item's translatable name (see RiftShardItem.getName) —
     * only reached when no real lang-file entry exists for this type's translation key, which is
     * always true for a player-defined type and only sometimes true for a shipped/curated one.
     */
    public String defaultDisplayName() {
        String[] words = rawType.trim().split("[_\\s]+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1).toLowerCase(java.util.Locale.ROOT));
        }
        return result.toString();
    }
}
