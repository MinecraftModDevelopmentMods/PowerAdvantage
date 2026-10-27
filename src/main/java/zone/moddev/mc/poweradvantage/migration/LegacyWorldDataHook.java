package zone.moddev.mc.poweradvantage.migration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Dynamic;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixUtils;

import cpw.mods.modlauncher.api.INameMappingService;
import cyano.poweradvantage.api.migration.LegacyMigrationApi;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.datafix.fixes.BlockStateFlatteningMap;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.util.datafix.TypeReferences;
import net.minecraft.world.storage.SaveFormat.LevelSave;
import net.minecraft.world.storage.IServerConfiguration;
import net.minecraftforge.fml.WorldPersistenceHooks;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/**
 * Shared Advantage pre-flattening migration hook for Minecraft 1.10 and 1.12
 * worlds. Add-ons contribute their identities through {@link LegacyMigrationApi}.
 */
public final class LegacyWorldDataHook implements WorldPersistenceHooks.WorldPersistenceHook {
    private static final LegacyWorldDataHook INSTANCE = new LegacyWorldDataHook();
    private static final ResourceLocation BLOCK_REGISTRY = new ResourceLocation("minecraft", "blocks");
    private static final Map<String, CompoundNBT> LEGACY_WORLD_DATA = new ConcurrentHashMap<>();
    private static final BitSet LEGACY_BLOCK_IDS = new BitSet();
    private static final Map<Integer, String> LEGACY_ENCHANTMENTS = new LinkedHashMap<>();
    private static final Set<Long> LEGACY_CHUNKS = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<Long, Integer> LEGACY_BLOCK_COUNTS = new ConcurrentHashMap<>();
    private static final Map<String, String> VANILLA_BLOCK_ENTITY_IDS = vanillaBlockEntityIds();
    private static final String PRESERVE_CHUNK_MARKER = "AdvantageLegacyPreserveChunk";
    private static final int LAST_PRE_FLATTENING_VERSION = 1343;
    private static boolean legacyWorldActive;
    private static boolean registered;

    private LegacyWorldDataHook() {}

    public static synchronized void register() {
        if (!registered) {
            WorldPersistenceHooks.addHook(INSTANCE);
            registered = true;
        }
    }

    /** Called by the coremod before Forge reads level.dat. */
    public static synchronized void prepareLegacyWorld(File levelDat) {
        AdvantageLegacyMigrations.registerPowerAdvantage();
        legacyWorldActive = false;
        LEGACY_ENCHANTMENTS.clear();
        LEGACY_CHUNKS.clear();
        LEGACY_BLOCK_COUNTS.clear();
        removeRetiredProfessionSnapshot(levelDat);
        File source = legacyRegistrySource(levelDat);
        if (source == null) return;
        try (FileInputStream input = new FileInputStream(source)) {
            CompoundNBT root = CompressedStreamTools.readCompressed(input);
            if (!root.contains("FML", 10)) return;
            prepareLegacyData(levelDat.getParentFile(), root.getCompound("FML"));
            migrateLoosePlayerData(levelDat, root);
        } catch (IOException exception) {
            PowerAdvantage.LOGGER.warn("Could not inspect '{}' for legacy Advantage registry data", source, exception);
        }
    }

    private static void removeRetiredProfessionSnapshot(File levelDat) {
        if (!levelDat.isFile()) return;
        try {
            CompoundNBT root;
            try (FileInputStream input = new FileInputStream(levelDat)) {
                root = CompressedStreamTools.readCompressed(input);
            }
            if (removeRetiredVanillaProfessionRegistry(root)) writeWithBackup(levelDat, root);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not prepare the old villager registry in " + levelDat, exception);
        }
    }

