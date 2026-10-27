package zone.moddev.mc.advantageworks;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.modsupport.rf.TileEntityRFConverter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.Commands;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.poweradvantage.machines.conveyors.BlockConveyor;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityConveyor;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.StorageTankTileEntity;
import zone.moddev.mc.poweradvantage.util.InventoryLocks;

/** Capability and native loot witnesses, restricted to a marked disposable world. */
final class NativeInteropProbe {
    private static final File MARKER = new File("advantage-native-interop.allow");
    private static final BlockPos TANK = new BlockPos(1024, 8, 1024);
    private static final BlockPos BELT = new BlockPos(1040, 8, 1024);

    static void register() {
        if (MARKER.isFile()) MinecraftForge.EVENT_BUS.addListener(new NativeInteropProbe()::starting);
    }

    private void starting(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("advinterop")
                .requires(source -> source.hasPermission(4))
                .then(Commands.argument("phase", StringArgumentType.word()).executes(context -> {
                    String phase = StringArgumentType.getString(context, "phase");
                    try { run(context.getSource().getLevel(), phase); }
                    catch (Throwable error) { throw new IllegalStateException("Native interop probe failed", error); }
                    System.out.println("ADVINTEROP COMPLETE " + phase);
                    return 1;
                })));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void check(JsonArray checks, String id, Runnable body) {
        JsonObject row = new JsonObject();
        row.addProperty("id", id);
        try { body.run(); row.addProperty("status", "PASS"); }
        catch (AssertionError | RuntimeException error) {
            row.addProperty("status", "FAIL");
            row.addProperty("failure", error.toString());
            java.io.StringWriter trace = new java.io.StringWriter();
            error.printStackTrace(new java.io.PrintWriter(trace));
            row.addProperty("traceback", trace.toString());
        }
        checks.add(row);
    }

    private static Block block(String id) {
        ResourceLocation key = new ResourceLocation(id);
        require(ForgeRegistries.BLOCKS.containsKey(key), "Missing registry entry: " + id);
        return ForgeRegistries.BLOCKS.getValue(key);
    }

    private static void place(ServerWorld world, BlockPos pos, BlockState state) {
        require(pos.getX() >= 1023 && pos.getX() <= 1100 && pos.getY() == 8
                && pos.getZ() >= 1023 && pos.getZ() <= 1030, "Outside interop fixture bounds");
        world.setBlock(pos, net.minecraft.block.Blocks.AIR.defaultBlockState(), 3);
        world.setBlock(pos, state, 3);
    }

