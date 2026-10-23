package cyano.poweradvantage.api.fluid;

import javax.annotation.Nullable;

import net.minecraft.fluid.Fluid;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.registry.IRegistry;
import zone.moddev.mc.poweradvantage.init.Fluids;

/**
 * Forge 25's transitional fluid stack is unfinished, so Advantage keeps its
 * millibucket state in this native-fluid-backed value object.
 */
public final class FluidStack {
    private final Fluid fluid;
    public int amount;
    @Nullable public NBTTagCompound tag;

    public FluidStack(Fluid fluid, int amount) {
        if (fluid == null) throw new IllegalArgumentException("fluid");
        this.fluid = fluid;
        this.amount = Math.max(0, amount);
    }

    public FluidStack(FluidStack other, int amount) {
        this(other.fluid, amount);
        this.tag = other.tag == null ? null : other.tag.copy();
    }

    public Fluid getFluid() { return fluid; }
    public FluidStack copy() { return new FluidStack(this, amount); }

    public boolean isFluidEqual(@Nullable FluidStack other) {
        return other != null && fluid.isEquivalentTo(other.fluid)
                && (tag == null ? other.tag == null : tag.equals(other.tag));
    }

    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        ResourceLocation id = IRegistry.field_212619_h.getKey(fluid);
        if (id != null) nbt.setString("FluidName", id.toString());
        nbt.setInt("Amount", amount);
        if (tag != null) nbt.setTag("Tag", tag.copy());
        return nbt;
    }

    @Nullable
    public static FluidStack loadFluidStackFromNBT(NBTTagCompound nbt) {
        if (nbt == null || !nbt.contains("FluidName", 8)) return null;
        Fluid fluid = Fluids.byName(nbt.getString("FluidName"));
        if (fluid == null) return null;
        FluidStack stack = new FluidStack(fluid, nbt.getInt("Amount"));
        if (nbt.contains("Tag", 10)) stack.tag = nbt.getCompound("Tag").copy();
        return stack;
    }
}
