package zone.moddev.mc.advantageworks.runner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class WorldgenFailureCleanupTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void expiredDeadlineStopsAndDestroysWithoutAnyExtraWait() throws Exception {
        FakeProcess process = new FakeProcess();
        Exception failure = requestFailure(process, temporary.newFolder(), System.nanoTime() - 1L);
        assertTrue(failure.getMessage().contains("shared shutdown budget"));
        assertFalse("Expired scan leaked its owned process", process.alive);
        assertTrue(process.commands().contains("stop"));
        assertEquals(0, process.waitCalls);
        assertEquals(1, process.destroyCalls);
    }

    @Test public void staleReportCannotLeaveAnOwnedProcessBehind() throws Exception {
        File results = temporary.newFolder();
        File stale = new File(results, "worldgen-sample.json");
        Files.write(stale.toPath(), "old report".getBytes(StandardCharsets.UTF_8));
        FakeProcess process = new FakeProcess();
        requestFailure(process, results, System.nanoTime() - 1L);
        assertFalse("Stale completion leaked its owned process", process.alive);
        File[] archived = results.listFiles((dir, name) -> name.startsWith("worldgen-sample.json.before-first-run-"));
        assertNotNull(archived);
        assertEquals(1, archived.length);
        assertEquals("old report", new String(Files.readAllBytes(archived[0].toPath()), StandardCharsets.UTF_8));
        assertEquals(0, process.waitCalls);
    }

    @Test public void failedReportUsesOnlyTheRemainingBudgetThenDestroys() throws Exception {
        File results = temporary.newFolder();
        FakeProcess process = new FakeProcess();
        process.afterCommand = () -> {
            try {
                if (process.commands().contains("advworks sample-worldgen")) {
                    Files.write(new File(results, "worldgen-sample-failed.json").toPath(),
                            "{\"failure\":\"population failed\"}".getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception failure) { throw new IllegalStateException(failure); }
        };
        long budget = TimeUnit.SECONDS.toNanos(300);
        Exception failure = requestFailure(process, results, System.nanoTime() + budget);
        assertTrue(failure.getMessage().contains("population failed"));
        assertFalse("Failed report leaked its owned process", process.alive);
        assertEquals(1, process.waitCalls);
        assertTrue(process.waitNanos > 0 && process.waitNanos <= budget);
        assertEquals(1, process.destroyCalls);
    }

    @Test public void archiveFailureKeepsThePrimaryExceptionAndStopsBeforeAnySampleCommand() throws Exception {
        FakeProcess process = new FakeProcess();
        IOException primary = new IOException("archive failed");
        try {
            WorksRunner.withWorldgenFailureCleanup(process, 30L, () -> 27L, () -> { throw primary; });
            fail("Archive failure disappeared");
        } catch (IOException actual) { assertSame(primary, actual); }
        assertFalse(process.alive);
        assertEquals("stop" + System.lineSeparator(), process.commands());
        assertEquals(3L, process.waitNanos);
        assertEquals(0, primary.getSuppressed().length);
    }

    @Test public void cleanupUsesTheBudgetRemainingAfterSendingStop() throws Exception {
        FakeProcess process = new FakeProcess();
        long[] clock = {27L};
        process.afterCommand = () -> clock[0] = 29L;
        Exception primary = new Exception("scan failed");
        try {
            WorksRunner.withWorldgenFailureCleanup(process, 30L, () -> clock[0], () -> { throw primary; });
            fail("Scan failure disappeared");
        } catch (Exception actual) { assertSame(primary, actual); }
        assertEquals(1L, process.waitNanos);
        assertEquals(1, process.destroyCalls);
    }

    @Test public void gracefulFailureShutdownDoesNotForceDestroyAnExitedProcess() throws Exception {
        FakeProcess process = new FakeProcess();
        process.exitOnWait = true;
        Exception primary = new Exception("scan failed");
        try {
            WorksRunner.withWorldgenFailureCleanup(process, 30L, () -> 27L, () -> { throw primary; });
            fail("Scan failure disappeared");
        } catch (Exception actual) { assertSame(primary, actual); }
        assertFalse(process.alive);
        assertEquals(1, process.waitCalls);
        assertEquals(0, process.destroyCalls);
    }

    @Test public void interruptionDestroysPromptlyWithoutWaitingAndRestoresInterruptStatus() throws Exception {
        FakeProcess process = new FakeProcess();
        InterruptedException primary = new InterruptedException("user interrupted");
        try {
            try {
                WorksRunner.withWorldgenFailureCleanup(process, Long.MAX_VALUE, () -> 0L, () -> { throw primary; });
                fail("Interruption disappeared");
            } catch (InterruptedException actual) { assertSame(primary, actual); }
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, process.waitCalls);
            assertFalse(process.alive);
        } finally { Thread.interrupted(); }
    }

    @Test public void interruptionDuringCleanupIsSuppressedAndStillDestroysPromptly() throws Exception {
        FakeProcess process = new FakeProcess();
        Exception primary = new Exception("scan failed");
        process.waitFailure = new InterruptedException("interrupted while stopping");
        try {
            try {
                WorksRunner.withWorldgenFailureCleanup(process, 30L, () -> 27L, () -> { throw primary; });
                fail("Scan failure disappeared");
            } catch (Exception actual) { assertSame(primary, actual); }
            assertTrue(Thread.currentThread().isInterrupted());
            assertArrayEquals(new Throwable[] {process.waitFailure}, primary.getSuppressed());
            assertEquals(1, process.waitCalls);
            assertFalse(process.alive);
        } finally { Thread.interrupted(); }
    }

    @Test public void interruptionWhileSendingStopSkipsTheRemainingWait() throws Exception {
        FakeProcess process = new FakeProcess();
        Exception primary = new Exception("scan failed");
        process.afterCommand = () -> Thread.currentThread().interrupt();
        try {
            try {
                WorksRunner.withWorldgenFailureCleanup(process, Long.MAX_VALUE, () -> 0L, () -> { throw primary; });
                fail("Scan failure disappeared");
            } catch (Exception actual) { assertSame(primary, actual); }
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(0, process.waitCalls);
            assertFalse(process.alive);
        } finally { Thread.interrupted(); }
    }

    @Test public void stopWriteFailureIsSuppressedAndCannotSkipForcedDestruction() throws Exception {
        FakeProcess process = new FakeProcess();
        Exception primary = new Exception("scan failed");
        process.writeFailure = new IOException("stop pipe failed");
        try {
            WorksRunner.withWorldgenFailureCleanup(process, 30L, () -> 27L, () -> { throw primary; });
            fail("Scan failure disappeared");
        } catch (Exception actual) { assertSame(primary, actual); }
        assertArrayEquals(new Throwable[] {process.writeFailure}, primary.getSuppressed());
        assertEquals(0, process.waitCalls);
        assertFalse(process.alive);
    }

    @Test public void forcedDestructionFailureIsSuppressedRatherThanReplacingTheScanFailure() throws Exception {
        FakeProcess process = new FakeProcess();
        Exception primary = new Exception("scan failed");
        process.destroyFailure = new IllegalStateException("destroy failed");
        try {
            WorksRunner.withWorldgenFailureCleanup(process, 30L, () -> 30L, () -> { throw primary; });
            fail("Scan failure disappeared");
        } catch (Exception actual) { assertSame(primary, actual); }
        assertArrayEquals(new Throwable[] {process.destroyFailure}, primary.getSuppressed());
        assertEquals(0, process.waitCalls);
        assertEquals(1, process.destroyCalls);
    }

    @Test public void alreadyExitedProcessNeedsNoCleanupCommandsOrWait() throws Exception {
        FakeProcess process = new FakeProcess();
        process.alive = false;
        Exception primary = new Exception("process already exited");
        try {
            WorksRunner.withWorldgenFailureCleanup(process, 30L, () -> 27L, () -> { throw primary; });
            fail("Scan failure disappeared");
        } catch (Exception actual) { assertSame(primary, actual); }
        assertEquals("", process.commands());
        assertEquals(0, process.waitCalls);
        assertEquals(0, process.destroyCalls);
    }

    @Test public void completedRequestDoesNotStopTheProcessBeforeNormalSaveAndStop() throws Exception {
        FakeProcess process = new FakeProcess();
        WorksRunner.withWorldgenFailureCleanup(process, 30L, () -> 27L, () -> {});
        assertTrue(process.alive);
        assertEquals("", process.commands());
        assertEquals(0, process.waitCalls);
        assertEquals(0, process.destroyCalls);
    }

    private static Exception requestFailure(FakeProcess process, File results, long deadline) throws Exception {
        Method method = WorksRunner.class.getDeclaredMethod("awaitWorldgen", Process.class, File.class,
                String.class, int.class, long.class);
        method.setAccessible(true);
        try { method.invoke(null, process, results, "first-run", 16, deadline); fail("Request unexpectedly completed"); }
        catch (InvocationTargetException failure) {
            assertTrue(failure.getCause() instanceof Exception);
            return (Exception) failure.getCause();
        }
        throw new AssertionError("No failure");
    }

    private static final class FakeProcess extends Process {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean alive = true;
        int waitCalls, destroyCalls;
        long waitNanos;
        boolean exitOnWait;
        IOException writeFailure;
        InterruptedException waitFailure;
        RuntimeException destroyFailure;
        Runnable afterCommand = () -> {};

        String commands() { return new String(output.toByteArray(), StandardCharsets.UTF_8); }
        @Override public OutputStream getOutputStream() {
            return new OutputStream() {
                @Override public void write(int value) throws IOException {
                    if (writeFailure != null) throw writeFailure;
                    output.write(value);
                }
                @Override public void flush() { afterCommand.run(); }
            };
        }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public InputStream getErrorStream() { return new ByteArrayInputStream(new byte[0]); }
        @Override public boolean isAlive() { return alive; }
        @Override public int waitFor() { throw new AssertionError("Unbounded wait is forbidden"); }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            waitCalls++;
            waitNanos = unit.toNanos(timeout);
            if (waitFailure != null) throw waitFailure;
            if (exitOnWait) alive = false;
            return !alive;
        }
        @Override public int exitValue() { if (alive) throw new IllegalThreadStateException(); return 0; }
        @Override public void destroy() { throw new AssertionError("Use explicit forced cleanup"); }
        @Override public Process destroyForcibly() {
            destroyCalls++;
            if (destroyFailure != null) throw destroyFailure;
            alive = false;
            return this;
        }
    }
}
