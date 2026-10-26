package zone.moddev.mc.advantageworks;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import cyano.poweradvantage.api.simple.TileEntitySimplePowerMachine;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemUseContext;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.FurnaceTileEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.poweradvantage.init.Fluids;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.StillTileEntity;
import zone.moddev.mc.poweradvantage.util.FluidContainerHelper;
import static zone.moddev.mc.advantageworks.ProgressionProbe.*;

/** Non-table processing edges are verified with live bounded machines here. */
final class ProcessProgression {
    private static JsonArray evidence = new JsonArray();
    private static final java.util.Set<String> crusherWitnesses = new java.util.HashSet<>();
    static void reset() { evidence = new JsonArray(); crusherWitnesses.clear(); }
    static JsonArray witnesses() { return evidence; }

    static void smelt(ServerWorld world, JsonObject fixture, String recipe, ItemStack input, ItemStack expected) {
        FurnaceTileEntity furnace = (FurnaceTileEntity) fresh(world, fixture,
                new BlockPos(16394, 80, 16384), "minecraft:furnace");
        furnace.setInventorySlotContents(0, input.copy());
        furnace.setInventorySlotContents(1, stack("minecraft:coal"));
        for (int tick = 0; tick < 201; tick++) furnace.tick();
        ItemStack output = furnace.getStackInSlot(2);
        require(furnace.getStackInSlot(0).isEmpty() && furnace.getStackInSlot(1).isEmpty()
                && !output.isEmpty() && id(output).equals(id(expected)) && output.getCount() == expected.getCount(),
                "Live furnace failed " + recipe + ": input=" + furnace.getStackInSlot(0)
                    + ", fuel=" + furnace.getStackInSlot(1) + ", output=" + output
                    + ", expected=" + expected + ", saved=" + furnace.write(new net.minecraft.nbt.CompoundNBT()));
        record("live_furnace:" + recipe, input.getCount(), output.getCount());
    }

    static void hammer(ServerWorld world, ServerPlayerEntity player, Map<String, JsonObject> known,
            JsonObject fixture, String recipe, ItemStack input, ItemStack expected) {
        BlockPos pos = new BlockPos(16396, 80, 16384);
        fresh(world, fixture, pos, "minecraft:stone");
        AxisAlignedBB area = new AxisAlignedBB(pos.up());
        for (ItemEntity entity : world.getEntitiesWithinAABB(ItemEntity.class, area)) {
            entity.setItem(ItemStack.EMPTY);
            entity.remove();
        }
        String hammer = known.keySet().stream().filter(key -> key.startsWith("basemetals:")
                && key.endsWith("_crackhammer")).findFirst().orElseThrow(() -> new IllegalStateException("No craftable hammer"));
        player.inventory.clear();
        player.setHeldItem(Hand.MAIN_HAND, stack(hammer));
        player.setSneaking(false);
        ItemEntity source = new ItemEntity(world, pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D, input.copy());
        world.addEntity(source);
        ActionResultType action = player.getHeldItemMainhand().getItem().onItemUse(
                new ItemUseContext(player, Hand.MAIN_HAND, new net.minecraft.util.math.BlockRayTraceResult(
                        new net.minecraft.util.math.Vec3d(pos.getX() + 0.5D, pos.getY() + 1D, pos.getZ() + 0.5D),
                        Direction.UP, pos, false)));
        require(action == ActionResultType.SUCCESS && source.getItem().isEmpty()
                && player.getHeldItemMainhand().getDamage() == 1, "Live hammer failed " + recipe);
        int produced = 0;
        for (ItemEntity entity : world.getEntitiesWithinAABB(ItemEntity.class, area)) {
            if (entity == source || entity.getItem().isEmpty()) continue;
            require(id(entity.getItem()).equals(id(expected)), "Unexpected hammer output for " + recipe
                    + ": " + entity.getItem() + ", expected " + expected);
            produced += entity.getItem().getCount();
            entity.setItem(ItemStack.EMPTY);
            entity.remove();
        }
        require(produced == expected.getCount(), "Hammer output count changed for " + recipe);
        record("live_hammer:" + recipe, input.getCount(), produced);
    }

    static boolean canCrush(Map<String, JsonObject> known, ItemStack input) {
        if (input.getItem() instanceof BlockItem) {
            for (String id : known.keySet()) {
                if (id.startsWith("basemetals:") && id.endsWith("_crackhammer")
                        && stack(id).getItem().canHarvestBlock(((BlockItem) input.getItem()).getBlock().getDefaultState())) return true;
            }
            return (has(known, "steamadvantage:steam_crusher", "steamadvantage:steam_boiler_coal", "minecraft:water_bucket"))
                    || has(known, "electricadvantage:electric_crusher", "electricadvantage:hydroelectric_generator", "minecraft:water_bucket");
        }
        return true;
    }

