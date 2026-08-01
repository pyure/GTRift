package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.GTRiftRecipes;
import com.pyure.gtrift.common.data.GTRiftRecipes.ExpandedOutput;
import com.pyure.gtrift.common.data.GTRiftRecipes.ExpansionResult;
import com.pyure.gtrift.common.data.ShardType;
import com.pyure.gtrift.common.data.ShardTypeOutput;
import com.pyure.gtrift.common.item.GTRiftItems;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Pure logic — no world state, template = "empty". Exercises GTRiftRecipes.expandOutputs directly
 * against the shipped "diamond" default's real, loaded ShardType (specs/rift-shard-types.md outputs:
 * raw_diamond chance=0.05, raw_graphite chance=0.30, raw_coal chance=0.10), rather than
 * the real addCentrifugeRecipe/GTRecipeBuilder pipeline or RecipeManager — see
 * RiftShardRecipeTruncationTest's doc comment for why touching the live recipe-builder pipeline from a
 * test is unsafe; querying RecipeManager.getAllRecipesFor turned out not to reliably reflect GTCEu's
 * own recipes either (returned 0 for a real, freshly-registered set), so the pure expansion function is
 * both the safe AND the reliable way to verify this. Confirms the x1/x2/x4/x8 quality-multiplier
 * chance progression (GTRiftRecipes.QUALITY_MULTIPLIER) end to end.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftShardRecipeGenerationTest {

    @GameTest(template = "empty")
    public static void diamondOutputsExpandWithExpectedChanceProgression(GameTestHelper helper) {
        ShardType diamond = GTRiftItems.allShardItems().get("diamond").get().getShardType();
        List<ShardTypeOutput> outputs = diamond.outputs();
        helper.assertTrue(outputs.size() == 3,
                "expected the shipped diamond default to have 3 outputs, got %d: %s".formatted(outputs.size(), outputs));

        assertChances(helper, outputs, 1, List.of(500, 3000, 1000));
        assertChances(helper, outputs, 2, List.of(1000, 6000, 2000));
        assertChances(helper, outputs, 4, List.of(2000, 10000, 2000, 4000));
        assertChances(helper, outputs, 8, List.of(4000, 10000, 10000, 4000, 8000));

        helper.succeed();
    }

    private static void assertChances(GameTestHelper helper, List<ShardTypeOutput> outputs, int multiplier,
                                       List<Integer> expectedChances) {
        ExpansionResult result = GTRiftRecipes.expandOutputs(outputs, multiplier);
        helper.assertTrue(result.truncated().isEmpty(),
                "multiplier %d: expected no truncation, got %s".formatted(multiplier, result.truncated()));

        List<Integer> actualChances = result.outputs().stream().map(ExpandedOutput::chance).toList();
        helper.assertTrue(actualChances.equals(expectedChances),
                "multiplier %d: expected output chances %s, got %s".formatted(multiplier, expectedChances, actualChances));
    }
}
