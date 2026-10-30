package zone.moddev.mc.poweradvantage.machines.fluidmachines;

import java.util.List;

import javax.annotation.Nullable;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PoweredEntity;
import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.FluidTank;
import cyano.poweradvantage.api.simple.BlockSimpleFluidMachine;
import net.minecraft.block.material.Material;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.World;
import zone.moddev.mc.poweradvantage.init.Fluids;

/** Portable tank block. FluidID and Volume remain unchanged for old item stacks. */
public class StorageTankBlock extends BlockSimpleFluidMachine {
    public StorageTankBlock() { super(Material.PISTON, 3F); }

    @Override public PoweredEntity newBlockEntity(IBlockReader world) { return new StorageTankTileEntity(); }
    @Override public boolean hasAnalogOutputSignal(BlockState state) { return true; }
    @Override public int getAnalogOutputSignal(BlockState state, World world, BlockPos pos) {
        TileEntity tile = world.getBlockEntity(pos);
        return tile instanceof StorageTankTileEntity ? ((StorageTankTileEntity) tile).getRedstoneOutput() : 0;
    }
    @Override public boolean isPowerSink(ConduitType type) { return true; }
    @Override public boolean isPowerSource(ConduitType type) { return true; }

    @Override
    public void setPlacedBy(World world, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        CompoundNBT tag = stack.getTag();
        TileEntity tile = world.getBlockEntity(pos);
        if (tag != null && tag.contains("FluidID", 8) && tag.contains("Volume", 99)
                && tile instanceof StorageTankTileEntity) {
            net.minecraft.fluid.Fluid fluid = Fluids.byName(tag.getString("FluidID"));
            if (fluid != null) ((StorageTankTileEntity) tile).getTank()
                    .setFluid(new FluidStack(fluid, tag.getInt("Volume")));
        }
    }

    public ItemStack createItem(StorageTankTileEntity tile) {
        ItemStack stack = new ItemStack(this);
        if (tile.getTank().getFluidAmount() > 0) {
            CompoundNBT tag = new CompoundNBT();
            net.minecraft.util.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.FLUIDS
                    .getKey(tile.getTank().getFluid().getFluid());
            if (id != null) tag.putString("FluidID", id.toString());
            tag.putInt("Volume", tile.getTank().getFluidAmount());
            stack.setTag(tag);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable IBlockReader world,
            List<ITextComponent> tooltip, ITooltipFlag flag) {
        CompoundNBT tag = stack.getTag();
        if (tag == null || !tag.contains("FluidID", 8) || !tag.contains("Volume", 99)) {
            tooltip.add(new TranslationTextComponent("tooltip.poweradvantage.empty"));
            return;
        }
        float buckets = tag.getInt("Volume") / (float) FluidTank.BUCKET_VOLUME;
        tooltip.add(new StringTextComponent(buckets + " buckets of " + tag.getString("FluidID")));
    }

    /** Retained for callers from the 1.12 implementation. */
    public void displayInformation(ItemStack stack) {}
}
