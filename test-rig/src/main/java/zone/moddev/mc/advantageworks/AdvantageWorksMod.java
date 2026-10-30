package zone.moddev.mc.advantageworks;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(
        modid = AdvantageWorksMod.MODID,
        name = "Advantage Works Test Harness",
        version = AdvantageWorksMod.VERSION,
        acceptableRemoteVersions = "*"
)
public final class AdvantageWorksMod {
    public static final String MODID = "advantageworkstest";
    public static final String VERSION = "1.1.0.110021";
    @Mod.Instance(MODID) public static AdvantageWorksMod instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        WorksController.getInstance().loadDefinition();
        net.minecraftforge.common.ForgeChunkManager.setForcedChunkLoadingCallback(this, (tickets, world) -> {
            for (net.minecraftforge.common.ForgeChunkManager.Ticket ticket : tickets)
                for (net.minecraft.util.math.ChunkPos chunk : ticket.getChunkList())
                    net.minecraftforge.common.ForgeChunkManager.forceChunk(ticket, chunk);
        });
        MachineOperationalProbe.register();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new AdvantageWorksCommand());
        MachineOperationalProbe.registerCommands(event);
        NativeHarvestProbe.registerCommands(event);
        NativeRecipeProgressionProbe.registerCommands(event);
    }
}
