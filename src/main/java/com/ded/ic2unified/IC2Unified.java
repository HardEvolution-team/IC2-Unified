package com.ded.ic2unified;

import com.ded.ic2unified.energy.EnergyBridge;
import com.ded.ic2unified.recipe.MaterialUnifier;
import com.ded.ic2unified.recipe.RecipeConflictResolver;
import com.ded.ic2unified.world.WorldGenFilter;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Compatibility layer between IC2 Experimental (mod id {@code ic2}) and a relocated IC2 Classic (mod id {@code ic2classic},
 * produced by the {@code relocateClassic} gradle task).
 */
@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.VERSION,
        dependencies = "required-after:ic2;required-after:ic2classic")
public class IC2Unified {

    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (UnifiedConfig.energy.enabled) {
            MinecraftForge.EVENT_BUS.register(EnergyBridge.INSTANCE);
            LOGGER.info("EU bridge between IC2 Experimental and IC2 Classic enabled");
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        WorldGenFilter.apply();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // ore-dictionary based 1:1 conversions first, so the conflict resolver can avoid clashing with them
        MaterialUnifier.apply();
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        // both IC2 builds may still add crafting recipes in their postInit, so resolve conflicts as late as possible
        RecipeConflictResolver.apply();
        WorldGenFilter.apply(); // in case a generator was registered late
    }
}
