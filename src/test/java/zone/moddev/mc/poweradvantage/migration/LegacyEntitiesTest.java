package zone.moddev.mc.poweradvantage.migration;

import net.minecraft.util.datafix.DefaultTypeReferences;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.util.SharedConstants;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.*;

public class LegacyEntitiesTest {
    @Test public void retainsWolfInDirect110Chunk() {
        assertWolf(false);
    }

    @Test public void retainsWolfBesideCustomEntityInDirect110Chunk() {
        assertWolf(true);
    }

    @Test public void fixesVanillaPassengersInsideUnknownModEntities() {
        CompoundNBT vehicle = entity("example:vehicle");
        vehicle.putString("CustomState", "keep-verbatim");
        CompoundNBT wolf = entity("Wolf");
        wolf.putString("OwnerUUID", "a614e658-6270-33c3-9297-7ce80908964f");
        CompoundNBT nested = entity("example:seat");
        ListNBT nestedPassengers = new ListNBT();
        nestedPassengers.add(entity("Cow"));
        nested.put("Passengers", nestedPassengers);
        ListNBT passengers = new ListNBT();
        passengers.add(wolf);
        passengers.add(nested);
        vehicle.put("Passengers", passengers);
        CompoundNBT root = chunk(vehicle, 512);
        root = convert(root, 512);
        CompoundNBT actual = root.getCompound("Level").getList("Entities", 10).getCompound(0);
        assertEquals("example:vehicle", actual.getString("id"));
        assertEquals("keep-verbatim", actual.getString("CustomState"));
        ListNBT fixed = actual.getList("Passengers", 10);
        assertEquals(2, fixed.size());
        assertEquals("minecraft:wolf", fixed.getCompound(0).getString("id"));
        assertEquals(UUID.fromString(wolf.getString("OwnerUUID")), fixed.getCompound(0).getUUID("Owner"));
        assertEquals("example:seat", fixed.getCompound(1).getString("id"));
        assertEquals("minecraft:cow", fixed.getCompound(1).getList("Passengers", 10).getCompound(0).getString("id"));
    }

    @Test public void usesVanillaSubtypeFixersRatherThanLowercasingOldIds() {
        CompoundNBT skeleton = entity("Skeleton");
        skeleton.putByte("SkeletonType", (byte) 1);
        CompoundNBT root = chunk(skeleton, 512);
        root = convert(root, 512);
        assertEquals("minecraft:wither_skeleton", root.getCompound("Level").getList("Entities", 10).getCompound(0).getString("id"));
    }

    @Test public void leavesModernEntityAndUnrelatedCustomNbtUnchangedOnRepeatedPreparation() {
        CompoundNBT wolf = entity("minecraft:wolf");
        wolf.putString("OwnerUUID", "a614e658-6270-33c3-9297-7ce80908964f");
        wolf.put("CustomData", entity("Wolf"));
        CompoundNBT root = chunk(wolf, SharedConstants.getCurrentVersion().getWorldVersion());
        LegacyWorldDataHook.prepareLegacyChunk(root);
        LegacyWorldDataHook.prepareLegacyChunk(root);
        assertEquals(wolf, root.getCompound("Level").getList("Entities", 10).getCompound(0));
    }

    @Test public void repeatPreparationDoesNotLoseConvertedVanillaState() {
        CompoundNBT wolf = entity("Wolf");
        wolf.putString("OwnerUUID", "a614e658-6270-33c3-9297-7ce80908964f");
        CompoundNBT root = chunk(wolf, 512);
        LegacyWorldDataHook.prepareLegacyChunk(root);
        CompoundNBT once = root.copy();
        LegacyWorldDataHook.prepareLegacyChunk(root);
        assertEquals(once, root);
    }

    private static CompoundNBT entity(String id) {
        CompoundNBT entity = new CompoundNBT();
        entity.putString("id", id);
        return entity;
    }

    private static CompoundNBT chunk(CompoundNBT entity, int version) {
        CompoundNBT root = new CompoundNBT();
        root.putInt("DataVersion", version);
        CompoundNBT level = new CompoundNBT();
        ListNBT entities = new ListNBT();
        entities.add(entity);
        level.put("Entities", entities);
        root.put("Level", level);
        return root;
    }

