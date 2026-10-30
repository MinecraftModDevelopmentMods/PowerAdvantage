package zone.moddev.mc.poweradvantage.util;

import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.FluidTank;
import cyano.poweradvantage.api.fluid.FluidTankInfo;
import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.Direction;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import org.junit.Test;
import org.junit.BeforeClass;
import static org.junit.Assert.*;

public class ForgeFluidAdapterTest {
    private static Fluid WATER;

    @BeforeClass public static void bootstrapNativeRegistries() {
        net.minecraft.util.registry.Bootstrap.bootStrap();
        WATER = net.minecraft.fluid.Fluids.WATER;
    }

    @Test public void simulatesWithoutChangingTankOrInputThenExecutesExactAmount() {
        TankHandler tank = new TankHandler();
        int[] changes = {0};
        ForgeFluidAdapter adapter = new ForgeFluidAdapter(tank, Direction.NORTH, () -> changes[0]++);
        net.minecraftforge.fluids.FluidStack offered = new net.minecraftforge.fluids.FluidStack(WATER, 1500);
        CompoundNBT tag = new CompoundNBT();
        tag.putString("Owner", "preserve-me");
        offered.setTag(tag);
        assertEquals(1000, adapter.fill(offered, FluidAction.SIMULATE));
        assertEquals(0, tank.tank.getFluidAmount());
        assertEquals(0, changes[0]);
        assertEquals(1000, adapter.fill(offered, FluidAction.EXECUTE));
        assertEquals(1000, tank.tank.getFluidAmount());
        assertEquals(1500, offered.getAmount());
        assertEquals(tag, offered.getTag());
        assertEquals(1, changes[0]);
        tank.tank.getFluid().tag.putString("Owner", "changed-in-tank");
        assertEquals("preserve-me", offered.getTag().getString("Owner"));
    }

    @Test public void keepsSidedRestrictionsAndRejectsEmptyOrInvalidRequests() {
        TankHandler tank = new TankHandler();
        ForgeFluidAdapter adapter = new ForgeFluidAdapter(tank, Direction.SOUTH, () -> fail("Changed blocked tank"));
        assertFalse(adapter.isFluidValid(0, new net.minecraftforge.fluids.FluidStack(WATER, 10)));
        assertEquals(0, adapter.fill(new net.minecraftforge.fluids.FluidStack(WATER, 10), FluidAction.EXECUTE));
        assertEquals(0, adapter.fill(net.minecraftforge.fluids.FluidStack.EMPTY, FluidAction.EXECUTE));
        assertTrue(adapter.drain(-1, FluidAction.EXECUTE).isEmpty());
        assertTrue(adapter.drain(net.minecraftforge.fluids.FluidStack.EMPTY, FluidAction.EXECUTE).isEmpty());
        assertEquals(0, adapter.getTankCapacity(-1));
        assertTrue(adapter.getFluidInTank(8).isEmpty());
    }

    @Test public void drainsWithSimulationLimitsAndIndependentSnapshots() {
        TankHandler tank = new TankHandler();
        tank.tank.fill(new FluidStack(WATER, 900), true);
        int[] changes = {0};
        ForgeFluidAdapter adapter = new ForgeFluidAdapter(tank, Direction.NORTH, () -> changes[0]++);
        net.minecraftforge.fluids.FluidStack view = adapter.getFluidInTank(0);
        view.setAmount(1);
        assertEquals(900, tank.tank.getFluidAmount());
        assertEquals(300, adapter.drain(300, FluidAction.SIMULATE).getAmount());
        assertEquals(900, tank.tank.getFluidAmount());
        assertEquals(0, changes[0]);
        net.minecraftforge.fluids.FluidStack request = new net.minecraftforge.fluids.FluidStack(WATER, 250);
        assertEquals(250, adapter.drain(request, FluidAction.EXECUTE).getAmount());
        assertEquals(250, request.getAmount());
        assertEquals(650, tank.tank.getFluidAmount());
        assertEquals(650, adapter.drain(1000, FluidAction.EXECUTE).getAmount());
        assertEquals(0, tank.tank.getFluidAmount());
        assertEquals(2, changes[0]);
    }

    @Test public void copiesBothDirectionsIncludingNbt() {
        FluidStack source = new FluidStack(WATER, 700);
        source.tag = new CompoundNBT();
        source.tag.putInt("batch", 7);
        net.minecraftforge.fluids.FluidStack nativeStack = ForgeFluidAdapter.toForge(source);
        FluidStack copy = ForgeFluidAdapter.fromForge(nativeStack);
        copy.tag.putInt("batch", 8);
        assertEquals(7, source.tag.getInt("batch"));
        assertEquals(7, nativeStack.getTag().getInt("batch"));
        assertNull(ForgeFluidAdapter.fromForge(net.minecraftforge.fluids.FluidStack.EMPTY));
        assertTrue(ForgeFluidAdapter.toForge(null).isEmpty());
    }

    private static class TankHandler implements IAdvantageFluidHandler {
        final FluidTank tank = new FluidTank(1000);
        @Override public FluidTankInfo[] getTankInfo(Direction face) { return new FluidTankInfo[] {tank.getInfo()}; }
        @Override public boolean canFill(Direction face, Fluid fluid) { return face == Direction.NORTH; }
        @Override public boolean canDrain(Direction face, Fluid fluid) { return face == Direction.NORTH; }
        @Override public int fill(Direction face, FluidStack stack, boolean execute) { return tank.fill(stack, execute); }
        @Override public FluidStack drain(Direction face, FluidStack stack, boolean execute) { return tank.drain(stack, execute); }
        @Override public FluidStack drain(Direction face, int amount, boolean execute) { return tank.drain(amount, execute); }
    }
}
