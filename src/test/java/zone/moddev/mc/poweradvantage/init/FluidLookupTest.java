package zone.moddev.mc.poweradvantage.init;

import cyano.poweradvantage.api.fluid.FluidStack;
import net.minecraft.nbt.CompoundNBT;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class FluidLookupTest {
    @BeforeClass public static void bootstrap() { net.minecraft.util.registry.Bootstrap.bootStrap(); }

    @Test public void resolvesLegacyUnqualifiedVanillaNamesInsteadOfTheEmptyDefault() {
        assertSame(net.minecraft.fluid.Fluids.WATER, Fluids.byName("water"));
        assertSame(net.minecraft.fluid.Fluids.LAVA, Fluids.byName("lava"));
        assertSame(net.minecraft.fluid.Fluids.WATER, Fluids.byName("minecraft:water"));
    }

    @Test public void unknownNamesAreNotReplacedByAnEmptyFluid() {
        assertNull(Fluids.byName("not_registered"));
        assertNull(Fluids.byName("missingmod:not_registered"));
        assertNull(Fluids.byName(null));
    }

    @Test public void retainsAmountAndNbtWhenReadingLegacySavedWater() {
        CompoundNBT data = new CompoundNBT();
        data.putString("FluidName", "water");
        data.putInt("Amount", 700);
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("batch", 37);
        data.put("Tag", tag);
        FluidStack stack = FluidStack.loadFluidStackFromNBT(data);
        assertNotNull(stack);
        assertSame(net.minecraft.fluid.Fluids.WATER, stack.getFluid());
        assertEquals(700, stack.amount);
        assertEquals(tag, stack.tag);
    }
}
