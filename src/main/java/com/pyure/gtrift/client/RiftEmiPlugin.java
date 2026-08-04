package com.pyure.gtrift.client;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.block.GTRiftBlocks;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-only, discovered automatically by EMI's own classpath scan via the @EmiEntrypoint annotation —
 * no manual registration needed elsewhere. Needs no ModList guard the way RiftJeiPlugin has: this class
 * is only ever loaded/invoked at all once EMI itself is present, so there's no double-registration path
 * to protect against.
 *
 * Shares its data-gathering with RiftJeiPlugin/RiftReiPlugin via RiftMobInfoPages — see that class's own
 * doc comment for why it reads the config folder directly rather than the live RiftMobPool.NORMAL/ELITE
 * singletons.
 */
@EmiEntrypoint
public class RiftEmiPlugin implements EmiPlugin {

    @Override
    public void register(EmiRegistry registry) {
        List<EmiIngredient> stacks = List.of(EmiStack.of(GTRiftBlocks.RIFT_BEACON.getBlock()));

        Path configDir = FMLPaths.CONFIGDIR.get().resolve(GTRift.MOD_ID);
        for (RiftMobInfoPages.InfoPage page : RiftMobInfoPages.build(
                configDir.resolve("rift_mobs"), configDir.resolve("rift_elite_mobs"))) {
            List<Component> text = new ArrayList<>();
            text.add(page.title());
            text.addAll(page.lines());
            ResourceLocation id = new ResourceLocation(GTRift.MOD_ID,
                    "info/rift_beacon/" + sanitize(page.dimensionKey()));
            registry.addRecipe(new EmiInfoRecipe(stacks, text, id));
        }
    }

    /**
     * ResourceLocation's path validation rejects ':' (the raw dimensionKey format, e.g.
     * "minecraft:the_nether") and anything outside [a-z0-9/._-] — replace anything invalid with '_'
     * rather than special-casing the handful of characters a dimension id could realistically contain.
     */
    private static String sanitize(String dimensionKey) {
        return dimensionKey.toLowerCase().replaceAll("[^a-z0-9/._-]", "_");
    }
}
