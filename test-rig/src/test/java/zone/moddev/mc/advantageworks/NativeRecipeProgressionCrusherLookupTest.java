package zone.moddev.mc.advantageworks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeRecipeProgressionCrusherLookupTest {
    private static final Object RECIPE = new Object();
    private static final RuntimeException LOOKUP_FAILURE = new IllegalArgumentException("Broken recipe lookup");

    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    @Test public void instanceLookupUsesTheRegistrySingletonAndOriginalInput() throws Throwable {
        ItemStack input = new ItemStack(Items.DIAMOND);
        SingletonRegistry.INSTANCE.seen = null;
        assertSame(RECIPE, lookup(SingletonRegistry.class, input));
        assertSame(input, SingletonRegistry.INSTANCE.seen);
    }

    @Test public void staticLookupDoesNotRequireASingletonAccessor() throws Throwable {
        ItemStack input = new ItemStack(Items.DIAMOND);
        StaticRegistry.seen = null;
        assertSame(RECIPE, lookup(StaticRegistry.class, input));
        assertSame(input, StaticRegistry.seen);
    }

    @Test(expected = NoSuchMethodException.class)
    public void instanceLookupWithoutSingletonIsNotSilentlySkipped() throws Throwable {
        lookup(MissingSingletonRegistry.class, new ItemStack(Items.DIAMOND));
    }

    @Test(expected = IllegalStateException.class)
    public void nullSingletonIsNotAcceptedAsAnEmptyRecipeRegistry() throws Throwable {
        lookup(NullSingletonRegistry.class, new ItemStack(Items.DIAMOND));
    }

    @Test public void lookupFailuresRemainFailuresRatherThanMissingRecipes() throws Throwable {
        try {
            lookup(FailingRegistry.class, new ItemStack(Items.DIAMOND));
            fail("Broken lookup was silently accepted");
        } catch (InvocationTargetException failure) {
            assertSame(LOOKUP_FAILURE, failure.getCause());
        }
    }

    @Test public void genuineMissingRecipeRemainsNull() throws Throwable {
        assertNull(lookup(EmptyRegistry.class, new ItemStack(Items.DIAMOND)));
    }

    private static Object lookup(Class<?> registry, ItemStack input) throws Throwable {
        Method method = NativeRecipeProgressionProbe.class.getDeclaredMethod("crusherRecipe", Class.class, ItemStack.class);
        method.setAccessible(true);
        try { return method.invoke(null, registry, input); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }

    public static final class SingletonRegistry {
        private static final SingletonRegistry INSTANCE = new SingletonRegistry();
        private ItemStack seen;
        public static SingletonRegistry getInstance() { return INSTANCE; }
        public Object getRecipeForInputItem(ItemStack input) { seen = input; return RECIPE; }
    }

    public static final class StaticRegistry {
        private static ItemStack seen;
        public static Object getRecipeForInputItem(ItemStack input) { seen = input; return RECIPE; }
    }

    public static final class MissingSingletonRegistry {
        public Object getRecipeForInputItem(ItemStack input) { return RECIPE; }
    }

    public static final class NullSingletonRegistry {
        public static NullSingletonRegistry getInstance() { return null; }
        public Object getRecipeForInputItem(ItemStack input) { return RECIPE; }
    }

    public static final class FailingRegistry {
        public static Object getRecipeForInputItem(ItemStack input) { throw LOOKUP_FAILURE; }
    }

    public static final class EmptyRegistry {
        public static Object getRecipeForInputItem(ItemStack input) { return null; }
    }
}