    static boolean removeRetiredVanillaProfessionRegistry(CompoundNBT root) {
        boolean changed = false;
        for (String namespace : new String[] {"fml", "FML"}) {
            CompoundNBT registries = root.getCompound(namespace).getCompound("Registries");
            String key = "minecraft:villagerprofessions";
            if (!registries.contains(key, 10)) continue;
            CompoundNBT snapshot = registries.getCompound(key);
            if (!snapshot.contains("ids", 9)) continue;
            boolean vanillaOnly = true;
            for (INBT entry : (ListNBT) snapshot.get("ids")) {
                if (!(entry instanceof CompoundNBT) || !Arrays.asList("minecraft:farmer", "minecraft:librarian",
                        "minecraft:priest", "minecraft:smith", "minecraft:butcher", "minecraft:nitwit")
                        .contains(((CompoundNBT) entry).getString("K"))) vanillaOnly = false;
            }
            for (String list : new String[] {"aliases", "overrides", "dummied"}) {
                if (snapshot.contains(list, 9) && !((ListNBT) snapshot.get(list)).isEmpty()) vanillaOnly = false;
            }
            if (snapshot.getIntArray("blocked").length > 0 || !vanillaOnly) continue;
            // Vanilla fixes the villagers and offers. Remove only the retired vanilla ID index.
            registries.remove(key);
            changed = true;
        }
        return changed;
    }

    /** Called by the coremod immediately after a legacy chunk NBT is read. */
    public static synchronized void prepareLegacyChunk(CompoundNBT root) {
        if (root == null) return;
        migrateLegacyNbt(root);
        migrateLegacyEntities(root);
        if (!legacyWorldActive || !root.contains("Level", 10)) return;
        CompoundNBT level = root.getCompound("Level");
        int legacyBlocks = countLegacyBlocks(level);
        if (legacyBlocks > 0) {
            LEGACY_BLOCK_COUNTS.put(Long.valueOf(chunkKey(level.getInt("xPos"), level.getInt("zPos"))),
                    Integer.valueOf(legacyBlocks));
        }
        level.putBoolean("TerrainPopulated", true);
        level.putBoolean("LightPopulated", true);
        level.putBoolean(PRESERVE_CHUNK_MARKER, true);
    }

    private static void migrateLegacyEntities(CompoundNBT root) {
        int version = root.contains("DataVersion", 99) ? root.getInt("DataVersion") : -1;
        if (version >= LAST_PRE_FLATTENING_VERSION || !root.contains("Level", 10)) return;
        DataFixer fixer = DataFixesManager.getDataFixer();
        Set<?> vanillaIds = fixer.getSchema(DataFixUtils.makeKey(version))
                .findChoiceType(TypeReferences.ENTITY).types().keySet();
        ListNBT entities = root.getCompound("Level").getList("Entities", 10);
        for (int index = 0; index < entities.size(); index++) {
            entities.set(index, migrateVanillaEntity(entities.getCompound(index), version, fixer, vanillaIds));
        }
    }

    private static CompoundNBT migrateVanillaEntity(CompoundNBT entity, int version,
            DataFixer fixer, Set<?> vanillaIds) {
        boolean hasPassengers = entity.contains("Passengers", 9);
        ListNBT passengers = entity.getList("Passengers", 10);
        // An unknown mod choice can stop a mixed chunk's early entity-ID fixes.
        // Fix each recognised vanilla entity independently, without its passengers.
        if (vanillaIds.contains(entity.getString("id"))) {
            CompoundNBT isolated = entity.copy();
            isolated.remove("Passengers");
            entity = (CompoundNBT) fixer.update(TypeReferences.ENTITY,
                    new Dynamic<>(net.minecraft.nbt.NBTDynamicOps.INSTANCE, isolated),
                    version, LAST_PRE_FLATTENING_VERSION).getValue();
        }
        for (int index = 0; index < passengers.size(); index++) {
            passengers.set(index, migrateVanillaEntity(passengers.getCompound(index), version, fixer, vanillaIds));
        }
        if (hasPassengers) entity.put("Passengers", passengers);
        return entity;
    }

    /** Called after vanilla datafixing, before a legacy chunk is returned. */
    public static CompoundNBT finalizeLegacyChunk(CompoundNBT root) {
        if (root != null && root.contains("Level", 10)) {
            CompoundNBT level = root.getCompound("Level");
            if (level.getBoolean(PRESERVE_CHUNK_MARKER)) {
                level.putString("Status", "full");
                level.remove(PRESERVE_CHUNK_MARKER);
            }
        }
        return root;
    }

    public static boolean shouldBlockWorldgenWrite(net.minecraft.util.math.BlockPos position) {
        return legacyWorldActive && position != null
                && LEGACY_CHUNKS.contains(Long.valueOf(chunkKey(position.getX() >> 4, position.getZ() >> 4)));
    }

