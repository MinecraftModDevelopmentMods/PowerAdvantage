package zone.moddev.mc.advantageworks;

import java.util.Base64;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeNbtTest {
    @Test public void compressedRoundTripKeepsNativeTypesArraysAndPunctuation() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("name", "stage: 1, \"quoted\" {payload} [x]");
        tag.setByte("byte", (byte) -3);
        tag.setShort("short", (short) 321);
        tag.setInteger("int", 12345678);
        tag.setLong("long", -6814567815397174923L);
        tag.setFloat("health", 980.0F);
        tag.setIntArray("array", new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE});
        tag.setByteArray("bytes", new byte[]{-128, 0, 127});
        NBTTagList pos = new NBTTagList();
        pos.appendTag(new NBTTagDouble(56095.019725011385));
        pos.appendTag(new NBTTagDouble(88.5));
        pos.appendTag(new NBTTagDouble(50016.5));
        tag.setTag("Pos", pos);
        NBTTagCompound nested = new NBTTagCompound();
        nested.setString("tag", "native:exact");
        tag.setTag("nested", nested);
        NBTTagCompound untouched = tag.copy();
        NBTTagCompound decoded = NativeNbt.decode(NativeNbt.encode(tag));
        assertEquals(untouched, tag);
        assertEquals(tag, decoded);
        assertTrue(decoded.hasKey("byte", 1));
        assertTrue(decoded.hasKey("short", 2));
        assertTrue(decoded.hasKey("long", 4));
        assertTrue(decoded.hasKey("health", 5));
        assertArrayEquals(tag.getIntArray("array"), decoded.getIntArray("array"));
        assertEquals(56095.019725011385, decoded.getTagList("Pos", 6).getDoubleAt(0), 0);
    }

    @Test public void malformedBase64IsRejected() {
        assertThrows(IllegalArgumentException.class, () -> NativeNbt.decode("not base64"));
    }

    @Test public void corruptCompressedDataIsRejected() {
        String invalid = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3, 4});
        assertThrows(IllegalArgumentException.class, () -> NativeNbt.decode(invalid));
    }
}
