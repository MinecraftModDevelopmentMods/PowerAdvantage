package zone.moddev.mc.advantageworks;

import java.nio.file.Paths;
import org.junit.Test;
import static org.junit.Assert.*;

public class WorksWorldGuardTest {
    @Test public void nativeRootDotRetainsTheActualWorldName() {
        assertTrue(WorksWorldGuard.authorized(Paths.get("runtime/AdvantageWorksMigration/."), true));
    }

    @Test public void plainMarkedWorksWorldRemainsAllowed() {
        assertTrue(WorksWorldGuard.authorized(Paths.get("runtime/AdvantageWorks"), true));
    }

    @Test public void missingMarkerStillFailsClosed() {
        assertFalse(WorksWorldGuard.authorized(Paths.get("runtime/AdvantageWorksMigration/."), false));
    }

    @Test public void unrelatedWorldStillFailsClosed() {
        assertFalse(WorksWorldGuard.authorized(Paths.get("runtime/Sylvester/."), true));
    }

    @Test public void parentTraversalCannotBorrowAWorksPrefix() {
        assertFalse(WorksWorldGuard.authorized(Paths.get("runtime/AdvantageWorks/../Sylvester/."), true));
    }

    @Test public void missingWorldFailsClosed() {
        assertFalse(WorksWorldGuard.authorized(null, true));
    }
}
