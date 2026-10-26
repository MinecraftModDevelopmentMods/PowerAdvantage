package zone.moddev.mc.advantageworks.runner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URL;
import java.net.URLConnection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Owns disposable runtime directories and complete server-process restarts. */
public final class WorksRunner {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    // The 2,000 mB batch needs 1,000 ticks at the still's 2:1, one-step/tick rate.
    private static final int OIL_DISTILLATION_RUN_SECONDS = 60;
    private static final List<String> DEFAULT_LIFECYCLE_STATIONS = Arrays.asList(
            "W-01", "W-02", "W-03", "W-04",
            "S-01", "S-02", "S-03", "S-04", "S-05", "S-06", "S-07", "S-08",
            "E-01", "E-02", "E-03", "E-04", "E-05", "E-06", "E-07", "E-08",
            "P-API-01");

    private WorksRunner() {
    }

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        Profile profile = readProfile(arguments.profile);
        if (arguments.serverPort != null) profile.serverPort = arguments.serverPort;
        File testRig = findTestRig(arguments.profile);
        File powerRoot = testRig.getParentFile().getCanonicalFile();
        // Pin validation precedes directory creation, downloads, and world access.
        requireInputPins(profile);
        if (arguments.validateOnly) {
            List<Map<String, Object>> inputs = stageMods(testRig, null, profile.mods);
            System.out.println("Native 1.15.2 profile preflight only (no world opened): " + GSON.toJson(inputs));
            return;
        }
        String stamp = timestamp();
        File runtimeBase = resolve(testRig, profile.runtimeBase);
        File runtime = new File(runtimeBase, profile.name + "-" + stamp).getCanonicalFile();
        requireChild(powerRoot, runtime);
        if (!runtime.mkdirs()) throw new IllegalStateException("Cannot create runtime " + runtime);

        File mods = new File(runtime, "mods");
        File results = new File(runtime, "advantage-works-results");
        if (!mods.mkdirs() || !results.mkdirs()) {
            throw new IllegalStateException("Cannot create runtime directories under " + runtime);
        }
        writeRuntimeFiles(runtime, profile);
        stageConfigFixtures(testRig, runtime, profile.configFixtures);
        List<Map<String, Object>> staged = stageMods(testRig, mods, profile.mods);
        if ("packaged".equals(profile.mode)) preparePackagedServer(testRig, runtime, profile);
        List<String> lifecycleStations = arguments.lifecycleStations == null
                ? profile.lifecycleStations : arguments.lifecycleStations;
        boolean selectedLifecycle = arguments.lifecycleStations != null
                || !DEFAULT_LIFECYCLE_STATIONS.equals(lifecycleStations);
        int stationRunSeconds = arguments.stationRunSeconds == null
                ? profile.stationRunSeconds : arguments.stationRunSeconds;
        writeRunManifest(runtime, profile, staged, arguments.automated,
                lifecycleStations, stationRunSeconds);

        System.out.println("Advantage Works runtime: " + runtime);
        Process first = launch(profile, powerRoot, runtime, results);
        File latestLog = new File(runtime, "logs/latest.log");
        waitForReady(first, latestLog, profile.startupTimeoutSeconds);
        if (profile.buildWorks) {
            if (selectedLifecycle) {
                for (String station : lifecycleStations) send(first, "advworks build " + station);
            } else {
                send(first, "advworks build all");
            }
            Thread.sleep(3000L);
        }

