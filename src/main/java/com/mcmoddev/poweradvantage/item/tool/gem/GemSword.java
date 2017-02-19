package com.mcmoddev.poweradvantage.item.tool.gem;

import com.mcmoddev.poweradvantage.misc.Crystal;
import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class GemSword extends ItemSword implements IItemColor {
    private Crystal crystal;
    public GemSword(Crystal crystal) {
        super(ToolMaterial.DIAMOND);
        this.crystal = crystal;
        setRegistryName("sword_" + crystal.getOreDict().toLowerCase());
        setUnlocalizedName("poweradvantage.sword_" + crystal.getOreDict().toLowerCase());
        GameRegistry.register(this);
    }

    @Override
    public int getColorFromItemstack(ItemStack stack, int tintIndex) {
        if (tintIndex == 1)
            return this.crystal.getRGB();
        return 0xFFFFFF;
    }
}
