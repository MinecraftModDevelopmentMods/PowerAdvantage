package zone.moddev.mc.advantageworks;

import java.util.function.Consumer;

/** A finite fixture, actual world ticks, then an independently stated outcome. */
final class MachineScenario {
    final String id, contract;
    final int ticks;
    final Consumer<MachineContext> setup, verify;
    boolean exclusive;

    MachineScenario(String id, String contract, int ticks, Consumer<MachineContext> setup,
                    Consumer<MachineContext> verify) {
        if (!id.matches("[a-z0-9][a-z0-9_/-]+") || contract.trim().isEmpty() || ticks < 1 || ticks > 12000) {
            throw new IllegalArgumentException("Invalid operating scenario " + id);
        }
        this.id = id;
        this.contract = contract;
        this.ticks = ticks;
        this.setup = setup;
        this.verify = verify;
    }

    MachineScenario exclusive() { exclusive = true; return this; }
}
