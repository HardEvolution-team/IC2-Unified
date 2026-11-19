package com.ded.ic2unified.world;

import com.ded.ic2unified.IC2Unified;
import com.ded.ic2unified.UnifiedConfig;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.registry.GameRegistry;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

/** Removes the world generators of one of the IC2 builds, so copper/tin/uranium ores are not generated twice. */
public final class WorldGenFilter {

    private WorldGenFilter() {}

    @SuppressWarnings("unchecked")
    public static void apply() {
        if (!UnifiedConfig.worldgen.disableClassicWorldGen && !UnifiedConfig.worldgen.disableExperimentalWorldGen) return;
        try {
            Field setField = GameRegistry.class.getDeclaredField("worldGenerators");
            Field indexField = GameRegistry.class.getDeclaredField("worldGeneratorIndex");
            Field sortedField = GameRegistry.class.getDeclaredField("sortedGeneratorList");
            setField.setAccessible(true);
            indexField.setAccessible(true);
            sortedField.setAccessible(true);
            Set<IWorldGenerator> generators = (Set<IWorldGenerator>) setField.get(null);
            Map<IWorldGenerator, Integer> index = (Map<IWorldGenerator, Integer>) indexField.get(null);
            int before = generators.size();
            generators.removeIf(WorldGenFilter::shouldRemove);
            index.keySet().removeIf(WorldGenFilter::shouldRemove);
            sortedField.set(null, null);
            if (before != generators.size()) {
                IC2Unified.LOGGER.info("Removed {} IC2 world generator(s)", before - generators.size());
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            IC2Unified.LOGGER.error("Could not filter IC2 world generators", e);
        }
    }

    private static boolean shouldRemove(IWorldGenerator gen) {
        String n = gen.getClass().getName();
        if (UnifiedConfig.worldgen.disableClassicWorldGen && n.startsWith("ic2classic.")) return true;
        return UnifiedConfig.worldgen.disableExperimentalWorldGen && n.startsWith("ic2.");
    }
}
