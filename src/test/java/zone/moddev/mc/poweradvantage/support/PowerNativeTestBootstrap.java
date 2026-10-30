package zone.moddev.mc.poweradvantage.support;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;
import static org.junit.Assert.*;

/** Establishes Forge 25 launcher metadata for headless native-object unit fixtures only. */
public final class PowerNativeTestBootstrap {
    private PowerNativeTestBootstrap() {}

    public static synchronized void register() {
        Properties platform = new Properties();
        try (InputStream input = Files.newInputStream(Paths.get("gradle.properties"))) {
            platform.load(input);
            Class<?> loader = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            version(loader, "mcVersion", platform.getProperty("minecraft_version"));
            version(loader, "forgeVersion", platform.getProperty("forge_version"));
        } catch (java.io.IOException | ReflectiveOperationException failure) {
            throw new AssertionError("Cannot establish the configured Forge unit-test platform", failure);
        }
        net.minecraft.init.Bootstrap.register();
    }

    private static void version(Class<?> loader, String name, String expected) throws ReflectiveOperationException {
        assertNotNull("Missing configured version " + name, expected);
        Field field = loader.getDeclaredField(name);
        field.setAccessible(true);
        Object actual = field.get(null);
        if (actual == null) field.set(null, expected);
        else assertEquals("Conflicting test platform " + name, expected, actual);
    }
}
