package zone.moddev.mc.advantageworks;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class SteamReloadControlHoldTest {
    @Test public void holdRemovesOnlyTheMeasuredObjectAndKeepsOtherRegistrationsInOrder() {
        Object before = new Object(), measured = new Object(), after = new Object();
        List<Object> tickables = new ArrayList<>(Arrays.asList(before, measured, after));
        SteamReloadControlCases.removeMeasuredTile(tickables, measured);
        assertEquals(Arrays.asList(before, after), tickables);
    }

    @Test public void missingRegistrationIsAFailureRatherThanAnUnobservedHold() {
        Object other = new Object();
        List<Object> tickables = new ArrayList<>(Collections.singletonList(other));
        try { SteamReloadControlCases.removeMeasuredTile(tickables, new Object()); fail("Missing actual registration"); }
        catch (AssertionError expected) { assertTrue(expected.getMessage().contains("found 0")); }
        assertEquals(Collections.singletonList(other), tickables);
    }

    @Test public void duplicateRegistrationFailsWithoutRemovingEvidence() {
        Object measured = new Object();
        List<Object> tickables = new ArrayList<>(Arrays.asList(measured, measured));
        try { SteamReloadControlCases.removeMeasuredTile(tickables, measured); fail("Duplicate actual registrations"); }
        catch (AssertionError expected) { assertTrue(expected.getMessage().contains("found 2")); }
        assertEquals(2, tickables.size());
    }

    @Test public void equalButDifferentObjectsCannotBeRemovedInsteadOfTheMeasuredInstance() {
        String other = new String("equal"), measured = new String("equal");
        List<String> tickables = new ArrayList<>(Arrays.asList(other, measured));
        SteamReloadControlCases.removeMeasuredTile(tickables, measured);
        assertEquals(1, tickables.size());
        assertSame(other, tickables.get(0));
    }

    @Test public void catalogContainsAllSixExclusiveOneTickControls() {
        List<MachineScenario> cases = new ArrayList<>();
        SteamReloadControlCases.register(cases);
        Set<String> expected = new HashSet<>(Arrays.asList(
                "steam/lifecycle/crusher-comparator-matrix",
                "steam/lifecycle/crusher-first-loaded-unpowered",
                "steam/lifecycle/crusher-first-loaded-signalled",
                "steam/lifecycle/crusher-first-loaded-no-steam",
                "steam/lifecycle/furnace-first-loaded-unpowered",
                "steam/lifecycle/furnace-first-loaded-signalled"));
        assertEquals(6, cases.size());
        for (MachineScenario scenario : cases) {
            assertTrue(expected.remove(scenario.id));
            assertEquals(1, scenario.ticks);
            assertTrue(scenario.exclusive);
        }
        assertTrue(expected.isEmpty());
    }
}
