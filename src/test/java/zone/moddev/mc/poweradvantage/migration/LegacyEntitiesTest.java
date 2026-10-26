package zone.moddev.mc.poweradvantage.migration;

import net.minecraft.util.datafix.DefaultTypeReferences;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.datafix.DataFixesManager;
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
        LegacyWorldDataHook.prepareLegacyChunk(root);
        CompoundNBT actual = root.getCompound("Level").getList("Entities", 10).getCompound(0);
        assertEquals("example:vehicle", actual.getString("id"));
        assertEquals("keep-verbatim", actual.getString("CustomState"));
        ListNBT fixed = actual.getList("Passengers", 10);
        assertEquals(2, fixed.size());
        assertEquals("minecraft:wolf", fixed.getCompound(0).getString("id"));
        assertEquals(wolf.getString("OwnerUUID"), fixed.getCompound(0).getString("OwnerUUID"));
        assertEquals("example:seat", fixed.getCompound(1).getString("id"));
        assertEquals("minecraft:cow", fixed.getCompound(1).getList("Passengers", 10).getCompound(0).getString("id"));
    }

    @Test public void usesVanillaSubtypeFixersRatherThanLowercasingOldIds() {
        CompoundNBT skeleton = entity("Skeleton");
        skeleton.putByte("SkeletonType", (byte) 1);
        CompoundNBT root = chunk(skeleton, 512);
        LegacyWorldDataHook.prepareLegacyChunk(root);
        assertEquals("minecraft:wither_skeleton", root.getCompound("Level").getList("Entities", 10).getCompound(0).getString("id"));
    }

    @Test public void leavesModernEntityAndUnrelatedCustomNbtUnchangedOnRepeatedPreparation() {
        CompoundNBT wolf = entity("minecraft:wolf");
        wolf.putString("OwnerUUID", "a614e658-6270-33c3-9297-7ce80908964f");
        wolf.put("CustomData", entity("Wolf"));
        CompoundNBT root = chunk(wolf, 1343);
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
        LegacyWorldDataHook.prepareLegacyChunk(root);
        CompoundNBT fixed = NBTUtil.update(DataFixesManager.getDataFixer(), DefaultTypeReferences.CHUNK, root, 512, 1343);
        CompoundNBT actual = fixed.getCompound("Level").getList("Entities", 10).getCompound(0);
        assertEquals("minecraft:wolf", actual.getString("id"));
        assertEquals(wolf.getString("OwnerUUID"), actual.getString("OwnerUUID"));
        assertTrue(actual.getBoolean("Sitting"));
        assertEquals(123L, actual.getLong("UUIDMost"));
        assertEquals(456L, actual.getLong("UUIDLeast"));
        assertEquals(customEntity ? 2 : 1, fixed.getCompound("Level").getList("Entities", 10).size());
    }
}
