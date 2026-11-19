package com.ded.ic2unified;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Config(modid = Reference.MOD_ID)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class UnifiedConfig {

    @Config.Comment("Shared EU grid between IC2 Experimental and IC2 Classic")
    public static final Energy energy = new Energy();

    @Config.Comment("Crafting recipe conflicts between both IC2 builds")
    public static final Recipes recipes = new Recipes();

    @Config.Comment("Duplicate materials (copper, tin, ...) present in both IC2 builds")
    public static final Materials materials = new Materials();

    @Config.Comment("World generation")
    public static final WorldGen worldgen = new WorldGen();

    public static final class Energy {
        @Config.Comment("Mirror every energy tile of one IC2 build into the energy net of the other one, so cables, "
                + "generators, storages and machines of both mods connect to each other.")
        @Config.RequiresMcRestart
        public boolean enabled = true;

        @Config.Comment("Also mirror cables (conductors). Without it only machines/generators/storages adjacent to a cable "
                + "of the other mod connect, cables of different mods do not connect to each other.")
        public boolean mirrorConductors = true;
    }

    public enum ConflictPolicy {
        /** Remove the IC2 Classic recipe, the Classic item becomes craftable from the Experimental one. */
        PREFER_EXPERIMENTAL,
        /** Remove the IC2 Experimental recipe, the Experimental item becomes craftable from the Classic one. */
        PREFER_CLASSIC,
        /** Do nothing. */
        KEEP_BOTH
    }

    public static final class Recipes {
        @Config.Comment("What to do when an IC2 Classic and an IC2 Experimental crafting recipe accept the same grid")
        @Config.RequiresMcRestart
        public ConflictPolicy conflictPolicy = ConflictPolicy.PREFER_EXPERIMENTAL;

        @Config.Comment("When a recipe is removed, add a 1:1 shapeless conversion from the winner's item to the loser's item")
        @Config.RequiresMcRestart
        public boolean addConversionForRemoved = true;

        @Config.Comment("Log every detected conflict")
        public boolean logConflicts = true;
    }

    public static final class Materials {
        @Config.Comment("Add 2 -> 2 conversion recipes between equivalent Classic and Experimental materials "
                + "(items sharing an ore dictionary name with one of the prefixes below)")
        @Config.RequiresMcRestart
        public boolean addConversionRecipes = true;

        @Config.Comment("Ore dictionary prefixes treated as materials")
        @Config.RequiresMcRestart
        public String[] prefixes = {"ingot", "nugget", "dust", "dustTiny", "dustSmall", "plate", "plateDense", "casing",
                "crushed", "crushedPurified", "block", "ore", "gem", "itemRubber", "materialRubber", "itemRawRubber"};

        @Config.Comment("Ore dictionary names that should never be unified")
        @Config.RequiresMcRestart
        public String[] blacklist = {};
    }

    public static final class WorldGen {
        @Config.Comment("Remove IC2 Classic world generators (ores, rubber trees). IC2 Experimental already generates "
                + "copper/tin/lead/uranium ores and rubber trees, both are interchangeable through the ore dictionary.")
        @Config.RequiresMcRestart
        public boolean disableClassicWorldGen = true;

        @Config.Comment("Remove IC2 Experimental world generators instead / as well")
        @Config.RequiresMcRestart
        public boolean disableExperimentalWorldGen = false;
    }

    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (event.getModID().equals(Reference.MOD_ID)) {
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
        }
    }

    private UnifiedConfig() {}
}
