package com.pyure.gtrift.common.data;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.item.GTRiftItems;
import com.pyure.gtrift.common.item.RiftQuality;

import com.gregtechceu.gtceu.api.GTValues;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Mob pool entries come from two independent sources, merged together:
 *
 * 1. Datapacks/other mods, via the normal SimpleJsonResourceReloadListener scan of
 *    data/&lt;namespace&gt;/&lt;directory&gt;/*.json across every loaded pack — how another mod or a
 *    world's own datapack can ADD entries with zero GTRift-specific integration.
 * 2. GTRift's own defaults, extracted to config/gtrift/&lt;directory&gt;/*.json the first time that
 *    folder doesn't exist, then read directly from there afterward — a real, standalone,
 *    player-editable/deletable file, not something sealed inside the mod jar. Deliberately NOT
 *    shipped as bundled data resources: a file baked into the jar can only be customized by
 *    authoring a whole separate overriding datapack at the exact same resource path, which isn't
 *    reasonable to expect a player to know how to do or to discover unprompted. Deleting a specific
 *    file (or the whole folder, treated as "reset to defaults" on next launch) here is a normal,
 *    self-explanatory action — the same expectation a config file already sets.
 */
@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RiftMobPoolLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * min/max default to 1 in the shipped defaults below; minTier is a GTValues.VN tier name
     * ("ulv", "lv", "mv", ...), not a raw int, to match the player-facing JSON format.
     */
    private record DefaultDrop(String type, String quality, double chance, int min, int max, String minTier,
                                double eliteChanceMultiplier, double eliteAmountMultiplier) {}

    private record DefaultEntry(String entityId, int weight, double healthMultiplier, double damageMultiplier,
                                 double speedMultiplier, List<DefaultDrop> drops) {}

    /**
     * `fatal` means "skip the whole entry" (mirrors the unknown-entity treatment in parseEntry) — an
     * unresolvable dimension id is a fatal case, everything else (missing field, "all", empty array,
     * "all" mixed with ids) just produces a value plus zero or more issue messages.
     */
    public record DimensionParseResult(Optional<Set<ResourceKey<Level>>> dimensions, List<String> issues,
                                        boolean fatal) {}

    /**
     * Illustrative defaults, not a tuned economy: each mob has one common drop (high chance, no tier
     * gate) plus one rarer bonus drop, some of them tier-gated, to demonstrate multiple simultaneous
     * drops, low-chance rarity, and per-tier availability out of the box.
     *
     * The eliteChanceMultiplier/eliteAmountMultiplier values here are only meaningful for the
     * rift_elite_mobs copy — in RiftEventSpawner.trySpawnMob, the elite coin-flip picks which POOL to
     * draw from before a mob is ever chosen, so a mob drawn from RiftMobPool.NORMAL (rift_mobs) can
     * never end up tagged gtrift_elite, and its drops' multiplier fields can never fire. This list is
     * shared between both directories (same mob types in both, matching the original Phase 5 design:
     * "an elite without any addons installed is still one of the same 4 vanilla mobs, just
     * glowing/named/boss-barred"), so extractDefaultsIfMissing writes these multiplier values only for
     * rift_elite_mobs and forces 1.0 (an honest no-op) for rift_mobs, rather than shipping a nonzero
     * value that implies a behavior that can't happen there.
     *
     * healthMultiplier/damageMultiplier/speedMultiplier are unrelated to the elite system above — they
     * apply to every entry in both pools, on top of the flat per-tier stat bonus (see
     * RiftEventSpawner.applyDifficultyScaling). Zombie's 1.2 health here is purely illustrative, so a
     * pack dev opening a freshly-extracted config sees the field in use, not just documented —
     * extractDefaultsIfMissing only writes a multiplier key when it's not the 1.0 no-op, so the other
     * three entries' JSON stays uncluttered.
     */
    private static final List<DefaultEntry> DEFAULT_ENTRIES = List.of(
            new DefaultEntry("minecraft:zombie", 100, 1.2, 1.0, 1.0, List.of(
                    new DefaultDrop("diamond", "normal", 0.9, 1, 1, "ulv", 1.2, 2.0),
                    new DefaultDrop("diamond", "rich", 0.1, 1, 1, "ulv", 3.0, 2.0))),
            new DefaultEntry("minecraft:skeleton", 100, 1.0, 1.0, 1.0, List.of(
                    new DefaultDrop("diamond", "normal", 0.9, 1, 1, "ulv", 1.2, 2.0),
                    new DefaultDrop("diamond", "rich", 0.1, 1, 1, "ulv", 3.0, 2.0))),
            new DefaultEntry("minecraft:spider", 100, 1.0, 1.0, 1.0, List.of(
                    new DefaultDrop("diamond", "normal", 0.9, 1, 1, "ulv", 1.2, 2.0),
                    new DefaultDrop("diamond", "rich", 0.08, 1, 1, "lv", 3.0, 2.0))),
            new DefaultEntry("minecraft:husk", 100, 1.0, 1.0, 1.0, List.of(
                    new DefaultDrop("diamond", "normal", 0.9, 1, 1, "ulv", 1.2, 2.0),
                    new DefaultDrop("diamond", "extremely_rich", 0.05, 1, 1, "mv", 3.0, 2.0))));

    private final String directory;
    private final RiftMobPool target;
    private final Set<ResourceKey<Level>> validDimensions;

    /** Reset fresh at the top of every apply() — a new RiftMobPoolLoader instance is created per
     * reload (see onAddReloadListeners), so this never leaks state across reload cycles. */
    private List<String> issues = new ArrayList<>();

    /**
     * validDimensions is captured once, at construction (see onAddReloadListeners), from the
     * RegistryAccess AddReloadListenerEvent already carries — NOT from
     * ServerLifecycleHooks.getCurrentServer(), which returns null during the very first reload of a
     * world's startup (that reload runs as part of WorldLoader.load(), strictly before the
     * MinecraftServer object is constructed). RegistryAccess's dimension/LevelStem data, by contrast,
     * is resolved earlier still, as part of building that same RegistryAccess — so it's reliably
     * available even on this first reload. Confirmed the hard way: a real client run showed "Unknown
     * dimension 'minecraft:overworld'" on world load using the ServerLifecycleHooks approach.
     */
    public RiftMobPoolLoader(String directory, RiftMobPool target, Set<ResourceKey<Level>> validDimensions) {
        super(new Gson(), directory);
        this.directory = directory;
        this.target = target;
        this.validDimensions = validDimensions;
    }

    /**
     * Writes the default entries to config/gtrift/&lt;directory&gt;/ the first time that folder
     * doesn't exist — never regenerates an existing (even emptied) folder, so a player who deletes
     * individual files, or empties the whole folder, has that respected permanently. Deleting the
     * whole folder itself is treated as "reset to defaults" on next launch, matching common config
     * conventions (e.g. GTRiftConfig's own YAML file does the same if deleted).
     */
    public static void extractDefaultsIfMissing(String directory) {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(GTRift.MOD_ID).resolve(directory);
        if (Files.exists(dir)) return;

        // Only mobs drawn from RiftMobPool.ELITE (this directory) can ever be tagged gtrift_elite —
        // see the DEFAULT_ENTRIES doc comment above. A mob from rift_mobs can't, so the multiplier
        // keys are omitted from that file entirely rather than written as an inert "1.0" — parseDrops
        // already treats a missing key as 1.0 (see its GsonHelper.getAsDouble(..., 1.0) defaults), so
        // this changes nothing behaviorally, it just doesn't ship a field that can never do anything.
        boolean elitePool = directory.equals("rift_elite_mobs");

        try {
            Files.createDirectories(dir);
            for (DefaultEntry defaultEntry : DEFAULT_ENTRIES) {
                JsonObject json = new JsonObject();
                json.addProperty("entity", defaultEntry.entityId());
                json.addProperty("weight", defaultEntry.weight());
                // Only written when not the 1.0 no-op, same "don't ship a field that changes nothing"
                // reasoning as the elite multipliers below, just on a different condition (every entry
                // in both pools can use these, so it's per-value here, not per-pool).
                if (defaultEntry.healthMultiplier() != 1.0) {
                    json.addProperty("health_multiplier", defaultEntry.healthMultiplier());
                }
                if (defaultEntry.damageMultiplier() != 1.0) {
                    json.addProperty("damage_multiplier", defaultEntry.damageMultiplier());
                }
                if (defaultEntry.speedMultiplier() != 1.0) {
                    json.addProperty("speed_multiplier", defaultEntry.speedMultiplier());
                }

                JsonArray dropsArray = new JsonArray();
                for (DefaultDrop drop : defaultEntry.drops()) {
                    JsonObject dropJson = new JsonObject();
                    dropJson.addProperty("type", drop.type());
                    dropJson.addProperty("quality", drop.quality());
                    dropJson.addProperty("chance", drop.chance());
                    dropJson.addProperty("min", drop.min());
                    dropJson.addProperty("max", drop.max());
                    dropJson.addProperty("min_tier", drop.minTier());
                    if (elitePool) {
                        dropJson.addProperty("elite_chance_multiplier", drop.eliteChanceMultiplier());
                        dropJson.addProperty("elite_amount_multiplier", drop.eliteAmountMultiplier());
                    }
                    dropsArray.add(dropJson);
                }
                json.add("drops", dropsArray);

                String fileName = new ResourceLocation(defaultEntry.entityId()).getPath() + ".json";
                Files.writeString(dir.resolve(fileName), GSON.toJson(json), StandardCharsets.UTF_8);
            }
            LOGGER.info("Extracted default entries for rift mob pool '{}' to {}", directory, dir);
        } catch (IOException e) {
            LOGGER.warn("Failed to extract default entries for rift mob pool '{}' to {}", directory, dir, e);
        }
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager resourceManager,
                          ProfilerFiller profiler) {
        issues = new ArrayList<>();
        List<RiftMobPoolEntry> entries = new ArrayList<>();

        for (Map.Entry<ResourceLocation, JsonElement> fileEntry : data.entrySet()) {
            parseEntry(fileEntry.getValue(), fileEntry.getKey().toString()).ifPresent(entries::add);
        }
        int fromDatapacks = entries.size();

        entries.addAll(loadConfigFolderEntries());
        int fromConfig = entries.size() - fromDatapacks;

        target.setEntries(entries);
        target.setIssues(issues);
        LOGGER.info("Loaded {} entries into rift mob pool '{}' ({} from datapacks/mods, {} from config, {} issue(s))",
                entries.size(), directory, fromDatapacks, fromConfig, issues.size());
    }

    private List<RiftMobPoolEntry> loadConfigFolderEntries() {
        Path dir = FMLPaths.CONFIGDIR.get().resolve(GTRift.MOD_ID).resolve(directory);
        List<RiftMobPoolEntry> entries = new ArrayList<>();
        if (!Files.isDirectory(dir)) return entries;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonElement json = GSON.fromJson(reader, JsonElement.class);
                    parseEntry(json, file.toString()).ifPresent(entries::add);
                } catch (Exception e) {
                    String message = "[%s] Failed to parse config-folder file %s, skipping: %s"
                            .formatted(directory, file, e.getMessage());
                    LOGGER.warn(message);
                    issues.add(message);
                }
            }
        } catch (IOException e) {
            String message = "[%s] Failed to list config-folder entries in %s: %s"
                    .formatted(directory, dir, e.getMessage());
            LOGGER.warn(message);
            issues.add(message);
        }
        return entries;
    }

    private Optional<RiftMobPoolEntry> parseEntry(JsonElement json, String source) {
        try {
            JsonObject object = json.getAsJsonObject();
            String entityId = GsonHelper.getAsString(object, "entity");
            int weight = GsonHelper.getAsInt(object, "weight");
            EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(entityId));
            if (entityType == null) {
                String message = "[%s] Unknown entity '%s' in %s, skipping".formatted(directory, entityId, source);
                LOGGER.warn(message);
                issues.add(message);
                return Optional.empty();
            }
            DimensionParseResult dimensionResult = parseDimensions(object, directory, source, validDimensions);
            for (String issue : dimensionResult.issues()) {
                LOGGER.warn(issue);
                issues.add(issue);
            }
            if (dimensionResult.fatal()) {
                return Optional.empty();
            }

            double healthMultiplier = GsonHelper.getAsDouble(object, "health_multiplier", 1.0);
            double damageMultiplier = GsonHelper.getAsDouble(object, "damage_multiplier", 1.0);
            double speedMultiplier = GsonHelper.getAsDouble(object, "speed_multiplier", 1.0);

            List<RiftDropEntry> drops = parseDrops(object, source);
            return Optional.of(new RiftMobPoolEntry(entityType, weight, drops, dimensionResult.dimensions(),
                    healthMultiplier, damageMultiplier, speedMultiplier));
        } catch (Exception e) {
            String message = "[%s] Failed to parse %s, skipping: %s".formatted(directory, source, e.getMessage());
            LOGGER.warn(message);
            issues.add(message);
            return Optional.empty();
        }
    }

    /** "drops" is optional — an entry with none simply never drops anything. */
    private List<RiftDropEntry> parseDrops(JsonObject object, String source) {
        List<RiftDropEntry> drops = new ArrayList<>();
        if (!object.has("drops")) return drops;

        for (JsonElement dropElement : GsonHelper.getAsJsonArray(object, "drops")) {
            try {
                JsonObject dropObject = dropElement.getAsJsonObject();
                String type = GsonHelper.getAsString(dropObject, "type");
                if (!GTRiftItems.allShardItems().containsKey(type)) {
                    String message = "[%s] Unknown shard type '%s' in a drop entry in %s, skipping that entry"
                            .formatted(directory, type, source);
                    LOGGER.warn(message);
                    issues.add(message);
                    continue;
                }
                RiftQuality quality = RiftQuality
                        .valueOf(GsonHelper.getAsString(dropObject, "quality").toUpperCase(Locale.ROOT));
                double chance = GsonHelper.getAsDouble(dropObject, "chance");
                int min = GsonHelper.getAsInt(dropObject, "min", 1);
                int max = GsonHelper.getAsInt(dropObject, "max", min);
                int minTier = parseTier(dropObject, "min_tier", source);
                double eliteChanceMultiplier = GsonHelper.getAsDouble(dropObject, "elite_chance_multiplier", 1.0);
                double eliteAmountMultiplier = GsonHelper.getAsDouble(dropObject, "elite_amount_multiplier", 1.0);
                drops.add(new RiftDropEntry(type, quality, minTier, chance, min, max,
                        eliteChanceMultiplier, eliteAmountMultiplier));
            } catch (Exception e) {
                String message = "[%s] Failed to parse a drop entry in %s, skipping that entry: %s"
                        .formatted(directory, source, e.getMessage());
                LOGGER.warn(message);
                issues.add(message);
            }
        }
        return drops;
    }

    /** Missing key defaults to ULV (always eligible); an unrecognized tier name also falls back to ULV. */
    private int parseTier(JsonObject object, String key, String source) {
        if (!object.has(key)) return GTValues.ULV;
        String name = GsonHelper.getAsString(object, key);
        for (int tier = 0; tier < GTValues.VN.length; tier++) {
            if (GTValues.VN[tier].equalsIgnoreCase(name)) return tier;
        }
        String message = "[%s] Unknown tier '%s' in %s, defaulting to ULV".formatted(directory, name, source);
        LOGGER.warn(message);
        issues.add(message);
        return GTValues.ULV;
    }

    /**
     * Pure and public — no dependency on `this.issues`/`this.directory` or the reload-listener
     * instance — so it can be exercised directly by a GameTest with a synthetic valid-dimension set,
     * and so Phase 4's JEI plugin can reuse it against config-folder files read outside the normal
     * reload cycle. "directory" is only used to prefix log/issue messages, matching every other
     * message format in this class.
     */
    public static DimensionParseResult parseDimensions(JsonObject object, String directory, String source,
                                                         Set<ResourceKey<Level>> validDimensions) {
        List<String> issues = new ArrayList<>();
        if (!object.has("dimensions")) {
            return new DimensionParseResult(Optional.empty(), issues, false);
        }

        JsonElement element = object.get("dimensions");
        List<String> rawIds = new ArrayList<>();
        if (element.isJsonPrimitive()) {
            rawIds.add(element.getAsString());
        } else {
            for (JsonElement item : element.getAsJsonArray()) {
                rawIds.add(item.getAsString());
            }
        }

        if (rawIds.isEmpty()) {
            return new DimensionParseResult(Optional.of(Set.of()), issues, false);
        }

        boolean hasAll = rawIds.stream().anyMatch(id -> id.equalsIgnoreCase("all"));
        if (hasAll) {
            if (rawIds.size() > 1) {
                issues.add("[%s] \"dimensions\" mixes \"all\" with specific ids in %s, ignoring the ids and treating as \"all\""
                        .formatted(directory, source));
            }
            return new DimensionParseResult(Optional.empty(), issues, false);
        }

        Set<ResourceKey<Level>> keys = new HashSet<>();
        for (String rawId : rawIds) {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(rawId));
            if (!validDimensions.contains(key)) {
                issues.add("[%s] Unknown dimension '%s' in %s, skipping entry".formatted(directory, rawId, source));
                return new DimensionParseResult(Optional.empty(), issues, true);
            }
            keys.add(key);
        }
        return new DimensionParseResult(Optional.of(Set.copyOf(keys)), issues, false);
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        Set<ResourceKey<Level>> validDimensions = new HashSet<>();
        for (ResourceKey<LevelStem> levelStemKey : event.getRegistryAccess().registryOrThrow(Registries.LEVEL_STEM)
                .registryKeySet()) {
            validDimensions.add(Registries.levelStemToLevel(levelStemKey));
        }

        event.addListener(new RiftMobPoolLoader("rift_mobs", RiftMobPool.NORMAL, validDimensions));
        event.addListener(new RiftMobPoolLoader("rift_elite_mobs", RiftMobPool.ELITE, validDimensions));
    }
}
