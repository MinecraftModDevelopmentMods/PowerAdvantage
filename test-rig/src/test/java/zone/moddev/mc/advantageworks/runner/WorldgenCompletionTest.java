package zone.moddev.mc.advantageworks.runner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class WorldgenCompletionTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void archivesStaleSuccessAndFailureBeforeEitherRunWaits() throws Exception {
        File results = temporary.newFolder();
        for (String phase : new String[] {"first-run", "restart-run"}) {
            File old = new File(results, "worldgen-sample.json");
            File failed = new File(results, "worldgen-sample-failed.json");
            write(old, report());
            write(failed, "{\"failure\":\"previous failure\"}");
            WorksRunner.WorldgenCompletion completion = new WorksRunner.WorldgenCompletion(results, phase);
            assertFalse(old.exists());
            assertFalse(failed.exists());
            assertEquals(report(), read(completion.previousReport));
            assertEquals("{\"failure\":\"previous failure\"}", read(completion.previousFailure));
            assertFalse(completion.complete());
        }
    }

    @Test public void aNewReportWithIdenticalCountsAndTimestampIsStillFresh() throws Exception {
        File results = temporary.newFolder();
        write(new File(results, "worldgen-sample.json"), report());
        WorksRunner.WorldgenCompletion completion = new WorksRunner.WorldgenCompletion(results, "restart-run");
        assertFalse(completion.complete());
        write(completion.report, report());
        assertTrue(completion.complete());
        assertEquals(report(), read(completion.previousReport));
    }

    @Test public void repeatedArchivalNeverReplacesAnOlderReport() throws Exception {
        File results = temporary.newFolder();
        write(new File(results, "worldgen-sample.json"), report());
        WorksRunner.WorldgenCompletion first = new WorksRunner.WorldgenCompletion(results, "first-run");
        write(first.report, report());
        WorksRunner.WorldgenCompletion second = new WorksRunner.WorldgenCompletion(results, "first-run");
        assertNotEquals(first.previousReport, second.previousReport);
        assertEquals(report(), read(first.previousReport));
        assertEquals(report(), read(second.previousReport));
    }

    @Test public void waitsForPublicationRatherThanAcceptingTheOldReport() throws Exception {
        File results = temporary.newFolder();
        write(new File(results, "worldgen-sample.json"), report());
        WorksRunner.WorldgenCompletion completion = new WorksRunner.WorldgenCompletion(results, "first-run");
        int[] pauses = {0};
        WorksRunner.awaitWorldgenCompletion(() -> true, completion, 10L, () -> 0L, () -> {
            pauses[0]++;
            assertFalse(completion.complete());
            write(completion.report, report());
        });
        assertEquals(1, pauses[0]);
    }

    @Test public void staleCompletionCannotSatisfyTheDeadline() throws Exception {
        File results = temporary.newFolder();
        write(new File(results, "worldgen-sample.json"), report());
        WorksRunner.WorldgenCompletion completion = new WorksRunner.WorldgenCompletion(results, "restart-run");
        long[] clock = {0L};
        try {
            WorksRunner.awaitWorldgenCompletion(() -> true, completion, 3L, () -> clock[0], () -> clock[0]++);
            fail("Stale report accepted");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("shared shutdown budget"));
        }
        assertEquals(3L, clock[0]);
        assertEquals(report(), read(completion.previousReport));
    }

    @Test public void aFailedJobIsRejectedEvenIfASuccessFileAlsoExists() throws Exception {
        WorksRunner.WorldgenCompletion completion = new WorksRunner.WorldgenCompletion(temporary.newFolder(), "first-run");
        write(completion.report, report());
        write(completion.failure, "{\"failure\":\"population failed\"}");
        try { completion.complete(); fail("Failed job accepted"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("population failed")); }
    }

    @Test public void serverExitBeforeCompletionFailsImmediately() throws Exception {
        WorksRunner.WorldgenCompletion completion = new WorksRunner.WorldgenCompletion(temporary.newFolder(), "first-run");
        try {
            WorksRunner.awaitWorldgenCompletion(() -> false, completion, 10L, () -> 0L,
                    () -> fail("Exited process must not be polled"));
            fail("Exited process accepted");
        } catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("Server exited")); }
    }

    @Test public void incompleteReportsAreNotCompletion() throws Exception {
        WorksRunner.WorldgenCompletion completion = new WorksRunner.WorldgenCompletion(temporary.newFolder(), "first-run");
        write(completion.report, "{\"format\":1,\"station\":\"worldgen\",\"kind\":\"sample\"}");
        try { completion.complete(); fail("Incomplete report accepted"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("Incomplete")); }
    }

    @Test public void samplingAndShutdownShareTheExistingDeadlineWithoutAnExtension() {
        long start = 123L;
        assertEquals(start + TimeUnit.SECONDS.toNanos(300), WorksRunner.worldgenDeadline(300, start));
    }

    @Test(expected = IllegalArgumentException.class) public void rejectsAnUnboundedCompletionBudget() {
        WorksRunner.worldgenDeadline(0, 0L);
    }

    private static String report() {
        return "{\"format\":1,\"recordedAtUtc\":\"2026-10-26T13:29:16Z\",\"station\":\"worldgen\","
                + "\"kind\":\"sample\",\"radius\":16,\"chunksScanned\":3267,\"centers\":{},\"counts\":{},"
                + "\"biomeViolations\":{},\"biomeBoundaryBlocks\":{}}";
    }

    private static void write(File file, String text) throws Exception {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
