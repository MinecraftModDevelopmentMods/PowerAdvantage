package zone.moddev.mc.advantageworks.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.EntityLeashKnot;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketClientStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Additional-dimension source fixtures; no compile dependency on an Advantage mod. */
final class LegacyDimensionEntities {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final File MARKER = new File("advantage-migration.allow");
    private static final File DEFINITION = new File("dimension-fixture.json");
    private static final String WITNESS = "AdvWorksDimensionEntity";
    private static final BlockPos SPAWN = new BlockPos(160, 100, 24);
    private static final Set<World> LIVE = Collections.newSetFromMap(new IdentityHashMap<World, Boolean>());
    private static final Map<World, List<ForgeChunkManager.Ticket>> TICKETS = new IdentityHashMap<>();
    private static Object mod;
    private final Map<World, List<Entity>> held = new IdentityHashMap<>();

    static void register(Object owner) {
        mod = owner;
        ForgeChunkManager.setForcedChunkLoadingCallback(owner, (tickets, world) -> {
            // Each marked process requests its own bounded tickets before loading the fixture.
            for (ForgeChunkManager.Ticket ticket : tickets) ForgeChunkManager.releaseTicket(ticket);
        });
        MinecraftForge.EVENT_BUS.register(new LegacyDimensionEntities());
    }

    private static boolean marked(World world) {
        return MARKER.isFile() && !world.isRemote
                && "AdvantageWorksMigration".equals(world.getWorldInfo().getWorldName());
    }

    private static boolean nativeOnLoad(JsonObject definition) {
        return definition.has("nativeOnLoad") && definition.get("nativeOnLoad").getAsBoolean();
    }

    private static boolean frozen(World world, JsonObject definition) {
        return marked(world) && DEFINITION.isFile() && !nativeOnLoad(definition) && !LIVE.contains(world);
    }

    private static boolean witnessed(Entity entity) { return entity.getEntityData().hasKey(WITNESS, 8); }

    @SubscribeEvent public void load(WorldEvent.Load event) {
        if (!marked(event.getWorld()) || !DEFINITION.isFile()) return;
        try {
            JsonObject definition = definition();
            if (event.getWorld().provider.getDimension() == dimension(definition)) prepare((WorldServer) event.getWorld());
        } catch (Exception ex) { throw new IllegalStateException("Dimension load preparation failed", ex); }
    }

