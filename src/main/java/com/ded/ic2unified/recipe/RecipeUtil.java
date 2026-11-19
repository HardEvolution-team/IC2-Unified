package com.ded.ic2unified.recipe;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.oredict.OreDictionary;

import javax.annotation.Nullable;

final class RecipeUtil {

    static final String EXP = "ic2";
    static final String CLASSIC = "ic2classic";

    private static final Container DUMMY = new Container() {
        @Override
        public boolean canInteractWith(EntityPlayer playerIn) {
            return false;
        }
    };

    private RecipeUtil() {}

    /** Mod id of the recipe output item, or null. */
    @Nullable
    static String outputOwner(IRecipe recipe) {
        ItemStack out;
        try {
            out = recipe.getRecipeOutput();
        } catch (RuntimeException e) {
            return null;
        }
        if (out.isEmpty()) return null;
        ResourceLocation id = out.getItem().getRegistryName();
        return id == null ? null : id.getNamespace();
    }

    /** Builds a crafting grid with one sample stack for every ingredient of the recipe, or null if impossible. */
    @Nullable
    static InventoryCrafting sampleGrid(IRecipe recipe) {
        NonNullList<Ingredient> ingredients;
        try {
            ingredients = recipe.getIngredients();
        } catch (RuntimeException e) {
            return null;
        }
        if (ingredients.isEmpty()) return null;
        int width = 3, height = 3;
        if (recipe instanceof IShapedRecipe) {
            width = ((IShapedRecipe) recipe).getRecipeWidth();
            height = ((IShapedRecipe) recipe).getRecipeHeight();
            if (width <= 0 || height <= 0 || width > 3 || height > 3) return null;
        } else if (ingredients.size() > 9) {
            return null;
        }
        InventoryCrafting inv = new InventoryCrafting(DUMMY, 3, 3);
        boolean any = false;
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ing = ingredients.get(i);
            if (ing == Ingredient.EMPTY) continue;
            ItemStack[] stacks = ing.getMatchingStacks();
            if (stacks.length == 0) {
                if (ing.apply(ItemStack.EMPTY)) continue;
                return null; // e.g. empty ore dictionary entry - recipe can not be crafted anyway
            }
            ItemStack s = stacks[0].copy();
            if (s.getMetadata() == OreDictionary.WILDCARD_VALUE) s.setItemDamage(0);
            s.setCount(1);
            int slot = recipe instanceof IShapedRecipe ? (i / width) * 3 + (i % width) : i;
            if (slot >= 9 || (recipe instanceof IShapedRecipe && i >= width * height)) return null; // inconsistent recipe
            inv.setInventorySlotContents(slot, s);
            any = true;
        }
        return any ? inv : null;
    }

    static int filledSlots(InventoryCrafting inv) {
        int n = 0;
        for (int i = 0; i < inv.getSizeInventory(); i++) if (!inv.getStackInSlot(i).isEmpty()) n++;
        return n;
    }

    static boolean matchesSafe(IRecipe recipe, InventoryCrafting inv) {
        try {
            return recipe.matches(inv, null);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
