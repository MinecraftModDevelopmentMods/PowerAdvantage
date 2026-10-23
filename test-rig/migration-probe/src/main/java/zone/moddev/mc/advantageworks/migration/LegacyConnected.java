package zone.moddev.mc.advantageworks.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import zone.moddev.mc.advantageworks.connected.ConnectedSession;

/** Builds only new legacy fixtures; imported equipment is never constructed here. */
final class LegacyConnected implements ConnectedSession.Port {
    private final WorldServer world;
    private final List<TileEntity> held = new ArrayList<>();
    private ConnectedSession session;
    private static LegacyConnected instance;
    private static Object owner;

    private LegacyConnected(WorldServer world) { this.world = world; }

    static void register(Object mod) { owner = mod; MinecraftForge.EVENT_BUS.register(new Events()); }

    private static LegacyConnected get(WorldServer world) throws Exception {
        if (!world.getWorldInfo().getWorldName().equals("AdvantageWorksMigration")) throw new IllegalStateException("Unexpected connected world");
        if (instance == null || instance.world != world) {
            instance = new LegacyConnected(world);
            instance.session = new ConnectedSession(instance);
        }
        return instance;
    }

    private static boolean enabled() { return new File("advantage-connected.allow").isFile(); }

    static CommandBase command() {
        return new CommandBase() {
            @Override public String getName() { return "advconnected"; }
            @Override public String getUsage(ICommandSender sender) { return "advconnected build|snapshot <label> | step <ticks> <label>"; }
            @Override public int getRequiredPermissionLevel() { return 4; }
            @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
                try {
                    if (!enabled() || args.length < 2) throw new IllegalStateException("Unmarked connected fixture");
                    String operation = args[0];
                    int ticks = operation.equals("step") ? Integer.parseInt(args[1]) : 0;
                    String label = args[operation.equals("step") ? 2 : 1];
                    get(net.minecraftforge.common.DimensionManager.getWorld(0)).session.execute(operation, label, ticks, true);
                } catch (Exception ex) { throw new IllegalStateException("Connected probe failed", ex); }
            }
        };
    }

    public static final class Events {
        @SubscribeEvent public void tick(TickEvent.WorldTickEvent event) {
            if (!enabled() || event.world.isRemote || event.world.provider.getDimension() != 0) return;
            try {
                ConnectedSession current = get((WorldServer) event.world).session;
                if (event.phase == TickEvent.Phase.START) current.start(); else current.end();
            } catch (Exception ex) { throw new IllegalStateException("Connected probe failed", ex); }
        }
    }

    @Override public void prepare(JsonObject definition) {
        JsonArray b = definition.getAsJsonArray("bounds");
        net.minecraftforge.common.ForgeChunkManager.Ticket ticket = null;
        int used = 0;
        for (int x = b.get(0).getAsInt() >> 4; x <= b.get(3).getAsInt() >> 4; x++) {
            for (int z = b.get(2).getAsInt() >> 4; z <= b.get(5).getAsInt() >> 4; z++) {
                if (ticket == null || used >= ticket.getChunkListDepth()) {
                    ticket = net.minecraftforge.common.ForgeChunkManager.requestTicket(owner, world, net.minecraftforge.common.ForgeChunkManager.Type.NORMAL);
                    if (ticket == null) throw new IllegalStateException("No bounded fixture chunk ticket");
                    used = 0;
                }
                net.minecraftforge.common.ForgeChunkManager.forceChunk(ticket, new net.minecraft.util.math.ChunkPos(x, z));
                used++;
                world.getChunkProvider().provideChunk(x, z);
            }
        }
        net.minecraftforge.common.util.FakePlayer sentinel = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        sentinel.setPosition(0.5, 80, 0.5);
        if (!world.playerEntities.contains(sentinel)) world.playerEntities.add(sentinel);
    }

    @Override public void hold() { held.addAll(world.tickableTileEntities); world.tickableTileEntities.clear(); }
    @Override public void restore() { for (TileEntity tile : held) if (!tile.isInvalid() && !world.tickableTileEntities.contains(tile)) world.tickableTileEntities.add(tile); held.clear(); }
    @Override public long time() { return world.getTotalWorldTime(); }

    @Override public void build(JsonObject definition) throws Exception {
        for (JsonElement value : definition.getAsJsonArray("blocks")) {
            JsonObject e = value.getAsJsonObject();
            BlockPos pos = position(e);
            if (!world.isAirBlock(pos)) throw new IllegalStateException("Source fixture position already occupied: " + pos);
            ResourceLocation name = new ResourceLocation(e.get("name").getAsString());
            if (!Block.REGISTRY.containsKey(name)) throw new IllegalStateException("Missing source block: " + name);
            Block block = Block.REGISTRY.getObject(name);
            world.setBlockState(pos, block.getStateFromMeta(e.has("metadata") ? e.get("metadata").getAsInt() : 0), 2);
            TileEntity tile = world.getTileEntity(pos);
            if (tile != null) {
                if (e.has("seed")) {
                    NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound());
                    NBTTagCompound seed = JsonToNBT.getTagFromJson(e.get("seed").getAsString());
                    for (String key : seed.getKeySet()) {
                        if (!key.equals("Tank") && !key.equals("Items")) throw new IllegalArgumentException("Only initial fluid/items may be supplied");
                        tag.setTag(key, seed.getTag(key));
                    }
                    tile.readFromNBT(tag);
                }
                tile.getTileData().setString("AdvConnectedWitness", e.get("id").getAsString());
                tile.markDirty();
            }
        }
    }

    @Override public JsonArray snapshot(JsonObject definition) throws Exception {
        JsonArray result = new JsonArray();
        for (JsonElement value : definition.getAsJsonArray("blocks")) {
            JsonObject e = value.getAsJsonObject();
            JsonObject entry = new com.google.gson.Gson().fromJson(e.toString(), JsonObject.class);
            entry.remove("seed");
            BlockPos pos = position(e);
            IBlockState state = world.getBlockState(pos);
            entry.addProperty("actualName", state.getBlock().getRegistryName().toString());
            JsonObject properties = new JsonObject();
            for (java.util.Map.Entry<IProperty<?>, Comparable<?>> p : state.getProperties().entrySet()) properties.addProperty(p.getKey().getName(), p.getValue().toString().toLowerCase(java.util.Locale.ROOT));
            entry.add("properties", properties);
            TileEntity tile = world.getTileEntity(pos);
            if (tile != null) {
                NBTTagCompound tag = tile.writeToNBT(new NBTTagCompound());
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                net.minecraft.nbt.CompressedStreamTools.writeCompressed(tag, bytes);
                entry.addProperty("nbt", tag.toString());
                entry.addProperty("nbtBase64", java.util.Base64.getEncoder().encodeToString(bytes.toByteArray()));
                entry.addProperty("objectIdentity", System.identityHashCode(tile));
                entry.addProperty("witness", tile.getTileData().getString("AdvConnectedWitness"));
            }
            result.add(entry);
        }
        return result;
    }

    private static BlockPos position(JsonObject e) { return new BlockPos(e.get("x").getAsInt(), e.get("y").getAsInt(), e.get("z").getAsInt()); }
}