        if (arguments.automated) {
            if (profile.buildWorks) {
                for (String station : lifecycleStations) {
                    send(first, "advworks start " + station);
                    int runSeconds = "W-03".equals(station)
                            ? Math.max(stationRunSeconds, OIL_DISTILLATION_RUN_SECONDS) : stationRunSeconds;
                    Thread.sleep(runSeconds * 1000L);
                    send(first, "advworks checkpoint " + station + " before-restart");
                    send(first, "advworks stop " + station);
                }
                checkStations(first, lifecycleStations, selectedLifecycle);
            }
            if (profile.worldgenSampleRadius > 0) {
                Thread.sleep(stationRunSeconds * 1000L);
                send(first, "advworks sample-worldgen " + profile.worldgenSampleRadius);
            }
            send(first, "save-all");
            send(first, "stop");
            requireExit(first, profile.shutdownTimeoutSeconds);
            requireNoCommandFailures(latestLog, "first-run");
            copyIfPresent(latestLog, new File(results, "first-run.log"));
            File firstWorldgen = new File(results, "worldgen-first-run.json");
            if (profile.worldgenSampleRadius > 0) {
                copyIfPresent(new File(results, "worldgen-sample.json"), firstWorldgen);
                requireWorldgenSummary(firstWorldgen, profile.worldgenExpectations, "first-run");
            }
            requireProfileAssertions(runtime, results, profile, "first-run",
                    profile.firstProfileAssertions);
            requireFileAssertions(runtime, results, "first-run", profile.firstFileAssertions);
            applyProfileMutations(runtime, profile);
            if (latestLog.exists() && !latestLog.delete()) {
                throw new IllegalStateException("Cannot clear first-run log before restart: " + latestLog);
            }

            Process second = launch(profile, powerRoot, runtime, results);
            waitForReady(second, latestLog, profile.startupTimeoutSeconds);
            Thread.sleep(stationRunSeconds * 1000L);
            if (profile.buildWorks) {
                for (String station : lifecycleStations) {
                    send(second, "advworks checkpoint " + station + " after-restart");
                }
                checkStations(second, lifecycleStations, selectedLifecycle);
            }
            if (profile.worldgenSampleRadius > 0) {
                send(second, "advworks sample-worldgen " + profile.worldgenSampleRadius);
            }
            send(second, "save-all");
            send(second, "stop");
            requireExit(second, profile.shutdownTimeoutSeconds);
            requireNoCommandFailures(latestLog, "restart-run");
            copyIfPresent(latestLog, new File(results, "restart-run.log"));
            File restartWorldgen = new File(results, "worldgen-restart-run.json");
            if (profile.worldgenSampleRadius > 0) {
                copyIfPresent(new File(results, "worldgen-sample.json"), restartWorldgen);
                requireWorldgenSummary(restartWorldgen, profile.worldgenExpectations, "restart-run");
                if (profile.requireStableWorldgenCounts) {
                    requireStableWorldgenCounts(firstWorldgen, restartWorldgen,
                            profile.stableWorldgenCountExclusions);
                }
            }
            requireProfileAssertions(runtime, results, profile, "restart-run",
                    profile.restartProfileAssertions);
            requireFileAssertions(runtime, results, "restart-run", profile.restartFileAssertions);
            if (profile.buildWorks) {
                if (!selectedLifecycle) {
                    requireAcceptableSummary(new File(results, "all-summary.json"));
                } else {
                    requireAcceptableStations(results, lifecycleStations);
                }
            }
            System.out.println("Automated Advantage Works run complete: " + results);
            return;
        }

