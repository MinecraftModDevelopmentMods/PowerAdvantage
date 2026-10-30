package zone.moddev.mc.advantageworks;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

@Mod(modid = "advantageworkstest", name = "Advantage Works Test Harness", version = "1.1.0.112021",
        acceptableRemoteVersions = "*", dependencies = "required-after:poweradvantage")
public final class AdvantageWorksMod {
    @Mod.Instance("advantageworkstest") public static AdvantageWorksMod instance;
    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        net.minecraftforge.common.ForgeChunkManager.setForcedChunkLoadingCallback(this, (tickets, world) -> {
            for (net.minecraftforge.common.ForgeChunkManager.Ticket ticket : tickets)
                for (net.minecraft.util.math.ChunkPos chunk : ticket.getChunkList())
                    net.minecraftforge.common.ForgeChunkManager.forceChunk(ticket, chunk);
        });
        MachineOperationalProbe.register();
    }
    @Mod.EventHandler public void serverStarting(FMLServerStartingEvent event) {
        MachineOperationalProbe.registerCommands(event);
        NativeHarvestProbe.registerCommands(event);
        NativeRecipeProgressionProbe.registerCommands(event);
    }
}
