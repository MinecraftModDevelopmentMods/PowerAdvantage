package zone.moddev.mc.poweradvantage.resources;

import cyano.poweradvantage.api.GUIBlock;
import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.simple.BlockSimplePowerConduit;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.IBlockReader;
import org.junit.BeforeClass;
import org.junit.Test;
import zone.moddev.mc.poweradvantage.machines.conveyors.BlockConveyor;
import zone.moddev.mc.poweradvantage.machines.conveyors.BlockConveyorFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityConveyor;
import static org.junit.Assert.*;

public class NativeMachinePropertyTest {
    @BeforeClass public static void bootstrapNativeRegistries() { Bootstrap.bootStrap(); }

    @Test public void materialConstructorsKeepTheLegacyNonSolidDefault() {
        assertFalse(new MaterialMachine().defaultBlockState().canOcclude());
    }

    @Test public void explicitNativePropertiesRetainBothSolidAndNonSolidChoices() {
        assertTrue(new PropertyMachine(Block.Properties.of(Material.STONE)).defaultBlockState().canOcclude());
        assertFalse(new PropertyMachine(Block.Properties.of(Material.STONE).noOcclusion())
                .defaultBlockState().canOcclude());
    }

    @Test public void conveyorsRemainNonSolidWhenUsingExplicitNativeProperties() {
        assertFalse(new BlockConveyor(Material.PISTON, 0.75F).defaultBlockState().canOcclude());
        assertFalse(new BlockConveyorFilter(Material.PISTON, 0.75F, TileEntityConveyor.class)
                .defaultBlockState().canOcclude());
    }

    private static final class MaterialMachine extends GUIBlock {
        MaterialMachine() { super(Material.PISTON, 0.75F); }
        @Override public TileEntity newBlockEntity(IBlockReader world) { return null; }
    }

    @Test public void conduitSubclassesCanChooseNativePropertiesWithoutReplacingNetworkBehavior() {
        PropertyConduit solid = new PropertyConduit(Block.Properties.of(Material.PISTON));
        PropertyConduit nonSolid = new PropertyConduit(Block.Properties.of(Material.PISTON).noOcclusion());
        assertTrue(solid.defaultBlockState().canOcclude());
        assertFalse(nonSolid.defaultBlockState().canOcclude());
        assertEquals(6, nonSolid.defaultBlockState().getProperties().size());
        assertEquals("electricity", nonSolid.getTypes()[0].toString());
    }

    private static final class PropertyMachine extends GUIBlock {
        PropertyMachine(Block.Properties properties) { super(properties); }
        @Override public TileEntity newBlockEntity(IBlockReader world) { return null; }
    }

    private static final class PropertyConduit extends BlockSimplePowerConduit {
        PropertyConduit(Block.Properties properties) { super(properties, 0.125F, new ConduitType("electricity")); }
    }
}
