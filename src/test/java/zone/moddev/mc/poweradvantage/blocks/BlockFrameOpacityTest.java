package zone.moddev.mc.poweradvantage.blocks;

import net.minecraft.block.material.Material;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class BlockFrameOpacityTest {
    @BeforeClass public static void bootstrap() { net.minecraft.util.registry.Bootstrap.register(); }

    @Test public void frameRemainsNonSolidOnTheDedicatedServer() {
        BlockFrame frame = new BlockFrame(Material.PISTON, .75F, 10) {
            // Dedicated-server stripping removes the client-only CUTOUT override.
            @Override public net.minecraft.util.BlockRenderLayer getRenderLayer() {
                return net.minecraft.util.BlockRenderLayer.SOLID;
            }
        };
        assertFalse(frame.isSolid(frame.getDefaultState()));
        assertFalse(frame.getDefaultState().isSolid());
    }
}