    private static IFluidHandler fluid(TileEntity tile) {
        return tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, Direction.UP)
                .orElseThrow(() -> new AssertionError("Missing native fluid capability"));
    }

    private static void energy(ServerWorld world, JsonArray checks) {
        String[] ids = {"converter_rf_steam", "converter_rf_electricity", "converter_rf_quantum", "converter_tr_electricity"};
        String[] types = {"steam", "electricity", "quantum", "electricity"};
        for (int index = 0; index < ids.length; index++) {
            final String id = ids[index];
            final ConduitType type = new ConduitType(types[index]);
            final BlockPos pos = new BlockPos(1060 + index * 8, 8, 1024);
            place(world, pos, block("poweradvantage:" + id).defaultBlockState());
            TileEntityRFConverter tile = (TileEntityRFConverter) world.getBlockEntity(pos);
            check(checks, id + "/capability", () -> {
                IEnergyStorage storage = tile.getCapability(CapabilityEnergy.ENERGY, Direction.NORTH)
                        .orElseThrow(() -> new AssertionError("Missing Forge Energy capability"));
                require(storage == tile, "Capability does not expose the live converter");
                require(storage.receiveEnergy(-1, false) == 0 && storage.extractEnergy(-1, false) == 0,
                        "Negative transfers accepted");
                require(storage.receiveEnergy(123, true) == 123 && storage.getEnergyStored() == 0,
                        "Simulation changed converter storage");
                require(storage.receiveEnergy(123, false) == 123 && storage.getEnergyStored() == 123,
                        "Native receive lost energy");
                require(storage.extractEnergy(23, true) == 23 && storage.getEnergyStored() == 123,
                        "Extraction simulation changed storage");
                require(storage.extractEnergy(23, false) == 23 && storage.getEnergyStored() == 100,
                        "Native extraction lost energy");
                require(storage.receiveEnergy(Integer.MAX_VALUE, false) == storage.getMaxEnergyStored() - 100,
                        "Receive exceeded capacity");
                require(storage.extractEnergy(Integer.MAX_VALUE, false) == storage.getMaxEnergyStored()
                        && storage.getEnergyStored() == 0, "Extraction exceeded stored energy");
            });
            check(checks, id + "/forge-to-advantage", () -> {
                tile.setEnergy(0, type);
                tile.setOtherEnergy(100);
                double before = tile.getOtherEnergy();
                tile.powerUpdate();
                double after = tile.getOtherEnergy() + tile.convertEnergyToOther(tile.getEnergy(type), type);
                require(tile.getEnergy(type) > 0 && Math.abs(before - after) < 0.001,
                        "Conversion lost energy: before=" + before + ", after=" + after
                                + ", advantage=" + tile.getEnergy(type));
            });
            check(checks, id + "/advantage-to-forge", () -> {
                tile.setEnergy(tile.getEnergyCapacity(type) * 0.75f, type);
                tile.setOtherEnergy(0);
                double before = tile.convertEnergyToOther(tile.getEnergy(type), type);
                tile.powerUpdate();
                double after = tile.getOtherEnergy() + tile.convertEnergyToOther(tile.getEnergy(type), type);
                require(tile.getOtherEnergy() > 0 && Math.abs(before - after) < 0.001,
                        "Reverse conversion lost energy: before=" + before + ", after=" + after);
            });
            check(checks, id + "/invalidation", () -> {
                LazyOptional<IEnergyStorage> old = tile.getCapability(CapabilityEnergy.ENERGY, Direction.NORTH);
                require(old.isPresent(), "Missing original capability");
                world.setBlock(pos, net.minecraft.block.Blocks.AIR.defaultBlockState(), 3);
                require(!old.isPresent(), "Removed converter still exposes its stale capability");
                world.setBlock(pos, block("poweradvantage:" + id).defaultBlockState(), 3);
                LazyOptional<IEnergyStorage> replacement = world.getBlockEntity(pos).getCapability(CapabilityEnergy.ENERGY, Direction.NORTH);
                require(replacement.isPresent() && replacement != old, "Replacement did not expose a fresh capability");
            });
        }
    }

    private static void tank(ServerWorld world, JsonArray checks) {
        Block block = block("poweradvantage:fluid_storage_tank");
        place(world, TANK, block.defaultBlockState());
        TileEntity tile = world.getBlockEntity(TANK);
        check(checks, "fluid/simulation-conservation-and-identity", () -> {
            IFluidHandler tank = fluid(tile);
            FluidStack offered = new FluidStack(net.minecraft.fluid.Fluids.WATER, 1000);
            require(tank.fill(offered, IFluidHandler.FluidAction.SIMULATE) == 1000
                    && tank.getFluidInTank(0).isEmpty(), "Simulated fill changed contents");
            require(tank.fill(offered, IFluidHandler.FluidAction.EXECUTE) == 1000 && offered.getAmount() == 1000,
                    "Native fill amount or input immutability failed");
            require(tank.fill(new FluidStack(net.minecraft.fluid.Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 0,
                    "Different fluids mixed");
            require(tank.drain(500, IFluidHandler.FluidAction.SIMULATE).getAmount() == 500
                    && tank.getFluidInTank(0).getAmount() == 1000, "Simulated drain changed contents");
            FluidStack drained = tank.drain(500, IFluidHandler.FluidAction.EXECUTE);
            require(drained.getAmount() == 500 && drained.getFluid() == net.minecraft.fluid.Fluids.WATER
                    && tank.getFluidInTank(0).getAmount() == 500, "Drain lost fluid or identity");
        });
        check(checks, "portable-tank/native-break-drop-and-replace", () -> {
            LazyOptional<IFluidHandler> old = tile.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, Direction.UP);
            require(world.destroyBlock(TANK, true), "Native block destruction failed");
            require(!old.isPresent(), "Removed tank still exposes a stale capability");
            List<ItemEntity> drops = world.getEntitiesOfClass(ItemEntity.class, new AxisAlignedBB(TANK).inflate(2));
            require(drops.size() == 1, "Expected exactly one portable tank drop; got " + drops.size());
            ItemStack item = drops.get(0).getItem().copy();
            require(item.getItem() == block.asItem() && item.getCount() == 1 && item.hasTag()
                    && item.getTag().getString("FluidID").equals("minecraft:water")
                    && item.getTag().getInt("Volume") == 500, "Native loot lost portable fluid NBT: " + item);
            drops.get(0).remove();
            world.setBlock(TANK, block.defaultBlockState(), 3);
            block.setPlacedBy(world, TANK, block.defaultBlockState(),
                    net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world), item);
            require(fluid(world.getBlockEntity(TANK)).getFluidInTank(0).getAmount() == 500,
                    "Replacing native drop did not restore exactly 500 mB");
        });
    }

    private static void locks(ServerWorld world, JsonArray checks) {
        place(world, BELT.west(), net.minecraft.block.Blocks.CHEST.defaultBlockState());
        place(world, BELT, block("poweradvantage:item_conveyor").defaultBlockState().setValue(BlockConveyor.FACING, Direction.EAST));
        place(world, BELT.east(), net.minecraft.block.Blocks.CHEST.defaultBlockState());
        IInventory input = (IInventory) world.getBlockEntity(BELT.west());
        IInventory output = (IInventory) world.getBlockEntity(BELT.east());
        input.setItem(0, new ItemStack(net.minecraft.item.Items.DIRT, 16));
        TileEntityConveyor belt = (TileEntityConveyor) world.getBlockEntity(BELT);
        TileEntity chest = world.getBlockEntity(BELT.west());
        CompoundNBT state = chest.save(new CompoundNBT());
        state.putString("Lock", "AdvantageInteropKey");
        chest.load(chest.getBlockState(), state);
        check(checks, "locked-chest/conveyor-refuses-extraction", () -> {
            require(InventoryLocks.isLocked(input), "Native nonempty chest lock was not detected");
            for (int i = 0; i < 160; i++) belt.tick();
            require(input.getItem(0).getCount() == 16 && output.isEmpty() && belt.isEmpty(),
                    "Conveyor bypassed locked source");
        });
        state.remove("Lock");
        chest.load(chest.getBlockState(), state);
        check(checks, "unlocked-chest/conveyor-transfers-exactly", () -> {
            require(!InventoryLocks.isLocked(input), "Cleared lock still blocks extraction");
            for (int i = 0; i < 160; i++) belt.tick();
            require(input.isEmpty() && belt.isEmpty() && output.getItem(0).getCount() == 16
                    && output.getItem(0).getItem() == net.minecraft.item.Items.DIRT,
                    "Unlocked native chest did not deliver exactly sixteen dirt");
        });
    }

    private static void run(ServerWorld world, String phase) throws Exception {
        require(MARKER.isFile() && world.getServer().getWorldData().getLevelName().equals("AdvantageWorksMigration"), "Unmarked world");
        require(phase.equals("exercise") || phase.equals("restored"), "Unknown interop phase");
        JsonArray checks = new JsonArray();
        if (phase.equals("exercise")) {
            energy(world, checks);
            tank(world, checks);
            locks(world, checks);
        } else {
            check(checks, "portable-tank/full-process-restart", () -> {
                FluidStack stack = fluid(world.getBlockEntity(TANK)).getFluidInTank(0);
                require(stack.getFluid() == net.minecraft.fluid.Fluids.WATER && stack.getAmount() == 500,
                        "Restart lost portable tank contents");
            });
            check(checks, "conveyor/full-process-restart", () -> {
                IInventory output = (IInventory) world.getBlockEntity(BELT.east());
                require(output.getItem(0).getItem() == net.minecraft.item.Items.DIRT
                        && output.getItem(0).getCount() == 16, "Restart changed exact conveyor output");
            });
        }
        JsonObject report = new JsonObject();
        report.addProperty("phase", phase);
        report.addProperty("worldTime", world.getGameTime());
        report.addProperty("status", java.util.stream.StreamSupport.stream(checks.spliterator(), false)
                .allMatch(row -> row.getAsJsonObject().get("status").getAsString().equals("PASS")) ? "PASS" : "FAIL");
        report.add("checks", checks);
        File destination = new File("advantage-works-results", "native-interop-" + phase + ".json");
        require(!destination.exists(), "Refusing to overwrite interop evidence");
        destination.getParentFile().mkdirs();
        Files.write(destination.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
    }
}
