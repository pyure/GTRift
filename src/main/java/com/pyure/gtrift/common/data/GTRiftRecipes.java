package com.pyure.gtrift.common.data;

import com.pyure.gtrift.common.item.GTRiftItems;
import com.pyure.gtrift.common.item.RiftRichness;
import com.pyure.gtrift.common.item.RiftShardItem;

import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.ingredient.nbtpredicate.NBTPredicates;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Generates one Centrifuge recipe per (ShardType x RiftRichness), reading `outputs` straight from the
 * shard type's own loaded data (see ShardTypeLoader/ShardType) — real GT ore items GTRift has no
 * opinion about, per specs/rift-shard-types.md.
 */
public class GTRiftRecipes {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");

    /**
     * Unrefined placeholder pending playtesting — doubles per richness tier, matching the old
     * fixed-affinity system's exact ladder, same as this project's existing convention for EUt/duration
     * numbers.
     */
    private static final Map<RiftRichness, Integer> RICHNESS_MULTIPLIER = new EnumMap<>(RiftRichness.class);
    static {
        RICHNESS_MULTIPLIER.put(RiftRichness.SPARSE, 1);
        RICHNESS_MULTIPLIER.put(RiftRichness.NORMAL, 2);
        RICHNESS_MULTIPLIER.put(RiftRichness.RICH, 4);
        RICHNESS_MULTIPLIER.put(RiftRichness.EXTREMELY_RICH, 8);
    }

    /** GTRecipeTypes.CENTRIFUGE_RECIPES.setMaxIOSize(2, 6, 1, 6) — confirmed against the real 7.5.3 jar. */
    static final int MAX_OUTPUT_SLOTS = 6;

    /** ChanceLogic.getMaxChancedValue() — chance/tierChanceBoost are expressed on this 0-10000 scale. */
    static final int MAX_CHANCE = ChanceLogic.getMaxChancedValue();

    /** One already-expanded, guaranteed-or-chanced output unit: `chance` is on the 0-MAX_CHANCE scale. */
    public record ExpandedOutput(Item item, int chance) {}

    /** `truncated` names every output (by item id) dropped once MAX_OUTPUT_SLOTS was reached. */
    public record ExpansionResult(List<ExpandedOutput> outputs, List<ResourceLocation> truncated) {}

    public static void init(Consumer<FinishedRecipe> consumer) {
        for (ItemEntry<RiftShardItem> entry : GTRiftItems.allShardItems().values()) {
            ShardType type = entry.get().getShardType();
            for (RiftRichness richness : RiftRichness.values()) {
                addCentrifugeRecipe(consumer, type, richness);
            }
        }
    }

    private static void addCentrifugeRecipe(Consumer<FinishedRecipe> consumer, ShardType type,
                                             RiftRichness richness) {
        String id = "gtrift:centrifuge/%s_shard_%s".formatted(type.sanitizedId(), richness.name().toLowerCase(Locale.ROOT));
        int multiplier = RICHNESS_MULTIPLIER.get(richness);

        ExpansionResult expansion = expandOutputs(type.outputs(), multiplier);
        if (!expansion.truncated().isEmpty()) {
            LOGGER.warn("Centrifuge recipe '{}' would exceed {} output slots; truncating remaining output(s) {}",
                    id, MAX_OUTPUT_SLOTS, expansion.truncated());
        }

        GTRecipeBuilder builder = GTRecipeTypes.CENTRIFUGE_RECIPES.recipeBuilder(id)
                .inputItemNbtPredicate(GTRiftItems.createStack(type.sanitizedId(), richness, 1),
                        NBTPredicates.eq("Richness", richness.name()))
                .duration(100)
                .EUt(10);

        for (ExpandedOutput output : expansion.outputs()) {
            builder.chancedOutput(new ItemStack(output.item()), output.chance(), 0);
        }

        builder.save(consumer);
    }

    /**
     * Pure — no GTRecipeBuilder/recipe-registry involvement at all, so this (and only this) is safe to
     * call directly from a test with synthetic/fake data. Expands each output's `chance * multiplier`
     * into whole guaranteed units (chance=MAX_CHANCE) plus one fractional unit for the remainder, per
     * the spec's own worked example ("a chance of 2.5 means guaranteed 2, plus a 50% chance of a 3rd"),
     * then truncates (not proportionally scales) once MAX_OUTPUT_SLOTS would be exceeded — stopping at
     * the first output that no longer fits and naming it plus everything after it as truncated.
     */
    public static ExpansionResult expandOutputs(List<ShardTypeOutput> outputs, int multiplier) {
        List<ExpandedOutput> expanded = new ArrayList<>();
        List<ResourceLocation> truncated = new ArrayList<>();
        int usedSlots = 0;

        for (int i = 0; i < outputs.size(); i++) {
            ShardTypeOutput output = outputs.get(i);

            // ForgeRegistries.ITEMS.getValue returns the registry's default (Items.AIR), not null,
            // for an id that was never registered — containsKey is the only reliable "does this
            // resolve to a real item" check.
            if (!ForgeRegistries.ITEMS.containsKey(output.itemId())) {
                LOGGER.warn("Shard type outputs entry '{}' does not resolve to a real item, skipping it",
                        output.itemId());
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(output.itemId());

            double effectiveChance = output.chance() * multiplier;
            int guaranteedCount = (int) Math.floor(effectiveChance);
            int fractionalChance = (int) Math.round((effectiveChance - guaranteedCount) * MAX_CHANCE);
            int neededSlots = guaranteedCount + (fractionalChance > 0 ? 1 : 0);

            if (usedSlots + neededSlots > MAX_OUTPUT_SLOTS) {
                truncated.addAll(outputs.subList(i, outputs.size()).stream().map(ShardTypeOutput::itemId).toList());
                break;
            }

            for (int copy = 0; copy < guaranteedCount; copy++) {
                expanded.add(new ExpandedOutput(item, MAX_CHANCE));
            }
            if (fractionalChance > 0) {
                expanded.add(new ExpandedOutput(item, fractionalChance));
            }
            usedSlots += neededSlots;
        }

        return new ExpansionResult(expanded, truncated);
    }
}
