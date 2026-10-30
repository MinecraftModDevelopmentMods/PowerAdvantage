package zone.moddev.mc.advantageworks;

import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.FluidTank;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** Independent finite inputs, ordinary world ticks, and material/energy outcomes. */
final class SteamOperationalCases {
    private static final int X = 8, Y = 4, Z = 8;
    private static final Class<?>[] NO_PARAMETERS = new Class<?>[0];

    private SteamOperationalCases() {}

    static void register(List<MachineScenario> cases) {
        boilers(cases);
        furnace(cases);
        crusherAndStorage(cases);
        still(cases);
        pump(cases);
        SteamOperationalActors.register(cases);
        SteamOperationalSupplementalCases.register(cases);
        SteamReloadControlCases.register(cases);
    }

    private static void add(List<MachineScenario> cases, String id, String contract, int ticks,
                            Consumer<MachineContext> setup, Consumer<MachineContext> verify) {
        cases.add(new MachineScenario("steam/" + id, contract, ticks, setup, verify));
    }

    private static void machine(MachineContext c, String id) {
        c.block(X, Y, Z, "steamadvantage:" + id);
    }

    private static void supply(MachineContext c, float amount) {
        c.steamSupply(X - 1, Y, Z, amount);
    }

    private static float steam(MachineContext c) { return c.energy(X, Y, Z, "steam"); }

    private static float remainingSteam(MachineContext c) {
        return steam(c) + c.energy(X - 1, Y, Z, "steam");
    }

    private static FluidTank tank(MachineContext c, String getter) {
        return (FluidTank) c.invoke(X, Y, Z, getter, NO_PARAMETERS);
    }

    private static int amount(MachineContext c, String getter) {
        int value = tank(c, getter).getFluidAmount();
        c.record(getter + "/amount", value);
        return value;
    }

