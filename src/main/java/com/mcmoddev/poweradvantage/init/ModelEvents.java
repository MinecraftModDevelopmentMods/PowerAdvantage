package com.mcmoddev.poweradvantage.init;

import java.util.List;
import java.util.stream.Collectors;

import com.mcmoddev.lib.client.renderer.FluidStateMapper;
import com.mcmoddev.poweradvantage.PowerAdvantage;

import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@EventBusSubscriber(Side.CLIENT)
public class ModelEvents {
    
    @SubscribeEvent
    public static void registerModels(final ModelRegistryEvent event) {
    	List<ItemStack> machines = Materials.getMaterialByName("pa-machines").getItemRegistry().entrySet().stream().map(ent -> ent.getValue()).collect(Collectors.toList());
    	machines.addAll(Materials.getMaterialByName("steel").getItems().stream().filter( it -> it.getItem().getRegistryName().getNamespace().equals(PowerAdvantage.MODID)).collect(Collectors.toList()));
    	PowerAdvantage.LOGGER.info("Trying for registration of #inventory variants, %d in number", machines.size());
    	machines.stream()
    	.map(ItemStack::getItem)
    	.forEach(it -> {
    		PowerAdvantage.LOGGER.info("Registering #inventory variant for %s", it.getRegistryName());
    		ModelLoader.setCustomModelResourceLocation(it, 0, new ModelResourceLocation(it.getRegistryName(), "inventory"));
    	});

    	final Block block = Materials.getMaterialByName("pa-machines").getBlock("steam");
    	final Item item = Item.getItemFromBlock(block);
    	final ResourceLocation resLoc = block.getRegistryName();
    	final FluidStateMapper mapper = new FluidStateMapper( resLoc.toString() );
    	ModelBakery.registerItemVariants(item);
    	ModelLoader.setCustomMeshDefinition(item, mapper);
    	ModelLoader.setCustomStateMapper(block, mapper);
    }

}
