package zone.moddev.mc.advantageworks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class Native116PackMetadataTest {
    @Test public void harnessResourcesDeclareTheNative116PackFormat() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/pack.mcmeta")) {
            assertNotNull(input);
            JsonObject pack = new JsonParser().parse(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("pack");
            assertTrue(pack.getAsJsonPrimitive("pack_format").isNumber());
            assertEquals(6, pack.get("pack_format").getAsInt());
        }
    }
}
