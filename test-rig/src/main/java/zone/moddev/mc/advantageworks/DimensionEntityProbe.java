package zone.moddev.mc.advantageworks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
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
import net.minecraft.block.BedBlock;
import net.minecraft.command.Commands;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.item.LeashKnotEntity;
import net.minecraft.entity.item.HangingEntity;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketDirection;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.client.CClientStatusPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Native additional-dimension player and entity observations, without client acceptance claims. */
final class DimensionEntityProbe {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final File MARKER = new File("advantage-migration.allow");
    private static final File DEFINITION = new File("dimension-fixture.json");
    private static final String WITNESS = "AdvWorksDimensionEntity";
    private static final BlockPos SPAWN = new BlockPos(160, 100, 24);
    private final Set<World> live = Collections.newSetFromMap(new IdentityHashMap<World, Boolean>());
    private static final Set<World> PINNED = Collections.newSetFromMap(new IdentityHashMap<World, Boolean>());
    private final Map<World, NativeEntityHold> held = new IdentityHashMap<>();

    static void register() {
        if (!MARKER.isFile()) return;
        DimensionEntityProbe probe = new DimensionEntityProbe();
        MinecraftForge.EVENT_BUS.addListener(probe::load);
        MinecraftForge.EVENT_BUS.addListener(probe::tick);
        MinecraftForge.EVENT_BUS.addListener(probe::starting);
    }

    private static boolean marked(World world) {
        return MARKER.isFile() && !world.isClientSide
                && "AdvantageWorksMigration".equals(world.getServer().getWorldData().getLevelName());
    }

    private static boolean nativeOnLoad(JsonObject definition) {
        return definition.has("nativeOnLoad") && definition.get("nativeOnLoad").getAsBoolean();
    }

    private boolean frozen(World world, JsonObject definition) {
        return marked(world) && DEFINITION.isFile() && !nativeOnLoad(definition) && !live.contains(world);
    }

    private static boolean witnessed(Entity entity) { return entity.getPersistentData().contains(WITNESS, 8); }

    private void load(WorldEvent.Load event) {
        if (!(event.getWorld() instanceof ServerWorld)) return;
        ServerWorld world = (ServerWorld) event.getWorld();
        if (!marked(world) || !DEFINITION.isFile()) return;
        try {
            JsonObject definition = definition();
            if (Native116Worlds.legacyId(world.dimension()) == dimension(definition)) prepare(world);
        } catch (Exception ex) { throw new IllegalStateException("Dimension load preparation failed", ex); }
    }

    private void tick(TickEvent.WorldTickEvent event) {
        if (!marked(event.world) || !DEFINITION.isFile()) return;
        if (event.phase == TickEvent.Phase.START) {
            try {
                JsonObject definition = definition();
                int selected = dimension(definition);
                if (event.world.dimension().equals(World.OVERWORLD) && nativeOnLoad(definition)) {
                    ServerWorld selectedWorld = event.world.getServer().getLevel(Native116Worlds.key(selected));
                    if (selectedWorld == null || !marked(selectedWorld)) throw new IllegalStateException("Dimension unavailable");
                    prepare(selectedWorld);
                }
                if (Native116Worlds.legacyId(event.world.dimension()) != selected) return;
                // Pin/load before either frozen observation or normal native initialization.
                prepare((ServerWorld) event.world);
                if (!frozen(event.world, definition)) return;
                NativeEntityHold hold = new NativeEntityHold();
                for (Entity entity : witnesses((ServerWorld) event.world)) hold.hold(entity);
                held.put(event.world, hold);
            } catch (Exception ex) { throw new IllegalStateException("Dimension tick preparation failed", ex); }
        } else if (event.phase == TickEvent.Phase.END) {
            NativeEntityHold hold = held.remove(event.world);
            if (hold != null) hold.restore();
        }
    }

    private void starting(RegisterCommandsEvent event) {
        for (String operation : new String[] {"read", "snapshot", "operate", "respawn"}) {
            event.getDispatcher().register(Commands.literal("advdimensions").requires(source -> source.hasPermission(4))
                    .then(Commands.literal(operation).then(Commands.argument("label", StringArgumentType.word()).executes(context -> {
                        try {
                            execute(context.getSource().getServer(), operation, StringArgumentType.getString(context, "label"));
                            return 1;
                        } catch (Exception ex) { throw new IllegalStateException("Dimension probe failed", ex); }
                    }))));
        }
    }

