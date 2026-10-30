package zone.moddev.mc.advantageworks.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;

/** Native, worldless GUI capture only; no operating or migration claims. */
@Mod(modid = ClientResourceProbe.MODID, name = "Advantage GUI Capture", version = "1.0.0.110021",
        clientSideOnly = true, acceptedMinecraftVersions = "[1.10.2]",
        dependencies = "required-after:poweradvantage;required-after:steamadvantage;required-after:electricadvantage")
@Mod.EventBusSubscriber(value = Side.CLIENT)
public final class ClientResourceProbe {
    static final String MODID = "advantageclientresources";
    private static boolean finished;
    private static int ticks;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("advantageworks.clientResources") || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (++ticks < 5) return;
        finished = true;
        JsonObject result = new JsonObject();
        result.addProperty("scope", "MACHINE_GUI_ONLY");
        try {
            result.addProperty("minecraft", ForgeVersion.mcVersion);
            result.addProperty("forge", ForgeVersion.getVersion());
            require("1.10.2".equals(ForgeVersion.mcVersion) && "12.18.3.2511".equals(ForgeVersion.getVersion()),
                    "Wrong native client target");
            require(minecraft.world == null && minecraft.player == null && !minecraft.isIntegratedServerRunning(),
                    "GUI capture must not open a world or connect to a server");
            require(Boolean.getBoolean("advantageworks.requireExtensions")
                    && Boolean.getBoolean("advantageworks.machineGuiLayouts"), "All three mods and the GUI audit are required");
            JsonArray mods = new JsonArray();
            JsonObject versions = new JsonObject();
            for (String id : new String[]{"poweradvantage", "steamadvantage", "electricadvantage"}) {
                ModContainer mod = Loader.instance().getIndexedModList().get(id);
                require(mod != null, "Combined launch omitted " + id);
                String expected = System.getProperty("advantageworks.expectedVersion." + id);
                require(expected != null && !expected.isEmpty() && expected.equals(mod.getVersion()),
                        "Candidate version differs from frozen input: " + id);
                mods.add(new com.google.gson.JsonPrimitive(id));
                versions.addProperty(id, mod.getVersion());
            }
            result.add("advantage_mods", mods);
            result.add("mod_versions", versions);
            require(minecraft.fontRendererObj != null && minecraft.getTextureManager() != null, "Client resources are not ready");
            MachineGuiLayoutProbe.verify(minecraft, result);
            require(result.get("machine_gui_layout_count").getAsInt() == 39
                    && result.get("machine_gui_text_spec").getAsInt() == 2
                    && result.get("machine_gui_text_checks").getAsInt() == 351
                    && result.get("machine_gui_negative_controls_rejected").getAsInt() == 355,
                    "Incomplete native GUI coverage");
            result.addProperty("status", "PASS");
            LogManager.getLogger(MODID).info("ADVANTAGE_MACHINE_GUIS PASS screens=39 captions=351 controls=355");
        } catch (RuntimeException | LinkageError | AssertionError failure) {
            result.addProperty("status", "FAIL");
            result.addProperty("error", failure.toString());
            LogManager.getLogger(MODID).error("ADVANTAGE_MACHINE_GUIS FAIL", failure);
        } finally {
            try { Files.write(Paths.get("client-resources.json"), result.toString().getBytes(StandardCharsets.UTF_8)); }
            catch (IOException failure) { throw new IllegalStateException("Could not write native GUI result", failure); }
            finally { minecraft.shutdown(); }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
