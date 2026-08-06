package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.RiftMobPoolDatagen;
import com.pyure.gtrift.common.data.RiftMobPoolDatagen.DropEconomy;
import com.pyure.gtrift.common.data.RiftMobPoolDatagen.GenerationResult;
import com.pyure.gtrift.common.data.RiftMobPoolDatagen.MobDimensionEntry;
import com.pyure.gtrift.common.data.RiftMobPoolDatagen.RosterResolution;
import com.pyure.gtrift.common.data.ShardType;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Pure-fixture style, mirroring RiftShardOreDatagenTest/ShardTypeLoaderTest — every case here uses
 * synthetic ShardType/roster fixtures, no dependency on the real GTRegistries.ORE_VEINS registry.
 * Only generateAllWritesResolvedPairsAndRespectsDedup touches real file I/O, and even that's entirely
 * against isolated scratch directories.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftMobPoolDatagenTest {

    private static final Gson GSON = new Gson();
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft:overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft:the_nether");
    private static final ResourceLocation END = new ResourceLocation("minecraft:the_end");

    @GameTest(template = "empty")
    public static void rosterResolvesToSeventeenPairsAcrossAllThreeDimensions(GameTestHelper helper) {
        RosterResolution resolution = RiftMobPoolDatagen.resolveRosterEntries(Set.of(OVERWORLD, NETHER, END));

        helper.assertTrue(resolution.resolved().size() == 17,
                "expected 17 roster pairs, got %d: %s".formatted(resolution.resolved().size(), resolution.resolved()));
        helper.assertTrue(resolution.skipped().isEmpty(), "expected no skipped pairs, got %s".formatted(resolution.skipped()));

        long overworldCount = resolution.resolved().stream().filter(e -> e.dimensionId().equals(OVERWORLD)).count();
        long netherCount = resolution.resolved().stream().filter(e -> e.dimensionId().equals(NETHER)).count();
        long endCount = resolution.resolved().stream().filter(e -> e.dimensionId().equals(END)).count();
        helper.assertTrue(overworldCount == 9, "expected 9 Overworld pairs, got %d".formatted(overworldCount));
        helper.assertTrue(netherCount == 5, "expected 5 Nether pairs, got %d".formatted(netherCount));
        helper.assertTrue(endCount == 3, "expected 3 End pairs, got %d".formatted(endCount));

        long endermanCount = resolution.resolved().stream().filter(e -> e.entityId().equals("minecraft:enderman")).count();
        helper.assertTrue(endermanCount == 2, "expected enderman to appear twice, got %d".formatted(endermanCount));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void resolveRosterEntriesSkipsUnknownDimensions(GameTestHelper helper) {
        RosterResolution resolution = RiftMobPoolDatagen.resolveRosterEntries(Set.of(OVERWORLD, NETHER));

        helper.assertTrue(resolution.resolved().stream().noneMatch(e -> e.dimensionId().equals(END)),
                "expected zero End pairs when End isn't a valid dimension, got %s".formatted(resolution.resolved()));
        helper.assertTrue(resolution.skipped().size() == 3,
                "expected exactly 3 skipped End pairs, got %d: %s".formatted(resolution.skipped().size(), resolution.skipped()));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bucketShardTypesByDimensionExcludesUndatedAndUnresolvable(GameTestHelper helper) {
        ShardType noDimensions = shardType("no_dims", Optional.empty(), OptionalInt.of(10));
        ShardType unresolvable = shardType("unresolvable",
                Optional.of(Set.of(new ResourceLocation("gtrift_test:fake_dim"))), OptionalInt.of(10));
        ShardType shardB = shardType("overworld_b", Optional.of(Set.of(OVERWORLD)), OptionalInt.of(10));
        ShardType shardA = shardType("overworld_a", Optional.of(Set.of(OVERWORLD)), OptionalInt.of(20));

        Map<ResourceLocation, List<ShardType>> buckets = RiftMobPoolDatagen.bucketShardTypesByDimension(
                List.of(noDimensions, unresolvable, shardB, shardA), Set.of(OVERWORLD, NETHER, END));

        helper.assertTrue(!buckets.containsKey(new ResourceLocation("gtrift_test:fake_dim")),
                "expected the unresolvable dimension to never appear as a bucket key, got %s".formatted(buckets.keySet()));
        List<ShardType> overworldBucket = buckets.get(OVERWORLD);
        helper.assertTrue(overworldBucket != null && overworldBucket.size() == 2,
                "expected exactly the 2 real overworld shard types, got %s".formatted(overworldBucket));
        helper.assertTrue(overworldBucket.get(0).sanitizedId().equals("overworld_a"),
                "expected sanitizedId-sorted order (overworld_a before overworld_b), got %s".formatted(overworldBucket));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void assignShardTypesRoundRobinSplitsUnevenlyAndCoversEveryShardType(GameTestHelper helper) {
        MobDimensionEntry mobA = new MobDimensionEntry("minecraft:zombie", OVERWORLD);
        MobDimensionEntry mobB = new MobDimensionEntry("minecraft:skeleton", OVERWORLD);
        List<ShardType> bucket = List.of(
                shardType("s1", Optional.of(Set.of(OVERWORLD)), OptionalInt.of(10)),
                shardType("s2", Optional.of(Set.of(OVERWORLD)), OptionalInt.of(20)),
                shardType("s3", Optional.of(Set.of(OVERWORLD)), OptionalInt.of(30)),
                shardType("s4", Optional.of(Set.of(OVERWORLD)), OptionalInt.of(40)),
                shardType("s5", Optional.of(Set.of(OVERWORLD)), OptionalInt.of(50)));

        Map<MobDimensionEntry, List<ShardType>> assignments = RiftMobPoolDatagen.assignShardTypes(
                List.of(mobA, mobB), Map.of(OVERWORLD, bucket));

        int sizeA = assignments.get(mobA).size();
        int sizeB = assignments.get(mobB).size();
        helper.assertTrue(Set.of(sizeA, sizeB).equals(Set.of(2, 3)),
                "expected a 3/2 split, got %d and %d".formatted(sizeA, sizeB));

        Set<String> allAssignedIds = new HashSet<>();
        for (ShardType s : assignments.get(mobA)) allAssignedIds.add(s.sanitizedId());
        for (ShardType s : assignments.get(mobB)) allAssignedIds.add(s.sanitizedId());
        helper.assertTrue(allAssignedIds.size() == 5,
                "expected all 5 distinct shard types assigned exactly once, got %s".formatted(allAssignedIds));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void assignShardTypesEmptyBucketProducesEmptyAssignmentsNotError(GameTestHelper helper) {
        MobDimensionEntry mob = new MobDimensionEntry("minecraft:shulker", END);

        Map<MobDimensionEntry, List<ShardType>> assignments = RiftMobPoolDatagen.assignShardTypes(List.of(mob), Map.of());

        helper.assertTrue(assignments.containsKey(mob), "expected the mob to still have an entry in the result");
        helper.assertTrue(assignments.get(mob).isEmpty(),
                "expected an empty (not missing/erroring) assignment, got %s".formatted(assignments.get(mob)));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void computeDropEconomyEndpointsOfRange(GameTestHelper helper) {
        ShardType commonType = shardType("common", Optional.of(Set.of(OVERWORLD)), OptionalInt.of(100));
        ShardType rareType = shardType("rare", Optional.of(Set.of(OVERWORLD)), OptionalInt.of(5));

        DropEconomy commonEconomy = RiftMobPoolDatagen.computeDropEconomy(commonType, 5, 100);
        helper.assertTrue(Math.abs(commonEconomy.chance() - 0.9) < 1e-9,
                "expected chance ~0.9 for the most common vein, got %s".formatted(commonEconomy.chance()));
        helper.assertTrue(commonEconomy.minTierName().equals("ulv"),
                "expected min_tier ulv for the most common vein, got %s".formatted(commonEconomy.minTierName()));

        DropEconomy rareEconomy = RiftMobPoolDatagen.computeDropEconomy(rareType, 5, 100);
        helper.assertTrue(Math.abs(rareEconomy.chance() - 0.15) < 1e-9,
                "expected chance ~0.15 for the rarest vein, got %s".formatted(rareEconomy.chance()));
        helper.assertTrue(rareEconomy.minTierName().equals("mv"),
                "expected min_tier mv for the rarest vein, got %s".formatted(rareEconomy.minTierName()));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void computeDropEconomySingleVeinBucketDoesNotDivideByZero(GameTestHelper helper) {
        ShardType onlyType = shardType("only", Optional.of(Set.of(OVERWORLD)), OptionalInt.of(50));

        DropEconomy economy = RiftMobPoolDatagen.computeDropEconomy(onlyType, 50, 50);

        helper.assertTrue(!Double.isNaN(economy.chance()) && !Double.isInfinite(economy.chance()),
                "expected a real chance value, got %s".formatted(economy.chance()));
        helper.assertTrue(Math.abs(economy.chance() - 0.9) < 1e-9,
                "expected the common-band chance for a single-vein bucket, got %s".formatted(economy.chance()));
        helper.assertTrue(economy.minTierName().equals("ulv"),
                "expected the common-band tier for a single-vein bucket, got %s".formatted(economy.minTierName()));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void buildMobPoolFileJsonNormalOmitsEliteFieldsElitePoolIncludesThem(GameTestHelper helper) {
        MobDimensionEntry mob = new MobDimensionEntry("minecraft:zombie", OVERWORLD);
        List<ShardType> assigned = List.of(shardType("iron", Optional.of(Set.of(OVERWORLD)), OptionalInt.of(50)));

        JsonObject normalJson = RiftMobPoolDatagen.buildMobPoolFileJson(mob, assigned, 50, 50, false);
        helper.assertTrue(normalJson.get("entity").getAsString().equals("minecraft:zombie"),
                "expected entity minecraft:zombie, got %s".formatted(normalJson.get("entity")));
        helper.assertTrue(normalJson.get("weight").getAsInt() == 100,
                "expected flat weight 100, got %s".formatted(normalJson.get("weight")));
        JsonArray dimensionsArray = normalJson.getAsJsonArray("dimensions");
        helper.assertTrue(dimensionsArray.size() == 1 && dimensionsArray.get(0).getAsString().equals("minecraft:overworld"),
                "expected a single-element dimensions array, got %s".formatted(dimensionsArray));

        JsonObject normalDrop = normalJson.getAsJsonArray("drops").get(0).getAsJsonObject();
        helper.assertTrue(normalDrop.get("type").getAsString().equals("iron"), "expected drop type iron, got %s".formatted(normalDrop));
        helper.assertTrue(normalDrop.get("quality").getAsString().equals("normal"), "expected quality normal, got %s".formatted(normalDrop));
        helper.assertTrue(!normalDrop.has("elite_chance_multiplier") && !normalDrop.has("elite_amount_multiplier"),
                "expected no elite multiplier keys on the normal pool, got %s".formatted(normalDrop));

        JsonObject eliteJson = RiftMobPoolDatagen.buildMobPoolFileJson(mob, assigned, 50, 50, true);
        JsonObject eliteDrop = eliteJson.getAsJsonArray("drops").get(0).getAsJsonObject();
        helper.assertTrue(eliteDrop.has("elite_chance_multiplier") && eliteDrop.has("elite_amount_multiplier"),
                "expected elite multiplier keys on the elite pool, got %s".formatted(eliteDrop));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void alreadyCoveredFindsPairRegardlessOfFilename(GameTestHelper helper) {
        Path dir = newScratchDir();
        writeFile(dir, "some_arbitrary_name.json", """
                {
                  "entity": "minecraft:zombie",
                  "weight": 100,
                  "dimensions": ["minecraft:overworld"],
                  "drops": []
                }
                """);

        Set<MobDimensionEntry> covered = RiftMobPoolDatagen.alreadyCovered(dir);

        helper.assertTrue(covered.equals(Set.of(new MobDimensionEntry("minecraft:zombie", OVERWORLD))),
                "expected exactly the (zombie, overworld) pair, got %s".formatted(covered));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void generateAllWritesResolvedPairsAndRespectsDedup(GameTestHelper helper) {
        Path shardTypesDir = newScratchDir();
        writeFile(shardTypesDir, "iron.json", """
                {
                  "type": "iron",
                  "color": "#111111",
                  "outputs": [],
                  "dimensions": ["minecraft:overworld"],
                  "vein_weight": 80
                }
                """);
        writeFile(shardTypesDir, "coal.json", """
                {
                  "type": "coal",
                  "color": "#222222",
                  "outputs": [],
                  "dimensions": ["minecraft:overworld"],
                  "vein_weight": 40
                }
                """);
        writeFile(shardTypesDir, "sulfur.json", """
                {
                  "type": "sulfur",
                  "color": "#333333",
                  "outputs": [],
                  "dimensions": ["minecraft:the_nether"],
                  "vein_weight": 100
                }
                """);
        // Deliberately no End-dimension fixture — exercises the "empty bucket" edge case for real.

        Path mobsDir = newScratchDir();
        Path eliteDir = newScratchDir();
        // Pre-seed one already-covered pair in mobsDir only, to confirm per-directory dedup independence.
        writeFile(mobsDir, "existing_zombie.json", """
                {
                  "entity": "minecraft:zombie",
                  "weight": 50,
                  "dimensions": ["minecraft:overworld"],
                  "drops": []
                }
                """);

        GenerationResult result = RiftMobPoolDatagen.generateAll(mobsDir, eliteDir, shardTypesDir,
                Set.of(OVERWORLD, NETHER, END));

        helper.assertTrue(!result.writtenNormal().contains("zombie_overworld.json"),
                "expected the already-covered zombie/overworld pair NOT to be regenerated in mobsDir, got %s"
                        .formatted(result.writtenNormal()));
        helper.assertTrue(result.writtenElite().contains("zombie_overworld.json"),
                "expected zombie/overworld to be freshly generated in eliteDir (nothing pre-existing there), got %s"
                        .formatted(result.writtenElite()));
        helper.assertTrue(result.writtenNormal().size() == 16,
                "expected 16 new normal files (17 roster pairs minus the 1 already covered), got %d: %s"
                        .formatted(result.writtenNormal().size(), result.writtenNormal()));
        helper.assertTrue(result.writtenElite().size() == 17,
                "expected all 17 elite files freshly generated, got %d: %s"
                        .formatted(result.writtenElite().size(), result.writtenElite()));

        JsonObject skeletonJson = readJson(mobsDir.resolve("skeleton_overworld.json"));
        JsonArray skeletonDrops = skeletonJson.getAsJsonArray("drops");
        helper.assertTrue(skeletonDrops.size() > 0,
                "expected skeleton/overworld to have real drops from the iron/coal fixtures, got %s".formatted(skeletonDrops));

        JsonObject shulkerJson = readJson(mobsDir.resolve("shulker_the_end.json"));
        helper.assertTrue(shulkerJson.getAsJsonArray("drops").size() == 0,
                "expected shulker/the_end to have zero drops (empty End bucket, no fixture seeded), got %s"
                        .formatted(shulkerJson.getAsJsonArray("drops")));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void maybeGenerateNoOpsWhenBothDirectoriesAlreadyExist(GameTestHelper helper) {
        Path mobsDir = newScratchDir();
        Path eliteDir = newScratchDir();
        Path shardTypesDir = newScratchDir(); // empty; should never even be read in this scenario

        GenerationResult result = RiftMobPoolDatagen.maybeGenerate(mobsDir, eliteDir, shardTypesDir,
                Set.of(OVERWORLD, NETHER, END));

        helper.assertTrue(result.writtenNormal().isEmpty() && result.writtenElite().isEmpty(),
                "expected a true no-op when both directories already exist, got %s / %s"
                        .formatted(result.writtenNormal(), result.writtenElite()));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void maybeGenerateRunsWhenOnlyOneDirectoryIsMissingAndRespectsExistingDedup(GameTestHelper helper) {
        Path shardTypesDir = newScratchDir();
        writeFile(shardTypesDir, "iron.json", """
                {
                  "type": "iron",
                  "color": "#111111",
                  "outputs": [],
                  "dimensions": ["minecraft:overworld"],
                  "vein_weight": 80
                }
                """);

        Path mobsDir = newScratchDir(); // exists, pre-seeded with one already-covered pair
        writeFile(mobsDir, "existing_zombie.json", """
                {
                  "entity": "minecraft:zombie",
                  "weight": 50,
                  "dimensions": ["minecraft:overworld"],
                  "drops": []
                }
                """);
        Path eliteDir = mobsDir.getParent().resolve("gtrift_mob_pool_datagen_missing_" + System.nanoTime());
        // eliteDir deliberately never created — genuinely doesn't exist yet.

        GenerationResult result = RiftMobPoolDatagen.maybeGenerate(mobsDir, eliteDir, shardTypesDir,
                Set.of(OVERWORLD, NETHER, END));

        helper.assertTrue(!result.writtenNormal().contains("zombie_overworld.json"),
                "expected the already-covered zombie/overworld pair NOT to be regenerated in mobsDir, got %s"
                        .formatted(result.writtenNormal()));
        helper.assertTrue(result.writtenNormal().size() == 16,
                "expected 16 new normal files (17 roster pairs minus the 1 already covered), got %d: %s"
                        .formatted(result.writtenNormal().size(), result.writtenNormal()));
        helper.assertTrue(result.writtenElite().size() == 17,
                "expected all 17 elite files freshly generated since eliteDir didn't exist, got %d: %s"
                        .formatted(result.writtenElite().size(), result.writtenElite()));
        helper.assertTrue(Files.isDirectory(eliteDir), "expected eliteDir to have been created by generateAll");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void legacyShardTypesWithNoDimensionDataProduceEmptyDropsNotCrash(GameTestHelper helper) {
        // Real repro found via a live runClient install: a rift_shard_types/ folder generated before
        // ShardType gained dimensions/vein_weight has every entry excluded from every bucket (see
        // ShardType's own doc comment), so every mob-pool file gets an empty drops array. Confirms
        // generateAll still succeeds cleanly (real files, just empty drops) rather than throwing, and
        // locks in the exact shape of the bug that prompted RiftMobPoolDatagen's own diagnostic warning.
        Path shardTypesDir = newScratchDir();
        writeFile(shardTypesDir, "legacy_iron.json", """
                {
                  "type": "legacy_iron",
                  "color": "#111111",
                  "outputs": []
                }
                """);

        Path mobsDir = newScratchDir();
        Path eliteDir = newScratchDir();

        GenerationResult result = RiftMobPoolDatagen.generateAll(mobsDir, eliteDir, shardTypesDir,
                Set.of(OVERWORLD, NETHER, END));

        helper.assertTrue(result.writtenNormal().size() == 17,
                "expected all 17 roster files to still be written despite the legacy shard type, got %d: %s"
                        .formatted(result.writtenNormal().size(), result.writtenNormal()));

        JsonObject zombieJson = readJson(mobsDir.resolve("zombie_overworld.json"));
        helper.assertTrue(zombieJson.getAsJsonArray("drops").size() == 0,
                "expected zero drops (the legacy shard type carries no dimension data to bucket it), got %s"
                        .formatted(zombieJson.getAsJsonArray("drops")));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void wipeAndRegenerateClearsOnlyJsonFilesThenRegenerates(GameTestHelper helper) {
        Path shardTypesDir = newScratchDir();
        writeFile(shardTypesDir, "iron.json", """
                {
                  "type": "iron",
                  "color": "#111111",
                  "outputs": [],
                  "dimensions": ["minecraft:overworld"],
                  "vein_weight": 80
                }
                """);

        Path mobsDir = newScratchDir();
        writeFile(mobsDir, "stale_hand_authored.json", """
                {
                  "entity": "minecraft:zombie",
                  "weight": 50,
                  "dimensions": ["minecraft:overworld"],
                  "drops": []
                }
                """);
        writeFile(mobsDir, "keep_me.txt", "not a shard/mob file, should survive the wipe");
        Path eliteDir = newScratchDir();

        RiftMobPoolDatagen.GenerationResult result = RiftMobPoolDatagen.wipeAndRegenerate(mobsDir, eliteDir,
                shardTypesDir, Set.of(OVERWORLD, NETHER, END));

        helper.assertTrue(!Files.exists(mobsDir.resolve("stale_hand_authored.json")),
                "expected the pre-existing json file to be deleted by the wipe");
        helper.assertTrue(Files.exists(mobsDir.resolve("keep_me.txt")),
                "expected the non-json file to survive the wipe");
        // Everything regenerated fresh (nothing left to dedup against after the wipe), so the wiped
        // pair (zombie/overworld) is now freshly written too, unlike the additive fill scenario above.
        helper.assertTrue(result.writtenNormal().contains("zombie_overworld.json"),
                "expected zombie/overworld to be freshly regenerated after the wipe, got %s"
                        .formatted(result.writtenNormal()));
        helper.assertTrue(result.writtenNormal().size() == 17,
                "expected all 17 roster pairs freshly regenerated in mobsDir, got %d: %s"
                        .formatted(result.writtenNormal().size(), result.writtenNormal()));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void ensureShardTypesExistNoOpsWhenPresentGeneratesWhenAbsent(GameTestHelper helper) {
        Path presentDir = newScratchDir();
        writeFile(presentDir, "manual.json", """
                {
                  "type": "manual",
                  "color": "#123456",
                  "outputs": []
                }
                """);

        RiftMobPoolDatagen.ensureShardTypesExist(presentDir);
        helper.assertTrue(Files.exists(presentDir.resolve("manual.json")),
                "expected the pre-existing hand-authored file to be left untouched (no-op)");

        Path absentDir = presentDir.getParent().resolve("gtrift_mob_pool_datagen_missing_shard_types_" + System.nanoTime());
        helper.assertTrue(!Files.exists(absentDir), "test setup assumption: absentDir must not already exist");

        RiftMobPoolDatagen.ensureShardTypesExist(absentDir);
        helper.assertTrue(Files.isDirectory(absentDir),
                "expected ensureShardTypesExist to create the directory and generate real shard types");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collectValidDimensionsReturnsRealLoadedDimensions(GameTestHelper helper) {
        Set<ResourceLocation> validDimensions =
                RiftMobPoolDatagen.collectValidDimensions(helper.getLevel().getServer().registryAccess());

        helper.assertTrue(validDimensions.contains(OVERWORLD),
                "expected the real server's Overworld to be present, got %s".formatted(validDimensions));
        helper.assertTrue(validDimensions.contains(NETHER),
                "expected the real server's Nether to be present, got %s".formatted(validDimensions));
        helper.assertTrue(validDimensions.contains(END),
                "expected the real server's End to be present, got %s".formatted(validDimensions));

        helper.succeed();
    }

    private static ShardType shardType(String sanitizedId, Optional<Set<ResourceLocation>> dimensions, OptionalInt veinWeight) {
        return new ShardType(sanitizedId, sanitizedId, 0x000000, List.of(), dimensions, veinWeight);
    }

    private static JsonObject readJson(Path file) {
        try {
            return GSON.fromJson(Files.readString(file), JsonElement.class).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path newScratchDir() {
        try {
            return Files.createTempDirectory("gtrift_mob_pool_datagen_test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeFile(Path dir, String name, String content) {
        try {
            Files.writeString(dir.resolve(name), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
