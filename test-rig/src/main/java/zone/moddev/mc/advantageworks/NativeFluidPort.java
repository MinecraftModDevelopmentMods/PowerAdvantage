package zone.moddev.mc.advantageworks;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;

final class NativeFluidPort {
    private final IFluidHandler handler;
    private final TileEntity legacy;
    private final EnumFacing face;
    NativeFluidPort(TileEntity tile, EnumFacing face) {
        this.face = face;
        handler = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face);
        legacy = handler == null ? tile : null;
        if (legacy != null) {
            try {
                tile.getClass().getMethod("fill", EnumFacing.class, FluidStack.class, boolean.class);
                tile.getClass().getMethod("drain", EnumFacing.class, int.class, boolean.class);
                tile.getClass().getMethod("getTankInfo", EnumFacing.class);
            } catch (NoSuchMethodException failure) {
                throw new IllegalStateException("No supported native or legacy sided fluid interface", failure);
            }
        }
    }
    private Object invoke(String method, Class<?>[] types, Object... args) {
        try { return legacy.getClass().getMethod(method, types).invoke(legacy, args); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            throw new IllegalStateException("Legacy fluid operation failed", failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Legacy fluid interface changed", failure); }
    }
    int fill(FluidStack stack, boolean execute) {
        return legacy == null ? handler.fill(stack, execute)
                : (Integer)invoke("fill", new Class<?>[]{EnumFacing.class, FluidStack.class, boolean.class}, face, stack, execute);
    }
    FluidStack drain(int amount, boolean execute) {
        return legacy == null ? handler.drain(amount, execute)
                : (FluidStack)invoke("drain", new Class<?>[]{EnumFacing.class, int.class, boolean.class}, face, amount, execute);
    }
    FluidStack getFluidInTank(int index) {
        return legacy == null ? handler.getTankProperties()[index].getContents()
                : ((net.minecraftforge.fluids.FluidTankInfo[])invoke("getTankInfo", new Class<?>[]{EnumFacing.class}, face))[index].fluid;
    }
    int amount(int index) { FluidStack stack = getFluidInTank(index); return stack == null ? 0 : stack.amount; }
}
