package zone.moddev.mc.advantageworks;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class Native116DefinitionTest {
    @Test
    public void nativeTargetPreservesEverySourceStationAndDistrict() throws Exception {
        Gson gson = new Gson();
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(
                "/assets/advantageworkstest/works/works-1.13.2.json"), StandardCharsets.UTF_8)) {
            WorksDefinition source = gson.fromJson(reader, WorksDefinition.class);
            WorksDefinition target = WorksDefinitionLoader.loadNative116();
            assertEquals("1.13.2", source.minecraft);
            assertEquals("1.16.5", target.minecraft);
            assertEquals(21, target.stations.size());
            assertEquals(gson.toJsonTree(source.districts), gson.toJsonTree(target.districts));
            assertEquals(gson.toJsonTree(source.stations), gson.toJsonTree(target.stations));
        }
    }
}
