package com.mcmoddev.poweradvantage.util;

import java.io.File;

import com.mcmoddev.lib.util.Config;
import com.mcmoddev.poweradvantage.PowerAdvantage;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class PAConfig extends Config {

	private static Configuration configuration;
	private static final String CONFIG_FILE = "config/PowerAdvantage3.cfg";
	private static final String GENERAL_CAT = "General";

	@SubscribeEvent
	public void onConfigChange(final ConfigChangedEvent.OnConfigChangedEvent event) {
		if (event.getModID().equals(PowerAdvantage.MODID)) {
			init();
		}
	}
	
	public static void init() {
		if (configuration == null) {
			configuration = new Configuration(new File(CONFIG_FILE));
			MinecraftForge.EVENT_BUS.register(new PAConfig());
			configuration.get(GENERAL_CAT, "test", false);
			configuration.save();
		}
	}
}
