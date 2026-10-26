package zone.moddev.mc.poweradvantage.util;

import cyano.poweradvantage.api.fluid.FluidStack;
import cyano.poweradvantage.api.fluid.FluidTank;
import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.item.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Direction;
import net.minecraft.util.Hand;
import net.minecraft.util.registry.Registry;

/** Uses Forge's container handling for buckets and capability-based fluid containers. */
public final class FluidContainerHelper {
    private FluidContainerHelper() {}

    public static boolean interact(PlayerEntity player, Hand hand,
            IAdvantageFluidHandler handler, Direction face) {
        if (player.world.isRemote) return false;
        return net.minecraftforge.fluids.FluidUtil.interactWithFluidHandler(player, hand,
                new ForgeFluidAdapter(handler, face, () -> {
                    if (handler instanceof net.minecraft.tileentity.TileEntity)
                        ((net.minecraft.tileentity.TileEntity) handler).markDirty();
                }));
    }

    public static Fluid fluidForBucket(Item bucketItem) {
        for (Fluid fluid : net.minecraftforge.registries.ForgeRegistries.FLUIDS) {
            if (fluid.getFilledBucket() == bucketItem && bucketItem != Items.BUCKET) return fluid;
        }
        return null;
    }

}
