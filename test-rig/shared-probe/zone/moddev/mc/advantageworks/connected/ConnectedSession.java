package zone.moddev.mc.advantageworks.connected;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Tick-bounded observation shared by legacy and current test-only adapters. */
public final class ConnectedSession {
    public interface Port {
        void prepare(JsonObject definition) throws Exception;
        void build(JsonObject definition) throws Exception;
        void hold();
        void restore();
        long time();
        JsonArray snapshot(JsonObject definition) throws Exception;
    }

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final JsonObject definition;
    private final Port port;
    private int remaining;
    private int requested;
    private long started;
    private String completion;
    private int buildCalls;

    public ConnectedSession(Port port) throws Exception {
        if (!new File("advantage-connected.allow").isFile()
                || !new File("migration-operate.allow").isFile()) {
            throw new IllegalStateException("Connected tests require both explicit allow markers");
        }
        this.port = port;
        definition = JSON.fromJson(new String(Files.readAllBytes(new File("connected-fixture.json").toPath()),
                StandardCharsets.UTF_8), JsonObject.class);
        validate(definition);
        port.prepare(definition);
    }

    public static void validate(JsonObject definition) {
        if (definition.get("format").getAsInt() != 1) throw new IllegalArgumentException("Unsupported fixture format");
        JsonArray bounds = definition.getAsJsonArray("bounds");
        if (bounds.size() != 6) throw new IllegalArgumentException("Invalid bounds");
        int[] b = new int[6];
        for (int i = 0; i < 6; i++) b[i] = bounds.get(i).getAsInt();
        if (b[1] < 64 || b[4] > 160) throw new IllegalArgumentException("Fixtures must be above flat terrain");
        for (int i = 0; i < 3; i++) {
            if (b[i] > b[i + 3] || b[i + 3] - b[i] > 512) throw new IllegalArgumentException("Oversized bounds");
        }
        java.util.Set<String> positions = new java.util.HashSet<>();
        java.util.Set<String> identities = new java.util.HashSet<>();
        for (com.google.gson.JsonElement value : definition.getAsJsonArray("blocks")) {
            JsonObject entry = value.getAsJsonObject();
            if (!entry.get("id").getAsString().matches("[A-Za-z0-9_-]+")
                    || !identities.add(entry.get("id").getAsString())) throw new IllegalArgumentException("Duplicate/unsafe identity");
            String key = "";
            for (int i = 0; i < 3; i++) {
                int n = entry.get(new String[] {"x", "y", "z"}[i]).getAsInt();
                if (n < b[i] || n > b[i + 3]) throw new IllegalArgumentException("Placement outside bounds");
                key += ":" + n;
            }
            if (!positions.add(key)) throw new IllegalArgumentException("Overlapping placements");
            if (entry.get("name").getAsString().contains("infinite_")) throw new IllegalArgumentException("Infinite supplies forbidden");
        }
    }

    public void start() {
        if (remaining == 0) port.hold();
    }

    public void end() throws Exception {
        port.restore();
        if (remaining > 0 && --remaining == 0) snapshot(completion);
    }

    public void execute(String operation, String label, int ticks, boolean permitBuild) throws Exception {
        if (!label.matches("[a-zA-Z0-9_-]+")) throw new IllegalArgumentException("Unsafe label");
        if (remaining != 0) throw new IllegalStateException("A measured interval is already running");
        if (operation.equals("build")) {
            if (!permitBuild || !new File("connected-build.allow").isFile()
                    || new File("connected-built.json").exists()) throw new IllegalStateException("Build is source-only and single-use");
            port.build(definition);
            buildCalls++;
            write(new File("connected-built.json"), definition);
            snapshot(label);
        } else if (operation.equals("snapshot")) {
            requested = 0;
            snapshot(label);
        } else if (operation.equals("step")) {
            if (ticks < 1 || ticks > 4000) throw new IllegalArgumentException("Unsafe tick interval");
            requested = remaining = ticks;
            started = port.time();
            completion = label;
        } else {
            throw new IllegalArgumentException("Read/step only after initial source construction");
        }
    }

    private void snapshot(String label) throws Exception {
        JsonArray state = port.snapshot(definition);
        JsonObject report = new JsonObject();
        report.addProperty("format", 1);
        report.addProperty("held", remaining == 0);
        report.addProperty("requestedTicks", requested);
        report.addProperty("advancedTicks", requested == 0 ? 0 : port.time() - started);
        report.addProperty("gameTime", port.time());
        report.addProperty("buildCallsThisProcess", buildCalls);
        report.add("snapshot", state);
        write(new File("connected-" + label + ".json"), report);
        write(new File("migration-" + label + ".json"), state);
        System.out.println("ADVCONNECTED COMPLETE " + label);
    }

    private static void write(File path, Object value) throws Exception {
        Files.write(path.toPath(), JSON.toJson(value).getBytes(StandardCharsets.UTF_8));
    }
}