    private static void assertWolf(boolean customEntity) {
        CompoundNBT root = new CompoundNBT();
        root.putInt("DataVersion", 512);
        CompoundNBT level = new CompoundNBT();
        ListNBT entities = new ListNBT();
        CompoundNBT wolf = new CompoundNBT();
        wolf.putString("id", "Wolf");
        wolf.putString("OwnerUUID", "a614e658-6270-33c3-9297-7ce80908964f");
        wolf.putBoolean("Sitting", true);
        wolf.putLong("UUIDMost", 123L);
        wolf.putLong("UUIDLeast", 456L);
        entities.add(wolf);
        if (customEntity) {
            CompoundNBT turbine = new CompoundNBT();
            turbine.putString("id", "electricadvantage.electricadvantage.Hydroturbine");
            turbine.putIntArray("parentPos", new int[] {164, 81, 8});
            entities.add(turbine);
        }
        level.put("Entities", entities);
        root.put("Level", level);
        CompoundNBT fixed = convert(root, 512);
        CompoundNBT actual = fixed.getCompound("Level").getList("Entities", 10).getCompound(0);
        assertEquals("minecraft:wolf", actual.getString("id"));
        assertEquals(UUID.fromString(wolf.getString("OwnerUUID")), actual.getUUID("Owner"));
        assertTrue(actual.getBoolean("Sitting"));
        assertEquals(new UUID(123L, 456L), actual.getUUID("UUID"));
        assertEquals(customEntity ? 2 : 1, fixed.getCompound("Level").getList("Entities", 10).size());
    }

    @Test public void preservesCustomEntityUuidAndPrivateNbtThroughWholeChunkFix() {
        CompoundNBT custom = entity("example:turbine");
        custom.putLong("UUIDMost", -9014291490481488382L);
        custom.putLong("UUIDLeast", -5032249493117728499L);
        CompoundNBT privateData = entity("Wolf");
        privateData.putLong("UUIDMost", 88L);
        privateData.putLong("UUIDLeast", 99L);
        custom.put("PrivateData", privateData);
        for (int version : new int[] {512, 1343, 1631, 1976, 2230}) {
            CompoundNBT converted = convert(chunk(custom.copy(), version), version);
            CompoundNBT actual = converted.getCompound("Level").getList("Entities", 10).getCompound(0);
            assertEquals(new UUID(-9014291490481488382L, -5032249493117728499L), actual.getUUID("UUID"));
            assertEquals(privateData, actual.getCompound("PrivateData"));
            assertFalse(converted.contains("AdvantageLegacyEntityDataFix"));
        }
    }

    @Test public void preservesOwnerHealthAndUuidInPostFlatteningMixedChunks() {
        for (int version : new int[] {1343, 1631, 1976, 2230}) {
            CompoundNBT wolf = entity("minecraft:wolf");
            UUID uuid = new UUID(-1767247258114444277L, -6740529603542625373L);
            UUID owner = UUID.fromString("a614e658-6270-33c3-9297-7ce80908964f");
            wolf.putLong("UUIDMost", uuid.getMostSignificantBits());
            wolf.putLong("UUIDLeast", uuid.getLeastSignificantBits());
            wolf.putString("OwnerUUID", owner.toString());
            wolf.putFloat("Health", 8F);
            wolf.putBoolean("Sitting", true);
            CompoundNBT root = chunk(wolf, version);
            root.getCompound("Level").getList("Entities", 10).add(entity("example:turbine"));
            CompoundNBT converted = convert(root, version);
            CompoundNBT actual = converted.getCompound("Level").getList("Entities", 10).getCompound(0);
            assertEquals(uuid, actual.getUUID("UUID"));
            assertEquals(owner, actual.getUUID("Owner"));
            assertEquals(8F, actual.getFloat("Health"), 0F);
            assertTrue(actual.getBoolean("Sitting"));
            converted.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
            assertEquals(converted, convert(converted.copy(), SharedConstants.getCurrentVersion().getWorldVersion()));
        }
    }

    private static CompoundNBT convert(CompoundNBT root, int version) {
        LegacyWorldDataHook.prepareLegacyChunk(root);
        LegacyWorldDataHook.prepareLegacyEntities(root);
        CompoundNBT fixed = NBTUtil.update(DataFixesManager.getDataFixer(), DefaultTypeReferences.CHUNK,
                root, version, SharedConstants.getCurrentVersion().getWorldVersion());
        return LegacyWorldDataHook.finalizeLegacyChunk(fixed);
    }

