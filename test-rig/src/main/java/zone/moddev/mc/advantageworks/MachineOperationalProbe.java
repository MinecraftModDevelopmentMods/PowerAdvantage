package zone.moddev.mc.advantageworks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.CommandException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/** Natural world ticks and recorded finite resources; never drives tickUpdate directly. */
final class MachineOperationalProbe {
    private static final File MARKER = new File("advantage-machine-operation.allow");
    private static final File FIXTURE = new File("machine-operation-fixture.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MachineOperationalProbe instance;
    private final List<Run> runs = new ArrayList<>();
    private final List<TileEntity> held = new ArrayList<>();
    private final NativeEntityHold heldEntities = new NativeEntityHold();
    private WorldServer world;
    private boolean paused = true;
    private boolean preparing;
    private int prepareIndex;
    private String label;
    private long campaignStart;
    private long nextPreparation;
    private JsonArray inspectionCases, inspectionResults;
    private int inspectionIndex;
    private String inspectionLabel;
    private InspectionPlan inspectionPending;

    static void register() {
        if (!MARKER.isFile()) return;
        MachineOperationalProbe probe = new MachineOperationalProbe();
        instance = probe;
        MinecraftForge.EVENT_BUS.register(probe);
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void loadedChunk(net.minecraftforge.event.world.ChunkEvent.Load event) {
        if (!paused || !FIXTURE.isFile() || !(event.getWorld() instanceof WorldServer)) return;
        WorldServer current = (WorldServer)event.getWorld();
        if (current.provider.getDimension() != 0) return;
        // Chunk.onLoad adds its tiles before this event, including during startup and world.tick.
        held.addAll(current.tickableTileEntities);
        current.tickableTileEntities.clear();
        for (Entity entity : new ArrayList<>(current.loadedEntityList))
            if (!(entity instanceof net.minecraft.entity.player.EntityPlayer)) heldEntities.hold(entity);
    }

    static void registerCommands(FMLServerStartingEvent event) {
        if (instance == null) return;
        event.registerServerCommand(new CommandBase() {
            @Override public String getName() { return "advmachines"; }
            @Override public String getUsage(ICommandSender sender) { return "/advmachines run <group> <label> | inspect <label>"; }
            @Override public int getRequiredPermissionLevel() { return 4; }
            @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                if (args.length == 3 && args[0].equals("run")) instance.start(server.getWorld(0), args[1], args[2]);
                else if (args.length == 2 && args[0].equals("inspect")) instance.inspect(server.getWorld(0), args[1]);
                else throw new CommandException(getUsage(sender));
            }
        });
    }

    private static List<MachineScenario> catalog() {
        List<MachineScenario> scenarios = new ArrayList<>();
        PowerOperationalCases.register(scenarios);
        BucketOperationalCases.register(scenarios);
        BatteryOperationalCases.register(scenarios);
        DrillOperationalCases.register(scenarios);
        SteamNetworkLifecycleCases.register(scenarios);
        ElectricLifecycleOperationalCases.register(scenarios);
        for (String extension : new String[]{"SteamOperationalCases", "ElectricOperationalCases"}) {
            try {
                Class<?> type = Class.forName("zone.moddev.mc.advantageworks." + extension);
                java.lang.reflect.Method method = type.getDeclaredMethod("register", List.class);
                method.setAccessible(true);
                method.invoke(null, scenarios);
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Incomplete operating catalog: " + extension, error);
            }
        }
        LegacyPartialNetworkCases.register(scenarios);
        Set<String> ids = new HashSet<>();
        for (MachineScenario scenario : scenarios) if (!ids.add(scenario.id)) throw new IllegalArgumentException("Duplicate case " + scenario.id);
        if (scenarios.isEmpty()) throw new IllegalStateException("No operating tests");
        return scenarios;
    }

    private void authorize(WorldServer current, String requestedLabel) {
        if (!MARKER.isFile() || !current.getWorldInfo().getWorldName().equals("AdvantageWorksMigration")
                || !requestedLabel.matches("[A-Za-z0-9_-]+")) throw new IllegalStateException("Unmarked or unsafe operating world");
        world = current;
        FakePlayer sentinel = FakePlayerFactory.getMinecraft(world);
        sentinel.setPosition(49900.5, 80, 49900.5);
        if (!world.playerEntities.contains(sentinel)) world.playerEntities.add(sentinel);
    }

    private void start(WorldServer current, String group, String requestedLabel) {
        authorize(current, requestedLabel);
        if (FIXTURE.exists() || !runs.isEmpty() || !paused) throw new IllegalStateException("Construction is single-use; never rebuild a restarted fixture");
        boolean endurance = group.equals("endurance") || group.equals("endurance-smoke");
        List<MachineScenario> catalog;
        if (endurance) {
            catalog = new ArrayList<>();
            if (group.equals("endurance")) SteamNetworkLifecycleCases.registerEndurance(catalog);
            else SteamNetworkLifecycleCases.registerEnduranceSmoke(catalog);
        } else catalog = catalog();
        JsonArray declarations = new JsonArray();
        for (int i = 0; i < catalog.size(); i++) {
            MachineScenario scenario = catalog.get(i);
            if (!group.equals("all") && !endurance && !scenario.id.startsWith(group + "/")) continue;
            Run run = new Run(scenario, new MachineContext(world, i));
            runs.add(run);
            JsonObject entry = new JsonObject();
            entry.addProperty("id", scenario.id); entry.addProperty("contract", scenario.contract);
            entry.addProperty("ticks", scenario.ticks); entry.addProperty("exclusive", scenario.exclusive);
            declarations.add(entry);
        }
        if (runs.isEmpty()) throw new IllegalArgumentException("Unknown or empty operating group " + group);
        label = requestedLabel;
        campaignStart = world.getTotalWorldTime();
        JsonObject description = new JsonObject();
        description.addProperty("group", group);
        JsonArray notApplicable = new JsonArray();
        for (String machine : new String[]{"power-drain", "steam-pump", "electric-pump"}) {
            JsonObject excluded = new JsonObject();
            excluded.addProperty("id", "pickup/" + machine);
            excluded.addProperty("status", "NOT_APPLICABLE");
            excluded.addProperty("reason", "Minecraft 1.12.2 has no waterlogged block state; ordinary sources are tested separately");
            notApplicable.add(excluded);
        }
        description.add("notApplicable", notApplicable);
        description.add("cases", declarations);
        write("machine-catalog-" + label + ".json", description);
        preparing = true;
        System.out.println("ADVMACHINES START " + label + " cases=" + runs.size());
    }

    private void setup(Run run) {
        run.started = true;
        try {
            run.context.floor();
            run.scenario.setup.accept(run.context);
            run.context.validateTickRegistration();
            run.before = run.context.snapshot();
            run.startTick = world.getTotalWorldTime();
            run.context.activationTick = run.startTick;
            run.context.settingUp = false;
        } catch (Throwable failure) { fail(run, "setup", failure); }
    }

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public void tick(TickEvent.WorldTickEvent event) {
        if (event.world.isRemote || !(event.world instanceof WorldServer)
                || event.world.provider.getDimension() != 0) return;
        WorldServer current = (WorldServer) event.world;
        if (event.phase == TickEvent.Phase.START && paused) {
            held.addAll(current.tickableTileEntities);
            current.tickableTileEntities.clear();
            for (Entity entity : new ArrayList<>(current.loadedEntityList)) if (!(entity instanceof net.minecraft.entity.player.EntityPlayer)) heldEntities.hold(entity);
        } else if (event.phase == TickEvent.Phase.END) {
            for (TileEntity tile : held) if (!tile.isInvalid() && !current.tickableTileEntities.contains(tile)) current.tickableTileEntities.add(tile);
            held.clear(); heldEntities.restore();
            if (current != world) return;
            if (inspectionCases != null) {
                if (world.getTotalWorldTime() >= nextPreparation) inspectNext();
                return;
            }
            if (preparing) {
                if (world.getTotalWorldTime() < nextPreparation) return;
                while (prepareIndex < runs.size()) {
                    Run run = runs.get(prepareIndex);
                    if (!run.chunksRequested) {
                        run.context.prepareChunks();
                        run.chunksRequested = true;
                        run.readinessDeadline = world.getTotalWorldTime() + 80;
                        nextPreparation = world.getTotalWorldTime() + 1;
                        return;
                    }
                    if (!run.context.chunksReady()) {
                        if (world.getTotalWorldTime() > run.readinessDeadline) {
                            fail(run, "chunk-readiness", new IllegalStateException("Native chunk load did not complete"));
                            prepareIndex++;
                        }
                        nextPreparation = world.getTotalWorldTime() + 1;
                        return;
                    }
                    prepareIndex++;
                    if (run.scenario.exclusive) continue;
                    setup(run);
                    nextPreparation = world.getTotalWorldTime() + 20;
                    System.out.println("ADVMACHINES PREPARED " + run.scenario.id);
                    return;
                }
                for (Run run : runs) if (run.started && !run.finished) {
                    run.startTick = world.getTotalWorldTime();
                    run.context.activationTick = run.startTick;
                }
                preparing = false;
                paused = false;
                System.out.println("ADVMACHINES MEASURING " + label);
                return;
            }
            if (paused) return;
            // Measured END phase only: preparation and cold inspection returned above.
            for (Run run : runs) if (run.started && !run.finished && run.scenario.timed != null) {
                try {
                    run.scenario.timed.accept(run.context);
                } catch (Throwable failure) {
                    fail(run, "timed", failure);
                    run.finishTick = world.getTotalWorldTime();
                    run.after = run.context.snapshot();
                }
            }
            for (Run run : runs) if (run.started && !run.finished && world.getTotalWorldTime() - run.startTick >= run.scenario.ticks) {
                try {
                    run.scenario.verify.accept(run.context);
                    if (run.context.verified == 0) throw new AssertionError("No measured operating outcome");
                    cleanup(run);
                    run.finished = true;
                    run.status = "PASS";
                } catch (Throwable failure) { fail(run, "verify", failure); }
                run.finishTick = world.getTotalWorldTime();
                run.after = run.context.snapshot();
            }
            if (runs.stream().anyMatch(run -> run.started && !run.finished)) return;
            for (Run run : runs) if (!run.started) { setup(run); return; }
            paused = true;
            finish();
        }
    }

    private void fail(Run run, String phase, Throwable failure) {
        try {
            cleanup(run);
        } catch (Throwable cleanupFailure) {
            if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
        }
        run.finished = true; run.status = "FAIL";
        run.failure = phase + ": " + failure;
        java.io.StringWriter trace = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(trace));
        run.trace = trace.toString();
        JsonObject failed = new JsonObject();
        failed.addProperty("id", run.scenario.id); failed.addProperty("phase", phase);
        failed.addProperty("status", "FAIL"); failed.addProperty("failure", run.failure);
        failed.addProperty("traceback", run.trace);
        failed.add("actions", GSON.toJsonTree(run.context.actions));
        failed.add("assertions", GSON.toJsonTree(run.context.checks));
        write("machine-failure-" + label + "-" + run.scenario.id.replace('/', '_') + ".json", failed);
        System.out.println("ADVMACHINES CASE_FAIL " + run.scenario.id + " phase=" + phase);
    }

