package zone.moddev.mc.advantageworks;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import java.util.List;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

/** Finite legacy jobs. Verification only reads the world; live phase actions need a core hook. */
public final class ElectricLifecycleOperationalCases {
    private static final String EA = "electricadvantage:";
    private static final String ELECTRICITY = "electricity";
    private static final PropertyBool LIT = PropertyBool.create("lit");
    private static final PropertyBool ACTIVE = PropertyBool.create("active");
    private static final int TICKS = 160;
    private static final String[] CHEMISTRIES = {"lead_acid", "nickel_hydride", "alkaline", "lithium"};
    private static final float[] CAPACITIES = {1000, 2000, 4000, 8000};
    private static final float[] GAPS = {11, 13, 17, 19, 23, 29, 31, 37};

    private ElectricLifecycleOperationalCases() {}

    public static void register(List<MachineScenario> cases) {
        mixedCharge(cases);
        mixedDischarge(cases);
        freshRefill(cases, false);
        freshRefill(cases, true);
        cableSwitch(cases);
        cableGap(cases);
        solarControl(cases);
    }

    private static void mixedCharge(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/lifecycle/battery-mixed-charge",
                "Eight mixed batteries with 180 total room accept exactly 180 of a finite 200 electricity, "
                + "all reach their own capacity and the source retains 20. No battery stock or custom NBT is lost.",
                TICKS, c -> {
                    c.block(6, 4, 8, EA + "steam_powered_generator");
                    c.power(6, 4, 8, ELECTRICITY, 200);
                    float[] charge = new float[8];
                    for (int slot = 0; slot < 8; slot++) charge[slot] = capacity(slot) - GAPS[slot];
                    stockBank(c, charge, false);
                    close(c, "setup.capacity", bankCapacity(c), 30000);
                    close(c, "setup.room", 30000 - c.energy(7, 4, 8, ELECTRICITY), 180);
                }, c -> {
                    window(c, TICKS);
                    float[] charge = readBank(c, true);
                    for (int slot = 0; slot < 8; slot++) close(c, "slot" + slot + ".full", charge[slot], capacity(slot));
                    close(c, "bank.full", sum(charge), 30000);
                    close(c, "source.retained", c.energy(6, 4, 8, ELECTRICITY), 20);
                    NBTTagCompound generator = c.nbt(6, 4, 8);
                    c.require(generator.hasKey("steam", 5), "Finite generator records its real steam reserve");
                    close(c, "source.noSteam", generator.getFloat("steam"), 0);
                    close(c, "total.electricity", sum(charge) + c.energy(6, 4, 8, ELECTRICITY), 30020);
                }));
    }

    private static void mixedDischarge(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/lifecycle/battery-mixed-discharge",
                "A real LED drains across seven nearly empty battery slots into the eighth mixed battery. "
                + "The exact eight-tick lighting debit, remaining item charge and LED buffer conserve 10.875 electricity.",
                TICKS, c -> {
                    float[] charge = new float[8];
                    for (int slot = 0; slot < 7; slot++) charge[slot] = 0.125f;
                    charge[7] = 10;
                    stockBank(c, charge, false);
                    c.block(8, 4, 8, EA + "led_bar");
                }, c -> {
                    window(c, TICKS);
                    float[] charge = readBank(c, true);
                    float debit = ElectricOperationalRates.ledDebit(c.activationTick,
                            phase(c, 7, 4, 8), phase(c, 8, 4, 8), TICKS, 10.875f);
                    c.record("led.expectedDebit", debit);
                    c.require(debit > 0.875f, "The finite window must drain beyond the first seven batteries");
                    for (int slot = 0; slot < 7; slot++) close(c, "slot" + slot + ".empty", charge[slot], 0);
                    float led = c.energy(8, 4, 8, ELECTRICITY);
                    close(c, "lastSlot.remaining", charge[7], 10.875f - debit - led);
                    close(c, "total.electricity", sum(charge) + led, 10.875f - debit);
                    c.require(c.world.getBlockState(c.at(8, 4, 8)).getValue(LIT), "Retained charge still powers the LED");
                }));
    }

    private static void freshRefill(List<MachineScenario> cases, boolean emptyTags) {
        cases.add(new MachineScenario("electric/lifecycle/battery-fresh-" + (emptyTags ? "empty-tag" : "no-tag"),
                "Fresh mixed batteries historically start full, even with an empty compound and no energy key. "
                + "Natural LED use creates room and a finite generator refills it, persisting first-use charge "
                + "without creating energy or replacing any of the eight battery items.", TICKS, c -> {
                    c.block(6, 4, 8, EA + "steam_powered_generator");
                    c.power(6, 4, 8, ELECTRICITY, 20);
                    stockBank(c, null, emptyTags);
                    close(c, "setup.freshCapacity", bankCapacity(c), 30000);
                    close(c, "setup.freshCharge", c.energy(7, 4, 8, ELECTRICITY), 30000);
                    // LED phase 3, array phase 2, generator phase 0: discharge precedes the refill.
                    c.require(phase(c, 6, 4, 8) == 0 && phase(c, 7, 4, 8) == 2
                            && phase(c, 7, 3, 8) == 3, "Fixture retains the declared distinct native phases");
                    c.block(7, 3, 8, EA + "led_bar");
                }, c -> {
                    window(c, TICKS);
                    float[] charge = readBank(c, false);
                    float generatedReserve = c.energy(6, 4, 8, ELECTRICITY);
                    float led = c.energy(7, 3, 8, ELECTRICITY);
                    int firstSupply = Math.min(
                            ElectricOperationalRates.firstUpdateDelay(c.activationTick, phase(c, 6, 4, 8)),
                            ElectricOperationalRates.firstUpdateDelay(c.activationTick, phase(c, 7, 4, 8)));
                    int paidUpdates = 0;
                    for (int delay : ElectricOperationalRates.updateDelays(c.activationTick, phase(c, 7, 3, 8), TICKS))
                        if (delay > firstSupply) paidUpdates++;
                    float debit = paidUpdates * 0.125f;
                    c.record("fresh.expectedLightingDebit", debit);
                    c.record("fresh.sourceDebit", 20 - generatedReserve);
                    c.require(debit > 1.5f, "Window requires sustained fresh-battery use");
                    c.require(20 - generatedReserve > 0.5f, "Source supplied more than the LED's initial buffer");
                    c.require(30000 - sum(charge) <= 0.5f, "Real refills keep the batteries within one LED buffer of full");
                    close(c, "fresh.conservation", sum(charge) + generatedReserve + led, 30020 - debit);
                    for (int slot = 1; slot < 8; slot++) close(c, "fresh.slot" + slot, charge[slot], capacity(slot));
                    ItemStack first = c.inventory(7, 4, 8).getStackInSlot(0);
                    c.require(first.hasTagCompound() && first.getTagCompound().hasKey("energy", 5),
                            "First-use charge is now persisted in real item NBT");
                    c.require(c.world.getBlockState(c.at(7, 3, 8)).getValue(LIT), "Refilled stock sustains the LED");
                }));
    }

    private static void cableSwitch(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/lifecycle/cable-switch",
                "A 16-block-separated battery and oven work through actual electric cable and an unpowered switch. "
                + "A separate redstone-disabled switch blocks all transfer. One potato costs 400 of 500 electricity; "
                + "the blocked lane retains its potato and all 500 electricity.", 260, c -> {
                    cableLane(c, 8, false, false);
                    cableLane(c, 20, true, false);
                }, c -> {
                    window(c, 260);
                    for (int z : new int[]{8, 20}) {
                        boolean enabled = z == 8;
                        c.require(c.world.getBlockState(c.at(11, 4, z)).getValue(ACTIVE) == enabled,
                                "Switch state reflects its real redstone input in lane " + z);
                        itemCount(c, 19, 4, z, 0, "minecraft:potato", enabled ? 0 : 1);
                        itemCount(c, 3, 4, z, 0, EA + "lithium_battery", 1);
                        itemCount(c, 19, 4, z, 1, "minecraft:baked_potato", enabled ? 1 : 0);
                        float remaining = c.energy(3, 4, z, ELECTRICITY) + c.energy(19, 4, z, ELECTRICITY);
                        close(c, "lane" + z + ".remaining", remaining, enabled ? 100 : 500);
                        if (!enabled) close(c, "blocked.ovenEnergy", c.energy(19, 4, z, ELECTRICITY), 0);
                    }
                }));
    }

    private static void cableGap(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/lifecycle/cable-static-gap",
                "A single missing cable isolates a finite battery from a valid oven input. This is a static "
                + "broken-route control, not proof of interruption or reconnection during operation.", 260, c -> {
                    cableLane(c, 8, false, true);
                }, c -> {
                    window(c, 260);
                    c.require(c.world.isAirBlock(c.at(15, 4, 8)), "Declared one-block cable gap remains open");
                    c.require(c.world.getBlockState(c.at(11, 4, 8)).getValue(ACTIVE), "Switch is on, not hiding the cable gap");
                    itemCount(c, 19, 4, 8, 0, "minecraft:potato", 1);
                    itemCount(c, 3, 4, 8, 0, EA + "lithium_battery", 1);
                    itemCount(c, 19, 4, 8, 1, "minecraft:baked_potato", 0);
                    close(c, "gap.battery", c.energy(3, 4, 8, ELECTRICITY), 500);
                    close(c, "gap.oven", c.energy(19, 4, 8, ELECTRICITY), 0);
                }));
    }

    private static void cableLane(MachineContext c, int z, boolean disabled, boolean gap) {
        c.electricSupply(3, 4, z, 500);
        c.block(11, 4, z, EA + "electric_switch");
        for (int x = 4; x <= 18; x++)
            if (x != 11 && !(gap && x == 15)) c.block(x, 4, z, EA + "electric_conduit");
        if (gap) c.block(15, 4, z, "minecraft:air");
        c.block(19, 4, z, EA + "electric_oven");
        c.put(19, 4, z, 0, "minecraft:potato", 1);
        if (disabled) c.block(11, 5, z, "minecraft:redstone_block");
    }

    private static void solarControl(List<MachineScenario> cases) {
        cases.add(new MachineScenario("electric/lifecycle/solar-redstone",
                "Two equally lit roofed panels have actual level-14 incident block light. The unpowered "
                + "control generates at the retained blocklight rate; redstone inhibition must produce zero "
                + "from the first power update. No time or weather is changed.", TICKS, c -> {
                    c.lightHalo();
                    solarRoom(c, 8);
                    solarRoom(c, 22);
                    c.block(7, 2, 8, "minecraft:redstone_block");
                }, c -> {
                    window(c, TICKS);
                    for (int x : new int[]{8, 22}) {
                        c.require(!c.world.canSeeSky(c.at(x, 3, 8)), "Panel " + x + " is under a real roof");
                        c.require(c.world.getLight(c.at(x, 3, 8)) == 14, "Panel " + x + " has real incident light");
                    }
                    float perUpdate = (float)(Math.PI * 12500 / (24000 / Math.PI / 8)) * 0.1f * 14 * 0.0666666f;
                    float actual = c.energy(22, 2, 8, ELECTRICITY);
                    c.record("control.generated", actual);
                    c.require(actual >= perUpdate * 18 && actual <= perUpdate * 20 + 0.01f,
                            "Uninhibited panel actually generates at its native blocklight rate");
                    c.require(c.world.isBlockPowered(c.at(8, 2, 8)), "Negative panel has a real redstone signal");
                    close(c, "inhibited.generated", c.energy(8, 2, 8, ELECTRICITY), 0);
                }));
    }

    private static void solarRoom(MachineContext c, int x) {
        for (int dx = -2; dx <= 2; dx++) for (int y = 0; y <= 4; y++) for (int dz = -2; dz <= 2; dz++)
            if (Math.abs(dx) == 2 || Math.abs(dz) == 2 || y == 0 || y == 4)
                c.block(x + dx, y, 8 + dz, "minecraft:stone");
        c.block(x, 2, 8, EA + "photovoltaic_generator");
        c.block(x + 1, 3, 8, "minecraft:glowstone");
    }

    private static void stockBank(MachineContext c, float[] charge, boolean emptyTags) {
        c.block(7, 4, 8, EA + "electric_battery_array");
        for (int slot = 0; slot < 8; slot++) {
            c.put(7, 4, 8, slot, battery(slot), 1);
            ItemStack stack = c.inventory(7, 4, 8).getStackInSlot(slot);
            if (charge != null) {
                NBTTagCompound tag = NativeStacks.tag(stack);
                tag.setFloat("energy", charge[slot]);
                tag.setInteger("AdvWorksSlot", slot);
            } else if (emptyTags) {
                stack.setTagCompound(new NBTTagCompound());
            }
            if (charge == null) c.require(emptyTags ? stack.hasTagCompound() && !stack.getTagCompound().hasKey("energy")
                    : !stack.hasTagCompound(), "Fresh slot " + slot + " starts without a charge key");
        }
        c.inventory(7, 4, 8).markDirty();
    }

    private static float[] readBank(MachineContext c, boolean markers) {
        IInventory bank = c.inventory(7, 4, 8);
        c.require(bank.getSizeInventory() == 8, "Battery array retains all eight stock slots");
        float[] charge = new float[8];
        for (int slot = 0; slot < 8; slot++) {
            ItemStack stack = bank.getStackInSlot(slot);
            c.require(count(stack) == 1 && new ResourceLocation(battery(slot)).equals(stack.getItem().getRegistryName()),
                    "Slot " + slot + " retains its exact battery identity and quantity");
            NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : null;
            if (markers) c.require(tag != null && tag.hasKey("energy", 5)
                    && tag.hasKey("AdvWorksSlot", 3) && tag.getInteger("AdvWorksSlot") == slot,
                    "Slot " + slot + " retains its custom item NBT");
            c.require(tag == null || !tag.hasKey("energy") || tag.hasKey("energy", 5), "Stored charge retains float NBT");
            charge[slot] = tag != null && tag.hasKey("energy") ? tag.getFloat("energy") : capacity(slot);
            c.require(Float.isFinite(charge[slot]) && charge[slot] >= 0 && charge[slot] <= capacity(slot),
                    "Slot " + slot + " charge is finite and within its own capacity");
            c.record("battery.slot" + slot + ".charge", charge[slot]);
        }
        close(c, "bank.capacity", bankCapacity(c), 30000);
        close(c, "bank.itemLedger", c.energy(7, 4, 8, ELECTRICITY), sum(charge));
        return charge;
    }

    private static float bankCapacity(MachineContext c) {
        return ((PoweredEntity)c.tile(7, 4, 8)).getEnergyCapacity(new ConduitType(ELECTRICITY));
    }

    private static String battery(int slot) { return EA + CHEMISTRIES[slot % CHEMISTRIES.length] + "_battery"; }
    private static float capacity(int slot) { return CAPACITIES[slot % CAPACITIES.length]; }
    private static float sum(float[] values) { float total = 0; for (float value : values) total += value; return total; }
    private static int count(ItemStack stack) { return stack == null || stack.isEmpty() ? 0 : stack.getCount(); }

    private static void itemCount(MachineContext c, int x, int y, int z, int slot, String item, int expected) {
        ItemStack stack = c.inventory(x, y, z).getStackInSlot(slot);
        int quantity = count(stack);
        c.require(quantity == expected && (quantity == 0 || new ResourceLocation(item).equals(stack.getItem().getRegistryName())),
                "Slot " + slot + " at " + c.at(x, y, z) + " retains " + expected + " " + item);
    }

    private static int phase(MachineContext c, int x, int y, int z) {
        BlockPos pos = c.at(x, y, z);
        return ElectricOperationalRates.offset(pos.getX(), pos.getY(), pos.getZ());
    }

    private static void window(MachineContext c, int ticks) {
        long elapsed = c.world.getTotalWorldTime() - c.activationTick;
        c.record("observedTicks", elapsed);
        c.require(elapsed == ticks, "Supplemental job observes exactly " + ticks + " natural ticks");
    }

    private static void close(MachineContext c, String key, float actual, float expected) {
        c.record(key, actual);
        c.require(Float.isFinite(actual) && Math.abs(actual - expected) <= 0.001f,
                key + " expected " + expected + ", got " + actual);
    }
}
