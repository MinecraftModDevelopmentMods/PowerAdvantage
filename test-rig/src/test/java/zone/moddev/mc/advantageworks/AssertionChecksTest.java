package zone.moddev.mc.advantageworks;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import net.minecraft.nbt.CompoundNBT;
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
        AssertionChecks.fluidAmount(new CompoundNBT(), "Tank", null);
    }

    @Test(expected = IllegalArgumentException.class) public void missingAmountCannotSatisfyZero() {
        CompoundNBT tile = new CompoundNBT();
        tile.put("Tank", new CompoundNBT());
        AssertionChecks.fluidAmount(tile, "Tank", null);
    }

    @Test(expected = IllegalArgumentException.class) public void emptyFalseIsNotAnEmptyTank() {
        CompoundNBT tile = new CompoundNBT(), tank = new CompoundNBT();
        tank.putBoolean("Empty", false);
        tile.put("Tank", tank);
        AssertionChecks.fluidAmount(tile, "Tank", null);
    }

    @Test public void explicitEmptyAndMeasuredFluidAreDifferentFromMissingEvidence() {
        CompoundNBT tile = new CompoundNBT(), tank = new CompoundNBT();
        tank.putBoolean("Empty", true);
        tile.put("Tank", tank);
        assertEquals(0, AssertionChecks.fluidAmount(tile, "Tank", null));
        tank.remove("Empty");
        tank.putInt("Amount", 1000);
        tank.putString("FluidName", "minecraft:water");
        assertEquals(1000, AssertionChecks.fluidAmount(tile, "Tank", "minecraft:water"));
        assertEquals(0, AssertionChecks.fluidAmount(tile, "Tank", "poweradvantage:crude_oil"));
    }

    private Map<String, Object> row(String status) { return Collections.singletonMap("status", status); }
}
