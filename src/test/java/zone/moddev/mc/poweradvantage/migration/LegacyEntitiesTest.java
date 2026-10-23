package zone.moddev.mc.poweradvantage.migration;

import com.mojang.datafixers.DataFixTypes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
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
        NBTTagCompound vehicle = entity("example:vehicle");
        vehicle.setString("CustomState", "keep-verbatim");
        NBTTagCompound wolf = entity("Wolf");
        wolf.setString("OwnerUUID", "a614e658-6270-33c3-9297-7ce80908964f");
        NBTTagCompound nested = entity("example:seat");
        NBTTagList nestedPassengers = new NBTTagList();
        nestedPassengers.add(entity("Cow"));
        nested.setTag("Passengers", nestedPassengers);
        NBTTagList passengers = new NBTTagList();
        passengers.add(wolf);
        passengers.add(nested);
        vehicle.setTag("Passengers", passengers);
        NBTTagCompound root = chunk(vehicle, 512);
        LegacyWorldDataHook.prepareLegacyChunk(root);
        NBTTagCompound actual = root.getCompound("Level").getList("Entities", 10).getCompound(0);
        assertEquals("example:vehicle", actual.getString("id"));
        assertEquals("keep-verbatim", actual.getString("CustomState"));
        NBTTagList fixed = actual.getList("Passengers", 10);
        assertEquals(2, fixed.size());
        assertEquals("minecraft:wolf", fixed.getCompound(0).getString("id"));
        assertEquals(wolf.getString("OwnerUUID"), fixed.getCompound(0).getString("OwnerUUID"));
        assertEquals("example:seat", fixed.getCompound(1).getString("id"));
        assertEquals("minecraft:cow", fixed.getCompound(1).getList("Passengers", 10).getCompound(0).getString("id"));
    }

    @Test public void usesVanillaSubtypeFixersRatherThanLowercasingOldIds() {
        NBTTagCompound skeleton = entity("Skeleton");
        skeleton.setByte("SkeletonType", (byte) 1);
        NBTTagCompound root = chunk(skeleton, 512);
        LegacyWorldDataHook.prepareLegacyChunk(root);
        assertEquals("minecraft:wither_skeleton", root.getCompound("Level").getList("Entities", 10).getCompound(0).getString("id"));
    }

    @Test public void leavesModernEntityAndUnrelatedCustomNbtUnchangedOnRepeatedPreparation() {
        NBTTagCompound wolf = entity("minecraft:wolf");
        wolf.setString("OwnerUUID", "a614e658-6270-33c3-9297-7ce80908964f");
        wolf.setTag("CustomData", entity("Wolf"));
        NBTTagCompound root = chunk(wolf, 1343);
        LegacyWorldDataHook.prepareLegacyChunk(root);
        LegacyWorldDataHook.prepareLegacyChunk(root);
        assertEquals(wolf, root.getCompound("Level").getList("Entities", 10).getCompound(0));
    }

    @Test public void repeatPreparationDoesNotLoseConvertedVanillaState() {
        NBTTagCompound wolf = entity("Wolf");
        wolf.setString("OwnerUUID", "a614e658-6270-33c3-9297-7ce80908964f");
        NBTTagCompound root = chunk(wolf, 512);
        LegacyWorldDataHook.prepareLegacyChunk(root);
        NBTTagCompound once = root.copy();
        LegacyWorldDataHook.prepareLegacyChunk(root);
        assertEquals(once, root);
    }

    private static NBTTagCompound entity(String id) {
        NBTTagCompound entity = new NBTTagCompound();
        entity.setString("id", id);
        return entity;
    }

    private static NBTTagCompound chunk(NBTTagCompound entity, int version) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInt("DataVersion", version);
        NBTTagCompound level = new NBTTagCompound();
        NBTTagList entities = new NBTTagList();
        entities.add(entity);
        level.setTag("Entities", entities);
        root.setTag("Level", level);
        return root;
    }

    private static void assertWolf(boolean customEntity) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInt("DataVersion", 512);
        NBTTagCompound level = new NBTTagCompound();
        NBTTagList entities = new NBTTagList();
        NBTTagCompound wolf = new NBTTagCompound();
        wolf.setString("id", "Wolf");
        wolf.setString("OwnerUUID", "a614e658-6270-33c3-9297-7ce80908964f");
        wolf.setBoolean("Sitting", true);
        wolf.setLong("UUIDMost", 123L);
        wolf.setLong("UUIDLeast", 456L);
        entities.add(wolf);
        if (customEntity) {
            NBTTagCompound turbine = new NBTTagCompound();
            turbine.setString("id", "electricadvantage.electricadvantage.Hydroturbine");
            turbine.setIntArray("parentPos", new int[] {164, 81, 8});
            entities.add(turbine);
        }
        level.setTag("Entities", entities);
        root.setTag("Level", level);
        LegacyWorldDataHook.prepareLegacyChunk(root);
        NBTTagCompound fixed = NBTUtil.update(DataFixesManager.getDataFixer(), DataFixTypes.CHUNK, root, 512, 1343);
        NBTTagCompound actual = fixed.getCompound("Level").getList("Entities", 10).getCompound(0);
        assertEquals("minecraft:wolf", actual.getString("id"));
        assertEquals(wolf.getString("OwnerUUID"), actual.getString("OwnerUUID"));
        assertTrue(actual.getBoolean("Sitting"));
        assertEquals(123L, actual.getLong("UUIDMost"));
        assertEquals(456L, actual.getLong("UUIDLeast"));
        assertEquals(customEntity ? 2 : 1, fixed.getCompound("Level").getList("Entities", 10).size());
    }
}
