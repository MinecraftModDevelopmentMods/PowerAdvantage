package zone.moddev.mc.advantageworks.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Disposable legacy fixture writer; deliberately has no Advantage compile dependency. */
@Mod(modid = "advantagemigrationprobe", name = "Advantage Migration Probe", version = "1.0.0",
        acceptableRemoteVersions = "*", serverSideOnly = true)
public final class LegacyProbe {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File MARKER = new File("advantage-migration.allow");
    private final java.util.Map<World, List<net.minecraft.entity.Entity>> heldActors = new java.util.IdentityHashMap<>();

    @Mod.EventHandler public void initialize(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        LegacyDimensionEntities.register(this);
        LegacyConnected.register(this);
    }

    @SubscribeEvent public void load(WorldEvent.Load event) { freeze(event.getWorld()); }
    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            freeze(event.world);
            if (actorsFrozen(event.world)) {
                List<net.minecraft.entity.Entity> held = new ArrayList<>();
                for (net.minecraft.entity.Entity entity : event.world.loadedEntityList) {
                    if (LegacyActors.witnessed(entity)) held.add(entity);
                }
                event.world.loadedEntityList.removeAll(held);
                heldActors.put(event.world, held);
            }
        } else {
            List<net.minecraft.entity.Entity> held = heldActors.remove(event.world);
            if (held != null) event.world.loadedEntityList.addAll(held);
        }
    }

    static boolean actorsFrozen(World world) {
        return MARKER.isFile() && !world.isRemote && new File("actor-fixture.json").isFile()
                && !new File("actor-operate.allow").isFile();
    }

    private void freeze(World world) {
        if (MARKER.isFile() && !world.isRemote && !new File("migration-operate.allow").isFile()) {
            world.tickableTileEntities.clear();
        }
    }

    @Mod.EventHandler public void starting(FMLServerStartingEvent event) {
        event.registerServerCommand(LegacyConnected.command());
        event.registerServerCommand(new CommandBase() {
            @Override public String getName() { return "advprobe"; }
            @Override public String getUsage(ICommandSender sender) { return "advprobe build|snapshot <label>"; }
            @Override public int getRequiredPermissionLevel() { return 4; }
            @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
                try {
                    if (!MARKER.isFile()) throw new IllegalStateException("Unmarked migration runtime");
                    World world = net.minecraftforge.common.DimensionManager.getWorld(0);
                    if (!world.getWorldInfo().getWorldName().equals("AdvantageWorksMigration")) {
                        throw new IllegalStateException("Refusing an unexpected world");
                    }
                    if (args.length == 2 && args[0].startsWith("dimensions-")) {
                        LegacyDimensionEntities.execute(server, args[0].substring("dimensions-".length()), args[1]);
                        return;
                    }
                    if (args.length == 2 && args[0].startsWith("actors-")) {
                        if (!args[1].matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid label");
                        if (args[0].equals("actors-build")) LegacyActors.build(server, (net.minecraft.world.WorldServer) world);
                        else if (!args[0].equals("actors-read") && !args[0].equals("actors-snapshot")) throw new IllegalArgumentException("Invalid actor command");
                        LegacyActors.snapshot(server, (net.minecraft.world.WorldServer) world, args[1], !args[0].equals("actors-snapshot"));
                        return;
                    }
                    if (args.length == 0 || !(args[0].equals("build") || args[0].equals("snapshot"))) {
                        throw new IllegalArgumentException("Expected build or snapshot");
                    }
                    File definitionFile = new File("migration-fixture.json");
                    JsonArray definition;
                    if (args[0].equals("build")) {
                        definition = build(world);
                        Files.write(definitionFile.toPath(), JSON.toJson(definition).getBytes(StandardCharsets.UTF_8));
                    } else {
                        definition = JSON.fromJson(new String(Files.readAllBytes(definitionFile.toPath()),
                                StandardCharsets.UTF_8), JsonArray.class);
                    }
                    snapshot(world, definition, args.length > 1 ? args[1] : "source");
                    System.out.println("ADVPROBE COMPLETE " + args[0]);
                } catch (Exception ex) { throw new IllegalStateException("Migration probe failed", ex); }
            }
        });
    }

    private JsonArray build(World world) throws Exception {
        JsonArray definition = new JsonArray();
        List<ResourceLocation> names = new ArrayList<>();
        for (ResourceLocation name : Block.REGISTRY.getKeys()) {
            if (name.toString().matches("(poweradvantage|steamadvantage|electricadvantage):.*")) names.add(name);
        }
        names.sort((left, right) -> left.toString().compareTo(right.toString()));
        int index = 0;
        for (ResourceLocation name : names) {
            Block block = Block.REGISTRY.getObject(name);
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int meta = 0; meta < 16; meta++) {
                IBlockState state;
                try { state = block.getStateFromMeta(meta); } catch (RuntimeException ignored) { continue; }
                if (!seen.add(state.toString())) continue;
                // Flowing liquids are tested as sealed sources; scheduled fluid ticks are not frozen.
                if ((name.toString().endsWith(":crude_oil") || name.toString().endsWith(":refined_oil")) && meta != 0) continue;
                BlockPos pos = new BlockPos(-48 + (index % 24) * 4, 80, -48 + (index / 24) * 4);
                index++;
                world.setBlockState(pos.down(), Blocks.BEDROCK.getDefaultState(), 2);
                if (name.toString().endsWith("oil")) {
                    for (net.minecraft.util.EnumFacing facing : net.minecraft.util.EnumFacing.HORIZONTALS) {
                        world.setBlockState(pos.offset(facing), Blocks.GLASS.getDefaultState(), 2);
                    }
                }
                world.setBlockState(pos, state, 2);
                TileEntity tile = world.getTileEntity(pos);
                if (tile != null) {
                    NBTTagCompound nbt = tile.writeToNBT(new NBTTagCompound());
                    // Both canonical and historical typo keys seed the original serializers.
                    nbt.merge(JsonToNBT.getTagFromJson("{Temperature:37.5f,Tempoerature:37.5f,progress:7s,Progress:7s,"
                            + "Tank:{FluidName:\"water\",Amount:750},TankOut:{FluidName:\"water\",Amount:750},"
                            + "InputTank:{FluidName:\"crude_oil\",Amount:750},TankIn:{FluidName:\"crude_oil\",Amount:750},"
                            + "Items:[{Slot:0b,id:\"minecraft:dirt\",Count:13b,Damage:0s}]}"));
                    int[] energy = nbt.getIntArray("Energy");
                    for (int i = 0; i < energy.length; i++) energy[i] = Float.floatToIntBits(100f);
                    nbt.setIntArray("Energy", energy);
                    tile.readFromNBT(nbt);
                    tile.markDirty();
                }
                JsonObject entry = new JsonObject();
                entry.addProperty("x", pos.getX()); entry.addProperty("y", pos.getY()); entry.addProperty("z", pos.getZ());
                entry.addProperty("name", name.toString()); entry.addProperty("metadata", block.getMetaFromState(state));
                definition.add(entry);
            }
        }
        List<ResourceLocation> itemNames = new ArrayList<>();
        for (ResourceLocation name : net.minecraft.item.Item.REGISTRY.getKeys()) {
            if (name.toString().matches("(poweradvantage|steamadvantage|electricadvantage):.*")) itemNames.add(name);
        }
        itemNames.sort((left, right) -> left.toString().compareTo(right.toString()));
        for (int start = 0; start < itemNames.size(); start += 25) {
            BlockPos pos = new BlockPos(-56, 80, -48 + start / 25 * 4);
            world.setBlockState(pos.down(), Blocks.BEDROCK.getDefaultState(), 2);
            world.setBlockState(pos, Blocks.CHEST.getDefaultState(), 2);
            net.minecraft.tileentity.TileEntityChest chest = (net.minecraft.tileentity.TileEntityChest) world.getTileEntity(pos);
            for (int slot = 0; slot < 25 && start + slot < itemNames.size(); slot++) {
                net.minecraft.item.Item item = net.minecraft.item.Item.REGISTRY.getObject(itemNames.get(start + slot));
                net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(item, item.isDamageable() ? 1 : 3);
                if (item.isDamageable()) stack.setItemDamage(5);
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString("MigrationWitness", itemNames.get(start + slot).toString());
                if (itemNames.get(start + slot).toString().contains("battery")) tag.setFloat("energy", 123f);
                stack.setTagCompound(tag);
                if (itemNames.get(start + slot).toString().endsWith(":musket")) {
                    for (ResourceLocation enchantment : net.minecraft.enchantment.Enchantment.REGISTRY.getKeys()) {
                        if (enchantment.toString().startsWith("steamadvantage:")) {
                            stack.addEnchantment(net.minecraft.enchantment.Enchantment.REGISTRY.getObject(enchantment), 1);
                        }
                    }
                }
                chest.setInventorySlotContents(slot, stack);
            }
            if (start == 0) {
                net.minecraft.item.Item bucket = net.minecraft.item.Item.REGISTRY.getObject(new ResourceLocation("forge:bucketfilled"));
                if (bucket != null) {
                    for (int slot = 25; slot <= 26; slot++) {
                        net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(bucket);
                        NBTTagCompound tag = new NBTTagCompound();
                        tag.setString("FluidName", slot == 25 ? "crude_oil" : "refined_oil");
                        tag.setInteger("Amount", 1000);
                        stack.setTagCompound(tag);
                        chest.setInventorySlotContents(slot, stack);
                    }
                }
            }
            chest.markDirty();
            JsonObject entry = new JsonObject();
            entry.addProperty("x", pos.getX()); entry.addProperty("y", pos.getY()); entry.addProperty("z", pos.getZ());
            entry.addProperty("name", "minecraft:chest"); entry.addProperty("metadata", 2);
            definition.add(entry);
        }
        return definition;
    }

    private void snapshot(World world, JsonArray definition, String label) throws Exception {
        if (!label.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid snapshot label");
        JsonArray result = new JsonArray();
        for (JsonElement element : definition) {
            JsonObject entry = JSON.fromJson(JSON.toJson(element), JsonObject.class);
            BlockPos pos = new BlockPos(entry.get("x").getAsInt(), entry.get("y").getAsInt(), entry.get("z").getAsInt());
            IBlockState state = world.getBlockState(pos);
            entry.addProperty("actualName", state.getBlock().getRegistryName().toString());
            JsonObject properties = new JsonObject();
            for (java.util.Map.Entry<IProperty<?>, Comparable<?>> property : state.getProperties().entrySet()) {
                properties.addProperty(property.getKey().getName(), property.getValue().toString().toLowerCase(java.util.Locale.ROOT));
            }
            entry.add("properties", properties);
            TileEntity tile = world.getTileEntity(pos);
            if (tile != null) {
                tile.markDirty();
                NBTTagCompound nbt = tile.writeToNBT(new NBTTagCompound());
                entry.addProperty("nbt", nbt.toString());
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                net.minecraft.nbt.CompressedStreamTools.writeCompressed(nbt, bytes);
                entry.addProperty("nbtBase64", java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
            }
            result.add(entry);
        }
        Files.write(new File("migration-" + label + ".json").toPath(), JSON.toJson(result).getBytes(StandardCharsets.UTF_8));
    }
}