    static boolean advance(ServerWorld world, ServerPlayerEntity player, Map<String, JsonObject> known, JsonObject fixture) {
        boolean changed = false;
        for (String machine : new String[] {"steamadvantage:steam_crusher", "electricadvantage:electric_crusher"}) {
            if (known.containsKey(machine) && crusherWitnesses.add(machine)) {
                for (String potato : new String[] {"minecraft:potato", "minecraft:poisonous_potato"})
                    crushPotato(world, fixture, machine, potato);
            }
        }
        if (known.containsKey("electricadvantage:sulfur_ore") && !known.containsKey("electricadvantage:sulfur_powder")) {
            BlockPos pos = new BlockPos(16392, 80, 16384);
            fresh(world, fixture, pos, "electricadvantage:sulfur_ore");
            java.util.List<ItemStack> drops = net.minecraft.block.Block.getDrops(
                    world.getBlockState(pos), world, pos, world.getTileEntity(pos));
            require(drops.size() == 1 && id(drops.get(0)).equals("electricadvantage:sulfur_powder")
                    && drops.get(0).getCount() >= 1 && drops.get(0).getCount() <= 4, "Sulfur ore drops changed");
            changed |= acquire(known, player, drops.get(0), "mine_generated_sulfur_ore", Collections.singletonList("electricadvantage:sulfur_ore"));
        }
        if (known.containsKey("minecraft:bucket")) {
            for (String fluid : new String[] {"minecraft:water", "poweradvantage:crude_oil"}) {
                String bucket = fluid.equals("minecraft:water") ? "minecraft:water_bucket" : fluid + "_bucket";
                if (known.containsKey(bucket)) continue;
                BlockPos pos = new BlockPos(16390, 80, 16384);
                fresh(world, fixture, pos, fluid);
                player.inventory.clear();
                player.setPositionAndRotation(pos.getX() + 0.5D, pos.getY() + 2, pos.getZ() + 0.5D, 0, 90);
                player.setHeldItem(Hand.MAIN_HAND, stack("minecraft:bucket"));
                ItemStack output = player.getHeldItemMainhand().getItem().onItemRightClick(world, player, Hand.MAIN_HAND).getResult();
                require(!output.isEmpty() && id(output).equals(bucket), "Native world-fluid pickup changed identity: " + fluid);
                require(world.getFluidState(pos).isEmpty(), "Bucket pickup left its source behind");
                changed |= acquire(known, player, output, "bucket_collect:" + fluid, Collections.singletonList("minecraft:bucket"));
                record("bucket_collect:" + fluid, 1000, 1);
            }
        }
        if (!known.containsKey("poweradvantage:refined_oil_bucket") && has(known,
                "poweradvantage:still", "poweradvantage:crude_oil_bucket", "minecraft:bucket", "minecraft:coal")) {
            StillTileEntity still = (StillTileEntity) fresh(world, fixture, new BlockPos(16386, 80, 16384), "poweradvantage:still");
            ((IInventory) still).setInventorySlotContents(0, stack("minecraft:coal"));
            still.powerUpdate();
            for (int batch = 0; batch < 2; batch++) {
                require(still.getInputTank().fill(new FluidStack(Fluids.crude_oil, 1000), true) == 1000, "Still rejected crude oil");
                for (int tick = 0; tick < 502; tick++) still.tickUpdate(true);
                require(still.getInputTank().getFluidAmount() == 0 && still.getTank().getFluidAmount() == (batch + 1) * 500,
                        "Distillation did not conserve its 2:1 ratio");
            }
            player.inventory.clear();
            player.setHeldItem(Hand.MAIN_HAND, stack("minecraft:bucket"));
            require(FluidContainerHelper.interact(player, Hand.MAIN_HAND, still, null), "Cannot collect refined oil from still");
            ItemStack output = player.getHeldItemMainhand().copy();
            require(id(output).equals("poweradvantage:refined_oil_bucket") && still.getTank().getFluidAmount() == 0,
                    "Refined-oil bucket changed identity or duplicated fluid");
            changed |= acquire(known, player, output, "live_distillation:2000mB_to_1000mB",
                    Arrays.asList("poweradvantage:still", "poweradvantage:crude_oil_bucket", "minecraft:coal", "minecraft:bucket"));
            record("live_distillation", 2000, 1000);
        }
        if (!known.containsKey("electricadvantage:petrolplastic_ingot") && has(known,
                "electricadvantage:plastic_refinery", "poweradvantage:refined_oil_bucket")
                && (known.containsKey("electricadvantage:hydroelectric_generator")
                    || known.containsKey("electricadvantage:photovoltaic_generator")
                    || known.containsKey("electricadvantage:steam_powered_generator"))) {
            TileEntity tile = fresh(world, fixture, new BlockPos(16388, 80, 16384), "electricadvantage:plastic_refinery");
            IAdvantageFluidHandler fluids = (IAdvantageFluidHandler) tile;
            require(fluids.fill(null, new FluidStack(Fluids.refined_oil, 100), true) == 100, "Refinery rejected refined oil");
            TileEntitySimplePowerMachine machine = (TileEntitySimplePowerMachine) tile;
            ConduitType electricity = new ConduitType("electricity");
            float supplied = 0;
            for (int tick = 0; tick < 101; tick++) {
                supplied += machine.addEnergy(500, electricity);
                machine.tickUpdate(true);
            }
            ItemStack output;
            try { output = (ItemStack) tile.getClass().getMethod("getOutputSlot", int.class).invoke(tile, 0); }
            catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            require(output != null && !output.isEmpty() && id(output).equals("electricadvantage:petrolplastic_ingot") && output.getCount() == 1,
                    "Refinery did not make exactly one plastic ingot");
            require(fluids.getTankInfo(null)[0].fluid == null || fluids.getTankInfo(null)[0].fluid.amount == 0, "Refinery did not consume 100mB");
            require(Math.abs(supplied - machine.getEnergy(electricity) - 1200) < 0.001F, "Refinery electricity consumption changed");
            changed |= acquire(known, player, output, "live_refinery:100mB_1200energy",
                    Arrays.asList("electricadvantage:plastic_refinery", "poweradvantage:refined_oil_bucket", "electricadvantage:hydroelectric_generator"));
            record("live_refinery", 100, 1);
        }
        return changed;
    }