    @Test public void retainsZeroHalfAndExistingNativeUuids() {
        for (UUID uuid : new UUID[] {new UUID(0L, 1L), new UUID(1L, 0L), new UUID(0L, 0L)}) {
            CompoundNBT custom = entity("example:vehicle");
            custom.putLong("UUIDMost", uuid.getMostSignificantBits());
            custom.putLong("UUIDLeast", uuid.getLeastSignificantBits());
            assertEquals(uuid, convert(chunk(custom, 512), 512).getCompound("Level")
                    .getList("Entities", 10).getCompound(0).getUUID("UUID"));
        }
        CompoundNBT custom = entity("example:vehicle");
        custom.putUUID("UUID", new UUID(17L, 19L));
        custom.putLong("UUIDMost", 88L);
        custom.putLong("UUIDLeast", 99L);
        assertEquals(new UUID(17L, 19L), convert(chunk(custom, 2230), 2230).getCompound("Level")
                .getList("Entities", 10).getCompound(0).getUUID("UUID"));
    }

    @Test public void baseMetalsDurabilitySurvivesBothEntryHookOrders() throws Exception {
        java.lang.reflect.Method basePrepare = Class.forName("zone.moddev.mc.basemetals.migration.LegacyWorldDataHook")
                .getMethod("prepareLegacyChunk", CompoundNBT.class);
        for (boolean baseFirst : new boolean[] {false, true}) {
            CompoundNBT cart = entity("minecraft:chest_minecart");
            CompoundNBT pick = entity("basemetals:steel_pickaxe");
            pick.putByte("Count", (byte) 1);
            pick.putShort("Damage", (short) 17);
            pick.putByte("Slot", (byte) 0);
            ListNBT items = new ListNBT();
            items.add(pick);
            cart.put("Items", items);
            CompoundNBT root = chunk(cart, 1343);
            if (baseFirst) basePrepare.invoke(null, root);
            LegacyWorldDataHook.prepareLegacyChunk(root);
            if (!baseFirst) basePrepare.invoke(null, root);
            LegacyWorldDataHook.prepareLegacyEntities(root);
            root = NBTUtil.update(DataFixesManager.getDataFixer(), DefaultTypeReferences.CHUNK,
                    root, 1343, SharedConstants.getCurrentVersion().getWorldVersion());
            root = LegacyWorldDataHook.finalizeLegacyChunk(root);
            CompoundNBT actual = root.getCompound("Level").getList("Entities", 10).getCompound(0)
                    .getList("Items", 10).getCompound(0);
            assertEquals("basemetals:steel_pickaxe", actual.getString("id"));
            assertEquals(1, actual.getInt("Count"));
            assertEquals(17, actual.getCompound("tag").getInt("Damage"));
        }
    }

    @Test(expected = IllegalStateException.class) public void rejectsMalformedEntityListWithoutErasingIt() {
        CompoundNBT root = chunk(entity("example:vehicle"), 512);
        ListNBT wrong = new ListNBT();
        wrong.add(StringNBT.valueOf("keep"));
        root.getCompound("Level").put("Entities", wrong);
        LegacyWorldDataHook.prepareLegacyEntities(root);
    }

    @Test(expected = IllegalStateException.class) public void rejectsMalformedPassengerList() {
        CompoundNBT custom = entity("example:vehicle");
        ListNBT wrong = new ListNBT();
        wrong.add(StringNBT.valueOf("keep"));
        custom.put("Passengers", wrong);
        LegacyWorldDataHook.prepareLegacyEntities(chunk(custom, 512));
    }

    @Test(expected = IllegalStateException.class) public void rejectsMalformedStagedList() {
        CompoundNBT root = chunk(entity("example:vehicle"), 512);
        ListNBT wrong = new ListNBT();
        wrong.add(StringNBT.valueOf("keep"));
        root.put("AdvantageLegacyEntityDataFix", wrong);
        LegacyWorldDataHook.finalizeLegacyChunk(root);
    }

    @Test public void detachesPendingChunkNbtIncludingNestedMapsAndArrays() {
        CompoundNBT pending = chunk(entity("example:vehicle"), SharedConstants.getCurrentVersion().getWorldVersion());
        pending.getCompound("Level").putIntArray("Biomes", new int[] {1, 2, 3});
        CompoundNBT before = pending.copy();
        CompoundNBT read = LegacyWorldDataHook.copyChunkForMigration(pending);
        assertNotSame(pending, read);
        assertNotSame(pending.getCompound("Level"), read.getCompound("Level"));
        read.getCompound("Level").putBoolean("TerrainPopulated", true);
        read.getCompound("Level").getList("Entities", 10).getCompound(0).putInt("test", 1);
        read.getCompound("Level").getIntArray("Biomes")[0] = 19;
        assertEquals(before, pending);
        assertNull(LegacyWorldDataHook.copyChunkForMigration(null));
    }
}
