package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.RiftShardOreDatagen;
import com.pyure.gtrift.common.data.RiftShardOreDatagen.GenerationResult;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Isolated scratch directory, same idiom as ShardTypeLoaderTest — never touches the real config folder,
 * so it can't conflict with the empirical timing checks done against that real folder elsewhere.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftShardOreDatagenWipeTest {

    @GameTest(template = "empty")
    public static void wipeDeletesExistingJsonButPreservesNonJsonThenRegenerates(GameTestHelper helper) {
        Path dir = newScratchDir();
        writeFile(dir, "existing.json", """
                { "type": "fake", "color": "#000000", "outputs": [] }
                """);
        writeFile(dir, "readme.txt", "not a shard type file, should survive a wipe");

        GenerationResult result = RiftShardOreDatagen.wipeAndRegenerate(dir);

        helper.assertTrue(!Files.exists(dir.resolve("existing.json")),
                "expected existing.json to be deleted by wipeAndRegenerate");
        helper.assertTrue(Files.exists(dir.resolve("readme.txt")),
                "expected the non-JSON file to survive a wipe");
        helper.assertTrue(!result.written().isEmpty(),
                "expected wipeAndRegenerate to produce a fresh set from the real ore vein registry, got none");

        helper.succeed();
    }

    private static Path newScratchDir() {
        try {
            return Files.createTempDirectory("gtrift_shard_ore_datagen_wipe_test");
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
