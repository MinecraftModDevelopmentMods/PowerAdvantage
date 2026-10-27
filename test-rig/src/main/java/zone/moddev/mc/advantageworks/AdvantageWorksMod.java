package zone.moddev.mc.advantageworks;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

@Mod(AdvantageWorksMod.MODID)
public final class AdvantageWorksMod {
    public static final String MODID = "advantageworkstest";
    public static final String VERSION = "1.1.0.115021";

    public AdvantageWorksMod() {
        MigrationProbe.register();
        ConnectedProbe.register();
        ActorProbe.register();
        DimensionEntityProbe.register();
        LifecycleProbe.register();
        RecipeUnlockProbe.register();
        ProgressionProbe.register();
        NativeInteropProbe.register();
        NativeHarvestProbe.register();
        WorksController.getInstance().loadDefinition();
        MinecraftForge.EVENT_BUS.addListener(this::serverStarting);
    }

    private void serverStarting(FMLServerStartingEvent event) {
        AdvantageWorksCommand.register(event.getCommandDispatcher());
    }
}
