package zone.moddev.mc.advantageworks;

import java.util.List;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;

final class PowerOperationalCases {
    static void register(List<MachineScenario> cases) {
        stills(cases);
        for (boolean sourceLocked : new boolean[]{true, false}) cases.add(new MachineScenario(
                "power/conveyor/locked-" + (sourceLocked ? "source" : "destination"),
                "Automation must not bypass a vanilla chest lock, at either end of the belt", 160,
                c -> {
                    conveyor(c, "poweradvantage:item_conveyor"); c.put(4, 2, 5, 0, "minecraft:dirt", 16);
                    net.minecraft.nbt.CompoundNBT lock = new net.minecraft.nbt.CompoundNBT(); lock.putString("Lock", "Works key");
                    c.seed(sourceLocked ? 4 : 6, 2, 5, lock);
                }, c -> {
                    c.require(c.count(6, 2, 5, "minecraft:dirt") == 0, "locked route delivers no stock");
                    c.require(c.count(4, 2, 5, "minecraft:dirt") + c.count(5, 2, 5, "minecraft:dirt") == 16,
                            "locked route retains every input item");
                }));
        for (Direction direction : Direction.values()) {
            cases.add(new MachineScenario("power/conveyor/" + direction.getName(),
                    "A finite 16 dirt reaches the chest in the declared direction, without loss or extra items", 200,
                    c -> {
                        BlockPos offset = BlockPos.ZERO.offset(direction);
                        c.block(8, 6, 8, "poweradvantage:item_conveyor"); c.facing(8, 6, 8, direction.getName());
                        c.block(8 - offset.getX(), 6 - offset.getY(), 8 - offset.getZ(), "minecraft:chest");
                        c.block(8 + offset.getX(), 6 + offset.getY(), 8 + offset.getZ(), "minecraft:chest");
                        c.put(8 - offset.getX(), 6 - offset.getY(), 8 - offset.getZ(), 0, "minecraft:dirt", 16);
                    }, c -> {
                        BlockPos offset = BlockPos.ZERO.offset(direction);
                        c.require(c.count(8 + offset.getX(), 6 + offset.getY(), 8 + offset.getZ(), "minecraft:dirt") == 16, "all dirt delivered");
                        c.require(c.count(8 - offset.getX(), 6 - offset.getY(), 8 - offset.getZ(), "minecraft:dirt") == 0
                                && c.count(8, 6, 8, "minecraft:dirt") == 0, "source and conveyor exhausted");
                    }));
        }
        cases.add(new MachineScenario("power/conveyor/blocked", "Full output retains every dirt item in source or belt", 200,
                c -> { conveyor(c, "poweradvantage:item_conveyor"); c.put(4, 2, 5, 0, "minecraft:dirt", 16); full(c, 6, 2, 5); },
                c -> {
                    c.require(c.count(4, 2, 5, "minecraft:dirt") + c.count(5, 2, 5, "minecraft:dirt") == 16, "blocked conveyor retains all inputs");
                    c.require(c.count(6, 2, 5, "minecraft:cobblestone") == 1728, "full output contents unchanged");
                }));
        cases.add(new MachineScenario("power/conveyor/item-entity", "Dropped dirt is collected and delivered without a duplicate entity", 200,
                c -> {
                    c.block(5, 2, 5, "poweradvantage:item_conveyor"); c.facing(5, 2, 5, "east");
                    c.block(6, 2, 5, "minecraft:chest"); c.block(4, 1, 5, "minecraft:stone");
                    c.block(3, 2, 5, "minecraft:stone"); c.block(4, 2, 4, "minecraft:stone"); c.block(4, 2, 6, "minecraft:stone");
                    BlockPos p = c.at(4, 2, 5);
                    ItemEntity dropped = new ItemEntity(c.world, p.getX() + .5, p.getY() + .01, p.getZ() + .5, new ItemStack(Items.DIRT, 16));
                    dropped.setMotion(0, 0, 0);
                    c.world.addEntity(dropped);
                }, c -> {
                    c.require(c.count(6, 2, 5, "minecraft:dirt") == 16, "dropped input delivered");
                    c.require(c.world.getEntitiesWithinAABB(ItemEntity.class, new AxisAlignedBB(c.at(3, 0, 4), c.at(8, 5, 7))).isEmpty(), "no duplicate/lost item entity remains");
                }));
        cases.add(new MachineScenario("power/conveyor/nbt-preserved", "Distinct custom item data must not merge into or overwrite another stack", 200,
                c -> {
                    conveyor(c, "poweradvantage:item_conveyor");
                    c.put(4, 2, 5, 0, "minecraft:dirt", 3); c.put(4, 2, 5, 1, "minecraft:dirt", 3);
                    c.inventory(4, 2, 5).getStackInSlot(0).getOrCreateTag().putString("Witness", "A");
                    c.inventory(4, 2, 5).getStackInSlot(1).getOrCreateTag().putString("Witness", "B");
                    c.put(6, 2, 5, 0, "minecraft:dirt", 1);
                    c.inventory(6, 2, 5).getStackInSlot(0).getOrCreateTag().putString("Witness", "A");
                }, c -> {
                    int a = 0, b = 0, unknown = 0;
                    for (int slot = 0; slot < c.inventory(6, 2, 5).getSizeInventory(); slot++) {
                        ItemStack stack = c.inventory(6, 2, 5).getStackInSlot(slot);
                        if (stack.isEmpty()) continue;
                        String witness = stack.hasTag() ? stack.getTag().getString("Witness") : "";
                        if (witness.equals("A")) a += stack.getCount();
                        else if (witness.equals("B")) b += stack.getCount();
                        else unknown += stack.getCount();
                    }
                    c.record("customA", a); c.record("customB", b); c.record("unknownTags", unknown);
                    c.require(a == 4 && b == 3 && unknown == 0, "exact custom-NBT stack identities and quantities retained");
                    c.require(c.count(4, 2, 5, "minecraft:dirt") + c.count(5, 2, 5, "minecraft:dirt") == 0, "all custom stock delivered");
                }));
        String[][] filters = {
                {"block", "minecraft:dirt", "minecraft:apple"}, {"food", "minecraft:apple", "minecraft:dirt"},
                {"fuel", "minecraft:coal", "minecraft:dirt"}, {"ore", "minecraft:iron_ingot", "minecraft:dirt"},
                {"plant", "minecraft:wheat_seeds", "minecraft:dirt"}, {"smelt", "minecraft:iron_ore", "minecraft:dirt"}
        };
        for (String[] filter : filters) cases.add(new MachineScenario("power/filter/" + filter[0],
                "Eight matching items go down; eight nonmatching items go forward; bounded inputs exhaust", 300,
                c -> { filter(c, "poweradvantage:item_filter_" + filter[0]); c.put(4, 2, 5, 0, filter[1], 8); c.put(4, 2, 5, 1, filter[2], 8); },
                c -> filterOutcome(c, filter[1], filter[2], true)));
        for (int ghosts = 0; ghosts <= 3; ghosts++) {
            final int slots = ghosts;
            cases.add(new MachineScenario("power/filter/inventory-" + slots,
                    "Zero references pass all; one or several references sort matching dirt, retaining reference slots", 300,
                    c -> {
                        filter(c, "poweradvantage:item_filter_inventory");
                        c.put(4, 2, 5, 0, "minecraft:dirt", 8); c.put(4, 2, 5, 1, "minecraft:apple", 8);
                        if (slots > 0) c.put(5, 2, 5, 1, "minecraft:dirt", 1);
                        if (slots > 1) { c.put(5, 2, 5, 2, "minecraft:cobblestone", 1); c.put(5, 2, 5, 3, "minecraft:coal", 1); }
                    }, c -> {
                        filterOutcome(c, "minecraft:dirt", "minecraft:apple", slots > 0);
                        c.require(c.inventory(5, 2, 5).getStackInSlot(1).getCount() == (slots > 0 ? 1 : 0), "reference dirt retained separately from throughput");
                    }));
        }
        for (int flags = 0; flags < 4; flags++) {
            final boolean lowerFull = (flags & 1) != 0, forwardFull = (flags & 2) != 0;
            cases.add(new MachineScenario("power/filter/overflow-" + flags,
                    "Use lower output first, overflow forward when lower is full, retain input when both are full", 300,
                    c -> {
                        filter(c, "poweradvantage:item_filter_overflow"); c.put(4, 2, 5, 0, "minecraft:dirt", 16);
                        if (lowerFull) full(c, 5, 1, 5); if (forwardFull) full(c, 6, 2, 5);
                    }, c -> {
                        int lower = c.count(5, 1, 5, "minecraft:dirt"), forward = c.count(6, 2, 5, "minecraft:dirt");
                        c.require(lower == (lowerFull ? 0 : 16), "overflow lower routing");
                        c.require(forward == (lowerFull && !forwardFull ? 16 : 0), "overflow forward routing");
                        c.require(c.count(4, 2, 5, "minecraft:dirt") + c.count(5, 2, 5, "minecraft:dirt") + lower + forward == 16, "overflow conservation even with both outputs blocked");
                    }));
        }
        for (String fluid : new String[]{"minecraft:water", "poweradvantage:crude_oil", "poweradvantage:refined_oil"}) {
            cases.add(new MachineScenario("power/drain/" + fluid.replace(':', '_'),
                    "Exactly one native fluid source travels through drain and pipe to storage with registry identity intact", 80,
                    c -> { drain(c, fluid, "poweradvantage:fluid_storage_tank", false); },
                    c -> {
                        c.require(c.world.isAirBlock(c.at(5, 2, 5)), "native source collected");
                        c.require(c.fluidAmount(7, 1, 5) == 1000 && c.fluidAmount(5, 1, 5) == 0, "exact source volume delivered");
                        c.require(c.nbt(7, 1, 5).getCompound("Tank").getString("FluidName").equals(fluid), "fluid registry identity retained");
                    }));
        }
        cases.add(new MachineScenario("power/drain/non-source", "Flowing water without a source cannot create a bucket", 80,
                c -> {
                    drain(c, "minecraft:water", "poweradvantage:fluid_storage_tank", false);
                    c.world.setBlockState(c.at(5, 2, 5), net.minecraft.block.Blocks.WATER.getDefaultState()
                            .with(net.minecraft.block.FlowingFluidBlock.LEVEL, 7), 3);
                }, c -> c.require(c.fluidAmount(5, 1, 5) + c.fluidAmount(7, 1, 5) == 0, "no source means no collected millibuckets")));
        for (boolean matching : new boolean[]{true, false}) cases.add(new MachineScenario("power/tank/filter-" + matching,
                "A bucket reference admits matching water or rejects it without losing the source volume", 80,
                c -> { drain(c, "minecraft:water", "poweradvantage:fluid_metal_tank", false); c.put(7, 1, 5, 0, matching ? "minecraft:water_bucket" : "minecraft:lava_bucket", 1); },
                c -> {
                    c.require(c.fluidAmount(7, 1, 5) == (matching ? 1000 : 0), "bucket filter controls actual network acceptance");
                    c.require(c.fluidAmount(7, 1, 5) + c.fluidAmount(5, 1, 5) == 1000, "rejected water is retained rather than deleted");
                }));
        cases.add(new MachineScenario("power/tank/full", "Full metal storage never overfills or deletes the extra source bucket", 80,
                c -> { drain(c, "minecraft:water", "poweradvantage:fluid_metal_tank", false); c.tank(7, 1, 5, "minecraft:water", 10000); },
                c -> c.require(c.fluidAmount(7, 1, 5) == 10000 && c.fluidAmount(5, 1, 5) == 1000, "full output applies backpressure and conserves 11000 mB")));
        for (boolean signal : new boolean[]{true, false}) cases.add(new MachineScenario("power/fluid-switch/" + signal,
                "Redstone isolates the fluid path; removing it permits transfer without replacing machinery", 100,
                c -> {
                    drain(c, "minecraft:water", "poweradvantage:fluid_storage_tank", true);
                    if (signal) c.block(6, 2, 5, "minecraft:redstone_block");
                }, c -> {
                    c.require(c.fluidAmount(7, 1, 5) == (signal ? 0 : 1000), "redstone controls fluid delivery");
                    c.require(c.fluidAmount(7, 1, 5) + c.fluidAmount(5, 1, 5) == 1000, "switch conserves fluid");
                }));
        for (boolean blocked : new boolean[]{true, false}) cases.add(new MachineScenario("power/discharge/" + blocked,
                "A discharge places one bucket into air, but must never replace solid bedrock", 80,
                c -> {
                    c.block(5, 2, 5, "poweradvantage:fluid_discharge");
                    for (Direction direction : Direction.values()) if (direction != Direction.UP) {
                        BlockPos p = BlockPos.ZERO.offset(direction);
                        c.block(5 + p.getX(), 1 + p.getY(), 5 + p.getZ(), "minecraft:bedrock");
                    }
                    if (blocked) c.block(5, 1, 5, "minecraft:bedrock");
                    c.tank(5, 2, 5, "minecraft:water", 1000);
                }, c -> {
                    c.require(c.fluidAmount(5, 2, 5) == (blocked ? 1000 : 0), "discharge respects available placement space");
                    c.require(blocked ? c.world.getBlockState(c.at(5, 1, 5)).getBlock() == net.minecraft.block.Blocks.BEDROCK
                            : c.world.getFluidState(c.at(5, 1, 5)).isSource(), "solid host preserved or actual source placed");
                }));
    }

