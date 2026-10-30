package zone.moddev.mc.advantageworks;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.*;

public class BayDrillAssertionsTest {
    @Test public void initialAndSingleMovedWitnessesKeepAllTwelveFacingChecks() {
        int count = 0;
        for (WorksDefinition.Station station : WorksDefinitionLoader.load().stations) {
            for (WorksDefinition.Assertion assertion : station.assertions) {
                if (!BayDrillAssertions.applies(station, assertion)) continue;
                assertTrue(matches(station, assertion, assertion.pos));
                assertTrue(station.id + "/" + assertion.id + " on its declared rear track",
                        matches(station, assertion, BayDrillAssertions.rearTrack(station, assertion)));
                count++;
            }
        }
        assertEquals(12, count);
    }

    @Test public void missingDuplicateWrongFacingAndOffTrackWitnessesFail() {
        for (WorksDefinition.Station station : WorksDefinitionLoader.load().stations) {
            for (WorksDefinition.Assertion assertion : station.assertions) {
                if (!BayDrillAssertions.applies(station, assertion)) continue;
                int[] rear = BayDrillAssertions.rearTrack(station, assertion);
                assertFalse(matches(station, assertion));
                assertFalse(matches(station, assertion, assertion.pos, rear));
                assertFalse(BayDrillAssertions.matches(station, assertion, p -> true, p -> false));
                int[] stray = rear.clone();
                stray[0] += 2;
                assertFalse(matches(station, assertion, stray));
            }
        }
    }

    @Test public void noUndeclaredTrackFallbackIsAllowed() {
        WorksDefinition.Station station = WorksDefinitionLoader.load().stations.stream()
                .filter(s -> "S-05".equals(s.id)).findFirst().get();
        station.placements.clear();
        try {
            BayDrillAssertions.rearTrack(station, station.assertions.get(0));
            fail("Undeclared movement accepted");
        } catch (IllegalArgumentException expected) {
            assertEquals("Drill bay has no declared rear track", expected.getMessage());
        }
    }

    private static boolean matches(WorksDefinition.Station station, WorksDefinition.Assertion assertion,
                                   int[]... present) {
        Set<String> positions = new HashSet<>();
        for (int[] p : present) positions.add(Arrays.toString(p));
        return BayDrillAssertions.matches(station, assertion,
                p -> positions.contains(Arrays.toString(p)), p -> positions.contains(Arrays.toString(p)));
    }
}