    @Override public String getModId() { return "FML"; }

    @Override
    public CompoundNBT getDataForWriting(LevelSave handler, IServerConfiguration info) {
        CompoundNBT legacy = LEGACY_WORLD_DATA.get(worldKey(handler.getWorldDir().toFile()));
        return legacy == null ? new CompoundNBT() : legacy.copy();
    }

    @Override
    public void readData(LevelSave handler, IServerConfiguration info, CompoundNBT tag) {
        prepareLegacyData(handler.getWorldDir().toFile(), tag);
    }

    private static synchronized void prepareLegacyData(File worldDirectory, CompoundNBT tag) {
        if (!tag.contains("Registries", 10)) return;
        CompoundNBT registries = tag.getCompound("Registries");
        if (!registries.contains(BLOCK_REGISTRY.toString(), 10)) return;
        installLegacyEnchantments(registries);
        String key = worldKey(worldDirectory);
        boolean first = !LEGACY_WORLD_DATA.containsKey(key);
        int states = installLegacyBlockStates(registries.getCompound(BLOCK_REGISTRY.toString()));
        if (first) LEGACY_WORLD_DATA.put(key, tag.copy());
        legacyWorldActive = states > 0;
        int chunks = indexLegacyChunks(worldDirectory);
        if (first && states > 0) {
            PowerAdvantage.LOGGER.info("Prepared {} legacy Advantage states and protected {} existing chunks in '{}'",
                    Integer.valueOf(states), Integer.valueOf(chunks), worldDirectory);
            writeMigrationReport(worldDirectory, states, chunks);
        }
    }

    private static int installLegacyBlockStates(CompoundNBT snapshot) {
        LEGACY_BLOCK_IDS.clear();
        Map<ResourceLocation, Integer> ids = new LinkedHashMap<>();
        ListNBT savedIds = snapshot.getList("ids", 10);
        int highestState = 0;
        for (int index = 0; index < savedIds.size(); index++) {
            CompoundNBT entry = savedIds.getCompound(index);
            ResourceLocation id = safeId(normalizeLegacyRegistryName(entry.getString("K")));
            if (id == null || !LegacyMigrationApi.handlesNamespace(id.getNamespace())) continue;
            int numeric = entry.getInt("V");
            ids.put(id, Integer.valueOf(numeric));
            LEGACY_BLOCK_IDS.set(numeric);
            highestState = Math.max(highestState, (numeric << 4) | 15);
        }
        if (ids.isEmpty()) return 0;

        Dynamic<?>[] table = expandFlatteningTable(highestState + 1);
        int mapped = 0;
        for (Map.Entry<ResourceLocation, Integer> entry : ids.entrySet()) {
            ResourceLocation target = LegacyMigrationApi.blockTarget(entry.getKey());
            Block block = ForgeRegistries.BLOCKS.getValue(target);
            if (block == null) {
                throw new IllegalStateException("No Advantage replacement for legacy block " + entry.getKey()
                        + " (expected " + target + ")");
            }
            for (int metadata = 0; metadata < 16; metadata++) {
                BlockState state = LegacyMigrationApi.decodeBlockState(entry.getKey(), block, metadata);
                table[(entry.getValue().intValue() << 4) | metadata] =
                        BlockStateFlatteningMap.parse(NBTUtil.writeBlockState(state).toString());
                mapped++;
            }
        }
        return mapped;
    }

    private static void installLegacyEnchantments(CompoundNBT registries) {
        LEGACY_ENCHANTMENTS.clear();
        ListNBT entries = registries.getCompound("minecraft:enchantments").getList("ids", 10);
        for (int index = 0; index < entries.size(); index++) {
            CompoundNBT entry = entries.getCompound(index);
            ResourceLocation name = safeId(entry.getString("K"));
            if (name != null) LEGACY_ENCHANTMENTS.put(Integer.valueOf(entry.getInt("V")), name.toString());
        }
    }

