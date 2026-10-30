package zone.moddev.mc.advantageworks;

import java.util.function.Consumer;

/** A finite fixture, actual world ticks, then an independently stated outcome. */
final class MachineScenario {
    final String id, contract;
    final int ticks;
    final Consumer<MachineContext> setup, verify;
    Consumer<MachineContext> timed, cleanup;
    boolean exclusive;

    MachineScenario(String id, String contract, int ticks, Consumer<MachineContext> setup,
                    Consumer<MachineContext> verify) {
        int tickLimit = "steam/lifecycle/network-endurance-30min".equals(id) ? 36000 : 12000;
        if (!id.matches("[a-z0-9][a-z0-9_/-]+") || contract.trim().isEmpty() || ticks < 1 || ticks > tickLimit) {
            throw new IllegalArgumentException("Invalid operating scenario " + id);
        }
        this.id = id;
        this.contract = contract;
        this.ticks = ticks;
        this.setup = setup;
        this.verify = verify;
    }

    MachineScenario exclusive() { exclusive = true; return this; }

    MachineScenario timed(Consumer<MachineContext> hook) {
        if (timed != null) throw new IllegalStateException("Timed callback already assigned");
        timed = java.util.Objects.requireNonNull(hook, "timed");
        return this;
    }

    MachineScenario cleanup(Consumer<MachineContext> hook) {
        if (cleanup != null) throw new IllegalStateException("Cleanup callback already assigned");
        cleanup = java.util.Objects.requireNonNull(hook, "cleanup");
        return this;
    }
}