    @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
        if (!marked(event.world) || !DEFINITION.isFile()) return;
        if (event.phase == TickEvent.Phase.START) {
            try {
                JsonObject definition = definition();
                int selected = dimension(definition);
                if (event.world.provider.getDimension() == 0 && nativeOnLoad(definition)) {
                    if (DimensionManager.getWorld(selected) == null) DimensionManager.initDimension(selected);
                    WorldServer selectedWorld = DimensionManager.getWorld(selected);
                    if (selectedWorld == null || !marked(selectedWorld)) throw new IllegalStateException("Dimension unavailable");
                    prepare(selectedWorld);
                }
                if (event.world.provider.getDimension() != selected) return;
                // Pin/load before either frozen observation or normal native initialization.
                prepare((WorldServer) event.world);
                if (!frozen(event.world, definition)) return;
                List<Entity> entities = witnesses((WorldServer) event.world);
                entities.retainAll(event.world.loadedEntityList);
                event.world.loadedEntityList.removeAll(entities);
                held.put(event.world, entities);
            } catch (Exception ex) { throw new IllegalStateException("Dimension tick preparation failed", ex); }
        } else {
            List<Entity> entities = held.remove(event.world);
            if (entities != null) for (Entity entity : entities) {
                if (!event.world.loadedEntityList.contains(entity)) event.world.loadedEntityList.add(entity);
            }
        }
    }

    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        held.remove(event.getWorld()); LIVE.remove(event.getWorld());
        // Forge's HIGHEST unload handler already owns ticket cleanup.
        TICKETS.remove(event.getWorld());
    }

    static void execute(MinecraftServer server, String operation, String label) throws Exception {
        if (!label.matches("[a-zA-Z0-9_-]+") || !MARKER.isFile()) throw new IllegalArgumentException("Unmarked runtime or unsafe label");
        if (!operation.matches("build|read|snapshot|operate|respawn")) throw new IllegalArgumentException("Invalid dimension operation");
        JsonObject definition = definition();
        WorldServer overworld = DimensionManager.getWorld(0);
        if (overworld == null || !marked(overworld)) throw new IllegalStateException("Unexpected overworld");
        int dimension = dimension(definition);
        if (DimensionManager.getWorld(dimension) == null) DimensionManager.initDimension(dimension);
        WorldServer world = DimensionManager.getWorld(dimension);
        if (world == null || !marked(world) || world.provider.getDimension() != dimension) throw new IllegalStateException("Dimension unavailable");
        prepare(world);
        if (operation.equals("build")) build(server, world, definition);
        if (operation.equals("operate")) LIVE.add(world);
        JsonObject result = snapshot(server, world, definition, operation);
        Files.write(new File("dimensions-" + label + ".json").toPath(), JSON.toJson(result).getBytes(StandardCharsets.UTF_8));
        System.out.println("ADVDIMENSIONS COMPLETE " + label);
    }

    private static JsonObject definition() throws Exception {
        JsonObject result = JSON.fromJson(new String(Files.readAllBytes(DEFINITION.toPath()), StandardCharsets.UTF_8), JsonObject.class);
        dimension(result);
        String name = result.has("playerName") ? result.get("playerName").getAsString() : "AdvWorksDimensionPlayer";
        UUID uuid = result.has("playerUuid") ? UUID.fromString(result.get("playerUuid").getAsString())
                : UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        result.addProperty("playerName", name); result.addProperty("playerUuid", uuid.toString());
        return result;
    }

    private static int dimension(JsonObject definition) {
        int result = definition.get("dimension").getAsInt();
        if (result != -1 && result != 1) throw new IllegalArgumentException("Expected Nether (-1) or End (1)");
        return result;
    }

    private static void prepare(WorldServer world) {
        if (!TICKETS.containsKey(world)) {
            List<ForgeChunkManager.Ticket> tickets = new ArrayList<>();
            ForgeChunkManager.Ticket ticket = null;
            int used = 0;
            for (int x = 6; x <= 13; x++) for (int z = -2; z <= 2; z++) {
                if (ticket == null || (ticket.getChunkListDepth() > 0 && used >= ticket.getChunkListDepth())) {
                    ticket = ForgeChunkManager.requestTicket(mod, world, ForgeChunkManager.Type.NORMAL);
                    if (ticket == null) throw new IllegalStateException("Could not pin fixture chunks");
                    tickets.add(ticket); used = 0;
                }
                ForgeChunkManager.forceChunk(ticket, new ChunkPos(x, z)); used++;
            }
            TICKETS.put(world, tickets);
        }
        FakePlayer sentinel = FakePlayerFactory.getMinecraft(world);
        if (sentinel.connection == null) new OfflineConnection(world.getMinecraftServer(), sentinel);
        sentinel.setPosition(144.5, 100, 8.5);
        if (!world.playerEntities.contains(sentinel)) world.playerEntities.add(sentinel);
        for (int x = 6; x <= 13; x++) for (int z = -2; z <= 2; z++) world.getChunkProvider().provideChunk(x, z);
    }

    private static void platform(WorldServer world, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
            world.setBlockState(new BlockPos(x, 99, z), Blocks.BEDROCK.getDefaultState(), 2);
            world.setBlockState(new BlockPos(x, 106, z), Blocks.BEDROCK.getDefaultState(), 2);
            for (int y = 100; y < 106; y++) world.setBlockState(new BlockPos(x, y, z),
                    (x == minX || x == maxX || z == minZ || z == maxZ ? Blocks.BEDROCK : Blocks.AIR).getDefaultState(), 2);
        }
    }

    private static void build(MinecraftServer server, WorldServer world, JsonObject definition) throws Exception {
        if (!witnesses(world).isEmpty() || definition.has("entities")) throw new IllegalStateException("Use a fresh disposable fixture");
        platform(world, 140, 180, 2, 14);
        EntityPlayerMP player = player(server, world, definition);
        NBTTagCompound data = player.writeToNBT(new NBTTagCompound());
        data.setTag("Inventory", inventory()); data.setTag("EnderItems", inventory());
        data.setInteger("Dimension", dimension(definition)); data.setFloat("Health", 17f);
        data.setInteger("XpLevel", 23); data.setInteger("XpTotal", 751); data.setFloat("XpP", 0.375f);
        data.setInteger("SelectedItemSlot", 3); data.setInteger("foodLevel", 16); data.setFloat("foodSaturationLevel", 3.5f);
        player.readFromNBT(data);
        player.setPositionAndRotation(144.5, 100, 8.5, 37, -12);
        player.setSpawnChunk(SPAWN, true, 0);
        world.getSaveHandler().getPlayerNBTManager().writePlayerData(player);

        EntityBoat boat = new EntityBoat(world, 152.5, 100, 8.5);
        boat.setNoGravity(true); add(world, boat, "boat");
        EntityWolf wolf = new EntityWolf(world);
        wolf.setPositionAndRotation(152.5, 100, 8.5, 42, 0);
        wolf.setTamed(true); wolf.setOwnerId(player.getUniqueID()); wolf.setSitting(true);
        wolf.setNoAI(true); wolf.enablePersistence(); add(world, wolf, "passenger-wolf");
        if (!wolf.startRiding(boat, true)) throw new IllegalStateException("Passenger could not mount boat");
        EntityLiving horse = (EntityLiving) Class.forName("net.minecraft.entity.passive.EntityHorse")
                .getConstructor(World.class).newInstance(world);
        NBTTagCompound horseData = horse.writeToNBT(new NBTTagCompound());
        horseData.setInteger("Type", 0); horseData.setInteger("Variant", 0); horseData.setBoolean("Tame", true);
        horseData.setString("OwnerUUID", player.getUniqueID().toString());
        horseData.setTag("SaddleItem", stack("minecraft:saddle", 1).writeToNBT(new NBTTagCompound()));
        horseData.setTag("ArmorItem", stack("minecraft:iron_horse_armor", 1).writeToNBT(new NBTTagCompound()));
        horse.readFromNBT(horseData);
        horse.setPositionAndRotation(164.5, 100, 8.5, 0, 0); horse.setNoAI(true); horse.enablePersistence();
        add(world, horse, "horse");
        BlockPos fence = new BlockPos(168, 100, 8);
        world.setBlockState(fence, Blocks.OAK_FENCE.getDefaultState(), 2);
        EntityLeashKnot knot = new EntityLeashKnot(world, fence); add(world, knot, "leash-knot");
        leashApi(horse, new String[] {"setLeashHolder", "setLeashedToEntity", "func_110162_b"},
                new Class<?>[] {Entity.class, boolean.class}, knot, false);
        BlockPos generator = new BlockPos(176, 101, 8);
        ResourceLocation generatorId = new ResourceLocation("electricadvantage:hydroelectric_generator");
        if (!Block.REGISTRY.containsKey(generatorId)) throw new IllegalStateException("Missing source generator");
        world.setBlockState(generator, Block.REGISTRY.getObject(generatorId).getDefaultState(), 2);
        TileEntity tile = world.getTileEntity(generator);
        if (tile == null) throw new IllegalStateException("Missing generator tile");
        Entity turbine = null;
        for (String prefix : new String[] {"zone.moddev.mc", "com.mcmoddev", "cyano"}) {
            try { turbine = (Entity) Class.forName(prefix + ".electricadvantage.entities.HydroturbineEntity")
                    .getConstructor(World.class, TileEntity.class).newInstance(world, tile); break; }
            catch (ClassNotFoundException ignored) { }
        }
        if (turbine == null) throw new IllegalStateException("No source hydroturbine implementation");
        add(world, turbine, "hydroturbine");
        JsonArray entities = new JsonArray();
        for (Entity entity : witnesses(world)) {
            JsonObject entry = new JsonObject(); entry.addProperty("witness", entity.getEntityData().getString(WITNESS));
            entry.addProperty("uuid", entity.getUniqueID().toString()); entities.add(entry);
        }
        definition.add("entities", entities);
        definition.addProperty("universalBucket", Item.REGISTRY.containsKey(new ResourceLocation("forge:bucketfilled")));
        JsonObject geometry = new JsonObject(); geometry.addProperty("minX", 144); geometry.addProperty("maxX", 176);
        geometry.addProperty("y", 100); geometry.addProperty("z", 8); definition.add("geometry", geometry);
        JsonObject parent = new JsonObject(); parent.addProperty("x", 176); parent.addProperty("y", 101); parent.addProperty("z", 8);
        parent.addProperty("name", generatorId.toString()); definition.add("generator", parent);
        Files.write(DEFINITION.toPath(), JSON.toJson(definition).getBytes(StandardCharsets.UTF_8));
    }

    private static EntityPlayerMP player(MinecraftServer server, WorldServer world, JsonObject definition) {
        return new EntityPlayerMP(server, world, new GameProfile(UUID.fromString(definition.get("playerUuid").getAsString()),
                definition.get("playerName").getAsString()), new PlayerInteractionManager(world));
    }

    private static EntityPlayerMP readPlayer(MinecraftServer server, WorldServer world, JsonObject definition) {
        EntityPlayerMP player = player(server, world, definition);
        if (server.getPlayerList().readPlayerDataFromFile(player) == null) throw new IllegalStateException("No player file loaded");
        if (player.dimension != dimension(definition) || player.getServerWorld() != world) throw new IllegalStateException("Player loaded in wrong dimension");
        return player;
    }

    private static NBTTagList inventory() {
        NBTTagList result = new NBTTagList();
        String[] names = {"steamadvantage:musket", "electricadvantage:lithium_battery", "electricadvantage:lead_acid_battery",
                "electricadvantage:alkaline_battery", "electricadvantage:nickel_hydride_battery"};
        for (int slot = 0; slot < names.length; slot++) {
            NBTTagCompound nbt = stack(names[slot], slot == 0 ? 1 : 3).writeToNBT(new NBTTagCompound());
            nbt.setByte("Slot", (byte) slot); result.appendTag(nbt);
        }
        ItemStack oil;
        if (Item.REGISTRY.containsKey(new ResourceLocation("forge:bucketfilled"))) {
            oil = stack("forge:bucketfilled", 1); oil.getTagCompound().setString("FluidName", "crude_oil");
            oil.getTagCompound().setInteger("Amount", 1000);
        } else oil = stack("poweradvantage:crude_oil", 1);
        NBTTagCompound nbt = oil.writeToNBT(new NBTTagCompound()); nbt.setByte("Slot", (byte) 5); result.appendTag(nbt);
        return result;
    }

    private static ItemStack stack(String name, int count) {
        ResourceLocation id = new ResourceLocation(name);
        if (!Item.REGISTRY.containsKey(id)) throw new IllegalStateException("Missing source item " + name);
        Item item = Item.REGISTRY.getObject(id); ItemStack result = new ItemStack(item, count);
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("MigrationWitness", name);
        NBTTagCompound nested = new NBTTagCompound(); nested.setString("OwnerNote", "keep-this-verbatim"); tag.setTag("Nested", nested);
        if (name.contains("battery")) tag.setFloat("energy", 123f);
        result.setTagCompound(tag); if (item.isDamageable()) result.setItemDamage(5);
        if (name.endsWith(":musket")) for (ResourceLocation enchantment : net.minecraft.enchantment.Enchantment.REGISTRY.getKeys()) {
            if (enchantment.toString().startsWith("steamadvantage:")) result.addEnchantment(net.minecraft.enchantment.Enchantment.REGISTRY.getObject(enchantment), 1);
        }
        return result;
    }

    private static void add(WorldServer world, Entity entity, String witness) {
        entity.setUniqueId(UUID.nameUUIDFromBytes(("AdvWorksDimensionEntity:" + world.provider.getDimension() + ":" + witness).getBytes(StandardCharsets.UTF_8)));
        entity.getEntityData().setString(WITNESS, witness); entity.setEntityInvulnerable(true);
        entity.motionX = 0; entity.motionY = 0; entity.motionZ = 0;
        if (!world.spawnEntity(entity)) throw new IllegalStateException("Failed to spawn " + witness);
    }

    private static List<Entity> witnesses(WorldServer world) {
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
        List<Entity> result = new ArrayList<>();
        for (Entity entity : new ArrayList<>(world.loadedEntityList)) collect(entity, seen, result);
        result.sort((a, b) -> a.getEntityData().getString(WITNESS).compareTo(b.getEntityData().getString(WITNESS)));
        return result;
    }

    private static void collect(Entity entity, Set<Entity> seen, List<Entity> result) {
        if (!seen.add(entity)) return;
        if (witnessed(entity)) result.add(entity);
        for (Entity passenger : entity.getPassengers()) collect(passenger, seen, result);
    }

    private static JsonObject snapshot(MinecraftServer server, WorldServer world, JsonObject definition, String operation) throws Exception {
        JsonObject result = new JsonObject(); result.add("definition", definition);
        result.addProperty("operation", operation); result.addProperty("dimension", world.provider.getDimension());
        result.addProperty("gameTime", world.getTotalWorldTime()); result.addProperty("frozen", frozen(world, definition));
        result.addProperty("nativeOnLoad", nativeOnLoad(definition));
        boolean read = operation.equals("read") || operation.equals("build") || operation.equals("respawn");
        result.addProperty("playerAdapter", read); result.add("playerWorldDimension", JsonNull.INSTANCE);
        if (read) {
            EntityPlayerMP player = readPlayer(server, world, definition);
            result.addProperty("playerWorldDimension", player.getServerWorld().provider.getDimension());
            result.add("player", encode(player.writeToNBT(new NBTTagCompound())));
            if (operation.equals("respawn")) result.add("respawn", respawn(server, world, definition, player));
            else world.getSaveHandler().getPlayerNBTManager().writePlayerData(player);
        }
        JsonArray entities = new JsonArray();
        for (Entity entity : witnesses(world)) {
            // The optional chunk-root writer refuses passengers; the atomic writer does not.
            NBTTagCompound nbt = new NBTTagCompound();
            if (!entity.writeToNBTAtomically(nbt)) throw new IllegalStateException("Could not serialize dimension witness");
            JsonObject entry = encode(nbt); entry.addProperty("witness", entity.getEntityData().getString(WITNESS));
            entry.addProperty("uuid", entity.getUniqueID().toString()); entry.addProperty("ticksExisted", entity.ticksExisted);
            entry.addProperty("entityId", entity.getEntityId());
            entry.addProperty("instanceId", System.identityHashCode(entity));
            entry.add("vehicleUuid", entity.getRidingEntity() == null ? JsonNull.INSTANCE : JSON.toJsonTree(entity.getRidingEntity().getUniqueID().toString()));
            JsonArray passengers = new JsonArray(); for (Entity passenger : entity.getPassengers()) passengers.add(JSON.toJsonTree(passenger.getUniqueID().toString()));
            entry.add("passengerUuids", passengers);
            Entity leash = entity instanceof EntityLiving ? (Entity) leashApi((EntityLiving) entity,
                    new String[] {"getLeashHolder", "getLeashedToEntity", "func_110166_bE"}, new Class<?>[0]) : null;
            entry.add("leashHolderUuid", leash == null ? JsonNull.INSTANCE : JSON.toJsonTree(leash.getUniqueID().toString()));
            entry.add("leashHolderType", leash == null ? JsonNull.INSTANCE : JSON.toJsonTree(leash instanceof EntityLeashKnot
                    ? "minecraft:leash_knot" : leash.getClass().getName()));
            BlockPos leashPos = leash instanceof EntityHanging ? ((EntityHanging) leash).getHangingPosition() : null;
            entry.add("leashHolderPosition", leashPos == null ? JsonNull.INSTANCE
                    : JSON.toJsonTree(new int[] {leashPos.getX(), leashPos.getY(), leashPos.getZ()}));
            entities.add(entry);
        }
        result.add("entities", entities);
        BlockPos pos = new BlockPos(176, 101, 8); JsonObject generator = new JsonObject();
        generator.addProperty("actualName", world.getBlockState(pos).getBlock().getRegistryName().toString());
        TileEntity tile = world.getTileEntity(pos); if (tile != null) generator.add("tile", encode(tile.writeToNBT(new NBTTagCompound())));
        result.add("generator", generator);
        return result;
    }

    private static JsonObject respawn(MinecraftServer server, WorldServer world, JsonObject definition, EntityPlayerMP player) throws Exception {
        JsonObject options = definition.has("respawn") ? definition.getAsJsonObject("respawn") : new JsonObject();
        boolean keep = !options.has("keepInventory") || options.get("keepInventory").getAsBoolean();
        String bedMode = options.has("bedMode") ? options.get("bedMode").getAsString() : "forced";
        if (!bedMode.matches("forced|bed|missing")) throw new IllegalArgumentException("Invalid bed mode");
        WorldServer destination = DimensionManager.getWorld(0);
        platform(destination, 156, 166, 20, 29);
        if (bedMode.equals("bed")) {
            destination.setBlockState(SPAWN, Blocks.BED.getDefaultState().withProperty(BlockBed.FACING, EnumFacing.EAST)
                    .withProperty(BlockBed.PART, BlockBed.EnumPartType.FOOT), 2);
            destination.setBlockState(SPAWN.east(), Blocks.BED.getDefaultState().withProperty(BlockBed.FACING, EnumFacing.EAST)
                    .withProperty(BlockBed.PART, BlockBed.EnumPartType.HEAD), 2);
        }
        player.setSpawnChunk(SPAWN, bedMode.equals("forced"), 0);
        JsonObject result = new JsonObject(); result.addProperty("scope", "server-only-dummy-network");
        result.addProperty("handler", "NetHandlerPlayServer.PERFORM_RESPAWN");
        result.addProperty("keepInventory", keep); result.addProperty("bedMode", bedMode);
        NBTTagCompound before = player.writeToNBT(new NBTTagCompound());
        result.addProperty("fromDimension", player.dimension); result.add("before", encode(before));
        JsonObject diagnostics = new JsonObject(); result.add("spawnDiagnostics", diagnostics);
        diagnostics.add("before", spawnDiagnostics(player, before));
        Set<Entity> existing = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>()); existing.addAll(world.loadedEntityList);
        boolean oldSource = world.getGameRules().getBoolean("keepInventory"), oldTarget = destination.getGameRules().getBoolean("keepInventory");
        EntityPlayerMP replacement = null;
        try {
            world.getGameRules().setOrCreateGameRule("keepInventory", Boolean.toString(keep));
            destination.getGameRules().setOrCreateGameRule("keepInventory", Boolean.toString(keep));
            OfflineConnection connection = new OfflineConnection(server, player);
            player.setHealth(0); player.onDeath(new DamageSource("advworksDimensionDeath"));
            NBTTagCompound afterDeath = player.writeToNBT(new NBTTagCompound());
            result.add("afterDeath", encode(afterDeath));
            diagnostics.add("afterDeath", spawnDiagnostics(player, afterDeath));
            connection.processClientStatus(new CPacketClientStatus(CPacketClientStatus.State.PERFORM_RESPAWN));
            replacement = server.getPlayerList().getPlayerByUUID(player.getUniqueID());
            if (replacement == null || replacement == player) throw new IllegalStateException("Native respawn handler did not replace player");
            new OfflineConnection(server, replacement);
            NBTTagCompound after = replacement.writeToNBT(new NBTTagCompound());
            result.add("after", encode(after));
            diagnostics.add("after", spawnDiagnostics(replacement, after));
            result.addProperty("toDimension", replacement.getServerWorld().provider.getDimension());
            BlockPos bed = replacement.getBedLocation(replacement.dimension);
            result.add("bedLocation", bed == null ? JsonNull.INSTANCE : JSON.toJsonTree(new int[] {bed.getX(), bed.getY(), bed.getZ()}));
            JsonArray drops = new JsonArray();
            for (Entity entity : world.loadedEntityList) if (entity instanceof EntityItem && !existing.contains(entity)) {
                NBTTagCompound nbt = entity.writeToNBT(new NBTTagCompound()); nbt.setString("id", "minecraft:item"); drops.add(encode(nbt));
            }
            result.add("drops", drops);
            return result;
        } finally {
            try {
                if (replacement != null) server.getPlayerList().playerLoggedOut(replacement);
            } finally {
                world.getGameRules().setOrCreateGameRule("keepInventory", Boolean.toString(oldSource));
                destination.getGameRules().setOrCreateGameRule("keepInventory", Boolean.toString(oldTarget));
            }
        }
    }

    private static JsonObject spawnDiagnostics(EntityPlayerMP player, NBTTagCompound nbt) throws Exception {
        JsonObject result = new JsonObject(); result.addProperty("queryDimension", 0);
        result.addProperty("playerWorldDimension", player.getServerWorld().provider.getDimension());
        try {
            result.add("spawnDimension", JSON.toJsonTree(player.getClass().getMethod("getSpawnDimension").invoke(player)));
        } catch (NoSuchMethodException ignored) {
            // Forge 1.10 has dimension-aware bed getters but no player spawn-dimension getter.
            result.add("spawnDimension", JsonNull.INSTANCE);
        }
        BlockPos bed = player.getBedLocation(0);
        result.add("bedLocation", bed == null ? JsonNull.INSTANCE : JSON.toJsonTree(new int[] {bed.getX(), bed.getY(), bed.getZ()}));
        result.addProperty("spawnForced", player.isSpawnForced(0));
        JsonObject spawn = new JsonObject();
        for (String key : new String[] {"SpawnX", "SpawnY", "SpawnZ"}) {
            spawn.add(key, nbt.hasKey(key, 99) ? JSON.toJsonTree(nbt.getInteger(key)) : JsonNull.INSTANCE);
        }
        spawn.add("SpawnForced", nbt.hasKey("SpawnForced", 99) ? JSON.toJsonTree(nbt.getBoolean("SpawnForced")) : JsonNull.INSTANCE);
        spawn.add("SpawnDimension", nbt.hasKey("SpawnDimension", 8) ? JSON.toJsonTree(nbt.getString("SpawnDimension"))
                : nbt.hasKey("SpawnDimension", 99) ? JSON.toJsonTree(nbt.getInteger("SpawnDimension")) : JsonNull.INSTANCE);
        result.add("serializedSpawn", spawn);
        return result;
    }

    private static final class OfflineConnection extends NetHandlerPlayServer {
        OfflineConnection(MinecraftServer server, EntityPlayerMP player) { super(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), player); }
        @Override public void sendPacket(Packet<?> packet) { /* Deliberately no socket or client login. */ }
    }

    private static Object leashApi(EntityLiving entity, String[] names, Class<?>[] types, Object... args) throws Exception {
        // MCP renamed these native leash methods between the two source mappings.
        for (String name : names) {
            try { return EntityLiving.class.getMethod(name, types).invoke(entity, args); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new IllegalStateException("No native leash API");
    }

    private static JsonObject encode(NBTTagCompound nbt) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(nbt, bytes);
        JsonObject result = new JsonObject(); result.addProperty("nbtBase64", Base64.getEncoder().encodeToString(bytes.toByteArray())); return result;
    }
}
