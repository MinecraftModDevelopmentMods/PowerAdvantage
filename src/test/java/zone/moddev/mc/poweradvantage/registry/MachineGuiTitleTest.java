package zone.moddev.mc.poweradvantage.registry;

import net.minecraft.block.Blocks;
import net.minecraft.tileentity.ChestTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class MachineGuiTitleTest {
    @BeforeClass public static void bootstrap() {
        net.minecraft.util.registry.Bootstrap.bootStrap();
    }

    @Test public void defaultTitleUsesTheBlockTranslationInsteadOfAnInternalInventoryName() {
        assertEquals("block.minecraft.chest", ((TranslationTextComponent) MachineGUIRegistry.menuTitle(
                new ChestTileEntity(), Blocks.CHEST)).getKey());
    }

    @Test public void renamedMachinesKeepThePlayersFullName() {
        ChestTileEntity tile = new ChestTileEntity();
        tile.setCustomName(new StringTextComponent("John's oil tank"));
        assertEquals("John's oil tank", MachineGUIRegistry.menuTitle(tile, Blocks.CHEST).getString());
    }

    @Test public void unnamedTilesWithoutInventoryNamesStillHaveATranslatableTitle() {
        TileEntity tile = new TileEntity(TileEntityType.CHEST) {};
        assertEquals("block.minecraft.chest", ((TranslationTextComponent) MachineGUIRegistry.menuTitle(tile, Blocks.CHEST)).getKey());
    }
}
