package zone.moddev.mc.poweradvantage.registry;

import java.lang.reflect.Method;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class MachineGuiTitleTest {
    @BeforeClass public static void bootstrap() {
        zone.moddev.mc.poweradvantage.support.PowerNativeTestBootstrap.register();
    }

    @Test public void defaultTitleUsesTheBlockTranslationInsteadOfAnInternalInventoryName() throws Exception {
        assertEquals("block.minecraft.chest", ((TextComponentTranslation) title(
                new TileEntityChest(), Blocks.CHEST)).getKey());
    }

    @Test public void renamedMachinesKeepThePlayersFullName() throws Exception {
        TileEntityChest tile = new TileEntityChest();
        tile.setCustomName(new TextComponentString("John's oil tank"));
        assertEquals("John's oil tank", title(tile, Blocks.CHEST).getString());
    }

    @Test public void unnamedTilesWithoutInventoryNamesStillHaveATranslatableTitle() throws Exception {
        TileEntity tile = new TileEntity(TileEntityType.CHEST) {};
        assertEquals("block.minecraft.chest", ((TextComponentTranslation) title(tile, Blocks.CHEST)).getKey());
    }

    private static ITextComponent title(TileEntity tile, Block block) throws Exception {
        Method method;
        try { method = MachineGUIRegistry.class.getDeclaredMethod("menuTitle", TileEntity.class, Block.class); }
        catch (NoSuchMethodException missing) { throw new AssertionError("Machine titles need a shared, testable equipment-name resolver", missing); }
        method.setAccessible(true);
        return (ITextComponent) method.invoke(null, tile, block);
    }
}
