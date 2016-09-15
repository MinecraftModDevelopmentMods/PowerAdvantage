package mmd.poweradvantage.mod;

import mmd.poweradvantage.api.energy.IPowerConsumer;
import mmd.poweradvantage.api.energy.IPowerHolder;
import mmd.poweradvantage.api.energy.IPowerProducer;
import mmd.poweradvantage.api.energy.implementation.PowerContainer;
import mmd.poweradvantage.mod.capability.PowerCapabilities;
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

        CapabilityManager.INSTANCE.register(IPowerConsumer.class, new PowerCapabilities.CapabilityEnergyConsumer<>(), PowerContainer.class);
        CapabilityManager.INSTANCE.register(IPowerProducer.class, new PowerCapabilities.CapabilityEnergyProducer<>(), PowerContainer.class);
        CapabilityManager.INSTANCE.register(IPowerHolder.class, new PowerCapabilities.CapabilityEnergyHolder<>(), PowerContainer.class);
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