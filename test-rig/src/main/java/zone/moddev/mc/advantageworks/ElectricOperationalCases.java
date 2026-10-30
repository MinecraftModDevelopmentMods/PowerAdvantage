package zone.moddev.mc.advantageworks;

import java.util.List;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.monster.HuskEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.common.util.FakePlayer;

/** Independent operating contracts. Only setup seeds inputs; all work uses world ticks. */
public final class ElectricOperationalCases {
    private static final String EA = "electricadvantage:";
    private static final String ELECTRICITY = "electricity";
    private static final String GREENHOUSE = "greenhouse";
    private static final int Y = 2;
    private static final int Z = 8;
    private static final int[] CELLS = {3, 9, 15, 21, 27};

    private ElectricOperationalCases() {}

    public static void register(List<MachineScenario> cases) {
        processor(cases, "furnace", "electric_furnace", "minecraft:iron_ore",
                "minecraft:iron_ingot", 4, 6, 1, 1600, 440);
        processor(cases, "crusher", "electric_crusher", "minecraft:cobblestone",
                "minecraft:gravel", 3, 6, 1, 2400, 440);
        processor(cases, "oven", "electric_oven", "minecraft:potato",
                "minecraft:baked_potato", 1, 1, 1, 400, 240);
        parallelInputs(cases);
        fabricator(cases);
        refinery(cases);
        still(cases);
        pump(cases);
        growth(cases);
        led(cases);
        hydro(cases);
        solar(cases);
        steamGeneration(cases);
        batteries(cases);
        turret(cases);
    }

