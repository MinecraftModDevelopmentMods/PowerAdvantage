package com.mcmoddev.poweradvantage.util;

import java.lang.reflect.Field;
import com.google.common.collect.BiMap;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

public final class FluidHelper {
    protected FluidHelper() {}

    public static boolean isFluidContainer(ItemStack stack) {
        return stack.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
    }

    public static FluidStack getContainedFluid(ItemStack stack) {
        IFluidHandlerItem handler = stack.getCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null);
        if (handler != null) {
            return handler.drain(Fluid.BUCKET_VOLUME, false);
        }

        return null;
    }

    @Deprecated
    public static int getFluidId(Fluid fluid) {
        try {
            Field field = FluidRegistry.class.getDeclaredField("fluidIDs");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            BiMap<Fluid, Integer> map = (BiMap<Fluid, Integer>)field.get(null);
            return map.get(fluid);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Deprecated
    public static Fluid getFluidById(int id) {
        try {
            Field field = FluidRegistry.class.getDeclaredField("fluidNames");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            BiMap<Integer, String> map = (BiMap<Integer, String>)field.get(null);
            return FluidRegistry.getFluid(map.get(id));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }
}
