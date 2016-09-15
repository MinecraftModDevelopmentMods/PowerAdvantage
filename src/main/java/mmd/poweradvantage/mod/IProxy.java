package mmd.poweradvantage.mod;

import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@SuppressWarnings("unused")
public interface IProxy {

    void onConstruct(FMLConstructionEvent e);

    void preInit(FMLPreInitializationEvent e);

    void init(FMLInitializationEvent e);

    void postInit(FMLPostInitializationEvent e);

    void registerRenderers();

    void registerEventHandlers();

}
