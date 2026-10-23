package zone.moddev.mc.poweradvantage.util;

import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.FluidTank;
import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.fluid.Fluid;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.registry.IRegistry;

/** Native bucket interaction for Forge 25's transitional fluid implementation. */
public final class FluidContainerHelper {
    private FluidContainerHelper() {}

    public static boolean interact(EntityPlayer player, EnumHand hand,
            IAdvantageFluidHandler handler, EnumFacing face) {
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) return false;

        if (held.getItem() == Items.BUCKET) {
            FluidStack available = handler.drain(face, FluidTank.BUCKET_VOLUME, false);
            if (available == null || available.amount < FluidTank.BUCKET_VOLUME) return false;
            Item filledBucket = available.getFluid().getFilledBucket();
            if (filledBucket == null || filledBucket == Items.AIR) return false;
            handler.drain(face, FluidTank.BUCKET_VOLUME, true);
            replaceOne(player, hand, held, new ItemStack(filledBucket));
            return true;
        }

        Fluid contained = fluidForBucket(held.getItem());
        if (contained == null) return false;
        FluidStack bucket = new FluidStack(contained, FluidTank.BUCKET_VOLUME);
        if (handler.fill(face, bucket, false) < FluidTank.BUCKET_VOLUME) return false;
        handler.fill(face, bucket, true);
        replaceOne(player, hand, held, new ItemStack(Items.BUCKET));
        return true;
    }

    public static Fluid fluidForBucket(Item bucketItem) {
        for (Fluid fluid : IRegistry.field_212619_h) {
            if (fluid.getFilledBucket() == bucketItem && bucketItem != Items.BUCKET) return fluid;
        }
        return null;
    }

    private static void replaceOne(EntityPlayer player, EnumHand hand, ItemStack held, ItemStack replacement) {
        if (player.abilities.isCreativeMode) return;
        held.shrink(1);
        if (held.isEmpty()) player.setHeldItem(hand, replacement);
        else if (!player.inventory.addItemStackToInventory(replacement)) player.dropItem(replacement, false);
    }
}
