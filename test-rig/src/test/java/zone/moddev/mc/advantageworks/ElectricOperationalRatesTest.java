package zone.moddev.mc.advantageworks;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ElectricOperationalRatesTest {
    @Test public void everySetupPhaseAndPositionOffsetMatchesTheDeclaredEightTickSchedule() {
        for (int start = 0; start < 8; start++) for (int offset = 0; offset < 8; offset++) {
            int first = ElectricOperationalRates.firstUpdateDelay(start, offset);
            assertTrue(first >= 1 && first <= 8);
            assertEquals(0, (start + first + offset) % 8);
            for (int earlier = 1; earlier < first; earlier++) assertNotEquals(0, (start + earlier + offset) % 8);
            List<Integer> updates = ElectricOperationalRates.updateDelays(start, offset, 40);
            assertEquals(5, updates.size());
            for (int index = 0; index < updates.size(); index++) assertEquals(first + 8 * index, (int)updates.get(index));
        }
    }

    @Test public void absolutePositionParityAndLongWorldEpochArePreserved() {
        assertEquals(0, ElectricOperationalRates.offset(50008, 82, 50008));
        assertEquals(2, ElectricOperationalRates.offset(50007, 82, 50008));
        assertEquals(7, ElectricOperationalRates.offset(-1, -1, -1));
        for (int phase = 0; phase < 8; phase++) for (int offset = 0; offset < 8; offset++)
            assertEquals(ElectricOperationalRates.updateDelays(phase, offset, 100),
                    ElectricOperationalRates.updateDelays((1L << 40) + phase, offset, 100));
    }

    @Test public void noUpdateIsInventedBeforeTheFirstEligibleWorldTick() {
        assertTrue(ElectricOperationalRates.updateDelays(0, 0, 7).isEmpty());
        assertEquals(Arrays.asList(8), ElectricOperationalRates.updateDelays(0, 0, 8));
        assertEquals(Arrays.asList(1, 9), ElectricOperationalRates.updateDelays(7, 0, 9));
        assertEquals(0, ElectricOperationalRates.preloadedSteamElectricity(32, 0), 0);
    }

    @Test public void directlyPreloadedGeneratorLosesOneHalfUnitPerUpdateNotPerWaitingTick() {
        for (int start = 0; start < 8; start++) {
            int count = ElectricOperationalRates.updateDelays(start, 0, 40).size();
            assertEquals(984.375f, ElectricOperationalRates.preloadedSteamElectricity(32, count), 0);
            assertEquals(29.5f, ElectricOperationalRates.fullGeneratorSteam(32, count), 0);
        }
    }

    @Test public void smallGeneratorPacketsAreNotRoundedAwayOrChargedExcessDecay() {
        assertEquals(0, ElectricOperationalRates.preloadedSteamElectricity(0, 5), 0);
        assertEquals(7.8125f, ElectricOperationalRates.preloadedSteamElectricity(0.25f, 5), 0);
        assertEquals(15.625f, ElectricOperationalRates.preloadedSteamElectricity(0.5f, 5), 0);
        assertEquals(3.90625f, ElectricOperationalRates.preloadedSteamElectricity(0.625f, 5), 0);
    }

    @Test public void suppliedSteamUsesTheActualFirstTransferPhaseForAllEightStartTimes() {
        float[] expected = {2894.53125f, 2896.484375f, 2898.4375f, 2900.390625f,
                2902.34375f, 2904.296875f, 2890.625f, 2892.578125f};
        for (int start = 0; start < 8; start++) {
            ElectricOperationalRates.SupplyLedger ledger = ElectricOperationalRates.tankSupply(start, 2, 0, 100, 96);
            assertEquals(expected[start], ledger.generated, 0);
            assertEquals(3, ledger.transfers.size());
            assertEquals(1.5f, ledger.generatorLeakage, 0);
            assertEquals(96, ledger.generated / 31.25f + ledger.tankLeakage + ledger.generatorLeakage, 0);
            assertEquals(0, ledger.remaining, 0);
        }
        ElectricOperationalRates.SupplyLedger zero = ElectricOperationalRates.tankSupply(0, 2, 0, 100, 96);
        assertEquals(Arrays.asList(14, 22, 30), zero.transfers);
        assertEquals(Arrays.asList(16, 24, 32), zero.conversions);
    }

    @Test public void scarceTankSteamMayExpireBeforeTransferDependingOnSetupPhase() {
        assertEquals(3.90625f, ElectricOperationalRates.tankSupply(0, 2, 0, 100, 1).generated, 0);
        assertEquals(0, ElectricOperationalRates.tankSupply(6, 2, 0, 100, 1).generated, 0);
    }

    @Test public void ledFirstSupplyPhaseDeterminesWhetherNineOrTenUpdatesArePaid() {
        float[] expected = {1.25f, 1.25f, 1.25f, 1.25f, 1.25f, 1.25f, 1.125f, 1.125f};
        for (int start = 0; start < 8; start++)
            assertEquals(expected[start], ElectricOperationalRates.ledDebit(start, 2, 0, 80, 10), 0);
        assertEquals(0.5f, ElectricOperationalRates.ledDebit(0, 0, 4, 80, 0.5f), 0);
    }

    @Test public void fabricatorAccountsForSurplusDrivenSecondWorkWithoutChangingProductCount() {
        assertEquals(3952, ElectricOperationalRates.fabricatorDebit(0, 0, 260, false), 0);
        assertEquals(3968, ElectricOperationalRates.fabricatorDebit(0, 0, 260, true), 0);
        assertEquals(4000, ElectricOperationalRates.fabricatorDebit(7, 0, 260, false), 0);
        for (int phase = 0; phase < 8; phase++) {
            float early = ElectricOperationalRates.fabricatorDebit(phase, 0, 260, true);
            float late = ElectricOperationalRates.fabricatorDebit(phase, 0, 260, false);
            assertTrue(early >= late && early - late <= 16);
            assertTrue(late >= 3200 && early <= 4000);
        }
    }

    @Test public void daylightOracleUsesScheduledDayTimesRatherThanPeakPowerTimesUpdateCount() {
        float fixed = ElectricOperationalRates.daylightElectricity(0, 0, 160, 6000, false, 1);
        float advancing = ElectricOperationalRates.daylightElectricity(0, 0, 160, 6000, true, 1);
        assertTrue(advancing < fixed);
        assertEquals(0, ElectricOperationalRates.daylightElectricity(0, 0, 160, 18000, false, 1), 0);
        assertEquals(advancing * 0.35f,
                ElectricOperationalRates.daylightElectricity(0, 0, 160, 6000, true, 0.35f), 0.001f);
    }
}
