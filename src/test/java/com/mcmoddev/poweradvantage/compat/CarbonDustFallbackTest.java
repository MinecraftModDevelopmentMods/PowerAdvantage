package com.mcmoddev.poweradvantage.compat;

import com.mcmoddev.poweradvantage.PowerAdvantage;
import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.OreIngredient;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class CarbonDustFallbackTest {
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    private static final class Registry {
        final Map<String, List<ItemStack>> ores = new HashMap<>();
        List<ItemStack> entries(String name) { return ores.computeIfAbsent(name, key -> new ArrayList<>()); }
        void apply() { BaseMetalsCompat.registerCarbonDustFallback(this::entries, (name, stack) -> entries(name).add(stack)); }
    }

    @Test public void bothProvidersAreCopiedWithoutChangingQuantityMetadataOrNbt() {
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
        coal.setCount(9);
        coal.getTagCompound().setString("Witness", "changed");
        charcoal.setItemDamage(0);
        assertEquals(3, actual.get(0).getCount());
        assertEquals("original", actual.get(0).getTagCompound().getString("Witness"));
        assertEquals(1, actual.get(1).getMetadata());
    }

    @Test public void existingCanonicalEntriesRemainAuthoritative() {
        Registry registry = new Registry();
        ItemStack original = new ItemStack(Items.DIAMOND);
        registry.entries("dustCarbon").add(original);
        registry.entries("dustCoal").add(new ItemStack(Items.COAL));
        registry.apply();
        assertEquals(1, registry.entries("dustCarbon").size());
        assertSame(original, registry.entries("dustCarbon").get(0));
    }

    @Test public void missingAndEmptyProvidersDoNotInventMaterials() {
        Registry registry = new Registry();
        registry.entries("dustCoal").addAll(Arrays.asList(null, ItemStack.EMPTY, new ItemStack(Items.COAL, 0)));
        registry.apply();
        assertTrue(registry.entries("dustCarbon").isEmpty());
    }

    @Test public void repeatedCallsDoNotDuplicateTheFallback() {
        Registry registry = new Registry();
        registry.entries("dustCoal").add(new ItemStack(Items.COAL));
        registry.apply();
        registry.apply();
        assertEquals(1, registry.entries("dustCarbon").size());
    }

    @Test public void steelAndUnrelatedMaterialsAreUntouched() {
        Registry registry = new Registry();
        ItemStack steel = new ItemStack(Items.IRON_INGOT);
        registry.entries("blockSteel").add(steel);
        registry.entries("dustGraphite").add(new ItemStack(Items.DIAMOND));
        registry.apply();
        assertSame(steel, registry.entries("blockSteel").get(0));
        assertEquals(1, registry.entries("blockSteel").size());
        assertTrue(registry.entries("dustCarbon").isEmpty());
    }

    @Test public void nativeIngredientRejectsBeforeTheAliasAndAcceptsBothProvidersAfterIt() {
        String prefix = "advworksCarbon" + UUID.randomUUID().toString().replace("-", "");
        OreDictionary.registerOre(prefix + "dustCoal", new ItemStack(Items.COAL, 1, 0));
        OreDictionary.registerOre(prefix + "dustCharcoal", new ItemStack(Items.COAL, 1, 1));
        Ingredient ingredient = new OreIngredient(prefix + "dustCarbon");
        assertEquals(0, ingredient.getMatchingStacks().length);
        assertFalse(ingredient.apply(new ItemStack(Items.COAL)));
        BaseMetalsCompat.registerCarbonDustFallback(name -> OreDictionary.getOres(prefix + name),
                (name, stack) -> OreDictionary.registerOre(prefix + name, stack));
        assertEquals(2, OreDictionary.getOres(prefix + "dustCarbon").size());
        assertTrue(ingredient.apply(new ItemStack(Items.COAL, 1, 0)));
        assertTrue(ingredient.apply(new ItemStack(Items.COAL, 1, 1)));
        assertFalse(ingredient.apply(ItemStack.EMPTY));
        assertFalse(ingredient.apply(new ItemStack(Items.IRON_INGOT)));
        assertEquals(2, new OreIngredient(prefix + "dustCarbon").getMatchingStacks().length);
    }

    @Test public void recipeRegistryHookHasHighestPriorityAfterItemRegistration() throws Exception {
        Mod.EventBusSubscriber subscriber = BaseMetalsCompat.class.getAnnotation(Mod.EventBusSubscriber.class);
        assertNotNull(subscriber);
        assertEquals(PowerAdvantage.MODID, subscriber.modid());
        Method hook = BaseMetalsCompat.class.getMethod("registerRecipeAliases", RegistryEvent.Register.class);
        assertEquals(EventPriority.HIGHEST, hook.getAnnotation(SubscribeEvent.class).priority());
        assertTrue(hook.getGenericParameterTypes()[0].getTypeName().contains(IRecipe.class.getName()));
    }
}
