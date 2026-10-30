package com.mcmoddev.poweradvantage.conduitnetwork;

import cyano.poweradvantage.api.*;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class PowerTransferAccountingTest {
    private static final ConduitType TYPE = new ConduitType("electricity");

    @Test public void partialAcceptanceLeavesEnergyForTheNextConsumer() {
        Sink first = new Sink(10), second = new Sink(100);
        assertEquals(80, send(80, first, second), 0);
        assertEquals(10, first.stored, 0);
        assertEquals(70, second.stored, 0);
    }

    @Test public void rejectedOfferIsNotChargedToTheSource() {
        assertEquals(0, send(80, new Sink(0)), 0);
    }

    @Test public void onlyActuallyStoredEnergyIsChargedToTheSource() {
        Sink first = new Sink(10), second = new Sink(5);
        assertEquals(15, send(80, first, second), 0);
    }

    private static float send(float available, Sink... sinks) {
        return ConduitRegistry.distributePower(available, TYPE, PowerRequest.LOW_PRIORITY, null, null,
                Arrays.stream(sinks).map(s -> new PowerRequest(PowerRequest.MEDIUM_PRIORITY, 100, s))
                        .collect(java.util.stream.Collectors.toList()));
    }

    private static final class Sink implements IPowerMachine {
        final float capacity;
        float stored;
        Sink(float capacity) { this.capacity = capacity; }
        @Override public float getEnergyCapacity(ConduitType type) { return capacity; }
        @Override public float getEnergy(ConduitType type) { return stored; }
        @Override public void setEnergy(float energy, ConduitType type) { stored = energy; }
        @Override public float addEnergy(float energy, ConduitType type) {
            float accepted = Math.min(energy, capacity - stored);
            stored += accepted;
            return accepted;
        }
        @Override public float subtractEnergy(float energy, ConduitType type) { return 0; }
        @Override public PowerRequest getPowerRequest(ConduitType type) { return PowerRequest.REQUEST_NOTHING; }
        @Override public boolean isPowerSink(ConduitType type) { return true; }
        @Override public boolean isPowerSource(ConduitType type) { return false; }
        @Override public boolean canAcceptConnection(PowerConnectorContext context) { return true; }
        @Override public ConduitType[] getTypes() { return new ConduitType[]{TYPE}; }
    }
}
