package zone.moddev.mc.advantageworks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import org.junit.Test;
import static org.junit.Assert.*;

public class MeasurementDefinitionTest {
    @Test public void emptyAssertionsAreNotAValidTest() throws Exception {
        rejects(station -> station.assertions.clear());
    }

    @Test public void aMisspelledOperatorCannotBecomeAKnownFailure() throws Exception {
        rejects(station -> station.assertions.get(0).operator = "equlas");
    }

    @Test public void nonfiniteExpectationsAreNotValidMeasurements() throws Exception {
        rejects(station -> station.assertions.get(0).value = Double.POSITIVE_INFINITY);
    }

    @Test public void tileMeasurementNeedsAPosition() throws Exception {
        rejects(station -> station.assertions.get(0).pos = null);
    }

    private void rejects(Consumer<WorksDefinition.Station> corrupt) throws Exception {
        WorksDefinition.Station station = WorksDefinitionLoader.loadNative116().stations.get(0);
        corrupt.accept(station);
        Method validate = WorksDefinitionLoader.class.getDeclaredMethod("validatePlacements", WorksDefinition.Station.class);
        validate.setAccessible(true);
        try { validate.invoke(null, station); fail("Malformed measurement accepted"); }
        catch (InvocationTargetException expected) { assertTrue(expected.getCause() instanceof IllegalArgumentException); }
    }
}
