package zone.moddev.mc.advantageworks;

import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeRecipeProgressionAdapterTest {
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    @Test public void vanillaShapedInputsAndNarrowWidthUseNativeInterfaces() throws Exception {
        IRecipe recipe = new ShapedRecipes("", 1, 2, NonNullList.from(Ingredient.EMPTY,
                Ingredient.fromItem(Items.DIAMOND), Ingredient.fromItem(Items.COAL)), new ItemStack(Items.STICK));
        assertEquals(1, width(recipe));
        assertEquals(2, inputs(recipe).size());
        assertSame(Items.DIAMOND, inputs(recipe).get(0).get(0).getItem());
        assertSame(Items.COAL, inputs(recipe).get(1).get(0).getItem());
    }

    @Test public void vanillaShapelessInputsUseTheNativeIngredientList() throws Exception {
        IRecipe recipe = new ShapelessRecipes("", new ItemStack(Items.STICK), NonNullList.from(Ingredient.EMPTY,
                Ingredient.fromItem(Items.DIAMOND), Ingredient.fromItem(Items.COAL)));
        assertEquals(2, inputs(recipe).size());
        assertSame(Items.COAL, inputs(recipe).get(1).get(0).getItem());
    }

    @Test public void forgeShapedInputsRetainBlankCellsAndWidth() throws Exception {
        IRecipe recipe = new ShapedOreRecipe(new ResourceLocation("advantageworks", "adapter"), new ItemStack(Items.STICK), "d ", 'd', Items.DIAMOND);
        assertEquals(2, width(recipe));
        assertEquals(2, inputs(recipe).size());
        assertSame(Items.DIAMOND, inputs(recipe).get(0).get(0).getItem());
        assertTrue(inputs(recipe).get(1).isEmpty());
    }

    @Test public void forgeShapelessInputsUseTheNativeIngredientInterface() throws Exception {
        IRecipe recipe = new ShapelessOreRecipe(new ResourceLocation("advantageworks", "adapter"), new ItemStack(Items.STICK), Items.DIAMOND, Items.COAL);
        assertEquals(2, inputs(recipe).size());
        assertSame(Items.DIAMOND, inputs(recipe).get(0).get(0).getItem());
    }

    @Test public void missingOreProvidersRemainEmptyRatherThanInventingIngredients() throws Exception {
        IRecipe recipe = new ShapelessOreRecipe(new ResourceLocation("advantageworks", "adapter"), new ItemStack(Items.STICK), "advworksMissingRecipeAdapterProvider");
        assertEquals(1, inputs(recipe).size());
        assertTrue(inputs(recipe).get(0).isEmpty());
    }

    @Test public void nativeRemainderCallbackReturnsTheActualBucketContainer() throws Exception {
        IRecipe recipe = new ShapelessRecipes("", new ItemStack(Items.STICK), NonNullList.from(Ingredient.EMPTY, Ingredient.fromItem(Items.WATER_BUCKET)));
        InventoryCrafting grid = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return true; }
        }, 3, 3);
        grid.setInventorySlotContents(0, new ItemStack(Items.WATER_BUCKET));
        Method method = NativeRecipeProgressionProbe.class.getDeclaredMethod("remainders", IRecipe.class, InventoryCrafting.class);
        method.setAccessible(true);
        List<ItemStack> remaining = (List<ItemStack>)method.invoke(null, recipe, grid);
        assertEquals(9, remaining.size());
        assertSame(Items.BUCKET, remaining.get(0).getItem());
        assertEquals(1, remaining.get(0).getCount());
        for (int i = 1; i < 9; i++) assertTrue(remaining.get(i).isEmpty());
    }

    private static List<List<ItemStack>> inputs(IRecipe recipe) throws Exception {
        Method method = NativeRecipeProgressionProbe.class.getDeclaredMethod("ingredients", IRecipe.class);
        method.setAccessible(true);
        return (List<List<ItemStack>>)method.invoke(null, recipe);
    }

    private static int width(IRecipe recipe) throws Exception {
        Method method = NativeRecipeProgressionProbe.class.getDeclaredMethod("width", IRecipe.class);
        method.setAccessible(true);
        return ((Number)method.invoke(null, recipe)).intValue();
    }
}
