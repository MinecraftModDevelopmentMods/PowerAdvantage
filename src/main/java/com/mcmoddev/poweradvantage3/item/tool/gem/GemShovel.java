package com.mcmoddev.poweradvantage3.item.tool.gem;

import com.mcmoddev.poweradvantage3.misc.Crystal;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class GemShovel extends ItemSpade implements IItemColor {
    private Crystal crystal;

    public GemShovel(Crystal crystal) {
        super(ToolMaterial.DIAMOND);
        this.crystal = crystal;
        setRegistryName("shovel_" + crystal.getOreDict().toLowerCase());
        setUnlocalizedName("poweradvantage3.shovel_" + crystal.getOreDict().toLowerCase());
        GameRegistry.register(this);
    }

    @Override
    public int getColorFromItemstack(ItemStack stack, int tintIndex) {
        if (tintIndex == 1)
            return this.crystal.getRGB();
        return 0xFFFFFF;
    }
}
