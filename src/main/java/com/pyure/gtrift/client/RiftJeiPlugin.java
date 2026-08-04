package com.pyure.gtrift.client;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.common.block.GTRiftBlocks;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Client-only — JEI itself is a client-side mod, so this class and its discovery (the @JeiPlugin
 * annotation, scanned by JEI's own classpath scan at its own bootstrap — no manual registration needed
 * anywhere else in this codebase) never runs on a dedicated server. Confirmed against
 * GregTech-Modern's GTJEIPlugin precedent (same JEI release line, 15.20.0.115 there vs GTRift's
 * 15.20.0.130) — that class is never manually registered either.
 *
 * The actual mob-pool data-gathering (reading config/gtrift/rift_mobs|rift_elite_mobs, filtering,
 * sorting, formatting) lives in RiftMobInfoPages, shared with RiftEmiPlugin/RiftReiPlugin — see that
 * class's own doc comment for why it reads the config folder directly rather than the live
 * RiftMobPool.NORMAL/ELITE singletons.
 *
 * Bails out entirely once a native EMI/REI plugin is loaded — otherwise EMI's own built-in JEI-compat
 * bridge (its mods.toml declares a soft dependency on "jei") could surface this same info a second time
 * once RiftEmiPlugin registers it natively too.
 *
 * Not refreshed when the mob pool reloads (world join / /reload) — registered once, statically, the
 * conventional way JEI plugins register content, matching specs/rift-mob-dimensions.md's own accepted
 * tradeoff.
 */
@JeiPlugin
public class RiftJeiPlugin implements IModPlugin {

    private static final ResourceLocation PLUGIN_UID = new ResourceLocation(GTRift.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (ModList.get().isLoaded("rei") || ModList.get().isLoaded("emi")) return;

        Path configDir = FMLPaths.CONFIGDIR.get().resolve(GTRift.MOD_ID);
        for (RiftMobInfoPages.InfoPage page : RiftMobInfoPages.build(
                configDir.resolve("rift_mobs"), configDir.resolve("rift_elite_mobs"))) {
            List<Component> lines = new ArrayList<>();
            lines.add(page.title());
            lines.addAll(page.lines());
            // addIngredientInfo internally builds one IJeiIngredientInfoRecipe per call and registers
            // it against JEI's own built-in RecipeTypes.INFORMATION category — confirmed by decompiling
            // the real mezz.jei.library.load.registration.RecipeRegistration implementation. The recipe
            // carries its own ingredient, so JEI's normal "uses" (U-key) indexing finds it automatically
            // — no separate registerRecipeCatalysts call is needed.
            registration.addIngredientInfo(GTRiftBlocks.RIFT_BEACON.getBlock(), lines.toArray(Component[]::new));
        }
    }
}
