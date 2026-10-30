package cyano.poweradvantage.api.fluid;

import java.util.List;

import javax.annotation.Nullable;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PowerRequest;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import zone.moddev.mc.poweradvantage.conduitnetwork.ConduitRegistry;
import zone.moddev.mc.poweradvantage.init.Fluids;

/**
 * Stable entry point for offering fluid from another mod to a PowerAdvantage
 * fluid-conduit network.
 */
public final class FluidNetworkApi {
    private FluidNetworkApi() {
        throw new IllegalAccessError("This class cannot be instantiated");
    }

    /**
     * Offers fluid to every eligible consumer connected to the conduit at the
     * supplied position without mutating the offered stack.
     *
     * @return the exact number of millibuckets accepted by the network
     */
    public static int offerFluid(World world, BlockPos conduitPosition, FluidStack offered, int maxTransfer) {
        return offerFluid(world, conduitPosition, offered, maxTransfer, PowerRequest.LAST_PRIORITY, null);
    }

    /**
     * Offers fluid to a conduit network without mutating the supplied stack.
     * The caller remains responsible for draining the returned amount.
     *
     * @param minimumPriority lowest request priority that may receive fluid
     * @param source optional source object to exclude from receiving its own offer
     * @return the exact number of millibuckets accepted by consumers
     */
    public static int offerFluid(World world, BlockPos conduitPosition, FluidStack offered, int maxTransfer,
            byte minimumPriority, @Nullable Object source) {
        if (world == null || world.isClientSide || conduitPosition == null || offered == null
                || offered.getFluid() == null || offered.amount <= 0 || maxTransfer <= 0
                || !world.hasChunkAt(conduitPosition)) {
            return 0;
        }

        Block conduit = world.getBlockState(conduitPosition).getBlock();
        if (!(conduit instanceof FluidConduitBlock)) return 0;

        ConduitType fluidType = Fluids.fluidToConduitType(offered.getFluid());
        List<PowerRequest> requests = ConduitRegistry.getInstance().getRequestsForPower(
                world, conduitPosition, Fluids.fluidConduit_general, fluidType);
        return distributeFluid(requests, fluidType, offered, maxTransfer, minimumPriority, source);
    }

    static int distributeFluid(List<PowerRequest> requests, ConduitType fluidType, FluidStack offered,
            int maxTransfer, byte minimumPriority, @Nullable Object source) {
        if (offered == null || offered.getFluid() == null || offered.amount <= 0 || maxTransfer <= 0) return 0;
        return distributeFluid(requests, fluidType, Math.min(offered.amount, maxTransfer), minimumPriority, source);
    }

    static int distributeFluid(List<PowerRequest> requests, ConduitType fluidType, int available,
            byte minimumPriority, @Nullable Object source) {
        if (requests == null || fluidType == null || available <= 0) return 0;
        int remaining = available;
        for (PowerRequest request : requests) {
            if (remaining <= 0) break;
            if (request == null || request.amount <= 0 || request.entity == null || request.entity == source) continue;
            if (request.priority < minimumPriority) break;

            int requested = Math.min(remaining, positiveInt(request.amount));
            if (requested <= 0) continue;
            int accepted = Math.min(requested, positiveInt(request.entity.addEnergy(requested, fluidType)));
            remaining -= accepted;
        }
        return available - remaining;
    }

    private static int positiveInt(float value) {
        if (Float.isNaN(value) || value <= 0) return 0;
        return value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
