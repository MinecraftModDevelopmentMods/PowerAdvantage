package zone.moddev.mc.advantageworks;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class MachineScenarioTimedHooksTest {
    private static final String LONG = "steam/lifecycle/network-endurance-30min";

    private static MachineScenario scenario(String id, int ticks) {
        return new MachineScenario(id, "Bounded test contract", ticks, c -> {}, c -> {});
    }

    @Test public void onlyExactEnduranceIdMayExceedOrdinaryLimit() {
        assertEquals(36000, scenario(LONG, 36000).ticks);
        assertThrows(IllegalArgumentException.class, () -> scenario(LONG, 36001));
        for (String id : new String[]{"steam/ordinary", "steam/lifecycle/network-endurance-smoke",
                "steam/lifecycle/network-endurance-30min-extra", "electric/lifecycle/network-endurance-30min"}) {
            assertEquals(12000, scenario(id, 12000).ticks);
            assertThrows(IllegalArgumentException.class, () -> scenario(id, 12001));
        }
    }

    @Test public void ordinaryScenariosHaveNoTimedOrCleanupSideEffects() {
        MachineScenario scenario = scenario("steam/ordinary", 100);
        assertNull(scenario.timed);
        assertNull(scenario.cleanup);
    }

    @Test public void callbacksAreExplicitAndCannotBeReassignedOrSetNull() {
        AtomicInteger timed = new AtomicInteger(), cleaned = new AtomicInteger();
        MachineScenario scenario = scenario("steam/ordinary", 100)
                .timed(c -> timed.incrementAndGet()).cleanup(c -> cleaned.incrementAndGet());
        assertEquals(0, timed.get()); assertEquals(0, cleaned.get());
        scenario.timed.accept(null); scenario.cleanup.accept(null);
        assertEquals(1, timed.get()); assertEquals(1, cleaned.get());
        assertThrows(IllegalStateException.class, () -> scenario.timed(c -> {}));
        assertThrows(IllegalStateException.class, () -> scenario.cleanup(c -> {}));
        assertThrows(NullPointerException.class, () -> scenario("steam/null-timed", 1).timed(null));
        assertThrows(NullPointerException.class, () -> scenario("steam/null-cleanup", 1).cleanup(null));
    }
}
