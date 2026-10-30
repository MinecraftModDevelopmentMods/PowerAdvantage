package zone.moddev.mc.advantageworks;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import java.util.List;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

/** Finite track moves on native world ticks; no direct calls to drill update/movement methods. */
final class DrillOperationalCases {
    private static final int TICKS = 80;
    private static final int X = 12, Y = 8, Z = 12;
    private static final float STEAM_LEAK_PER_TICK = 0.0625F;

    private DrillOperationalCases() {}

    static void register(List<MachineScenario> cases) {
        for (String district : new String[]{"steam", "electric"}) {
            for (EnumFacing facing : EnumFacing.values()) {
                for (String mode : new String[]{"move", "no-power", "redstone-blocked"}) {
                    scenario(cases, district, facing, mode);
                }
            }
        }
    }

    private static final class Witness {
        NBTTagCompound[] slots;
        NBTTagCompound audit;
        String customName, tileId;
        int inventorySize;
    }

    private static void scenario(List<MachineScenario> cases, String district, EnumFacing facing, String mode) {
        boolean steam = district.equals("steam");
        boolean move = mode.equals("move"), noPower = mode.equals("no-power");
        String namespace = steam ? "steamadvantage" : "electricadvantage";
        String drill = namespace + ":" + district + "_drill";
        String track = namespace + ":" + district + "_track";
        String conduit = namespace + (steam ? ":steam_pipe" : ":electric_conduit");
        String energyType = steam ? "steam" : "electricity";
        // The single track is perpendicular to the bore, never in its input/output path.
        EnumFacing travel = facing.getAxis() == EnumFacing.Axis.X ? EnumFacing.SOUTH : EnumFacing.EAST;
        BlockPos source = new BlockPos(X, Y, Z), destination = source.offset(travel);
        BlockPos front = source.offset(facing), nextFront = destination.offset(facing);
        BlockPos hold = destination.offset(travel);
        float reserve = noPower ? 0 : steam ? 50 : 500;
        float moveCost = steam ? 10 : 250;
        Witness witness = new Witness();
        String id = district + "/drill-track-" + facing.getName() + "-" + mode;
        cases.add(new MachineScenario(id,
                "A " + facing.getName() + "-facing " + district + " drill takes one perpendicular track step on natural ticks, "
                + "retaining every inventory slot, item/tile custom NBT and facing, leaving a conduit and steel frame, "
                + "and paying exactly its movement cost. Bedrock stops the bore and a destination redstone hold prevents "
                + "further work; no-power and source-redstone controls must not move or spend movement energy.",
                TICKS, c -> {
                    c.block(X, Y, Z, drill);
                    c.facing(X, Y, Z, facing.getName());
                    block(c, destination, track);
                    block(c, front, "minecraft:bedrock");
                    block(c, nextFront, "minecraft:bedrock");
                    block(c, hold, "minecraft:redstone_block");
                    if (mode.equals("redstone-blocked")) block(c, source.offset(travel.getOpposite()), "minecraft:redstone_block");
                    c.require(c.world.isBlockPowered(at(c, source)) == mode.equals("redstone-blocked"),
                            "Only the redstone-blocked control has a powered source position");
                    c.require(c.world.isBlockPowered(at(c, destination)),
                            "The destination has a real redstone hold before the move");
                    PoweredEntity original = (PoweredEntity) c.tile(X, Y, Z);
                    try {
                        c.require(original.getClass().getField("ENERGY_COST_MOVE").getFloat(null) == moveCost,
                                "Native rated movement cost is " + moveCost + " " + energyType);
                    } catch (ReflectiveOperationException error) {
                        throw new IllegalStateException("Native drill movement rating unavailable", error);
                    }
                    witness.inventorySize = c.inventory(X, Y, Z).getSizeInventory();
                    c.require(witness.inventorySize == (steam ? 5 : 6), "Native drill inventory is available in full");
                    c.put(X, Y, Z, 0, "minecraft:dirt", 11);
                    c.put(X, Y, Z, witness.inventorySize - 1, "minecraft:cobblestone", 7);
                    for (int slot : new int[]{0, witness.inventorySize - 1}) {
                        NBTTagCompound itemTag = NativeStacks.tag(c.inventory(X, Y, Z).getStackInSlot(slot));
                        itemTag.setString("AdvantageWorksPayload", id + "/slot-" + slot);
                        itemTag.setIntArray("Witness", new int[]{slot, 11, 7, facing.getIndex()});
                    }
                    witness.customName = "Drill witness " + id;
                    witness.audit = new NBTTagCompound();
                    witness.audit.setString("case", id);
                    witness.audit.setIntArray("origin", new int[]{at(c, source).getX(), at(c, source).getY(), at(c, source).getZ()});
                    NBTTagCompound forgeData = new NBTTagCompound();
                    forgeData.setTag("AdvantageWorksDrill", witness.audit.copy());
                    NBTTagCompound patch = new NBTTagCompound();
                    patch.setString("CustomName", witness.customName);
                    patch.setTag("ForgeData", forgeData);
                    c.seed(X, Y, Z, patch);
                    c.power(X, Y, Z, energyType, reserve);
                    NBTTagCompound saved = c.nbt(X, Y, Z);
                    witness.tileId = saved.getString("id");
                    c.require(!witness.tileId.isEmpty(), "Original drill has a persistent tile identity");
                    witness.slots = inventorySnapshot(c.inventory(X, Y, Z));
                    c.require(itemCount(c.inventory(X, Y, Z)) == 18, "Exactly eighteen tagged cargo items are seeded");
                    c.require(drops(c).isEmpty(), "No dropped resources exist before natural operation");
                    c.record("drill/source", xyz(at(c, source)));
                    c.record("drill/destination", xyz(at(c, destination)));
                    c.record("drill/frontFacing", facing.getName());
                    c.record("drill/trackDirection", travel.getName());
                    c.record("drill/initialEnergy", reserve);
                    c.record("drill/movementCost", moveCost);
                }, c -> {
                    BlockPos active = move ? destination : source;
                    requireBlock(c, source, move ? conduit : drill, "Source becomes the correct conduit only after a successful move");
                    requireBlock(c, destination, move ? drill : track, "Destination contains the moved drill, or the untouched single track in a control");
                    requireBlock(c, front, move ? "poweradvantage:steel_frame" : "minecraft:bedrock",
                            "The old bore mouth becomes one steel frame only on movement");
                    requireBlock(c, nextFront, "minecraft:bedrock", "Destination bedrock stop is never mined");
                    requireBlock(c, hold, "minecraft:redstone_block", "Destination hold remains physically present");
                    c.require(!move || c.world.isBlockPowered(at(c, active)), "Moved drill is held by real redstone");
                    if (mode.equals("redstone-blocked")) c.require(c.world.isBlockPowered(at(c, source)), "Blocked control remains inhibited");

                    TileEntity tile = c.tile(active.getX(), active.getY(), active.getZ());
                    c.require(tile instanceof PoweredEntity && !tile.isInvalid(), "Live destination/source drill is a valid powered tile");
                    c.require(tile.getPos().equals(at(c, active)), "Tile coordinates match the actual occupied block, not the old location");
                    IBlockState state = c.world.getBlockState(at(c, active));
                    c.require(readFacing(state) == facing, "Physical block retains its six-way facing");
                    IBlockState restored = state.getBlock().getStateFromMeta(state.getBlock().getMetaFromState(state));
                    c.require(readFacing(restored) == facing, "Facing survives the native metadata save/load codec");

                    NBTTagCompound saved = c.nbt(active.getX(), active.getY(), active.getZ());
                    c.require(saved.getString("id").equals(witness.tileId), "Tile registry identity survives the move");
                    c.require(saved.getInteger("x") == at(c, active).getX()
                            && saved.getInteger("y") == at(c, active).getY()
                            && saved.getInteger("z") == at(c, active).getZ(), "Serialized tile coordinates are rebased at the real destination");
                    c.require(saved.getString("CustomName").equals(witness.customName), "Custom inventory name is retained");
                    c.require(saved.getCompoundTag("ForgeData").getCompoundTag("AdvantageWorksDrill").equals(witness.audit),
                            "Unrelated nested Forge tile NBT survives untouched");
                    if (steam) c.require(saved.hasKey("Facing", 1) && saved.getByte("Facing") == facing.getIndex(),
                            "Steam drill also retains its legacy NBT facing");
                    c.require(saved.getShort("progress") == 0 && !saved.hasKey("targetX")
                            && !saved.hasKey("targetY") && !saved.hasKey("targetZ"),
                            "Bounded move/control has no fabricated mining progress or target");

                    IInventory inventory = c.inventory(active.getX(), active.getY(), active.getZ());
                    c.require(inventory.getSizeInventory() == witness.inventorySize, "Inventory size is unchanged");
                    NBTTagCompound[] actual = inventorySnapshot(inventory);
                    for (int slot = 0; slot < witness.slots.length; slot++) {
                        c.require(witness.slots[slot].equals(actual[slot]), "Exact item identity, quantity and custom NBT retained in slot " + slot);
                    }
                    c.require(itemCount(inventory) == 18, "All eighteen finite cargo items remain, with no mining products");
                    TileEntity other = c.world.getTileEntity(at(c, move ? source : destination));
                    c.require(!(other instanceof IInventory) || itemCount((IInventory) other) == 0,
                            "Cargo is not duplicated into the vacated source or untouched track");
                    int dropped = 0;
                    for (EntityItem item : drops(c)) dropped += count(item.getEntityItem());
                    c.record("drill/droppedResources", dropped);
                    c.require(dropped == 0 && drops(c).isEmpty(), "Moving never spills or duplicates cargo as dropped entities");
                    int drills = 0, tracks = 0;
                    for (int x = X - 2; x <= X + 2; x++) for (int y = Y - 2; y <= Y + 2; y++) for (int z = Z - 2; z <= Z + 2; z++) {
                        String block = String.valueOf(c.world.getBlockState(c.at(x, y, z)).getBlock().getRegistryName());
                        if (block.equals(drill)) drills++;
                        if (block.equals(track)) tracks++;
                    }
                    c.require(drills == 1 && tracks == (move ? 0 : 1), "Exactly one drill remains and only a successful step consumes the sole track");
                    float energy = ((PoweredEntity) tile).getEnergy(new ConduitType(energyType));
                    float expected = reserve - (move ? moveCost : 0) - (steam && !noPower ? TICKS * STEAM_LEAK_PER_TICK : 0);
                    c.record("drill/finalEnergy", energy);
                    c.record("drill/expectedEnergy", expected);
                    c.require(Float.isFinite(energy) && energy == expected,
                            "Live drill retains exact reserve minus one successful movement debit and rated per-tick steam leakage");
                }));
    }