    // This is an actual face-capability insertion, not c.tank's direct reserve seeding.
    private static void fill(MachineContext c, String id, int amount) {
        Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(id));
        c.require(fluid != null, "Registered input fluid " + id);
        IFluidHandler handler = c.tile(X, Y, Z).getCapability(
                CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("Missing Steam input fluid capability"));
        int accepted = handler.fill(new net.minecraftforge.fluids.FluidStack(fluid, amount),
                IFluidHandler.FluidAction.EXECUTE);
        c.record("capability/input/" + id, accepted);
        c.require(accepted == amount, "UP fluid capability accepts exactly " + amount + " mB of " + id);
        c.tile(X, Y, Z).markDirty();
    }

    private static void reserve(MachineContext c, String key, String fluid, int amount) {
        String getter;
        if (key.equals("TankIn")) getter = "getInputTank";
        else if (key.equals("TankOut")) getter = "getOutputTank";
        else throw new IllegalArgumentException("Unknown Steam reserve tank " + key);
        FluidTank tank = tank(c, getter);
        Fluid type = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(fluid));
        c.require(type != null && amount > 0 && amount <= tank.getCapacity(), "Valid finite setup reserve for " + key);
        // Replace the complete tank value: recursively merged NBT retains an old Empty marker.
        tank.setFluid(new FluidStack(type, amount));
        c.tile(X, Y, Z).markDirty();
        c.record("setup/reserve/" + key, fluid + ":" + amount);
        fluid(c, getter, fluid, amount);
        c.require(!c.nbt(X, Y, Z).getCompound(key).contains("Empty"), "Finite reserve is serialized as nonempty before ticking");
    }

    private static void slot(MachineContext c, int index, String item, int count) {
        ItemStack stack = c.inventory(X, Y, Z).getStackInSlot(index);
        String actual = stack.isEmpty() ? "empty" : String.valueOf(stack.getItem().getRegistryName());
        c.record("slot/" + index, actual + ":" + stack.getCount());
        c.require(count == 0 ? stack.isEmpty()
                : actual.equals(item) && stack.getCount() == count,
                "Slot " + index + " is " + (count == 0 ? "empty" : count + " " + item));
    }

    private static void fluid(MachineContext c, String getter, String expected, int count) {
        FluidTank tank = tank(c, getter);
        String id = tank.getFluidAmount() == 0 ? "empty"
                : String.valueOf(ForgeRegistries.FLUIDS.getKey(tank.getFluid().getFluid()));
        c.record(getter + "/fluid", id);
        c.record(getter + "/amount", tank.getFluidAmount());
        c.require(tank.getFluidAmount() == count && (count == 0 || expected.equals(id)),
                getter + " contains exactly " + count + " mB " + expected);
    }

    private static void boilers(List<MachineScenario> cases) {
        add(cases, "coal-production", "One coal and 64 mB water produce exactly 64 steam; both resources debit.", 160,
                c -> { machine(c, "steam_boiler_coal"); fill(c, "minecraft:water", 64);
                    c.put(X, Y, Z, 0, "minecraft:coal", 1); },
                c -> { slot(c, 0, "minecraft:coal", 0); fluid(c, "getTank", "minecraft:water", 0);
                    c.require(steam(c) == 64, "64 water becomes exactly 64 steam during the finite coal burn"); });
        add(cases, "coal-invalid-fuel", "Stone cannot burn or consume water.", 120,
                c -> { machine(c, "steam_boiler_coal"); fill(c, "minecraft:water", 64);
                    c.put(X, Y, Z, 0, "minecraft:stone", 1); },
                c -> { slot(c, 0, "minecraft:stone", 1); fluid(c, "getTank", "minecraft:water", 64);
                    c.require(steam(c) == 0, "Invalid fuel produces no steam"); });
        add(cases, "coal-dry", "A dry boiler does not consume its coal or create steam.", 120,
                c -> { machine(c, "steam_boiler_coal"); c.put(X, Y, Z, 0, "minecraft:coal", 1); },
                c -> { slot(c, 0, "minecraft:coal", 1); c.require(steam(c) == 0, "No water means no steam"); });
        add(cases, "coal-redstone", "Redstone prevents a cold coal boiler igniting.", 120,
                c -> { machine(c, "steam_boiler_coal"); fill(c, "minecraft:water", 64);
                    c.put(X, Y, Z, 0, "minecraft:coal", 1); c.block(X + 1, Y, Z, "minecraft:redstone_block"); },
                c -> { slot(c, 0, "minecraft:coal", 1); fluid(c, "getTank", "minecraft:water", 64);
                    c.require(steam(c) == 0, "Redstone inhibits ignition"); });

        add(cases, "oil-production", "A finite 100 mB crude-oil charge converts 64 water to 80 steam at the historical 25-percent bonus.", 160,
                c -> { machine(c, "steam_boiler_oil"); fill(c, "minecraft:water", 64);
                    fill(c, "poweradvantage:crude_oil", 100); },
                c -> { fluid(c, "getWaterTank", "minecraft:water", 0);
                    fluid(c, "getFuelTank", "poweradvantage:crude_oil", 0);
                    c.require(steam(c) == 80, "64 debited water produces exactly 80 steam at the oil boiler's 1.25 ratio"); });
        add(cases, "oil-water-not-fuel", "Water is not liquid fuel and cannot burn itself.", 120,
                c -> { machine(c, "steam_boiler_oil"); fill(c, "minecraft:water", 64); },
                c -> { fluid(c, "getWaterTank", "minecraft:water", 64);
                    fluid(c, "getFuelTank", "poweradvantage:crude_oil", 0);
                    c.require(steam(c) == 0, "Nonflammable water does not produce steam without fuel"); });
        add(cases, "oil-dry", "Liquid fuel is preserved when no water is available.", 120,
                c -> { machine(c, "steam_boiler_oil"); fill(c, "poweradvantage:crude_oil", 100); },
                c -> { fluid(c, "getFuelTank", "poweradvantage:crude_oil", 100);
                    c.require(steam(c) == 0, "Dry oil boiler does not ignite"); });
        add(cases, "oil-small-charge", "A 1 mB fuel aliquot can boil a finite three-water charge without inventing water.", 100,
                c -> { machine(c, "steam_boiler_oil"); fill(c, "minecraft:water", 3);
                    fill(c, "poweradvantage:crude_oil", 1); },
                c -> { fluid(c, "getFuelTank", "poweradvantage:crude_oil", 0);
                    fluid(c, "getWaterTank", "minecraft:water", 0);
                    float value = steam(c); c.require(value >= 0 && value <= 3.75F,
                            "Three water cannot create more than 3.75 steam at the historical oil bonus (including idle loss)");
                    c.require(c.nbt(X, Y, Z).getInt("BurnTime") == 0, "Small fuel aliquot burns out naturally"); });

        add(cases, "geothermal-production", "A contained lava heat source boils finite water from a cold start.", 240,
                c -> { machine(c, "steam_boiler_geothermal"); heat(c); fill(c, "minecraft:water", 256); },
                c -> { int left = amount(c, "getTank"); float output = steam(c);
                    c.require(left < 256 && output > 0 && output <= 256 - left,
                            "Geothermal output is positive and bounded by debited water");
                    c.require(c.world.getFluidState(c.at(X, Y - 1, Z)).isSource(),
                            "Geothermal heat source is retained, not treated as consumable fuel"); });
        add(cases, "geothermal-cold", "Water without environmental heat cannot produce steam.", 240,
                c -> { machine(c, "steam_boiler_geothermal"); fill(c, "minecraft:water", 16); },
                c -> { fluid(c, "getTank", "minecraft:water", 16);
                    c.require(steam(c) == 0, "Cold geothermal boiler produces nothing"); });
        add(cases, "geothermal-dry", "Environmental heat cannot create steam without water.", 240,
                c -> { machine(c, "steam_boiler_geothermal"); heat(c); },
                c -> { c.require(amount(c, "getTank") == 0 && steam(c) == 0, "Hot dry boiler produces no steam"); });

        add(cases, "electric-production", "Finite battery power converts water at 16 electricity per mB and 0.5 steam per mB.", 160,
                c -> { machine(c, "steam_boiler_electric"); fill(c, "minecraft:water", 64);
                    c.electricSupply(X - 1, Y, Z, 4096); },
                c -> { int water = amount(c, "getTank");
                    float electric = c.energy(X - 1, Y, Z, "electricity") + c.energy(X, Y, Z, "electricity");
                    float output = steam(c); c.record("electricity/debited", 4096 - electric);
                    c.require(water == 0 && output > 0 && output <= 32, "64 water produces at most 32 steam after idle loss");
                    c.require(Math.abs(4096 - electric - 16 * (64 - water)) < 0.001F,
                            "Every water conversion debits exactly 16 electricity from the finite battery/machine total"); });
        add(cases, "electric-no-power", "Water cannot boil without electricity.", 120,
                c -> { machine(c, "steam_boiler_electric"); fill(c, "minecraft:water", 64); },
                c -> { fluid(c, "getTank", "minecraft:water", 64); c.require(steam(c) == 0, "No electrical power gives no steam"); });
        add(cases, "electric-dry", "A connected charged battery is not consumed by a dry boiler.", 120,
                c -> { machine(c, "steam_boiler_electric"); c.electricSupply(X - 1, Y, Z, 4096); },
                c -> { float left = c.energy(X - 1, Y, Z, "electricity") + c.energy(X, Y, Z, "electricity");
                    c.require(left == 4096 && steam(c) == 0, "Dry boiler conserves all finite electricity"); });
        add(cases, "electric-insufficient", "Less than one 16-electricity conversion cannot debit water.", 100,
                c -> { machine(c, "steam_boiler_electric"); fill(c, "minecraft:water", 8);
                    c.electricSupply(X - 1, Y, Z, 15); },
                c -> { fluid(c, "getTank", "minecraft:water", 8);
                    c.require(steam(c) == 0 && c.energy(X, Y, Z, "electricity") == 15,
                            "Sub-operation electrical charge is retained without partial production"); });
    }

    private static void heat(MachineContext c) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            c.block(X + dx, Y - 2, Z + dz, "minecraft:stone");
            if (dx != 0 || dz != 0) c.block(X + dx, Y - 1, Z + dz, "minecraft:stone");
        }
        c.block(X, Y - 1, Z, "minecraft:lava");
    }

    private static void furnace(List<MachineScenario> cases) {
        add(cases, "furnace-three-lanes", "All three cold-start smelting lanes finish independently using one coal and finite steam.", 900,
                c -> { machine(c, "steam_furnace"); supply(c, 1200); c.put(X, Y, Z, 0, "minecraft:coal", 1);
                    c.put(X, Y, Z, 1, "minecraft:sand", 3); c.put(X, Y, Z, 2, "minecraft:cobblestone", 3);
                    c.put(X, Y, Z, 3, "minecraft:potato", 3); },
                c -> { for (int i = 0; i <= 3; i++) slot(c, i, "", 0);
                    slot(c, 4, "minecraft:glass", 3); slot(c, 5, "minecraft:stone", 3);
                    slot(c, 6, "minecraft:baked_potato", 3);
                    c.require(remainingSteam(c) < 1200 - 100, "Steam heating debits more than idle leakage"); });
        add(cases, "furnace-no-fuel", "Steam is a heat bonus, not a substitute for combustible furnace fuel.", 300,
                c -> { machine(c, "steam_furnace"); supply(c, 600); c.put(X, Y, Z, 1, "minecraft:sand", 1); },
                c -> { slot(c, 1, "minecraft:sand", 1); slot(c, 4, "", 0);
                    c.require(remainingSteam(c) >= 600 - 2 * 300 * 0.0625F, "Unlit furnace uses no steam heating bonus"); });
        add(cases, "furnace-full-outputs", "Blocked lanes neither consume input nor start a coal burn.", 300,
                c -> { machine(c, "steam_furnace"); supply(c, 600); c.put(X, Y, Z, 0, "minecraft:coal", 1);
                    for (int i = 1; i <= 3; i++) { c.put(X, Y, Z, i, "minecraft:sand", 1);
                        c.put(X, Y, Z, i + 3, "minecraft:glass", 64); } },
                c -> { slot(c, 0, "minecraft:coal", 1); for (int i = 1; i <= 3; i++) {
                    slot(c, i, "minecraft:sand", 1); slot(c, i + 3, "minecraft:glass", 64); } });
        add(cases, "furnace-invalid-input", "A nonsmeltable input cannot consume coal or produce output.", 300,
                c -> { machine(c, "steam_furnace"); supply(c, 600); c.put(X, Y, Z, 0, "minecraft:coal", 1);
                    c.put(X, Y, Z, 1, "minecraft:diamond", 1); },
                c -> { slot(c, 0, "minecraft:coal", 1); slot(c, 1, "minecraft:diamond", 1); slot(c, 4, "", 0); });
        add(cases, "furnace-without-steam", "Coal alone remains a valid slower heat source in the historical furnace.", 1000,
                c -> { machine(c, "steam_furnace"); c.put(X, Y, Z, 0, "minecraft:coal", 1);
                    c.put(X, Y, Z, 1, "minecraft:sand", 1); },
                c -> { slot(c, 0, "", 0); slot(c, 1, "", 0); slot(c, 4, "minecraft:glass", 1);
                    c.require(steam(c) == 0, "Unboosted operation does not invent steam"); });
    }

    private static void crusherAndStorage(List<MachineScenario> cases) {
        add(cases, "crusher-and-tank-discharge", "A finite steam tank drives two complete 125-tick crushing operations.", 360,
                c -> { machine(c, "steam_crusher"); supply(c, 1000); c.put(X, Y, Z, 0, "minecraft:stone", 2); },
                c -> { slot(c, 0, "", 0); c.require(c.count(X, Y, Z, "minecraft:cobblestone") == 2,
                        "Two stone become exactly two cobblestone");
                    c.require(remainingSteam(c) <= 1000 - 2 * 125 * 1.5F,
                            "Tank discharge funds at least the exact 375-steam productive work"); });
        add(cases, "crusher-no-power", "A crushable input cannot progress without steam.", 300,
                c -> { machine(c, "steam_crusher"); c.put(X, Y, Z, 0, "minecraft:stone", 1); },
                c -> { slot(c, 0, "minecraft:stone", 1); c.require(c.count(X, Y, Z, "minecraft:cobblestone") == 0,
                        "Unpowered crusher produces nothing"); });
        add(cases, "crusher-full-outputs", "All five full outputs preserve the input and avoid productive steam debit.", 300,
                c -> { machine(c, "steam_crusher"); supply(c, 600); c.put(X, Y, Z, 0, "minecraft:stone", 1);
                    for (int i = 1; i <= 5; i++) c.put(X, Y, Z, i, "minecraft:cobblestone", 64); },
                c -> { slot(c, 0, "minecraft:stone", 1);
                    c.require(c.count(X, Y, Z, "minecraft:cobblestone") == 320, "Full output stacks remain unchanged");
                    c.require(remainingSteam(c) >= 600 - 2 * 300 * 0.0625F, "Blocked crusher loses only idle steam"); });
        add(cases, "crusher-invalid-input", "An invalid recipe does not consume input or productive steam.", 300,
                c -> { machine(c, "steam_crusher"); supply(c, 600); c.put(X, Y, Z, 0, "minecraft:bedrock", 1);
                    c.require(!c.inventory(X, Y, Z).isItemValidForSlot(0, c.inventory(X, Y, Z).getStackInSlot(0)),
                            "Active recipe registry confirms bedrock is not a crushable input"); },
                c -> { slot(c, 0, "minecraft:bedrock", 1); for (int i = 1; i <= 5; i++) slot(c, i, "", 0);
                    c.require(remainingSteam(c) >= 600 - 2 * 300 * 0.0625F, "Invalid recipe spends no productive steam"); });
        add(cases, "tank-redstone-stop", "A redstone-disabled tank cannot discharge into a connected working consumer.", 300,
                c -> { machine(c, "steam_crusher"); supply(c, 600);
                    c.block(X - 2, Y, Z, "minecraft:redstone_block"); c.put(X, Y, Z, 0, "minecraft:stone", 1); },
                c -> { slot(c, 0, "minecraft:stone", 1); slot(c, 1, "", 0);
                    c.require(steam(c) == 0 && c.energy(X - 1, Y, Z, "steam") >= 600 - 300 * 0.0625F,
                            "Disabled tank retains its reserve apart from specified idle leakage"); });
        add(cases, "tank-backup-no-cycling", "Backup-priority storage does not discharge pointlessly into other storage.", 160,
                c -> { machine(c, "steam_tank"); supply(c, 600); },
                c -> { c.require(steam(c) == 0, "Empty backup storage receives no storage-to-storage transfer");
                    c.require(c.energy(X - 1, Y, Z, "steam") == 590, "Source storage loses exactly 160 idle ticks, not a transfer"); });
    }

    private static void still(List<MachineScenario> cases) {
        add(cases, "still-production", "Steam distills finite crude oil at the historical 2:1 ratio.", 180,
                c -> { machine(c, "steam_still"); supply(c, 500); fill(c, "poweradvantage:crude_oil", 200); },
                c -> { fluid(c, "getInputTank", "poweradvantage:crude_oil", 0);
                    fluid(c, "getOutputTank", "poweradvantage:refined_oil", 100);
                    c.require(remainingSteam(c) <= 500 - 50 * 1.5F, "50 two-application distillations debit 75 steam"); });
        add(cases, "still-no-power", "Valid crude oil remains untouched without steam.", 160,
                c -> { machine(c, "steam_still"); fill(c, "poweradvantage:crude_oil", 200); },
                c -> { fluid(c, "getInputTank", "poweradvantage:crude_oil", 200);
                    fluid(c, "getOutputTank", "poweradvantage:refined_oil", 0); });
        add(cases, "still-full-output", "A full output cannot destroy crude oil or spend productive steam.", 160,
                c -> { machine(c, "steam_still"); supply(c, 500); fill(c, "poweradvantage:crude_oil", 200);
                    reserve(c, "TankOut", "poweradvantage:refined_oil", 1000); },
                c -> { fluid(c, "getInputTank", "poweradvantage:crude_oil", 200);
                    fluid(c, "getOutputTank", "poweradvantage:refined_oil", 1000);
                    c.require(remainingSteam(c) >= 500 - 2 * 160 * 0.0625F, "Full still spends no productive steam"); });
        add(cases, "still-one-output-space", "The final single output space accepts one recipe, never discarding a second recipe.", 160,
                c -> { machine(c, "steam_still"); supply(c, 500); fill(c, "poweradvantage:crude_oil", 200);
                    reserve(c, "TankOut", "poweradvantage:refined_oil", 999); },
                c -> { fluid(c, "getInputTank", "poweradvantage:crude_oil", 198);
                    fluid(c, "getOutputTank", "poweradvantage:refined_oil", 1000);
                    c.require(remainingSteam(c) <= 498.5F, "The single accepted operation debits steam"); });
        add(cases, "still-incomplete-input", "An incomplete 2-mB recipe cannot turn one mB into product.", 160,
                c -> { machine(c, "steam_still"); supply(c, 500);
                    reserve(c, "TankIn", "poweradvantage:crude_oil", 1); },
                c -> { fluid(c, "getInputTank", "poweradvantage:crude_oil", 1);
                    fluid(c, "getOutputTank", "poweradvantage:refined_oil", 0); });
        add(cases, "still-invalid-fluid", "A real capability rejects water as still feedstock.", 120,
                c -> { machine(c, "steam_still"); supply(c, 500);
                    IFluidHandler handler = c.tile(X, Y, Z).getCapability(
                            CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, Direction.UP)
                            .orElseThrow(() -> new IllegalStateException("Missing still fluid capability"));
                    int accepted = handler.fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.fluid.Fluids.WATER, 100),
                            IFluidHandler.FluidAction.EXECUTE);
                    c.record("capability/rejected-water", accepted);
                    c.require(accepted == 0, "Water is rejected by the still input capability"); },
                c -> { fluid(c, "getInputTank", "minecraft:water", 0);
                    fluid(c, "getOutputTank", "poweradvantage:refined_oil", 0); });
    }

    private static void basin(MachineContext c) {
        // Lava's nine sources are finite; a 3x3 water pool would regenerate sources.
        for (int x = 6; x <= 10; x++) for (int z = 6; z <= 10; z++) {
            if (x == 6 || x == 10 || z == 6 || z == 10) c.block(x, 0, z, "minecraft:stone");
            else c.block(x, 0, z, "minecraft:lava");
        }
        c.require(sources(c) == 9, "Contained 3x3 intake begins with nine finite source blocks");
    }

    private static int sources(MachineContext c) {
        int count = 0;
        for (int x = 7; x <= 9; x++) for (int z = 7; z <= 9; z++) {
            if (c.world.getFluidState(c.at(x, 0, z)).isSource()) count++;
        }
        c.record("intake/source-blocks", count);
        return count;
    }

    private static boolean blockIs(MachineContext c, int x, int y, int z, String id) {
        String actual = String.valueOf(c.world.getBlockState(c.at(x, y, z)).getBlock().getRegistryName());
        c.record("block/" + x + "/" + y + "/" + z, actual);
        return actual.equals(id);
    }

    private static void pump(List<MachineScenario> cases) {
        add(cases, "pump-3x3-intake-lift", "The pump builds its lift, removes finite real sources and delivers matching fluid at elevation.", 320,
                c -> { machine(c, "steam_pump"); basin(c); supply(c, 600);
                    c.block(X + 1, Y, Z, "poweradvantage:fluid_storage_tank"); },
                c -> { int removed = 9 - sources(c); int stored = c.fluidAmount(X + 1, Y, Z);
                    int internal = amount(c, "getTank");
                    c.require(removed > 0 && stored >= 1000, "Real intake removal delivers at least a bucket four blocks above the intake");
                    c.require(stored + internal == removed * 1000, "Every removed source is conserved between receiver and pump");
                    for (int y = 1; y <= 3; y++) c.require(blockIs(c, X, y, Z, "steamadvantage:pump_pipe_steam"),
                            "Natural pump ticks build lift segment " + y);
                    c.require(remainingSteam(c) <= 600 - 3 * 5 - removed * (32 + 4),
                            "Finite steam debits pipe placement and four-block lifting for every removed bucket"); });
        add(cases, "pump-no-power", "A real 3x3 intake remains untouched without steam, including lift construction.", 180,
                c -> { machine(c, "steam_pump"); basin(c);
                    c.block(X + 1, Y, Z, "poweradvantage:fluid_storage_tank"); },
                c -> { c.require(sources(c) == 9 && amount(c, "getTank") == 0 && c.fluidAmount(X + 1, Y, Z) == 0,
                        "No power means no source removal or fluid output");
                    c.require(blockIs(c, X, Y - 1, Z, "minecraft:air"), "No steam means no lift construction"); });
        add(cases, "pump-insufficient-steam", "A sub-five-steam reserve cannot construct a lift or remove liquid.", 120,
                c -> { machine(c, "steam_pump"); basin(c); c.power(X, Y, Z, "steam", 4); },
                c -> { c.require(sources(c) == 9 && amount(c, "getTank") == 0, "Scarce steam does not pump any liquid");
                    c.require(blockIs(c, X, Y - 1, Z, "minecraft:air"), "Insufficient steam cannot place the first pipe"); });
        add(cases, "pump-full-output", "A full connected receiver cannot delete pump fluid or debit a new source.", 160,
                c -> { machine(c, "steam_pump"); basin(c); supply(c, 600); fill(c, "minecraft:lava", 1000);
                    c.block(X + 1, Y, Z, "poweradvantage:fluid_storage_tank");
                    c.tank(X + 1, Y, Z, "minecraft:lava", 4000); },
                c -> { fluid(c, "getTank", "minecraft:lava", 1000);
                    c.require(c.fluidAmount(X + 1, Y, Z) == 4000 && sources(c) == 9,
                            "Full-output backpressure preserves receiver, pump and all nine intake sources");
                    c.require(blockIs(c, X, Y - 1, Z, "minecraft:air"), "Full internal tank also prevents lift work"); });
    }
}
