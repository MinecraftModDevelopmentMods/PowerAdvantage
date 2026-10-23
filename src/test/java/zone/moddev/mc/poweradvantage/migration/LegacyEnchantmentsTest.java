package zone.moddev.mc.poweradvantage.migration;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyEnchantmentsTest {
    @Test public void resolvesOriginalMusketIdsBeforeVanillaCanAssignBindingCurse() {
        Map<Integer, String> saved = new LinkedHashMap<>();
        saved.put(10, "steamadvantage:high_explosive");
        saved.put(11, "steamadvantage:powderless");
        saved.put(12, "steamadvantage:rapid_reload");
        saved.put(13, "steamadvantage:recoil");
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (Integer id : saved.keySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setShort("id", id.shortValue());
            entry.setShort("lvl", (short) 3);
            list.add(entry);
        }
        tag.setTag("ench", list);
        assertTrue(LegacyWorldDataHook.migrateLegacyEnchantments(tag, saved));
        assertFalse(tag.contains("ench", 9));
        for (int i = 0; i < 4; i++) {
            assertEquals(saved.get(10 + i), tag.getList("Enchantments", 10).getCompound(i).getString("id"));
            assertEquals(3, tag.getList("Enchantments", 10).getCompound(i).getShort("lvl"));
        }
        assertFalse(LegacyWorldDataHook.migrateLegacyEnchantments(tag, saved));
    }

    @Test public void honorsWorldSpecificIdsAndPreservesStoredEnchantments() {
        Map<Integer, String> saved = new LinkedHashMap<>();
        saved.put(173, "steamadvantage:rapid_reload");
        NBTTagCompound book = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setShort("id", (short) 173);
        entry.setShort("lvl", (short) 2);
        list.add(entry);
        book.setTag("StoredEnchantments", list);
        assertTrue(LegacyWorldDataHook.migrateLegacyEnchantments(book, saved));
        assertEquals("steamadvantage:rapid_reload", book.getList("StoredEnchantments", 10).getCompound(0).getString("id"));
        assertEquals(2, book.getList("StoredEnchantments", 10).getCompound(0).getShort("lvl"));
    }

    @Test(expected = IllegalStateException.class)
    public void refusesIncompleteOldListInsteadOfSilentlyDroppingAnEnchantment() {
        Map<Integer, String> saved = new LinkedHashMap<>();
        saved.put(10, "steamadvantage:high_explosive");
        NBTTagCompound tag = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setShort("id", (short) 99);
        list.add(entry);
        tag.setTag("ench", list);
        LegacyWorldDataHook.migrateLegacyEnchantments(tag, saved);
    }
}
