package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.ShardType;
import com.pyure.gtrift.common.data.ShardTypeLoader;
import com.pyure.gtrift.common.data.ShardTypeLoader.ShardTypeLoadResult;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Pure logic — no world state needed, template = "empty", same pattern as RiftEventSpawnerDropTest.
 * Exercises ShardTypeLoader.loadAll(Path) directly against isolated scratch directories rather than
 * the real config folder.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class ShardTypeLoaderTest {

    @GameTest(template = "empty")
    public static void mixedValidAndInvalidFilesProduceExpectedResult(GameTestHelper helper) {
        Path dir = newScratchDir();
        // Deterministic processing order (loadAll sorts by filename) is relied on below.
        writeFile(dir, "a_well_formed.json", """
                {
                  "type": "carebear",
                  "color": "#112233",
                  "outputs": [
                    { "item": "minecraft:diamond", "chance": 0.5 }
                  ]
                }
                """);
        writeFile(dir, "b_bad_output.json", """
                {
                  "type": "badoutput",
                  "color": "#445566",
                  "outputs": [
                    { "item": "NOT A VALID ID", "chance": 0.5 },
                    { "item": "minecraft:iron_ingot", "chance": 0.3 }
                  ]
                }
                """);
        writeFile(dir, "c_degenerate_chance.json", """
                {
                  "type": "degenerate",
                  "color": "#778899",
                  "outputs": [
                    { "item": "minecraft:gold_ingot", "chance": 0 }
                  ]
                }
                """);
        writeFile(dir, "d_dup_first.json", """
                {
                  "type": "Diamond",
                  "color": "#1B9AAA",
                  "outputs": []
                }
                """);
        writeFile(dir, "e_dup_second.json", """
                {
                  "type": "diamond",
                  "color": "#000000",
                  "outputs": []
                }
                """);

        ShardTypeLoadResult result = ShardTypeLoader.loadAll(dir);

        helper.assertTrue(result.shardTypes().size() == 3,
                "expected 3 surviving shard types, got %d: %s".formatted(result.shardTypes().size(), result.shardTypes()));
        assertHasType(helper, result, "carebear", 1);
        assertHasType(helper, result, "badoutput", 1);
        assertHasType(helper, result, "diamond", 0);

        ShardType diamond = findType(result, "diamond").orElseThrow();
        helper.assertTrue(diamond.color() == 0x1B9AAA,
                "expected the FIRST-loaded (d_dup_first, #1B9AAA) definition to win, got color 0x%06X"
                        .formatted(diamond.color()));

        helper.assertTrue(result.issues().size() == 1,
                "expected exactly 1 issue (the degenerate-chance file's rejection), got %d: %s"
                        .formatted(result.issues().size(), result.issues()));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingOutputsIsValidNotAnError(GameTestHelper helper) {
        Path dir = newScratchDir();
        writeFile(dir, "no_outputs.json", """
                {
                  "type": "empty_type",
                  "color": "#ABCDEF"
                }
                """);

        ShardTypeLoadResult result = ShardTypeLoader.loadAll(dir);

        helper.assertTrue(result.issues().isEmpty(), "expected no issues, got %s".formatted(result.issues()));
        assertHasType(helper, result, "empty_type", 0);

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void malformedColorRejectsWholeFile(GameTestHelper helper) {
        Path dir = newScratchDir();
        writeFile(dir, "missing_hash.json", """
                {
                  "type": "no_hash",
                  "color": "112233"
                }
                """);
        writeFile(dir, "wrong_length.json", """
                {
                  "type": "too_short",
                  "color": "#123"
                }
                """);

        ShardTypeLoadResult result = ShardTypeLoader.loadAll(dir);

        helper.assertTrue(result.shardTypes().isEmpty(),
                "expected both malformed-color files to be rejected, got %s".formatted(result.shardTypes()));
        helper.assertTrue(result.issues().isEmpty(),
                "malformed color is warn-tier, not error-tier — expected no issues, got %s".formatted(result.issues()));

        helper.succeed();
    }

    private static void assertHasType(GameTestHelper helper, ShardTypeLoadResult result, String sanitizedId,
                                       int expectedOutputCount) {
        ShardType type = findType(result, sanitizedId)
                .orElseThrow(() -> new AssertionError("expected a surviving shard type '%s', got %s"
                        .formatted(sanitizedId, result.shardTypes())));
        helper.assertTrue(type.outputs().size() == expectedOutputCount,
                "expected '%s' to have %d output(s), got %d".formatted(sanitizedId, expectedOutputCount,
                        type.outputs().size()));
    }

    private static Optional<ShardType> findType(ShardTypeLoadResult result, String sanitizedId) {
        return result.shardTypes().stream().filter(t -> t.sanitizedId().equals(sanitizedId)).findFirst();
    }

    private static Path newScratchDir() {
        try {
            return Files.createTempDirectory("gtrift_shard_type_test");
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
