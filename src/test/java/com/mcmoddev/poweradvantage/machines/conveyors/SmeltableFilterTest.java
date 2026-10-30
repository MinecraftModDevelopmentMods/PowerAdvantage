package com.mcmoddev.poweradvantage.machines.conveyors;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class SmeltableFilterTest {
    @BeforeClass public static void bootstrap() { net.minecraft.init.Bootstrap.register(); }
    @Test public void furnaceInputsStillMatch() {
        TileEntitySmeltableFilter filter = new TileEntitySmeltableFilter();
        assertTrue(filter.matchesFilter(new ItemStack(Items.POTATO)));
        assertTrue(filter.matchesFilter(new ItemStack(Blocks.IRON_ORE)));
    }
    @Test public void anEmptyRecipeResultDoesNotMatch() {
        assertFalse(new TileEntitySmeltableFilter().matchesFilter(new ItemStack(Blocks.DIRT)));
    }
    @Test public void missingInputDoesNotMatch() {
        TileEntitySmeltableFilter filter = new TileEntitySmeltableFilter();
        assertFalse(filter.matchesFilter(null));
        assertFalse(filter.matchesFilter(ItemStack.EMPTY));
    }
}
