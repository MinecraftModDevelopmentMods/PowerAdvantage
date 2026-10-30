package zone.moddev.mc.advantageworks;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import net.minecraft.command.Commands;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

/** Read-only readiness witnesses. This probe never acquires a chunk or changes machinery. */
final class ChunkReadinessProbe {
    static void register() { MinecraftForge.EVENT_BUS.addListener(ChunkReadinessProbe::commands); }

    private static void commands(FMLServerStartingEvent event) {
        event.getCommandDispatcher().register(Commands.literal("advchunks").requires(source -> source.hasPermissionLevel(4))
                .then(Commands.argument("label", StringArgumentType.word())
                .then(Commands.argument("x1", IntegerArgumentType.integer(-1024, 100000))
                .then(Commands.argument("z1", IntegerArgumentType.integer(-1024, 100000))
                .then(Commands.argument("x2", IntegerArgumentType.integer(-1024, 100000))
                .then(Commands.argument("z2", IntegerArgumentType.integer(-1024, 100000)).executes(context -> {
                    observe(context.getSource().getWorld(), StringArgumentType.getString(context, "label"),
                            IntegerArgumentType.getInteger(context, "x1"), IntegerArgumentType.getInteger(context, "z1"),
                            IntegerArgumentType.getInteger(context, "x2"), IntegerArgumentType.getInteger(context, "z2"));
                    return 1;
                })))))));
    }

    private static void observe(WorldServer world, String label, int x1, int z1, int x2, int z2) {
        if (!new File("advantage-works.allow").isFile()
                || !world.getWorldInfo().getWorldName().equals("AdvantageWorksMigration")
                || !label.matches("[A-Za-z0-9_-]+") || x2 < x1 || z2 < z1 || x2 - x1 > 512 || z2 - z1 > 512)
            throw new IllegalArgumentException("Unmarked world, unsafe label or unbounded chunk request");
        JsonArray rows = new JsonArray();
        boolean ready = true;
        for (int x = x1 >> 4; x <= x2 >> 4; x++) for (int z = z1 >> 4; z <= z2 >> 4; z++) {
            Chunk chunk = world.getChunkProvider().loadedChunks.get(net.minecraft.util.math.ChunkPos.asLong(x, z));
            boolean loaded = chunk != null && chunk.isLoaded();
            ready &= loaded;
            JsonObject row = new JsonObject();
            row.addProperty("x", x); row.addProperty("z", z); row.addProperty("loaded", loaded); rows.add(row);
        }
        JsonObject result = new JsonObject();
        result.addProperty("label", label); result.addProperty("worldTime", world.getGameTime());
        result.addProperty("ready", ready); result.add("chunks", rows);
        try {
            File file = new File("advantage-works-results", "chunk-readiness-" + label + ".json");
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(result).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW);
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot retain native readiness evidence", failure); }
        System.out.println("ADVCHUNKS OBSERVED " + label);
    }
}
