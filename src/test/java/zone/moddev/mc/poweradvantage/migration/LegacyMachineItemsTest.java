package zone.moddev.mc.poweradvantage.migration;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.util.datafix.DefaultTypeReferences;
import net.minecraft.util.datafix.TypeReferences;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.BeforeClass;
import org.junit.Test;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityConveyor;
import static org.junit.Assert.*;

public class LegacyMachineItemsTest {
    @BeforeClass public static void bootstrap() {
        net.minecraft.util.registry.Bootstrap.bootStrap();
        AdvantageLegacyMigrations.registerPowerAdvantage();
    }

    @Test public void colouredWoolSurvivesAnActualConveyorInventoryLoad() {
        CompoundNBT tile = convertedTile(stack("minecraft:wool", 14, 7));
        TileEntityConveyor conveyor = new TileEntityConveyor();
        conveyor.load(Blocks.AIR.defaultBlockState(), tile);
        assertSame(Items.RED_WOOL, conveyor.getItem(0).getItem());
        assertEquals(7, conveyor.getItem(0).getCount());
    }

    @Test public void preservesVanillaMetadataVariantsInMachineSlots() {
        for (String[] row : new String[][] {{"minecraft:coal", "1", "minecraft:charcoal"},
                {"minecraft:dye", "4", "minecraft:lapis_lazuli"},
                {"minecraft:planks", "1", "minecraft:spruce_planks"}}) {
            CompoundNBT item = convertedTile(stack(row[0], Integer.parseInt(row[1]), 7))
                    .getList("Items", 10).getCompound(0);
            assertEquals(row[2], item.getString("id"));
            assertEquals(7, item.getInt("Count"));
            assertEquals(0, item.getInt("Slot"));
        }
    }

    @Test public void nativeItemFixersPreserveDamageAndEnchantmentEffects() {
        CompoundNBT pick = stack("minecraft:diamond_pickaxe", 17, 1);
        CompoundNBT tag = new CompoundNBT();
        ListNBT enchantments = new ListNBT();
        CompoundNBT enchantment = new CompoundNBT();
        enchantment.putShort("id", (short) 32);
        enchantment.putShort("lvl", (short) 3);
        enchantments.add(enchantment);
        tag.put("ench", enchantments);
        pick.put("tag", tag);
        CompoundNBT actual = convertedTile(pick).getList("Items", 10).getCompound(0);
        assertEquals(17, actual.getCompound("tag").getInt("Damage"));
        assertEquals("minecraft:efficiency", actual.getCompound("tag")
                .getList("Enchantments", 10).getCompound(0).getString("id"));
        assertEquals(3, actual.getCompound("tag").getList("Enchantments", 10).getCompound(0).getInt("lvl"));
    }

    @Test public void nestedStoredItemsAreConvertedWithoutChangingMachineState() {
        CompoundNBT tile = tile(stack("minecraft:wool", 14, 7));
        CompoundNBT nested = new CompoundNBT();
        nested.put("Reference", stack("minecraft:dye", 4, 1));
        tile.put("Recipe", nested);
        tile.putFloat("Energy", 19.5F);
        tile.putString("CustomName", "keep");
        CompoundNBT actual = convert(chunk(tile), 512).getCompound("Level").getList("TileEntities", 10).getCompound(0);
        assertEquals("minecraft:lapis_lazuli", actual.getCompound("Recipe").getCompound("Reference").getString("id"));
        assertEquals(19.5F, actual.getFloat("Energy"), 0F);
        assertEquals("keep", actual.getString("CustomName"));
    }

    @Test public void foreignPrivatePayloadsAreNotInterpretedAsAdvantageInventories() {
        CompoundNBT foreign = tile(stack("minecraft:wool", 14, 7));
        foreign.putString("id", "other:private_machine");
        CompoundNBT expected = foreign.copy();
        CompoundNBT root = chunk(foreign);
        LegacyWorldDataHook.prepareLegacyChunk(root);
        LegacyWorldDataHook.prepareLegacyEntities(root);
        assertEquals(expected, root.getCompound("Level").getList("TileEntities", 10).getCompound(0));
    }

