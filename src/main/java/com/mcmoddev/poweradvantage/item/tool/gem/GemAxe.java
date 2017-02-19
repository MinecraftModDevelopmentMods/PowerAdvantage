package com.mcmoddev.poweradvantage.item.tool.gem;

import com.mcmoddev.poweradvantage.item.tool.BaseAxe;
import com.mcmoddev.poweradvantage.misc.Crystal;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class GemAxe extends BaseAxe implements IItemColor {
    private Crystal crystal;
    public GemAxe(Crystal crystal) {
        super(ToolMaterial.DIAMOND, 8.1F, -2.9F);
        this.crystal = crystal;
        setRegistryName("axe_" + crystal.getOreDict().toLowerCase());
        setUnlocalizedName("poweradvantage.axe_" + crystal.getOreDict().toLowerCase());
        GameRegistry.register(this);
    }

    @Override
    public int getColorFromItemstack(ItemStack stack, int tintIndex) {
        if (tintIndex == 1)
            return this.crystal.getRGB();
        return 0xFFFFFF;
    }
}
