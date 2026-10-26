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
    @BeforeClass public static void bootstrapNativeRegistries() { Bootstrap.register(); }

    @Test public void materialConstructorsKeepTheLegacyNonSolidDefault() {
        assertFalse(new MaterialMachine().getDefaultState().isSolid());
    }

    @Test public void explicitNativePropertiesRetainBothSolidAndNonSolidChoices() {
        assertTrue(new PropertyMachine(Block.Properties.create(Material.ROCK)).getDefaultState().isSolid());
        assertFalse(new PropertyMachine(Block.Properties.create(Material.ROCK).notSolid())
                .getDefaultState().isSolid());
    }

    @Test public void conveyorsRemainNonSolidWhenUsingExplicitNativeProperties() {
        assertFalse(new BlockConveyor(Material.PISTON, 0.75F).getDefaultState().isSolid());
        assertFalse(new BlockConveyorFilter(Material.PISTON, 0.75F, TileEntityConveyor.class)
                .getDefaultState().isSolid());
    }

    private static final class MaterialMachine extends GUIBlock {
        MaterialMachine() { super(Material.PISTON, 0.75F); }
        @Override public TileEntity createNewTileEntity(IBlockReader world) { return null; }
    }

    @Test public void conduitSubclassesCanChooseNativePropertiesWithoutReplacingNetworkBehavior() {
        PropertyConduit solid = new PropertyConduit(Block.Properties.create(Material.PISTON));
        PropertyConduit nonSolid = new PropertyConduit(Block.Properties.create(Material.PISTON).notSolid());
        assertTrue(solid.getDefaultState().isSolid());
        assertFalse(nonSolid.getDefaultState().isSolid());
        assertEquals(6, nonSolid.getDefaultState().getProperties().size());
        assertEquals("electricity", nonSolid.getTypes()[0].toString());
    }

    private static final class PropertyMachine extends GUIBlock {
        PropertyMachine(Block.Properties properties) { super(properties); }
        @Override public TileEntity createNewTileEntity(IBlockReader world) { return null; }
    }

    private static final class PropertyConduit extends BlockSimplePowerConduit {
        PropertyConduit(Block.Properties properties) { super(properties, 0.125F, new ConduitType("electricity")); }
    }
}
