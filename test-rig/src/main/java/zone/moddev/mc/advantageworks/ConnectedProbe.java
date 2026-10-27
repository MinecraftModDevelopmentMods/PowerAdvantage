package zone.moddev.mc.advantageworks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.BlockState;
import net.minecraft.command.Commands;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.Property;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import zone.moddev.mc.advantageworks.connected.ConnectedSession;

/** The target adapter has no construction, inventory, energy or network-reset operation. */
final class ConnectedProbe implements ConnectedSession.Port {
    private ServerWorld world;
    private ConnectedSession session;
    private final List<TileEntity> held = new ArrayList<>();

    static void register() {
        if (!new File("advantage-connected.allow").isFile()) return;
        ConnectedProbe probe = new ConnectedProbe();
        MinecraftForge.EVENT_BUS.addListener(probe::tick);
        MinecraftForge.EVENT_BUS.addListener(probe::starting);
    }

    private ConnectedSession get(ServerWorld current) throws Exception {
        if (!current.getServer().getWorldData().getLevelName().equals("AdvantageWorksMigration")) throw new IllegalStateException("Unexpected connected world");
        if (world != current) {
            world = current;
            session = new ConnectedSession(this);
        }
        return session;
    }

    private void tick(TickEvent.WorldTickEvent event) {
        if (event.world.isClientSide || Native116Worlds.legacyId(event.world.dimension()) != 0) return;
        try {
            ConnectedSession current = get((ServerWorld) event.world);
            if (event.phase == TickEvent.Phase.START) current.start(); else current.end();
        } catch (Exception ex) { throw new IllegalStateException("Connected probe failed", ex); }
    }

    private void starting(FMLServerStartingEvent event) {
        event.getServer().getCommands().getDispatcher().register(Commands.literal("advconnected").requires(s -> s.hasPermission(4))
                .then(Commands.literal("snapshot").then(Commands.argument("label", StringArgumentType.word()).executes(c -> {
                    execute(c.getSource().getLevel(), "snapshot", StringArgumentType.getString(c, "label"), 0); return 1;
                })))
                .then(Commands.literal("step").then(Commands.argument("ticks", IntegerArgumentType.integer(1, 4000))
                        .then(Commands.argument("label", StringArgumentType.word()).executes(c -> {
                            execute(c.getSource().getLevel(), "step", StringArgumentType.getString(c, "label"), IntegerArgumentType.getInteger(c, "ticks")); return 1;
                        })))));
    }

    private void execute(ServerWorld current, String operation, String label, int ticks) {
        try { get(current).execute(operation, label, ticks, false); }
        catch (Exception ex) { throw new IllegalStateException("Connected probe failed", ex); }
    }

    @Override public void prepare(JsonObject definition) {
        JsonArray b = definition.getAsJsonArray("bounds");
        for (int x = b.get(0).getAsInt() >> 4; x <= b.get(3).getAsInt() >> 4; x++) {
            for (int z = b.get(2).getAsInt() >> 4; z <= b.get(5).getAsInt() >> 4; z++) {
                world.setChunkForced(x, z, true);
                world.getChunk(x, z);
            }
        }
        net.minecraftforge.common.util.FakePlayer sentinel = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        sentinel.setPos(0.5, 80, 0.5);
        if (!world.players().contains(sentinel)) world.players().add(sentinel);
    }

    @Override public void build(JsonObject definition) { throw new UnsupportedOperationException("Target construction is forbidden"); }
    @Override public void hold() { held.addAll(world.tickableBlockEntities); world.tickableBlockEntities.clear(); }
    @Override public void restore() { for (TileEntity tile : held) if (!tile.isRemoved() && !world.tickableBlockEntities.contains(tile)) world.tickableBlockEntities.add(tile); held.clear(); }
    @Override public long time() { return world.getGameTime(); }

    @Override public JsonArray snapshot(JsonObject definition) throws Exception {
        JsonArray result = new JsonArray();
        for (JsonElement value : definition.getAsJsonArray("blocks")) {
            JsonObject e = value.getAsJsonObject();
            JsonObject entry = new com.google.gson.Gson().fromJson(e.toString(), JsonObject.class);
            entry.remove("seed");
            BlockPos pos = new BlockPos(e.get("x").getAsInt(), e.get("y").getAsInt(), e.get("z").getAsInt());
            BlockState state = world.getBlockState(pos);
            entry.addProperty("actualName", state.getBlock().getRegistryName().toString());
            JsonObject properties = new JsonObject();
            for (Property<?> p : state.getProperties()) properties.addProperty(p.getName(), state.getValue(p).toString().toLowerCase(java.util.Locale.ROOT));
            entry.add("properties", properties);
            TileEntity tile = world.getBlockEntity(pos);
            if (tile != null) {
                CompoundNBT tag = tile.save(new CompoundNBT());
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
}
