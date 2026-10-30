package zone.moddev.mc.poweradvantage.migration;

import static org.junit.Assert.*;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.nbt.StringNBT;
import net.minecraft.profiler.EmptyProfiler;
import net.minecraft.profiler.IProfiler;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.util.datafix.DefaultTypeReferences;
import net.minecraft.util.datafix.TypeReferences;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import org.junit.BeforeClass;
import org.junit.Test;

public class LegacyEntityAttributeAliasesTest {
    private static final UUID MODIFIER = new UUID(17L, 19L);

    @BeforeClass public static void bootstrap() { Bootstrap.bootStrap(); }

    @Test public void changesOnlyExactNameAndPreservesUnknownFieldsAndUuidModifiers() {
        CompoundNBT entity = entity("example:vehicle", true);
        CompoundNBT before = entity.copy();
        CompoundNBT root = chunk(entity);
        LegacyEntityDataFixes.prepare(root);
        LegacyEntityDataFixes.finish(root);
        CompoundNBT actual = root.getCompound("Level").getList("Entities", 10).getCompound(0);
        CompoundNBT expected = before.copy();
        expected.getList("Attributes", 10).getCompound(0).putString("Name", "forge:swim_speed");
        assertEquals(expected, actual);
        assertEquals(before, entity);
    }

    @Test public void aliasesVanillaEntityBeforeNativeDfuAndPreservesModifierIdentity() {
        CompoundNBT root = chunk(entity("minecraft:wolf", true));
        LegacyEntityDataFixes.prepare(root);
        LegacyEntityDataFixes.finish(root);
        CompoundNBT attribute = root.getCompound("Level").getList("Entities", 10).getCompound(0)
                .getList("Attributes", 10).getCompound(0);
        assertEquals("forge:swim_speed", attribute.getString("Name"));
        assertEquals(2D, attribute.getDouble("Base"), 0D);
        assertEquals("keep-attribute-field", attribute.getString("PrivateNote"));
        CompoundNBT modifier = attribute.getList("Modifiers", 10).getCompound(0);
        assertEquals(MODIFIER, modifier.getUUID("UUID"));
        assertEquals("keep-modifier-field", modifier.getString("PrivateNote"));
    }

    @Test public void aliasesOnlyEntityPassengersNotInventoryOrPrivateCompounds() {
        CompoundNBT parent = entity("example:vehicle", false);
        CompoundNBT child = entity("example:seat", true);
        ListNBT passengers = new ListNBT(); passengers.add(child);
        parent.put("Passengers", passengers);
        CompoundNBT foreign = entity("example:private", true);
        parent.put("PrivateData", foreign.copy());
        parent.put("ForgeData", foreign.copy());
        ListNBT items = new ListNBT(); items.add(foreign.copy()); parent.put("Items", items);
        CompoundNBT root = chunk(parent);
        LegacyEntityDataFixes.prepare(root); LegacyEntityDataFixes.finish(root);
        CompoundNBT actual = root.getCompound("Level").getList("Entities", 10).getCompound(0);
        assertEquals("forge:swim_speed", actual.getList("Passengers", 10).getCompound(0)
                .getList("Attributes", 10).getCompound(0).getString("Name"));
        assertEquals(foreign, actual.getCompound("PrivateData"));
        assertEquals(foreign, actual.getCompound("ForgeData"));
        assertEquals(items, actual.getList("Items", 10));
    }

    @Test public void rejectsLegacyNativeCollisionWithoutChangingChunk() {
        assertCollision("forge.swimSpeed", "forge:swim_speed");
    }

    @Test public void rejectsDuplicateLegacyOrNativeAliasWithoutChangingChunk() {
        assertCollision("forge.swimSpeed", "forge.swimSpeed");
        assertCollision("forge:swim_speed", "forge:swim_speed");
    }

    @Test public void exactAliasDoesNotRenameOtherForgeOrUnknownAttributes() {
        CompoundNBT entity = entity("example:vehicle", false);
        ListNBT list = new ListNBT();
        for (String name : new String[] {"forge.SwimSpeed", "example:swimSpeed", "forge:other", "forge.swimSpeed.extra"}) {
            CompoundNBT attribute = attribute(false); attribute.putString("Name", name); list.add(attribute);
        }
        entity.put("Attributes", list);
        CompoundNBT root = chunk(entity), before = root.copy();
        LegacyEntityDataFixes.prepare(root); LegacyEntityDataFixes.finish(root);
        assertEquals(before, root);
    }

