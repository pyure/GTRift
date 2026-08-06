package com.pyure.gtrift.common.data;

import com.pyure.gtrift.GTRift;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates real content for both mob-pool config directories (rift_mobs/rift_elite_mobs), replacing
 * the old hand-authored, explicitly-illustrative RiftMobPoolLoader.DEFAULT_ENTRIES, the same way
 * RiftShardOreDatagen replaced the old hardcoded "diamond" placeholder. See
 * specs/rift-mob-datagen.md/plans/rift-mob-datagen.md for the full design.
 *
 * Automatic first-launch trigger: onServerAboutToStart, self-sufficient rather than relying on Forge's
 * dispatch order between it and RiftShardOreDatagen's own independent ServerAboutToStartEvent handler —
 * it explicitly ensures shard types exist itself (calling RiftShardOreDatagen.generateAll directly if
 * needed) before generating, deliberately not using EventPriority to solve the same problem implicitly.
 */
@Mod.EventBusSubscriber(modid = GTRift.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RiftMobPoolDatagen {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String TAG = "rift_mob_pool_datagen";

    private static final int DEFAULT_MOB_WEIGHT = 100;
    private static final double ELITE_CHANCE_MULTIPLIER = 1.2;
    private static final double ELITE_AMOUNT_MULTIPLIER = 2.0;
    private static final double MOST_COMMON_CHANCE = 0.9;
    private static final double RAREST_CHANCE = 0.15;

    /** One generated file's worth of (mob, dimension) identity — also the round-robin assignment key. */
    public record MobDimensionEntry(String entityId, ResourceLocation dimensionId) {}

    public record RosterResolution(List<MobDimensionEntry> resolved, List<String> skipped) {}

    public record DropEconomy(double chance, String minTierName) {}

    public record GenerationResult(List<String> writtenNormal, List<String> writtenElite) {}

    /**
     * Hand-curated, mirroring the spirit of the old DEFAULT_ENTRIES — not a registry scan, not
     * biome-derived. `enderman` deliberately appears in both the Overworld and End lists (real vanilla
     * behavior), producing two separate generated files; every other mob appears once.
     */
    private static final List<MobDimensionEntry> ROSTER = List.of(
            // Overworld
            entry("minecraft:zombie", "minecraft:overworld"),
            entry("minecraft:skeleton", "minecraft:overworld"),
            entry("minecraft:spider", "minecraft:overworld"),
            entry("minecraft:husk", "minecraft:overworld"),
            entry("minecraft:creeper", "minecraft:overworld"),
            entry("minecraft:witch", "minecraft:overworld"),
            entry("minecraft:drowned", "minecraft:overworld"),
            entry("minecraft:cave_spider", "minecraft:overworld"),
            entry("minecraft:enderman", "minecraft:overworld"),
            // Nether
            entry("minecraft:blaze", "minecraft:the_nether"),
            entry("minecraft:wither_skeleton", "minecraft:the_nether"),
            entry("minecraft:piglin_brute", "minecraft:the_nether"),
            entry("minecraft:hoglin", "minecraft:the_nether"),
            entry("minecraft:magma_cube", "minecraft:the_nether"),
            // End
            entry("minecraft:shulker", "minecraft:the_end"),
            entry("minecraft:enderman", "minecraft:the_end"),
            entry("minecraft:endermite", "minecraft:the_end"));

    private RiftMobPoolDatagen() {}

    private static MobDimensionEntry entry(String entityId, String dimensionId) {
        return new MobDimensionEntry(entityId, new ResourceLocation(dimensionId));
    }

    /**
     * Pure. Filters ROSTER against the real/valid dimension set — a roster pair whose dimension isn't
     * actually loaded in this world is skipped (reported, not silently dropped) rather than generated
     * with a dimension id RiftMobPoolLoader would later reject as fatal on load.
     */
    public static RosterResolution resolveRosterEntries(Set<ResourceLocation> validDimensions) {
        List<MobDimensionEntry> resolved = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (MobDimensionEntry candidate : ROSTER) {
            if (validDimensions.contains(candidate.dimensionId())) {
                resolved.add(candidate);
            } else {
                skipped.add("[%s] Roster entry (%s, %s) references a dimension not loaded in this world, skipping"
                        .formatted(TAG, candidate.entityId(), candidate.dimensionId()));
            }
        }
        return new RosterResolution(resolved, skipped);
    }

    /**
     * Pure. Groups shard types by real, resolvable dimension — a shard type with no dimensions() data,
     * or whose dimensions() value doesn't resolve to a dimension in validDimensions, is excluded
     * entirely (see ShardType's own doc comment: this is expected, inert-metadata behavior, not an
     * error). Each bucket comes back sorted by sanitizedId for deterministic round-robin assignment.
     */
    public static Map<ResourceLocation, List<ShardType>> bucketShardTypesByDimension(
            List<ShardType> shardTypes, Set<ResourceLocation> validDimensions) {
        Map<ResourceLocation, List<ShardType>> buckets = new LinkedHashMap<>();
        for (ShardType shardType : shardTypes) {
            if (shardType.dimensions().isEmpty()) continue;
            for (ResourceLocation dimension : shardType.dimensions().get()) {
                if (!validDimensions.contains(dimension)) continue;
                buckets.computeIfAbsent(dimension, d -> new ArrayList<>()).add(shardType);
            }
        }
        for (List<ShardType> bucket : buckets.values()) {
            bucket.sort(Comparator.comparing(ShardType::sanitizedId));
        }
        return buckets;
    }

    /**
     * Pure. Round-robin distributes each dimension's shard-type bucket across that dimension's own
     * roster entries only, so every shard type ends up assigned to exactly one mob and no single mob
     * absorbs a whole bucket. Every resolved roster entry gets an entry in the result (possibly an
     * empty list), never a missing key.
     */
    public static Map<MobDimensionEntry, List<ShardType>> assignShardTypes(
            List<MobDimensionEntry> resolvedRoster, Map<ResourceLocation, List<ShardType>> bucketed) {
        Map<MobDimensionEntry, List<ShardType>> assignments = new LinkedHashMap<>();
        Map<ResourceLocation, List<MobDimensionEntry>> rosterByDimension = new LinkedHashMap<>();
        for (MobDimensionEntry rosterEntry : resolvedRoster) {
            assignments.put(rosterEntry, new ArrayList<>());
            rosterByDimension.computeIfAbsent(rosterEntry.dimensionId(), d -> new ArrayList<>()).add(rosterEntry);
        }

        for (Map.Entry<ResourceLocation, List<ShardType>> bucketEntry : bucketed.entrySet()) {
            List<MobDimensionEntry> mobsInDimension = rosterByDimension.get(bucketEntry.getKey());
            if (mobsInDimension == null || mobsInDimension.isEmpty()) continue;

            List<ShardType> shardTypes = bucketEntry.getValue();
            for (int i = 0; i < shardTypes.size(); i++) {
                MobDimensionEntry target = mobsInDimension.get(i % mobsInDimension.size());
                assignments.get(target).add(shardTypes.get(i));
            }
        }
        return assignments;
    }

    /**
     * Pure. Normalizes the shard type's own vein_weight against the bucket's observed [minWeight,
     * maxWeight] range — rarity 0 (most common) to 1 (rarest) — and maps that onto chance/min_tier.
     * minWeight == maxWeight (a single-vein bucket) is treated as rarity 0 rather than dividing by
     * zero. A shard type missing vein_weight (independently optional from dimensions — a hand-edited
     * file could set one without the other) falls back to minWeight, i.e. the common band, a safe
     * default rather than a crash.
     */
    public static DropEconomy computeDropEconomy(ShardType shardType, int minWeight, int maxWeight) {
        int weight = shardType.veinWeight().orElse(minWeight);
        double rarity = maxWeight == minWeight ? 0.0 : 1.0 - (weight - minWeight) / (double) (maxWeight - minWeight);
        double chance = MOST_COMMON_CHANCE + (RAREST_CHANCE - MOST_COMMON_CHANCE) * rarity;
        String minTierName = rarity < (1.0 / 3.0) ? "ulv" : rarity < (2.0 / 3.0) ? "lv" : "mv";
        return new DropEconomy(chance, minTierName);
    }

    /**
     * Pure. `bucketMinWeight`/`bucketMaxWeight` are the whole dimension bucket's range (not just
     * assignedShardTypes' own range), per the spec's "normalize against the bucket's own min/max, not
     * just what one mob happens to carry." `elitePool=true` adds the flat elite multipliers to every
     * drop; `elitePool=false` omits both keys entirely rather than writing an inert 1.0 (matches the
     * "don't ship a field that changes nothing" convention already used for health/damage/speed
     * multipliers elsewhere).
     */
    public static JsonObject buildMobPoolFileJson(MobDimensionEntry entry, List<ShardType> assignedShardTypes,
                                                   int bucketMinWeight, int bucketMaxWeight, boolean elitePool) {
        JsonObject json = new JsonObject();
        json.addProperty("entity", entry.entityId());
        json.addProperty("weight", DEFAULT_MOB_WEIGHT);

        JsonArray dimensionsArray = new JsonArray();
        dimensionsArray.add(entry.dimensionId().toString());
        json.add("dimensions", dimensionsArray);

        JsonArray drops = new JsonArray();
        for (ShardType shardType : assignedShardTypes) {
            DropEconomy economy = computeDropEconomy(shardType, bucketMinWeight, bucketMaxWeight);
            JsonObject drop = new JsonObject();
            drop.addProperty("type", shardType.sanitizedId());
            drop.addProperty("quality", "normal");
            drop.addProperty("chance", economy.chance());
            drop.addProperty("min", 1);
            drop.addProperty("max", 1);
            drop.addProperty("min_tier", economy.minTierName());
            if (elitePool) {
                drop.addProperty("elite_chance_multiplier", ELITE_CHANCE_MULTIPLIER);
                drop.addProperty("elite_amount_multiplier", ELITE_AMOUNT_MULTIPLIER);
            }
            drops.add(drop);
        }
        json.add("drops", drops);
        return json;
    }

    /**
     * Content-based dedup scan — reads each existing file's raw "entity"/"dimensions" fields directly
     * (not the full validating RiftMobPoolLoader), so an already-covered (entity, dimension) pair is
     * recognized regardless of the file's actual name. Only recognizes the single-dimension shape this
     * generator itself writes (a bare string, or a one-element array) — a hand-authored file with
     * multiple dimensions or "all" doesn't map to one specific roster pair, so it simply isn't counted
     * as covering any (conservative: at worst causes one harmless extra generated file, never silently
     * skips something a user actually wanted regenerated).
     */
    public static Set<MobDimensionEntry> alreadyCovered(Path directory) {
        Set<MobDimensionEntry> covered = new HashSet<>();
        if (!Files.isDirectory(directory)) return covered;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : stream) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    JsonObject object = GSON.fromJson(reader, JsonElement.class).getAsJsonObject();
                    if (!object.has("entity") || !object.has("dimensions")) continue;
                    String entityId = object.get("entity").getAsString();

                    JsonElement dimensionsElement = object.get("dimensions");
                    List<String> rawIds = new ArrayList<>();
                    if (dimensionsElement.isJsonPrimitive()) {
                        rawIds.add(dimensionsElement.getAsString());
                    } else if (dimensionsElement.isJsonArray()) {
                        for (JsonElement item : dimensionsElement.getAsJsonArray()) rawIds.add(item.getAsString());
                    }
                    if (rawIds.size() == 1) {
                        covered.add(new MobDimensionEntry(entityId, new ResourceLocation(rawIds.get(0))));
                    }
                } catch (Exception e) {
                    LOGGER.warn("[{}] Failed to inspect {} for dedup, ignoring: {}", TAG, file, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[{}] Failed to list {} for dedup: {}", TAG, directory, e.getMessage());
        }
        return covered;
    }

    /**
     * Real file writes — the single shared entry point for every trigger (later phases: automatic
     * first-launch, `fill`, `wipe confirm`). Reads shard-type data from shardTypesDir (via
     * ShardTypeLoader — never RiftShardOreDatagen/GTRegistries.ORE_VEINS directly, keeping this class's
     * only dependency on already-generated ShardType data). Creates mobsDir/eliteDir if missing.
     */
    public static GenerationResult generateAll(Path mobsDir, Path eliteDir, Path shardTypesDir,
                                                Set<ResourceLocation> validDimensions) {
        try {
            Files.createDirectories(mobsDir);
            Files.createDirectories(eliteDir);
        } catch (IOException e) {
            LOGGER.warn("[{}] Failed to create mob-pool directories ({}, {}): {}", TAG, mobsDir, eliteDir, e.getMessage());
            return new GenerationResult(List.of(), List.of());
        }

        List<ShardType> shardTypes = ShardTypeLoader.loadAll(shardTypesDir).shardTypes();
        Map<ResourceLocation, List<ShardType>> bucketed = bucketShardTypesByDimension(shardTypes, validDimensions);

        // Real, observed failure mode (not hypothetical): a rift_shard_types/ folder generated before
        // dimensions/vein_weight existed on ShardType has every entry silently excluded from every
        // bucket (see ShardType's own doc comment on those fields being non-fatal/inert on absence) —
        // producing entirely-empty drops for every mob with no error anywhere. Neither this method's own
        // dedup nor ensureShardTypesExist's "does the folder exist" check can detect or fix that on
        // their own, so surface it here instead of leaving it silent.
        if (!shardTypes.isEmpty() && shardTypes.stream().noneMatch(type -> type.dimensions().isPresent())) {
            LOGGER.warn("[{}] Loaded {} shard type(s) from {}, but none carry dimension data — every " +
                            "generated mob-pool drop will be empty. This usually means these shard types " +
                            "predate GTRift's ore-vein-datagen dimensions/vein_weight fields; try " +
                            "'/gtrift shard_types wipe confirm' to regenerate them with real data, then " +
                            "re-run mob-pool generation.",
                    TAG, shardTypes.size(), shardTypesDir);
        }

        RosterResolution resolution = resolveRosterEntries(validDimensions);
        for (String skip : resolution.skipped()) LOGGER.info(skip);

        Map<MobDimensionEntry, List<ShardType>> assignments = assignShardTypes(resolution.resolved(), bucketed);

        Set<MobDimensionEntry> coveredNormal = alreadyCovered(mobsDir);
        Set<MobDimensionEntry> coveredElite = alreadyCovered(eliteDir);

        List<String> writtenNormal = new ArrayList<>();
        List<String> writtenElite = new ArrayList<>();

        for (MobDimensionEntry rosterEntry : resolution.resolved()) {
            List<ShardType> assigned = assignments.getOrDefault(rosterEntry, List.of());
            List<ShardType> bucket = bucketed.getOrDefault(rosterEntry.dimensionId(), List.of());
            int[] range = weightRange(bucket);

            if (!coveredNormal.contains(rosterEntry)) {
                JsonObject json = buildMobPoolFileJson(rosterEntry, assigned, range[0], range[1], false);
                writeFile(mobsDir, rosterEntry, json, writtenNormal);
            }
            if (!coveredElite.contains(rosterEntry)) {
                JsonObject json = buildMobPoolFileJson(rosterEntry, assigned, range[0], range[1], true);
                writeFile(eliteDir, rosterEntry, json, writtenElite);
            }
        }

        LOGGER.info("[{}] Generated {} normal / {} elite mob-pool file(s): {} / {}", TAG,
                writtenNormal.size(), writtenElite.size(), writtenNormal, writtenElite);
        return new GenerationResult(writtenNormal, writtenElite);
    }

    public static Path mobsDir() {
        return FMLPaths.CONFIGDIR.get().resolve(GTRift.MOD_ID).resolve(RiftMobPoolLoader.NORMAL_DIRECTORY);
    }

    public static Path eliteDir() {
        return FMLPaths.CONFIGDIR.get().resolve(GTRift.MOD_ID).resolve(RiftMobPoolLoader.ELITE_DIRECTORY);
    }

    /**
     * Testable gate + delegate — no-ops (both real directories untouched) if both mobsDir/eliteDir
     * already exist, matching the old extractDefaultsIfMissing's per-directory-independent granularity.
     * Otherwise delegates to the real generateAll, which handles the "only one of the two is missing"
     * case correctly on its own via its own per-directory dedup — no separate logic needed here for
     * that case.
     */
    public static GenerationResult maybeGenerate(Path mobsDir, Path eliteDir, Path shardTypesDir,
                                                  Set<ResourceLocation> validDimensions) {
        if (Files.exists(mobsDir) && Files.exists(eliteDir)) {
            return new GenerationResult(List.of(), List.of());
        }
        return generateAll(mobsDir, eliteDir, shardTypesDir, validDimensions);
    }

    /**
     * Fires once per server boot. Checks its own gate first (both real directories already exist ->
     * return immediately, no work on ordinary later boots). Otherwise delegates to the two shared
     * helpers below before generating.
     */
    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        Path mobsDir = mobsDir();
        Path eliteDir = eliteDir();
        if (Files.exists(mobsDir) && Files.exists(eliteDir)) return;

        ensureShardTypesExist(RiftShardOreDatagen.configDir());
        Set<ResourceLocation> validDimensions = collectValidDimensions(event.getServer().registryAccess());
        maybeGenerate(mobsDir, eliteDir, RiftShardOreDatagen.configDir(), validDimensions);
    }

    /**
     * Ensures shard types exist at shardTypesDir, generating them if not — deliberately not relying on
     * Forge's dispatch order between this class's ServerAboutToStartEvent handler and
     * RiftShardOreDatagen's own independent one (EventPriority is unused anywhere else in this
     * codebase, and getting the ordering wrong here would only surface via a real first-boot log, not
     * compilation). Takes an explicit Path (rather than always resolving RiftShardOreDatagen.configDir()
     * internally) so it's scratch-dir testable. Shared by both the automatic trigger and the
     * `/gtrift mob_pools` commands, which need the same guarantee.
     */
    public static void ensureShardTypesExist(Path shardTypesDir) {
        if (!Files.exists(shardTypesDir)) {
            RiftShardOreDatagen.generateAll(shardTypesDir);
        }
    }

    /**
     * Real dimension-registry collection — same approach RiftMobPoolLoader.onAddReloadListeners
     * already uses (LEVEL_STEM's registry key set, mapped to Level dimension keys, then to their raw
     * ResourceLocation). Shared here so the automatic trigger and the commands below don't each
     * duplicate it.
     */
    public static Set<ResourceLocation> collectValidDimensions(RegistryAccess registryAccess) {
        Set<ResourceLocation> validDimensions = new HashSet<>();
        for (ResourceKey<LevelStem> levelStemKey : registryAccess.registryOrThrow(Registries.LEVEL_STEM).registryKeySet()) {
            validDimensions.add(Registries.levelStemToLevel(levelStemKey).location());
        }
        return validDimensions;
    }

    /**
     * Deletes every *.json file currently in mobsDir/eliteDir (not the directories themselves, not
     * non-JSON files, not shardTypesDir), then runs generateAll against the now-empty directories.
     * Destructive by design, used by the `wipe confirm` command.
     */
    public static GenerationResult wipeAndRegenerate(Path mobsDir, Path eliteDir, Path shardTypesDir,
                                                       Set<ResourceLocation> validDimensions) {
        deleteJsonFiles(mobsDir);
        deleteJsonFiles(eliteDir);
        return generateAll(mobsDir, eliteDir, shardTypesDir, validDimensions);
    }

    private static void deleteJsonFiles(Path directory) {
        if (!Files.isDirectory(directory)) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.json")) {
            for (Path file : stream) {
                Files.delete(file);
            }
        } catch (IOException e) {
            LOGGER.warn("[{}] Failed to clear {} before regenerating: {}", TAG, directory, e.getMessage());
        }
    }

    private static int[] weightRange(List<ShardType> bucket) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (ShardType shardType : bucket) {
            int weight = shardType.veinWeight().orElse(0);
            min = Math.min(min, weight);
            max = Math.max(max, weight);
        }
        return new int[] {min, max};
    }

    private static void writeFile(Path directory, MobDimensionEntry entry, JsonObject json, List<String> written) {
        String fileName = new ResourceLocation(entry.entityId()).getPath() + "_" + entry.dimensionId().getPath() + ".json";
        try {
            Files.writeString(directory.resolve(fileName), GSON.toJson(json), StandardCharsets.UTF_8);
            written.add(fileName);
        } catch (IOException e) {
            LOGGER.warn("[{}] Failed to write {} to {}: {}", TAG, fileName, directory, e.getMessage());
        }
    }
}