    /** Resolve saved IDs, not today's registry order or vanilla's conflicting numeric IDs. */
    static boolean migrateLegacyEnchantments(CompoundNBT compound, Map<Integer, String> savedIds) {
        boolean changed = migrateEnchantmentList(compound, "StoredEnchantments", savedIds);
        changed |= migrateEnchantmentList(compound, "Enchantments", savedIds);
        if (compound.contains("ench", 9)) {
            ListNBT enchantments = compound.getList("ench", 10);
            if (!enchantments.isEmpty() && !savedIds.isEmpty()) {
                // A partial conversion would leave an unreadable mixed old/new list.
                for (int index = 0; index < enchantments.size(); index++) {
                    CompoundNBT enchantment = enchantments.getCompound(index);
                    if (enchantment.contains("id", 99)
                            && !savedIds.containsKey(Integer.valueOf(enchantment.getInt("id")))) {
                        throw new IllegalStateException("Missing saved enchantment mapping for " + enchantment.getInt("id"));
                    }
                }
                migrateEnchantmentList(compound, "ench", savedIds);
                compound.put("Enchantments", enchantments);
                compound.remove("ench");
                changed = true;
            }
        }
        return changed;
    }

    private static boolean migrateEnchantmentList(CompoundNBT compound, String key, Map<Integer, String> savedIds) {
        boolean changed = false;
        ListNBT list = compound.getList(key, 10);
        for (int index = 0; index < list.size(); index++) {
            CompoundNBT enchantment = list.getCompound(index);
            if (!enchantment.contains("id", 99)) continue;
            String name = savedIds.get(Integer.valueOf(enchantment.getInt("id")));
            if (name != null) {
                enchantment.putString("id", name);
                changed = true;
            }
        }
        return changed;
    }

    private static void migrateLoosePlayerData(File levelDat, CompoundNBT root) throws IOException {
        boolean changed = migrateLegacyNbt(root) > 0;
        if (changed && levelDat.isFile()) writeWithBackup(levelDat, root);
        File playerData = new File(levelDat.getParentFile(), "playerdata");
        File[] files = playerData.listFiles((directory, name) -> name.endsWith(".dat"));
        if (files == null) return;
        Arrays.sort(files);
        for (File file : files) {
            try (FileInputStream input = new FileInputStream(file)) {
                CompoundNBT player = CompressedStreamTools.readCompressed(input);
                if (migrateLegacyNbt(player) > 0) writeWithBackup(file, player);
            }
        }
    }

    private static int migrateLegacyNbt(INBT value) {
        int changed = 0;
        if (value instanceof CompoundNBT) {
            CompoundNBT compound = (CompoundNBT) value;
            String id = compound.getString("id");
            String vanillaBlockEntity = VANILLA_BLOCK_ENTITY_IDS.get(id);
            if (vanillaBlockEntity != null) {
                compound.putString("id", vanillaBlockEntity);
                id = vanillaBlockEntity;
                changed++;
            }

            String tileTarget = LegacyMigrationApi.tileTarget(id);
            String entityTarget = LegacyMigrationApi.entityTarget(tileTarget);
            if (!entityTarget.equals(normalizeIdentity(id))) {
                compound.putString("id", entityTarget);
                id = entityTarget;
                changed++;
            }

            if ("forge:bucketfilled".equalsIgnoreCase(id) && compound.contains("tag", 10)) {
                CompoundNBT tag = compound.getCompound("tag");
                ResourceLocation bucket = LegacyMigrationApi.bucketTarget(tag.getString("FluidName"));
                if (bucket != null) {
                    compound.putString("id", bucket.toString());
                    tag.remove("FluidName");
                    tag.remove("Amount");
                    if (tag.isEmpty()) compound.remove("tag");
                    changed++;
                }
            } else if (compound.contains("Count", 99)) {
                ResourceLocation oldId = safeId(id);
                if (oldId != null && LegacyMigrationApi.handlesNamespace(oldId.getNamespace())) {
                    ResourceLocation target = LegacyMigrationApi.itemTarget(oldId);
                    if (!target.equals(oldId)) {
                        compound.putString("id", target.toString());
                        changed++;
                    }
                    if (LegacyMigrationApi.isDurableItem(oldId)
                            && compound.contains("Damage", 99) && compound.getInt("Damage") > 0) {
                        CompoundNBT tag = compound.contains("tag", 10)
                                ? compound.getCompound("tag") : new CompoundNBT();
                        if (!tag.contains("Damage", 99)) {
                            tag.putInt("Damage", compound.getInt("Damage"));
                            compound.put("tag", tag);
                            changed++;
                        }
                    }
                }
            }

            changed += migrateFluidName(compound, "FluidName");
            changed += migrateFluidName(compound, "FluidID");
            if (migrateLegacyEnchantments(compound, LEGACY_ENCHANTMENTS)) changed++;
            if (LegacyMigrationApi.migrateRegisteredNbt(compound)) changed++;
            for (String key : new ArrayList<>(compound.getAllKeys())) {
                INBT child = compound.get(key);
                if (child != null) changed += migrateLegacyNbt(child);
            }
        } else if (value instanceof ListNBT) {
            ListNBT list = (ListNBT) value;
            for (int index = 0; index < list.size(); index++) changed += migrateLegacyNbt(list.get(index));
        }
        return changed;
    }

