package cyano.poweradvantage.api.fluid;

import javax.annotation.Nullable;

import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NBTTagCompound;

/** Minimal millibucket tank backed by Minecraft 1.13 native fluids. */
public class FluidTank {
    public static final int BUCKET_VOLUME = 1000;

    @Nullable protected FluidStack fluid;
    protected int capacity;

    public FluidTank(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    public FluidTank(Fluid fluid, int amount, int capacity) {
        this(capacity);
        this.fluid = new FluidStack(fluid, Math.min(amount, capacity));
    }

    @Nullable public FluidStack getFluid() { return fluid; }
    public int getFluidAmount() { return fluid == null ? 0 : fluid.amount; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = Math.max(0, capacity); }

    public void setFluid(@Nullable FluidStack fluid) {
        this.fluid = fluid == null || fluid.amount <= 0 ? null
                : new FluidStack(fluid, Math.min(fluid.amount, capacity));
        onContentsChanged();
    }

    public int fill(@Nullable FluidStack resource, boolean doFill) {
        if (resource == null || resource.amount <= 0 || !canFillFluidType(resource)) return 0;
        int stored = getFluidAmount();
        if (stored > 0 && !this.fluid.isFluidEqual(resource)) return 0;
        int accepted = Math.min(capacity - stored, resource.amount);
        if (doFill && accepted > 0) {
            if (stored == 0) this.fluid = new FluidStack(resource, accepted);
            else this.fluid.amount += accepted;
            onContentsChanged();
        }
        return accepted;
    }

    @Nullable
    public FluidStack drain(int amount, boolean doDrain) {
        if (fluid == null || amount <= 0 || !canDrainFluidType(fluid)) return null;
        int drained = Math.min(amount, fluid.amount);
        FluidStack result = new FluidStack(fluid, drained);
        if (doDrain) {
            fluid.amount -= drained;
            if (fluid.amount <= 0) fluid = null;
            onContentsChanged();
        }
        return result;
    }

    @Nullable
    public FluidStack drain(@Nullable FluidStack request, boolean doDrain) {
        return request == null || fluid == null || !fluid.isFluidEqual(request)
                ? null : drain(request.amount, doDrain);
    }

    protected boolean canFillFluidType(FluidStack resource) { return true; }
    protected boolean canDrainFluidType(FluidStack resource) { return true; }
    protected void onContentsChanged() {}

    public FluidTankInfo getInfo() { return new FluidTankInfo(fluid, capacity); }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        if (fluid == null) nbt.setBoolean("Empty", true);
        else fluid.writeToNBT(nbt);
        return nbt;
    }

    public FluidTank readFromNBT(NBTTagCompound nbt) {
        setFluid(nbt.contains("Empty", 1) ? null : FluidStack.loadFluidStackFromNBT(nbt));
        return this;
    }
}