    @Test public void repeatedAliasPreparationIsIdempotent() {
        CompoundNBT root = chunk(entity("example:vehicle", true));
        LegacyEntityDataFixes.prepare(root);
        CompoundNBT staged = root.copy();
        LegacyEntityDataFixes.prepare(root); assertEquals(staged, root);
        LegacyEntityDataFixes.finish(root);
        CompoundNBT once = root.copy();
        LegacyEntityDataFixes.prepare(root); LegacyEntityDataFixes.finish(root);
        assertEquals(once, root);
    }

    @Test public void modernChunksAndPlayersRemainUnchanged() {
        CompoundNBT root = chunk(entity("minecraft:wolf", true));
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
        CompoundNBT before = root.copy();
        LegacyEntityDataFixes.prepare(root); assertEquals(before, root);
        CompoundNBT player = player(entity("minecraft:wolf", true));
        CompoundNBT result = LegacyPlayerDataFixes.update(DataFixesManager.getDataFixer(), DefaultTypeReferences.PLAYER,
                player, SharedConstants.getCurrentVersion().getWorldVersion());
        assertEquals(player, result);
    }

    @Test public void playerRootVehicleAndPassengersArePreparedBeforeNativePlayerDfu() {
        CompoundNBT vehicle = entity("example:vehicle", true);
        ListNBT passengers = new ListNBT(); passengers.add(entity("example:seat", true)); vehicle.put("Passengers", passengers);
        CompoundNBT source = player(vehicle), expected = source.copy();
        CompoundNBT result = LegacyPlayerDataFixes.update(new RecordingFixer(value -> {
            CompoundNBT rootVehicle = value.getCompound("RootVehicle").getCompound("Entity");
            assertEquals("forge:swim_speed", rootVehicle.getList("Attributes", 10).getCompound(0).getString("Name"));
            assertEquals("forge:swim_speed", rootVehicle.getList("Passengers", 10).getCompound(0)
                    .getList("Attributes", 10).getCompound(0).getString("Name"));
            return value;
        }), DefaultTypeReferences.PLAYER, source, 512);
        assertEquals(expected, source);
        expected.getCompound("RootVehicle").getCompound("Entity").getList("Attributes", 10)
                .getCompound(0).putString("Name", "forge:swim_speed");
        expected.getCompound("RootVehicle").getCompound("Entity").getList("Passengers", 10).getCompound(0)
                .getList("Attributes", 10).getCompound(0).putString("Name", "forge:swim_speed");
        assertEquals(expected, result);
    }

    @Test public void playerInventoryAndForeignRootVehicleLookalikesAreNotWalked() {
        CompoundNBT player = player(entity("example:vehicle", true));
        CompoundNBT foreign = entity("example:private", true);
        foreign.putByte("Count", (byte) 3);
        ListNBT inventory = new ListNBT(); inventory.add(foreign.copy()); player.put("Inventory", inventory);
        player.put("PrivateData", player(entity("example:foreign", true)));
        CompoundNBT before = player.copy();
        CompoundNBT result = LegacyPlayerDataFixes.update(new RecordingFixer(value -> value),
                DefaultTypeReferences.PLAYER, player, 1343);
        assertEquals(before, player);
        assertEquals(inventory, result.getList("Inventory", 10));
        assertEquals(before.getCompound("PrivateData"), result.getCompound("PrivateData"));
    }

    @Test public void rootVehiclePassengerCollisionFailsWithoutChangingPlayer() {
        CompoundNBT vehicle = entity("example:vehicle", true);
        CompoundNBT child = entity("example:seat", true);
        CompoundNBT duplicate = attribute(false); duplicate.putString("Name", "forge:swim_speed");
        child.getList("Attributes", 10).add(duplicate);
        ListNBT passengers = new ListNBT(); passengers.add(child); vehicle.put("Passengers", passengers);
        CompoundNBT player = player(vehicle), before = player.copy();
        try {
            LegacyPlayerDataFixes.update(DataFixesManager.getDataFixer(), DefaultTypeReferences.PLAYER, player, 1343);
            fail("Alias collision accepted");
        } catch (IllegalStateException expected) { assertEquals(before, player); }
    }

