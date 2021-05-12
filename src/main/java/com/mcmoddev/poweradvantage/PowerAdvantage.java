package com.mcmoddev.poweradvantage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mcmoddev.poweradvantage.util.PAConfig;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = PowerAdvantage.MODID, version = PowerAdvantage.VERSION, name=PowerAdvantage.NAME, dependencies = "required-after:forge@[14.23.1.2592,);required-after:mmdlib;after:basemetals;",
		acceptedMinecraftVersions = "[1.12,)")
public class PowerAdvantage
{
	public static final String MODID   = "poweradvantage";
	public static final String NAME    = "Power Advantage";
	public static final String VERSION = "3.0.0";
    public static final Logger LOGGER  = LogManager.getFormatterLogger(MODID);
    
    @Instance
	public static PowerAdvantage instance;

    @EventHandler
	public void construction(FMLConstructionEvent event) {
	    PAConfig.init();
	}
	
	@EventHandler
	public void preInit(FMLPreInitializationEvent event)
	{
	}

	@EventHandler
	public void init(FMLInitializationEvent event)
	{
	}


	@EventHandler
	public void postInit(FMLPostInitializationEvent event)
	{
	}

}
