package zone.moddev.mc.advantageworks;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidTankInfo;
import org.junit.Test;
import org.junit.BeforeClass;
import static org.junit.Assert.*;

public class LegacyFluidPortTest {
    @BeforeClass public static void bootstrap() {
        net.minecraft.init.Bootstrap.register();
        assertTrue(net.minecraftforge.fluids.FluidRegistry.registerFluid(WATER));
    }
    private static final Fluid WATER = new Fluid("fixture_water", new ResourceLocation("blocks/water_still"),
            new ResourceLocation("blocks/water_flow"));

    @Test public void legacySideAndSimulationAreNotLost() {
        LegacyTile tile = new LegacyTile();
        NativeFluidPort north = new NativeFluidPort(tile, EnumFacing.NORTH);
        NativeFluidPort south = new NativeFluidPort(tile, EnumFacing.SOUTH);
        FluidStack offer = new FluidStack(WATER, 700);
        assertEquals(0, south.fill(offer, true));
        assertEquals(700, north.fill(offer, false));
        assertEquals(0, north.amount(0));
        assertEquals(700, north.fill(offer, true));
        assertEquals(700, north.amount(0));
        assertEquals(700, offer.amount);
    }

    @Test public void legacyDrainsPreserveVolumeAndSimulation() {
        NativeFluidPort port = new NativeFluidPort(new LegacyTile(), EnumFacing.NORTH);
        port.fill(new FluidStack(WATER, 1000), true);
        assertEquals(250, port.drain(250, false).amount);
        assertEquals(1000, port.amount(0));
        assertEquals(250, port.drain(250, true).amount);
        assertEquals(750, port.amount(0));
    }

    @Test(expected = IllegalStateException.class) public void missingInterfaceIsNotAcceptedAsAnEmptyTank() {
        new NativeFluidPort(new TileEntity() {}, EnumFacing.NORTH);
    }

    public static final class LegacyTile extends TileEntity {
        final FluidTank tank = new FluidTank(1000);
        @Override public <T> T getCapability(Capability<T> capability, EnumFacing side) { return null; }
        public int fill(EnumFacing side, FluidStack resource, boolean execute) {
            return side == EnumFacing.NORTH ? tank.fill(resource, execute) : 0;
        }
        public FluidStack drain(EnumFacing side, int amount, boolean execute) {
            return side == EnumFacing.NORTH ? tank.drain(amount, execute) : null;
        }
        public FluidTankInfo[] getTankInfo(EnumFacing side) { return new FluidTankInfo[]{tank.getInfo()}; }
    }
}
