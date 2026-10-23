package zone.moddev.mc.advantageworks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import net.minecraft.state.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.Commands;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

/** Read-only state witnesses in explicitly marked disposable migration worlds. */
final class MigrationProbe {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File MARKER = new File("advantage-migration.allow");

    static void register() {
        if (!MARKER.isFile()) return;
        MigrationProbe probe = new MigrationProbe();
        MinecraftForge.EVENT_BUS.addListener(probe::load);
        MinecraftForge.EVENT_BUS.addListener(probe::tick);
        MinecraftForge.EVENT_BUS.addListener(probe::starting);
    }

    private void load(WorldEvent.Load event) {
        if (event.getWorld() instanceof World) freeze((World) event.getWorld());
    }
    private void tick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.START) freeze(event.world);
    }
    private void freeze(World world) {
        if (!world.isRemote && !new File("migration-operate.allow").isFile()) world.tickableTileEntities.clear();
    }

    private void starting(FMLServerStartingEvent event) {
        event.getCommandDispatcher().register(Commands.literal("advprobe")
                .requires(source -> source.hasPermissionLevel(4))
                .then(Commands.literal("snapshot")
                        .then(Commands.argument("label", StringArgumentType.word()).executes(context -> {
                            try {
                                snapshot(context.getSource().getWorld(), StringArgumentType.getString(context, "label"));
                                System.out.println("ADVPROBE COMPLETE snapshot");
                                return 1;
                            } catch (Exception ex) { throw new IllegalStateException("Migration probe failed", ex); }
                        }))));
    }

    private void snapshot(World world, String label) throws Exception {
        if (!MARKER.isFile() || !world.getWorldInfo().getWorldName().equals("AdvantageWorksMigration")) {
            throw new IllegalStateException("Refusing an unmarked or unexpected world");
        }
        if (!label.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Invalid snapshot label");
        JsonArray definition = JSON.fromJson(new String(Files.readAllBytes(new File("migration-fixture.json").toPath()),
                StandardCharsets.UTF_8), JsonArray.class);
        JsonArray result = new JsonArray();
        for (JsonElement element : definition) {
            JsonObject entry = JSON.fromJson(JSON.toJson(element), JsonObject.class);
            BlockPos pos = new BlockPos(entry.get("x").getAsInt(), entry.get("y").getAsInt(), entry.get("z").getAsInt());
            IBlockState state = world.getBlockState(pos);
            entry.addProperty("actualName", state.getBlock().getRegistryName().toString());
            JsonObject properties = new JsonObject();
            for (IProperty<?> property : state.getProperties()) {
                properties.addProperty(property.getName(), state.get(property).toString().toLowerCase(Locale.ROOT));
            }
            entry.add("properties", properties);
            TileEntity tile = world.getTileEntity(pos);
            if (tile != null) {
                tile.markDirty();
                NBTTagCompound nbt = tile.write(new NBTTagCompound());
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
