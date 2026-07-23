package com.pyure.gtrift.common.data;

import com.pyure.gtrift.common.item.GTRiftItems;
import com.pyure.gtrift.common.item.RiftAffinity;
import com.pyure.gtrift.common.item.RiftRichness;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.recipe.ingredient.nbtpredicate.NBTPredicates;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.data.recipes.FinishedRecipe;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public class GTRiftRecipes {

    private static final Map<RiftAffinity, List<Material>> AFFINITY_MATERIALS = new EnumMap<>(RiftAffinity.class);
    static {
        AFFINITY_MATERIALS.put(RiftAffinity.FERROUS, List.of(GTMaterials.Iron, GTMaterials.Steel));
        AFFINITY_MATERIALS.put(RiftAffinity.CONDUCTIVE, List.of(GTMaterials.Copper, GTMaterials.Tin, GTMaterials.Gold));
        AFFINITY_MATERIALS.put(RiftAffinity.PRECIOUS,
                List.of(GTMaterials.Diamond, GTMaterials.Emerald, GTMaterials.Silver, GTMaterials.Platinum));
    }

    private static final Map<RiftRichness, Integer> RICHNESS_YIELD = new EnumMap<>(RiftRichness.class);
    static {
        RICHNESS_YIELD.put(RiftRichness.SPARSE, 1);
        RICHNESS_YIELD.put(RiftRichness.NORMAL, 2);
        RICHNESS_YIELD.put(RiftRichness.RICH, 4);
        RICHNESS_YIELD.put(RiftRichness.EXTREMELY_RICH, 8);
    }

    public static void init(Consumer<FinishedRecipe> consumer) {
        for (RiftAffinity affinity : RiftAffinity.values()) {
            for (RiftRichness richness : RiftRichness.values()) {
                addCentrifugeRecipe(consumer, affinity, richness);
            }
        }
    }

    private static void addCentrifugeRecipe(Consumer<FinishedRecipe> consumer, RiftAffinity affinity,
                                             RiftRichness richness) {
        String id = "gtrift:centrifuge/%s_rift_shard_%s".formatted(
                affinity.name().toLowerCase(Locale.ROOT), richness.name().toLowerCase(Locale.ROOT));

        var builder = GTRecipeTypes.CENTRIFUGE_RECIPES.recipeBuilder(id)
                .inputItemNbtPredicate(GTRiftItems.createStack(affinity, richness, 1),
                        NBTPredicates.eq("Richness", richness.name()))
                .duration(100)
                .EUt(10);

        int yield = RICHNESS_YIELD.get(richness);
        for (Material material : AFFINITY_MATERIALS.get(affinity)) {
            builder.outputItems(TagPrefix.dust, material, yield);
        }

        builder.save(consumer);
    }
}
