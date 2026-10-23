package zone.moddev.mc.poweradvantage.machines.fluidmachines;

import java.util.List;

import javax.annotation.Nullable;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.FluidTank;
import cyano.poweradvantage.api.simple.BlockSimpleFluidMachine;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.IRegistry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import zone.moddev.mc.poweradvantage.init.Fluids;

/** Portable tank block. FluidID and Volume remain unchanged for old item stacks. */
public class StorageTankBlock extends BlockSimpleFluidMachine {
    public StorageTankBlock() { super(Material.PISTON, 3F); }

    @Override public PoweredEntity createNewTileEntity(IBlockReader world) { return new StorageTankTileEntity(); }
    @Override public boolean hasComparatorInputOverride(IBlockState state) { return true; }
    @Override public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return tile instanceof StorageTankTileEntity ? ((StorageTankTileEntity) tile).getRedstoneOutput() : 0;
    }
    @Override public boolean isPowerSink(ConduitType type) { return true; }
    @Override public boolean isPowerSource(ConduitType type) { return true; }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
            @Nullable EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        NBTTagCompound tag = stack.getTag();
        TileEntity tile = world.getTileEntity(pos);
        if (tag != null && tag.contains("FluidID", 8) && tag.contains("Volume", 99)
                && tile instanceof StorageTankTileEntity) {
            net.minecraft.fluid.Fluid fluid = Fluids.byName(tag.getString("FluidID"));
            if (fluid != null) ((StorageTankTileEntity) tile).getTank()
                    .setFluid(new FluidStack(fluid, tag.getInt("Volume")));
        }
    }

    @Override
    public void harvestBlock(World world, EntityPlayer player, BlockPos pos, IBlockState state,
            @Nullable TileEntity tile, ItemStack tool) {
        if (!world.isRemote && !world.restoringBlockSnapshots) {
            spawnAsEntity(world, pos, tile instanceof StorageTankTileEntity
                    ? createItem((StorageTankTileEntity) tile) : new ItemStack(this));
        }
    }

    public ItemStack createItem(StorageTankTileEntity tile) {
        ItemStack stack = new ItemStack(this);
        if (tile.getTank().getFluidAmount() > 0) {
            NBTTagCompound tag = new NBTTagCompound();
            net.minecraft.util.ResourceLocation id = IRegistry.field_212619_h
                    .getKey(tile.getTank().getFluid().getFluid());
            if (id != null) tag.setString("FluidID", id.toString());
            tag.setInt("Volume", tile.getTank().getFluidAmount());
            stack.setTag(tag);
        }
        return stack;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable IBlockReader world,
            List<ITextComponent> tooltip, ITooltipFlag flag) {
        NBTTagCompound tag = stack.getTag();
        if (tag == null || !tag.contains("FluidID", 8) || !tag.contains("Volume", 99)) {
            tooltip.add(new TextComponentTranslation("tooltip.poweradvantage.empty"));
            return;
        }
        float buckets = tag.getInt("Volume") / (float) FluidTank.BUCKET_VOLUME;
        tooltip.add(new TextComponentString(buckets + " buckets of " + tag.getString("FluidID")));
    }

    /** Retained for callers from the 1.12 implementation. */
    public void displayInformation(ItemStack stack) {}
}