    private static void stills(List<MachineScenario> cases) {
        for (String mode : new String[]{"production", "no-fuel", "invalid", "full", "one-space", "redstone"}) {
            final int output = mode.equals("full") ? 1000 : mode.equals("one-space") ? 999 : 0;
            final int product = mode.equals("production") ? 500 : mode.equals("one-space") ? 1 : 0;
            cases.add(new MachineScenario("power/still/" + mode,
                    "Finite crude oil produces refined oil at 2:1; fuel, valid input, capacity and redstone govern useful work", 620,
                    c -> {
                        c.block(8, 4, 8, "poweradvantage:still");
                        zone.moddev.mc.poweradvantage.machines.fluidmachines.StillTileEntity still =
                                (zone.moddev.mc.poweradvantage.machines.fluidmachines.StillTileEntity)c.tile(8, 4, 8);
                        still.getInputTank().setFluid(new cyano.poweradvantage.api.fluid.FluidStack(
                                mode.equals("invalid") ? net.minecraft.fluid.Fluids.WATER
                                        : net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(new net.minecraft.util.ResourceLocation("poweradvantage:crude_oil")), 1000));
                        if (!mode.equals("no-fuel")) c.put(8, 4, 8, 0, "minecraft:coal", 1);
                        if (output > 0) c.tank(8, 4, 8, "poweradvantage:refined_oil", output);
                        if (mode.equals("redstone")) c.block(9, 4, 8, "minecraft:redstone_block");
                    }, c -> {
                        zone.moddev.mc.poweradvantage.machines.fluidmachines.StillTileEntity still =
                                (zone.moddev.mc.poweradvantage.machines.fluidmachines.StillTileEntity)c.tile(8, 4, 8);
                        c.require(still.getInputTank().getFluidAmount() == 1000 - product * 2, "exact raw input debit");
                        c.require(c.fluidAmount(8, 4, 8) == output + product, "exact bounded distillation product");
                        if (product == 0 && !mode.equals("no-fuel"))
                            c.require(c.count(8, 4, 8, "minecraft:coal") == 1, "blocked job does not consume fresh fuel");
                    }));
        }
    }

