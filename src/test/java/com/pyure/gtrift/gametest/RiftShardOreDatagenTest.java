package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.RiftShardOreDatagen;
import com.pyure.gtrift.common.data.RiftShardOreDatagen.GenerationResult;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import it.unimi.dsi.fastutil.objects.ObjectIntPair;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Pure logic (buildShardTypeJson) plus one structural real-registry check (generateAll), same
 * isolated-scratch-directory idiom as ShardTypeLoaderTest. Uses real GTMaterials constants as fixtures
 * rather than a real ore vein registry — GTMaterials.SolderingAlloy is a crafted alloy, never a worldgen
 * ore, used here specifically because it has no TagPrefix.rawOre item.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftShardOreDatagenTest {

    private static final Gson GSON = new Gson();

    @GameTest(template = "empty")
    public static void chanceNormalizedAgainstPreFilterSum(GameTestHelper helper) {
        JsonObject json = RiftShardOreDatagen.buildShardTypeJson(veinId("diamond_and_copper"), List.of(
                ObjectIntPair.of(GTMaterials.Diamond, 30),
                ObjectIntPair.of(GTMaterials.Copper, 70)));

        JsonArray outputs = json.getAsJsonArray("outputs");
        helper.assertTrue(outputs.size() == 2, "expected 2 output entries, got %d".formatted(outputs.size()));
        assertChance(helper, outputs, rawOreId(GTMaterials.Diamond), 0.3);
        assertChance(helper, outputs, rawOreId(GTMaterials.Copper), 0.7);

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void materialWithNoRawOreDroppedWithoutInflatingRest(GameTestHelper helper) {
        helper.assertTrue(ChemicalHelper.get(TagPrefix.rawOre, GTMaterials.SolderingAlloy).isEmpty(),
                "expected SolderingAlloy to have no raw-ore item (test fixture assumption)");

        JsonObject json = RiftShardOreDatagen.buildShardTypeJson(veinId("mixed_vein"), List.of(
                ObjectIntPair.of(GTMaterials.Diamond, 20),
                ObjectIntPair.of(GTMaterials.SolderingAlloy, 30),
                ObjectIntPair.of(GTMaterials.Copper, 50)));

        JsonArray outputs = json.getAsJsonArray("outputs");
        helper.assertTrue(outputs.size() == 2,
                "expected the SolderingAlloy entry dropped, 2 remaining, got %d".formatted(outputs.size()));
        // NOT re-normalized to 20/70 and 50/70 — still fractions of the original 100 total.
        assertChance(helper, outputs, rawOreId(GTMaterials.Diamond), 0.2);
        assertChance(helper, outputs, rawOreId(GTMaterials.Copper), 0.5);

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void allFilteredVeinProducesEmptyOutputs(GameTestHelper helper) {
        JsonObject json = RiftShardOreDatagen.buildShardTypeJson(veinId("alloy_only"), List.of(
                ObjectIntPair.of(GTMaterials.SolderingAlloy, 100)));

        JsonArray outputs = json.getAsJsonArray("outputs");
        helper.assertTrue(outputs.size() == 0, "expected empty outputs, got %s".formatted(outputs));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zeroSumChancesProducesEmptyOutputsNotCrash(GameTestHelper helper) {
        JsonObject json = RiftShardOreDatagen.buildShardTypeJson(veinId("degenerate_vein"), List.of(
                ObjectIntPair.of(GTMaterials.Diamond, 0),
                ObjectIntPair.of(GTMaterials.Copper, 0)));

        JsonArray outputs = json.getAsJsonArray("outputs");
        helper.assertTrue(outputs.size() == 0, "expected empty outputs (not NaN/crash), got %s".formatted(outputs));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void colorIsDeterministicPerVeinIdAndDiffersAcrossIds(GameTestHelper helper) {
        List<ObjectIntPair<Material>> materials = List.of(ObjectIntPair.of(GTMaterials.Diamond, 100));

        String colorA1 = RiftShardOreDatagen.buildShardTypeJson(veinId("alpha_vein"), materials).get("color").getAsString();
        String colorA2 = RiftShardOreDatagen.buildShardTypeJson(veinId("alpha_vein"), materials).get("color").getAsString();
        helper.assertTrue(colorA1.equals(colorA2),
                "expected the same vein id to produce the same color twice, got %s vs %s".formatted(colorA1, colorA2));

        String colorB = RiftShardOreDatagen.buildShardTypeJson(veinId("beta_vein"), materials).get("color").getAsString();
        helper.assertTrue(!colorA1.equals(colorB),
                "expected different vein ids to produce different colors, both got %s".formatted(colorA1));

        helper.assertTrue(colorA1.matches("#[0-9A-F]{6}"), "expected a '#RRGGBB' hex color, got %s".formatted(colorA1));

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void generateAllWritesValidFilesToScratchDirectory(GameTestHelper helper) {
        Path dir = newScratchDir();

        GenerationResult result = RiftShardOreDatagen.generateAll(dir);

        helper.assertTrue(!result.written().isEmpty(),
                "expected at least one shard type generated from the real loaded ore vein registry, got none");

        int fileCount = 0;
        try {
            for (Path file : (Iterable<Path>) Files.newDirectoryStream(dir, "*.json")) {
                fileCount++;
                JsonObject json = GSON.fromJson(Files.readString(file), JsonElement.class).getAsJsonObject();
                helper.assertTrue(json.has("type") && json.has("color") && json.has("outputs"),
                        "expected type/color/outputs keys in %s, got %s".formatted(file, json));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        helper.assertTrue(fileCount == result.written().size(),
                "expected file count (%d) to match written count (%d)".formatted(fileCount, result.written().size()));

        helper.succeed();
    }

    private static void assertChance(GameTestHelper helper, JsonArray outputs, String itemId, double expected) {
        for (JsonElement element : outputs) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.get("item").getAsString().equals(itemId)) {
                double actual = entry.get("chance").getAsDouble();
                helper.assertTrue(Math.abs(actual - expected) < 1e-9,
                        "expected chance %s for %s, got %s".formatted(expected, itemId, actual));
                return;
            }
        }
        throw new AssertionError("expected an output entry for '%s', got %s".formatted(itemId, outputs));
    }

    private static String rawOreId(Material material) {
        ItemStack stack = ChemicalHelper.get(TagPrefix.rawOre, material);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id.toString();
    }

    private static ResourceLocation veinId(String path) {
        return new ResourceLocation("gtrift_test", path);
    }

    private static Path newScratchDir() {
        try {
            return Files.createTempDirectory("gtrift_shard_ore_datagen_test");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
