package com.mcmoddev.poweradvantage.init;

import com.mcmoddev.lib.data.SharedStrings;
import com.mcmoddev.lib.events.IRegAPI;
import com.mcmoddev.lib.events.MMDLibRegisterBlockTypes;

import com.mcmoddev.poweradvantage.PowerAdvantage;

import net.minecraft.block.Block;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@SuppressWarnings("unused")
@EventBusSubscriber(modid=PowerAdvantage.MODID)
public class BlockTypes {
	private BlockTypes() {
        throw new IllegalAccessError(SharedStrings.NOT_INSTANTIABLE);
	}

	@SubscribeEvent
    public static void blockTypeRegistrationEvent(final MMDLibRegisterBlockTypes ev) {
            IRegAPI<Block> api = ev.getApi();
            
    }
}
