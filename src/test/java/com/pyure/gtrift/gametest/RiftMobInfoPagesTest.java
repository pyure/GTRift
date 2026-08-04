package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.client.RiftMobInfoPages;
import com.pyure.gtrift.client.RiftMobInfoPages.InfoPage;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Pure logic — no world state needed, template = "empty", same pattern as ShardTypeLoaderTest.
 * Exercises RiftMobInfoPages.build(Path, Path) directly against isolated scratch directories rather
 * than the real config folder.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftMobInfoPagesTest {

    @GameTest(template = "empty")
    public static void weightZeroEntryIsExcluded(GameTestHelper helper) {
        Path mobsDir = newScratchDir();
        Path eliteDir = newScratchDir();
        writeFile(mobsDir, "zero_weight.json", """
                { "entity": "minecraft:zombie", "weight": 0 }
                """);
        writeFile(mobsDir, "real_weight.json", """
                { "entity": "minecraft:skeleton", "weight": 5 }
                """);

        List<InfoPage> pages = RiftMobInfoPages.build(mobsDir, eliteDir);

        helper.assertTrue(pages.size() == 1, "expected 1 page, got %d".formatted(pages.size()));
        List<Component> lines = pages.get(0).lines();
        helper.assertTrue(lines.size() == 1, "expected 1 surviving line, got %d".formatted(lines.size()));
        helper.assertTrue(lines.get(0).getString().contains("Skeleton"),
                "expected the surviving line to be the skeleton entry, got: %s".formatted(lines.get(0).getString()));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entriesSortedByWeightDescending(GameTestHelper helper) {
        Path mobsDir = newScratchDir();
        Path eliteDir = newScratchDir();
        writeFile(mobsDir, "low.json", """
                { "entity": "minecraft:cow", "weight": 1 }
                """);
        writeFile(mobsDir, "high.json", """
                { "entity": "minecraft:pig", "weight": 10 }
                """);
        writeFile(mobsDir, "mid.json", """
                { "entity": "minecraft:sheep", "weight": 5 }
                """);

        List<InfoPage> pages = RiftMobInfoPages.build(mobsDir, eliteDir);

        helper.assertTrue(pages.size() == 1, "expected 1 page, got %d".formatted(pages.size()));
        List<Component> lines = pages.get(0).lines();
        helper.assertTrue(lines.size() == 3, "expected 3 lines, got %d".formatted(lines.size()));
        helper.assertTrue(lines.get(0).getString().contains("Pig"),
                "expected Pig (weight 10) first, got: %s".formatted(lines.get(0).getString()));
        helper.assertTrue(lines.get(1).getString().contains("Sheep"),
                "expected Sheep (weight 5) second, got: %s".formatted(lines.get(1).getString()));
        helper.assertTrue(lines.get(2).getString().contains("Cow"),
                "expected Cow (weight 1) third, got: %s".formatted(lines.get(2).getString()));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eliteEntryGetsEliteSuffixNormalDoesNot(GameTestHelper helper) {
        Path mobsDir = newScratchDir();
        Path eliteDir = newScratchDir();
        writeFile(mobsDir, "normal_entry.json", """
                { "entity": "minecraft:zombie", "weight": 5 }
                """);
        writeFile(eliteDir, "elite_entry.json", """
                { "entity": "minecraft:skeleton", "weight": 5 }
                """);

        List<InfoPage> pages = RiftMobInfoPages.build(mobsDir, eliteDir);

        helper.assertTrue(pages.size() == 1, "expected 1 page, got %d".formatted(pages.size()));
        List<Component> lines = pages.get(0).lines();
        helper.assertTrue(lines.size() == 2, "expected 2 lines, got %d".formatted(lines.size()));

        String zombieLine = findLine(lines, "Zombie");
        String skeletonLine = findLine(lines, "Skeleton");
        helper.assertTrue(!zombieLine.contains("Elite"),
                "normal-pool entry should not carry [Elite], got: %s".formatted(zombieLine));
        helper.assertTrue(skeletonLine.contains("Elite"),
                "elite-pool entry should carry [Elite], got: %s".formatted(skeletonLine));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unrestrictedAndSpecificDimensionPaging(GameTestHelper helper) {
        Path mobsDir = newScratchDir();
        Path eliteDir = newScratchDir();
        writeFile(mobsDir, "unrestricted.json", """
                { "entity": "minecraft:cow", "weight": 1 }
                """);
        writeFile(mobsDir, "nether_only.json", """
                { "entity": "minecraft:blaze", "weight": 1, "dimensions": ["minecraft:the_nether"] }
                """);

        List<InfoPage> pages = RiftMobInfoPages.build(mobsDir, eliteDir);

        helper.assertTrue(pages.size() == 2, "expected 2 pages, got %d".formatted(pages.size()));

        InfoPage allPage = findPage(pages, "all");
        helper.assertTrue(allPage.title().getString().equals("All dimensions"),
                "expected 'All dimensions' title, got: %s".formatted(allPage.title().getString()));

        InfoPage netherPage = findPage(pages, "minecraft:the_nether");
        helper.assertTrue(netherPage.title().getString().equals("The Nether"),
                "expected 'The Nether' title, got: %s".formatted(netherPage.title().getString()));

        helper.succeed();
    }

    private static String findLine(List<Component> lines, String needle) {
        return lines.stream()
                .map(Component::getString)
                .filter(s -> s.contains(needle))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a line containing '%s', got %s".formatted(needle, lines)));
    }

    private static InfoPage findPage(List<InfoPage> pages, String dimensionKey) {
        return pages.stream()
                .filter(p -> p.dimensionKey().equals(dimensionKey))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a page keyed '%s', got %s".formatted(dimensionKey,
                        pages.stream().map(InfoPage::dimensionKey).toList())));
    }

    private static Path newScratchDir() {
        try {
            return Files.createTempDirectory("gtrift_mob_info_pages_test");
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
