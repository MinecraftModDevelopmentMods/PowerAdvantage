package com.mcmoddev.poweradvantage.proxy;

import com.mcmoddev.poweradvantage.client.ModelFluidCell;
import com.mcmoddev.poweradvantage.init.RegisterJsonModels;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        super.preInit();
        RegisterJsonModels.registerModels();
        ModelFluidCell.init();
    }

    @Override
    public void init() {
        super.init();
        RegisterJsonModels.registerColor();
    }
}