    private static int count(ItemStack stack) { return NativeStacks.count(stack); }

    private static int itemCount(IInventory inventory) {
        int total = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) total += count(inventory.getStackInSlot(slot));
        return total;
    }

    private static NBTTagCompound[] inventorySnapshot(IInventory inventory) {
        NBTTagCompound[] result = new NBTTagCompound[inventory.getSizeInventory()];
        for (int slot = 0; slot < result.length; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            result[slot] = count(stack) == 0 ? new NBTTagCompound() : stack.writeToNBT(new NBTTagCompound());
        }
        return result;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static EnumFacing readFacing(IBlockState state) {
        for (IProperty property : state.getPropertyKeys()) if (property.getName().equals("facing")) {
            return (EnumFacing) state.getValue(property);
        }
        throw new IllegalStateException("Drill has no native facing property: " + state);
    }

    private static List<EntityItem> drops(MachineContext c) {
        BlockPos min = c.at(0, 0, 0), max = c.at(31, 31, 31);
        return c.world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(min.getX(), min.getY(), min.getZ(),
                max.getX() + 1, max.getY() + 1, max.getZ() + 1));
    }

    private static void block(MachineContext c, BlockPos local, String id) {
        c.block(local.getX(), local.getY(), local.getZ(), id);
    }

    private static BlockPos at(MachineContext c, BlockPos local) {
        return c.at(local.getX(), local.getY(), local.getZ());
    }

    private static int[] xyz(BlockPos pos) { return new int[]{pos.getX(), pos.getY(), pos.getZ()}; }

    private static void requireBlock(MachineContext c, BlockPos local, String id, String message) {
        BlockPos pos = at(c, local);
        c.require(id.equals(String.valueOf(c.world.getBlockState(pos).getBlock().getRegistryName())), message + " at " + pos);
    }
}