    private static int migrateFluidName(CompoundNBT compound, String key) {
        if (!compound.contains(key, 8)) return 0;
        String oldName = compound.getString(key);
        String target = LegacyMigrationApi.fluidTarget(oldName);
        if (target.equals(normalizeIdentity(oldName))) return 0;
        compound.putString(key, target);
        return 1;
    }

    private static File legacyRegistrySource(File levelDat) {
        for (File candidate : new File[] { levelDat, new File(levelDat.getParentFile(), "level.dat_old") }) {
            if (!candidate.isFile()) continue;
            try (FileInputStream input = new FileInputStream(candidate)) {
                CompoundNBT root = CompressedStreamTools.readCompressed(input);
                if (root.contains("FML", 10) && containsLegacyRegistryEntry(root)) return candidate;
            } catch (IOException ignored) {}
        }
        return null;
    }

    private static boolean containsLegacyRegistryEntry(CompoundNBT root) {
        ListNBT ids = root.getCompound("FML").getCompound("Registries")
                .getCompound(BLOCK_REGISTRY.toString()).getList("ids", 10);
        for (int index = 0; index < ids.size(); index++) {
            ResourceLocation id = safeId(normalizeLegacyRegistryName(ids.getCompound(index).getString("K")));
            if (id != null && LegacyMigrationApi.handlesNamespace(id.getNamespace())) return true;
        }
        return false;
    }

