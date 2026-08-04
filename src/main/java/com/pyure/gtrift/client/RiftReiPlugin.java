package com.pyure.gtrift.client;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.block.GTRiftBlocks;

import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryIngredients;
import me.shedaniel.rei.forge.REIPluginClient;
import me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * Client-only, discovered automatically by REI's own classpath scan via the @REIPluginClient annotation
 * — no manual registration needed elsewhere. Needs no ModList guard the way RiftJeiPlugin has: this
 * class is only ever loaded/invoked at all once REI itself is present, so there's no double-registration
 * path to protect against.
 *
 * Shares its data-gathering with RiftJeiPlugin/RiftEmiPlugin via RiftMobInfoPages — see that class's own
 * doc comment for why it reads the config folder directly rather than the live RiftMobPool.NORMAL/ELITE
 * singletons.
 *
 * DefaultInformationDisplay self-registers against REI's own built-in information category (its
 * getCategoryIdentifier() is derived internally, with no external configuration anywhere in its public
 * API) — no separate category-registration call is needed, same self-contained shape as JEI's
 * addIngredientInfo and EMI's EmiInfoRecipe.
 */
@REIPluginClient
public class RiftReiPlugin implements REIClientPlugin {

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        EntryIngredient ingredient = EntryIngredients.of(GTRiftBlocks.RIFT_BEACON.getBlock());

        Path configDir = FMLPaths.CONFIGDIR.get().resolve(GTRift.MOD_ID);
        for (RiftMobInfoPages.InfoPage page : RiftMobInfoPages.build(
                configDir.resolve("rift_mobs"), configDir.resolve("rift_elite_mobs"))) {
            registry.add(DefaultInformationDisplay.createFromEntries(ingredient, page.title())
                    .lines(page.lines()));
        }
    }
}
