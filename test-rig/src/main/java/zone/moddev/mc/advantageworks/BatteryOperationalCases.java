package zone.moddev.mc.advantageworks;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import java.util.List;
import net.minecraft.state.properties.BlockStateProperties;

final class BatteryOperationalCases {
    static void register(List<MachineScenario> cases) {
        String[] chemistries = {"lead_acid", "nickel_hydride", "alkaline", "lithium"};
        int[] capacities = {1000, 2000, 4000, 8000};
        for (int index = 0; index < chemistries.length; index++) {
            final String chemistry = chemistries[index];
            final int capacity = capacities[index];
            cases.add(new MachineScenario("electric/battery-chemistry-" + chemistry,
                    "Each battery chemistry supplies a real LED from finite item NBT, retaining its rated capacity and exact energy debit", 160,
                    c -> {
                        c.block(7, 4, 8, "electricadvantage:electric_battery_array");
                        c.put(7, 4, 8, 0, "electricadvantage:" + chemistry + "_battery", 1);
                        c.inventory(7, 4, 8).getStackInSlot(0).getOrCreateTag().putFloat("energy", 10);
                        c.block(8, 4, 8, "electricadvantage:led_bar");
                    }, c -> {
                        PoweredEntity array = (PoweredEntity)c.tile(7, 4, 8);
                        c.require(array.getEnergyCapacity(new ConduitType("electricity")) == capacity, "rated battery capacity retained");
                        c.require(c.world.getBlockState(c.at(8, 4, 8)).get(BlockStateProperties.LIT), "battery powers the live LED");
                        net.minecraft.util.math.BlockPos source = c.at(7, 4, 8), sink = c.at(8, 4, 8);
                        float debit = ElectricOperationalRates.ledDebit(c.activationTick,
                                ElectricOperationalRates.offset(source.getX(), source.getY(), source.getZ()),
                                ElectricOperationalRates.offset(sink.getX(), sink.getY(), sink.getZ()), 160, 10);
                        float remaining = c.energy(7, 4, 8, "electricity") + c.energy(8, 4, 8, "electricity");
                        c.require(debit > 0 && Math.abs(10 - remaining - debit) < 0.001f, "exact finite charge is conserved apart from rated lighting cost");
                        c.require(c.inventory(7, 4, 8).getStackInSlot(0).getCount() == 1, "battery item is retained, not consumed");
                    }));
        }
    }
}
