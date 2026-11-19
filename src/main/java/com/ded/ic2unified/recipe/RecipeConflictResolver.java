package com.ded.ic2unified.recipe;

import com.ded.ic2unified.IC2Unified;
import com.ded.ic2unified.Reference;
import com.ded.ic2unified.UnifiedConfig;
import com.ded.ic2unified.UnifiedConfig.ConflictPolicy;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import net.minecraftforge.registries.IForgeRegistryModifiable;

import java.util.*;

/**
 * Finds crafting recipes of IC2 Classic and IC2 Experimental that accept the same crafting grid (the classic case: both
 * mods craft "their" machine casing, cable, circuit... from identical ingredients, so only one of them is ever craftable
 * and JEI shows two recipes for one grid) and removes the loser according to {@link UnifiedConfig.Recipes#conflictPolicy}.
 */
public final class RecipeConflictResolver {

    private RecipeConflictResolver() {}

    public static void apply() {
        ConflictPolicy policy = UnifiedConfig.recipes.conflictPolicy;
        if (policy == ConflictPolicy.KEEP_BOTH) return;
        long start = System.currentTimeMillis();

        List<IRecipe> exp = new ArrayList<>();
        List<IRecipe> classic = new ArrayList<>();
        for (IRecipe r : ForgeRegistries.RECIPES.getValuesCollection()) {
            ResourceLocation name = r.getRegistryName();
            if (name != null && name.getNamespace().equals(Reference.MOD_ID)) continue; // our own conversions
            String owner = RecipeUtil.outputOwner(r);
            if (RecipeUtil.EXP.equals(owner)) exp.add(r);
            else if (RecipeUtil.CLASSIC.equals(owner)) classic.add(r);
        }

        Map<Integer, List<Sampled>> expBySize = bucket(exp);
        Map<Integer, List<Sampled>> classicBySize = bucket(classic);

        // loser -> winner
        Map<IRecipe, IRecipe> conflicts = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Sampled>> e : classicBySize.entrySet()) {
            List<Sampled> others = expBySize.getOrDefault(e.getKey(), Collections.emptyList());
            for (Sampled c : e.getValue()) {
                for (Sampled x : others) {
                    if (RecipeUtil.matchesSafe(x.recipe, c.grid) || RecipeUtil.matchesSafe(c.recipe, x.grid)) {
                        if (policy == ConflictPolicy.PREFER_EXPERIMENTAL) conflicts.putIfAbsent(c.recipe, x.recipe);
                        else conflicts.putIfAbsent(x.recipe, c.recipe);
                    }
                }
            }
        }

        IForgeRegistryModifiable<IRecipe> registry = (IForgeRegistryModifiable<IRecipe>) ForgeRegistries.RECIPES;
        List<IRecipe> conversions = new ArrayList<>();
        Set<String> convertedPairs = new HashSet<>();
        int removed = 0;
        for (Map.Entry<IRecipe, IRecipe> e : conflicts.entrySet()) {
            IRecipe loser = e.getKey(), winner = e.getValue();
            if (UnifiedConfig.recipes.logConflicts) {
                IC2Unified.LOGGER.info("Recipe conflict: {} ({}) vs {} ({}) -> removing {}",
                        loser.getRegistryName(), loser.getRecipeOutput(), winner.getRegistryName(), winner.getRecipeOutput(),
                        loser.getRegistryName());
            }
            if (loser.getRegistryName() == null) continue;
            ResourceLocation loserName = loser.getRegistryName();
            registry.remove(loserName);
            registry.register(new DisabledRecipe().setRegistryName(loserName));
            removed++;
            if (UnifiedConfig.recipes.addConversionForRemoved) {
                IRecipe conv = conversion(winner.getRecipeOutput(), loser.getRecipeOutput(), convertedPairs);
                if (conv != null) conversions.add(conv);
            }
        }

        int added = 0;
        for (IRecipe conv : conversions) {
            InventoryCrafting grid = RecipeUtil.sampleGrid(conv);
            boolean clash = false;
            if (grid != null) {
                for (IRecipe r : ForgeRegistries.RECIPES.getValuesCollection()) {
                    if (RecipeUtil.matchesSafe(r, grid)) {
                        clash = true;
                        break;
                    }
                }
            }
            if (clash) continue;
            ForgeRegistries.RECIPES.register(conv);
            added++;
        }
        IC2Unified.LOGGER.info("Recipe conflicts: {} classic / {} experimental recipes checked, {} removed, {} conversions added ({} ms)",
                classic.size(), exp.size(), removed, added, System.currentTimeMillis() - start);
    }

    private static IRecipe conversion(ItemStack from, ItemStack to, Set<String> done) {
        if (from.isEmpty() || to.isEmpty() || MaterialUnifier.isMaterial(from) || MaterialUnifier.isMaterial(to)) return null;
        String key = from.getItem().getRegistryName() + "@" + from.getMetadata() + "->" + to.getItem().getRegistryName() + "@" + to.getMetadata();
        if (!done.add(key)) return null;
        ItemStack in = from.copy();
        in.setCount(1);
        ItemStack out = to.copy();
        out.setCount(1);
        ShapelessOreRecipe r = new ShapelessOreRecipe(new ResourceLocation(Reference.MOD_ID, "conversion"), out, Ingredient.fromStacks(in));
        r.setRegistryName(new ResourceLocation(Reference.MOD_ID, "conflict/" + key.replaceAll("[^a-z0-9_]", "_")));
        return r;
    }

    private static Map<Integer, List<Sampled>> bucket(List<IRecipe> recipes) {
        Map<Integer, List<Sampled>> map = new HashMap<>();
        for (IRecipe r : recipes) {
            InventoryCrafting grid = RecipeUtil.sampleGrid(r);
            if (grid == null) continue;
            map.computeIfAbsent(RecipeUtil.filledSlots(grid), k -> new ArrayList<>()).add(new Sampled(r, grid));
        }
        return map;
    }

    private static final class Sampled {
        final IRecipe recipe;
        final InventoryCrafting grid;

        Sampled(IRecipe recipe, InventoryCrafting grid) {
            this.recipe = recipe;
            this.grid = grid;
        }
    }
}