    private static int countLegacyBlocks(CompoundNBT level) {
        int found = 0;
        ListNBT sections = level.getList("Sections", 10);
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            CompoundNBT section = sections.getCompound(sectionIndex);
            byte[] blocks = section.getByteArray("Blocks");
            if (blocks.length != 4096) continue;
            byte[] add = section.getByteArray("Add");
            for (int index = 0; index < blocks.length; index++) {
                int high = add.length == 2048 ? (add[index >> 1] >> ((index & 1) * 4)) & 15 : 0;
                if (LEGACY_BLOCK_IDS.get((blocks[index] & 255) | (high << 8))) found++;
            }
        }
        return found;
    }

    /** Snapshot used by migration probes after spawn chunks have loaded. */
    public static Map<Long, Integer> legacyPreparedBlockCounts() {
        return new LinkedHashMap<>(LEGACY_BLOCK_COUNTS);
    }

    private static int indexLegacyChunks(File worldDirectory) {
        LEGACY_CHUNKS.clear();
        File regionDirectory = new File(worldDirectory, "region");
        File[] regions = regionDirectory.listFiles((directory, name) -> name.matches("r\\.-?\\d+\\.-?\\d+\\.mc[ar]"));
        if (regions == null) return 0;
        byte[] locations = new byte[4096];
        for (File region : regions) {
            String[] parts = region.getName().split("\\.");
            if (parts.length != 4) continue;
            try (InputStream input = Files.newInputStream(region.toPath())) {
                int read = 0;
                while (read < locations.length) {
                    int count = input.read(locations, read, locations.length - read);
                    if (count < 0) break;
                    read += count;
                }
                int regionX = Integer.parseInt(parts[1]);
                int regionZ = Integer.parseInt(parts[2]);
                for (int index = 0; index < read / 4; index++) {
                    int offset = index * 4;
                    if ((locations[offset] | locations[offset + 1] | locations[offset + 2] | locations[offset + 3]) != 0) {
                        LEGACY_CHUNKS.add(Long.valueOf(chunkKey(
                                regionX * 32 + (index & 31), regionZ * 32 + (index >> 5))));
                    }
                }
            } catch (IOException | NumberFormatException exception) {
                PowerAdvantage.LOGGER.warn("Could not inspect legacy chunk locations in '{}'", region, exception);
            }
        }
        return LEGACY_CHUNKS.size();
    }

    @SuppressWarnings("unchecked")
    private static Dynamic<?>[] expandFlatteningTable(int requiredLength) {
        try {
            String fieldName = ObfuscationReflectionHelper.remapName(INameMappingService.Domain.FIELD, "field_199200_b");
            Field valuesField = BlockStateFlatteningMap.class.getDeclaredField(fieldName);
            valuesField.setAccessible(true);
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(valuesField, valuesField.getModifiers() & ~Modifier.FINAL);
            Dynamic<?>[] current = (Dynamic<?>[]) valuesField.get(null);
            if (current.length >= requiredLength) return current;
            Dynamic<?>[] expanded = Arrays.copyOf(current, requiredLength);
            valuesField.set(null, expanded);
            return expanded;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not expand Minecraft's legacy block-state flattening table", exception);
        }
    }

    private static void writeWithBackup(File file, CompoundNBT data) throws IOException {
        File backup = new File(file.getParentFile(), file.getName() + ".poweradvantage-legacy-backup");
        if (!backup.exists()) Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        File temporary = new File(file.getParentFile(), file.getName() + ".poweradvantage.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            CompressedStreamTools.writeCompressed(data, output);
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeMigrationReport(File worldDirectory, int states, int chunks) {
        File report = new File(worldDirectory, "poweradvantage-migration-report.txt");
        if (report.exists()) return;
        List<String> lines = Arrays.asList(
                "Advantage 1.14 legacy migration",
                "Prepared block states: " + states,
                "Protected existing chunks: " + chunks,
                "Namespaces: " + String.join(", ", LegacyMigrationApi.namespaces()),
                "Source files were backed up before any loose NBT mutation.");
        try {
            Files.write(report.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            PowerAdvantage.LOGGER.warn("Could not write migration report '{}'", report, exception);
        }
    }

    private static String normalizeLegacyRegistryName(String name) {
        String normalized = normalizeIdentity(name);
        return normalized.indexOf(':') < 0 ? PowerAdvantage.MODID + ":" + normalized : normalized;
    }

    private static String normalizeIdentity(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static ResourceLocation safeId(String value) {
        try { return value == null || value.isEmpty() ? null : new ResourceLocation(value.toLowerCase(Locale.ROOT)); }
        catch (RuntimeException ignored) { return null; }
    }

    private static long chunkKey(int x, int z) {
        return ((long) x & 0xffffffffL) << 32 | ((long) z & 0xffffffffL);
    }

    private static String worldKey(File directory) {
        try { return directory.getCanonicalPath(); }
        catch (IOException exception) { return directory.getAbsolutePath(); }
    }

    private static Map<String, String> vanillaBlockEntityIds() {
        Map<String, String> ids = new LinkedHashMap<>();
        ids.put("Airportal", "minecraft:end_portal");
        ids.put("Banner", "minecraft:banner");
        ids.put("Beacon", "minecraft:beacon");
        ids.put("Cauldron", "minecraft:brewing_stand");
        ids.put("Chest", "minecraft:chest");
        ids.put("Comparator", "minecraft:comparator");
        ids.put("Control", "minecraft:command_block");
        ids.put("DLDetector", "minecraft:daylight_detector");
        ids.put("Dropper", "minecraft:dropper");
        ids.put("EnchantTable", "minecraft:enchanting_table");
        ids.put("EndGateway", "minecraft:end_gateway");
        ids.put("EnderChest", "minecraft:ender_chest");
        ids.put("FlowerPot", "minecraft:flower_pot");
        ids.put("Furnace", "minecraft:furnace");
        ids.put("Hopper", "minecraft:hopper");
        ids.put("MobSpawner", "minecraft:mob_spawner");
        ids.put("Music", "minecraft:noteblock");
        ids.put("Piston", "minecraft:piston");
        ids.put("RecordPlayer", "minecraft:jukebox");
        ids.put("Sign", "minecraft:sign");
        ids.put("Skull", "minecraft:skull");
        ids.put("Structure", "minecraft:structure_block");
        ids.put("Trap", "minecraft:dispenser");
        return Collections.unmodifiableMap(ids);
    }
}
