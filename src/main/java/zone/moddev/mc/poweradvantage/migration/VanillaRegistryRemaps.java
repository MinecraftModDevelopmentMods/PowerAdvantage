package zone.moddev.mc.poweradvantage.migration;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.brain.memory.MemoryModuleType;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/** Vanilla's data fixers rename saved data, but not Forge's registry snapshot. */
public final class VanillaRegistryRemaps {
    private VanillaRegistryRemaps() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(Item.class, VanillaRegistryRemaps::items);
        MinecraftForge.EVENT_BUS.addGenericListener(EntityType.class, VanillaRegistryRemaps::entities);
        MinecraftForge.EVENT_BUS.addGenericListener(SoundEvent.class, VanillaRegistryRemaps::sounds);
        MinecraftForge.EVENT_BUS.addGenericListener(Biome.class, VanillaRegistryRemaps::biomes);
        MinecraftForge.EVENT_BUS.addGenericListener(MemoryModuleType.class, VanillaRegistryRemaps::memories);
    }

    private static void items(RegistryEvent.MissingMappings<Item> event) { remap(event, ForgeRegistries.ITEMS); }
    private static void entities(RegistryEvent.MissingMappings<EntityType<?>> event) { remap(event, ForgeRegistries.ENTITIES); }
    private static void sounds(RegistryEvent.MissingMappings<SoundEvent> event) { remap(event, ForgeRegistries.SOUND_EVENTS); }
    private static void biomes(RegistryEvent.MissingMappings<Biome> event) { remap(event, ForgeRegistries.BIOMES); }
    private static void memories(RegistryEvent.MissingMappings<MemoryModuleType<?>> event) { remap(event, ForgeRegistries.MEMORY_MODULE_TYPES); }

    static <T extends IForgeRegistryEntry<T>> void remap(RegistryEvent.MissingMappings<T> event, IForgeRegistry<T> active) {
        for (RegistryEvent.MissingMappings.Mapping<T> mapping : event.getMappings("minecraft")) {
            if (mapping.getAction() != RegistryEvent.MissingMappings.Action.DEFAULT) continue;
            if (retiredTransientMemory(event.getName(), mapping.key)) {
                // Both old types used register(name) without a serializer: no saved
                // brain values exist. Retire only their obsolete Forge ID indexes.
                mapping.ignore();
                PowerAdvantage.LOGGER.info("Retired non-serialized vanilla AI registry index {}", mapping.key);
                continue;
            }
            ResourceLocation replacement = replacement(event.getName(), mapping.key);
            if (replacement == null) continue;
            // The event's registry is STAGING: new entries need not be present there yet.
            if (!active.containsKey(replacement)) {
                throw new IllegalStateException("Missing native vanilla migration target " + replacement);
            }
            T target = active.getValue(replacement);
            mapping.remap(target);
            PowerAdvantage.LOGGER.info("Migrated vanilla registry entry {} to {}", mapping.key, replacement);
        }
    }

    static boolean retiredTransientMemory(ResourceLocation registry, ResourceLocation old) {
        return registry.equals(new ResourceLocation("minecraft:memory_module_type"))
                && old.getNamespace().equals("minecraft")
                && (old.getPath().equals("golem_last_seen_time") || old.getPath().equals("opened_doors"));
    }

    static ResourceLocation replacement(ResourceLocation registry, ResourceLocation old) {
        if (!"minecraft".equals(old.getNamespace())) return null;
        String target = null;
        if (registry.equals(new ResourceLocation("minecraft:entity_type")) && old.getPath().equals("zombie_pigman")) {
            target = "zombified_piglin";
        } else if (registry.equals(new ResourceLocation("minecraft:item")) && old.getPath().equals("zombie_pigman_spawn_egg")) {
            target = "zombified_piglin_spawn_egg";
        } else if (registry.equals(new ResourceLocation("minecraft:worldgen/biome")) && old.getPath().equals("nether")) {
            target = "nether_wastes";
        } else if (registry.equals(new ResourceLocation("minecraft:sound_event"))) {
            for (String sound : new String[] {"ambient", "angry", "death", "hurt"}) {
                if (old.getPath().equals("entity.zombie_pigman." + sound)) target = "entity.zombified_piglin." + sound;
            }
            // The former single Nether biome becomes Nether Wastes in 1.16.
            if (old.getPath().equals("music.nether")) target = "music.nether.nether_wastes";
        }
        return target == null ? null : new ResourceLocation("minecraft", target);
    }
}
