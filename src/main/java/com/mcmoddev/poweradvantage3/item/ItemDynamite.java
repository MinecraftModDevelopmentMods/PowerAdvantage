package com.mcmoddev.poweradvantage3.item;

import com.mcmoddev.poweradvantage3.entity.EntityDynamite;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.List;

public class ItemDynamite extends Item {

    public ItemDynamite() {
        setHasSubtypes(true);
        setRegistryName("dynamite");
        setUnlocalizedName("poweradvantage3.dynamite");
        GameRegistry.register(this);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(ItemStack itemStackIn, World worldIn, EntityPlayer playerIn, EnumHand hand) {
        if (!playerIn.capabilities.isCreativeMode) {
            --itemStackIn.stackSize;
        }
        worldIn.playSound(null, playerIn.posX, playerIn.posY, playerIn.posZ, SoundEvents.UI_BUTTON_CLICK, SoundCategory.NEUTRAL, 0.5F, 0.4F / (itemRand.nextFloat() * 0.4F + 0.8F));
        if (!worldIn.isRemote) {
            EntityDynamite dynamite = new EntityDynamite(worldIn, playerIn);
            dynamite.setHeadingFromThrower(playerIn, playerIn.rotationPitch, playerIn.rotationYaw, 0.0F, 1.5F, 1.0F);
            worldIn.spawnEntity(dynamite);
        }

        playerIn.addStat(StatList.getObjectUseStats(this));
        return ActionResult.newResult(EnumActionResult.SUCCESS, itemStackIn);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item itemIn, CreativeTabs tab, List<ItemStack> subItems) {
        for (Type type : Type.values())
            subItems.add(new ItemStack(itemIn, 1, type.ordinal()));
    }

    public enum Type {
        NORMAL,
        FIREY,
        STICKY;

        public static Type byMeta(int meta) {
            if (meta >= values().length)
                meta = 0;
            return values()[meta];
        }
    }
}