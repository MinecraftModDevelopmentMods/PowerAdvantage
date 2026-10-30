package zone.moddev.mc.advantageworks;

import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeRecipeProgressionDiagnosticsTest {
    @Test public void unlabelledNpeRetainsItsExactCallSiteAndStage() throws Exception {
        NullPointerException failure = npe();
        JsonObject row = record("native-slot-on-take", failure);
        assertEquals("UNVERIFIED", row.get("status").getAsString());
        assertEquals("native-slot-on-take", row.get("failureStage").getAsString());
        assertEquals("java.lang.NullPointerException", row.get("reason").getAsString());
        String trace = row.get("stackTrace").getAsString();
        assertTrue(trace.contains("NativeRecipeProgressionDiagnosticsTest.npe("));
        assertTrue(trace.contains("java.lang.NullPointerException"));
    }

    @Test public void wrappedFailuresRetainTheirCauses() throws Exception {
        JsonObject row = record("recipe-book-reset", new IllegalStateException("outer", npe()));
        assertTrue(row.get("stackTrace").getAsString().contains("Caused by: java.lang.NullPointerException"));
        assertTrue(row.get("stackTrace").getAsString().contains("IllegalStateException: outer"));
    }

    @Test public void linkageErrorsAreNotReclassifiedAsPassingRecipes() throws Exception {
        JsonObject row = record("native-result", new NoSuchMethodError("native mapping witness"));
        assertEquals("UNVERIFIED", row.get("status").getAsString());
        assertTrue(row.get("stackTrace").getAsString().contains("NoSuchMethodError: native mapping witness"));
    }

    private static NullPointerException npe() { return new NullPointerException(); }

    private static JsonObject record(String stage, Throwable failure) throws Exception {
        Method method = NativeRecipeProgressionProbe.class.getDeclaredMethod("recordFailure", JsonObject.class, String.class, Throwable.class);
        method.setAccessible(true);
        JsonObject row = new JsonObject();
        method.invoke(null, row, stage, failure);
        return row;
    }
}
