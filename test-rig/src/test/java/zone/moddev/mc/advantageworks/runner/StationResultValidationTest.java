package zone.moddev.mc.advantageworks.runner;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class StationResultValidationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void emptySummaryCannotPass() throws Exception {
        rejects("requireAcceptableSummary", "{}", true);
    }

    @Test public void zeroCompletedSummaryCannotPass() throws Exception {
        rejects("requireAcceptableSummary", "{\"summary\":{\"SKIP\":21}}", true);
    }

    @Test public void unknownStationStatusCannotPass() throws Exception {
        rejects("requireAcceptableStations", "{\"status\":\"RUNNING\"}", false);
    }

    @Test public void passWithoutAssertionsCannotPass() throws Exception {
        rejects("requireAcceptableStations", "{\"station\":\"E-03\",\"status\":\"PASS\",\"assertions\":[]}", false);
    }

    @Test public void passCannotHideFailedAssertion() throws Exception {
        rejects("requireAcceptableStations", "{\"station\":\"E-03\",\"status\":\"PASS\","
                + "\"assertions\":[{\"id\":\"work\",\"status\":\"FAIL\"}]}", false);
    }

    @Test public void allSkippedAssertionsCannotBecomeAPass() throws Exception {
        rejects("requireAcceptableStations", "{\"station\":\"E-03\",\"status\":\"PASS\","
                + "\"assertions\":[{\"id\":\"work\",\"status\":\"SKIP\"}]}", false);
    }

    @Test public void knownBugIsNotAnUnqualifiedOperatingPass() throws Exception {
        rejects("requireAcceptableStations", "{\"station\":\"E-03\",\"status\":\"EXPECTED_FAIL\"}", false);
    }

    @Test public void earlierBootCannotSupplyMissingRestartResults() throws Exception {
        File root = temporary.newFolder();
        File stale = new File(root, "E-03-latest.json");
        byte[] bytes = "earlier pass".getBytes(StandardCharsets.UTF_8);
        Files.write(stale.toPath(), bytes);
        WorksRunner.archiveStationResults(root, "restart-run", Collections.singletonList("E-03"));
        assertFalse(stale.exists());
        assertArrayEquals(bytes, Files.readAllBytes(new File(root, "previous-restart-run/E-03-latest.json").toPath()));
    }

    @Test public void genuineMeasuredPassAndExplicitManualResultRemainDistinct() throws Exception {
        for (String status : new String[]{"PASS", "MANUAL_REQUIRED"}) {
            File root = temporary.newFolder();
            String json = "{\"station\":\"E-03\",\"status\":\"" + status + "\","
                    + "\"assertions\":[{\"id\":\"work\",\"status\":\"PASS\"}],\"manualChecks\":"
                    + (status.equals("MANUAL_REQUIRED") ? "[\"open GUI\"]" : "[]") + "}";
            Files.write(new File(root, "E-03-latest.json").toPath(), json.getBytes(StandardCharsets.UTF_8));
            Method check = WorksRunner.class.getDeclaredMethod("requireAcceptableStations", File.class, java.util.List.class);
            check.setAccessible(true);
            check.invoke(null, root, Collections.singletonList("E-03"));
        }
    }

    @Test public void completeSummaryMustAgreeWithEveryStation() throws Exception {
        com.google.gson.JsonObject summary = new com.google.gson.JsonObject();
        com.google.gson.JsonObject counts = new com.google.gson.JsonObject();
        com.google.gson.JsonObject stations = new com.google.gson.JsonObject();
        for (String id : new String[]{"W-01", "W-02", "W-03", "W-04", "S-01", "S-02", "S-03", "S-04",
                "S-05", "S-06", "S-07", "S-08", "E-01", "E-02", "E-03", "E-04", "E-05", "E-06", "E-07", "E-08", "P-API-01"}) {
            stations.addProperty(id, "MANUAL_REQUIRED");
        }
        counts.addProperty("MANUAL_REQUIRED", 21);
        summary.add("summary", counts);
        summary.add("stationStatuses", stations);
        File file = temporary.newFile();
        Method check = WorksRunner.class.getDeclaredMethod("requireAcceptableSummary", File.class);
        check.setAccessible(true);
        Files.write(file.toPath(), summary.toString().getBytes(StandardCharsets.UTF_8));
        check.invoke(null, file);
        counts.addProperty("MANUAL_REQUIRED", 20);
        Files.write(file.toPath(), summary.toString().getBytes(StandardCharsets.UTF_8));
        try { check.invoke(null, file); fail("Missing count accepted"); }
        catch (InvocationTargetException expected) { assertTrue(expected.getCause() instanceof IllegalStateException); }
    }

    private void rejects(String method, String json, boolean summary) throws Exception {
        File root = temporary.newFolder();
        File result = new File(root, summary ? "summary.json" : "E-03-latest.json");
        Files.write(result.toPath(), json.getBytes(StandardCharsets.UTF_8));
        Method check = summary ? WorksRunner.class.getDeclaredMethod(method, File.class)
                : WorksRunner.class.getDeclaredMethod(method, File.class, java.util.List.class);
        check.setAccessible(true);
        try {
            if (summary) check.invoke(null, result);
            else check.invoke(null, root, Collections.singletonList("E-03"));
            fail("Incomplete or contradictory result accepted: " + json);
        } catch (InvocationTargetException expected) {
            assertTrue(expected.getCause().toString(), expected.getCause() instanceof IllegalStateException);
        }
    }
}
