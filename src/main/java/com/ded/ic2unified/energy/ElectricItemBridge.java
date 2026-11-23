package com.ded.ic2unified.energy;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

/**
 * Lets each IC2 build charge / discharge the electric items of the other one: a backup electric item manager is
 * registered in both APIs that forwards to the other API's manager. Tiers are identical in both mods.
 * {@code handles} only accepts items of the other API, so the forwarding never loops back.
 */
public final class ElectricItemBridge {

    private ElectricItemBridge() {}

    public static void register() {
        ic2.api.item.ElectricItem.registerBackupManager(new ClassicInExp());
        ic2classic.api.item.ElectricItem.registerBackupManager(new ExpInClassic());
    }

    static boolean isClassic(ItemStack s) {
        return !s.isEmpty() && (s.getItem() instanceof ic2classic.api.item.IElectricItem
                || s.getItem() instanceof ic2classic.api.item.ISpecialElectricItem);
    }

    static boolean isExp(ItemStack s) {
        return !s.isEmpty() && (s.getItem() instanceof ic2.api.item.IElectricItem
                || s.getItem() instanceof ic2.api.item.ISpecialElectricItem);
    }

    /** Classic items seen through the Experimental API. */
    static final class ClassicInExp implements ic2.api.item.IBackupElectricItemManager {
        private static ic2classic.api.item.IElectricItemManager m() { return ic2classic.api.item.ElectricItem.manager; }
        @Override public boolean handles(ItemStack s) { return isClassic(s) && !isExp(s); }
        @Override public double charge(ItemStack s, double a, int t, boolean i, boolean sim) { return m().charge(s, a, t, i, sim); }
        @Override public double discharge(ItemStack s, double a, int t, boolean i, boolean e, boolean sim) { return m().discharge(s, a, t, i, e, sim); }
        @Override public double getCharge(ItemStack s) { return m().getCharge(s); }
        @Override public double getMaxCharge(ItemStack s) { return m().getMaxCharge(s); }
        @Override public boolean canUse(ItemStack s, double a) { return m().canUse(s, a); }
        @Override public boolean use(ItemStack s, double a, EntityLivingBase e) { return m().use(s, a, e); }
        @Override public void chargeFromArmor(ItemStack s, EntityLivingBase e) { m().chargeFromArmor(s, e); }
        @Override public String getToolTip(ItemStack s) { return m().getToolTip(s); }
        @Override public int getTier(ItemStack s) { return m().getTier(s); }
    }

    /** Experimental items seen through the Classic API. */
    static final class ExpInClassic implements ic2classic.api.item.IBackupElectricItemManager {
        private static ic2.api.item.IElectricItemManager m() { return ic2.api.item.ElectricItem.manager; }
        @Override public boolean handles(ItemStack s) { return isExp(s) && !isClassic(s); }
        @Override public double charge(ItemStack s, double a, int t, boolean i, boolean sim) { return m().charge(s, a, t, i, sim); }
        @Override public double discharge(ItemStack s, double a, int t, boolean i, boolean e, boolean sim) { return m().discharge(s, a, t, i, e, sim); }
        @Override public double getCharge(ItemStack s) { return m().getCharge(s); }
        @Override public double getMaxCharge(ItemStack s) { return m().getMaxCharge(s); }
        @Override public boolean canUse(ItemStack s, double a) { return m().canUse(s, a); }
        @Override public boolean use(ItemStack s, double a, EntityLivingBase e) { return m().use(s, a, e); }
        @Override public void chargeFromArmor(ItemStack s, EntityLivingBase e) { m().chargeFromArmor(s, e); }
        @Override public String getToolTip(ItemStack s) { return m().getToolTip(s); }
        @Override public int getTier(ItemStack s) { return m().getTier(s); }
    }
}
