package zone.moddev.mc.advantageworks;

import org.junit.Test;
import static org.junit.Assert.*;

public class SteamNetworkLifecycleLedgerTest {
    @Test public void clockRequiresEveryMeasuredTickExactlyOnce() {
        SteamNetworkLifecycleCases.Clock clock = new SteamNetworkLifecycleCases.Clock(1040);
        for (int tick = 1; tick <= 1040; tick++) clock.advance(tick);
        clock.complete();
        assertEquals(1040, clock.last);
    }

    @Test public void shorterRunCannotClaimThirtyMinutes() {
        SteamNetworkLifecycleCases.Clock clock = new SteamNetworkLifecycleCases.Clock(36000);
        for (int tick = 1; tick <= 12000; tick++) clock.advance(tick);
        assertThrows(AssertionError.class, clock::complete);
        for (int tick = 12001; tick <= 36000; tick++) clock.advance(tick);
        clock.complete();
        assertEquals(36000, clock.last);
    }

    @Test public void missingDuplicateAndExtraTicksAreRejected() {
        SteamNetworkLifecycleCases.Clock clock = new SteamNetworkLifecycleCases.Clock(2);
        assertThrows(AssertionError.class, () -> clock.advance(2));
        clock.advance(1);
        assertThrows(AssertionError.class, () -> clock.advance(1));
        assertThrows(AssertionError.class, clock::complete);
        clock.advance(2);
        assertThrows(AssertionError.class, () -> clock.advance(3));
    }

    @Test public void clockCannotAcceptUnboundedOrZeroDuration() {
        assertThrows(IllegalArgumentException.class, () -> new SteamNetworkLifecycleCases.Clock(0));
        assertThrows(IllegalArgumentException.class, () -> new SteamNetworkLifecycleCases.Clock(36001));
    }

    @Test public void acceleratedTickCountIsNotThirtyMinuteWallClockProof() {
        long seconds = java.util.concurrent.TimeUnit.SECONDS.toNanos(1799);
        assertFalse(SteamNetworkLifecycleCases.provesThirtyMinutes(36000, seconds - 1));
        assertFalse(SteamNetworkLifecycleCases.provesThirtyMinutes(35999, seconds));
        assertFalse(SteamNetworkLifecycleCases.provesThirtyMinutes(12000, seconds));
        assertTrue(SteamNetworkLifecycleCases.provesThirtyMinutes(36000, seconds));
        assertTrue(SteamNetworkLifecycleCases.provesThirtyMinutes(36000, seconds + 1000000000L));
    }

