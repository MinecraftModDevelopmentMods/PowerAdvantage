package zone.moddev.mc.poweradvantage.resources;

import java.lang.reflect.Modifier;
import org.junit.Test;
import zone.moddev.mc.poweradvantage.init.ItemGroups;
import static org.junit.Assert.assertTrue;

public class CreativeTabInitializationTest {
    @Test public void addOnsCannotRaceTheSharedOneTimeTabInitialization() throws Exception {
        assertTrue("All three constructors share one initialization lock",
                Modifier.isSynchronized(ItemGroups.class.getMethod("init").getModifiers()));
    }
}
