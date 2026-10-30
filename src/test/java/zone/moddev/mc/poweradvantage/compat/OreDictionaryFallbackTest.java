package zone.moddev.mc.poweradvantage.compat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class OreDictionaryFallbackTest {
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    private static final class Registry {
        final Map<String, List<ItemStack>> ores = new HashMap<>();
        Item steel;
        int itemLookups;
        List<ItemStack> entries(String name) { return ores.computeIfAbsent(name, key -> new ArrayList<>()); }
        void apply() {
            BaseMetalsCompat.registerOreDictionaryFallbacks(this::entries,
                    (name, stack) -> entries(name).add(stack), id -> {
                        assertEquals(new ResourceLocation("basemetals", "steel_block"), id);
                        itemLookups++;
                        return steel;
                    });
        }
    }

    private static ItemBlock steelBlock(String itemName, String blockName) {
        Block block = new Block(Material.IRON);
        block.setRegistryName(new ResourceLocation(blockName));
        ItemBlock item = new ItemBlock(block);
        item.setRegistryName(new ResourceLocation(itemName));
        return item;
    }

    @Test public void carbonAliasesCopyBothActualProvidersAndRetainMetadataQuantityAndNbt() {
        Registry registry = new Registry();
        ItemStack coal = new ItemStack(Items.COAL, 3, 0);
        coal.setTagCompound(new NBTTagCompound());
        coal.getTagCompound().setString("Witness", "original");
        ItemStack charcoal = new ItemStack(Items.COAL, 2, 1);
        registry.entries("dustCoal").add(coal);
        registry.entries("dustCharcoal").add(charcoal);
        registry.apply();
        List<ItemStack> actual = registry.entries("dustCarbon");
        assertEquals(2, actual.size());
        assertNotSame(coal, actual.get(0));
        assertNotSame(charcoal, actual.get(1));
        assertTrue(ItemStack.areItemStacksEqual(coal, actual.get(0)));
        assertTrue(ItemStack.areItemStacksEqual(charcoal, actual.get(1)));
        coal.stackSize = 9;
        coal.getTagCompound().setString("Witness", "changed");
        charcoal.setItemDamage(0);
        assertEquals(3, actual.get(0).stackSize);
        assertEquals("original", actual.get(0).getTagCompound().getString("Witness"));
        assertEquals(1, actual.get(1).getMetadata());
    }

    @Test public void preexistingCarbonEntriesRemainAuthoritative() {
        Registry registry = new Registry();
        ItemStack existing = new ItemStack(Items.DIAMOND);
        registry.entries("dustCarbon").add(existing);
        registry.entries("dustCoal").add(new ItemStack(Items.COAL));
        registry.apply();
        assertEquals(1, registry.entries("dustCarbon").size());
        assertSame(existing, registry.entries("dustCarbon").get(0));
    }

    @Test public void missingProvidersNeverCreateCarbonOrSteel() {
        Registry registry = new Registry();
        registry.apply();
        assertTrue(registry.entries("dustCarbon").isEmpty());
        assertTrue(registry.entries("blockSteel").isEmpty());
    }

    @Test public void invalidDustStacksAreNotRegistered() {
        Registry registry = new Registry();
        registry.entries("dustCoal").addAll(Arrays.asList(null, new ItemStack(Items.COAL, 0), new ItemStack((Item)null, 1)));
        registry.apply();
        assertTrue(registry.entries("dustCarbon").isEmpty());
    }

    @Test public void repeatedInitializationDoesNotDuplicateFallbacks() {
        Registry registry = new Registry();
        registry.entries("dustCoal").add(new ItemStack(Items.COAL));
        registry.steel = steelBlock("basemetals:steel_block", "basemetals:steel_block");
        registry.apply();
        registry.apply();
        assertEquals(1, registry.entries("dustCarbon").size());
        assertEquals(1, registry.entries("blockSteel").size());
        assertEquals(1, registry.itemLookups);
    }

    @Test public void steelFallbackUsesOnlyTheExactRegisteredStorageItemBlock() {
        Registry registry = new Registry();
        registry.steel = steelBlock("basemetals:steel_block", "basemetals:steel_block");
        registry.apply();
        assertEquals(1, registry.entries("blockSteel").size());
        ItemStack actual = registry.entries("blockSteel").get(0);
        assertSame(registry.steel, actual.getItem());
        assertEquals(1, actual.stackSize);
        assertEquals(0, actual.getMetadata());
    }

    @Test public void plainItemsFramesAndOtherBlocksCannotStandInForSteelStorage() {
        Item plain = new Item();
        plain.setRegistryName(new ResourceLocation("basemetals", "steel_block"));
        for (Item candidate : new Item[]{plain,
                steelBlock("poweradvantage:steel_frame", "poweradvantage:steel_frame"),
                steelBlock("basemetals:steel_block", "poweradvantage:steel_frame"),
                steelBlock("basemetals:steel", "basemetals:steel")}) {
            Registry registry = new Registry();
            registry.steel = candidate;
            registry.apply();
            assertTrue(registry.entries("blockSteel").isEmpty());
        }
    }

    @Test public void preexistingSteelEntriesAreNotReplacedOrAugmented() {
        Registry registry = new Registry();
        ItemStack existing = new ItemStack(Items.IRON_INGOT);
        registry.entries("blockSteel").add(existing);
        registry.steel = steelBlock("basemetals:steel_block", "basemetals:steel_block");
        registry.apply();
        assertEquals(0, registry.itemLookups);
        assertEquals(1, registry.entries("blockSteel").size());
        assertSame(existing, registry.entries("blockSteel").get(0));
    }

    @Test public void aliasesExistBeforeNativeRecipeConstructionAndOreSizeQueries() {
        String prefix = "advworksFallback" + UUID.randomUUID().toString().replace("-", "");
        OreDictionary.registerOre(prefix + "dustCoal", new ItemStack(Items.COAL, 1, 0));
        OreDictionary.registerOre(prefix + "dustCharcoal", new ItemStack(Items.COAL, 1, 1));
        InventoryCrafting grid = new InventoryCrafting(new Container() {
            @Override public boolean canInteractWith(EntityPlayer player) { return true; }
        }, 3, 3);
        grid.setInventorySlotContents(0, new ItemStack(Items.COAL));
        assertEquals(0, OreDictionary.getOres(prefix + "dustCarbon").size());
        ShapelessOreRecipe missingAlias = new ShapelessOreRecipe(new ItemStack(Items.DIAMOND), prefix + "dustCarbon");
        assertFalse("The same material is unusable before the fallback", missingAlias.matches(grid, null));
        BaseMetalsCompat.registerOreDictionaryFallbacks(name -> OreDictionary.getOres(prefix + name),
                (name, stack) -> OreDictionary.registerOre(prefix + name, stack), id -> null);
        assertEquals(2, OreDictionary.getOres(prefix + "dustCarbon").size());
        ShapelessOreRecipe recipe = new ShapelessOreRecipe(new ItemStack(Items.DIAMOND), prefix + "dustCarbon");
        for (int damage : new int[]{0, 1}) {
            grid.setInventorySlotContents(0, new ItemStack(Items.COAL, 1, damage));
            assertTrue(recipe.matches(grid, null));
        }
        grid.setInventorySlotContents(0, null);
        assertFalse(recipe.matches(grid, null));
        grid.setInventorySlotContents(0, new ItemStack(Items.IRON_INGOT));
        assertFalse(recipe.matches(grid, null));
    }

    @Test public void lifecycleHookPrecedesFuelAndRecipeInitialization() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("src/main/java/zone/moddev/mc/poweradvantage/PowerAdvantage.java")), StandardCharsets.UTF_8);
        String init = source.substring(source.indexOf("public void init(FMLInitializationEvent event)"), source.indexOf("private void clientInit"));
        String hook = "BaseMetalsCompat.registerOreDictionaryFallbacks();";
        assertTrue(init.indexOf(hook) >= 0);
        assertEquals(init.indexOf(hook), init.lastIndexOf(hook));
        assertTrue(init.indexOf(hook) < init.indexOf("init.Fuels.init();"));
        assertTrue(init.indexOf(hook) < init.indexOf("init.Recipes.init();"));
        assertTrue(source.contains("after:basemetals"));
    }
}
