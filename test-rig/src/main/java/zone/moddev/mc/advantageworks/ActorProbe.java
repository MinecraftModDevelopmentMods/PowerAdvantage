package zone.moddev.mc.advantageworks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;
import net.minecraft.command.Commands;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

/** Real server player-file adapter and saved-entity witnesses; not a client login. */
final class ActorProbe {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File MARKER = new File("advantage-migration.allow");
    private final java.util.Set<net.minecraft.world.World> liveWorlds = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private final java.util.Map<net.minecraft.world.World, java.util.List<Entity>> heldActors = new java.util.IdentityHashMap<>();

    static void register() {
        if (!MARKER.isFile()) return;
        ActorProbe probe = new ActorProbe();
        MinecraftForge.EVENT_BUS.addListener(probe::tick);
        MinecraftForge.EVENT_BUS.addListener(probe::starting);
    }

    private boolean frozen(net.minecraft.world.World world) {
        return new File("actor-fixture.json").isFile() && !new File("actor-operate.allow").isFile() && !liveWorlds.contains(world);
    }

    private void tick(TickEvent.WorldTickEvent event) {
        if (event.world.isRemote) return;
        if (event.phase == TickEvent.Phase.START && frozen(event.world)) {
            java.util.List<Entity> held = new ArrayList<>();
            for (Entity entity : event.world.loadedEntityList) {
                if (entity.getEntityData().contains("AdvWorksActor", 8)) held.add(entity);
            }
            event.world.loadedEntityList.removeAll(held);
            heldActors.put(event.world, held);
        } else if (event.phase == TickEvent.Phase.END) {
            java.util.List<Entity> held = heldActors.remove(event.world);
            if (held != null) event.world.loadedEntityList.addAll(held);
        }
    }

    private void starting(FMLServerStartingEvent event) {
        for (String operation : new String[] {"snapshot", "read", "operate"}) {
            event.getCommandDispatcher().register(Commands.literal("advactors").requires(source -> source.hasPermissionLevel(4))
                    .then(Commands.literal(operation).then(Commands.argument("label", StringArgumentType.word()).executes(context -> {
                        try {
                            snapshot(context.getSource().getServer(), context.getSource().getWorld(),
                                    operation, StringArgumentType.getString(context, "label"));
                            return 1;
                        } catch (Exception ex) { throw new IllegalStateException("Actor probe failed", ex); }
                    }))));
        }
    }

    private void snapshot(MinecraftServer server, WorldServer world, String operation, String label) throws Exception {
        if (!MARKER.isFile() || !world.getWorldInfo().getWorldName().equals("AdvantageWorksMigration")
                || !label.matches("[a-zA-Z0-9_-]+")) throw new IllegalStateException("Unmarked world or unsafe label");
        JsonObject definition = JSON.fromJson(new String(Files.readAllBytes(new File("actor-fixture.json").toPath()),
                StandardCharsets.UTF_8), JsonObject.class);
        // Entity updates require neighbouring chunks and a non-idle world. This
        // sentinel is never logged in and never reads/writes the witness player.
        net.minecraftforge.common.util.FakePlayer sentinel = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        sentinel.setPosition(128.5, 80, -16.5);
        if (!world.playerEntities.contains(sentinel)) world.playerEntities.add(sentinel);
        for (int x = 6; x <= 13; x++) {
            for (int z = -2; z <= 2; z++) world.getChunk(x, z);
        }
        JsonObject result = new JsonObject();
        if (operation.equals("operate")) liveWorlds.add(world);
        result.addProperty("frozen", frozen(world));
        result.addProperty("gameTime", world.getGameTime());
        try (java.io.FileInputStream input = new java.io.FileInputStream(new File("AdvantageWorksMigration/level.dat"))) {
            NBTTagCompound data = CompressedStreamTools.readCompressed(input).getCompound("Data");
            if (data.contains("Player", 10)) result.add("embedded", encode(data.getCompound("Player")));
        }
        boolean read = operation.equals("read");
        result.addProperty("playerAdapter", read);
        if (read) {
            GameProfile profile = new GameProfile(UUID.fromString(definition.get("playerUuid").getAsString()),
                    definition.get("playerName").getAsString());
            EntityPlayerMP player = new EntityPlayerMP(server, world, profile, new PlayerInteractionManager(world));
            if (server.getPlayerList().readPlayerDataFromFile(player) == null) throw new IllegalStateException("No player file loaded");
            result.add("player", encode(player.writeWithoutTypeId(new NBTTagCompound())));
            world.getSaveHandler().getPlayerNBTManager().writePlayerData(player);
        }
        JsonArray entities = new JsonArray();
        for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
            if (!entity.getEntityData().contains("AdvWorksActor", 8)) continue;
            if (operation.equals("operate")) entity.canUpdate(true);
            NBTTagCompound nbt = new NBTTagCompound();
            if (!entity.writeUnlessRemoved(nbt)) throw new IllegalStateException("Could not serialize actor");
            JsonObject entry = encode(nbt);
            entry.addProperty("witness", entity.getEntityData().getString("AdvWorksActor"));
            entry.addProperty("ticksExisted", entity.ticksExisted);
            entities.add(entry);
        }
        result.add("entities", entities);
        Files.write(new File("actors-" + label + ".json").toPath(), JSON.toJson(result).getBytes(StandardCharsets.UTF_8));
        System.out.println("ADVACTORS COMPLETE " + label);
    }

    private static JsonObject encode(NBTTagCompound nbt) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(nbt, bytes);
        JsonObject result = new JsonObject();
        result.addProperty("nbtBase64", Base64.getEncoder().encodeToString(bytes.toByteArray()));
        return result;
    }
}