    private static void parallelInputs(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/parallel-inputs",
                "All four furnace lanes and all three crusher lanes finish independently from finite stock "
                + "and debit exactly 6400 and 7200 electricity without shared slot-progress or output loss.", 260, c -> {
                    c.block(8, Y, Z, EA + "electric_furnace");
                    c.electricSupply(7, Y, Z, 7800);
                    for (int slot = 0; slot < 4; slot++) c.put(8, Y, Z, slot, "minecraft:iron_ore", 1);
                    c.block(22, Y, Z, EA + "electric_crusher");
                    c.electricSupply(21, Y, Z, 7800);
                    for (int slot = 0; slot < 3; slot++) c.put(22, Y, Z, slot, "minecraft:cobblestone", 1);
                }, c -> {
                    equal(c, "parallel.furnaceProducts", output(c, 8, 4, 6, "minecraft:iron_ingot"), 4);
                    equal(c, "parallel.furnaceInputs", inputCount(c, 8, 4, "minecraft:iron_ore"), 0);
                    equal(c, "parallel.crusherProducts", output(c, 22, 3, 6, "minecraft:gravel"), 3);
                    equal(c, "parallel.crusherInputs", inputCount(c, 22, 3, "minecraft:cobblestone"), 0);
                    close(c, "parallel.furnaceDebit", 7800 - pairEnergy(c, 8), 6400, 0.01f);
                    close(c, "parallel.crusherDebit", 7800 - pairEnergy(c, 22), 7200, 0.01f);
                }));
    }

    private static void processor(List<MachineScenario> cases, String name, String block,
            String input, String output, int inputs, int outputs, int yield, int cost, int ticks) {
        final float reserve = cost * 2 + 800;
        cases.add(new MachineScenario("electric/" + name,
                "Two finite inputs produce exact recipe output and energy debit; no power, invalid input, "
                + "full output and insufficient total energy cannot create products or destroy inputs.", ticks,
                c -> {
                    for (int x : CELLS) c.block(x, Y, Z, EA + block);
                    for (int i = 0; i < CELLS.length; i++) {
                        int x = CELLS[i];
                        c.put(x, Y, Z, 0, i == 2 ? "minecraft:bedrock" : input, 2);
                        if (i != 1) c.electricSupply(x - 1, Y, Z, i == 4 ? cost / 2f : reserve);
                    }
                    for (int slot = inputs; slot < inputs + outputs; slot++)
                        c.put(CELLS[3], Y, Z, slot, output, 64);
                    c.record("energyPerRecipe", cost);
                    c.record("positiveFiniteReserve", reserve);
                }, c -> {
                    int x = CELLS[0];
                    equal(c, "positive.output", output(c, x, inputs, outputs, output), 2 * yield);
                    equal(c, "positive.input", slotCount(c, x, 0, input), 0);
                    close(c, "positive.energyDebit", reserve - pairEnergy(c, x), 2 * cost, 0.01f);
                    equal(c, "unpowered.output", output(c, CELLS[1], inputs, outputs, output), 0);
                    equal(c, "unpowered.input", slotCount(c, CELLS[1], 0, input), 2);
                    equal(c, "invalid.input", slotCount(c, CELLS[2], 0, "minecraft:bedrock"), 2);
                    equal(c, "invalid.output", output(c, CELLS[2], inputs, outputs, output), 0);
                    close(c, "invalid.energyConserved", pairEnergy(c, CELLS[2]), reserve, 0.01f);
                    equal(c, "full.input", slotCount(c, CELLS[3], 0, input), 2);
                    equal(c, "full.output", output(c, CELLS[3], inputs, outputs, output), outputs * 64);
                    close(c, "full.energyConserved", pairEnergy(c, CELLS[3]), reserve, 0.01f);
                    equal(c, "scarce.input", slotCount(c, CELLS[4], 0, input), 2);
                    equal(c, "scarce.output", output(c, CELLS[4], inputs, outputs, output), 0);
                    float left = pairEnergy(c, CELLS[4]);
                    c.record("scarce.energyRemaining", left);
                    c.require(left >= 0 && left < cost / 2f, "Scarcity spends finite energy without completing a recipe");
                }));
    }

    private static void fabricator(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/fabricator-recursive",
                "The actual fabricator recursively crafts four sticks from one log, retains two surplus planks "
                + "and the reference item. Its first craft costs 3200 electricity; the measured finite debit also "
                + "accounts for attempted second-craft work. Cyclic missing ingredients, no power, "
                + "full outputs and scarce energy must not fabricate or consume raw materials.", 260, c -> {
                    for (int x : CELLS) {
                        c.block(x, Y, Z, EA + "electric_fabricator");
                        c.put(x, Y, Z, 0, "minecraft:oak_log", 1);
                        c.put(x, Y, Z, 14, "minecraft:stick", 1);
                    }
                    for (int i = 0; i < CELLS.length; i++)
                        if (i != 1) c.electricSupply(CELLS[i] - 1, Y, Z, i == 4 ? 1600 : 4000);
                    c.put(CELLS[2], Y, Z, 0, "minecraft:bedrock", 1);
                    c.put(CELLS[2], Y, Z, 14, "minecraft:gold_ingot", 1);
                    c.put(CELLS[3], Y, Z, 12, "minecraft:stick", 64);
                    c.put(CELLS[3], Y, Z, 13, "minecraft:stick", 64);
                    c.record("fabricator.constructionGameTime", c.world.getGameTime());
                }, c -> {
                    equal(c, "recursive.sticks", output(c, CELLS[0], 12, 2, "minecraft:stick"), 4);
                    equal(c, "recursive.log", slotCount(c, CELLS[0], 0, "minecraft:oak_log"), 0);
                    equal(c, "recursive.surplusPlanks", inputCount(c, CELLS[0], 12, "minecraft:oak_planks"), 2);
                    equal(c, "recursive.reference", slotCount(c, CELLS[0], 14, "minecraft:stick"), 1);
                    recordSchedule(c, "fabricatorSupply", c.activationTick, CELLS[0] - 1, Y, Z, 260);
                    requireWindow(c, c.activationTick, 260);
                    float earlyDebit = ElectricOperationalRates.fabricatorDebit(c.activationTick, phase(c, CELLS[0] - 1, Y, Z), 260, true);
                    float lateDebit = ElectricOperationalRates.fabricatorDebit(c.activationTick, phase(c, CELLS[0] - 1, Y, Z), 260, false);
                    float debit = 4000 - pairEnergy(c, CELLS[0]);
                    c.record("recursive.expectedDebitSourceFirst", earlyDebit);
                    c.record("recursive.expectedDebitMachineFirst", lateDebit);
                    c.record("recursive.energyDebit", debit);
                    c.require(debit == earlyDebit || debit == lateDebit, "Fabricator debit equals completed craft plus phase-bounded second-craft work");
                    for (int i : new int[] {1, 3, 4}) {
                        equal(c, "negative" + i + ".rawLog", slotCount(c, CELLS[i], 0, "minecraft:oak_log"), 1);
                        equal(c, "negative" + i + ".output", output(c, CELLS[i], 12, 2, "minecraft:stick"), i == 3 ? 128 : 0);
                        equal(c, "negative" + i + ".reference", slotCount(c, CELLS[i], 14, "minecraft:stick"), 1);
                    }
                    equal(c, "boundedCycle.input", slotCount(c, CELLS[2], 0, "minecraft:bedrock"), 1);
                    equal(c, "boundedCycle.output", output(c, CELLS[2], 12, 2, "minecraft:gold_ingot"), 0);
                    close(c, "boundedCycle.energy", pairEnergy(c, CELLS[2]), 4000, 0.01f);
                    close(c, "full.energy", pairEnergy(c, CELLS[3]), 4000, 0.01f);
                    c.require(pairEnergy(c, CELLS[4]) < 1600, "Scarce fabricator attempts work but cannot complete it");
                }));
    }

    private static void refinery(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/refinery",
                "200 mB refined oil becomes exactly two petrolplastic ingots for 2400 electricity. "
                + "No electricity, invalid water, full output and 99 mB scarcity do not produce plastic.", 240, c -> {
                    for (int i = 0; i < CELLS.length; i++) {
                        int x = CELLS[i];
                        c.block(x, Y, Z, EA + "plastic_refinery");
                        if (i != 1) c.electricSupply(x - 1, Y, Z, 4000);
                        if (i != 2) reserve(c, x, Y, Z, "getTank", "poweradvantage:refined_oil", i == 4 ? 99 : 200);
                    }
                    rejectFluid(c, CELLS[2], "minecraft:water", 200);
                    c.put(CELLS[3], Y, Z, 0, EA + "petrolplastic_ingot", 64);
                }, c -> {
                    equal(c, "positive.plastic", c.count(CELLS[0], Y, Z, EA + "petrolplastic_ingot"), 2);
                    equal(c, "positive.oil", fluidAmount(c, CELLS[0], Y, Z), 0);
                    close(c, "positive.energyDebit", 4000 - pairEnergy(c, CELLS[0]), 2400, 0.01f);
                    for (int i = 1; i < CELLS.length; i++) {
                        equal(c, "negative" + i + ".plastic", c.count(CELLS[i], Y, Z, EA + "petrolplastic_ingot"), i == 3 ? 64 : 0);
                        equal(c, "negative" + i + ".fluid", fluidAmount(c, CELLS[i], Y, Z), i == 2 ? 0 : i == 4 ? 99 : 200);
                        if (i != 1) close(c, "negative" + i + ".energy", pairEnergy(c, CELLS[i]), 4000, 0.01f);
                    }
                }));
    }

    private static void still(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/still",
                "40 mB crude oil distills to 20 mB refined oil for 320 electricity, using the real tanks. "
                + "No power, invalid water, full output and a one-mB incomplete recipe preserve inputs and energy.", 80, c -> {
                    for (int i = 0; i < CELLS.length; i++) {
                        int x = CELLS[i];
                        c.block(x, Y, Z, EA + "electric_still");
                        if (i != 1) c.electricSupply(x - 1, Y, Z, 1000);
                        if (i != 2) reserve(c, x, Y, Z, "getInputTank", "poweradvantage:crude_oil", i == 4 ? 1 : 40);
                    }
                    rejectFluid(c, CELLS[2], "minecraft:water", 40);
                    reserve(c, CELLS[3], Y, Z, "getOutputTank", "poweradvantage:refined_oil", 1000);
                    equal(c, "setup.fullOutput", tankAmount(c, CELLS[3], 1), 1000);
                }, c -> {
                    equal(c, "positive.input", tankAmount(c, CELLS[0], 0), 0);
                    equal(c, "positive.output", tankAmount(c, CELLS[0], 1), 20);
                    fluidIs(c, CELLS[0], 1, "poweradvantage:refined_oil");
                    close(c, "positive.energyDebit", 1000 - pairEnergy(c, CELLS[0]), 320, 0.01f);
                    for (int i = 1; i < CELLS.length; i++) {
                        equal(c, "negative" + i + ".input", tankAmount(c, CELLS[i], 0), i == 2 ? 0 : i == 4 ? 1 : 40);
                        equal(c, "negative" + i + ".output", tankAmount(c, CELLS[i], 1), i == 3 ? 1000 : 0);
                        if (i != 1) close(c, "negative" + i + ".energy", pairEnergy(c, CELLS[i]), 1000, 0.01f);
                    }
                }));
        cases.add(new MachineScenario("electric/still-faced-input",
                "Each real Forge fluid face simulates and accepts exactly eight mB crude oil without simulation "
                + "mutation. The 48 finite mB then operate on natural ticks, making 24 mB refined oil for 384 electricity.", 80, c -> {
                    c.block(8, Y, Z, EA + "electric_still");
                    c.electricSupply(7, Y, Z, 1000);
                    net.minecraft.fluid.Fluid oil = ForgeRegistries.FLUIDS.getValue(new ResourceLocation("poweradvantage:crude_oil"));
                    c.require(oil != null, "Crude oil exists for faced automation");
                    int stored = 0;
                    for (Direction face : Direction.values()) {
                        IFluidHandler handler = fluids(c, 8, face);
                        equal(c, "face." + face + ".simulated", handler.fill(new FluidStack(oil, 8), IFluidHandler.FluidAction.SIMULATE), 8);
                        equal(c, "face." + face + ".unchanged", tankAmount(c, 8, 0), stored);
                        equal(c, "face." + face + ".accepted", handler.fill(new FluidStack(oil, 8), IFluidHandler.FluidAction.EXECUTE), 8);
                        stored += 8;
                    }
                    c.record("setup.facedInputReserve", stored);
                }, c -> {
                    equal(c, "faced.input", tankAmount(c, 8, 0), 0);
                    equal(c, "faced.output", tankAmount(c, 8, 1), 24);
                    fluidIs(c, 8, 1, "poweradvantage:refined_oil");
                    close(c, "faced.energyDebit", 1000 - pairEnergy(c, 8), 384, 0.01f);
                    for (Direction face : Direction.values()) {
                        FluidStack simulated = fluids(c, 8, face).drain(24, IFluidHandler.FluidAction.SIMULATE);
                        equal(c, "face." + face + ".drainable", simulated.getAmount(), 24);
                        c.require(new ResourceLocation("poweradvantage:refined_oil").equals(simulated.getFluid().getRegistryName()), "Face offers the real refined product " + face);
                        equal(c, "face." + face + ".drainSimulationUnchanged", tankAmount(c, 8, 1), 24);
                    }
                }));
    }

    private static void pump(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/pump",
                "A finite enclosed source is removed, delivered as 1000 mB water, and charged 1000 + "
                + "three vertical levels at 32 electricity. No power, no source, full tank and 1095 energy "
                + "cannot remove the source or create water.", 100, c -> {
                    for (int i = 0; i < CELLS.length; i++) {
                        int x = CELLS[i];
                        c.block(x, 5, Z, EA + "electric_pump");
                        c.block(x, 4, Z, EA + "pump_pipe_electric");
                        c.block(x, 3, Z, EA + "pump_pipe_electric");
                        c.block(x, 2, Z, "minecraft:stone");
                        for (int dx = -1; dx <= 1; dx++) {
                            c.block(x + dx, 1, Z - 1, "minecraft:stone");
                            c.block(x + dx, 1, Z + 1, "minecraft:stone");
                            c.block(x + dx, 3, Z - 1, "minecraft:stone");
                            c.block(x + dx, 3, Z + 1, "minecraft:stone");
                        }
                        c.block(x, 1, Z, "minecraft:stone");
                        c.block(x - 1, 2, Z, "minecraft:stone");
                        c.block(x + 1, 2, Z, "minecraft:stone");
                        c.block(x, 2, Z - 1, i == 2 ? "minecraft:stone" : "minecraft:water");
                        c.block(x - 1, 2, Z - 1, "minecraft:stone");
                        c.block(x + 1, 2, Z - 1, "minecraft:stone");
                        c.block(x, 2, Z - 2, "minecraft:stone");
                        if (i != 1) c.electricSupply(x - 1, 5, Z, i == 4 ? 1095 : 3000);
                        if (i == 3) reserve(c, x, 5, Z, "getTank", "minecraft:water", 1000);
                    }
                }, c -> {
                    equal(c, "positive.water", fluidAmount(c, CELLS[0], 5, Z), 1000);
                    c.require(c.world.getFluidState(c.at(CELLS[0], 2, Z - 1)).isEmpty(), "Pump removed the real source block");
                    close(c, "positive.energyDebit", 3000 - energyPair(c, CELLS[0], 5, Z), 1096, 0.01f);
                    for (int i = 1; i < CELLS.length; i++) {
                        equal(c, "negative" + i + ".water", fluidAmount(c, CELLS[i], 5, Z), i == 3 ? 1000 : 0);
                        if (i != 2) c.require(c.world.getFluidState(c.at(CELLS[i], 2, Z - 1)).isSource(), "Negative pump preserves source " + i);
                        if (i != 1) close(c, "negative" + i + ".energy", energyPair(c, CELLS[i], 5, Z), i == 4 ? 1095 : 3000, 0.01f);
                    }
                }));
    }

    private static void growth(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/growth-chain",
                "Controller converts finite dirt, water and electricity into greenhouse power and the chamber "
                + "turns one cane into two real products. Missing electricity, water or dirt and an invalid seed "
                + "cannot grow products; all resource conversions are conserved.", 900, c -> {
                    for (int i = 0; i < CELLS.length; i++) {
                        int x = CELLS[i];
                        c.block(x, Y, Z, EA + "growth_chamber_controller");
                        c.block(x + 1, Y, Z, EA + "growth_chamber");
                        c.put(x + 1, Y, Z, 0, i == 4 ? "minecraft:bedrock" : "minecraft:sugar_cane", 1);
                        if (i != 1) c.electricSupply(x - 1, Y, Z, 7200);
                        if (i != 2) reserve(c, x, Y, Z, "getTank", "minecraft:water", 1800);
                        if (i != 3) c.put(x, Y, Z, 0, "minecraft:dirt", 1);
                    }
                }, c -> {
                    equal(c, "positive.caneOutput", output(c, CELLS[0] + 1, 3, 6, "minecraft:sugar_cane"), 2);
                    equal(c, "positive.seed", slotCount(c, CELLS[0] + 1, 0, "minecraft:sugar_cane"), 0);
                    for (int i = 0; i < CELLS.length; i++) {
                        int x = CELLS[i];
                        float soil = ((Number)c.invoke(x, Y, Z, "getSoil", new Class<?>[0])).floatValue();
                        float converted = (i == 3 ? 0 : 1000 - soil);
                        float electricLeft = c.energy(x, Y, Z, ELECTRICITY) + (i == 1 ? 0 : c.energy(x - 1, Y, Z, ELECTRICITY));
                        close(c, "cell" + i + ".electricDebit", (i == 1 ? 0 : 7200) - electricLeft, 8 * converted, 0.1f);
                        // Each 12.5-unit controller batch uses exactly 25 mB with this fixture.
                        close(c, "cell" + i + ".waterDebit", (i == 2 ? 0 : 1800) - fluidAmount(c, x, Y, Z), 2 * converted, 0.1f);
                        float growthStored = c.energy(x, Y, Z, GREENHOUSE) + c.energy(x + 1, Y, Z, GREENHOUSE);
                        c.record("cell" + i + ".soilRemaining", soil);
                        c.record("cell" + i + ".growthStored", growthStored);
                        c.require(soil >= 0 && converted >= 0 && growthStored >= 0, "Growth resources stay nonnegative " + i);
                        if (i == 0) close(c, "positive.growthConsumed", converted - growthStored, 800, 0.1f);
                        if (i > 0) {
                            equal(c, "negative" + i + ".seed", slotCount(c, x + 1, 0, i == 4 ? "minecraft:bedrock" : "minecraft:sugar_cane"), 1);
                            equal(c, "negative" + i + ".output", output(c, x + 1, 3, 6, "minecraft:sugar_cane"), 0);
                            close(c, "negative" + i + ".growthConsumption", converted - growthStored, 0, 0.1f);
                        }
                    }
                }));
        cases.add(new MachineScenario("electric/growth-scarcity-full",
                "799 finite greenhouse units cannot finish an 800-tick cane crop; full outputs retain the seed "
                + "and all existing products without spawning overflow items.", 900, c -> {
                    growthCell(c, 8, 6392, false);
                    growthCell(c, 22, 7200, true);
                }, c -> {
                    equal(c, "scarce.seed", slotCount(c, 9, 0, "minecraft:sugar_cane"), 1);
                    equal(c, "scarce.output", output(c, 9, 3, 6, "minecraft:sugar_cane"), 0);
                    equal(c, "full.seed", slotCount(c, 23, 0, "minecraft:sugar_cane"), 1);
                    equal(c, "full.output", output(c, 23, 3, 6, "minecraft:sugar_cane"), 384);
                    AxisAlignedBB bay = new AxisAlignedBB(c.at(0, 0, 0), c.at(31, 31, 31));
                    equal(c, "overflowItems", c.world.getEntitiesWithinAABB(ItemEntity.class, bay).size(), 0);
                    float soil = ((Number)c.invoke(8, Y, Z, "getSoil", new Class<?>[0])).floatValue();
                    close(c, "scarce.convertedGrowth", 1000 - soil, 799, 0.1f);
                    close(c, "scarce.electricRemaining", pairEnergy(c, 8), 0, 0.1f);
                }));
    }

    private static void growthCell(MachineContext c, int x, float reserve, boolean full) {
        c.block(x, Y, Z, EA + "growth_chamber_controller");
        c.block(x + 1, Y, Z, EA + "growth_chamber");
        c.electricSupply(x - 1, Y, Z, reserve);
        c.put(x, Y, Z, 0, "minecraft:dirt", 1);
        reserve(c, x, Y, Z, "getTank", "minecraft:water", 1800);
        c.put(x + 1, Y, Z, 0, "minecraft:sugar_cane", 1);
        if (full) for (int slot = 3; slot < 9; slot++) c.put(x + 1, Y, Z, slot, "minecraft:sugar_cane", 64);
    }

    private static void led(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/led-rate",
                "LED consumes 0.125 electricity per eight world ticks, lights with a finite battery, "
                + "and stays dark with no electricity.", 80, c -> {
                    c.block(8, Y, Z, EA + "led_bar");
                    c.electricSupply(7, Y, Z, 10);
                    c.block(22, Y, Z, EA + "led_bar");
                    c.record("led.constructionGameTime", c.world.getGameTime());
                }, c -> {
                    c.require(c.world.getBlockState(c.at(8, Y, Z)).get(BlockStateProperties.LIT), "Powered LED is lit");
                    c.require(!c.world.getBlockState(c.at(22, Y, Z)).get(BlockStateProperties.LIT), "Unpowered LED is dark");
                    float debit = 10 - pairEnergy(c, 8);
                    recordSchedule(c, "led", c.activationTick, 8, Y, Z, 80);
                    recordSchedule(c, "battery", c.activationTick, 7, Y, Z, 80);
                    requireWindow(c, c.activationTick, 80);
                    float expectedDebit = ElectricOperationalRates.ledDebit(c.activationTick,
                            phase(c, 7, Y, Z), phase(c, 8, Y, Z), 80, 10);
                    c.record("led.expectedDebit", expectedDebit);
                    close(c, "led.debit", debit, expectedDebit, 0.001f);
                }));
        cases.add(new MachineScenario("electric/led-exhaustion",
                "A half-unit finite reserve is exhausted and cannot leave the LED lit indefinitely.", 80, c -> {
                    c.block(8, Y, Z, EA + "led_bar");
                    c.electricSupply(7, Y, Z, 0.5f);
                }, c -> {
                    close(c, "exhausted.energy", pairEnergy(c, 8), 0, 0.001f);
                    c.require(!c.world.getBlockState(c.at(8, Y, Z)).get(BlockStateProperties.LIT), "Exhausted LED turns off");
                }));
    }

    private static void hydro(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/hydro-rate",
                "A naturally spawned turbine in a contained horizontal water channel generates four electricity "
                + "per natural entity tick. A dry generator produces zero; a real empty battery receives the output.", 160, c -> {
                    // A finite source sustains a contained seven-block channel, not a scripted energy source.
                    for (int x = 4; x <= 13; x++) {
                        c.block(x, 0, Z, "minecraft:stone");
                        c.block(x, 1, Z - 1, "minecraft:stone");
                        c.block(x, 1, Z + 1, "minecraft:stone");
                    }
                    c.block(4, 1, Z, "minecraft:stone");
                    c.block(13, 1, Z, "minecraft:stone");
                    c.block(5, 1, Z, "minecraft:water");
                    c.block(8, Y, Z, EA + "hydroelectric_generator");
                    c.electricSupply(8, Y + 1, Z, 0);
                    for (int x = 20; x <= 24; x++) for (int z = Z - 2; z <= Z + 2; z++) {
                        c.block(x, 0, z, "minecraft:stone");
                        c.block(x, 1, z, x == 20 || x == 24 || z == Z - 2 || z == Z + 2 ? "minecraft:stone" : "minecraft:water");
                    }
                    c.block(22, Y, Z, EA + "hydroelectric_generator");
                    c.block(27, Y, 22, EA + "hydroelectric_generator");
                }, c -> {
                    c.require(!c.world.getFluidState(c.at(8, 1, Z)).isEmpty(), "Water reached the turbine");
                    net.minecraft.util.math.Vec3d flow = c.world.getFluidState(c.at(8, 1, Z)).getFlow(c.world, c.at(8, 1, Z));
                    c.require(flow.x * flow.x + flow.z * flow.z > 0,
                            "Turbine sees actual horizontal flow");
                    float generated = c.energy(8, Y, Z, ELECTRICITY) + c.energy(8, Y + 1, Z, ELECTRICITY);
                    // Source propagation is five ticks per block; 25 ticks is a bounded startup allowance.
                    between(c, "hydro.generated", generated, 4 * 135, 4 * 160);
                    close(c, "hydro.fourUnitQuantum", generated / 4, Math.round(generated / 4), 0.001f);
                    c.require(c.energy(8, Y + 1, Z, ELECTRICITY) > 0, "Hydro charges a real battery through its network");
                    c.require(c.world.getFluidState(c.at(22, 1, Z)).isSource(), "Still-water control remains a real source");
                    close(c, "stillWater.generated", c.energy(22, Y, Z, ELECTRICITY), 0, 0.001f);
                    close(c, "dry.generated", c.energy(27, Y, 22, ELECTRICITY), 0, 0.001f);
                    int turbines = 0;
                    for (Entity actor : c.world.getEntitiesWithinAABB(Entity.class,
                            new AxisAlignedBB(c.at(0, 0, 0), c.at(31, 31, 31)))) {
                        if (!new ResourceLocation(EA + "hydroturbine").equals(actor.getType().getRegistryName())) continue;
                        turbines++;
                        c.record("hydro.motion/" + actor.getUniqueID(), actor.getMotion().toString());
                        c.require(actor.getMotion().lengthSquared() == 0,
                                "Anchored turbine has no linear motion: " + actor.getUniqueID());
                    }
                    equal(c, "hydro.anchoredActors", turbines, 3);
                }));
        final long[] previousTime = new long[1];
        final boolean[] daylightCycle = new boolean[1];
        final float[] weatherFactor = new float[1];
        cases.add(new MachineScenario("electric/solar-daylight-rate",
                "An unroofed daylight panel generates at the retained sine-based rate every eight world ticks. "
                + "This exclusive case restores global time after measurement, including on assertion failure.", 160, c -> {
                    c.block(8, Y, Z, EA + "photovoltaic_generator");
                    c.require(c.world.canSeeSky(c.at(8, Y + 1, Z)), "Daylight panel top surface has actual sky access");
                    previousTime[0] = c.world.getDayTime();
                    weatherFactor[0] = c.world.isThundering() ? 0.2f : c.world.isRaining() ? 0.35f : 1;
                    c.record("setup.previousDayTime", previousTime[0]);
                    c.record("setup.weatherFactor", weatherFactor[0]);
                    c.world.setDayTime(6000);
                    daylightCycle[0] = c.world.getGameRules().getBoolean(GameRules.DO_DAYLIGHT_CYCLE);
                    c.record("setup.daylightCycle", daylightCycle[0]);
                    c.record("daylight.constructionGameTime", c.world.getGameTime());
                }, c -> {
                    try {
                        float nowWeather = c.world.isThundering() ? 0.2f : c.world.isRaining() ? 0.35f : 1;
                        close(c, "daylight.weatherUnchanged", nowWeather, weatherFactor[0], 0);
                        recordSchedule(c, "daylight", c.activationTick, 8, Y, Z, 160);
                        requireWindow(c, c.activationTick, 160);
                        float expected = ElectricOperationalRates.daylightElectricity(c.activationTick,
                                phase(c, 8, Y, Z), 160, 6000, daylightCycle[0], weatherFactor[0]);
                        c.record("daylight.expectedGenerated", expected);
                        // Math.sin is independent of Minecraft's quantized sine table; allow only its rounding error.
                        close(c, "daylight.generated", c.energy(8, Y, Z, ELECTRICITY), expected, 0.01f);
                        c.record("daylight.lastDayTime", c.world.getDayTime());
                    } finally {
                        c.world.setDayTime(previousTime[0]);
                    }
                }).exclusive());
    }

    private static void solar(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/solar-blocklight-rate",
                "A fully roofed panel uses the documented 0.1 blocklight efficiency and eight-tick generation "
                + "cadence; equally roofed dark and redstone-inhibited lit panels produce no energy, including "
                + "the first power update. No global time/weather mutation.", 160, c -> {
                    solarRoom(c, 8, true);
                    solarRoom(c, 15, true);
                    c.block(14, Y, Z, "minecraft:redstone_block");
                    solarRoom(c, 22, false);
                }, c -> {
                    c.require(!c.world.canSeeSky(c.at(8, Y + 1, Z)), "Lit panel is indoors");
                    c.require(!c.world.canSeeSky(c.at(22, Y + 1, Z)), "Dark panel is indoors");
                    int brightness = c.world.getLight(c.at(8, Y + 1, Z));
                    equal(c, "lit.localBrightness", brightness, 14);
                    equal(c, "dark.localBrightness", c.world.getLight(c.at(22, Y + 1, Z)), 0);
                    float perUpdate = (float)(Math.PI * 12500 / (24000 / Math.PI / 8)) * 0.1f * 14 * 0.0666666f;
                    between(c, "solar.generated", c.energy(8, Y, Z, ELECTRICITY), perUpdate * 18, perUpdate * 20 + 0.01f);
                    close(c, "dark.generated", c.energy(22, Y, Z, ELECTRICITY), 0, 0.001f);
                    c.require(c.world.isBlockPowered(c.at(15, Y, Z)), "Inhibited panel has a real redstone signal");
                    equal(c, "inhibited.localBrightness", c.world.getLight(c.at(15, Y + 1, Z)), 14);
                    close(c, "inhibited.generated", c.energy(15, Y, Z, ELECTRICITY), 0, 0.001f);
                    c.record("solar.expectedEnergyPerEightTicks", perUpdate);
                }));
    }

    private static void solarRoom(MachineContext c, int x, boolean lit) {
        for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy <= 4; dy++) for (int dz = -2; dz <= 2; dz++)
            if (Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 0 || dy == 4)
                c.block(x + dx, dy, Z + dz, "minecraft:stone");
        c.block(x, Y, Z, EA + "photovoltaic_generator");
        if (lit) c.block(x + 1, Y + 1, Z, "minecraft:glowstone");
    }

    private static void steamGeneration(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/steam-generator-rate",
                "A finite 32-unit steam packet converts on a natural power update after 0.5 steam decay "
                + "into 984.375 electricity; an empty generator produces none and full electrical output "
                + "never creates extra energy.", 40, c -> {
                    for (int x : new int[] {8, 16, 24}) c.block(x, Y, Z, EA + "steam_powered_generator");
                    c.power(8, Y, Z, "steam", 32);
                    c.power(24, Y, Z, "steam", 32);
                    c.power(24, Y, Z, ELECTRICITY, 1000);
                    c.record("generator.constructionGameTime", c.world.getGameTime());
                }, c -> {
                    for (int x : new int[] {8, 16, 24}) recordSchedule(c, "generator" + x, c.activationTick, x, Y, Z, 40);
                    requireWindow(c, c.activationTick, 40);
                    int updates = ElectricOperationalRates.updateDelays(c.activationTick, phase(c, 8, Y, Z), 40).size();
                    float expected = ElectricOperationalRates.preloadedSteamElectricity(32, updates);
                    c.record("positive.expectedGenerated", expected);
                    close(c, "positive.generated", c.energy(8, Y, Z, ELECTRICITY), expected, 0.001f);
                    close(c, "empty.generated", c.energy(16, Y, Z, ELECTRICITY), 0, 0.001f);
                    close(c, "full.electricity", c.energy(24, Y, Z, ELECTRICITY), 1000, 0.001f);
                    c.record("positive.steamRemaining", c.nbt(8, Y, Z).getFloat("steam"));
                    close(c, "positive.steamExhausted", c.nbt(8, Y, Z).getFloat("steam"), 0, 0.001f);
                    int fullUpdates = ElectricOperationalRates.updateDelays(c.activationTick, phase(c, 24, Y, Z), 40).size();
                    close(c, "full.steamAfterDecay", c.nbt(24, Y, Z).getFloat("steam"),
                            ElectricOperationalRates.fullGeneratorSteam(32, fullUpdates), 0.001f);
                }));
        cases.add(new MachineScenario("electric/steam-generator-supply",
                "A finite adjacent steam tank supplies a generator over the real conduit network; produced "
                + "electricity is bounded by 31.25 per supplied steam unit, including decay, and charges a battery.", 100, c -> {
                    c.block(8, Y, Z, EA + "steam_powered_generator");
                    c.steamSupply(7, Y, Z, 96);
                    c.electricSupply(9, Y, Z, 0);
                    c.record("steamSupply.constructionGameTime", c.world.getGameTime());
                }, c -> {
                    recordSchedule(c, "steamTank", c.activationTick, 7, Y, Z, 100);
                    recordSchedule(c, "generator", c.activationTick, 8, Y, Z, 100);
                    requireWindow(c, c.activationTick, 100);
                    ElectricOperationalRates.SupplyLedger expected = ElectricOperationalRates.tankSupply(
                            c.activationTick, phase(c, 7, Y, Z), phase(c, 8, Y, Z), 100, 96);
                    c.record("network.expectedTransfers", expected.transfers);
                    c.record("network.expectedConversions", expected.conversions);
                    c.record("network.expectedTankLeakage", expected.tankLeakage);
                    c.record("network.expectedGeneratorLeakage", expected.generatorLeakage);
                    c.record("network.expectedGenerated", expected.generated);
                    float generated = c.energy(8, Y, Z, ELECTRICITY) + c.energy(9, Y, Z, ELECTRICITY);
                    close(c, "network.generated", generated, expected.generated, 0.001f);
                    c.require(c.energy(9, Y, Z, ELECTRICITY) > 0, "Steam generation charges a real battery");
                    close(c, "network.steamSupplyExhausted", c.energy(7, Y, Z, "steam"), expected.remaining, 0.001f);
                }));
    }

    private static void batteries(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/battery-priority",
                "A finite generator supplies high-priority LED and medium-priority furnace before backup "
                + "battery charging. Its one-time LED allocation expires without further supply. Stored electricity "
                + "plus actual load debit is conserved; batteries do not cycle "
                + "into equal-priority batteries.", 80, c -> {
                    c.block(8, Y, Z, EA + "steam_powered_generator");
                    c.power(8, Y, Z, ELECTRICITY, 100);
                    c.block(9, Y, Z, EA + "electric_furnace");
                    c.block(8, Y, Z - 1, EA + "led_bar");
                    c.electricSupply(8, Y, Z + 1, 0);
                    c.electricSupply(22, Y, Z, 500);
                    c.electricSupply(23, Y, Z, 0);
                }, c -> {
                    float furnace = c.energy(9, Y, Z, ELECTRICITY);
                    float led = c.energy(8, Y, Z - 1, ELECTRICITY);
                    float source = c.energy(8, Y, Z, ELECTRICITY);
                    float backup = c.energy(8, Y, Z + 1, ELECTRICITY);
                    c.record("priority.furnaceStored", furnace);
                    c.record("priority.backupStored", backup);
                    close(c, "priority.furnaceAllocation", furnace, 99.5f, 0.001f);
                    close(c, "priority.backupAllocation", backup, 0, 0.001f);
                    c.require(!c.world.getBlockState(c.at(8, Y, Z - 1)).get(BlockStateProperties.LIT), "One-time half-unit LED allocation expires without creating energy");
                    close(c, "priority.totalStored", source + furnace + led + backup, 99.5f, 0.001f);
                    close(c, "noCycle.charged", c.energy(22, Y, Z, ELECTRICITY), 500, 0.001f);
                    close(c, "noCycle.empty", c.energy(23, Y, Z, ELECTRICITY), 0, 0.001f);
                }));
        cases.add(new MachineScenario("electric/battery-redstone",
                "A redstone-disabled battery retains its finite charge and cannot energize its adjacent oven.", 160, c -> {
                    c.electricSupply(7, Y, Z, 2000);
                    c.block(6, Y, Z, "minecraft:redstone_block");
                    c.block(8, Y, Z, EA + "electric_oven");
                    c.put(8, Y, Z, 0, "minecraft:potato", 1);
                }, c -> {
                    close(c, "disabled.battery", c.energy(7, Y, Z, ELECTRICITY), 2000, 0.001f);
                    close(c, "disabled.ovenEnergy", c.energy(8, Y, Z, ELECTRICITY), 0, 0.001f);
                    equal(c, "disabled.rawInput", slotCount(c, 8, 0, "minecraft:potato"), 1);
                    equal(c, "disabled.product", output(c, 8, 1, 1, "minecraft:baked_potato"), 0);
                }));
    }

    private static void turret(List<MachineScenario> cases) {
        for (int variant = 0; variant < 4; variant++) {
            final int mode = variant;
            final HuskEntity[] target = new HuskEntity[1];
            cases.add(new MachineScenario("electric/turret-" + new String[] {"hit", "no-power", "scarce", "occluded"}[mode],
                    "Natural turret acquisition must damage a real stationary hostile entity and debit a 1000-unit "
                    + "shot plus tracking energy. No electricity, sub-shot reserve and a solid wall cannot damage it.", 80, c -> {
                        turretRoom(c);
                        c.block(8, 1, Z, EA + "laser_turret");
                        if (mode != 1) c.power(8, 1, Z, ELECTRICITY, mode == 2 ? 999 : 1500);
                        if (mode == 3) for (int y = 1; y <= 4; y++) for (int z = Z - 2; z <= Z + 2; z++)
                            c.block(12, y, z, "minecraft:stone");
                        HuskEntity husk = EntityType.HUSK.create(c.world);
                        c.require(husk != null, "Created a real hostile target");
                        husk.setPositionAndRotation(c.at(16, 1, Z).getX() + 0.5, c.at(16, 1, Z).getY(), c.at(16, 1, Z).getZ() + 0.5, 0, 0);
                        husk.setNoAI(true);
                        husk.enablePersistence();
                        husk.setNoGravity(true);
                        c.require(husk.getAttribute(SharedMonsterAttributes.ARMOR) != null, "Hostile target has an armor attribute");
                        husk.getAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0);
                        equal(c, "target.fixtureArmor", husk.getTotalArmorValue(), 0);
                        // Fire immunity isolates the seven-point laser impact from its burn side effect.
                        husk.addPotionEffect(new net.minecraft.potion.EffectInstance(net.minecraft.potion.Effects.FIRE_RESISTANCE, 200, 0));
                        c.require(c.world.addEntity(husk), "Spawned target into the real server world");
                        target[0] = husk;
                        c.record("target.initialHealth", husk.getHealth());
                        c.record("target.entityUuid", husk.getUniqueID().toString());
                    }, c -> {
                        HuskEntity husk = target[0];
                        try {
                            c.require(husk != null && husk.isAlive(), "Hostile target survives for impact measurement");
                            c.record("target.health", husk.getHealth());
                            c.record("target.ticks", husk.ticksExisted);
                            c.record("target.turretEnergy", c.energy(8, 1, Z, ELECTRICITY));
                            c.require(husk.ticksExisted >= 70, "Target received natural server entity ticks");
                            if (mode == 0) {
                                close(c, "hit.health", husk.getHealth(), 13, 0.01f);
                                between(c, "hit.energyDebit", 1500 - c.energy(8, 1, Z, ELECTRICITY), 1024, 1032);
                            } else {
                                close(c, "negative.health", husk.getHealth(), 20, 0.01f);
                                if (mode == 1 || mode == 2) close(c, "negative.energy", c.energy(8, 1, Z, ELECTRICITY), mode == 1 ? 0 : 999, 0.01f);
                                if (mode == 3) between(c, "occluded.trackingDebit", 1500 - c.energy(8, 1, Z, ELECTRICITY), 70, 80);
                            }
                        } finally {
                            if (husk != null) husk.remove();
                            target[0] = null;
                        }
                    }));
        }
        final FakePlayer[] owner = new FakePlayer[1];
        final ScorePlayerTeam[] team = new ScorePlayerTeam[1];
        final HuskEntity[] victim = new HuskEntity[1];
        cases.add(new MachineScenario("electric/turret-owner-credit",
                "A real naturally acquired hostile target is hit and its last attacking mob is the turret's "
                + "registered owner, proving ownership has a gameplay effect beyond saved owner NBT.", 40, c -> {
                    turretRoom(c);
                    c.block(8, 1, Z, EA + "laser_turret");
                    c.power(8, 1, Z, ELECTRICITY, 1500);
                    UUID identity = UUID.randomUUID();
                    String name = "EOp" + identity.toString().substring(0, 8);
                    FakePlayer player = new FakePlayer(c.world, new GameProfile(identity, name));
                    player.connection = new net.minecraft.network.play.ServerPlayNetHandler(c.world.getServer(),
                            new net.minecraft.network.NetworkManager(net.minecraft.network.PacketDirection.SERVERBOUND), player) {
                        @Override public void sendPacket(net.minecraft.network.IPacket<?> packet) { }
                    };
                    player.setPositionAndRotation(c.at(8, 1, 16).getX() + 0.5, c.at(8, 1, 16).getY(), c.at(8, 1, 16).getZ() + 0.5, 0, 0);
                    owner[0] = player;
                    team[0] = c.world.getScoreboard().createTeam(name);
                    c.world.getScoreboard().addPlayerToTeam(player.getScoreboardName(), team[0]);
                    c.world.addNewPlayer(player);
                    c.invoke(8, 1, Z, "setOwner", new Class<?>[] {PlayerEntity.class}, player);
                    HuskEntity husk = EntityType.HUSK.create(c.world);
                    c.require(husk != null, "Created ownership target");
                    victim[0] = husk;
                    husk.setPositionAndRotation(c.at(16, 1, Z).getX() + 0.5, c.at(16, 1, Z).getY(), c.at(16, 1, Z).getZ() + 0.5, 0, 0);
                    husk.setNoAI(true);
                    husk.setNoGravity(true);
                    husk.enablePersistence();
                    c.require(husk.getAttribute(SharedMonsterAttributes.ARMOR) != null, "Ownership target has an armor attribute");
                    husk.getAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(0);
                    equal(c, "ownerCredit.fixtureArmor", husk.getTotalArmorValue(), 0);
                    husk.addPotionEffect(new net.minecraft.potion.EffectInstance(net.minecraft.potion.Effects.FIRE_RESISTANCE, 200, 0));
                    c.require(c.world.addEntity(husk), "Ownership target is in the real world");
                    c.record("owner.profile", name);
                }, c -> {
                    try {
                        c.require(c.world.getPlayers().contains(owner[0]), "Owner is registered in this server world");
                        c.record("ownerCredit.turretEnergy", c.energy(8, 1, Z, ELECTRICITY));
                        close(c, "ownerCredit.targetHealth", victim[0].getHealth(), 13, 0.01f);
                        c.require(victim[0].getRevengeTarget() == owner[0], "Real laser impact is credited to the configured owner");
                        c.record("ownerCredit.attacker", victim[0].getRevengeTarget().getUniqueID().toString());
                        between(c, "ownerCredit.energyDebit", 1500 - c.energy(8, 1, Z, ELECTRICITY), 1024, 1032);
                    } finally {
                        if (victim[0] != null) victim[0].remove();
                        if (owner[0] != null) {
                            owner[0].remove();
                            c.world.getPlayers().remove(owner[0]);
                        }
                        if (team[0] != null) c.world.getScoreboard().removeTeam(team[0]);
                        owner[0] = null;
                        team[0] = null;
                        victim[0] = null;
                    }
                }));
    }

    private static void turretRoom(MachineContext c) {
        for (int x = 2; x <= 29; x++) for (int z = 2; z <= 29; z++) {
            c.block(x, 0, z, "minecraft:stone");
            c.block(x, 5, z, "minecraft:stone");
            if (x == 2 || x == 29 || z == 2 || z == 29)
                for (int y = 1; y < 5; y++) c.block(x, y, z, "minecraft:stone");
        }
    }

    private static float pairEnergy(MachineContext c, int x) { return energyPair(c, x, Y, Z); }

    private static int phase(MachineContext c, int x, int y, int z) {
        BlockPos pos = c.at(x, y, z);
        return ElectricOperationalRates.offset(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void recordSchedule(MachineContext c, String key, long activationTick, int x, int y, int z, int ticks) {
        int offset = phase(c, x, y, z);
        c.record(key + ".activationTick", activationTick);
        c.record(key + ".position", c.at(x, y, z).toString());
        c.record(key + ".powerOffset", offset);
        c.record(key + ".expectedUpdateDelays", ElectricOperationalRates.updateDelays(activationTick, offset, ticks));
    }

    private static void requireWindow(MachineContext c, long activationTick, int ticks) {
        long elapsed = c.world.getGameTime() - activationTick;
        c.record("rate.elapsedTicks", elapsed);
        c.require(elapsed == ticks, "Rate oracle observed exactly " + ticks + " natural world ticks");
    }

    private static float energyPair(MachineContext c, int x, int y, int z) {
        return c.energy(x, y, z, ELECTRICITY) + c.energy(x - 1, y, z, ELECTRICITY);
    }

    private static int slotCount(MachineContext c, int x, int slot, String id) {
        return stackCount(c.inventory(x, Y, Z).getStackInSlot(slot), id);
    }

    private static int inputCount(MachineContext c, int x, int slots, String id) {
        return output(c, x, 0, slots, id);
    }

    private static int output(MachineContext c, int x, int first, int slots, String id) {
        IInventory inventory = c.inventory(x, Y, Z);
        int count = 0;
        for (int slot = first; slot < first + slots; slot++) count += stackCount(inventory.getStackInSlot(slot), id);
        return count;
    }

    private static int stackCount(ItemStack stack, String id) {
        return stack == null || stack.isEmpty() || !new ResourceLocation(id).equals(stack.getItem().getRegistryName()) ? 0 : stack.getCount();
    }

    private static IFluidHandler fluids(MachineContext c, int x) {
        return fluids(c, x, null);
    }

    private static IFluidHandler fluids(MachineContext c, int x, Direction face) {
        return c.tile(x, Y, Z).getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face)
                .orElseThrow(() -> new IllegalStateException("Missing real fluid capability at " + x));
    }

    // These are finite fixture reserves, not automation acceptance tests. Electric's
    // fluid machines are siblings of TileEntitySimpleFluidMachine, so use their public API.
    private static void reserve(MachineContext c, int x, int y, int z, String method, String id, int amount) {
        cyano.poweradvantage.api.fluid.FluidTank tank = (cyano.poweradvantage.api.fluid.FluidTank)
                c.invoke(x, y, z, method, new Class<?>[0]);
        net.minecraft.fluid.Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        c.require(fluid != null && amount >= 0 && amount <= tank.getCapacity(), "Valid finite fluid reserve " + id);
        tank.setFluid(new cyano.poweradvantage.api.fluid.FluidStack(fluid, amount));
        c.tile(x, y, z).markDirty();
        c.record("setup.reserve/" + x + "/" + y + "/" + z + "/" + method, id + ":" + amount);
    }

    private static int fluidAmount(MachineContext c, int x, int y, int z) {
        int amount = ((cyano.poweradvantage.api.fluid.FluidTank)c.invoke(x, y, z, "getTank", new Class<?>[0])).getFluidAmount();
        c.record("fluid/" + x + "/" + y + "/" + z, amount);
        return amount;
    }

    private static int tankAmount(MachineContext c, int x, int tank) {
        return fluids(c, x).getFluidInTank(tank).getAmount();
    }

    private static void fluidIs(MachineContext c, int x, int tank, String id) {
        FluidStack fluid = fluids(c, x).getFluidInTank(tank);
        c.record("fluid." + x + "." + tank, String.valueOf(fluid.getFluid().getRegistryName()));
        c.require(new ResourceLocation(id).equals(fluid.getFluid().getRegistryName()), "Tank contains " + id);
    }

    private static void rejectFluid(MachineContext c, int x, String id, int amount) {
        net.minecraft.fluid.Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        c.require(fluid != null, "Negative-control fluid exists: " + id);
        equal(c, "invalidFluid.accepted", fluids(c, x).fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE), 0);
    }

    private static void equal(MachineContext c, String key, int actual, int expected) {
        c.record(key, actual);
        c.require(actual == expected, key + " expected " + expected + ", got " + actual);
    }

    private static void close(MachineContext c, String key, float actual, float expected, float tolerance) {
        c.record(key, actual);
        c.require(Float.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                key + " expected " + expected + " +/- " + tolerance + ", got " + actual);
    }

    private static void between(MachineContext c, String key, float actual, float low, float high) {
        c.record(key, actual);
        c.require(Float.isFinite(actual) && actual >= low && actual <= high,
                key + " expected [" + low + ", " + high + "], got " + actual);
    }
}
