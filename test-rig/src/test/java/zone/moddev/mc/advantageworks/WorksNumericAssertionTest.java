package zone.moddev.mc.advantageworks;

import org.junit.Test;
import static org.junit.Assert.*;

public class WorksNumericAssertionTest {
    @Test public void infiniteEnergyCannotSatisfyMinimum() throws Exception {
        assertFalse(compare(Double.POSITIVE_INFINITY, "min", 1));
    }

    @Test public void negativeInfinityCannotSatisfyMaximum() throws Exception {
        assertFalse(compare(Double.NEGATIVE_INFINITY, "max", 0));
    }

    @Test public void ordinaryFiniteComparisonsStillWork() throws Exception {
        assertTrue(compare(1000, "equals", 1000));
        assertFalse(compare(999, "equals", 1000));
    }

    private boolean compare(double value, String operator, Number expected) throws Exception {
        return AssertionChecks.compare(value, operator, expected);
    }
}
