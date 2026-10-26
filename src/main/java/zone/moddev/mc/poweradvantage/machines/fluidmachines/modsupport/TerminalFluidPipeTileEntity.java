package zone.moddev.mc.poweradvantage.machines.fluidmachines.modsupport;

import java.util.List;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PowerConnectorContext;
import cyano.poweradvantage.api.PowerRequest;
import cyano.poweradvantage.api.PoweredEntity;
import cyano.poweradvantage.api.fluid.FluidRequest;
import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import net.minecraft.fluid.Fluid;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import zone.moddev.mc.poweradvantage.conduitnetwork.ConduitRegistry;
import zone.moddev.mc.poweradvantage.init.Entities;
import zone.moddev.mc.poweradvantage.init.Fluids;
import zone.moddev.mc.poweradvantage.util.FluidHandlerHelper;

/** Bridges adjacent Advantage fluid handlers onto the conduit request network. */
public class TerminalFluidPipeTileEntity extends PoweredEntity {
    private final Direction[] faces = Direction.values();
    private final IAdvantageFluidHandler[] neighbors = new IAdvantageFluidHandler[faces.length];

    public TerminalFluidPipeTileEntity() { super(Entities.PIPE_TERMINAL); }

    @Override public void tickUpdate(boolean server) {
        if (!server) return;
        for (int index = 0; index < faces.length; index++) {
            TileEntity tile = getWorld().getTileEntity(getPos().offset(faces[index]));
            neighbors[index] = FluidHandlerHelper.getHandler(tile, faces[index].getOpposite());
        }
    }

    @Override public boolean isPowerSink(ConduitType type) { return true; }
    @Override public boolean isPowerSource(ConduitType type) { return true; }
    @Override public boolean canAcceptConnection(PowerConnectorContext connection) {
        return ConduitType.areSameType(connection.powerType, Fluids.fluidConduit_general)
                || Fluids.conduitTypeToFluid(connection.powerType) != null;
    }
    @Override public ConduitType[] getTypes() { return new ConduitType[] { Fluids.fluidConduit_general }; }
    @Override public float getEnergyCapacity(ConduitType type) { return 0; }

    @Override public float getEnergy(ConduitType type) {
        int total = 0;
        for (int index = 0; index < neighbors.length; index++) {
            IAdvantageFluidHandler neighbor = neighbors[index];
            if (neighbor == null) continue;
            FluidStack available = neighbor.drain(faces[index].getOpposite(), Integer.MAX_VALUE, false);
            if (available != null) total += available.amount;
        }
        return total;
    }

    @Override public void powerUpdate() {
        for (int index = 0; index < neighbors.length; index++) {
            IAdvantageFluidHandler neighbor = neighbors[index];
            if (neighbor == null) continue;
            Direction face = faces[index].getOpposite();
            FluidStack available = neighbor.drain(face, Integer.MAX_VALUE, false);
            if (available == null || available.amount <= 0) continue;
            int moved = transmitFluidToConsumers(available, PowerRequest.LOW_PRIORITY);
            if (moved > 0) neighbor.drain(face, moved, true);
        }
    }

    protected int transmitFluidToConsumers(FluidStack available, byte minimumPriority) {
        ConduitType type = Fluids.fluidToConduitType(available.getFluid());
        List<PowerRequest> requests = ConduitRegistry.getInstance()
                .getRequestsForPower(getWorld(), getPos(), Fluids.fluidConduit_general, type);
        float remaining = available.amount;
        for (PowerRequest request : requests) {
            if (request.entity == this || request.amount <= 0) continue;
            if (request.priority < minimumPriority) break;
            float offered = Math.min(remaining, request.amount);
            float accepted = Math.max(0, Math.min(offered, request.entity.addEnergy(offered, type)));
            remaining -= accepted;
            if (remaining <= 0) break;
        }
        return available.amount - (int) remaining;
    }

    @Override public void setEnergy(float energy, ConduitType type) {}

    @Override public float addEnergy(float energy, ConduitType type) {
        Fluid fluid = Fluids.conduitTypeToFluid(type);
        if (fluid == null || energy <= 0) return 0;
        int accepted = 0;
        int offered = (int) energy;
        for (int index = 0; index < neighbors.length && accepted < offered; index++) {
            IAdvantageFluidHandler neighbor = neighbors[index];
            if (neighbor == null) continue;
            accepted += neighbor.fill(faces[index].getOpposite(), new FluidStack(fluid, offered - accepted), true);
        }
        return accepted;
    }

    @Override public float subtractEnergy(float energy, ConduitType type) { return 0; }

    @Override public PowerRequest getPowerRequest(ConduitType type) {
        Fluid fluid = Fluids.conduitTypeToFluid(type);
        if (fluid == null) return PowerRequest.REQUEST_NOTHING;
        int demand = 0;
        FluidStack offer = new FluidStack(fluid, Integer.MAX_VALUE);
        for (int index = 0; index < neighbors.length; index++) {
            IAdvantageFluidHandler neighbor = neighbors[index];
            if (neighbor != null) demand += neighbor.fill(faces[index].getOpposite(), offer, false);
        }
        return demand <= 0 ? PowerRequest.REQUEST_NOTHING
                : new FluidRequest((byte) (PowerRequest.LOW_PRIORITY - 1), demand, this);
    }
}
