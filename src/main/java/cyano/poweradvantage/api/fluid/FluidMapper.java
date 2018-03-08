package cyano.poweradvantage.api.fluid;

import cyano.poweradvantage.PowerAdvantage;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;

/**
 * Created by Jacob on 3/7/2018.
 */
public class FluidMapper extends StateMapperBase {
    private ModelResourceLocation resourceLocation;

    public FluidMapper(ModelResourceLocation locationIn){
        resourceLocation = locationIn;
    }

    @Override
    protected ModelResourceLocation getModelResourceLocation(IBlockState state) {

        final ModelResourceLocation fluidModelLocation = new ModelResourceLocation(
                PowerAdvantage.MODID.toLowerCase() + ":" + state.getBlock().getRegistryName().getResourcePath(), "normal");
//        System.out.println(String.format("Getting resource location %s, %s", fluidModelLocation.toString(), state.getBlock().toString()));
        return fluidModelLocation;
    }
}
