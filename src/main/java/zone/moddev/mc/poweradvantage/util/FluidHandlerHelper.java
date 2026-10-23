package zone.moddev.mc.poweradvantage.util;

import cyano.poweradvantage.api.fluid.IAdvantageFluidHandler;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

/** Looks up the stable Advantage fluid handler exposed by local machines. */
public final class FluidHandlerHelper {
    private FluidHandlerHelper() {}

    public static IAdvantageFluidHandler getHandler(TileEntity tileEntity, EnumFacing face) {
        return tileEntity instanceof IAdvantageFluidHandler ? (IAdvantageFluidHandler) tileEntity : null;
    }

    public static boolean hasHandler(TileEntity tileEntity, EnumFacing face) {
        return getHandler(tileEntity, face) != null;
    }
}
