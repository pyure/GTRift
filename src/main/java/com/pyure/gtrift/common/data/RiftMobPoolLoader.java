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
 * 2. config/gtrift/&lt;directory&gt;/*.json, read directly from there — a real, standalone,
 *    player-editable/deletable file, not something sealed inside the mod jar. This loader only ever
 *    reads whatever's already on disk; real content there comes from RiftMobPoolDatagen (ore-vein-
 *    shard-type-driven generation, see its own class doc) — the same split of responsibility
 *    RiftShardOreDatagen/ShardTypeLoader already use for shard types.
 */
@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class RiftMobPoolLoader extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Shared with RiftMobPoolDatagen so the two directory names have one source of truth. */
    static final String NORMAL_DIRECTORY = "rift_mobs";
    static final String ELITE_DIRECTORY = "rift_elite_mobs";

    /**
     * `fatal` means "skip the whole entry" (mirrors the unknown-entity treatment in parseEntry) — an
     * unresolvable dimension id is a fatal case, everything else (missing field, "all", empty array,
     * "all" mixed with ids) just produces a value plus zero or more issue messages.
     */
    public record DimensionParseResult(Optional<Set<ResourceKey<Level>>> dimensions, List<String> issues,
                                        boolean fatal) {}

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

        event.addListener(new RiftMobPoolLoader(NORMAL_DIRECTORY, RiftMobPool.NORMAL, validDimensions));
        event.addListener(new RiftMobPoolLoader(ELITE_DIRECTORY, RiftMobPool.ELITE, validDimensions));
    }
}