    @Test public void nativePlayerDfuRetainsVehicleAndPassengerCustomizedAttributes() {
        CompoundNBT vehicle = entity("minecraft:wolf", true);
        ListNBT passengers = new ListNBT(); passengers.add(entity("minecraft:wolf", true)); vehicle.put("Passengers", passengers);
        CompoundNBT player = player(vehicle), before = player.copy();
        CompoundNBT result = LegacyPlayerDataFixes.update(DataFixesManager.getDataFixer(), DefaultTypeReferences.PLAYER, player, 1343);
        CompoundNBT fixed = result.getCompound("RootVehicle").getCompound("Entity");
        for (CompoundNBT actual : new CompoundNBT[] {fixed, fixed.getList("Passengers", 10).getCompound(0)}) {
            CompoundNBT attribute = actual.getList("Attributes", 10).getCompound(0);
            assertEquals("forge:swim_speed", attribute.getString("Name"));
            assertEquals(2D, attribute.getDouble("Base"), 0D);
            assertEquals(MODIFIER, attribute.getList("Modifiers", 10).getCompound(0).getUUID("UUID"));
        }
        assertEquals(before, player);
    }

    @Test public void ownPlayerAttributeBaseTwoAndModifierSurviveNativePlayerDfu() {
        CompoundNBT player = player(entity("minecraft:wolf", true));
        player.put("Attributes", entity("minecraft:wolf", true).getList("Attributes", 10));
        CompoundNBT foreign = entity("example:private", true);
        player.put("PrivateData", foreign.copy());
        CompoundNBT before = player.copy();
        CompoundNBT result = LegacyPlayerDataFixes.update(DataFixesManager.getDataFixer(), DefaultTypeReferences.PLAYER, player, 1343);
        CompoundNBT attribute = result.getList("Attributes", 10).getCompound(0);
        assertEquals("forge:swim_speed", attribute.getString("Name"));
        assertEquals(2D, attribute.getDouble("Base"), 0D);
        assertEquals(MODIFIER, attribute.getList("Modifiers", 10).getCompound(0).getUUID("UUID"));
        assertEquals("keep-attribute-field", attribute.getString("PrivateNote"));
        assertEquals("keep-modifier-field", attribute.getList("Modifiers", 10).getCompound(0).getString("PrivateNote"));
        assertEquals(foreign, result.getCompound("PrivateData"));
        assertEquals(before, player);
    }

    @Test public void ownPlayerAttributeCollisionFailsWithoutMutatingCaller() {
        CompoundNBT player = player(entity("example:vehicle", true));
        ListNBT attributes = entity("example:attributes", true).getList("Attributes", 10);
        CompoundNBT duplicate = attribute(false); duplicate.putString("Name", "forge:swim_speed"); attributes.add(duplicate);
        player.put("Attributes", attributes);
        CompoundNBT before = player.copy();
        try {
            LegacyPlayerDataFixes.update(DataFixesManager.getDataFixer(), DefaultTypeReferences.PLAYER, player, 1343);
            fail("Own player attribute collision accepted");
        } catch (IllegalStateException expected) { assertEquals(before, player); }
    }

    @Test public void malformedAttributesFailClosedWithoutChangingSource() {
        CompoundNBT entity = entity("example:vehicle", false);
        ListNBT malformed = new ListNBT(); malformed.add(StringNBT.valueOf("keep")); entity.put("Attributes", malformed);
        CompoundNBT root = chunk(entity), before = root.copy();
        try { LegacyEntityDataFixes.prepare(root); fail("Malformed attributes accepted"); }
        catch (IllegalStateException expected) { assertEquals(before, root); }
    }

    @Test public void actualNativeWolfRetainsCustomizedBaseTwoAndNonemptyModifier() throws Exception {
        registerForgeAttributes();
        // Keep constructor-free test setup without linking restricted JDK types.
        Class<?> allocator = Class.forName("sun.misc.Unsafe");
        Field unsafeField = allocator.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        ServerWorld world = (ServerWorld) allocator.getMethod("allocateInstance", Class.class)
                .invoke(unsafeField.get(null), ServerWorld.class);
        set(World.class, world, "dimension", World.NETHER);
        set(World.class, world, "profiler", (Supplier<IProfiler>) () -> EmptyProfiler.INSTANCE);
        CompoundNBT source = entity("minecraft:wolf", true);
        source.putFloat("Health", 8F);
        CompoundNBT red = (CompoundNBT) DataFixesManager.getDataFixer().update(TypeReferences.ENTITY,
                new Dynamic<>(NBTDynamicOps.INSTANCE, source.copy()), 1343,
                SharedConstants.getCurrentVersion().getWorldVersion()).getValue();
        WolfEntity before = new WolfEntity(EntityType.WOLF, world); before.load(red);
        assertEquals(1D, before.getAttribute(ForgeMod.SWIM_SPEED.get()).getBaseValue(), 0D);
        CompoundNBT root = chunk(source);
        LegacyEntityDataFixes.prepare(root); LegacyEntityDataFixes.finish(root);
        WolfEntity after = new WolfEntity(EntityType.WOLF, world);
        after.load(root.getCompound("Level").getList("Entities", 10).getCompound(0));
        assertEquals(2D, after.getAttribute(ForgeMod.SWIM_SPEED.get()).getBaseValue(), 0D);
        assertNotNull(after.getAttribute(ForgeMod.SWIM_SPEED.get()).getModifier(MODIFIER));
        assertEquals(0.75D, after.getAttribute(ForgeMod.SWIM_SPEED.get()).getModifier(MODIFIER).getAmount(), 0D);
        assertEquals(2.75D, after.getAttributeValue(ForgeMod.SWIM_SPEED.get()), 0D);
        assertEquals(8F, after.getHealth(), 0F);
    }

