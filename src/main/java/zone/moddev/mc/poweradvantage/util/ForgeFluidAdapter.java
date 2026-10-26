package zone.moddev.mc.poweradvantage.util;

import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.FluidTankInfo;
import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import net.minecraft.util.Direction;
import net.minecraftforge.fluids.capability.IFluidHandler;

/** Exposes the stable Advantage contract through Forge's native fluid capability. */
public final class ForgeFluidAdapter implements IFluidHandler {
    private final IAdvantageFluidHandler handler;
    private final Direction face;
    private final Runnable changed;

    public ForgeFluidAdapter(IAdvantageFluidHandler handler, Direction face, Runnable changed) {
        this.handler = handler;
        this.face = face;
        this.changed = changed;
    }

    public static net.minecraftforge.fluids.FluidStack toForge(FluidStack stack) {
        return stack == null || stack.amount <= 0 ? net.minecraftforge.fluids.FluidStack.EMPTY
                : new net.minecraftforge.fluids.FluidStack(stack.getFluid(), stack.amount,
                        stack.tag == null ? null : stack.tag.copy());
    }

    public static FluidStack fromForge(net.minecraftforge.fluids.FluidStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        FluidStack copy = new FluidStack(stack.getFluid(), stack.getAmount());
        copy.tag = stack.hasTag() ? stack.getTag().copy() : null;
        return copy;
    }

    private FluidTankInfo[] tanks() {
        FluidTankInfo[] tanks = handler.getTankInfo(face);
        return tanks == null ? new FluidTankInfo[0] : tanks;
    }

    @Override public int getTanks() { return tanks().length; }
    @Override public net.minecraftforge.fluids.FluidStack getFluidInTank(int tank) {
        FluidTankInfo[] tanks = tanks();
        return tank < 0 || tank >= tanks.length || tanks[tank] == null
                ? net.minecraftforge.fluids.FluidStack.EMPTY : toForge(tanks[tank].fluid);
    }
    @Override public int getTankCapacity(int tank) {
        FluidTankInfo[] tanks = tanks();
        return tank < 0 || tank >= tanks.length || tanks[tank] == null ? 0 : Math.max(0, tanks[tank].capacity);
    }
    @Override public boolean isFluidValid(int tank, net.minecraftforge.fluids.FluidStack resource) {
        return resource != null && !resource.isEmpty() && getTankCapacity(tank) > 0
                && handler.canFill(face, resource.getFluid());
    }
    @Override public int fill(net.minecraftforge.fluids.FluidStack resource, FluidAction action) {
        FluidStack copy = fromForge(resource);
        if (copy == null || !handler.canFill(face, copy.getFluid())) return 0;
        int accepted = Math.max(0, Math.min(resource.getAmount(), handler.fill(face, copy, action.execute())));
        if (accepted > 0 && action.execute()) changed.run();
        return accepted;
    }
    @Override public net.minecraftforge.fluids.FluidStack drain(net.minecraftforge.fluids.FluidStack resource, FluidAction action) {
        FluidStack copy = fromForge(resource);
        if (copy == null || !handler.canDrain(face, copy.getFluid())) return net.minecraftforge.fluids.FluidStack.EMPTY;
        FluidStack result = handler.drain(face, copy, action.execute());
        if (result == null || !copy.isFluidEqual(result)) return net.minecraftforge.fluids.FluidStack.EMPTY;
        return drained(result, resource.getAmount(), action);
    }
    @Override public net.minecraftforge.fluids.FluidStack drain(int amount, FluidAction action) {
        if (amount <= 0) return net.minecraftforge.fluids.FluidStack.EMPTY;
        FluidStack available = handler.drain(face, amount, false);
        if (available == null || !handler.canDrain(face, available.getFluid())) return net.minecraftforge.fluids.FluidStack.EMPTY;
        return drained(handler.drain(face, new FluidStack(available, Math.min(amount, available.amount)), action.execute()), amount, action);
    }
    private net.minecraftforge.fluids.FluidStack drained(FluidStack result, int limit, FluidAction action) {
        if (result == null || result.amount <= 0) return net.minecraftforge.fluids.FluidStack.EMPTY;
        net.minecraftforge.fluids.FluidStack copy = toForge(new FluidStack(result, Math.min(limit, result.amount)));
        if (action.execute()) changed.run();
        return copy;
    }
}
