package zone.moddev.mc.advantageworks;

import java.util.Arrays;
import java.util.function.Predicate;

/** The walkable bays permit one track step, not an arbitrary search for a drill. */
final class BayDrillAssertions {
    private static final int[][] REAR = {{0, 1, 0}, {0, -1, 0}, {0, 0, 1},
            {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}};

    private BayDrillAssertions() { }

    static boolean applies(WorksDefinition.Station station, WorksDefinition.Assertion assertion) {
        return ("S-05".equals(station.id) && "steamadvantage:steam_drill".equals(assertion.block))
                || ("E-06".equals(station.id) && "electricadvantage:electric_drill".equals(assertion.block));
    }

    static int[] rearTrack(WorksDefinition.Station station, WorksDefinition.Assertion assertion) {
        int direction = assertion.meta == null
                ? Arrays.asList("down", "up", "north", "south", "west", "east").indexOf(assertion.expectedText)
                : assertion.meta;
        if (direction < 0 || direction >= REAR.length) throw new IllegalArgumentException("Invalid drill facing");
        int[] rear = assertion.pos.clone();
        for (int i = 0; i < 3; i++) rear[i] += REAR[direction][i];
        String track = assertion.block.startsWith("steamadvantage:")
                ? "steamadvantage:steam_track" : "electricadvantage:electric_track";
        if (station.placements.stream().noneMatch(p -> track.equals(p.block) && Arrays.equals(p.pos, rear))) {
            throw new IllegalArgumentException("Drill bay has no declared rear track");
        }
        return rear;
    }

    static boolean matches(WorksDefinition.Station station, WorksDefinition.Assertion assertion,
                           Predicate<int[]> isDrill, Predicate<int[]> hasFacing) {
        int[] rear = rearTrack(station, assertion);
        boolean initial = isDrill.test(assertion.pos);
        boolean moved = isDrill.test(rear);
        return initial != moved && hasFacing.test(initial ? assertion.pos : rear);
    }
}