    @Test public void currentVersionMachineDataIsNotRewritten() {
        CompoundNBT root = chunk(tile(stack("minecraft:red_wool", 0, 7)));
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        CompoundNBT expected = root.copy();
        LegacyWorldDataHook.prepareLegacyEntities(root);
        assertEquals(expected, root);
    }

    @Test public void repeatedPreparationDoesNotRepairAStoredTool() {
        CompoundNBT root = chunk(tile(stack("minecraft:diamond_pickaxe", 17, 1)));
        LegacyWorldDataHook.prepareLegacyChunk(root);
        LegacyWorldDataHook.prepareLegacyEntities(root);
        CompoundNBT once = root.copy();
        LegacyWorldDataHook.prepareLegacyEntities(root);
        assertEquals(once, root);
        assertEquals(17, root.getCompound("Level").getList("TileEntities", 10).getCompound(0)
                .getList("Items", 10).getCompound(0).getCompound("tag").getInt("Damage"));
    }

    @Test public void namedBookEnchantmentsAreNotReinterpretedAsProtection() {
        CompoundNBT book = stack("minecraft:enchanted_book", 0, 1);
        CompoundNBT tag = new CompoundNBT();
        ListNBT list = new ListNBT();
        CompoundNBT named = new CompoundNBT();
        named.putString("id", "steamadvantage:steam_capacity");
        named.putShort("lvl", (short) 3);
        list.add(named);
        tag.put("StoredEnchantments", list);
        book.put("tag", tag);
        CompoundNBT actual = convertedTile(book).getList("Items", 10).getCompound(0);
        assertEquals(list, actual.getCompound("tag").getList("StoredEnchantments", 10));
    }

    @Test public void nativeNestedContainerItemsAreFixedExactlyOnce() {
        CompoundNBT chest = stack("minecraft:chest", 0, 1);
        CompoundNBT blockEntity = new CompoundNBT();
        blockEntity.putString("id", "Chest");
        ListNBT contents = new ListNBT();
        contents.add(stack("minecraft:diamond_pickaxe", 17, 1));
        contents.add(stack("minecraft:wool", 14, 7));
        blockEntity.put("Items", contents);
        CompoundNBT tag = new CompoundNBT();
        tag.put("BlockEntityTag", blockEntity);
        chest.put("tag", tag);
        CompoundNBT actual = convertedTile(chest).getList("Items", 10).getCompound(0);
        ListNBT fixed = actual.getCompound("tag").getCompound("BlockEntityTag").getList("Items", 10);
        assertEquals(17, fixed.getCompound(0).getCompound("tag").getInt("Damage"));
        assertEquals("minecraft:red_wool", fixed.getCompound(1).getString("id"));
        assertEquals(7, fixed.getCompound(1).getInt("Count"));
    }

    @Test public void foreignItemPayloadInsideAnOwnedMachineRemainsVerbatim() {
        CompoundNBT foreign = stack("other:component", 9, 1);
        CompoundNBT tag = new CompoundNBT();
        tag.put("Private", stack("minecraft:wool", 14, 7));
        foreign.put("tag", tag);
        assertEquals(foreign, convertedTile(foreign).getList("Items", 10).getCompound(0));
    }

    @Test public void nestedNamedEnchantmentsSurviveNativeContainerFixes() {
        CompoundNBT book = stack("minecraft:enchanted_book", 0, 1);
        CompoundNBT tag = new CompoundNBT();
        ListNBT enchantments = new ListNBT();
        CompoundNBT enchantment = new CompoundNBT();
        enchantment.putString("id", "steamadvantage:steam_capacity");
        enchantment.putShort("lvl", (short) 3);
        enchantments.add(enchantment);
        tag.put("StoredEnchantments", enchantments);
        book.put("tag", tag);
        CompoundNBT root = chunk(tile(carrier(book)));
        root.putInt("DataVersion", 1343);
        CompoundNBT actual = convert(root, 1343).getCompound("Level").getList("TileEntities", 10)
                .getCompound(0).getList("Items", 10).getCompound(0).getCompound("tag")
                .getCompound("BlockEntityTag").getList("Items", 10).getCompound(0);
        assertEquals(enchantments, actual.getCompound("tag").getList("StoredEnchantments", 10));
    }