    private void execute(MinecraftServer server, String operation, String label) throws Exception {
        if (!MARKER.isFile() || !label.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Unmarked runtime or unsafe label");
        JsonObject definition = definition();
        ServerWorld overworld = server.getLevel(World.OVERWORLD);
        if (overworld == null || !marked(overworld)) throw new IllegalStateException("Unexpected overworld");
        ServerWorld world = server.getLevel(Native116Worlds.key(dimension(definition)));
        if (world == null || !marked(world) || Native116Worlds.legacyId(world.dimension()) != dimension(definition)) {
            throw new IllegalStateException("Dimension unavailable");
        }
        prepare(world);
        if (operation.equals("operate")) {
            live.add(world);
            for (Entity entity : witnesses(world)) entity.canUpdate(true);
        }
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

    private static void prepare(ServerWorld world) {
        if (!PINNED.contains(world)) {
            for (int x = 6; x <= 13; x++) for (int z = -2; z <= 2; z++) {
                world.setChunkForced(x, z, true);
                world.getChunk(x, z);
            }
            PINNED.add(world);
        }
        FakePlayer sentinel = FakePlayerFactory.getMinecraft(world);
        if (sentinel.connection == null) new OfflineConnection(world.getServer(), sentinel);
        sentinel.setPos(144.5, 100, 8.5);
        if (!world.players().contains(sentinel)) world.players().add(sentinel);
        for (int x = 6; x <= 13; x++) for (int z = -2; z <= 2; z++) world.getChunk(x, z);
    }

    private static List<Entity> witnesses(ServerWorld world) {
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
        List<Entity> result = new ArrayList<>();
        Native116Worlds.entities(world).forEach(entity -> collect(entity, seen, result));
        result.sort((a, b) -> a.getPersistentData().getString(WITNESS).compareTo(b.getPersistentData().getString(WITNESS)));
        return result;
    }

    private static void collect(Entity entity, Set<Entity> seen, List<Entity> result) {
        if (!seen.add(entity)) return;
        if (witnessed(entity)) result.add(entity);
        for (Entity passenger : entity.getPassengers()) collect(passenger, seen, result);
    }

    private static ServerPlayerEntity readPlayer(MinecraftServer server, ServerWorld world, JsonObject definition) {
        ServerPlayerEntity player = new ServerPlayerEntity(server, world,
                new GameProfile(UUID.fromString(definition.get("playerUuid").getAsString()), definition.get("playerName").getAsString()),
                new PlayerInteractionManager(world));
        if (server.getPlayerList().load(player) == null) throw new IllegalStateException("No player file loaded");
        if (Native116Worlds.legacyId(player.getLevel().dimension()) != dimension(definition) || player.getLevel() != world) {
            throw new IllegalStateException("Player loaded in wrong dimension");
        }
        return player;
    }

    private JsonObject snapshot(MinecraftServer server, ServerWorld world, JsonObject definition, String operation) throws Exception {
        JsonObject result = new JsonObject(); result.add("definition", definition);
        result.addProperty("operation", operation); result.addProperty("dimension", Native116Worlds.legacyId(world.dimension()));
        result.addProperty("gameTime", world.getGameTime()); result.addProperty("frozen", frozen(world, definition));
        result.addProperty("nativeOnLoad", nativeOnLoad(definition));
        boolean read = operation.equals("read") || operation.equals("respawn");
        result.addProperty("playerAdapter", read); result.add("playerWorldDimension", JsonNull.INSTANCE);
        if (read) {
            ServerPlayerEntity player = readPlayer(server, world, definition);
            result.addProperty("playerWorldDimension", Native116Worlds.legacyId(player.getLevel().dimension()));
            result.add("player", encode(player.saveWithoutId(new CompoundNBT())));
            if (operation.equals("respawn")) result.add("respawn", respawn(server, world, definition, player));
            else Native116PlayerIo.save(server.getPlayerList(), player);
        }
        JsonArray entities = new JsonArray();
        for (Entity entity : witnesses(world)) {
            // This native writer includes type ID and works for both roots and riding passengers.
            CompoundNBT nbt = entityNbt(entity);
            JsonObject entry = encode(nbt); entry.addProperty("witness", entity.getPersistentData().getString(WITNESS));
            entry.addProperty("uuid", entity.getUUID().toString()); entry.addProperty("ticksExisted", entity.tickCount);
            entry.addProperty("entityId", entity.getId());
            entry.addProperty("instanceId", System.identityHashCode(entity));
            entry.add("vehicleUuid", entity.getVehicle() == null ? JsonNull.INSTANCE : JSON.toJsonTree(entity.getVehicle().getUUID().toString()));
            JsonArray passengers = new JsonArray(); for (Entity passenger : entity.getPassengers()) passengers.add(passenger.getUUID().toString());
            entry.add("passengerUuids", passengers);
            Entity leash = entity instanceof MobEntity ? ((MobEntity) entity).getLeashHolder() : null;
            entry.add("leashHolderUuid", leash == null ? JsonNull.INSTANCE : JSON.toJsonTree(leash.getUUID().toString()));
            entry.add("leashHolderType", leash == null ? JsonNull.INSTANCE : JSON.toJsonTree(leash.getType().getRegistryName().toString()));
            BlockPos leashPos = leash instanceof HangingEntity ? ((HangingEntity) leash).getPos() : null;
            entry.add("leashHolderPosition", leashPos == null ? JsonNull.INSTANCE
                    : JSON.toJsonTree(new int[] {leashPos.getX(), leashPos.getY(), leashPos.getZ()}));
            entities.add(entry);
        }
        result.add("entities", entities);
        BlockPos pos = new BlockPos(176, 101, 8); JsonObject generator = new JsonObject();
        generator.addProperty("actualName", world.getBlockState(pos).getBlock().getRegistryName().toString());
        TileEntity tile = world.getBlockEntity(pos); if (tile != null) generator.add("tile", encode(tile.save(new CompoundNBT())));
        result.add("generator", generator);
        return result;
    }

    private static void platform(ServerWorld world) {
        for (int x = 156; x <= 166; x++) for (int z = 20; z <= 29; z++) {
            world.setBlock(new BlockPos(x, 99, z), Blocks.BEDROCK.defaultBlockState(), 2);
            world.setBlock(new BlockPos(x, 106, z), Blocks.BEDROCK.defaultBlockState(), 2);
            for (int y = 100; y < 106; y++) world.setBlock(new BlockPos(x, y, z),
                    (x == 156 || x == 166 || z == 20 || z == 29 ? Blocks.BEDROCK : Blocks.AIR).defaultBlockState(), 2);
        }
    }

    private static JsonObject respawn(MinecraftServer server, ServerWorld world, JsonObject definition, ServerPlayerEntity player) throws Exception {
        JsonObject options = definition.has("respawn") ? definition.getAsJsonObject("respawn") : new JsonObject();
        boolean keep = !options.has("keepInventory") || options.get("keepInventory").getAsBoolean();
        String bedMode = options.has("bedMode") ? options.get("bedMode").getAsString() : "forced";
        if (!bedMode.matches("forced|bed|missing")) throw new IllegalArgumentException("Invalid bed mode");
        ServerWorld destination = server.getLevel(World.OVERWORLD);
        platform(destination);
        if (bedMode.equals("bed")) {
            destination.setBlock(SPAWN, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST)
                    .setValue(BedBlock.PART, net.minecraft.state.properties.BedPart.FOOT), 2);
            destination.setBlock(SPAWN.east(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST)
                    .setValue(BedBlock.PART, net.minecraft.state.properties.BedPart.HEAD), 2);
        }
        player.setRespawnPosition(World.OVERWORLD, SPAWN, 0.0F, bedMode.equals("forced"), false);
        JsonObject result = new JsonObject(); result.addProperty("scope", "server-only-dummy-network");
        result.addProperty("handler", "ServerPlayNetHandler.PERFORM_RESPAWN");
        result.addProperty("keepInventory", keep); result.addProperty("bedMode", bedMode);
        CompoundNBT before = player.saveWithoutId(new CompoundNBT());
        result.addProperty("fromDimension", Native116Worlds.legacyId(player.getLevel().dimension())); result.add("before", encode(before));
        JsonObject diagnostics = new JsonObject(); result.add("spawnDiagnostics", diagnostics);
        diagnostics.add("before", spawnDiagnostics(player, before));
        Set<Entity> existing = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>()); Native116Worlds.entities(world).forEach(existing::add);
        boolean oldSource = world.getGameRules().getBoolean(net.minecraft.world.GameRules.RULE_KEEPINVENTORY),
                oldTarget = destination.getGameRules().getBoolean(net.minecraft.world.GameRules.RULE_KEEPINVENTORY);
        ServerPlayerEntity replacement = null;
        try {
            world.getGameRules().getRule(net.minecraft.world.GameRules.RULE_KEEPINVENTORY).set(keep, server);
            destination.getGameRules().getRule(net.minecraft.world.GameRules.RULE_KEEPINVENTORY).set(keep, server);
            OfflineConnection connection = new OfflineConnection(server, player);
            player.setHealth(0); player.die(DamageSource.GENERIC);
            CompoundNBT afterDeath = player.saveWithoutId(new CompoundNBT());
            result.add("afterDeath", encode(afterDeath));
            diagnostics.add("afterDeath", spawnDiagnostics(player, afterDeath));
            if (player.wonGame) throw new IllegalStateException("Expected ordinary death respawn, not queued End exit");
            connection.handleClientCommand(new CClientStatusPacket(CClientStatusPacket.State.PERFORM_RESPAWN));
            replacement = server.getPlayerList().getPlayer(player.getUUID());
            if (replacement == null || replacement == player) throw new IllegalStateException("Native respawn handler did not replace player");
            new OfflineConnection(server, replacement);
            CompoundNBT after = replacement.saveWithoutId(new CompoundNBT());
            result.add("after", encode(after));
            diagnostics.add("after", spawnDiagnostics(replacement, after));
            result.addProperty("toDimension", Native116Worlds.legacyId(replacement.getLevel().dimension()));
            BlockPos bed = replacement.getRespawnPosition();
            result.add("bedLocation", bed == null ? JsonNull.INSTANCE : JSON.toJsonTree(new int[] {bed.getX(), bed.getY(), bed.getZ()}));
            JsonArray drops = new JsonArray();
            for (Entity entity : Native116Worlds.entities(world).collect(java.util.stream.Collectors.toList())) if (entity instanceof ItemEntity && !existing.contains(entity)) {
                CompoundNBT nbt = new CompoundNBT(); if (!entity.saveAsPassenger(nbt)) throw new IllegalStateException("Could not serialize death drop");
                drops.add(encode(nbt));
            }
            result.add("drops", drops);
            return result;
        } finally {
            try {
                if (replacement != null) server.getPlayerList().remove(replacement);
            } finally {
                world.getGameRules().getRule(net.minecraft.world.GameRules.RULE_KEEPINVENTORY).set(oldSource, server);
                destination.getGameRules().getRule(net.minecraft.world.GameRules.RULE_KEEPINVENTORY).set(oldTarget, server);
            }
        }
    }

