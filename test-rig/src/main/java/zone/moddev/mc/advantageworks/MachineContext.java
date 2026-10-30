package zone.moddev.mc.advantageworks;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import cyano.poweradvantage.api.simple.TileEntitySimpleFluidMachine;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.IProperty;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;

final class MachineContext {
    public final ServerWorld world;
    final BlockPos origin;
    final Set<BlockPos> positions = new LinkedHashSet<>();
    final List<Map<String, Object>> actions = new ArrayList<>(), checks = new ArrayList<>();
    final Map<String, Object> measurements = new LinkedHashMap<>();
    boolean settingUp = true;
    long activationTick;
    int verified;

    MachineContext(ServerWorld world, int index) {
        this.world = world;
        origin = new BlockPos(50000 + index * 64, 80, 50000);
    }

    BlockPos at(int x, int y, int z) {
        if (x < 0 || x > 31 || y < -1 || y > 31 || z < 0 || z > 31) {
            throw new IllegalArgumentException("Operating fixture escaped its declared bay");
        }
        return origin.add(x, y, z);
    }

    private void mutation(String operation, BlockPos pos, Object value) {
        if (!settingUp) throw new IllegalStateException("Fixture mutation after setup: " + operation);
        positions.add(pos.toImmutable());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("operation", operation);
        row.put("pos", new int[]{pos.getX(), pos.getY(), pos.getZ()});
        row.put("value", value);
        actions.add(row);
    }

