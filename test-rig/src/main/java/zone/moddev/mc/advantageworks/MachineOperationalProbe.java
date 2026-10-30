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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.command.Commands;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.event.TickEvent;

/** Natural world ticks and recorded finite resources; never drives tickUpdate directly. */
final class MachineOperationalProbe {
    private static final File MARKER = new File("advantage-machine-operation.allow");
    private static final File FIXTURE = new File("machine-operation-fixture.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final List<Run> runs = new ArrayList<>();
    private final List<TileEntity> held = new ArrayList<>();
    private final NativeEntityHold heldEntities = new NativeEntityHold();
    private ServerWorld world;
    private boolean paused = true;
    private boolean preparing;
    private int prepareIndex;
    private String label;
    private long campaignStart;
    private long nextPreparation;
    private JsonArray inspectionCases, inspectionResults;
    private int inspectionIndex;
    private String inspectionLabel;

    static void register() {
        if (!MARKER.isFile()) return;
        MachineOperationalProbe probe = new MachineOperationalProbe();
        MinecraftForge.EVENT_BUS.addListener(probe::commands);
        MinecraftForge.EVENT_BUS.addListener(probe::tick);
    }

    private void commands(FMLServerStartingEvent event) {
        event.getCommandDispatcher().register(Commands.literal("advmachines").requires(source -> source.hasPermissionLevel(4))
                .then(Commands.literal("run").then(Commands.argument("group", StringArgumentType.word())
                        .then(Commands.argument("label", StringArgumentType.word()).executes(context -> {
                            start(context.getSource().getWorld(), StringArgumentType.getString(context, "group"),
                                    StringArgumentType.getString(context, "label")); return 1;
                        }))))
                .then(Commands.literal("inspect").then(Commands.argument("label", StringArgumentType.word()).executes(context -> {
                    inspect(context.getSource().getWorld(), StringArgumentType.getString(context, "label")); return 1;
                }))));
    }

    private static List<MachineScenario> catalog() {
        List<MachineScenario> scenarios = new ArrayList<>();
        PowerOperationalCases.register(scenarios);
        BatteryOperationalCases.register(scenarios);
        SourcePickupOperationalCases.register(scenarios);
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
        Set<String> ids = new HashSet<>();
        for (MachineScenario scenario : scenarios) if (!ids.add(scenario.id)) throw new IllegalArgumentException("Duplicate case " + scenario.id);
        if (scenarios.isEmpty()) throw new IllegalStateException("No operating tests");
        return scenarios;
    }

    private void authorize(ServerWorld current, String requestedLabel) {
        if (!MARKER.isFile() || !current.getWorldInfo().getWorldName().equals("AdvantageWorksMigration")
                || !requestedLabel.matches("[A-Za-z0-9_-]+")) throw new IllegalStateException("Unmarked or unsafe operating world");
        world = current;
        FakePlayer sentinel = FakePlayerFactory.getMinecraft(world);
        sentinel.setPosition(49900.5, 80, 49900.5);
        if (!world.getPlayers().contains(sentinel)) world.getPlayers().add(sentinel);
    }

    private void start(ServerWorld current, String group, String requestedLabel) {
        authorize(current, requestedLabel);
        if (FIXTURE.exists() || !runs.isEmpty() || !paused) throw new IllegalStateException("Construction is single-use; never rebuild a restarted fixture");
        List<MachineScenario> catalog = catalog();
        JsonArray declarations = new JsonArray();
        for (int i = 0; i < catalog.size(); i++) {
            MachineScenario scenario = catalog.get(i);
            if (!group.equals("all") && !scenario.id.startsWith(group + "/")) continue;
            Run run = new Run(scenario, new MachineContext(world, i));
            runs.add(run);
            JsonObject entry = new JsonObject();
            entry.addProperty("id", scenario.id); entry.addProperty("contract", scenario.contract);
            entry.addProperty("ticks", scenario.ticks); entry.addProperty("exclusive", scenario.exclusive);
            declarations.add(entry);
        }
        if (runs.isEmpty()) throw new IllegalArgumentException("Unknown or empty operating group " + group);
        label = requestedLabel;
        campaignStart = world.getGameTime();
        JsonObject description = new JsonObject();
        description.addProperty("group", group);
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
            run.before = run.context.snapshot();
            run.startTick = world.getGameTime();
            run.context.activationTick = run.startTick;
            run.context.settingUp = false;
        } catch (Throwable failure) { fail(run, "setup", failure); }
    }

