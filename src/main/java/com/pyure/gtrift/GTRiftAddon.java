package com.pyure.gtrift;

import com.pyure.gtrift.common.data.GTRiftControllerRecipes;
import com.pyure.gtrift.common.data.GTRiftRecipes;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;

import java.util.function.Consumer;

/**
 * GTCEu addon entry point. Discovered via @GTAddon annotation scanning.
 * initializeAddon() fires after GTRegistries.MACHINES is frozen — machine
 * registration must NOT go here. Use GTCEuAPI.RegisterEvent on the mod bus
 * instead (see CommonProxy).
 */
@GTAddon
public class GTRiftAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return GTRift.REGISTRATE;
    }

    @Override
    public void initializeAddon() {
        // Machine registration happens via GTCEuAPI.RegisterEvent in CommonProxy.
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> consumer) {
        GTRiftRecipes.init(consumer);
        GTRiftControllerRecipes.init(consumer);
    }

    @Override
    public String addonModId() {
        return GTRift.MOD_ID;
    }
}
