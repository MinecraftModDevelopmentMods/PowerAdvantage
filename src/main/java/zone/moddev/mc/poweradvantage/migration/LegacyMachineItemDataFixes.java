package zone.moddev.mc.poweradvantage.migration;

import cyano.poweradvantage.api.migration.LegacyMigrationApi;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.util.datafix.TypeReferences;

/** Vanilla cannot traverse inventories behind unknown mod block-entity choices. */
final class LegacyMachineItemDataFixes {
    private static final String PREPARED = "AdvantageLegacyMachineItemDataFix";

    private LegacyMachineItemDataFixes() {}

    static void prepare(CompoundNBT root) {
        int version = root.contains("DataVersion", 99) ? root.getInt("DataVersion") : -1;
        int current = SharedConstants.getCurrentVersion().getWorldVersion();
        if (version >= current || root.getBoolean(PREPARED) || !root.contains("Level", 10)) return;
        ListNBT tiles = root.getCompound("Level").getList("TileEntities", 10);
        for (int index = 0; index < tiles.size(); index++) {
            CompoundNBT tile = tiles.getCompound(index);
            ResourceLocation id = ResourceLocation.tryParse(tile.getString("id"));
            if (id != null && LegacyMigrationApi.handlesNamespace(id.getNamespace())) {
                rewrite(tile, version, current);
            }
        }
        root.putBoolean(PREPARED, true);
    }

    private static INBT rewrite(INBT value, int version, int current) {
        if (value instanceof CompoundNBT) {
            CompoundNBT compound = (CompoundNBT) value;
            ResourceLocation id = ResourceLocation.tryParse(compound.getString("id"));
            if (compound.contains("Count", 99) && id != null) {
                if (supported(id)) {
                    DataFixer fixer = DataFixesManager.getDataFixer();
                    CompoundNBT input = version < 704 ? upgradeTo704(compound, version, fixer) : compound.copy();
                    CompoundNBT fixed = (CompoundNBT) fixer.update(TypeReferences.ITEM_STACK,
                            new Dynamic<>(NBTDynamicOps.INSTANCE, input), Math.max(version, 704), current).getValue();
                    // The native item schema also fixes its nested vanilla inventories.
                    // Do not visit them again with the old version and reset tool damage.
                    return preserve(compound, fixed);
                }
                return compound;
            }
            for (String key : new ArrayList<>(compound.getAllKeys())) {
                compound.put(key, rewrite(compound.get(key), version, current));
            }
        } else if (value instanceof ListNBT) {
            ListNBT list = (ListNBT) value;
            for (int index = 0; index < list.size(); index++) list.set(index, rewrite(list.get(index), version, current));
        }
        return value;
    }

    static CompoundNBT upgradeTo704(CompoundNBT source, int version, DataFixer fixer) {
        // A root ITEM_STACK hits the unchecked 704 hook conversion too early.
        // Native PLAYER.Inventory lets the nested block-entity choice rename run.
        CompoundNBT player = new CompoundNBT();
        ListNBT inventory = new ListNBT();
        inventory.add(source.copy());
        player.put("Inventory", inventory);
        Object converted = fixer.update(TypeReferences.PLAYER, new Dynamic<>(NBTDynamicOps.INSTANCE, player),
                version, 704).getValue();
        if (!(converted instanceof CompoundNBT)) {
            throw new IllegalStateException("Native item envelope is not a player compound");
        }
        INBT items = ((CompoundNBT) converted).get("Inventory");
        if (!(items instanceof ListNBT) || ((ListNBT) items).size() != 1 || ((ListNBT) items).getElementType() != 10) {
            throw new IllegalStateException("Native item envelope changed singleton inventory cardinality or type");
        }
        CompoundNBT result = ((ListNBT) items).getCompound(0);
        ResourceLocation id = ResourceLocation.tryParse(result.getString("id"));
        if (!result.contains("id", 8) || id == null || id.getPath().isEmpty() || !result.contains("Count", 99)
                || !result.get("Count").equals(source.get("Count"))
                || !Objects.equals(result.get("Slot"), source.get("Slot"))) {
            throw new IllegalStateException("Native item envelope changed item shape, count or slot");
        }
        return result;
    }

    private static boolean supported(ResourceLocation id) {
        return id.getNamespace().equals("minecraft") || id.getNamespace().equals("basemetals")
                || id.getNamespace().equals("mmdlib") || LegacyMigrationApi.handlesNamespace(id.getNamespace());
    }

    private static INBT preserve(INBT original, INBT result) {
        if (original instanceof CompoundNBT && result instanceof CompoundNBT) {
            CompoundNBT before = (CompoundNBT) original, after = (CompoundNBT) result;
            ResourceLocation id = ResourceLocation.tryParse(before.getString("id"));
            if (before.contains("Count", 99) && id != null) {
                if (!after.contains("Count", 99) || before.getInt("Count") != after.getInt("Count")
                        || (before.contains("Slot", 99) && (!after.contains("Slot", 99)
                        || before.getInt("Slot") != after.getInt("Slot")))) {
                    throw new IllegalStateException("Native item migration changed inventory count or slot");
                }
                if (!supported(id)) return before.copy();
                restoreNamedEnchantments(before, after, "ench", "Enchantments");
                restoreNamedEnchantments(before, after, "StoredEnchantments", "StoredEnchantments");
            }
            for (String key : new ArrayList<>(before.getAllKeys())) {
                if (after.contains(key)) after.put(key, preserve(before.get(key), after.get(key)));
            }
        } else if (original instanceof ListNBT && result instanceof ListNBT) {
            ListNBT before = (ListNBT) original, after = (ListNBT) result;
            boolean inventory = before.stream().anyMatch(value -> value instanceof CompoundNBT
                    && ((CompoundNBT) value).contains("Count", 99) && ((CompoundNBT) value).contains("id", 8));
            if (inventory && before.size() != after.size()) {
                throw new IllegalStateException("Native item migration changed inventory cardinality");
            }
            for (int index = 0; index < Math.min(before.size(), after.size()); index++) {
                after.set(index, preserve(before.get(index), after.get(index)));
            }
        }
        return result;
    }

    private static void restoreNamedEnchantments(CompoundNBT source, CompoundNBT target, String oldKey, String newKey) {
        ListNBT before = source.getCompound("tag").getList(oldKey, 10);
        ListNBT after = target.getCompound("tag").getList(newKey, 10);
        for (int index = 0; index < before.size(); index++) {
            if (!before.getCompound(index).contains("id", 8)) continue;
            if (after.size() != before.size()) {
                throw new IllegalStateException("Native item migration changed a named enchantment list's size");
            }
            // The saved Forge registry has already translated these names. Vanilla's
            // numeric enchantment fixer must not reinterpret them as protection.
            after.set(index, before.getCompound(index).copy());
        }
    }

    static void finish(CompoundNBT root) {
        if (root != null) root.remove(PREPARED);
    }
}
