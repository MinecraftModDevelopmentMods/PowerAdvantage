package com.mcmoddev.poweradvantage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.minecraft.init.Bootstrap;
import net.minecraftforge.fluids.FluidRegistry;
import org.junit.Test;

import static org.junit.Assert.*;

public class UniversalBucketStartupTest {
    @Test public void bucketOptInBelongsToTheMainClassStaticInitializer() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java",
                PowerAdvantage.class.getName().replace('.', '/') + ".java")), StandardCharsets.UTF_8);
        String body = source.substring(source.indexOf("public class PowerAdvantage"));
        assertTrue("Opt in during construction, not a lifecycle callback or an optional integration",
                java.util.regex.Pattern.compile("static\\s*\\{\\s*FluidRegistry\\.enableUniversalBucket\\(\\);\\s*\\}")
                        .matcher(body).find());
    }

    @Test public void initializingPowerEnablesTheForgeBucketApi() throws Exception {
        Bootstrap.register();
        Class.forName(PowerAdvantage.class.getName(), true, PowerAdvantage.class.getClassLoader());
        assertTrue("Power must opt in itself before Forge registers the item", FluidRegistry.isUniversalBucketEnabled());
    }
}
