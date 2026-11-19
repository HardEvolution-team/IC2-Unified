package com.ded.ic2unified.recipe;

import com.ded.ic2unified.IC2Unified;
import com.ded.ic2unified.Reference;
import com.ded.ic2unified.UnifiedConfig;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;

import java.util.*;

/**
 * Both IC2 builds register their own copper/tin/bronze/lead/... ingots, dusts, plates, ores and so on. Machines of both
 * mods use the ore dictionary, so the items are mostly interchangeable already; this class adds explicit conversion
 * recipes (two items stacked vertically -> two items of the other mod) for every material that exists in both mods,
 * so nothing gets stuck in one of them (e.g. for recipes that ask for the exact item).
 */
public final class MaterialUnifier {

    private MaterialUnifier() {}

    /** True if the stack is registered under an ore dictionary name that counts as "material". */
    public static boolean isMaterial(ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (int id : OreDictionary.getOreIDs(stack)) {
            if (materialName(OreDictionary.getOreName(id))) return true;
        }
        return false;
    }

    static boolean materialName(String ore) {
        if (Arrays.asList(UnifiedConfig.materials.blacklist).contains(ore)) return false;
        for (String prefix : UnifiedConfig.materials.prefixes) {
            if (ore.equals(prefix)) return true;
            if (ore.length() > prefix.length() && ore.startsWith(prefix) && Character.isUpperCase(ore.charAt(prefix.length()))) {
                // "dustTiny" is a prefix itself, skip "dust" + "TinyCopper" matches only when the longer prefix is not configured
                return true;
            }
        }
        return false;
    }

    public static void apply() {
        if (!UnifiedConfig.materials.addConversionRecipes) return;
        Set<String> done = new HashSet<>();
        int added = 0, skipped = 0;
        for (String ore : OreDictionary.getOreNames()) {
            if (!materialName(ore)) continue;
            ItemStack exp = ItemStack.EMPTY, classic = ItemStack.EMPTY;
            for (ItemStack s : OreDictionary.getOres(ore, false)) {
                ResourceLocation id = s.getItem().getRegistryName();
                if (id == null || s.getMetadata() == OreDictionary.WILDCARD_VALUE) continue;
                if (exp.isEmpty() && id.getNamespace().equals(RecipeUtil.EXP)) exp = s;
                else if (classic.isEmpty() && id.getNamespace().equals(RecipeUtil.CLASSIC)) classic = s;
            }
            if (exp.isEmpty() || classic.isEmpty()) continue;
            String pairKey = key(exp) + "|" + key(classic);
            if (!done.add(pairKey)) continue; // same item pair under several ore names
            if (register(ore, "to_classic", exp, classic)) added++; else skipped++;
            if (register(ore, "to_experimental", classic, exp)) added++; else skipped++;
        }
        IC2Unified.LOGGER.info("Material unification: {} conversion recipes added, {} skipped because of conflicts", added, skipped);
    }

    private static String key(ItemStack s) {
        return s.getItem().getRegistryName() + "@" + s.getMetadata();
    }

    private static boolean register(String ore, String dir, ItemStack from, ItemStack to) {
        ItemStack in = from.copy();
        in.setCount(1);
        ItemStack out = to.copy();
        out.setCount(2);
        ResourceLocation name = new ResourceLocation(Reference.MOD_ID, "material/" + ore.toLowerCase(Locale.ROOT) + "_" + dir);
        if (ForgeRegistries.RECIPES.containsKey(name)) return false;
        ShapedOreRecipe recipe = new ShapedOreRecipe(new ResourceLocation(Reference.MOD_ID, "material_conversion"), out,
                "X", "X", 'X', in);
        recipe.setRegistryName(name);
        InventoryCrafting grid = RecipeUtil.sampleGrid(recipe);
        if (grid == null) return false;
        for (IRecipe r : ForgeRegistries.RECIPES.getValuesCollection()) {
            if (RecipeUtil.matchesSafe(r, grid)) return false;
        }
        ForgeRegistries.RECIPES.register(recipe);
        return true;
    }
}