        System.out.println("Server ready at localhost:" + profile.serverPort);
        System.out.println("Use the in-world signs or type Advantage Works commands. Type stop here when finished.");
        Thread input = new Thread(() -> bridgeInput(first), "advantage-works-console-input");
        input.setDaemon(true);
        input.start();
        requireExit(first, 0);
    }

    private static Process launch(Profile profile, File powerRoot, File runtime, File results) throws Exception {
        List<String> command = new ArrayList<>();
        File workingDirectory;
        if ("development".equals(profile.mode)) {
            boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
            command.add(new File(powerRoot, windows ? "gradlew.bat" : "gradlew").getAbsolutePath());
            command.add("runServer");
            String relative = powerRoot.toPath().relativize(runtime.toPath()).toString().replace('\\', '/');
            command.add("-PpowerAdvantageRunDirectory=" + relative);
            command.add("--no-daemon");
            workingDirectory = powerRoot;
        } else {
            if (profile.launchCommand != null && !profile.launchCommand.isEmpty()) {
                for (String token : profile.launchCommand) {
                    command.add(expand(token).replace("${runtime}", runtime.getAbsolutePath())
                            .replace("${powerRoot}", powerRoot.getAbsolutePath()));
                }
            } else {
                command.add(javaExecutable(java8Home(profile)).getAbsolutePath());
                command.add("-Xms512M");
                command.add("-Xmx2G");
                command.add("-jar");
                command.add("forge-" + profile.forge.version + ".jar");
                command.add("nogui");
            }
            workingDirectory = runtime;
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(workingDirectory);
        builder.redirectErrorStream(true);
        builder.environment().put("JAVA_HOME", "development".equals(profile.mode)
                ? gradleJavaHome(profile).getAbsolutePath() : java8Home(profile).getAbsolutePath());
        builder.environment().put("ADVANTAGE_WORKS_RUNTIME", runtime.getAbsolutePath());
        Process process = builder.start();
        Thread output = new Thread(() -> drainOutput(process), "advantage-works-server-output");
        output.setDaemon(true);
        output.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (process.isAlive()) {
                try {
                    send(process, "save-all");
                    send(process, "stop");
                } catch (Exception ignored) {
                    process.destroy();
                }
            }
        }));
        return process;
    }

    private static void waitForReady(Process process, File log, int timeoutSeconds) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (!process.isAlive()) throw new IllegalStateException("Server exited before becoming ready");
            if (log.isFile() && contains(log, "Done (")) return;
            Thread.sleep(500L);
        }
        throw new IllegalStateException("Server did not become ready within " + timeoutSeconds + " seconds");
    }

    private static boolean contains(File file, String needle) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) if (line.contains(needle)) return true;
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void requireExit(Process process, int timeoutSeconds) throws Exception {
        if (timeoutSeconds <= 0) {
            int exit = process.waitFor();
            if (exit != 0) throw new IllegalStateException("Server process failed with exit " + exit);
            return;
        }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Server did not stop within " + timeoutSeconds + " seconds");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Server process failed with exit " + process.exitValue());
        }
    }

    private static void send(Process process, String command) throws Exception {
        process.getOutputStream().write((command + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
        process.getOutputStream().flush();
    }

    private static void bridgeInput(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while (process.isAlive() && (line = reader.readLine()) != null) send(process, line);
        } catch (Exception ignored) {
        }
    }

    private static void drainOutput(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) System.out.println("[server] " + line);
        } catch (Exception ignored) {
        }
    }

    private static List<Map<String, Object>> stageMods(File testRig, File mods, List<ModInput> inputs)
            throws Exception {
        List<Map<String, Object>> staged = new ArrayList<>();
        for (ModInput input : inputs) {
            File source = resolve(testRig, input.path);
            if (!source.isFile()) {
                if (input.required) throw new IllegalStateException("Missing required mod " + input.id + ": " + source);
                continue;
            }
            if (input.file != null && !input.file.equals(source.getName())) {
                throw new IllegalStateException("Filename mismatch for " + input.id + ": expected "
                        + input.file + ", found " + source.getName());
            }
            String hash = sha256(source);
            if (!input.workspaceProduced && (input.sha256 == null || input.sha256.isEmpty())) {
                throw new IllegalStateException("Supplied mod " + input.id + " must pin SHA-256");
            }
            if (input.sha256 != null && !input.sha256.isEmpty()
                    && !hash.equalsIgnoreCase(input.sha256)) {
                throw new IllegalStateException("SHA-256 mismatch for " + input.id + ": " + hash);
            }
            if (input.verifyIdentity) verifyModIdentity(source, input.id, input.version);
            File destination = mods == null ? source : new File(mods, source.getName());
            if (mods != null) Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", input.id);
            record.put("version", input.version);
            record.put("file", destination.getName());
            record.put("sha256", hash);
            staged.add(record);
        }
        return staged;
    }

    private static void verifyModIdentity(File jar, String expectedId, String expectedVersion) throws Exception {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry entry = zip.getEntry("META-INF/mods.toml");
            if (entry == null) throw new IllegalStateException("No META-INF/mods.toml in " + jar);
            try (InputStreamReader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                StringBuilder text = new StringBuilder();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) >= 0) text.append(buffer, 0, count);
                Matcher sections = Pattern.compile("(?s)\\[\\[mods\\]\\](.*?)(?=\\[\\[|$)")
                        .matcher(text);
                while (sections.find()) {
                    String section = sections.group(1);
                    String actualId = tomlString(section, "modId");
                    if (!expectedId.equals(actualId)) continue;
                    String actualVersion = tomlString(section, "version");
                    if (expectedVersion != null && !expectedVersion.equals(actualVersion)) {
                        throw new IllegalStateException("Version mismatch for " + expectedId
                                + ": expected " + expectedVersion + ", found " + actualVersion);
                    }
                    return;
                }
                throw new IllegalStateException("Mod identity " + expectedId + " not found in " + jar);
            }
        }
    }

    private static void requireNoCommandFailures(File log, String phase) {
        if (contains(log, "Advantage Works command failed")) {
            throw new IllegalStateException("Advantage Works command failed during " + phase
                    + "; inspect " + log);
        }
    }

    private static String tomlString(String section, String key) {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key)
                + "\\s*=\\s*\"([^\"]*)\"\\s*(?:#.*)?$").matcher(section);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static void preparePackagedServer(File testRig, File runtime, Profile profile) throws Exception {
        if (profile.forge == null || profile.forge.version == null
                || profile.forge.installerPath == null || profile.forge.sha256 == null) {
            throw new IllegalArgumentException("Packaged profile must pin Forge installer path, version, and SHA-256");
        }
        File installer = resolve(testRig, profile.forge.installerPath);
        if (!installer.isFile()) {
            if (profile.forge.url == null) throw new IllegalStateException("Missing Forge installer " + installer);
            File parent = installer.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
                throw new IllegalStateException("Cannot create Forge cache " + parent);
            }
            download(profile.forge.url, installer);
        }
        String actual = sha256(installer);
        if (!profile.forge.sha256.equalsIgnoreCase(actual)) {
            throw new IllegalStateException("Forge installer SHA-256 mismatch: " + actual);
        }
        File launcher = new File(runtime, "forge-" + profile.forge.version + ".jar");
        if (launcher.isFile()) return;

        ProcessBuilder builder = new ProcessBuilder(
                javaExecutable(java8Home(profile)).getAbsolutePath(),
                "-jar", installer.getAbsolutePath(), "--installServer");
        builder.directory(runtime);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        drainOutput(process);
        if (!process.waitFor(5, TimeUnit.MINUTES) || process.exitValue() != 0 || !launcher.isFile()) {
            if (process.isAlive()) process.destroyForcibly();
            throw new IllegalStateException("Forge server installation failed under " + runtime);
        }
    }

    private static void download(String address, File destination) throws Exception {
        System.out.println("Downloading pinned Forge installer from " + address);
        URLConnection connection = new URL(address).openConnection();
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(120000);
        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        }
    }

    private static File java8Home(Profile profile) throws Exception {
        String configured = profile.java8Home;
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("ADVANTAGE_WORKS_JAVA8_HOME");
        }
        File home;
        if (configured != null && !configured.trim().isEmpty()) {
            home = new File(expand(configured));
        } else {
            home = new File(System.getProperty("user.home"),
                    ".gradle/jdks/temurin-8-amd64-windows.2");
        }
        File executable = javaExecutable(home);
        if (!executable.isFile()) {
            throw new IllegalStateException("Java 8 is required for packaged Forge 1.15.2. Set "
                    + "ADVANTAGE_WORKS_JAVA8_HOME; checked " + home);
        }
        return home.getCanonicalFile();
    }

    private static File gradleJavaHome(Profile profile) throws Exception {
        String configured = profile.gradleJavaHome;
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("ADVANTAGE_WORKS_GRADLE_JAVA_HOME");
        }
        File home;
        if (configured != null && !configured.trim().isEmpty()) {
            home = new File(expand(configured));
        } else if (javaMajorVersion() >= 17) {
            home = new File(System.getProperty("java.home"));
        } else {
            home = new File(System.getProperty("user.home"),
                    ".gradle/jdks/eclipse_adoptium-17-amd64-windows.2");
        }
        if (!javaExecutable(home).isFile()) {
            throw new IllegalStateException("Java 17 is required to invoke Gradle. Set "
                    + "ADVANTAGE_WORKS_GRADLE_JAVA_HOME; checked " + home);
        }
        return home.getCanonicalFile();
    }

    private static int javaMajorVersion() {
        String version = System.getProperty("java.specification.version", "8");
        if (version.startsWith("1.")) version = version.substring(2);
        int dot = version.indexOf('.');
        return Integer.parseInt(dot < 0 ? version : version.substring(0, dot));
    }

    private static File javaExecutable(File home) {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        return new File(new File(home, "bin"), windows ? "java.exe" : "java");
    }

    private static void writeRuntimeFiles(File runtime, Profile profile) throws Exception {
        write(new File(runtime, "eula.txt"), "eula=true\n");
        write(new File(runtime, "advantage-works.allow"), "Generated by Advantage Works runner.\n");
        write(new File(runtime, "banned-ips.json"), "[]\n");
        write(new File(runtime, "banned-players.json"), "[]\n");
        write(new File(runtime, "ops.json"), "[]\n");
        write(new File(runtime, "usercache.json"), "[]\n");
        write(new File(runtime, "whitelist.json"), "[]\n");
        File config = new File(runtime, "config");
        if (!config.isDirectory() && !config.mkdirs()) {
            throw new IllegalStateException("Cannot create config directory " + config);
        }
        write(new File(config, "poweradvantage.cfg"),
                "options {\n    S:recipe_mode=" + profile.recipeMode + "\n}\n");
        String properties = "allow-flight=true\n"
                + "difficulty=0\n"
                + "enable-command-block=true\n"
                + "force-gamemode=true\n"
                + "gamemode=1\n"
                + "generate-structures=false\n"
                + "generator-settings=" + profile.generatorSettings + "\n"
                + "level-name=" + profile.levelName + "\n"
                + "level-seed=" + profile.levelSeed + "\n"
                + "level-type=" + profile.levelType + "\n"
                + "max-players=8\n"
                + "motd=Advantage Works 1.15.2 Commissioning Ground\n"
                + "online-mode=false\n"
                + "server-ip=127.0.0.1\n"
                + "pvp=false\n"
                + "server-port=" + profile.serverPort + "\n"
                + "spawn-animals=false\n"
                + "spawn-monsters=false\n"
                + "spawn-npcs=false\n"
                + "spawn-protection=0\n"
                + "view-distance=10\n";
        write(new File(runtime, "server.properties"), properties);
    }

    private static void writeRunManifest(File runtime, Profile profile,
                                         List<Map<String, Object>> staged, boolean automated,
                                         List<String> lifecycleStations,
                                         int stationRunSeconds) throws Exception {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("profile", profile.name);
        manifest.put("mode", profile.mode);
        manifest.put("recipeMode", profile.recipeMode);
        manifest.put("sourceCommit", profile.sourceCommit);
        manifest.put("sourceVersion", profile.sourceVersion);
        manifest.put("minecraftVersion", profile.minecraftVersion);
        manifest.put("qualificationStatus", profile.qualificationStatus);
        manifest.put("legacySourceProfile", profile.legacySourceProfile);
        manifest.put("levelName", profile.levelName);
        manifest.put("levelType", profile.levelType);
        manifest.put("buildWorks", profile.buildWorks);
        manifest.put("worldgenSampleRadius", profile.worldgenSampleRadius);
        manifest.put("createdAtUtc", timestamp());
        manifest.put("automated", automated);
        manifest.put("lifecycleStations", lifecycleStations);
        manifest.put("stationRunSeconds", stationRunSeconds);
        manifest.put("oilDistillationMinimumRunSeconds", OIL_DISTILLATION_RUN_SECONDS);
        manifest.put("mods", staged);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(new File(runtime, "run-manifest.json")), StandardCharsets.UTF_8)) {
            GSON.toJson(manifest, writer);
        }
    }

    static Profile readProfile(File file) throws Exception {
        return readProfile(file, "1.15.2", "1.15.2-31.2.57");
    }

    static Profile readProfile(File file, String minecraft, String forge) throws Exception {
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            Profile profile = GSON.fromJson(reader, Profile.class);
            if (profile == null || profile.name == null || profile.mode == null) {
                throw new IllegalArgumentException("Incomplete profile " + file);
            }
            if (!minecraft.equals(profile.minecraftVersion)) {
                throw new IllegalArgumentException("Historical source profile, not a native115 destination: " + file);
            }
            if (!"PORTED_UNQUALIFIED".equals(profile.portStatus)) {
                throw new IllegalStateException("Native115 profile is not ported: " + profile.portBlocker);
            }
            if (profile.forge == null || !forge.equals(profile.forge.version)) {
                throw new IllegalArgumentException("Native115 profile must select Forge 1.15.2-31.2.57");
            }
            if (profile.levelName == null || !profile.levelName.startsWith("AdvantageWorks")) {
                throw new IllegalArgumentException("Profile levelName must begin with AdvantageWorks: " + file);
            }
            return profile;
        }
    }

    static void requireInputPins(Profile profile) {
        for (ModInput input : profile.mods) {
            if (input.sha256 == null || !input.sha256.matches("[A-Fa-f0-9]{64}")) {
                throw new IllegalStateException("Pending native115 SHA-256 pin: " + input.id);
            }
        }
    }

    static File findTestRig(File profile) throws Exception {
        File current = profile.getCanonicalFile().getParentFile();
        while (current != null) {
            if (new File(current, "build.gradle").isFile() && new File(current, "profiles").isDirectory()
                    && new File(current, "src/main/resources/assets/advantageworkstest").isDirectory()) return current;
            current = current.getParentFile();
        }
        throw new IllegalArgumentException("Profile must be inside the Advantage Works test-rig: " + profile);
    }

    private static void stageConfigFixtures(File testRig, File runtime,
                                            List<ConfigFixture> fixtures) throws Exception {
        for (ConfigFixture fixture : fixtures) {
            File source = resolve(testRig, fixture.source);
            if (!source.isFile()) throw new IllegalStateException("Missing config fixture " + source);
            File destination = resolve(runtime, fixture.destination);
            requireChild(runtime, destination);
            File parent = destination.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
                throw new IllegalStateException("Cannot create fixture directory " + parent);
            }
            Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void requireProfileAssertions(File runtime, File results, Profile profile,
                                                 String phase, List<JsonAssertion> assertions)
            throws Exception {
        if (assertions.isEmpty()) return;
        File profileFile = worldProfileFile(runtime, profile);
        JsonObject root = readJsonObject(profileFile);
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean failed = false;
        for (JsonAssertion assertion : assertions) {
            JsonElement actual = jsonValue(root, assertion);
            boolean passed = assertion.present ? actual != null : actual == null;
            if (passed && assertion.present && actual != null) {
                if (assertion.expectedBoolean != null) {
                    passed = actual.isJsonPrimitive()
                            && actual.getAsBoolean() == assertion.expectedBoolean;
                } else if (assertion.expectedString != null) {
                    passed = actual.isJsonPrimitive()
                            && assertion.expectedString.equals(actual.getAsString());
                } else if (assertion.expectedNumber != null) {
                    passed = actual.isJsonPrimitive()
                            && Math.abs(actual.getAsDouble() - assertion.expectedNumber) < 0.0001D;
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", assertion.id);
            row.put("section", assertion.section);
            row.put("entry", assertion.entry);
            row.put("field", assertion.field);
            row.put("expectedPresent", assertion.present);
            row.put("actual", actual == null ? null : actual);
            row.put("result", passed ? "PASS" : "FAIL");
            rows.add(row);
            failed |= !passed;
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("phase", phase);
        report.put("profileFile", profileFile.getAbsolutePath());
        report.put("assertions", rows);
        writeRunnerReport(results, phase + "-orespawn-profile.json", report);
        if (failed) throw new IllegalStateException("OreSpawn profile assertions failed during " + phase);
    }

    private static void applyProfileMutations(File runtime, Profile profile) throws Exception {
        if (profile.profileMutations.isEmpty()) return;
        File profileFile = worldProfileFile(runtime, profile);
        JsonObject root = readJsonObject(profileFile);
        for (JsonMutation mutation : profile.profileMutations) {
            JsonObject entry = jsonEntry(root, mutation);
            if (mutation.field == null || mutation.field.isEmpty()) {
                throw new IllegalArgumentException("Profile mutation requires a field");
            }
            if (mutation.booleanValue != null) entry.addProperty(mutation.field, mutation.booleanValue);
            else if (mutation.stringValue != null) entry.addProperty(mutation.field, mutation.stringValue);
            else if (mutation.numberValue != null) entry.addProperty(mutation.field, mutation.numberValue);
            else throw new IllegalArgumentException("Profile mutation has no value");
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(profileFile), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    private static JsonElement jsonValue(JsonObject root, JsonTarget target) {
        if (target.section == null || !root.has(target.section)
                || !root.get(target.section).isJsonObject()) return null;
        JsonObject section = root.getAsJsonObject(target.section);
        if (target.entry == null || !section.has(target.entry)) return null;
        JsonElement value = section.get(target.entry);
        if (target.field == null || target.field.isEmpty()) return value;
        if (!value.isJsonObject() || !value.getAsJsonObject().has(target.field)) return null;
        return value.getAsJsonObject().get(target.field);
    }

    private static JsonObject jsonEntry(JsonObject root, JsonTarget target) {
        JsonElement value = jsonValue(root, new JsonTargetCopy(target));
        if (value == null || !value.isJsonObject()) {
            throw new IllegalStateException("Missing OreSpawn profile entry "
                    + target.section + "/" + target.entry);
        }
        return value.getAsJsonObject();
    }

    private static File worldProfileFile(File runtime, Profile profile) throws Exception {
        File file = new File(new File(runtime, profile.levelName),
                "serverconfig/orespawn-worldgen.json").getCanonicalFile();
        requireChild(runtime, file);
        return file;
    }

    private static JsonObject readJsonObject(File file) throws Exception {
        if (!file.isFile()) throw new IllegalStateException("Missing JSON file " + file);
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement parsed = GSON.fromJson(reader, JsonElement.class);
            if (parsed == null || !parsed.isJsonObject()) {
                throw new IllegalStateException("Expected JSON object in " + file);
            }
            return parsed.getAsJsonObject();
        }
    }

    private static void requireFileAssertions(File runtime, File results, String phase,
                                              List<FileAssertion> assertions) throws Exception {
        if (assertions.isEmpty()) return;
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean failed = false;
        for (FileAssertion assertion : assertions) {
            File file = resolve(runtime, assertion.path);
            requireChild(runtime, file);
            int occurrences = file.isFile() && assertion.contains != null
                    ? occurrences(file, assertion.contains) : 0;
            boolean passed = file.isFile()
                    && (assertion.contains == null || occurrences >= assertion.minimumOccurrences);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", assertion.id);
            row.put("path", file.getAbsolutePath());
            row.put("contains", assertion.contains);
            row.put("occurrences", occurrences);
            row.put("result", passed ? "PASS" : "FAIL");
            rows.add(row);
            failed |= !passed;
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("phase", phase);
        report.put("assertions", rows);
        writeRunnerReport(results, phase + "-file-assertions.json", report);
        if (failed) throw new IllegalStateException("File assertions failed during " + phase);
    }

    private static int occurrences(File file, String needle) throws Exception {
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        int result = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            result++;
            from += needle.length();
        }
        return result;
    }

    private static void requireWorldgenSummary(File reportFile, WorldgenExpectations expectations,
                                               String phase) throws Exception {
        JsonObject report = readJsonObject(reportFile);
        JsonObject counts = report.has("counts") ? report.getAsJsonObject("counts") : new JsonObject();
        JsonObject violations = report.has("biomeViolations")
                ? report.getAsJsonObject("biomeViolations") : new JsonObject();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, Long> expected : expectations.minimumCounts.entrySet()) {
            long actual = counts.has(expected.getKey()) ? counts.get(expected.getKey()).getAsLong() : 0L;
            if (actual < expected.getValue()) {
                failures.add(expected.getKey() + " count " + actual + " is below " + expected.getValue());
            }
        }
        for (Map.Entry<String, Long> expected : expectations.maximumCounts.entrySet()) {
            long actual = counts.has(expected.getKey()) ? counts.get(expected.getKey()).getAsLong() : 0L;
            if (actual > expected.getValue()) {
                failures.add(expected.getKey() + " count " + actual + " exceeds " + expected.getValue());
            }
        }
        if (expectations.zeroBiomeViolations) {
            for (Map.Entry<String, JsonElement> entry : violations.entrySet()) {
                if (entry.getValue().getAsLong() != 0L) {
                    failures.add(entry.getKey() + " has " + entry.getValue().getAsLong()
                            + " biome placement violations");
                }
            }
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Worldgen assertions failed during " + phase + ": " + failures);
        }
    }

    private static void requireStableWorldgenCounts(File first, File restart,
                                                    List<String> exclusions) throws Exception {
        JsonObject firstRoot = readJsonObject(first);
        JsonObject restartRoot = readJsonObject(restart);
        if (!equalExcept(firstRoot.getAsJsonObject("counts"), restartRoot.getAsJsonObject("counts"), exclusions)
                || !equalExcept(firstRoot.getAsJsonObject("biomeViolations"),
                restartRoot.getAsJsonObject("biomeViolations"), exclusions)) {
            throw new IllegalStateException("Worldgen sample changed across full process restart");
        }
    }

    private static boolean equalExcept(JsonObject first, JsonObject restart, List<String> exclusions) {
        Set<String> keys = new LinkedHashSet<>();
        for (Map.Entry<String, JsonElement> entry : first.entrySet()) keys.add(entry.getKey());
        for (Map.Entry<String, JsonElement> entry : restart.entrySet()) keys.add(entry.getKey());
        for (String key : keys) {
            if (exclusions.contains(key)) continue;
            JsonElement firstValue = first.get(key);
            JsonElement restartValue = restart.get(key);
            if (firstValue == null ? restartValue != null : !firstValue.equals(restartValue)) return false;
        }
        return true;
    }

    private static void writeRunnerReport(File results, String name,
                                          Map<String, Object> report) throws Exception {
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(new File(results, name)), StandardCharsets.UTF_8)) {
            GSON.toJson(report, writer);
        }
    }

    private static final class JsonTargetCopy extends JsonTarget {
        JsonTargetCopy(JsonTarget source) {
            section = source.section;
            entry = source.entry;
        }
    }
    private static File resolve(File base, String path) throws Exception {
        path = expand(path);
        File file = new File(path);
        return (file.isAbsolute() ? file : new File(base, path)).getCanonicalFile();
    }

    private static String expand(String value) {
        if (value == null) return null;
        String result = value;
        int start;
        while ((start = result.indexOf("${env:")) >= 0) {
            int end = result.indexOf('}', start);
            if (end < 0) throw new IllegalArgumentException("Unclosed environment variable in " + value);
            String name = result.substring(start + 6, end);
            String replacement = System.getenv(name);
            if (replacement == null || replacement.isEmpty()) {
                throw new IllegalStateException("Environment variable " + name + " is required by the profile");
            }
            result = result.substring(0, start) + replacement + result.substring(end + 1);
        }
        return result;
    }

    private static void requireChild(File root, File child) {
        String rootPath = root.getAbsolutePath() + File.separator;
        if (!child.getAbsolutePath().startsWith(rootPath)) {
            throw new IllegalArgumentException("Runtime must remain inside PowerAdvantage: " + child);
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count);
        }
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest()) result.append(String.format("%02X", value));
        return result.toString();
    }

    private static void write(File file, String content) throws Exception {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.print(content);
        }
    }

    private static void copyIfPresent(File source, File destination) throws Exception {
        if (source.isFile()) Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private static void requireAcceptableSummary(File summary) throws Exception {
        if (!summary.isFile()) throw new IllegalStateException("Missing final result summary " + summary);
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(summary), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            JsonObject counts = root.getAsJsonObject("summary");
            int failed = counts != null && counts.has("FAIL") ? counts.get("FAIL").getAsInt() : 0;
            int unexpected = counts != null && counts.has("UNEXPECTED_PASS")
                    ? counts.get("UNEXPECTED_PASS").getAsInt() : 0;
            if (failed > 0 || unexpected > 0) {
                throw new IllegalStateException("Final Advantage Works result is not acceptable: " + counts);
            }
        }
    }

    private static void checkStations(Process process, List<String> stations, boolean selected) throws Exception {
        if (!selected) {
            send(process, "advworks check all");
            return;
        }
        for (String station : stations) send(process, "advworks check " + station);
    }

    private static void requireAcceptableStations(File results, List<String> stations) throws Exception {
        for (String station : stations) {
            File result = new File(results, station + "-latest.json");
            if (!result.isFile()) throw new IllegalStateException("Missing final station result " + result);
            try (InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(result), StandardCharsets.UTF_8)) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);
                String status = root.has("status") ? root.get("status").getAsString() : "";
                if ("FAIL".equals(status) || "UNEXPECTED_PASS".equals(status)) {
                    throw new IllegalStateException("Final Advantage Works station result is not acceptable: "
                            + station + "=" + status);
                }
            }
        }
    }

    private static String timestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss");
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date());
    }

    private static final class Arguments {
        File profile;
        boolean automated;
        boolean validateOnly;
        List<String> lifecycleStations;
        Integer stationRunSeconds;
        Integer serverPort;

        static Arguments parse(String[] args) {
            Arguments result = new Arguments();
            for (int i = 0; i < args.length; i++) {
                if ("--profile".equals(args[i]) && i + 1 < args.length) result.profile = new File(args[++i]);
                else if ("--automated".equals(args[i])) result.automated = true;
                else if ("--validate-only".equals(args[i])) result.validateOnly = true;
                else if ("--stations".equals(args[i]) && i + 1 < args.length) {
                    result.lifecycleStations = new ArrayList<>();
                    for (String station : args[++i].split(",")) {
                        if (!station.trim().isEmpty()) result.lifecycleStations.add(station.trim());
                    }
                    if (result.lifecycleStations.isEmpty()) {
                        throw new IllegalArgumentException("--stations requires at least one station ID");
                    }
                }
                else if ("--run-seconds".equals(args[i]) && i + 1 < args.length) {
                    result.stationRunSeconds = Integer.parseInt(args[++i]);
                    if (result.stationRunSeconds < 1) {
                        throw new IllegalArgumentException("--run-seconds must be positive");
                    }
                }
                else if ("--server-port".equals(args[i]) && i + 1 < args.length) {
                    result.serverPort = Integer.parseInt(args[++i]);
                    if (result.serverPort < 1 || result.serverPort > 65535) {
                        throw new IllegalArgumentException("--server-port must be between 1 and 65535");
                    }
                }
                else throw new IllegalArgumentException("Unknown runner argument " + args[i]);
            }
            if (result.profile == null) throw new IllegalArgumentException("--profile is required");
            return result;
        }
    }

    static final class Profile {
        String name;
        String mode;
        String runtimeBase = "build/works-native115";
        int serverPort = 25565;
        int startupTimeoutSeconds = 120;
        int shutdownTimeoutSeconds = 60;
        int stationRunSeconds = 5;
        String levelName = "AdvantageWorks";
        String levelSeed = "advantage-works-1.15.2-v1";
        String levelType = "FLAT";
        String generatorSettings = "";
        boolean buildWorks = true;
        int worldgenSampleRadius;
        boolean requireStableWorldgenCounts = true;
        List<String> stableWorldgenCountExclusions = new ArrayList<>();
        String java8Home;
        String gradleJavaHome;
        String recipeMode = "NORMAL";
        String sourceCommit;
        String sourceVersion;
        String minecraftVersion;
        String portStatus;
        String portBlocker;
        String qualificationStatus;
        String legacySourceProfile;
        ForgeInput forge;
        List<String> launchCommand = new ArrayList<>();
        List<ModInput> mods = new ArrayList<>();
        List<ConfigFixture> configFixtures = new ArrayList<>();
        List<JsonAssertion> firstProfileAssertions = new ArrayList<>();
        List<JsonAssertion> restartProfileAssertions = new ArrayList<>();
        List<JsonMutation> profileMutations = new ArrayList<>();
        List<FileAssertion> firstFileAssertions = new ArrayList<>();
        List<FileAssertion> restartFileAssertions = new ArrayList<>();
        WorldgenExpectations worldgenExpectations = new WorldgenExpectations();
        List<String> lifecycleStations = DEFAULT_LIFECYCLE_STATIONS;
    }

    private static final class ModInput {
        String id;
        String path;
        String file;
        String version;
        String sha256;
        boolean required = true;
        boolean workspaceProduced;
        boolean verifyIdentity = true;
    }

    private static final class ForgeInput {
        String version;
        String installerPath;
        String url;
        String sha256;
    }

    private static final class ConfigFixture {
        String source;
        String destination;
    }

    private static class JsonTarget {
        String section;
        String entry;
        String field;
    }

    private static final class JsonAssertion extends JsonTarget {
        String id;
        boolean present = true;
        Boolean expectedBoolean;
        String expectedString;
        Double expectedNumber;
    }

    private static final class JsonMutation extends JsonTarget {
        Boolean booleanValue;
        String stringValue;
        Double numberValue;
    }

    private static final class FileAssertion {
        String id;
        String path;
        String contains;
        int minimumOccurrences = 1;
    }

    private static final class WorldgenExpectations {
        Map<String, Long> minimumCounts = new LinkedHashMap<>();
        Map<String, Long> maximumCounts = new LinkedHashMap<>();
        boolean zeroBiomeViolations = true;
    }
}
