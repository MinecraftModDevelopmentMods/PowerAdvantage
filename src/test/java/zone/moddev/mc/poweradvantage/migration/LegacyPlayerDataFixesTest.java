package zone.moddev.mc.poweradvantage.migration;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.util.datafix.DefaultTypeReferences;
import net.minecraft.util.datafix.TypeReferences;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyPlayerDataFixesTest {
    @BeforeClass public static void bootstrap() { net.minecraft.util.registry.Bootstrap.bootStrap(); }

    @Test public void savedRegistryBookNamesSurviveBothPlayerInventories() {
        for (String inventory : new String[] {"Inventory", "EnderItems"}) {
            for (String name : new String[] {"minecraft:efficiency", "steamadvantage:steam_capacity"}) {
                CompoundNBT book = enchanted("minecraft:enchanted_book", "StoredEnchantments", false);
                assertTrue(LegacyWorldDataHook.migrateLegacyEnchantments(book.getCompound("tag"),
                        Collections.singletonMap(32, name)));
                CompoundNBT source = player(inventory, book), before = source.copy();
                CompoundNBT result = convert(source, 512);
                assertEquals(name, enchantment(item(result, inventory, 0), "StoredEnchantments").getString("id"));
                assertEquals(3, enchantment(item(result, inventory, 0), "StoredEnchantments").getInt("lvl"));
                assertEquals(before, source);
            }
        }
    }

    @Test public void namedOldEnchAndAlreadyModernEnchantmentsSurvive() {
        for (String key : new String[] {"ench", "Enchantments"}) {
            CompoundNBT result = convert(player("Inventory", enchanted("minecraft:diamond_pickaxe", key, true)), 512);
            assertEquals("steamadvantage:steam_capacity", enchantment(item(result, "Inventory", 0), "Enchantments").getString("id"));
            assertEquals(17, item(result, "Inventory", 0).getCompound("tag").getInt("Damage"));
        }
    }

    @Test public void numericEnchantmentsStillUseTheNativeMapping() {
        for (String key : new String[] {"ench", "StoredEnchantments"}) {
            CompoundNBT result = convert(player("Inventory", enchanted("minecraft:enchanted_book", key, false)), 512);
            String outputKey = key.equals("ench") ? "Enchantments" : key;
            assertEquals("minecraft:efficiency", enchantment(item(result, "Inventory", 0), outputKey).getString("id"));
        }
    }

    @Test public void nativeContainerBoundaryFixesNestedItemsExactlyOnce() {
        CompoundNBT chest = stack("minecraft:chest", 0);
        CompoundNBT blockEntity = new CompoundNBT();
        blockEntity.putString("id", "Chest");
        ListNBT contents = new ListNBT();
        contents.add(enchanted("minecraft:diamond_pickaxe", "ench", true));
        CompoundNBT wool = stack("minecraft:wool", 14);
        wool.putByte("Slot", (byte) 1);
        contents.add(wool);
        CompoundNBT book = enchanted("minecraft:enchanted_book", "StoredEnchantments", true);
        book.putByte("Slot", (byte) 2);
        contents.add(book);
        blockEntity.put("Items", contents);
        CompoundNBT tag = new CompoundNBT();
        tag.put("BlockEntityTag", blockEntity);
        chest.put("tag", tag);
        CompoundNBT source = player("Inventory", chest), before = source.copy();
        ListNBT fixed = item(convert(source, 512), "Inventory", 0).getCompound("tag")
                .getCompound("BlockEntityTag").getList("Items", 10);
        assertEquals(17, fixed.getCompound(0).getCompound("tag").getInt("Damage"));
        assertEquals("minecraft:red_wool", fixed.getCompound(1).getString("id"));
        assertEquals("steamadvantage:steam_capacity", enchantment(fixed.getCompound(0), "Enchantments").getString("id"));
        assertEquals("steamadvantage:steam_capacity", enchantment(fixed.getCompound(2), "StoredEnchantments").getString("id"));
        assertEquals(before, source);
    }

    @Test public void foreignItemPayloadIsNotRestoredOverNativeChanges() {
        CompoundNBT foreign = enchanted("other:component", "StoredEnchantments", true);
        foreign.getCompound("tag").putInt("Private", 19);
        CompoundNBT actual = item(convert(player("Inventory", foreign), 512), "Inventory", 0);
        assertFalse("Native damage removal must not be undone by a whole-stack overlay", actual.contains("Damage"));
        assertEquals(19, actual.getCompound("tag").getInt("Private"));
        assertEquals("steamadvantage:steam_capacity", enchantment(actual, "StoredEnchantments").getString("id"));
    }

    @Test public void restorationChangesOnlyTheSavedName() {
        CompoundNBT source = player("Inventory", enchanted("other:component", "StoredEnchantments", true));
        RecordingFixer fixer = new RecordingFixer(root -> {
            CompoundNBT book = item(root, "Inventory", 0);
            book.putString("id", "other:native_result");
            enchantment(book, "StoredEnchantments").putString("id", "minecraft:protection");
            enchantment(book, "StoredEnchantments").putShort("lvl", (short) 5);
            book.getCompound("tag").putInt("NativeField", 42);
        });
        CompoundNBT result = LegacyPlayerDataFixes.update(fixer, DefaultTypeReferences.PLAYER, source, 704);
        CompoundNBT book = item(result, "Inventory", 0);
        assertEquals("other:native_result", book.getString("id"));
        assertEquals(42, book.getCompound("tag").getInt("NativeField"));
        assertEquals(5, enchantment(book, "StoredEnchantments").getInt("lvl"));
        assertEquals("steamadvantage:steam_capacity", enchantment(book, "StoredEnchantments").getString("id"));
    }

    @Test public void sourceIsDetachedEvenWhenFixerMutatesNestedTagsAndArrays() {
        CompoundNBT source = player("Inventory", stack("minecraft:stone", 0));
        source.putIntArray("PrivateArray", new int[] {1, 2});
        CompoundNBT before = source.copy();
        RecordingFixer fixer = new RecordingFixer(root -> {
            root.getIntArray("PrivateArray")[0] = 99;
            item(root, "Inventory", 0).getCompound("tag").putInt("Native", 1);
            root.putString("Native", "changed");
        });
        CompoundNBT result = LegacyPlayerDataFixes.update(fixer, DefaultTypeReferences.PLAYER, source, 512);
        assertEquals(before, source);
        assertNotSame(source, result);
        item(result, "Inventory", 0).putInt("AfterReturn", 1);
        assertEquals(before, source);
    }

    @Test public void usesThePassedFixerAndNativePlayerTypeWithAnExplicit704Boundary() {
        int current = SharedConstants.getCurrentVersion().getWorldVersion();
        for (int source : new int[] {-1, 512, 703, 704, 1343, 2230}) {
            RecordingFixer fixer = new RecordingFixer(root -> {});
            LegacyPlayerDataFixes.update(fixer, DefaultTypeReferences.PLAYER, player("Inventory", stack("minecraft:stone", 0)), source);
            List<String> expected = source < 704 ? Arrays.asList(source + "->704", "704->" + current)
                    : Collections.singletonList(source + "->" + current);
            assertEquals(expected, fixer.calls);
        }
    }

    @Test public void currentOrNewerDataIsCopiedWithoutOldVersionDfu() {
        for (int version : new int[] {SharedConstants.getCurrentVersion().getWorldVersion(), Integer.MAX_VALUE}) {
            CompoundNBT source = player("Inventory", stack("minecraft:diamond_pickaxe", 17));
            RecordingFixer fixer = new RecordingFixer(root -> fail("Current data must not be reinterpreted"));
            CompoundNBT result = LegacyPlayerDataFixes.update(fixer, DefaultTypeReferences.PLAYER, source, version);
            assertEquals(source, result);
            assertNotSame(source, result);
            assertTrue(fixer.calls.isEmpty());
        }
    }

    @Test public void changedCountsFailClosed() {
        rejected(root -> item(root, "Inventory", 0).putByte("Count", (byte) 2));
    }

    @Test public void changedOrMissingSlotsFailClosed() {
        rejected(root -> item(root, "Inventory", 0).putByte("Slot", (byte) 1));
        rejected(root -> item(root, "Inventory", 0).remove("Slot"));
    }

    @Test public void changedInventoryCardinalityFailsClosed() {
        rejected(root -> root.getList("Inventory", 10).remove(0));
        rejected(root -> root.getList("Inventory", 10).add(stack("minecraft:stone", 0)));
    }

    @Test public void addingAnItemToAnEmptyInventoryFailsClosed() {
        for (String inventory : new String[] {"Inventory", "EnderItems"}) {
            CompoundNBT source = new CompoundNBT();
            source.put(inventory, new ListNBT());
            try {
                LegacyPlayerDataFixes.update(new RecordingFixer(root -> root.getList(inventory, 10)
                        .add(stack("minecraft:stone", 0))), DefaultTypeReferences.PLAYER, source, 704);
                fail("An empty inventory must not acquire an unpaired item");
            } catch (IllegalStateException expected) { assertTrue(source.getList(inventory, 10).isEmpty()); }
        }
    }

    @Test public void post704PlayerBooksRetainNames() {
        for (int version : new int[] {704, 1343, 1519, 2230}) {
            CompoundNBT source = player("EnderItems", enchanted("minecraft:enchanted_book", "StoredEnchantments", true));
            CompoundNBT result = convert(source, version);
            assertEquals("steamadvantage:steam_capacity", enchantment(item(result, "EnderItems", 0), "StoredEnchantments").getString("id"));
        }
    }

    @Test public void missingOrReplacedInventoryFailsClosed() {
        rejected(root -> root.remove("Inventory"));
        rejected(root -> root.putString("Inventory", "not an inventory"));
    }

    @Test public void changedNamedEnchantmentCardinalityFailsClosed() {
        rejected(root -> item(root, "Inventory", 0).getCompound("tag").getList("StoredEnchantments", 10).remove(0));
    }

    @Test public void wrongNativeTypeIsRejected() {
        try {
            LegacyPlayerDataFixes.update(DataFixesManager.getDataFixer(), DefaultTypeReferences.CHUNK,
                    new CompoundNBT(), 512);
            fail("The player-only wrapper must reject a different type");
        } catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("PLAYER")); }
    }

    private static void rejected(Consumer<CompoundNBT> mutation) {
        CompoundNBT source = player("Inventory", enchanted("minecraft:enchanted_book", "StoredEnchantments", true));
        CompoundNBT before = source.copy();
        try {
            LegacyPlayerDataFixes.update(new RecordingFixer(mutation), DefaultTypeReferences.PLAYER, source, 704);
            fail("An unpairable native inventory result must fail closed");
        } catch (IllegalStateException expected) { assertEquals(before, source); }
    }

    private static CompoundNBT convert(CompoundNBT source, int version) {
        return LegacyPlayerDataFixes.update(DataFixesManager.getDataFixer(), DefaultTypeReferences.PLAYER, source, version);
    }

    static CompoundNBT player(String inventory, CompoundNBT item) {
        CompoundNBT player = new CompoundNBT();
        player.putInt("DataVersion", 512);
        ListNBT items = new ListNBT();
        items.add(item);
        player.put(inventory, items);
        return player;
    }

    static CompoundNBT stack(String id, int damage) {
        CompoundNBT item = new CompoundNBT();
        item.putString("id", id);
        item.putByte("Count", (byte) 1);
        item.putByte("Slot", (byte) 0);
        item.putShort("Damage", (short) damage);
        item.put("tag", new CompoundNBT());
        return item;
    }

    static CompoundNBT enchanted(String id, String key, boolean named) {
        CompoundNBT item = stack(id, 17);
        CompoundNBT entry = new CompoundNBT();
        if (named) entry.putString("id", "steamadvantage:steam_capacity");
        else entry.putShort("id", (short) 32);
        entry.putShort("lvl", (short) 3);
        ListNBT enchantments = new ListNBT();
        enchantments.add(entry);
        item.getCompound("tag").put(key, enchantments);
        return item;
    }

    static CompoundNBT item(CompoundNBT root, String inventory, int index) { return root.getList(inventory, 10).getCompound(index); }
    static CompoundNBT enchantment(CompoundNBT item, String key) { return item.getCompound("tag").getList(key, 10).getCompound(0); }

    private static final class RecordingFixer implements DataFixer {
        private final Consumer<CompoundNBT> mutation;
        final List<String> calls = new ArrayList<>();
        RecordingFixer(Consumer<CompoundNBT> mutation) { this.mutation = mutation; }
        @Override public <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int from, int to) {
            assertSame(TypeReferences.PLAYER, type);
            calls.add(from + "->" + to);
            mutation.accept((CompoundNBT) input.getValue());
            return input;
        }
        @Override public Schema getSchema(int version) { throw new AssertionError("Not used by the wrapper"); }
    }
}