    private void tick(TickEvent.WorldTickEvent event) {
        if (event.world.isRemote || !(event.world instanceof ServerWorld)
                || event.world.dimension.getType() != net.minecraft.world.dimension.DimensionType.OVERWORLD) return;
        ServerWorld current = (ServerWorld) event.world;
        if (event.phase == TickEvent.Phase.START && paused) {
            held.addAll(current.tickableTileEntities);
            current.tickableTileEntities.clear();
            for (Entity entity : (Iterable<Entity>)current.getEntities()::iterator) if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) heldEntities.hold(entity);
        } else if (event.phase == TickEvent.Phase.END) {
            for (TileEntity tile : held) if (!tile.isRemoved() && !current.tickableTileEntities.contains(tile)) current.tickableTileEntities.add(tile);
            held.clear(); heldEntities.restore();
            if (current != world) return;
            if (inspectionCases != null) {
                if (world.getGameTime() >= nextPreparation) inspectNext();
                return;
            }
            if (preparing) {
                if (world.getGameTime() < nextPreparation) return;
                while (prepareIndex < runs.size()) {
                    Run run = runs.get(prepareIndex++);
                    if (run.scenario.exclusive) continue;
                    setup(run);
                    nextPreparation = world.getGameTime() + 20;
                    System.out.println("ADVMACHINES PREPARED " + run.scenario.id);
                    return;
                }
                for (Run run : runs) if (run.started && !run.finished) {
                    run.startTick = world.getGameTime();
                    run.context.activationTick = run.startTick;
                }
                preparing = false;
                paused = false;
                System.out.println("ADVMACHINES MEASURING " + label);
                return;
            }
            if (paused) return;
            for (Run run : runs) if (run.started && !run.finished && world.getGameTime() - run.startTick >= run.scenario.ticks) {
                try {
                    run.scenario.verify.accept(run.context);
                    if (run.context.verified == 0) throw new AssertionError("No measured operating outcome");
                    run.finished = true;
                    run.status = "PASS";
                } catch (Throwable failure) { fail(run, "verify", failure); }
                run.finishTick = world.getGameTime();
                run.after = run.context.snapshot();
            }
            if (runs.stream().anyMatch(run -> run.started && !run.finished)) return;
            for (Run run : runs) if (!run.started) { setup(run); return; }
            paused = true;
            finish();
        }
    }

    private void fail(Run run, String phase, Throwable failure) {
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
        report.addProperty("held", paused); report.addProperty("worldTime", world.getGameTime());
        report.addProperty("campaignTicks", world.getGameTime() - campaignStart);
        report.addProperty("status", runs.stream().allMatch(run -> run.status.equals("PASS")) ? "PASS" : "FAIL");
        report.add("cases", rows);
        write("machine-operation-" + label + ".json", report);
        System.out.println("ADVMACHINES COMPLETE " + label);
    }

    private void inspect(ServerWorld current, String requestedLabel) {
        authorize(current, requestedLabel);
        if (!paused || !runs.isEmpty()) throw new IllegalStateException("Restart inspection must run before any construction or operation");
        try {
            JsonObject fixture = GSON.fromJson(new String(Files.readAllBytes(FIXTURE.toPath()), StandardCharsets.UTF_8), JsonObject.class);
            inspectionCases = fixture.getAsJsonArray("cases");
            inspectionResults = new JsonArray();
            inspectionIndex = 0;
            inspectionLabel = requestedLabel;
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot read operating fixture", error); }
    }

    private void inspectNext() {
            if (inspectionIndex < inspectionCases.size()) {
                JsonObject source = inspectionCases.get(inspectionIndex++).getAsJsonObject();
                JsonObject row = new JsonObject(); row.addProperty("id", source.get("id").getAsString());
                JsonArray positions = new JsonArray();
                for (JsonElement point : source.getAsJsonArray("positions")) {
                    JsonObject saved = point.getAsJsonObject(), actual = new JsonObject();
                    BlockPos pos = new BlockPos(saved.get("x").getAsInt(), saved.get("y").getAsInt(), saved.get("z").getAsInt());
                    if (pos.getX() < 50000 || pos.getX() > 100000 || pos.getY() < 79 || pos.getY() > 111
                            || pos.getZ() < 50000 || pos.getZ() > 50031) throw new IllegalArgumentException("Saved position escaped proving ground");
                    actual.addProperty("x", pos.getX()); actual.addProperty("y", pos.getY()); actual.addProperty("z", pos.getZ());
                    actual.addProperty("block", world.getBlockState(pos).getBlock().getRegistryName().toString());
                    actual.addProperty("state", world.getBlockState(pos).toString());
                    TileEntity tile = world.getTileEntity(pos);
                    if (tile != null) actual.addProperty("nbt", tile.write(new net.minecraft.nbt.CompoundNBT()).toString());
                    positions.add(actual);
                }
                row.add("positions", positions); inspectionResults.add(row);
                int index = source.get("index").getAsInt();
                if (index < 0 || index > 780) throw new IllegalArgumentException("Saved actor bay escaped bounds");
                row.add("entities", new MachineContext(world, index).entities());
                nextPreparation = world.getGameTime() + 20;
                return;
            }
            JsonObject report = new JsonObject(); report.addProperty("held", paused);
            report.addProperty("constructionCallsThisProcess", 0); report.add("cases", inspectionResults);
            write("machine-inspection-" + inspectionLabel + ".json", report);
            System.out.println("ADVMACHINES INSPECTED " + inspectionLabel);
            inspectionCases = null;
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
        long startTick, finishTick;
        String status = "NOT_RUN", failure, trace;
        JsonArray before = new JsonArray(), after = new JsonArray();
        Run(MachineScenario scenario, MachineContext context) { this.scenario = scenario; this.context = context; }
    }
}