    private void cleanup(Run run) {
        if (run.cleanupDone) return;
        run.cleanupDone = true;
        if (run.scenario.cleanup != null) run.scenario.cleanup.accept(run.context);
    }

    private void finish() {
        JsonArray rows = new JsonArray(), fixture = new JsonArray();
        for (Run run : runs) {
            JsonObject row = GSON.toJsonTree(run.context.measurements).getAsJsonObject();
            row.addProperty("id", run.scenario.id); row.addProperty("contract", run.scenario.contract);
            row.addProperty("status", run.status); row.addProperty("startTick", run.startTick);
            row.addProperty("finishTick", run.finishTick); row.addProperty("requiredTicks", run.scenario.ticks);
            row.addProperty("verifiedAssertions", run.context.verified);
            if (run.failure != null) { row.addProperty("failure", run.failure); row.addProperty("traceback", run.trace); }
            row.add("actions", GSON.toJsonTree(run.context.actions)); row.add("assertions", GSON.toJsonTree(run.context.checks));
            row.add("before", run.before); row.add("after", run.after);
            rows.add(row);
            JsonObject saved = new JsonObject(); saved.addProperty("id", run.scenario.id);
            saved.addProperty("index", (run.context.origin.getX() - 50000) / 64);
            saved.add("positions", run.context.snapshot()); saved.add("entities", run.context.entities()); fixture.add(saved);
        }
        JsonObject description = new JsonObject(); description.add("cases", fixture);
        description.addProperty("freezeMethod", "Remove tickable entities during world ticks; never restore expected NBT");
        writeRoot(FIXTURE, description);
        JsonObject report = new JsonObject(); report.addProperty("label", label);
        report.addProperty("held", paused); report.addProperty("worldTime", world.getTotalWorldTime());
        report.addProperty("campaignTicks", world.getTotalWorldTime() - campaignStart);
        report.addProperty("status", runs.stream().allMatch(run -> run.status.equals("PASS")) ? "PASS" : "FAIL");
        report.add("cases", rows);
        write("machine-operation-" + label + ".json", report);
        System.out.println("ADVMACHINES COMPLETE " + label);
    }

