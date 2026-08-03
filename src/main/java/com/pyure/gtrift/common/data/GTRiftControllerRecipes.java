package com.pyure.gtrift.common.data;

import com.pyure.gtrift.common.block.GTRiftBlocks;

import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;

import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.function.Consumer;

/**
 * A single fixed crafting-table recipe for the Rift Beacon controller item — kept in its own file
 * rather than folded into GTRiftRecipes, whose own scope is specifically ShardType x RiftQuality
 * Centrifuge generation, not fixed content like this.
 */
public class GTRiftControllerRecipes {

    public static void init(Consumer<FinishedRecipe> consumer) {
        // Corners: ULV machine casing — same casing family as the real structure's own walls, one
        // tier down (the structure's walls need LV; the controller itself is a cheap capstone item,
        // not priced at the structure's own tier). Edges: Wrought Iron plate, idiomatic ULV-tier
        // filler. Center: an ender pearl (teleportation, the whole point of the item, at its heart).
        // Bottom edge: a redstone block (power) — both special items chosen for thematic fit.
        VanillaRecipeHelper.addShapedRecipe(consumer, true, "rift_beacon",
                GTRiftBlocks.RIFT_BEACON.asStack(),
                "CPC",
                "PEP",
                "CRC",
                'C', GTBlocks.MACHINE_CASING_ULV.asStack(),
                'P', new MaterialEntry(TagPrefix.plate, GTMaterials.WroughtIron),
                'E', new ItemStack(Items.ENDER_PEARL),
                'R', new ItemStack(Items.REDSTONE_BLOCK));
    }
}
