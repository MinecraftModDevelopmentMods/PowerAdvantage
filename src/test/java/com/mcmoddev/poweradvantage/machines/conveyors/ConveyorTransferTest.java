package com.mcmoddev.poweradvantage.machines.conveyors;

import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.util.EnumFacing;
import org.junit.BeforeClass;
import org.junit.Test;
import com.mcmoddev.poweradvantage.util.InventoryWrapper;
import static org.junit.Assert.*;

public class ConveyorTransferTest {
    @BeforeClass public static void bootstrap() { net.minecraft.init.Bootstrap.register(); }

    @Test public void laterEmptySlotRemainsAnAvailableDestination() {
        InventoryBasic destination = new InventoryBasic("test", false, 2);
        destination.setInventorySlotContents(0, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.COBBLESTONE), 64));
        assertTrue(TileEntityConveyor.canInsertItemInto(new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.DIRT)), InventoryWrapper.wrap(destination), EnumFacing.UP));
    }

    @Test public void fullStackIsNotAnAvailableDestination() {
        InventoryBasic destination = new InventoryBasic("test", false, 1);
        destination.setInventorySlotContents(0, new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.DIRT), 64));
        assertFalse(TileEntityConveyor.canInsertItemInto(new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.DIRT)), InventoryWrapper.wrap(destination), EnumFacing.UP));
    }

    @Test public void transfersPreserveDifferentCustomItemData() {
        InventoryBasic source = new InventoryBasic("source", false, 2),
                target = new InventoryBasic("target", false, 2);
        source.setInventorySlotContents(0, tagged(3, "A")); source.setInventorySlotContents(1, tagged(3, "B")); target.setInventorySlotContents(0, tagged(1, "A"));
        for (int n = 0; n < 6; n++) assertTrue(TileEntityConveyor.transferItem(InventoryWrapper.wrap(source), EnumFacing.EAST,
                InventoryWrapper.wrap(target), EnumFacing.WEST));
        assertTrue(source.isEmpty());
        assertEquals(4, target.getStackInSlot(0).getCount()); assertEquals("A", target.getStackInSlot(0).getTagCompound().getString("Witness"));
        assertEquals(3, target.getStackInSlot(1).getCount()); assertEquals("B", target.getStackInSlot(1).getTagCompound().getString("Witness"));
    }

    private static ItemStack tagged(int count, String value) {
        ItemStack stack = new ItemStack(net.minecraft.item.Item.getItemFromBlock(net.minecraft.init.Blocks.DIRT), count);
        net.minecraft.nbt.NBTTagCompound tag = new net.minecraft.nbt.NBTTagCompound(); tag.setString("Witness", value); stack.setTagCompound(tag);
        return stack;
    }
}
