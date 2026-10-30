package zone.moddev.mc.poweradvantage.migration;

import java.lang.reflect.Proxy;
import java.util.Collections;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent.MissingMappings;
import net.minecraftforge.registries.ForgeRegistryEntry;
import net.minecraftforge.registries.IForgeRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

public class VanillaRegistryMappingEventTest {
    private static final ResourceLocation SOURCE = new ResourceLocation("minecraft:zombie_pigman_spawn_egg");
    private static final ResourceLocation TARGET = new ResourceLocation("minecraft:zombified_piglin_spawn_egg");

    @Test public void resolvesTheTargetFromActiveNotTheIncompleteStagingRegistry() {
        Entry target = new Entry();
        IForgeRegistry<Entry> staging = registry(null, false), active = registry(target, true);
        MissingMappings.Mapping<Entry> mapping = new MissingMappings.Mapping<>(staging, active, SOURCE, 42);
        VanillaRegistryRemaps.remap(event(staging, mapping), active);
        assertEquals(MissingMappings.Action.REMAP, mapping.getAction());
        assertSame(target, mapping.getTarget());
        assertEquals(42, mapping.id);
    }

    @Test public void respectsAnExistingMappingDecision() {
        IForgeRegistry<Entry> active = registry(new Entry(), true);
        MissingMappings.Mapping<Entry> mapping = new MissingMappings.Mapping<>(active, active, SOURCE, 42);
        mapping.ignore();
        VanillaRegistryRemaps.remap(event(active, mapping), active);
        assertEquals(MissingMappings.Action.IGNORE, mapping.getAction());
    }

    @Test public void leavesUnrecognizedEntriesUnresolvedForForgeToReport() {
        IForgeRegistry<Entry> active = registry(new Entry(), true);
        MissingMappings.Mapping<Entry> mapping = new MissingMappings.Mapping<>(active, active,
                new ResourceLocation("othermod:zombie_pigman_spawn_egg"), 42);
        VanillaRegistryRemaps.remap(event(active, mapping), active);
        assertEquals(MissingMappings.Action.DEFAULT, mapping.getAction());
    }

    @Test public void neverSubstitutesARegistryDefaultForAMissingTarget() {
        IForgeRegistry<Entry> absent = registry(new Entry(), false);
        MissingMappings.Mapping<Entry> mapping = new MissingMappings.Mapping<>(absent, absent, SOURCE, 42);
        try {
            VanillaRegistryRemaps.remap(event(absent, mapping), absent);
            fail("A registry default must not replace the missing item");
        } catch (IllegalStateException expected) {
            assertEquals(MissingMappings.Action.DEFAULT, mapping.getAction());
        }
    }

    @Test public void retiresOnlyTheTwoNonSerializedVanillaMemoryIndexes() {
        IForgeRegistry<Entry> active = registry(null, false);
        for (String name : new String[] {"golem_last_seen_time", "opened_doors"}) {
            MissingMappings.Mapping<Entry> mapping = new MissingMappings.Mapping<>(active, active,
                    new ResourceLocation("minecraft", name), 42);
            MissingMappings<Entry> event = new MissingMappings<>(new ResourceLocation("minecraft:memory_module_type"),
                    active, Collections.singletonList(mapping));
            VanillaRegistryRemaps.remap(event, active);
            assertEquals(MissingMappings.Action.IGNORE, mapping.getAction());
            assertNull(mapping.getTarget());
        }
    }

    @Test public void neverRetiresOtherModsOrCurrentMemoryTypes() {
        ResourceLocation registry = new ResourceLocation("minecraft:memory_module_type");
        assertFalse(VanillaRegistryRemaps.retiredTransientMemory(registry, new ResourceLocation("othermod:opened_doors")));
        assertFalse(VanillaRegistryRemaps.retiredTransientMemory(registry, new ResourceLocation("minecraft:golem_detected_recently")));
        assertFalse(VanillaRegistryRemaps.retiredTransientMemory(new ResourceLocation("minecraft:item"),
                new ResourceLocation("minecraft:opened_doors")));
    }

    private static MissingMappings<Entry> event(IForgeRegistry<Entry> staging, MissingMappings.Mapping<Entry> mapping) {
        return new MissingMappings<>(new ResourceLocation("minecraft:item"), staging, Collections.singletonList(mapping));
    }

    @SuppressWarnings("unchecked")
    private static IForgeRegistry<Entry> registry(Entry target, boolean contains) {
        return (IForgeRegistry<Entry>) Proxy.newProxyInstance(IForgeRegistry.class.getClassLoader(),
                new Class<?>[] {IForgeRegistry.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getRegistrySuperType": return Entry.class;
                        case "containsKey": return contains && TARGET.equals(args[0]);
                        case "getValue": return target;
                        case "getKey": return args[0] == target ? TARGET : null;
                        default: throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    private static final class Entry extends ForgeRegistryEntry<Entry> {}
}