    private static void conveyor(MachineContext c, String filter) {
        c.block(4, 2, 5, "minecraft:chest"); c.block(5, 2, 5, filter); c.facing(5, 2, 5, "east"); c.block(6, 2, 5, "minecraft:chest");
    }
    private static void filter(MachineContext c, String filter) { conveyor(c, filter); c.block(5, 1, 5, "minecraft:chest"); }
    private static void full(MachineContext c, int x, int y, int z) {
        for (int slot = 0; slot < c.inventory(x, y, z).getSizeInventory(); slot++) c.put(x, y, z, slot, "minecraft:cobblestone", 64);
    }
    private static void filterOutcome(MachineContext c, String matching, String other, boolean sorted) {
        c.require(c.count(5, 1, 5, matching) == (sorted ? 8 : 0), "matching category routed down");
        c.require(c.count(6, 2, 5, other) == 8 && c.count(6, 2, 5, matching) == (sorted ? 0 : 8), "nonmatches routed forward");
        c.require(c.count(4, 2, 5, matching) + c.count(4, 2, 5, other) == 0 && c.inventory(5, 2, 5).getStackInSlot(0).isEmpty(), "finite source and filter buffer exhausted");
    }
    private static void drain(MachineContext c, String fluid, String tank, boolean switched) {
        c.block(5, 1, 5, "poweradvantage:fluid_drain");
        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            BlockPos p = BlockPos.ZERO.offset(d); c.block(5 + p.getX(), 2, 5 + p.getZ(), "minecraft:bedrock");
        }
        c.block(5, 2, 5, fluid);
        c.block(6, 1, 5, switched ? "poweradvantage:fluid_switch" : "poweradvantage:fluid_pipe");
        c.block(7, 1, 5, tank);
    }
}
