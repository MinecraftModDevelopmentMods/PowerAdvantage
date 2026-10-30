package zone.moddev.mc.poweradvantage.machines.conveyors;

import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Direction;
import org.junit.BeforeClass;
import org.junit.Test;
import zone.moddev.mc.poweradvantage.util.InventoryWrapper;
import static org.junit.Assert.*;

public class ConveyorTransferTest {
    @BeforeClass public static void bootstrap() { net.minecraft.util.registry.Bootstrap.bootStrap(); }

    @Test public void laterEmptySlotRemainsAnAvailableDestination() {
        Inventory destination = new Inventory(2);
        destination.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        assertTrue(TileEntityConveyor.canInsertItemInto(new ItemStack(Items.DIRT), InventoryWrapper.wrap(destination), Direction.UP));
    }

    @Test public void fullStackIsNotAnAvailableDestination() {
        Inventory destination = new Inventory(1);
        destination.setItem(0, new ItemStack(Items.DIRT, 64));
        assertFalse(TileEntityConveyor.canInsertItemInto(new ItemStack(Items.DIRT), InventoryWrapper.wrap(destination), Direction.UP));
    }

    @Test public void transfersPreserveDifferentCustomItemData() {
        Inventory source = new Inventory(2), target = new Inventory(2);
        source.setItem(0, tagged(3, "A")); source.setItem(1, tagged(3, "B")); target.setItem(0, tagged(1, "A"));
        for (int n = 0; n < 6; n++) assertTrue(TileEntityConveyor.transferItem(InventoryWrapper.wrap(source), Direction.EAST,
                InventoryWrapper.wrap(target), Direction.WEST));
        assertTrue(source.isEmpty());
        assertEquals(4, target.getItem(0).getCount()); assertEquals("A", target.getItem(0).getTag().getString("Witness"));
        assertEquals(3, target.getItem(1).getCount()); assertEquals("B", target.getItem(1).getTag().getString("Witness"));
    }

    private static ItemStack tagged(int count, String value) {
        ItemStack stack = new ItemStack(Items.DIRT, count);
        stack.getOrCreateTag().putString("Witness", value);
        return stack;
    }
}