    private void inspect(WorldServer current, String requestedLabel) {
        authorize(current, requestedLabel);
        if (!paused || !runs.isEmpty()) throw new IllegalStateException("Restart inspection must run before any construction or operation");
        try {
            JsonObject fixture = GSON.fromJson(new String(Files.readAllBytes(FIXTURE.toPath()), StandardCharsets.UTF_8), JsonObject.class);
            inspectionCases = fixture.getAsJsonArray("cases");
            inspectionResults = new JsonArray();
            inspectionIndex = 0;
            inspectionLabel = requestedLabel;
            inspectionPending = null;
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot read operating fixture", error); }
    }

    private void inspectNext() {
        if (inspectionIndex < inspectionCases.size()) {
            JsonObject source = inspectionCases.get(inspectionIndex).getAsJsonObject();
            String phase = "chunk-plan";
            try {
                long now = world.getTotalWorldTime();
                if (inspectionPending == null) {
                    inspectionPending = new InspectionPlan(source, now);
                    phase = "chunk-request";
                    // Native loads only: restored fixture tickets already consume the ticket allowance.
                    for (net.minecraft.util.math.ChunkPos chunk : inspectionPending.chunks) {
                        world.getChunk(chunk.x, chunk.z);
                    }
                    nextPreparation = now + 1;
                    return;
                }
                phase = "chunk-readiness";
                InspectionPlan plan = inspectionPending;
                if (!plan.ready(now, chunk -> world.getChunk(chunk.x, chunk.z).isLoaded())) {
                    nextPreparation = now + 1;
                    return;
                }
                phase = "snapshot";
                JsonObject row = new JsonObject();
                row.addProperty("id", source.get("id").getAsString());
                row.addProperty("chunksRequestedAt", plan.requestedAt);
                row.addProperty("chunksObservedAt", now);
                JsonArray positions = new JsonArray();
                for (BlockPos pos : plan.positions) {
                    JsonObject actual = new JsonObject();
                    actual.addProperty("x", pos.getX()); actual.addProperty("y", pos.getY()); actual.addProperty("z", pos.getZ());
                    actual.addProperty("block", world.getBlockState(pos).getBlock().getRegistryName().toString());
                    actual.addProperty("state", world.getBlockState(pos).toString());
                    TileEntity tile = world.getTileEntity(pos);
                    if (tile != null) {
                        net.minecraft.nbt.NBTTagCompound tag = tile.writeToNBT(new net.minecraft.nbt.NBTTagCompound());
                        actual.addProperty("nbt", tag.toString());

                    }
                    positions.add(actual);
                }
                row.add("positions", positions);
                JsonArray actors = new MachineContext(world, plan.index).entities();
                Set<java.util.UUID> observed = new HashSet<>();
                for (JsonElement actor : actors) observed.add(java.util.UUID.fromString(actor.getAsJsonObject().get("uuid").getAsString()));
                // Actors may have crossed the bay boundary. Look them up, never recreate them.
                for (java.util.UUID id : plan.actorIds) {
                    if (observed.contains(id)) continue;
                    Entity actor = world.getEntityFromUuid(id);
                    if (actor == null) continue; // A genuinely absent actor remains an exact-comparison failure.
                    net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound();
                    if (!actor.writeToNBTOptional(tag)) throw new IllegalStateException("Cannot save observed actor " + id);
                    JsonObject actual = new JsonObject();
                    actual.addProperty("uuid", actor.getUniqueID().toString());
                    actual.addProperty("nbt", tag.toString());

                    actors.add(actual);
                }
                row.add("entities", actors);
                inspectionResults.add(row);
                inspectionIndex++;
                inspectionPending = null;
                nextPreparation = now + 20;
                return;
            } catch (Throwable failure) {
                inspectionFailed(source, phase, failure);
                return;
            }
        }
        JsonObject report = new JsonObject(); report.addProperty("held", paused);
        report.addProperty("constructionCallsThisProcess", 0); report.add("cases", inspectionResults);
        write("machine-inspection-" + inspectionLabel + ".json", report);
        System.out.println("ADVMACHINES INSPECTED " + inspectionLabel);
        inspectionCases = null;
    }

    private void inspectionFailed(JsonObject source, String phase, Throwable failure) {
        JsonObject detail = new JsonObject();
        detail.add("id", source.get("id"));
        detail.addProperty("index", inspectionIndex);
        detail.addProperty("phase", phase);
        detail.addProperty("status", "FAIL");
        detail.addProperty("failure", failure.toString());
        detail.addProperty("held", paused);
        detail.addProperty("worldTime", world.getTotalWorldTime());
        if (inspectionPending != null) {
            detail.addProperty("chunksRequestedAt", inspectionPending.requestedAt);
            detail.addProperty("requestedChunks", inspectionPending.chunks.toString());
        }
        java.io.StringWriter trace = new java.io.StringWriter();
        failure.printStackTrace(new java.io.PrintWriter(trace));
        detail.addProperty("traceback", trace.toString());
        detail.add("completedCases", inspectionResults);
        write("machine-inspection-failure-" + inspectionLabel + "-" + inspectionIndex + ".json", detail);
        inspectionCases = null;
        throw new IllegalStateException("Cold inspection failed for " + source.get("id") + " during " + phase, failure);
    }

    /** Validates all authority before requesting chunks; observation is forbidden on the request tick. */
    static final class InspectionPlan {
        final int index;
        final long requestedAt;
        final List<BlockPos> positions = new ArrayList<>();
        final Set<net.minecraft.util.math.ChunkPos> chunks = new java.util.LinkedHashSet<>();
        final Set<java.util.UUID> actorIds = new java.util.LinkedHashSet<>();

        InspectionPlan(JsonObject source, long tick) {
            index = integer(source, "index", 0, 780);
            requestedAt = tick;
            int originX = 50000 + index * 64;
            if (!source.has("positions") || !source.get("positions").isJsonArray()
                    || !source.has("entities") || !source.get("entities").isJsonArray()) {
                throw new IllegalArgumentException("Missing authoritative positions/actors");
            }
            for (JsonElement row : source.getAsJsonArray("positions")) {
                JsonObject saved = row.getAsJsonObject();
                BlockPos pos = new BlockPos(integer(saved, "x", originX, originX + 31),
                        integer(saved, "y", 79, 111), integer(saved, "z", 50000, 50031));
                positions.add(pos);
                chunks.add(new net.minecraft.util.math.ChunkPos(pos));
            }
            for (JsonElement row : source.getAsJsonArray("entities")) {
                JsonObject saved = row.getAsJsonObject();
                java.util.UUID id = java.util.UUID.fromString(saved.get("uuid").getAsString());
                if (!actorIds.add(id)) throw new IllegalArgumentException("Duplicate authoritative actor " + id);
                net.minecraft.nbt.NBTTagCompound tag = readActor(saved.get("nbt").getAsString());
                chunks.add(new net.minecraft.util.math.ChunkPos(savedActorPosition(index, tag)));
            }
        }

        boolean ready(long tick, java.util.function.Predicate<net.minecraft.util.math.ChunkPos> loaded) {
            if (tick <= requestedAt) return false;
            if (tick > requestedAt + 80) throw new IllegalStateException("Cold chunk readiness exceeded 80 ticks");
            boolean allLoaded = true;
            for (net.minecraft.util.math.ChunkPos chunk : chunks) if (!loaded.test(chunk)) allLoaded = false;
            if (!allLoaded && tick == requestedAt + 80) throw new IllegalStateException("Native cold chunk load did not complete within 80 ticks");
            return allLoaded;
        }

        static BlockPos savedActorPosition(int index, net.minecraft.nbt.NBTTagCompound tag) {
            if (index < 0 || index > 780) throw new IllegalArgumentException("Saved actor bay escaped bounds");
            if (!tag.hasKey("Pos", 9)) throw new IllegalArgumentException("Saved actor is missing native Pos");
            net.minecraft.nbt.NBTTagList pos = tag.getTagList("Pos", 6);
            if (pos.tagCount() != 3) throw new IllegalArgumentException("Saved actor Pos must contain three native doubles");
            double x = pos.getDoubleAt(0), y = pos.getDoubleAt(1), z = pos.getDoubleAt(2);
            double dx = x - (50000 + index * 64), dz = z - 50000;
            // Only a bounded horizontal neighbourhood may be loaded; vertical coordinates stay in native world height.
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                    || y < 0 || y > 255 || dx * dx + dz * dz > 64 * 64) {
                throw new IllegalArgumentException("Saved actor Pos escaped the 64-block bay neighbourhood or world height");
            }
            return new BlockPos(x, y, z);
        }

        private static int integer(JsonObject source, String key, int min, int max) {
            JsonElement value = source.get(key);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Missing numeric " + key);
            }
            double number = value.getAsDouble();
            if (!Double.isFinite(number) || number != Math.rint(number) || number < min || number > max) {
                throw new IllegalArgumentException("Saved " + key + " escaped integral bounds " + min + ".." + max);
            }
            return (int) number;
        }

        private static net.minecraft.nbt.NBTTagCompound readActor(String encoded) {
            try {
                return net.minecraft.nbt.JsonToNBT.getTagFromJson(encoded);
            } catch (net.minecraft.nbt.NBTException failure) {
                throw new IllegalArgumentException("Cannot read authoritative native actor NBT", failure);
            }
        }
    }

    private static void write(String name, JsonObject document) { writeRoot(new File("advantage-works-results", name), document); }

    private static void writeRoot(File file, JsonObject document) {
        try {
            if (file.exists()) throw new IllegalStateException("Refusing to overwrite operating evidence " + file);
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            Files.write(file.toPath(), GSON.toJson(document).getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot write operating evidence", error); }
    }

    private static final class Run {
        final MachineScenario scenario;
        final MachineContext context;
        boolean started, finished;
        boolean chunksRequested;
        boolean cleanupDone;
        long readinessDeadline;
        long startTick, finishTick;
        String status = "NOT_RUN", failure, trace;
        JsonArray before = new JsonArray(), after = new JsonArray();
        Run(MachineScenario scenario, MachineContext context) { this.scenario = scenario; this.context = context; }
    }
}