    @Test public void nestedForeignItemsRetainTheirPrivateMetadata() {
        CompoundNBT foreign = stack("other:component", 9, 1);
        CompoundNBT actual = convertedTile(carrier(foreign)).getList("Items", 10).getCompound(0)
                .getCompound("tag").getCompound("BlockEntityTag").getList("Items", 10).getCompound(0);
        assertEquals(foreign, actual);
    }

    @Test public void positiveNativeContainerConversionsDoNotEmitErrors() {
        try (ErrorCapture capture = new ErrorCapture()) {
            nativeNestedContainerItemsAreFixedExactlyOnce();
            nestedForeignItemsRetainTheirPrivateMetadata();
            assertTrue("Positive native conversions emitted ERROR+: " + capture.errors, capture.errors.isEmpty());
        }
    }

    @Test public void native704EnvelopeUsesACopiedSingletonAndThePassedFixer() {
        CompoundNBT source = stack("minecraft:chest", 0, 1);
        source.putIntArray("PrivateArray", new int[] {1, 2});
        CompoundNBT before = source.copy();
        CompoundNBT result = LegacyMachineItemDataFixes.upgradeTo704(source, 512, new EnvelopeFixer(root -> {
            assertEquals(1, root.getAllKeys().size());
            assertEquals(1, root.getList("Inventory", 10).size());
            CompoundNBT item = root.getList("Inventory", 10).getCompound(0);
            assertEquals(before, item);
            item.getIntArray("PrivateArray")[0] = 99;
            item.putString("NativeField", "retained");
        }));
        assertEquals(before, source);
        assertEquals("retained", result.getString("NativeField"));
        assertEquals(99, result.getIntArray("PrivateArray")[0]);
        result.putInt("AfterReturn", 1);
        assertEquals(before, source);
    }

    @Test public void native704EnvelopeRejectsMissingOrWrongInventoryTypes() {
        rejectedEnvelope(root -> root.remove("Inventory"));
        rejectedEnvelope(root -> root.putString("Inventory", "not a list"));
        rejectedEnvelope(root -> {
            ListNBT items = new ListNBT();
            items.add(StringNBT.valueOf("not a stack"));
            root.put("Inventory", items);
        });
    }

    @Test public void native704EnvelopeRejectsNonSingletonInventories() {
        rejectedEnvelope(root -> root.getList("Inventory", 10).remove(0));
        rejectedEnvelope(root -> root.getList("Inventory", 10).add(stack("minecraft:stone", 0, 1)));
    }

    @Test public void native704EnvelopeRejectsCountAndSlotChanges() {
        rejectedEnvelope(root -> envelopeItem(root).remove("Count"));
        rejectedEnvelope(root -> envelopeItem(root).putByte("Count", (byte) 2));
        rejectedEnvelope(root -> envelopeItem(root).putInt("Count", 1));
        rejectedEnvelope(root -> envelopeItem(root).putByte("Slot", (byte) 1));
        rejectedEnvelope(root -> envelopeItem(root).remove("Slot"));
    }

    @Test public void native704EnvelopeRejectsMalformedItemIds() {
        rejectedEnvelope(root -> envelopeItem(root).remove("id"));
        rejectedEnvelope(root -> envelopeItem(root).putInt("id", 54));
        rejectedEnvelope(root -> envelopeItem(root).putString("id", ""));
        rejectedEnvelope(root -> envelopeItem(root).putString("id", "minecraft:"));
        rejectedEnvelope(root -> envelopeItem(root).putString("id", "not an item"));
    }

    @Test public void native704EnvelopeDoesNotAddASlotToAnUnslottedStack() {
        CompoundNBT source = stack("minecraft:chest", 0, 1);
        source.remove("Slot");
        CompoundNBT before = source.copy();
        try {
            LegacyMachineItemDataFixes.upgradeTo704(source, 512,
                    new EnvelopeFixer(root -> envelopeItem(root).putByte("Slot", (byte) 0)));
            fail("A new slot cannot be silently paired with an unslotted source");
        } catch (IllegalStateException expected) { assertEquals(before, source); }
    }

