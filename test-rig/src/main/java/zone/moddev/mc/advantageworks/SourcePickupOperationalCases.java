package zone.moddev.mc.advantageworks;

import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.state.properties.BlockStateProperties;

final class SourcePickupOperationalCases {
    static void register(List<MachineScenario> cases) {
        for (String machine : new String[]{"power-drain", "steam-pump", "electric-pump"}) {
            cases.add(new MachineScenario("pickup/" + machine,
                    "Native pickup removes exactly one bucket of water, preserving the waterlogged slab and retaining the collected fluid after restart", 400,
                    c -> {
                        if (machine.equals("power-drain")) {
                            c.block(8, 2, 8, "poweradvantage:fluid_drain");
                            c.block(9, 2, 8, "poweradvantage:fluid_pipe");
                            c.block(10, 2, 8, "poweradvantage:fluid_metal_tank");
                            for (int[] p : new int[][]{{7,3,8},{9,3,8},{8,3,7},{8,3,9},{8,4,8}})
                                c.block(p[0],p[1],p[2],"minecraft:bedrock");
                            slab(c, 8, 3, 8);
                        } else {
                            c.block(8, 4, 8, machine.equals("steam-pump") ? "steamadvantage:steam_pump" : "electricadvantage:electric_pump");
                            c.block(9, 4, 8, "poweradvantage:fluid_metal_tank");
                            if (machine.equals("steam-pump")) c.steamSupply(7, 4, 8, 600);
                            else c.electricSupply(7, 4, 8, 8000);
                            for (int[] p : new int[][]{{8,0,8},{8,0,7},{8,0,9},{7,0,8},{9,0,7},{9,0,9},{10,0,8},{9,1,8}})
                                c.block(p[0],p[1],p[2],"minecraft:bedrock");
                            slab(c, 9, 0, 8);
                        }
                    }, c -> {
                        boolean drain = machine.equals("power-drain");
                        net.minecraft.util.math.BlockPos source = drain ? c.at(8, 3, 8) : c.at(9, 0, 8);
                        c.require(c.world.getBlockState(source).is(Blocks.OAK_SLAB), "Water pickup must never delete the host slab");
                        c.require(!c.world.getBlockState(source).getValue(BlockStateProperties.WATERLOGGED), "Host is genuinely drained, not an unchanged water source");
                        int stored = drain ? amount(c, 8, 2, 8) + amount(c, 10, 2, 8)
                                : amount(c, 8, 4, 8) + amount(c, 9, 4, 8);
                        c.require(stored == 1000, "Exactly one source bucket is conserved in the real machine and receiver");
                        if (!drain) {
                            String type = machine.equals("steam-pump") ? "steam" : "electricity";
                            float remaining = c.energy(8, 4, 8, type) + c.energy(7, 4, 8, type);
                            c.require(remaining <= (type.equals("steam") ? 549 : 6572), "Lift construction and fluid pickup debit real finite energy");
                        }
                    }));
        }
    }

    private static int amount(MachineContext c, int x, int y, int z) {
        return ((cyano.poweradvantage.api.fluid.FluidTank)c.invoke(x, y, z, "getTank", new Class<?>[0])).getFluidAmount();
    }

    private static void slab(MachineContext c, int x, int y, int z) {
        c.block(x, y, z, "minecraft:oak_slab");
        c.world.setBlock(c.at(x,y,z), c.world.getBlockState(c.at(x,y,z)).setValue(BlockStateProperties.WATERLOGGED, true), 3);
    }
}
