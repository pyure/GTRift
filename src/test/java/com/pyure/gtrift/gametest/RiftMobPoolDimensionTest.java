package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.RiftMobPoolEntry;
import com.pyure.gtrift.common.data.RiftMobPoolLoader;
import com.pyure.gtrift.common.data.RiftMobPoolLoader.DimensionParseResult;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

/**
 * Pure logic — no world state, template = "empty". Exercises RiftMobPoolLoader.parseDimensions
 * directly against a synthetic valid-dimension set, matching ShardTypeLoaderTest's convention of
 * testing loader logic without going through a real reload cycle.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftMobPoolDimensionTest {

    private static final ResourceKey<Level> OVERWORLD =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("minecraft:overworld"));
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation("minecraft:the_nether"));
    private static final Set<ResourceKey<Level>> VALID_DIMENSIONS = Set.of(OVERWORLD, NETHER);

    @GameTest(template = "empty")
    public static void omittedFieldMeansUnrestricted(GameTestHelper helper) {
        DimensionParseResult result = parse("{}");
        helper.assertTrue(result.dimensions().isEmpty(), "expected unrestricted, got " + result.dimensions());
        helper.assertTrue(result.issues().isEmpty(), "expected no issues, got " + result.issues());
        helper.assertTrue(!result.fatal(), "expected not fatal");
        assertEligible(helper, result, OVERWORLD, true);
        assertEligible(helper, result, NETHER, true);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void bareStringIsShorthandForSingleElementArray(GameTestHelper helper) {
        DimensionParseResult result = parse("{\"dimensions\": \"minecraft:the_nether\"}");
        helper.assertTrue(result.dimensions().isPresent() && result.dimensions().get().equals(Set.of(NETHER)),
                "expected {nether}, got " + result.dimensions());
        helper.assertTrue(result.issues().isEmpty(), "expected no issues, got " + result.issues());
        assertEligible(helper, result, NETHER, true);
        assertEligible(helper, result, OVERWORLD, false);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void arrayOfIdsRestrictsToExactSet(GameTestHelper helper) {
        DimensionParseResult result = parse("{\"dimensions\": [\"minecraft:the_nether\", \"minecraft:overworld\"]}");
        helper.assertTrue(
                result.dimensions().isPresent() && result.dimensions().get().equals(Set.of(NETHER, OVERWORLD)),
                "expected {nether, overworld}, got " + result.dimensions());
        helper.assertTrue(result.issues().isEmpty(), "expected no issues, got " + result.issues());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allAloneMeansUnrestricted(GameTestHelper helper) {
        DimensionParseResult result = parse("{\"dimensions\": \"all\"}");
        helper.assertTrue(result.dimensions().isEmpty(), "expected unrestricted, got " + result.dimensions());
        helper.assertTrue(result.issues().isEmpty(), "expected no issues, got " + result.issues());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyArrayMeansNeverEligible(GameTestHelper helper) {
        DimensionParseResult result = parse("{\"dimensions\": []}");
        helper.assertTrue(result.dimensions().isPresent() && result.dimensions().get().isEmpty(),
                "expected present-and-empty (never), got " + result.dimensions());
        helper.assertTrue(result.issues().isEmpty(), "expected no issues, got " + result.issues());
        assertEligible(helper, result, OVERWORLD, false);
        assertEligible(helper, result, NETHER, false);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allMixedWithIdsWarnsAndTreatsAsUnrestricted(GameTestHelper helper) {
        DimensionParseResult result = parse("{\"dimensions\": [\"all\", \"minecraft:the_nether\"]}");
        helper.assertTrue(result.dimensions().isEmpty(), "expected unrestricted, got " + result.dimensions());
        helper.assertTrue(result.issues().size() == 1, "expected exactly 1 issue, got " + result.issues());
        helper.assertTrue(!result.fatal(), "expected not fatal — entry should still be usable");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownDimensionIsFatalAndSkipsEntry(GameTestHelper helper) {
        DimensionParseResult result = parse("{\"dimensions\": \"minecraft:does_not_exist\"}");
        helper.assertTrue(result.fatal(), "expected fatal — unknown dimension should skip the whole entry");
        helper.assertTrue(result.issues().size() == 1, "expected exactly 1 issue, got " + result.issues());
        helper.succeed();
    }

    private static void assertEligible(GameTestHelper helper, DimensionParseResult result,
                                        ResourceKey<Level> dimension, boolean expected) {
        RiftMobPoolEntry entry = new RiftMobPoolEntry(EntityType.ZOMBIE, 1, List.of(), result.dimensions());
        helper.assertTrue(entry.isEligibleFor(dimension) == expected,
                "expected isEligibleFor(%s) == %b for %s".formatted(dimension.location(), expected, result.dimensions()));
    }

    private static DimensionParseResult parse(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        return RiftMobPoolLoader.parseDimensions(object, "test_pool", "test_source", VALID_DIMENSIONS);
    }
}
