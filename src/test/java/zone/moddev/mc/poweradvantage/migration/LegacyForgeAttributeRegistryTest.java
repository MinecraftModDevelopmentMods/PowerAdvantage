package zone.moddev.mc.poweradvantage.migration;

import static org.junit.Assert.*;

import com.mojang.serialization.Dynamic;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTDynamicOps;
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

public class LegacyForgeAttributeRegistryTest {
    private static final String[][] ALIASES = {
        {"forge.swimSpeed", "forge:swim_speed"},
        {"forge.nameTagDistance", "forge:nametag_distance"},
        {"forge.entity_gravity", "forge:entity_gravity"},
        {"generic.reachDistance", "forge:reach_distance"}
    };

    @BeforeClass public static void bootstrap() throws Exception {
        Bootstrap.bootStrap();
        if (ForgeMod.SWIM_SPEED.isPresent()) return;
        Field field = ForgeMod.class.getDeclaredField("ATTRIBUTES"); field.setAccessible(true);
        Method register = DeferredRegister.class.getDeclaredMethod("addEntries", RegistryEvent.Register.class);
        register.setAccessible(true);
        register.invoke(field.get(null), new RegistryEvent.Register<>(ForgeRegistries.ATTRIBUTES.getRegistryName(), ForgeRegistries.ATTRIBUTES));
    }

    @Test public void retainsEveryForgeAliasAndForeignPayloadBeforeNativeDfu() {
        for (String[] alias : ALIASES) {
            CompoundNBT source = entity(alias[0], 7.5D), before = source.copy();
            CompoundNBT root = chunk(source);
            LegacyEntityDataFixes.prepare(root); LegacyEntityDataFixes.finish(root);
            CompoundNBT expected = before.copy();
            expected.getList("Attributes", 10).getCompound(0).putString("Name", alias[1]);
            assertEquals(alias[0], expected, fixedEntity(root));
            assertEquals(before, source);
        }
    }

    @Test public void nativeLivingEntitiesLoadCustomizedValuesAndModifiersForAllThreeAttributes() throws Exception {
        double[] values = {2D, 12.5D, 0.15D};
        Class<?> allocator = Class.forName("sun.misc.Unsafe");
        Field unsafe = allocator.getDeclaredField("theUnsafe"); unsafe.setAccessible(true);
        ServerWorld world = (ServerWorld) allocator.getMethod("allocateInstance", Class.class)
                .invoke(unsafe.get(null), ServerWorld.class);
        set(World.class, world, "dimension", World.NETHER);
        set(World.class, world, "profiler", (Supplier<IProfiler>) () -> EmptyProfiler.INSTANCE);
        for (int index = 0; index < 3; index++) {
            CompoundNBT source = entity(ALIASES[index][0], values[index]); source.putString("id", "minecraft:wolf");
            CompoundNBT raw = (CompoundNBT) DataFixesManager.getDataFixer().update(TypeReferences.ENTITY,
                    new Dynamic<>(NBTDynamicOps.INSTANCE, source.copy()), 1343,
                    SharedConstants.getCurrentVersion().getWorldVersion()).getValue();
            WolfEntity negative = new WolfEntity(EntityType.WOLF, world); negative.load(raw);
            ModifiableAttributeInstance old = negative.getAttribute(ForgeRegistries.ATTRIBUTES.getValue(
                    new net.minecraft.util.ResourceLocation(ALIASES[index][1])));
            assertNotEquals(ALIASES[index][0], values[index], old.getBaseValue(), 0D);
            CompoundNBT root = chunk(source);
            LegacyEntityDataFixes.prepare(root); LegacyEntityDataFixes.finish(root);
            WolfEntity positive = new WolfEntity(EntityType.WOLF, world); positive.load(fixedEntity(root));
            ModifiableAttributeInstance actual = positive.getAttribute(old.getAttribute());
            assertEquals(ALIASES[index][0], values[index], actual.getBaseValue(), 0D);
            assertEquals(0.125D, actual.getModifier(new UUID(17L, 19L)).getAmount(), 0D);
            assertEquals(values[index] + 0.125D, actual.getValue(), 0D);
        }
    }

    @Test public void playerReachSurvivesNativePlayerFixingAndNativeAttributeLoading() {
        CompoundNBT source = entity("generic.reachDistance", 7.5D), before = source.copy();
        CompoundNBT actual = LegacyPlayerDataFixes.update(DataFixesManager.getDataFixer(),
                DefaultTypeReferences.PLAYER, source, 1343);
        CompoundNBT value = actual.getList("Attributes", 10).getCompound(0);
        assertEquals("forge:reach_distance", value.getString("Name"));
        assertSame(ForgeMod.REACH_DISTANCE.get(), ForgeRegistries.ATTRIBUTES.getValue(
                new net.minecraft.util.ResourceLocation(value.getString("Name"))));
        ModifiableAttributeInstance nativeAttribute = new ModifiableAttributeInstance(ForgeMod.REACH_DISTANCE.get(), unused -> {});
        nativeAttribute.load(value);
        assertEquals(7.5D, nativeAttribute.getBaseValue(), 0D);
        assertEquals(7.625D, nativeAttribute.getValue(), 0D);
        assertEquals(0.125D, nativeAttribute.getModifier(new UUID(17L, 19L)).getAmount(), 0D);
        assertEquals(before, source);
    }

