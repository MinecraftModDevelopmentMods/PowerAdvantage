package cyano.poweradvantage.api.fluid;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.IPowerMachine;
import cyano.poweradvantage.api.PowerConnectorContext;
import cyano.poweradvantage.api.PowerRequest;
import net.minecraft.fluid.EmptyFluid;
import net.minecraft.fluid.Fluid;

public class FluidNetworkApiTest {
    private static final ConduitType WATER = new ConduitType("test-water");
    private static final Fluid TEST_FLUID = testFluid();

    private static Fluid testFluid() {
        net.minecraft.util.registry.Bootstrap.bootStrap();
        return new EmptyFluid();
    }

    @Test
    public void distributesOnlyWhatConsumersAccept() {
        Sink partial = new Sink(25);
        Sink full = new Sink(100);
        int accepted = FluidNetworkApi.distributeFluid(Arrays.asList(
                new PowerRequest(PowerRequest.HIGH_PRIORITY, 80, partial),
                new PowerRequest(PowerRequest.MEDIUM_PRIORITY, 100, full)),
                WATER, 100, PowerRequest.LAST_PRIORITY, null);
        assertEquals(100, accepted);
        assertEquals(25, partial.stored, 0);
        assertEquals(75, full.stored, 0);
    }

    @Test
    public void respectsPriorityAndSourceExclusion() {
        Sink source = new Sink(100);
        Sink lowPriority = new Sink(100);
        int accepted = FluidNetworkApi.distributeFluid(Arrays.asList(
                new PowerRequest(PowerRequest.HIGH_PRIORITY, 100, source),
                new PowerRequest(PowerRequest.BACKUP_PRIORITY, 100, lowPriority)),
                WATER, 100, PowerRequest.LOW_PRIORITY, source);
        assertEquals(0, accepted);
        assertEquals(0, source.stored, 0);
        assertEquals(0, lowPriority.stored, 0);
    }

    @Test
    public void respectsTransferLimitWithoutMutatingOffer() {
        Sink sink = new Sink(1000);
        FluidStack offered = new FluidStack(TEST_FLUID, 750);
        int accepted = FluidNetworkApi.distributeFluid(Arrays.asList(
                new PowerRequest(PowerRequest.MEDIUM_PRIORITY, 1000, sink)),
                WATER, offered, 300, PowerRequest.LAST_PRIORITY, null);
        assertEquals(300, accepted);
        assertEquals(750, offered.amount);
    }

    @Test
    public void clampsInvalidConsumerResponses() {
        Sink overAccepting = new Sink(1000) {
            @Override public float addEnergy(float energy, ConduitType type) { return energy + 100; }
        };
        Sink rejecting = new Sink(1000) {
            @Override public float addEnergy(float energy, ConduitType type) { return -10; }
        };
        assertEquals(40, FluidNetworkApi.distributeFluid(Arrays.asList(
                new PowerRequest(PowerRequest.MEDIUM_PRIORITY, 100, overAccepting)),
                WATER, 40, PowerRequest.LAST_PRIORITY, null));
        assertEquals(0, FluidNetworkApi.distributeFluid(Arrays.asList(
                new PowerRequest(PowerRequest.MEDIUM_PRIORITY, 100, rejecting)),
                WATER, 40, PowerRequest.LAST_PRIORITY, null));
    }

    private static class Sink implements IPowerMachine {
        private final float acceptanceLimit;
        private float stored;

        private Sink(float acceptanceLimit) { this.acceptanceLimit = acceptanceLimit; }
        @Override public float getEnergyCapacity(ConduitType type) { return acceptanceLimit; }
        @Override public float getEnergy(ConduitType type) { return stored; }
        @Override public void setEnergy(float energy, ConduitType type) { stored = energy; }
        @Override public float addEnergy(float energy, ConduitType type) {
            float accepted = Math.min(energy, acceptanceLimit - stored);
            stored += accepted;
            return accepted;
        }
        @Override public float subtractEnergy(float energy, ConduitType type) { return 0; }
        @Override public PowerRequest getPowerRequest(ConduitType type) { return PowerRequest.REQUEST_NOTHING; }
        @Override public boolean isPowerSink(ConduitType type) { return true; }
        @Override public boolean isPowerSource(ConduitType type) { return false; }
        @Override public boolean canAcceptConnection(PowerConnectorContext connection) { return true; }
        @Override public ConduitType[] getTypes() { return new ConduitType[] { WATER }; }
    }
}
