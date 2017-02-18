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

@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        super.preInit();
        RegisterJsonModels.registerModels();
        ModelFluidCell.init();
        ClientRegistry.bindTileEntitySpecialRenderer(TileTank.class, new TankTESR());
    }

    @Override
    public void init() {
        super.init();
        RegisterJsonModels.registerColor();
    }

    @Override
    public void postInit() {
        super.postInit();
    }

    @Override
    public void registerFluidRender(Block block, String name) {
        super.registerFluidRender(block, name);

        final ModelResourceLocation fluidLocation = new ModelResourceLocation("poweradvantage:fluids", name);
        ModelLoader.setCustomStateMapper(block, new StateMapperBase() {
            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return fluidLocation;
            }
        });
    }
}
