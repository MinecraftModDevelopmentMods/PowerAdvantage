package zone.moddev.mc.advantageworks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.*;

/** Registration is not runtime proof; these tests prevent silently missing case families. */
public class ElectricOperationalCasesTest {
    @Test public void allCaseFamiliesAreRegisteredWithFiniteWaits() {
        List<MachineScenario> cases = new ArrayList<>();
        ElectricOperationalCases.register(cases);
        Set<String> ids = new HashSet<>();
        for (MachineScenario scenario : cases) {
            assertTrue("Duplicate scenario " + scenario.id, ids.add(scenario.id));
            assertTrue(scenario.id, scenario.ticks >= 40 && scenario.ticks <= 1000);
            assertNotNull(scenario.setup);
            assertNotNull(scenario.verify);
            assertFalse(scenario.contract.trim().isEmpty());
        }
        assertEquals(25, cases.size());
        for (String family : new String[] {"furnace", "crusher", "oven", "parallel-inputs", "fabricator-recursive",
                "refinery", "still", "still-faced-input", "pump", "growth-chain", "growth-scarcity-full",
                "led-rate", "led-exhaustion", "hydro-rate", "solar-daylight-rate", "solar-blocklight-rate",
                "steam-generator-rate", "steam-generator-supply", "battery-priority", "battery-redstone",
                "turret-hit", "turret-no-power", "turret-scarce", "turret-occluded", "turret-owner-credit"})
            assertTrue("Missing operating family " + family, ids.contains("electric/" + family));
    }

    @Test public void globalDaylightChangeIsExplicitlyExclusive() {
        List<MachineScenario> cases = new ArrayList<>();
        ElectricOperationalCases.register(cases);
        int exclusive = 0;
        for (MachineScenario scenario : cases) {
            if (scenario.exclusive) {
                exclusive++;
                assertEquals("electric/solar-daylight-rate", scenario.id);
            }
        }
        assertEquals(1, exclusive);
    }
}
