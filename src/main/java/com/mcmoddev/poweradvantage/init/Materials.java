package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.lib.data.SharedStrings;
import com.mcmoddev.lib.events.MMDLibRegisterMaterials;
import com.mcmoddev.lib.material.MMDMaterialType.MaterialType;
import com.mcmoddev.poweradvantage.PowerAdvantage;

import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@EventBusSubscriber
public class Materials extends com.mcmoddev.lib.init.Materials {

    private Materials() {
            throw new IllegalAccessError(SharedStrings.NOT_INSTANTIABLE);
    }

    /**
     *
     */
    @SubscribeEvent
    public static void init(final MMDLibRegisterMaterials event) {
    	createOrelessRareMaterial("pa-machine", MaterialType.METAL, 0.75f, 1.00f, 0.00f, 0xFFFFFFFF);
    	if (getMaterialByName("pa-machine").isEmpty()) PowerAdvantage.LOGGER.info("material \"pa-machine\" is Materials.EMPTY!");
    	else PowerAdvantage.LOGGER.info("material \"pa-machine\" not Materials.EMPTY");
    }
}
