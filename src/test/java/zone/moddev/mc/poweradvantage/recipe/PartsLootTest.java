package zone.moddev.mc.poweradvantage.recipe;

import net.minecraft.util.ResourceLocation;
import net.minecraft.loot.LootTable;
import org.junit.Test;
import static org.junit.Assert.*;

public class PartsLootTest {
    @Test public void addsOnePoolPerLoadedModWithoutDuplicatingOnRepeat() {
        LootTable table = LootTable.lootTable().build();
        ResourceLocation chest = new ResourceLocation("minecraft", "chests/simple_dungeon");
        PartsLoot.addPools(table, chest, true, mod -> true);
        Object original = table.getPool("steamadvantage:legacy_parts");
        assertNotNull(original);
        PartsLoot.addPools(table, chest, true, mod -> true);
        assertSame(original, table.getPool("steamadvantage:legacy_parts"));
        assertNotNull(table.getPool("poweradvantage:legacy_parts"));
        assertNotNull(table.getPool("electricadvantage:legacy_parts"));
    }

    @Test public void keepsEndCityExceptionAndAbsentMods() {
        LootTable table = LootTable.lootTable().build();
        PartsLoot.addPools(table, new ResourceLocation("minecraft", "chests/end_city_treasure"), true,
                mod -> !mod.equals("steamadvantage"));
        assertNull(table.getPool("poweradvantage:legacy_parts"));
        assertNull(table.getPool("steamadvantage:legacy_parts"));
        assertNotNull(table.getPool("electricadvantage:legacy_parts"));
    }

    @Test public void leavesUnrelatedTablesAndDisabledLootUnchanged() {
        for (String id : new String[] {"minecraft:chests/desert_pyramid", "othermod:chests/simple_dungeon"}) {
            LootTable table = LootTable.lootTable().build();
            PartsLoot.addPools(table, new ResourceLocation(id), true, mod -> true);
            assertNull(table.getPool("poweradvantage:legacy_parts"));
        }
        LootTable table = LootTable.lootTable().build();
        PartsLoot.addPools(table, new ResourceLocation("minecraft", "chests/simple_dungeon"), false, mod -> true);
        assertNull(table.getPool("poweradvantage:legacy_parts"));
    }
}