    private static JsonObject spawnDiagnostics(ServerPlayerEntity player, CompoundNBT nbt) {
        JsonObject result = new JsonObject(); result.addProperty("queryDimension", 0);
        result.addProperty("playerWorldDimension", Native116Worlds.legacyId(player.getLevel().dimension()));
        result.addProperty("spawnDimension", Native116Worlds.legacyId(player.getRespawnDimension()));
        BlockPos bed = player.getRespawnPosition();
        result.add("bedLocation", bed == null ? JsonNull.INSTANCE : JSON.toJsonTree(new int[] {bed.getX(), bed.getY(), bed.getZ()}));
        result.addProperty("spawnForced", player.isRespawnForced());
        JsonObject spawn = new JsonObject();
        for (String key : new String[] {"SpawnX", "SpawnY", "SpawnZ"}) {
            spawn.add(key, nbt.contains(key, 99) ? JSON.toJsonTree(nbt.getInt(key)) : JsonNull.INSTANCE);
        }
        spawn.add("SpawnForced", nbt.contains("SpawnForced", 99) ? JSON.toJsonTree(nbt.getBoolean("SpawnForced")) : JsonNull.INSTANCE);
        spawn.add("SpawnDimension", nbt.contains("SpawnDimension", 8) ? JSON.toJsonTree(nbt.getString("SpawnDimension"))
                : nbt.contains("SpawnDimension", 99) ? JSON.toJsonTree(nbt.getInt("SpawnDimension")) : JsonNull.INSTANCE);
        result.add("serializedSpawn", spawn);
        return result;
    }

    private static final class OfflineConnection extends ServerPlayNetHandler {
        OfflineConnection(MinecraftServer server, ServerPlayerEntity player) { super(server, new NetworkManager(PacketDirection.SERVERBOUND), player); }
        @Override public void send(IPacket<?> packet) { /* Deliberately no socket or client login. */ }
        @Override public void send(IPacket<?> packet, GenericFutureListener<? extends Future<? super Void>> listener) {
            // Death-message packets use this overload; no native network work is queued.
        }
    }

    private static CompoundNBT entityNbt(Entity entity) {
        if (entity instanceof LeashKnotEntity) {
            CompoundNBT nbt = entity.saveWithoutId(new CompoundNBT());
            nbt.putString("id", entity.getType().getRegistryName().toString());
            return nbt;
        }
        CompoundNBT nbt = new CompoundNBT();
        if (!entity.saveAsPassenger(nbt)) throw new IllegalStateException("Could not serialize dimension witness");
        return nbt;
    }

    private static JsonObject encode(CompoundNBT nbt) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); CompressedStreamTools.writeCompressed(nbt, bytes);
        JsonObject result = new JsonObject(); result.addProperty("nbtBase64", Base64.getEncoder().encodeToString(bytes.toByteArray())); return result;
    }
}
