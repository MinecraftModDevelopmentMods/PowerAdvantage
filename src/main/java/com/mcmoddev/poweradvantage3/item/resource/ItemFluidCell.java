package com.mcmoddev.poweradvantage3.item.resource;

import com.mcmoddev.poweradvantage3.PowerAdvantageTab;
import com.mcmoddev.poweradvantage3.init.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockStaticLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.templates.FluidHandlerItemStack;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.commons.lang3.Validate;

import java.util.List;

public class ItemFluidCell extends Item {

    public static final int CAPACITY = Fluid.BUCKET_VOLUME;

    public ItemFluidCell() {
        super();
        setCreativeTab(PowerAdvantageTab.TAB);
        setRegistryName("cell");
        setUnlocalizedName("poweradvantage3.cell");
        setMaxStackSize(16);
        GameRegistry.register(this);
    }

    public static FluidHandler getFluidHandler(ItemStack stack) {
        return new FluidHandler(stack, CAPACITY);
    }

    public static ItemStack getCellWithFluid(Fluid fluid, int stackSize) {
        Validate.notNull(fluid);
        ItemStack stack = new ItemStack(ModItems.FLUID_CELL, stackSize);
        getFluidHandler(stack).fill(new FluidStack(fluid, CAPACITY), true);
        return stack;
    }

    public static ItemStack getEmptyCell(int amount) {
        return new ItemStack(ModItems.FLUID_CELL, amount);
    }

    public static ItemStack getCellWithFluid(Fluid fluid) {
        return getCellWithFluid(fluid, 1);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(ItemStack stack, World worldIn, EntityPlayer playerIn, EnumHand hand) {
        if (!worldIn.isRemote) {
            RayTraceResult result = rayTrace(worldIn, playerIn, true);
            if (result.typeOfHit == RayTraceResult.Type.BLOCK) {
                BlockPos pos = result.getBlockPos();
                IBlockState state = worldIn.getBlockState(pos);
                Block block = state.getBlock();
                if (block instanceof IFluidBlock) {
                    IFluidBlock fluidBlock = (IFluidBlock) block;
                    if (fluidBlock.canDrain(worldIn, pos)) {
                        FluidStack fluid = fluidBlock.drain(worldIn, pos, false);
                        if (fluid != null && fluid.amount == ItemFluidCell.CAPACITY) {
                            if (tryAddCellToInventory(playerIn, stack, fluid.getFluid())) {
                                fluidBlock.drain(worldIn, pos, true);
                                return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
                            }
                        }
                    }
                } else if (block instanceof BlockStaticLiquid) {
                    Fluid fluid = block.getMaterial(state) == Material.LAVA ? FluidRegistry.LAVA : FluidRegistry.WATER;
                    if (tryAddCellToInventory(playerIn, stack, fluid)) {
                        if (fluid != FluidRegistry.WATER)
                            worldIn.setBlockToAir(pos);
                        return ActionResult.newResult(EnumActionResult.SUCCESS, stack);
                    }
                }
            }
        }
        return ActionResult.newResult(EnumActionResult.FAIL, stack);
    }

    public boolean tryAddCellToInventory(EntityPlayer player, ItemStack stack, Fluid fluid) {
        if (player.inventory.addItemStackToInventory(ItemFluidCell.getCellWithFluid(fluid))) {
            --stack.stackSize;
            return true;
        }
        return false;
    }

    @Override
    public void getSubItems(Item itemIn, CreativeTabs tab, List<ItemStack> subItems) {
        subItems.add(getEmptyCell(1));
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        FluidStack fluidStack = getFluidHandler(stack).getFluid();
        return String.format(super.getItemStackDisplayName(stack), fluidStack == null ? I18n.translateToLocal("item.poweradvantage3.cell.empty.name") : fluidStack.getLocalizedName());
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, NBTTagCompound nbt) {
        return getFluidHandler(stack);
    }

    public static class FluidHandler extends FluidHandlerItemStack {

        public FluidHandler(ItemStack container, int capacity) {
            super(container, capacity);
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource.amount != capacity)
                return 0;
            return super.fill(resource, doFill);
        }

        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (maxDrain != capacity)
                return null;
            return super.drain(maxDrain, doDrain);
        }

    }

}