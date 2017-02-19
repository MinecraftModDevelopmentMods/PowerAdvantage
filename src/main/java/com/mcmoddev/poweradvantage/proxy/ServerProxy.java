package com.mcmoddev.poweradvantage.proxy;

import com.mcmoddev.poweradvantage.client.ModelFluidCell;
import com.mcmoddev.poweradvantage.client.TankTESR;
import com.mcmoddev.poweradvantage.init.RegisterJsonModels;
import com.mcmoddev.poweradvantage.tile.TileTank;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

public class ServerProxy extends CommonProxy {
    @Override
    public void preInit() {
        super.preInit();
    }

    @Override
    public void init() {
        super.init();
    }

    @Override
    public void postInit() {
        super.postInit();
    }

    @Override
    public void registerFluidRender(Block block, String name) {
        super.registerFluidRender(block, name);
    }
}
