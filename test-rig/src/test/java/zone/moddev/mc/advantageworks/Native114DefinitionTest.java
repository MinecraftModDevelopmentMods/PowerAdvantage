package zone.moddev.mc.advantageworks;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class Native114DefinitionTest {
    @Test
    public void nativeTargetPreservesEverySourceStationAndDistrict() throws Exception {
        Gson gson = new Gson();
        try (InputStreamReader reader = new InputStreamReader(getClass().getResourceAsStream(
                "/assets/advantageworkstest/works/works-1.13.2.json"), StandardCharsets.UTF_8)) {
            WorksDefinition source = gson.fromJson(reader, WorksDefinition.class);
            WorksDefinition target = WorksDefinitionLoader.load();
            assertEquals("1.13.2", source.minecraft);
            assertEquals("1.14.4", target.minecraft);
            assertEquals(21, target.stations.size());
            assertEquals(gson.toJsonTree(source.districts), gson.toJsonTree(target.districts));
            assertEquals(gson.toJsonTree(source.stations), gson.toJsonTree(target.stations));
        }
    }
}
