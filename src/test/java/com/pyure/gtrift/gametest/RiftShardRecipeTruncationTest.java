package com.pyure.gtrift.gametest;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.data.GTRiftRecipes;
import com.pyure.gtrift.common.data.GTRiftRecipes.ExpansionResult;
import com.pyure.gtrift.common.data.ShardTypeOutput;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Pure logic — no world state, template = "empty". Exercises GTRiftRecipes.expandOutputs directly
 * (never the real addCentrifugeRecipe/GTRecipeBuilder pipeline) — an earlier version of this test
 * called the production recipe-building method directly with a synthetic, unregistered "shard type",
 * which doesn't just build a FinishedRecipe in memory: GTRecipeBuilder.save() registers the recipe
 * into GTCEu's live, real recipe-matching structures immediately, regardless of what the passed
 * Consumer<FinishedRecipe> does with it. Since the synthetic type wasn't a real registered item,
 * GTRiftItems.createStack returned ItemStack.EMPTY for the recipe's input, producing a permanently
 * broken recipe (empty input Ingredient) that a real LV Centrifuge elsewhere in the same test run
 * then found and tried to match against, crashing the whole GameTest server
 * (ArrayIndexOutOfBoundsException in SizedIngredient.copy). expandOutputs is pure — it only computes
 * which outputs would be included/truncated, with zero interaction with the recipe registry — so this
 * version can safely use fake data without touching live game state.
 *
 * A deliberately-oversized fixture (3 outputs at chance=0.35, three real existing items — real-item
 * resolution isn't the thing under test here) crosses Centrifuge's real 6-output-slot limit once
 * octupled: each output needs floor(0.35*8)=2 guaranteed + 1 fractional = 3 slots, so only the first 2
 * outputs (6 slots) fit and the 3rd is truncated entirely — confirms truncation (not proportional
 * scale-down, per the plan's confirmed direction) and that truncated output(s) are named rather than
 * silently dropped.
 */
@PrefixGameTestTemplate(false)
@GameTestHolder(GTRift.MOD_ID)
public class RiftShardRecipeTruncationTest {

    @GameTest(template = "empty")
    public static void oversizedShardTypeTruncatesAtSixSlots(GameTestHelper helper) {
        List<ShardTypeOutput> outputs = List.of(
                new ShardTypeOutput(new ResourceLocation("gtceu", "raw_diamond"), 0.35),
                new ShardTypeOutput(new ResourceLocation("gtceu", "raw_graphite"), 0.35),
                new ShardTypeOutput(new ResourceLocation("gtceu", "raw_coal"), 0.35));

        ExpansionResult result = GTRiftRecipes.expandOutputs(outputs, 8);

        helper.assertTrue(result.outputs().size() == 6,
                "expected exactly 6 expanded output units (2 outputs x 3 slots each, 3rd dropped entirely), got %d: %s"
                        .formatted(result.outputs().size(), result.outputs()));
        helper.assertTrue(result.truncated().equals(List.of(new ResourceLocation("gtceu", "raw_coal"))),
                "expected exactly the 3rd output (raw_coal) named as truncated, got %s".formatted(result.truncated()));

        helper.succeed();
    }
}
