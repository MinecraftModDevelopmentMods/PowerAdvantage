package cyano.poweradvantage.api.fluid;

import net.minecraft.util.ResourceLocation;

/**
 * Legacy description of a colored fluid.
 *
 * <p>Forge 1.13 no longer exposes the concrete Forge fluid type used by the
 * original API. Native fluids are registered by the implementation; this
 * descriptor remains so source integrations can carry the same metadata
 * without depending on an implementation package.</p>
 */
@Deprecated
public final class ColoredFluid {

    private final String fluidName;
    private final ResourceLocation still;
    private final ResourceLocation flowing;
    private final int color;

    public ColoredFluid(String fluidName, ResourceLocation still, ResourceLocation flowing, int tintARGB) {
        this.fluidName = fluidName;
        this.still = still;
        this.flowing = flowing;
        this.color = tintARGB;
    }

    public String getName() {
        return fluidName;
    }

    public ResourceLocation getStill() {
        return still;
    }

    public ResourceLocation getFlowing() {
        return flowing;
    }

    public int getColor() {
        return color;
    }
}
