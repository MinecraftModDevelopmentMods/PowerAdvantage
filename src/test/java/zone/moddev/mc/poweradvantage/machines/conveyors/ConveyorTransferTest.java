package zone.moddev.mc.poweradvantage.machines.conveyors;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.EnumFacing;
import org.junit.BeforeClass;
import org.junit.Test;
import zone.moddev.mc.poweradvantage.util.InventoryWrapper;
import static org.junit.Assert.*;

public class ConveyorTransferTest {
    @BeforeClass public static void bootstrap() { zone.moddev.mc.poweradvantage.support.PowerNativeTestBootstrap.register(); }

    @Test public void laterEmptySlotRemainsAnAvailableDestination() {
        InventoryBasic destination = new InventoryBasic(new net.minecraft.util.text.TextComponentString("test"), 2);
        destination.setInventorySlotContents(0, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.COBBLESTONE), 64));
        assertTrue(TileEntityConveyor.canInsertItemInto(new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.DIRT)), InventoryWrapper.wrap(destination), EnumFacing.UP));
    }

    @Test public void fullStackIsNotAnAvailableDestination() {
        InventoryBasic destination = new InventoryBasic(new net.minecraft.util.text.TextComponentString("test"), 1);
        destination.setInventorySlotContents(0, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.DIRT), 64));
        assertFalse(TileEntityConveyor.canInsertItemInto(new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.DIRT)), InventoryWrapper.wrap(destination), EnumFacing.UP));
    }

    @Test public void transfersPreserveDifferentCustomItemData() {
        InventoryBasic source = new InventoryBasic(new net.minecraft.util.text.TextComponentString("source"), 2),
                target = new InventoryBasic(new net.minecraft.util.text.TextComponentString("target"), 2);
        source.setInventorySlotContents(0, tagged(3, "A")); source.setInventorySlotContents(1, tagged(3, "B")); target.setInventorySlotContents(0, tagged(1, "A"));
        for (int n = 0; n < 6; n++) assertTrue(TileEntityConveyor.transferItem(InventoryWrapper.wrap(source), EnumFacing.EAST,
                InventoryWrapper.wrap(target), EnumFacing.WEST));
        assertTrue(source.isEmpty());
        assertEquals(4, target.getStackInSlot(0).getCount()); assertEquals("A", target.getStackInSlot(0).getTag().getString("Witness"));
        assertEquals(3, target.getStackInSlot(1).getCount()); assertEquals("B", target.getStackInSlot(1).getTag().getString("Witness"));
    }

    private static ItemStack tagged(int count, String value) {
        ItemStack stack = new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.DIRT), count);
        stack.getOrCreateTag().setString("Witness", value);
        return stack;
    }
}
