package cyano.poweradvantage.api.migration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

/**
 * Registration surface for Advantage add-ons that need pre-flattening world
 * migration. PowerAdvantage owns the hook; add-ons only describe their legacy
 * identities and metadata codecs.
 */
public final class LegacyMigrationApi {
    @FunctionalInterface
    public interface BlockStateCodec {
        IBlockState decode(Block currentBlock, int legacyMetadata);
    }

    @FunctionalInterface
    public interface NbtMigration {
        boolean migrate(NBTTagCompound compound);
    }

    private static final Set<String> NAMESPACES = new LinkedHashSet<>();
    private static final Map<ResourceLocation, ResourceLocation> BLOCK_ALIASES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> ITEM_ALIASES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, BlockStateCodec> BLOCK_CODECS = new LinkedHashMap<>();
    private static final Map<String, String> TILE_ALIASES = new LinkedHashMap<>();
    private static final Map<String, String> ENTITY_ALIASES = new LinkedHashMap<>();
    private static final Map<String, String> FLUID_ALIASES = new LinkedHashMap<>();
    private static final Map<String, ResourceLocation> FLUID_BUCKETS = new LinkedHashMap<>();
    private static final Set<ResourceLocation> DURABLE_ITEMS = new LinkedHashSet<>();
    private static final Map<String, NbtMigration> NBT_MIGRATIONS = new LinkedHashMap<>();

    private LegacyMigrationApi() {}

    public static synchronized void registerNamespace(String namespace) {
        NAMESPACES.add(normalize(namespace));
    }

    public static synchronized void registerBlockAlias(ResourceLocation legacyId, ResourceLocation currentId) {
        BLOCK_ALIASES.put(legacyId, currentId);
        registerNamespace(legacyId.getNamespace());
    }

    public static synchronized void registerItemAlias(ResourceLocation legacyId, ResourceLocation currentId) {
        ITEM_ALIASES.put(legacyId, currentId);
        registerNamespace(legacyId.getNamespace());
    }

    public static synchronized void registerBlockStateCodec(ResourceLocation legacyId, BlockStateCodec codec) {
        if (codec == null) throw new NullPointerException("codec");
        BLOCK_CODECS.put(legacyId, codec);
        registerNamespace(legacyId.getNamespace());
    }

    public static synchronized void registerTileEntityAlias(String legacyId, String currentId) {
        TILE_ALIASES.put(normalizeIdentity(legacyId), normalizeIdentity(currentId));
    }

    public static synchronized void registerEntityAlias(String legacyId, String currentId) {
        ENTITY_ALIASES.put(normalizeIdentity(legacyId), normalizeIdentity(currentId));
    }

    public static synchronized void registerFluidAlias(String legacyName, String currentName) {
        FLUID_ALIASES.put(normalizeIdentity(legacyName), normalizeIdentity(currentName));
    }

    public static synchronized void registerLegacyFluidBucket(String legacyFluidName, ResourceLocation bucketId) {
        FLUID_BUCKETS.put(normalizeIdentity(legacyFluidName), bucketId);
    }

    public static synchronized void registerDurableItem(ResourceLocation itemId) {
        DURABLE_ITEMS.add(itemId);
    }

    public static synchronized void registerNbtMigration(String id, NbtMigration migration) {
        if (migration == null) throw new NullPointerException("migration");
        NBT_MIGRATIONS.put(normalizeIdentity(id), migration);
    }

    public static synchronized boolean handlesNamespace(String namespace) {
        return NAMESPACES.contains(normalize(namespace));
    }

    public static synchronized ResourceLocation blockTarget(ResourceLocation legacyId) {
        ResourceLocation target = BLOCK_ALIASES.get(legacyId);
        return target == null ? legacyId : target;
    }

    public static synchronized ResourceLocation itemTarget(ResourceLocation legacyId) {
        ResourceLocation target = ITEM_ALIASES.get(legacyId);
        return target == null ? legacyId : target;
    }

    public static synchronized IBlockState decodeBlockState(ResourceLocation legacyId, Block block, int metadata) {
        BlockStateCodec codec = BLOCK_CODECS.get(legacyId);
        return codec == null ? block.getDefaultState() : codec.decode(block, metadata & 15);
    }

    public static synchronized String tileTarget(String legacyId) {
        return alias(TILE_ALIASES, legacyId);
    }

    public static synchronized String entityTarget(String legacyId) {
        return alias(ENTITY_ALIASES, legacyId);
    }

    public static synchronized String fluidTarget(String legacyName) {
        return alias(FLUID_ALIASES, legacyName);
    }

    public static synchronized ResourceLocation bucketTarget(String legacyFluidName) {
        String normalized = normalizeIdentity(legacyFluidName);
        ResourceLocation direct = FLUID_BUCKETS.get(normalized);
        if (direct != null) return direct;
        String fluidTarget = alias(FLUID_ALIASES, normalized);
        return FLUID_BUCKETS.get(fluidTarget);
    }

    public static synchronized boolean isDurableItem(ResourceLocation id) {
        return DURABLE_ITEMS.contains(id) || DURABLE_ITEMS.contains(itemTarget(id));
    }

    public static synchronized boolean migrateRegisteredNbt(NBTTagCompound compound) {
        boolean changed = false;
        for (NbtMigration migration : NBT_MIGRATIONS.values()) changed |= migration.migrate(compound);
        return changed;
    }

    public static synchronized Set<String> namespaces() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(NAMESPACES));
    }

    public static synchronized Map<ResourceLocation, BlockStateCodec> blockStateCodecs() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(BLOCK_CODECS));
    }

    private static String alias(Map<String, String> aliases, String value) {
        String normalized = normalizeLookup(value);
        if (normalized.isEmpty()) return normalized;
        String target = aliases.get(normalized);
        return target == null ? normalized : target;
    }

    private static String normalize(String value) {
        if (value == null) throw new NullPointerException("value");
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) throw new IllegalArgumentException("Identity cannot be empty");
        return normalized;
    }

    private static String normalizeIdentity(String value) {
        return normalize(value);
    }

    private static String normalizeLookup(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
