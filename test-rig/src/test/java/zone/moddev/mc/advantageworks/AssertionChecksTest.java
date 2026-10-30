package zone.moddev.mc.advantageworks;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class AssertionChecksTest {
    @Test public void aHarnessErrorIsNotEvidenceOfAKnownGameplayBug() {
        assertEquals("FAIL", AssertionChecks.verdict(false, "SA-110-001", new IllegalArgumentException("bad path")));
        assertEquals("EXPECTED_FAIL", AssertionChecks.verdict(false, "SA-110-001", null));
        assertEquals("UNEXPECTED_PASS", AssertionChecks.verdict(true, "SA-110-001", null));
    }

    @Test public void emptyAndWhollySkippedMeasurementsAreNotPasses() {
        assertEquals("SKIP", AssertionChecks.aggregate(Collections.emptyList(), false));
        assertEquals("SKIP", AssertionChecks.aggregate(Collections.singletonList(row("SKIP")), true));
        assertEquals("FAIL", AssertionChecks.aggregate(Collections.singletonList(row("RUNNING")), false));
    }

    @Test public void mixedResultsCannotHideFailureOrManualWork() {
        assertEquals("FAIL", AssertionChecks.aggregate(Arrays.asList(row("PASS"), row("FAIL")), true));
        assertEquals("MANUAL_REQUIRED", AssertionChecks.aggregate(Arrays.asList(row("PASS"), row("SKIP")), true));
        assertEquals("PASS", AssertionChecks.aggregate(Collections.singletonList(row("PASS")), false));
    }

    @Test(expected = IllegalArgumentException.class) public void missingTankCannotSatisfyZero() {
        AssertionChecks.fluidAmount(new NBTTagCompound(), "Tank", null);
    }

    @Test(expected = IllegalArgumentException.class) public void missingAmountCannotSatisfyZero() {
        NBTTagCompound tile = new NBTTagCompound();
        tile.setTag("Tank", new NBTTagCompound());
        AssertionChecks.fluidAmount(tile, "Tank", null);
    }

    @Test(expected = IllegalArgumentException.class) public void emptyFalseIsNotAnEmptyTank() {
        NBTTagCompound tile = new NBTTagCompound(), tank = new NBTTagCompound();
        tank.setBoolean("Empty", false);
        tile.setTag("Tank", tank);
        AssertionChecks.fluidAmount(tile, "Tank", null);
    }

    @Test public void explicitEmptyAndMeasuredFluidAreDifferentFromMissingEvidence() {
        NBTTagCompound tile = new NBTTagCompound(), tank = new NBTTagCompound();
        tank.setBoolean("Empty", true);
        tile.setTag("Tank", tank);
        assertEquals(0, AssertionChecks.fluidAmount(tile, "Tank", null));
        tank.removeTag("Empty");
        tank.setInt("Amount", 1000);
        tank.setString("FluidName", "minecraft:water");
        assertEquals(1000, AssertionChecks.fluidAmount(tile, "Tank", "minecraft:water"));
        assertEquals(0, AssertionChecks.fluidAmount(tile, "Tank", "poweradvantage:crude_oil"));
    }

    private Map<String, Object> row(String status) { return Collections.singletonMap("status", status); }
}
