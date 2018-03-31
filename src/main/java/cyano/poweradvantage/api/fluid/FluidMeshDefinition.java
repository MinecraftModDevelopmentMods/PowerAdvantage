package cyano.poweradvantage.api.fluid;

import net.minecraft.client.renderer.ItemMeshDefinition;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.ItemStack;

/**
 * Created by Jacob on 3/7/2018.
 */
public class FluidMeshDefinition implements ItemMeshDefinition {
    private ModelResourceLocation resourceLocation;


    public FluidMeshDefinition(ModelResourceLocation locationIn){
        resourceLocation = locationIn;
    }

    @Override
    public ModelResourceLocation getModelLocation(ItemStack stack) {
        System.out.println(String.format("Getting resource location %s, %s", resourceLocation.toString(), stack.getItem().getRegistryName().toString()));
        return resourceLocation;
    }
}
