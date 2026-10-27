package zone.moddev.mc.poweradvantage.util;

import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

/** Lazily built sided adapters, discarded when a machine is removed or unloaded. */
public final class MachineCapabilities {
    private final TileEntity owner;
    private final Map<Direction, LazyOptional<?>> fluids = new HashMap<>();
    private final Map<Direction, LazyOptional<?>> items = new HashMap<>();

    public MachineCapabilities(TileEntity owner) { this.owner = owner; }

    public <T> LazyOptional<T> get(Capability<T> capability, Direction face) {
        if (owner.isRemoved()) return LazyOptional.empty();
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY && owner instanceof IAdvantageFluidHandler) {
            return fluids.computeIfAbsent(face, side -> LazyOptional.of(() ->
                    new ForgeFluidAdapter((IAdvantageFluidHandler) owner, side, owner::setChanged))).cast();
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY && owner instanceof IInventory) {
            return items.computeIfAbsent(face, side -> LazyOptional.of(() -> side != null && owner instanceof ISidedInventory
                    ? new SidedInvWrapper((ISidedInventory) owner, side) : new InvWrapper((IInventory) owner))).cast();
        }
        return LazyOptional.empty();
    }

    public void invalidate() {
        fluids.values().forEach(LazyOptional::invalidate);
        items.values().forEach(LazyOptional::invalidate);
        fluids.clear();
        items.clear();
    }
}