    private static void crushPotato(ServerWorld world, JsonObject fixture, String id, String potato) {
        boolean steam = id.startsWith("steamadvantage:");
        TileEntitySimplePowerMachine machine = (TileEntitySimplePowerMachine) fresh(world, fixture,
                new BlockPos(steam ? 16400 : 16402, 80, 16384), id);
        ItemStack input = stack(potato);
        input.setCount(2);
        IInventory inventory = (IInventory) machine;
        inventory.setInventorySlotContents(0, input);
        machine.powerUpdate();
        ConduitType power = new ConduitType(steam ? "steam" : "electricity");
        float supplied = 0;
        int ticks = steam ? 125 : 200;
        for (int tick = 0; tick < ticks; tick++) {
            supplied += machine.addEnergy(500, power);
            machine.tickUpdate(true);
        }
        ItemStack output;
        try { output = steam ? inventory.getStackInSlot(1)
                : (ItemStack) machine.getClass().getMethod("getOutputSlot", int.class).invoke(machine, 0); }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        require(output != null && !output.isEmpty() && id(output).equals("poweradvantage:starch") && output.getCount() == 1,
                "Powered crusher did not produce one starch: " + id + "/" + potato);
        require(inventory.getStackInSlot(0).getCount() == 1, "Powered crusher did not consume exactly one potato");
        require(supplied > machine.getEnergy(power), "Powered crusher did not consume energy");
        record("live_crusher:" + id + ":" + potato, 1, output.getCount());
    }

    private static boolean has(Map<String, JsonObject> known, String... ids) {
        for (String id : ids) if (!known.containsKey(id)) return false;
        return true;
    }

    private static TileEntity fresh(ServerWorld world, JsonObject fixture, BlockPos pos, String block) {
        JsonObject bounds = fixture.getAsJsonObject("machineBounds");
        int[] p = {pos.getX(), pos.getY(), pos.getZ()};
        for (int i = 0; i < 3; i++) require(p[i] >= bounds.getAsJsonArray("min").get(i).getAsInt()
                && p[i] <= bounds.getAsJsonArray("max").get(i).getAsInt(), "Machine fixture escaped its bounds");
        world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState());
        world.removeTileEntity(pos);
        net.minecraft.block.Block material = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(block));
        require(material != null, "Missing machine/fluid " + block);
        world.setBlockState(pos, material.getDefaultState());
        // Commands run within a server tick; queued tile replacements can still expose
        // the previous witness. Give each bounded fixture a clean native tile instance.
        TileEntity clean = material.createTileEntity(material.getDefaultState(), world);
        if (clean != null) {
            clean.setWorld(world);
            clean.setPos(pos);
            world.setTileEntity(pos, clean);
        }
        return clean;
    }

    private static void record(String operation, int input, int output) {
        JsonObject row = new JsonObject();
        row.addProperty("operation", operation);
        row.addProperty("input", input);
        row.addProperty("output", output);
        row.addProperty("status", "PASS");
        evidence.add(row);
    }
}
