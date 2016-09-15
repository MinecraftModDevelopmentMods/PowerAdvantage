package mmd.poweradvantage.mod;

import mmd.advantagecore.IProxy;
import mmd.advantagecore.energy.IAdvantageConsumer;
import mmd.advantagecore.energy.IAdvantageHolder;
import mmd.advantagecore.energy.IAdvantageProducer;
import mmd.advantagecore.energy.implementation.AdvantageContainer;
import mmd.advantagecore.capability.AdvantageCapabilities;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = ModPrefs.MODID, version = ModPrefs.VERSION, name = ModPrefs.NAME, dependencies = ModPrefs.DEPS, acceptableRemoteVersions = ModPrefs.ACC_MC)
@SuppressWarnings("unused")
public class PowerAdvantage {

    @Instance
    public static PowerAdvantage INSTANCE;

    @SidedProxy(clientSide = ModPrefs.CSIDE, serverSide = ModPrefs.SSIDE)
    public static IProxy PROXY;

    @EventHandler
    public void onConstruct(FMLConstructionEvent e) {
        PROXY.onConstruct(e);

        CapabilityManager.INSTANCE.register(IAdvantageConsumer.class, new AdvantageCapabilities.CapabilityAdvantageConsumer<>(), AdvantageContainer.class);
        CapabilityManager.INSTANCE.register(IAdvantageProducer.class, new AdvantageCapabilities.CapabilityAdvantageProducer<>(), AdvantageContainer.class);
        CapabilityManager.INSTANCE.register(IAdvantageHolder.class, new AdvantageCapabilities.CapabilityAdvantageHolder<>(), AdvantageContainer.class);
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        PROXY.preInit(e);
    }

    @EventHandler
    public void init(FMLInitializationEvent e) {
        PROXY.init(e);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent e) {
        PROXY.postInit(e);
    }

}