    @Test public void native704EnvelopeRejectsANonCompoundPlayerResult() {
        CompoundNBT source = stack("minecraft:chest", 0, 1), before = source.copy();
        DataFixer fixer = new EnvelopeFixer(root -> {}) {
            @Override public <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int from, int to) {
                return new Dynamic<>(input.getOps(), input.getOps().createList(Stream.empty()));
            }
        };
        try {
            LegacyMachineItemDataFixes.upgradeTo704(source, 512, fixer);
            fail("The native envelope must remain a compound");
        } catch (IllegalStateException expected) { assertEquals(before, source); }
    }

    private static CompoundNBT envelopeItem(CompoundNBT root) { return root.getList("Inventory", 10).getCompound(0); }

    private static void rejectedEnvelope(Consumer<CompoundNBT> mutation) {
        CompoundNBT source = stack("minecraft:chest", 0, 1), before = source.copy();
        try {
            LegacyMachineItemDataFixes.upgradeTo704(source, 512, new EnvelopeFixer(mutation));
            fail("An unpairable native envelope must fail closed");
        } catch (IllegalStateException expected) { assertEquals(before, source); }
    }

    private static class EnvelopeFixer implements DataFixer {
        private final Consumer<CompoundNBT> mutation;
        EnvelopeFixer(Consumer<CompoundNBT> mutation) { this.mutation = mutation; }
        @Override public <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int from, int to) {
            assertSame(TypeReferences.PLAYER, type);
            assertEquals(512, from);
            assertEquals(704, to);
            mutation.accept((CompoundNBT) input.getValue());
            return input;
        }
        @Override public Schema getSchema(int version) { throw new AssertionError("Not used by the envelope"); }
    }

    private static final class ErrorCapture extends AbstractAppender implements AutoCloseable {
        private final Logger logger = (Logger) LogManager.getRootLogger();
        final List<String> errors = new ArrayList<>();
        ErrorCapture() {
            super("LegacyMachineItemsPositiveErrors", null, null, true);
            start();
            logger.addAppender(this);
        }
        @Override public void append(LogEvent event) {
            if (event.getLevel().isMoreSpecificThan(Level.ERROR)) {
                errors.add(event.getLoggerName() + ": " + event.getMessage().getFormattedMessage());
            }
        }
        @Override public void close() { logger.removeAppender(this); stop(); }
    }

    private static CompoundNBT carrier(CompoundNBT item) {
        CompoundNBT chest = stack("minecraft:chest", 0, 1);
        CompoundNBT blockEntity = new CompoundNBT();
        blockEntity.putString("id", "minecraft:chest");
        ListNBT contents = new ListNBT();
        contents.add(item);
        blockEntity.put("Items", contents);
        CompoundNBT tag = new CompoundNBT();
        tag.put("BlockEntityTag", blockEntity);
        chest.put("tag", tag);
        return chest;
    }

    private static CompoundNBT convertedTile(CompoundNBT item) {
        return convert(chunk(tile(item)), 512).getCompound("Level").getList("TileEntities", 10).getCompound(0);
    }

    private static CompoundNBT convert(CompoundNBT root, int version) {
        LegacyWorldDataHook.prepareLegacyChunk(root);
        LegacyWorldDataHook.prepareLegacyEntities(root);
        CompoundNBT fixed = NBTUtil.update(DataFixesManager.getDataFixer(), DefaultTypeReferences.CHUNK,
                root, version, SharedConstants.getCurrentVersion().getWorldVersion());
        return LegacyWorldDataHook.finalizeLegacyChunk(fixed);
    }

    private static CompoundNBT stack(String id, int damage, int count) {
        CompoundNBT item = new CompoundNBT();
        item.putString("id", id);
        item.putByte("Count", (byte) count);
        item.putByte("Slot", (byte) 0);
        item.putShort("Damage", (short) damage);
        return item;
    }

    private static CompoundNBT tile(CompoundNBT item) {
        CompoundNBT tile = new CompoundNBT();
        tile.putString("id", "poweradvantage:item_conveyor");
        tile.putInt("x", 0); tile.putInt("y", 64); tile.putInt("z", 0);
        ListNBT items = new ListNBT();
        items.add(item);
        tile.put("Items", items);
        return tile;
    }

    private static CompoundNBT chunk(CompoundNBT tile) {
        CompoundNBT root = new CompoundNBT();
        root.putInt("DataVersion", 512);
        CompoundNBT level = new CompoundNBT();
        ListNBT tiles = new ListNBT();
        tiles.add(tile);
        level.put("TileEntities", tiles);
        root.put("Level", level);
        return root;
    }
}