    void floor() {
        if (!settingUp) throw new IllegalStateException("Foundation construction after setup");
        for (int x = origin.getX() >> 4; x <= (origin.getX() + 31) >> 4; x++) {
            for (int z = origin.getZ() >> 4; z <= (origin.getZ() + 31) >> 4; z++) {
                world.forceChunk(x, z, true);
                world.getChunk(x, z);
            }
        }
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            world.setBlockState(at(x, -1, z), net.minecraft.block.Blocks.BEDROCK.getDefaultState(), 2);
        }
    }

    void block(int x, int y, int z, String id) {
        ResourceLocation name = new ResourceLocation(id);
        if (!ForgeRegistries.BLOCKS.containsKey(name)) throw new IllegalArgumentException("Missing block " + id);
        mutation("block", at(x, y, z), id);
        world.setBlockState(at(x, y, z), ForgeRegistries.BLOCKS.getValue(name).getDefaultState(), 3);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void facing(int x, int y, int z, String facing) {
        BlockPos pos = at(x, y, z);
        mutation("facing", pos, facing);
        BlockState state = world.getBlockState(pos);
        for (IProperty property : state.getProperties()) if (property.getName().equals("facing")) {
            java.util.Optional<?> parsed = property.parseValue(facing);
            if (!parsed.isPresent()) throw new IllegalArgumentException("Invalid facing " + facing);
            Comparable value = (Comparable) parsed.get();
            world.setBlockState(pos, state.with(property, value), 3);
            return;
        }
        throw new IllegalArgumentException("No facing on " + state);
    }

    TileEntity tile(int x, int y, int z) {
        BlockPos pos = at(x, y, z);
        positions.add(pos.toImmutable());
        TileEntity tile = world.getTileEntity(pos);
        if (tile == null) throw new IllegalArgumentException("Missing fixture tile at " + pos);
        return tile;
    }

    IInventory inventory(int x, int y, int z) {
        TileEntity value = tile(x, y, z);
        if (!(value instanceof IInventory)) throw new IllegalArgumentException("Not an inventory: " + value);
        return (IInventory) value;
    }

    void put(int x, int y, int z, int slot, String item, int count) {
        ResourceLocation id = new ResourceLocation(item);
        if (!ForgeRegistries.ITEMS.containsKey(id) || count < 0 || count > 64) throw new IllegalArgumentException("Invalid stock " + item);
        mutation("inventory", at(x, y, z), slot + ":" + item + ":" + count);
        IInventory inventory = inventory(x, y, z);
        if (slot < 0 || slot >= inventory.getSizeInventory()) throw new IllegalArgumentException("Invalid fixture slot");
        inventory.setInventorySlotContents(slot, count == 0 ? ItemStack.EMPTY : new ItemStack(ForgeRegistries.ITEMS.getValue(id), count));
        inventory.markDirty();
    }

    int count(int x, int y, int z, String item) {
        Item expected = ForgeRegistries.ITEMS.getValue(new ResourceLocation(item));
        if (!ForgeRegistries.ITEMS.containsKey(new ResourceLocation(item))) throw new IllegalArgumentException("Missing item " + item);
        IInventory inventory = inventory(x, y, z);
        int total = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() == expected) total += stack.getCount();
        }
        record("count/" + x + "/" + y + "/" + z + "/" + item, total);
        return total;
    }

    void power(int x, int y, int z, String type, float value) {
        mutation("energy", at(x, y, z), type + ":" + value);
        PoweredEntity tile = (PoweredEntity) tile(x, y, z);
        ConduitType conduit = new ConduitType(type);
        if (!Float.isFinite(value) || value < 0 || value > tile.getEnergyCapacity(conduit)) throw new IllegalArgumentException("Invalid reserve");
        tile.setEnergy(value, conduit);
        tile.markDirty();
    }

    float energy(int x, int y, int z, String type) {
        float energy = ((PoweredEntity) tile(x, y, z)).getEnergy(new ConduitType(type));
        if (!Float.isFinite(energy) || energy < 0) throw new AssertionError("Invalid live energy");
        record("energy/" + x + "/" + y + "/" + z + "/" + type, energy);
        return energy;
    }

    void steamSupply(int x, int y, int z, float reserve) {
        block(x, y, z, "steamadvantage:steam_tank");
        power(x, y, z, "steam", reserve);
    }

    void electricSupply(int x, int y, int z, float reserve) {
        if (!Float.isFinite(reserve) || reserve < 0 || reserve > 8000) throw new IllegalArgumentException("Invalid battery reserve");
        block(x, y, z, "electricadvantage:electric_battery_array");
        put(x, y, z, 0, "electricadvantage:lithium_battery", 1);
        inventory(x, y, z).getStackInSlot(0).getOrCreateTag().putFloat("energy", reserve);
        inventory(x, y, z).markDirty();
    }

    void tank(int x, int y, int z, String fluid, int amount) {
        ResourceLocation id = new ResourceLocation(fluid);
        if (!ForgeRegistries.FLUIDS.containsKey(id) || amount < 0) throw new IllegalArgumentException("Invalid fluid reserve");
        mutation("tank", at(x, y, z), fluid + ":" + amount);
        cyano.poweradvantage.api.fluid.FluidTank tank = ((TileEntitySimpleFluidMachine) tile(x, y, z)).getTank();
        if (amount > tank.getCapacity()) throw new IllegalArgumentException("Tank reserve exceeds capacity");
        tank.setFluid(new cyano.poweradvantage.api.fluid.FluidStack(ForgeRegistries.FLUIDS.getValue(id), amount));
        tile(x, y, z).markDirty();
    }

    int fluidAmount(int x, int y, int z) {
        int amount = ((TileEntitySimpleFluidMachine) tile(x, y, z)).getTank().getFluidAmount();
        record("fluid/" + x + "/" + y + "/" + z, amount);
        return amount;
    }

    CompoundNBT nbt(int x, int y, int z) { return tile(x, y, z).write(new CompoundNBT()); }

    void seed(int x, int y, int z, CompoundNBT patch) {
        mutation("nbt", at(x, y, z), patch.toString());
        TileEntity tile = tile(x, y, z);
        CompoundNBT nbt = tile.write(new CompoundNBT());
        nbt.merge(patch.copy());
        tile.read(nbt);
        tile.markDirty();
    }

    Object invoke(int x, int y, int z, String method, Class<?>[] types, Object... arguments) {
        TileEntity tile = tile(x, y, z);
        try { return tile.getClass().getMethod(method, types).invoke(tile, arguments); }
        catch (InvocationTargetException error) { throw new IllegalStateException("Fixture method failed: " + method, error.getCause()); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Missing fixture method: " + method, error); }
    }

    void require(boolean condition, String message) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("phase", settingUp ? "setup" : "verify");
        row.put("assertion", message);
        row.put("status", condition ? "PASS" : "FAIL");
        checks.add(row);
        if (!settingUp) verified++;
        if (!condition) throw new AssertionError(message);
    }

    void record(String key, Object value) { measurements.put(key, value); }

    JsonArray snapshot() {
        JsonArray rows = new JsonArray();
        for (BlockPos pos : positions) {
            JsonObject row = new JsonObject();
            row.addProperty("x", pos.getX()); row.addProperty("y", pos.getY()); row.addProperty("z", pos.getZ());
            BlockState state = world.getBlockState(pos);
            row.addProperty("block", state.getBlock().getRegistryName().toString());
            row.addProperty("state", state.toString());
            TileEntity tile = world.getTileEntity(pos);
            if (tile != null) row.addProperty("nbt", tile.write(new CompoundNBT()).toString());
            rows.add(row);
        }
        return rows;
    }

    JsonArray entities() {
        JsonArray result = new JsonArray();
        net.minecraft.util.math.AxisAlignedBB bounds = new net.minecraft.util.math.AxisAlignedBB(at(0, -1, 0), at(31, 31, 31)).grow(.5);
        for (net.minecraft.entity.Entity entity : world.getEntitiesWithinAABB(net.minecraft.entity.Entity.class, bounds)) {
            if (entity instanceof net.minecraft.entity.player.PlayerEntity) continue;
            CompoundNBT nbt = new CompoundNBT();
            if (!entity.writeUnlessRemoved(nbt)) throw new IllegalStateException("Test actor could not be saved: " + entity);
            JsonObject row = new JsonObject();
            row.addProperty("uuid", entity.getUniqueID().toString()); row.addProperty("nbt", nbt.toString());
            result.add(row);
        }
        return result;
    }
}