    private static void assertCollision(String first, String second) {
        CompoundNBT entity = entity("example:vehicle", true);
        entity.getList("Attributes", 10).getCompound(0).putString("Name", first);
        CompoundNBT duplicate = attribute(false); duplicate.putString("Name", second);
        entity.getList("Attributes", 10).add(duplicate);
        CompoundNBT root = chunk(entity), before = root.copy();
        try { LegacyEntityDataFixes.prepare(root); fail("Alias collision accepted"); }
        catch (IllegalStateException expected) { assertEquals(before, root); }
    }

    private static CompoundNBT attribute(boolean modifier) {
        CompoundNBT attribute = new CompoundNBT(); attribute.putString("Name", "forge.swimSpeed");
        attribute.putDouble("Base", 2D); attribute.putString("PrivateNote", "keep-attribute-field");
        if (modifier) {
            CompoundNBT entry = new CompoundNBT(); entry.putString("Name", "customized-swim");
            entry.putLong("UUIDMost", MODIFIER.getMostSignificantBits()); entry.putLong("UUIDLeast", MODIFIER.getLeastSignificantBits());
            entry.putDouble("Amount", 0.75D); entry.putInt("Operation", 0); entry.putString("PrivateNote", "keep-modifier-field");
            ListNBT modifiers = new ListNBT(); modifiers.add(entry); attribute.put("Modifiers", modifiers);
        }
        return attribute;
    }

    private static CompoundNBT entity(String id, boolean attributes) {
        CompoundNBT result = new CompoundNBT(); result.putString("id", id);
        if (attributes) { ListNBT list = new ListNBT(); list.add(attribute(true)); result.put("Attributes", list); }
        return result;
    }

    private static CompoundNBT chunk(CompoundNBT entity) {
        CompoundNBT root = new CompoundNBT(); root.putInt("DataVersion", 1343);
        CompoundNBT level = new CompoundNBT(); ListNBT entities = new ListNBT(); entities.add(entity); level.put("Entities", entities);
        root.put("Level", level); return root;
    }

    private static CompoundNBT player(CompoundNBT entity) {
        CompoundNBT result = new CompoundNBT(); CompoundNBT vehicle = new CompoundNBT();
        vehicle.put("Entity", entity); vehicle.putUUID("Attach", new UUID(1L, 2L)); result.put("RootVehicle", vehicle); return result;
    }

    private static void registerForgeAttributes() throws Exception {
        if (ForgeMod.SWIM_SPEED.isPresent()) return;
        Field attributes = ForgeMod.class.getDeclaredField("ATTRIBUTES"); attributes.setAccessible(true);
        Method register = DeferredRegister.class.getDeclaredMethod("addEntries", RegistryEvent.Register.class); register.setAccessible(true);
        register.invoke(attributes.get(null), new RegistryEvent.Register<>(ForgeRegistries.ATTRIBUTES.getRegistryName(), ForgeRegistries.ATTRIBUTES));
    }

    private static void set(Class<?> owner, Object instance, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(instance, value);
    }

    private static final class RecordingFixer implements DataFixer {
        private final UnaryOperator<CompoundNBT> update;
        RecordingFixer(UnaryOperator<CompoundNBT> update) { this.update = update; }
        @Override public <T> Dynamic<T> update(DSL.TypeReference type, Dynamic<T> input, int from, int to) {
            update.apply((CompoundNBT) input.getValue());
            return input;
        }
        @Override public Schema getSchema(int version) { throw new AssertionError("Not used by the boundary"); }
    }
}
