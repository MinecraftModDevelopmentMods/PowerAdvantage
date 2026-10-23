package zone.moddev.mc.advantageworks;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class LifecycleProbeTest {
    private LifecycleProbe.Fixture fixture() {
        LifecycleProbe.Fixture fixture = new LifecycleProbe.Fixture();
        fixture.id = "S-MOVE-east";
        fixture.min = new int[] {2048, 70, 2048};
        fixture.max = new int[] {2079, 90, 2079};
        fixture.from = new int[] {2063, 80, 2063};
        fixture.to = new int[] {2063, 80, 2064};
        fixture.hold = new int[] {2063, 81, 2064};
        fixture.block = "steamadvantage:steam_drill";
        fixture.energyType = "steam";
        fixture.initialEnergy = 40;
        fixture.witnesses = Arrays.asList(fixture.from, fixture.to, fixture.hold);
        return fixture;
    }

    @Test public void boundedMoveAccepted() { LifecycleProbe.validate(fixture()); }
    @Test public void boundedOilBucketAccepted() {
        LifecycleProbe.Fixture f = fixture(); f.bucket = "poweradvantage:crude_oil_bucket";
        f.source = new int[] {2060, 80, 2060}; LifecycleProbe.validate(f);
    }
    @Test(expected = IllegalArgumentException.class) public void oilBucketOutsideBoundsRejected() {
        LifecycleProbe.Fixture f = fixture(); f.bucket = "poweradvantage:crude_oil_bucket";
        f.source = new int[] {2080, 80, 2060}; LifecycleProbe.validate(f);
    }
    @Test(expected = IllegalArgumentException.class) public void arbitraryBucketRejected() {
        LifecycleProbe.Fixture f = fixture(); f.bucket = "minecraft:lava_bucket";
        f.source = new int[] {2060, 80, 2060}; LifecycleProbe.validate(f);
    }
    @Test(expected = IllegalArgumentException.class) public void outsideBoundsRejected() {
        LifecycleProbe.Fixture f = fixture(); f.hold[0] = 2080; LifecycleProbe.validate(f);
    }
    @Test(expected = IllegalArgumentException.class) public void teleportRejected() {
        LifecycleProbe.Fixture f = fixture(); f.to[2]++; LifecycleProbe.validate(f);
    }
    @Test(expected = IllegalArgumentException.class) public void spawnAreaRejected() {
        LifecycleProbe.Fixture f = fixture(); f.min[0] = 0; LifecycleProbe.validate(f);
    }
    @Test public void repeatedRouteAccepted() {
        LifecycleProbe.Fixture f = fixture();
        f.route = Arrays.asList(f.from, f.to, new int[] {2063, 80, 2065});
        f.to = f.route.get(2);
        f.controls = Arrays.asList(f.hold, f.hold, f.hold);
        LifecycleProbe.validate(f);
    }
    @Test(expected = IllegalArgumentException.class) public void routeTeleportRejected() {
        LifecycleProbe.Fixture f = fixture();
        f.route = Arrays.asList(f.from, new int[] {2063, 80, 2066});
        f.to = f.route.get(1); f.controls = Arrays.asList(f.hold, f.hold);
        LifecycleProbe.validate(f);
    }
    @Test(expected = IllegalArgumentException.class) public void unboundedChunkSelectionRejected() {
        LifecycleProbe.Fixture f = fixture();
        f.groups = Collections.singletonMap("supply", Arrays.asList(new int[] {0, 0}));
        LifecycleProbe.validate(f);
    }
}
