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
import net.minecraft.block.state.IBlockState;
import net.minecraftforge.fluids.Fluid;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.block.properties.IProperty;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;


final class MachineContext {
    public final WorldServer world;
    final BlockPos origin;
    final Set<BlockPos> positions = new LinkedHashSet<>();
    final List<Map<String, Object>> actions = new ArrayList<>(), checks = new ArrayList<>();
    final Map<String, Object> measurements = new LinkedHashMap<>();
    boolean settingUp = true;
    long activationTick;
    int verified;
    private net.minecraftforge.common.ForgeChunkManager.Ticket ticket;
    private final Set<net.minecraft.util.math.ChunkPos> releasedChunks = new LinkedHashSet<>();

    private void holdChunk(int x, int z) {
        if (!settingUp) throw new IllegalStateException("Chunk acquisition after fixture setup");
        if (ticket == null) ticket = net.minecraftforge.common.ForgeChunkManager.requestTicket(
                AdvantageWorksMod.instance, world, net.minecraftforge.common.ForgeChunkManager.Type.NORMAL);
        if (ticket == null) throw new IllegalStateException("No fixture chunk ticket available");
        net.minecraftforge.common.ForgeChunkManager.forceChunk(ticket, new net.minecraft.util.math.ChunkPos(x, z));
        world.getChunkFromChunkCoords(x, z);
    }

