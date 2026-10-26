package zone.moddev.mc.advantageworks;

import java.util.Arrays;
import java.util.LinkedHashMap;
import org.junit.Test;

public class NativeTicketFixtureTest {
    private LifecycleProbe.Fixture network() {
        LifecycleProbe.Fixture f = new LifecycleProbe.Fixture();
        f.id = "C-FLUID";
        f.nativeTicketNetwork = true;
        f.min = new int[] {20480, 72, 2048};
        f.max = new int[] {20639, 90, 2079};
        f.witnesses = Arrays.asList(new int[] {20488, 80, 2056}, new int[] {20616, 80, 2056});
        f.groups = new LinkedHashMap<>();
        f.groups.put("supply", Arrays.asList(new int[] {1280, 128}));
        f.groups.put("machine", Arrays.asList(new int[] {1288, 128}));
        return f;
    }

    @Test public void boundedNativeNetworkAccepted() { LifecycleProbe.validate(network(), () -> false); }
    @Test public void boundedFullHolderNetworkAccepted() {
        LifecycleProbe.Fixture f = network(); f.max[0] = f.min[0] + 543;
        LifecycleProbe.validate(f, () -> false);
    }
    @Test(expected = IllegalArgumentException.class) public void excessiveHolderFootprintRejected() {
        LifecycleProbe.Fixture f = network(); f.max[0] = f.min[0] + 544;
        LifecycleProbe.validate(f, () -> false);
    }
    @Test(expected = IllegalArgumentException.class) public void oldFixtureBoundsRemainStrict() {
        LifecycleProbe.Fixture f = network(); f.nativeTicketNetwork = false;
        LifecycleProbe.validate(f, () -> false);
    }
    @Test(expected = IllegalArgumentException.class) public void arbitraryMachineCannotOptIn() {
        LifecycleProbe.Fixture f = network(); f.id = "S-MOVE-east";
        LifecycleProbe.validate(f, () -> false);
    }
    @Test(expected = IllegalArgumentException.class) public void bothAxesCannotExpand() {
        LifecycleProbe.Fixture f = network(); f.max[2] = f.min[2] + 159;
        LifecycleProbe.validate(f, () -> false);
    }
    @Test(expected = IllegalArgumentException.class) public void unboundedNativeLengthRejected() {
        LifecycleProbe.Fixture f = network(); f.max[0]++;
        LifecycleProbe.validate(f, () -> false);
    }
    @Test(expected = IllegalArgumentException.class) public void tallNativeNetworkRejected() {
        LifecycleProbe.Fixture f = network(); f.max[1] = f.min[1] + 32;
        LifecycleProbe.validate(f, () -> false);
    }
    @Test(expected = IllegalArgumentException.class) public void undeclaredGroupsRejected() {
        LifecycleProbe.Fixture f = network(); f.groups.remove("supply");
        LifecycleProbe.validate(f, () -> false);
    }
    @Test(expected = IllegalArgumentException.class) public void outOfBoundsNativeGroupRejected() {
        LifecycleProbe.Fixture f = network(); f.groups.put("bridge", Arrays.asList(new int[] {0, 0}));
        LifecycleProbe.validate(f, () -> false);
    }
}