    @Test public void genuineTransferCancelsOutOfTheFiniteSteamLedger() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(6000, 0, 8, 0, 0), false);
        ledger.accept(frame(5949.9375, 49.9375, 8, 0, 0));
        assertEquals(0, ledger.paidTicks);
        assertEquals(0.125, ledger.idleLoss, 0);
        assertEquals(5999.875, ledger.previous.totalSteam(), 0);
    }

    @Test public void productiveTickPaysExactlyOnePointFivePlusIdleLoss() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(6000, 10, 8, 0, 0), false);
        ledger.accept(frame(5999.9375, 8.4375, 8, 0, 1));
        assertEquals(1, ledger.paidTicks);
        assertEquals(0.125, ledger.idleLoss, 0);
    }

    @Test public void completed125TickBatchRetainsItsExactMaterialAndLastWorkDebit() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(6000, 10, 8, 0, 124), false);
        ledger.accept(frame(5999.9375, 8.4375, 7, 1, 0));
        assertEquals(1, ledger.previous.output);
        assertEquals(1, ledger.paidTicks);
    }

    @Test public void scarcityLosesProgressWithoutChargingItAsProductiveWork() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(6000, 0, 8, 0, 12), false);
        ledger.accept(frame(5999.9375, 0, 8, 0, 11));
        assertEquals(1, ledger.lostProgress);
        assertEquals(0, ledger.paidTicks);
        assertEquals(0.0625, ledger.idleLoss, 0);
    }

    @Test public void missingProductiveDebitCannotHideInsideIdleLoss() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(6000, 10, 8, 0, 0), false);
        assertThrows(AssertionError.class, () -> ledger.accept(frame(5999.9375, 9.9375, 8, 0, 1)));
    }

    @Test public void sourceCreationAndUnexplainedSteamLossAreRejected() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(6000, 0, 8, 0, 0), false);
        assertThrows(AssertionError.class, () -> ledger.accept(frame(6001, 0, 8, 0, 0)));
        assertThrows(AssertionError.class, () -> ledger.accept(frame(5999, 0, 8, 0, 0)));
    }

    @Test public void fractionalIdleLossCannotMasqueradeAsNativeLeakage() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(6000, 0, 8, 0, 0), false);
        assertThrows(AssertionError.class, () -> ledger.accept(frame(5999.99, 0, 8, 0, 0)));
    }

    @Test public void stockDuplicationAndLossAreRejected() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(6000, 0, 8, 0, 0), false);
        assertThrows(AssertionError.class, () -> ledger.accept(frame(6000, 0, 8, 1, 0)));
        assertThrows(AssertionError.class, () -> ledger.accept(frame(6000, 0, 7, 0, 0)));
    }

    @Test public void completedProductsCannotDisappearIntoAnotherInventory() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(6000, 0, 7, 1, 0), false);
        assertThrows(AssertionError.class, () -> ledger.accept(frame(6000, 0, 8, 0, 0)));
    }

    @Test public void skippedProductiveTicksAndCompletedBatchesAreRejected() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(6000, 10, 8, 0, 0), false);
        assertThrows(AssertionError.class, () -> ledger.accept(frame(5996.875, 10, 8, 0, 2)));
        assertThrows(AssertionError.class, () -> ledger.accept(frame(5996.875, 10, 6, 2, 0)));
    }

    @Test public void sourceCountCannotChangeAfterSetup() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(6000, 0, 8, 0, 0), false);
        assertThrows(AssertionError.class, () -> ledger.accept(new SteamNetworkLifecycleCases.Frame(
                new double[]{3000, 3000}, 0, 8, 0, 0)));
    }

    @Test public void invalidNativeBuffersAndProgressAreRejected() {
        for (double value : new double[]{-1, 10001, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(AssertionError.class, () -> ledger(frame(value, 0, 8, 0, 0), false));
        for (double value : new double[]{-1, 51, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(AssertionError.class, () -> ledger(frame(6000, value, 8, 0, 0), false));
        assertThrows(AssertionError.class, () -> ledger(frame(6000, 0, 8, 0, -1), false));
        assertThrows(AssertionError.class, () -> ledger(frame(6000, 0, 8, 0, 125), false));
    }

    @Test public void enduranceCannotPassWithOnlyAnEarlyProductThenAnIdleMachine() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(10000, 50, 8, 0, 0), true);
        for (int tick = 1; tick <= 32; tick++) ledger.accept(frame(10000 - tick * 1.625, 50, 8, 0, tick));
        double source = ledger.previous.sourceSteam[0];
        assertThrows(AssertionError.class, () -> ledger.accept(frame(source - 0.125, 50, 8, 0, 32)));
    }

    @Test public void boundedWarmupStillMeasuresAllItsTicksAndResources() {
        SteamNetworkLifecycleCases.FlowLedger ledger = ledger(frame(6000, 0, 8, 0, 0), true);
        for (int tick = 1; tick <= 32; tick++) ledger.accept(frame(6000 - tick * 0.0625, 0, 8, 0, 0));
        assertEquals(32, ledger.frames);
        assertEquals(2, ledger.idleLoss, 0);
        assertThrows(AssertionError.class, () -> ledger.accept(frame(5997.9375, 0, 8, 0, 0)));
    }

    @Test public void frameDoesNotAllowTheCallerToRewriteEarlierEvidence() {
        double[] source = {6000};
        SteamNetworkLifecycleCases.Frame frame = new SteamNetworkLifecycleCases.Frame(source, 0, 8, 0, 0);
        source[0] = 10000;
        assertEquals(6000, frame.totalSteam(), 0);
    }

    private static SteamNetworkLifecycleCases.Frame frame(double source, double buffer, int input, int products, int progress) {
        return new SteamNetworkLifecycleCases.Frame(new double[]{source}, buffer, input, products, progress);
    }

    private static SteamNetworkLifecycleCases.FlowLedger ledger(SteamNetworkLifecycleCases.Frame initial, boolean continuous) {
        return new SteamNetworkLifecycleCases.FlowLedger(8, initial, continuous);
    }
}