    MachineContext(WorldServer world, int index) {
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


    static boolean ownsBayChunk(BlockPos origin, net.minecraft.util.math.ChunkPos chunk) {
        return chunk.chunkXPos >= (origin.getX() >> 4) && chunk.chunkXPos <= ((origin.getX() + 31) >> 4)
                && chunk.chunkZPos >= (origin.getZ() >> 4) && chunk.chunkZPos <= ((origin.getZ() + 31) >> 4);
    }

    net.minecraft.world.chunk.Chunk readOnlyLoadedChunk(net.minecraft.util.math.ChunkPos chunk) {
        if (!ownsBayChunk(origin, chunk)) throw new IllegalArgumentException("Chunk outside the declared fixture bay");
        // getLoadedChunk(x,z) cancels native queued unloading; values() does not.
        for (net.minecraft.world.chunk.Chunk loaded : world.getChunkProvider().getLoadedChunks()) {
            if (loaded.xPosition == chunk.chunkXPos && loaded.zPosition == chunk.chunkZPos) return loaded;
        }
        return null;
    }

    void unforceOwnedChunk(net.minecraft.util.math.ChunkPos chunk) {
        if (settingUp || !ownsBayChunk(origin, chunk) || ticket == null || !ticket.getChunkList().contains(chunk))
            throw new IllegalStateException("Cannot release an unowned fixture chunk");
        java.util.Collection<net.minecraftforge.common.ForgeChunkManager.Ticket> owners =
                net.minecraftforge.common.ForgeChunkManager.getPersistentChunksFor(world).get(chunk);
        if (owners.size() != 1 || !owners.contains(ticket)) throw new IllegalStateException("Another ticket holds the selected fixture chunk");
        releasedChunks.add(chunk);
        net.minecraftforge.common.ForgeChunkManager.unforceChunk(ticket, chunk);
        if (net.minecraftforge.common.ForgeChunkManager.getPersistentChunksFor(world).containsKey(chunk))
            throw new IllegalStateException("Selected chunk remains forced");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("operation", "unforce-owned-chunk"); row.put("chunk", new int[]{chunk.chunkXPos, chunk.chunkZPos});
        row.put("worldTime", world.getTotalWorldTime()); actions.add(row);
    }

    void restoreOwnedChunk(net.minecraft.util.math.ChunkPos chunk) {
        if (!ownsBayChunk(origin, chunk) || ticket == null || !releasedChunks.contains(chunk))
            throw new IllegalStateException("Cannot restore a chunk not released by this context");
        net.minecraftforge.common.ForgeChunkManager.forceChunk(ticket, chunk);
        if (!ticket.getChunkList().contains(chunk)) throw new IllegalStateException("Fixture ticket was not restored");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("operation", "restore-owned-chunk"); row.put("chunk", new int[]{chunk.chunkXPos, chunk.chunkZPos});
        row.put("worldTime", world.getTotalWorldTime()); actions.add(row);
        world.getChunkFromChunkCoords(chunk.chunkXPos, chunk.chunkZPos);
        releasedChunks.remove(chunk);
    }

    void prepareChunks() {
        if (!settingUp) throw new IllegalStateException("Chunk preparation after setup");
        for (int x = origin.getX() >> 4; x <= (origin.getX() + 31) >> 4; x++) {
            for (int z = origin.getZ() >> 4; z <= (origin.getZ() + 31) >> 4; z++) {
                holdChunk(x, z);
            }
        }
    }

    boolean chunksReady() {
        for (int x = origin.getX() >> 4; x <= (origin.getX() + 31) >> 4; x++)
            for (int z = origin.getZ() >> 4; z <= (origin.getZ() + 31) >> 4; z++)
                if (!world.getChunkFromChunkCoords(x, z).isLoaded()) return false;
        return true;
    }

    void floor() {
        if (!settingUp) throw new IllegalStateException("Foundation construction after setup");
        if (!chunksReady()) throw new IllegalStateException("Construction before native chunk load completed");
        for (int x = 0; x < 32; x++) for (int z = 0; z < 32; z++) {
            world.setBlockState(at(x, -1, z), net.minecraft.init.Blocks.BEDROCK.getDefaultState(), 2);
        }
    }

    void lightHalo() {
        if (!settingUp) throw new IllegalStateException("Light neighbourhood loading after setup");
        // World.checkLightFor refuses to propagate without its native 16/18-block neighbourhood.
        for (int x = (origin.getX() >> 4) - 1; x <= ((origin.getX() + 31) >> 4) + 1; x++)
            for (int z = (origin.getZ() >> 4) - 1; z <= ((origin.getZ() + 31) >> 4) + 1; z++) {
                holdChunk(x, z);
            }
    }

    void validateTickRegistration() {
        for (BlockPos pos : positions) {
            TileEntity tile = world.getTileEntity(pos);
            if (!(tile instanceof net.minecraft.util.ITickable)) continue;
            int references = 0;
            for (TileEntity ticking : world.tickableTileEntities) if (ticking == tile) references++;
            require(references == 1, "Exactly one native tick registration at " + pos + "; observed " + references);
        }
    }

    void block(int x, int y, int z, String id) {
        ResourceLocation name = new ResourceLocation(id);
        if (!net.minecraft.block.Block.REGISTRY.containsKey(name)) throw new IllegalArgumentException("Missing block " + id);
        mutation("block", at(x, y, z), id);
        world.setBlockState(at(x, y, z), net.minecraft.block.Block.REGISTRY.getObject(name).getDefaultState(), 3);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    void facing(int x, int y, int z, String facing) {
        BlockPos pos = at(x, y, z);
        mutation("facing", pos, facing);
        IBlockState state = world.getBlockState(pos);
        for (IProperty property : state.getPropertyKeys()) if (property.getName().equals("facing")) {
            com.google.common.base.Optional<?> parsed = property.parseValue(facing);
            if (!parsed.isPresent()) throw new IllegalArgumentException("Invalid facing " + facing);
            Comparable value = (Comparable) parsed.get();
            world.setBlockState(pos, state.withProperty(property, value), 3);
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
        if (!net.minecraft.item.Item.REGISTRY.containsKey(id) || count < 0 || count > 64) throw new IllegalArgumentException("Invalid stock " + item);
        mutation("inventory", at(x, y, z), slot + ":" + item + ":" + count);
        IInventory inventory = inventory(x, y, z);
        if (slot < 0 || slot >= inventory.getSizeInventory()) throw new IllegalArgumentException("Invalid fixture slot");
        inventory.setInventorySlotContents(slot, count == 0 ? null : new ItemStack(net.minecraft.item.Item.REGISTRY.getObject(id), count));
        inventory.markDirty();
    }

    int count(int x, int y, int z, String item) {
        Item expected = net.minecraft.item.Item.REGISTRY.getObject(new ResourceLocation(item));
        if (!net.minecraft.item.Item.REGISTRY.containsKey(new ResourceLocation(item))) throw new IllegalArgumentException("Missing item " + item);
        IInventory inventory = inventory(x, y, z);
        int total = 0;
        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!(stack == null || stack.stackSize <= 0) && stack.getItem() == expected) total += stack.stackSize;
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
        NativeStacks.tag(inventory(x, y, z).getStackInSlot(0)).setFloat("energy", reserve);
        inventory(x, y, z).markDirty();
    }

    void tank(int x, int y, int z, String fluid, int amount) {
        net.minecraftforge.fluids.Fluid registered = NativeFluids.get(fluid);
        if (amount < 0) throw new IllegalArgumentException("Invalid fluid reserve");
        mutation("tank", at(x, y, z), fluid + ":" + amount);
        net.minecraftforge.fluids.FluidTank tank = ((TileEntitySimpleFluidMachine) tile(x, y, z)).getTank();
        if (amount > tank.getCapacity()) throw new IllegalArgumentException("Tank reserve exceeds capacity");
        tank.setFluid(new net.minecraftforge.fluids.FluidStack(registered, amount));
        tile(x, y, z).markDirty();
    }

    int fluidAmount(int x, int y, int z) {
        int amount = ((TileEntitySimpleFluidMachine) tile(x, y, z)).getTank().getFluidAmount();
        record("fluid/" + x + "/" + y + "/" + z, amount);
        return amount;
    }

    NBTTagCompound nbt(int x, int y, int z) { return tile(x, y, z).writeToNBT(new NBTTagCompound()); }

    void seed(int x, int y, int z, NBTTagCompound patch) {
        mutation("nbt", at(x, y, z), patch.toString());
        TileEntity tile = tile(x, y, z);
        NBTTagCompound nbt = tile.writeToNBT(new NBTTagCompound());
        nbt.merge(patch.copy());
        tile.readFromNBT(nbt);
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
            IBlockState state = world.getBlockState(pos);
            row.addProperty("block", state.getBlock().getRegistryName().toString());
            row.addProperty("state", state.toString());
            TileEntity tile = world.getTileEntity(pos);
            if (tile != null) {
                NBTTagCompound saved = tile.writeToNBT(new NBTTagCompound());
                row.addProperty("nbt", saved.toString());
                row.addProperty("nbtBase64", NativeNbt.encode(saved));
            }
            rows.add(row);
        }
        return rows;
    }

    JsonArray entities() {
        JsonArray result = new JsonArray();
        net.minecraft.util.math.AxisAlignedBB bounds = new net.minecraft.util.math.AxisAlignedBB(at(0, -1, 0), at(31, 31, 31)).expand(.5, .5, .5);
        for (net.minecraft.entity.Entity entity : world.getEntitiesWithinAABB(net.minecraft.entity.Entity.class, bounds)) {
            if (entity instanceof net.minecraft.entity.player.EntityPlayer) continue;
            NBTTagCompound nbt = new NBTTagCompound();
            if (!entity.writeToNBTOptional(nbt)) throw new IllegalStateException("Test actor could not be saved: " + entity);
            JsonObject row = new JsonObject();
            row.addProperty("uuid", entity.getUniqueID().toString()); row.addProperty("nbt", nbt.toString());
            row.addProperty("nbtBase64", NativeNbt.encode(nbt));
            result.add(row);
        }
        return result;
    }
}