    @Test public void allAliasCollisionsFailClosedWithoutChangingCaller() {
        for (String[] alias : ALIASES) for (String[] pair : new String[][] {
                {alias[0], alias[1]}, {alias[0], alias[0]}, {alias[1], alias[1]}}) {
            CompoundNBT source = entity(pair[0], 2D);
            source.getList("Attributes", 10).add(attribute(pair[1], 3D));
            CompoundNBT root = chunk(source), before = root.copy();
            try { LegacyEntityDataFixes.prepare(root); fail("Ambiguous attribute accepted: " + alias[0]); }
            catch (IllegalStateException expected) { assertEquals(before, root); }
        }
    }

    @Test public void everyAliasSurvivesPlayerMountsAndPassengersWithoutWalkingPrivateData() {
        for (String[] alias : ALIASES) {
            CompoundNBT source = entity(alias[0], 7.5D);
            CompoundNBT vehicle = entity(alias[0], 6.5D); vehicle.putString("id", "minecraft:wolf");
            CompoundNBT passenger = entity(alias[0], 5.5D); passenger.putString("id", "minecraft:wolf");
            ListNBT passengers = new ListNBT(); passengers.add(passenger); vehicle.put("Passengers", passengers);
            CompoundNBT rootVehicle = new CompoundNBT(); rootVehicle.put("Entity", vehicle);
            rootVehicle.putUUID("Attach", new UUID(23L, 29L)); source.put("RootVehicle", rootVehicle);
            source.put("PrivateData", entity(alias[0], 4.5D));
            CompoundNBT before = source.copy();
            CompoundNBT actual = LegacyPlayerDataFixes.update(DataFixesManager.getDataFixer(),
                    DefaultTypeReferences.PLAYER, source, 1343);
            CompoundNBT fixedVehicle = actual.getCompound("RootVehicle").getCompound("Entity");
            for (CompoundNBT item : new CompoundNBT[] {actual, fixedVehicle,
                    fixedVehicle.getList("Passengers", 10).getCompound(0)}) {
                assertEquals(alias[1], item.getList("Attributes", 10).getCompound(0).getString("Name"));
                assertEquals(0.125D, item.getList("Attributes", 10).getCompound(0)
                        .getList("Modifiers", 10).getCompound(0).getDouble("Amount"), 0D);
            }
            assertEquals(before.getCompound("PrivateData"), actual.getCompound("PrivateData"));
            assertEquals(before, source);
        }
    }

    @Test public void everyAliasIsIdempotentWithSavedValuesIntact() {
        for (String[] alias : ALIASES) {
            CompoundNBT root = chunk(entity(alias[0], 7.5D));
            LegacyEntityDataFixes.prepare(root); LegacyEntityDataFixes.finish(root);
            CompoundNBT before = root.copy();
            LegacyEntityDataFixes.prepare(root); LegacyEntityDataFixes.finish(root);
            assertEquals(before, root);
        }
    }

    @Test public void modernChunksAndSimilarUnknownNamesRemainUntouched() {
        for (String[] alias : ALIASES) {
            CompoundNBT root = chunk(entity(alias[0], 7D));
            root.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
            CompoundNBT before = root.copy(); LegacyEntityDataFixes.prepare(root); assertEquals(before, root);
            root = chunk(entity(alias[0] + ".private", 7D)); before = root.copy();
            LegacyEntityDataFixes.prepare(root); LegacyEntityDataFixes.finish(root); assertEquals(before, root);
        }
    }

    private static CompoundNBT entity(String name, double base) {
        CompoundNBT value = new CompoundNBT(); value.putString("id", "example:vehicle");
        ListNBT attributes = new ListNBT(); attributes.add(attribute(name, base)); value.put("Attributes", attributes);
        return value;
    }

    private static CompoundNBT attribute(String name, double base) {
        CompoundNBT value = new CompoundNBT(); value.putString("Name", name); value.putDouble("Base", base);
        value.putString("PrivateNote", "preserve-attribute-payload");
        CompoundNBT modifier = new CompoundNBT(); modifier.putString("Name", "customized-attribute");
        modifier.putLong("UUIDMost", 17L); modifier.putLong("UUIDLeast", 19L);
        modifier.putDouble("Amount", 0.125D); modifier.putInt("Operation", 0);
        modifier.putString("PrivateNote", "preserve-modifier-payload");
        ListNBT modifiers = new ListNBT(); modifiers.add(modifier); value.put("Modifiers", modifiers);
        return value;
    }

    private static CompoundNBT chunk(CompoundNBT entity) {
        CompoundNBT value = new CompoundNBT(); value.putInt("DataVersion", 1343);
        CompoundNBT level = new CompoundNBT(); ListNBT entities = new ListNBT(); entities.add(entity);
        level.put("Entities", entities); value.put("Level", level); return value;
    }

    private static CompoundNBT fixedEntity(CompoundNBT root) { return root.getCompound("Level").getList("Entities", 10).getCompound(0); }

    private static void set(Class<?> owner, Object instance, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(instance, value);
    }
}
