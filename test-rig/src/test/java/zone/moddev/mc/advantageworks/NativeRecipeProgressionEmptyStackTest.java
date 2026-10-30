package zone.moddev.mc.advantageworks;

import java.lang.reflect.Method;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeRecipeProgressionEmptyStackTest {
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }

    @Test public void positiveCountWithNoNativeItemIsEmptyAndHasNoToken() throws Exception {
        ItemStack invalid = new ItemStack((Item) null, 1);
        assertEquals(1, invalid.stackSize);
        assertNull(invalid.getItem());
        assertEquals(Boolean.TRUE, call("empty", invalid));
        assertNull(call("token", invalid));
    }

    @Test public void nullAndZeroCountRemaindersHaveNoToken() throws Exception {
        assertEquals(Boolean.TRUE, call("empty", null));
        assertNull(call("token", null));
        ItemStack exhausted = new ItemStack(Items.DIAMOND, 0);
        assertEquals(Boolean.TRUE, call("empty", exhausted));
        assertNull(call("token", exhausted));
    }

    @Test public void validNativeIngredientIsNotDiscarded() throws Exception {
        ItemStack valid = new ItemStack(Items.DIAMOND, 1);
        assertEquals(Boolean.FALSE, call("empty", valid));
        assertEquals("minecraft:diamond@0", call("token", valid));
    }

    private static Object call(String name, ItemStack stack) throws Exception {
        Method method = NativeRecipeProgressionProbe.class.getDeclaredMethod(name, ItemStack.class);
        method.setAccessible(true);
        return method.invoke(null, (Object) stack);
    }
}
