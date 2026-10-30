package zone.moddev.mc.advantageworks;

import java.util.ArrayList;
import java.util.List;

/** Input-only rate oracles. No tile calls, energy reads, or machine tick simulation. */
final class ElectricOperationalRates {
    private ElectricOperationalRates() {}

    static int offset(int x, int y, int z) {
        return ((z & 1) << 2) | ((x & 1) << 1) | (y & 1);
    }

    static int firstUpdateDelay(long activationTick, int offset) {
        if (offset < 0 || offset > 7) throw new IllegalArgumentException("Invalid power phase");
        int phase = (int)Math.floorMod(activationTick, 8L);
        return 1 + Math.floorMod(-phase - offset - 1, 8);
    }

    static List<Integer> updateDelays(long activationTick, int offset, int ticks) {
        if (ticks < 0) throw new IllegalArgumentException("Negative wait");
        List<Integer> result = new ArrayList<>();
        for (int delay = firstUpdateDelay(activationTick, offset); delay <= ticks; delay += 8) result.add(delay);
        return result;
    }

    static float ledDebit(long activationTick, int sourceOffset, int ledOffset, int ticks, float reserve) {
        int firstSupply = firstUpdateDelay(activationTick, sourceOffset);
        int paidUpdates = 0;
        for (int delay : updateDelays(activationTick, ledOffset, ticks)) if (delay > firstSupply) paidUpdates++;
        return Math.min(reserve, paidUpdates * 0.125f);
    }

    static float preloadedSteamElectricity(float steam, int updates) {
        if (updates == 0) return 0;
        // This generator's decay is per power update, not per world tick.
        return (steam > 0.5f ? steam - 0.5f : steam) * 31.25f;
    }

    static float fabricatorDebit(long activationTick, int supplyOffset, int ticks, boolean sourceTicksFirst) {
        int firstSupply = firstUpdateDelay(activationTick, supplyOffset);
        // READY, CRAFTING, 200 work ticks, COMPLETE, commit, READY, then the next craft.
        // The two possibilities only cover order of adjacent tiles inside the first supply tick.
        int secondWork = Math.max(0, ticks - firstSupply - (sourceTicksFirst ? 204 : 205));
        return Math.min(4000, 3200 + secondWork * 16);
    }

    static float fullGeneratorSteam(float steam, int updates) {
        for (int i = 0; i < updates; i++) if (steam > 0.5f) steam -= 0.5f;
        return steam;
    }

    static SupplyLedger tankSupply(long activationTick, int tankOffset, int generatorOffset,
            int ticks, float reserve) {
        if (tankOffset == generatorOffset) throw new IllegalArgumentException("Adjacent source phases must differ");
        SupplyLedger result = new SupplyLedger();
        float remaining = reserve;
        int previousTransfer = 0;
        // The tank's initial cached redstone gate is true; its first update only opens the gate.
        for (int transfer = firstUpdateDelay(activationTick, tankOffset) + 8;
                transfer <= ticks && remaining > 0; transfer += 8) {
            float leakage = Math.min(remaining, (transfer - previousTransfer) * 0.0625f);
            result.tankLeakage += leakage;
            remaining -= leakage;
            previousTransfer = transfer;
            if (remaining == 0) break;
            float packet = Math.min(32, remaining);
            remaining -= packet;
            int conversion = transfer + firstUpdateDelay(activationTick + transfer, generatorOffset);
            if (conversion > ticks) throw new IllegalArgumentException("Wait ends before final conversion");
            result.transfers.add(transfer);
            result.conversions.add(conversion);
            result.generated += preloadedSteamElectricity(packet, 1);
            result.generatorLeakage += packet > 0.5f ? 0.5f : 0;
        }
        result.remaining = Math.max(0, remaining - (ticks - previousTransfer) * 0.0625f);
        return result;
    }

    static float daylightElectricity(long activationTick, int offset, int ticks,
            long setupDayTime, boolean daylightCycle, float weather) {
        float total = 0;
        float pi = (float)Math.PI;
        float packet = pi * 12500 / (24000 / pi / 8);
        for (int delay : updateDelays(activationTick, offset, ticks)) {
            long day = setupDayTime + (daylightCycle ? delay : 0);
            float angle = (float)day * 2.61799E-4f;
            total += Math.max(0, packet * (float)Math.sin(angle) * weather);
        }
        return total;
    }

    static final class SupplyLedger {
        final List<Integer> transfers = new ArrayList<>(), conversions = new ArrayList<>();
        float generated, tankLeakage, generatorLeakage, remaining;
    }
}
