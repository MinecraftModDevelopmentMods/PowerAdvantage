package com.mcmoddev.poweradvantage.item.tool;

import com.mcmoddev.poweradvantage.PowerAdvantage;
import com.mcmoddev.poweradvantage.PowerAdvantageTab;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class ItemManual extends Item {
    public ItemManual() {
        setUnlocalizedName("poweradvantage.manual");
        setRegistryName("manual");
        setCreativeTab(PowerAdvantageTab.TAB);
        GameRegistry.register(this);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(ItemStack itemStackIn, World worldIn, EntityPlayer playerIn, EnumHand hand) {
        playerIn.openGui(PowerAdvantage.INSTANCE, 0, worldIn, 0, 0, 0);
        return super.onItemRightClick(itemStackIn, worldIn, playerIn, hand);
    }
}