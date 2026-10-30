package zone.moddev.mc.poweradvantage.registry;

import java.lang.reflect.Method;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class MachineGuiTitleTest {
    @BeforeClass public static void bootstrap() {
        net.minecraft.util.registry.Bootstrap.register();
    }

    @Test public void defaultTitleUsesTheBlockTranslationInsteadOfAnInternalInventoryName() throws Exception {
        assertEquals("block.minecraft.chest", ((TranslationTextComponent) title(
                new ChestTileEntity(), Blocks.CHEST)).getKey());
    }

    @Test public void renamedMachinesKeepThePlayersFullName() throws Exception {
        ChestTileEntity tile = new ChestTileEntity();
        tile.setCustomName(new StringTextComponent("John's oil tank"));
        assertEquals("John's oil tank", title(tile, Blocks.CHEST).getString());
    }

    @Test public void unnamedTilesWithoutInventoryNamesStillHaveATranslatableTitle() throws Exception {
        TileEntity tile = new TileEntity(TileEntityType.CHEST) {};
        assertEquals("block.minecraft.chest", ((TranslationTextComponent) title(tile, Blocks.CHEST)).getKey());
    }

    private static ITextComponent title(TileEntity tile, Block block) throws Exception {
        Method method;
        try { method = MachineGUIRegistry.class.getDeclaredMethod("menuTitle", TileEntity.class, Block.class); }
        catch (NoSuchMethodException missing) { throw new AssertionError("Machine titles need a shared, testable equipment-name resolver", missing); }
        method.setAccessible(true);
        return (ITextComponent) method.invoke(null, tile, block);
    }
}
