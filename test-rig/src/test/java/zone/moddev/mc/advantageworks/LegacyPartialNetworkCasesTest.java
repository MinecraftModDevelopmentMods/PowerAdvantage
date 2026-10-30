package zone.moddev.mc.advantageworks;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyPartialNetworkCasesTest {
    @Test public void isolatedElectricityAndFluidRemainExactlyConstantForEveryAbsentTick() {
        for (String type : new String[]{"electric", "fluid"}) {
            LegacyPartialNetworkCases.Isolation witness = new LegacyPartialNetworkCases.Isolation(type);
            for (int tick = 241; tick <= 481; tick++) witness.observe(tick, 5000, true, true);
            witness.complete();
            assertEquals(240, witness.lastTick - witness.anchorTick);
            assertEquals(241, witness.samples.size());
        }
    }

    @Test public void isolatedSteamLosesOnlyOneSixteenthPerNativeSourceTick() {
        LegacyPartialNetworkCases.Isolation witness = new LegacyPartialNetworkCases.Isolation("steam");
        for (int tick = 241; tick <= 481; tick++) witness.observe(tick, 5000 - (tick - 241) * 0.0625, true, true);
        witness.complete();
        assertEquals(4985, witness.lastSource, 0);
    }

    @Test public void ghostTransferFailsDespiteConservedGlobalEnergyOrFluid() {
        for (String type : new String[]{"steam", "electric", "fluid"}) {
            LegacyPartialNetworkCases.Isolation witness = new LegacyPartialNetworkCases.Isolation(type);
            witness.observe(241, 5000, true, true);
            double expected = 5000 - (type.equals("steam") ? 0.0625 : 0);
            assertThrows(AssertionError.class, () -> witness.observe(242, expected - 4, true, true));
            assertEquals(2, witness.samples.size());
            assertEquals(expected - 4, witness.lastSource, 0);
        }
    }

    @Test public void isolationRejectsSourceGainAsWellAsGhostDrain() {
        LegacyPartialNetworkCases.Isolation witness = new LegacyPartialNetworkCases.Isolation("electric");
        witness.observe(241, 5000, true, true);
        assertThrows(AssertionError.class, () -> witness.observe(242, 5001, true, true));
    }

    @Test public void onlyTheFirstActualAbsentObservationMayAnchorAfterUnloadPhaseWork() {
        LegacyPartialNetworkCases.Isolation witness = new LegacyPartialNetworkCases.Isolation("steam");
        witness.observe(241, 4923, true, true);
        witness.observe(242, 4922.9375, true, true);
        assertThrows(AssertionError.class, () -> witness.observe(243, 4920, true, true));
    }

    @Test public void isolationRejectsMissingTicksDuplicatesAndShortEvidence() {
        LegacyPartialNetworkCases.Isolation witness = new LegacyPartialNetworkCases.Isolation("electric");
        witness.observe(241, 5000, true, true);
        assertThrows(AssertionError.class, () -> witness.observe(243, 5000, true, true));
        assertThrows(AssertionError.class, () -> witness.observe(241, 5000, true, true));
        for (int tick = 242; tick < 461; tick++) witness.observe(tick, 5000, true, true);
        assertThrows(AssertionError.class, witness::complete);
    }

    @Test public void isolationCannotUseFrozenOrNonfiniteSourceAmountsAsLiveProof() {
        LegacyPartialNetworkCases.Isolation witness = new LegacyPartialNetworkCases.Isolation("fluid");
        assertThrows(AssertionError.class, () -> witness.observe(241, 5000, false, true));
        assertThrows(AssertionError.class, () -> witness.observe(241, 5000, true, false));
        assertThrows(AssertionError.class, () -> witness.observe(241, Double.NaN, true, true));
        assertThrows(AssertionError.class, () -> witness.observe(241, -1, true, true));
    }

    private static LegacyPartialNetworkCases.Gate gate() {
        LegacyPartialNetworkCases.Gate gate = new LegacyPartialNetworkCases.Gate();
        gate.request(240); return gate;
    }

    @Test public void actualUnloadMustPrecede240ConsecutiveAbsentTicksAndLaterReload() {
        LegacyPartialNetworkCases.Gate gate = gate();
        gate.unloaded(241);
        for (int tick = 241; tick < 481; tick++) assertFalse(gate.observe(tick, false, false, true));
        assertTrue(gate.observe(481, false, false, true));
        gate.restore(481); gate.loaded(482);
        assertFalse(gate.observe(482, true, true, true));
        assertFalse(gate.recovered);
        gate.observe(483, true, true, true);
        gate.complete(); assertTrue(gate.recovered);
    }

    @Test public void unforceRequestAloneIsNeverUnloadProof() {
        LegacyPartialNetworkCases.Gate gate = gate();
        assertThrows(AssertionError.class, () -> gate.observe(320, true, true, true));
        assertThrows(AssertionError.class, gate::complete);
    }

    @Test public void missingNativeCallbackIsNotAcceptedAsAbsence() {
        LegacyPartialNetworkCases.Gate gate = gate();
        assertFalse(gate.observe(241, false, false, true));
        assertThrows(AssertionError.class, () -> gate.observe(320, false, false, true));
    }

    @Test public void collectionMembershipMustDisappearNotMerelyTheLoadedFlag() {
        LegacyPartialNetworkCases.Gate gate = gate();
        gate.unloaded(241);
        for (int tick = 241; tick < 320; tick++) assertFalse(gate.observe(tick, true, false, true));
        assertEquals(-1, gate.absentStart);
        assertThrows(AssertionError.class, () -> gate.observe(320, true, false, true));
    }

    @Test public void retainedChunkLossFailsEvenIfSelectedChunkReallyUnloaded() {
        LegacyPartialNetworkCases.Gate gate = gate();
        gate.unloaded(241);
        assertThrows(AssertionError.class, () -> gate.observe(241, false, false, false));
    }

    @Test public void automaticProductionReloadCannotBeRelabelledExplicitRecovery() {
        LegacyPartialNetworkCases.Gate gate = gate();
        gate.unloaded(241); gate.observe(241, false, false, true);
        gate.loaded(242);
        assertThrows(AssertionError.class, () -> gate.observe(242, true, true, true));
        assertThrows(AssertionError.class, gate::complete);
    }

    @Test public void transientAbsenceWithoutLoadEventAlsoFails() {
        LegacyPartialNetworkCases.Gate gate = gate();
        gate.unloaded(241); gate.observe(241, false, false, true);
        assertThrows(AssertionError.class, () -> gate.observe(242, true, true, true));
    }

    @Test public void prematureRestoreAndDuplicateEventsAreRejected() {
        LegacyPartialNetworkCases.Gate gate = gate();
        assertThrows(AssertionError.class, () -> gate.restore(241));
        gate.unloaded(241);
        assertThrows(AssertionError.class, () -> gate.unloaded(242));
        assertThrows(AssertionError.class, () -> gate.request(242));
    }

    @Test public void forcedButNotActuallyLoadedRecoveryTimesOut() {
        LegacyPartialNetworkCases.Gate gate = readyToRestore();
        gate.restore(481);
        assertFalse(gate.observe(560, false, false, true));
        assertThrows(AssertionError.class, () -> gate.observe(562, false, false, true));
    }

    @Test public void loadFlagWithoutActualLoadCallbackCannotPass() {
        LegacyPartialNetworkCases.Gate gate = readyToRestore();
        gate.restore(481);
        gate.observe(482, true, true, true);
        assertFalse(gate.recovered);
        assertThrows(AssertionError.class, gate::complete);
    }

    @Test public void nativeLoadWithoutReadyChunkCannotPass() {
        LegacyPartialNetworkCases.Gate gate = readyToRestore();
        gate.restore(481); gate.loaded(482);
        gate.observe(483, true, false, true);
        assertThrows(AssertionError.class, gate::complete);
    }

    @Test public void ownedChunkBoundsExcludeNeighbouringFixturesAndHalos() {
        BlockPos origin = new BlockPos(50000, 80, 50000);
        assertTrue(MachineContext.ownsBayChunk(origin, new ChunkPos(3125, 3125)));
        assertTrue(MachineContext.ownsBayChunk(origin, new ChunkPos(3126, 3126)));
        assertFalse(MachineContext.ownsBayChunk(origin, new ChunkPos(3124, 3125)));
        assertFalse(MachineContext.ownsBayChunk(origin, new ChunkPos(3127, 3126)));
    }

    @Test public void finiteFluidMayConvertToSteamButCannotAppearOrDisappear() {
        LegacyPartialNetworkCases.checkResources("fluid", state(1900, 3500, 0, 0, 0, 600));
        assertThrows(AssertionError.class, () -> LegacyPartialNetworkCases.checkResources("fluid", state(1901, 3500, 0, 0, 0, 600)));
        assertThrows(AssertionError.class, () -> LegacyPartialNetworkCases.checkResources("fluid", state(1899, 3500, 0, 0, 0, 600)));
        assertThrows(AssertionError.class, () -> LegacyPartialNetworkCases.checkResources("fluid", state(1900, 3500, 0, 0, 0, Double.NaN)));
    }

    @Test public void finiteItemsAndEnergyRejectLossDuplicationAndBadProgress() {
        LegacyPartialNetworkCases.checkResources("steam", state(5000, 40, 6, 2, 124, 0));
        LegacyPartialNetworkCases.checkResources("electric", state(5000, 400, 6, 2, 99, 0));
        assertThrows(AssertionError.class, () -> LegacyPartialNetworkCases.checkResources("steam", state(5000, 40, 7, 2, 124, 0)));
        assertThrows(AssertionError.class, () -> LegacyPartialNetworkCases.checkResources("steam", state(5000, 40, 5, 2, 124, 0)));
        assertThrows(AssertionError.class, () -> LegacyPartialNetworkCases.checkResources("steam", state(6001, 0, 8, 0, 0, 0)));
        assertThrows(AssertionError.class, () -> LegacyPartialNetworkCases.checkResources("steam", state(5000, 40, 6, 2, 125, 0)));
        assertThrows(AssertionError.class, () -> LegacyPartialNetworkCases.checkResources("electric", state(5000, 40, 6, 2, 100, 0)));
    }

    private static LegacyPartialNetworkCases.Gate readyToRestore() {
        LegacyPartialNetworkCases.Gate gate = gate();
        gate.unloaded(241);
        gate.observe(241, false, false, true);
        assertTrue(gate.observe(481, false, false, true));
        return gate;
    }

    private static LegacyPartialNetworkCases.State state(double source, double consumer, int in, int out, int progress, double converted) {
        return new LegacyPartialNetworkCases.State(source, consumer, in, out, progress, converted);
    }
}
