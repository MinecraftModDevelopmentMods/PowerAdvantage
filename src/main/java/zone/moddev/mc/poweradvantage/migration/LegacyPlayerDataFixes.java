package zone.moddev.mc.poweradvantage.migration;

import com.mojang.datafixers.DataFixer;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTUtil;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.datafix.DefaultTypeReferences;

/** Keeps saved Forge enchantment names across the native numeric player fixers. */
public final class LegacyPlayerDataFixes {
    private LegacyPlayerDataFixes() {}

    public static CompoundNBT update(DataFixer fixer, DefaultTypeReferences type, CompoundNBT source, int version) {
        if (type != DefaultTypeReferences.PLAYER) throw new IllegalArgumentException("Expected native PLAYER type");
        CompoundNBT result = source.copy();
        int current = SharedConstants.getCurrentVersion().getWorldVersion();
        if (version >= current) return result;
        LegacyEntityDataFixes.preparePlayerAttributes(result);
        if (version < 704) {
            // Re-read the native namespaced container schema before later choice fixes.
            result = NBTUtil.update(fixer, type, result, version, 704);
            version = 704;
        }
        result = NBTUtil.update(fixer, type, result, version, current);
        preserveNames(source, result);
        return result;
    }

    private static void preserveNames(INBT original, INBT result) {
        if (original instanceof CompoundNBT && result instanceof CompoundNBT) {
            CompoundNBT before = (CompoundNBT) original, after = (CompoundNBT) result;
            if (isStack(before)) {
                if (!before.get("Count").equals(after.get("Count"))
                        || (before.contains("Slot") != after.contains("Slot"))
                        || (before.contains("Slot") && !before.get("Slot").equals(after.get("Slot")))) {
                    throw new IllegalStateException("Native player migration changed inventory count or slot");
                }
                restoreNamed(before, after, "ench", "Enchantments");
                restoreNamed(before, after, "StoredEnchantments", "StoredEnchantments");
            }
            for (String key : before.getAllKeys()) {
                if (after.contains(key)) preserveNames(before.get(key), after.get(key));
                else if (containsStack(before.get(key))) {
                    throw new IllegalStateException("Native player migration removed an inventory branch");
                }
            }
        } else if (original instanceof ListNBT && result instanceof ListNBT) {
            ListNBT before = (ListNBT) original, after = (ListNBT) result;
            boolean inventory = containsStack(before) || containsStack(after);
            if (inventory && before.size() != after.size()) {
                throw new IllegalStateException("Native player migration changed inventory cardinality");
            }
            for (int index = 0; index < Math.min(before.size(), after.size()); index++) {
                preserveNames(before.get(index), after.get(index));
            }
        } else if (containsStack(original)) {
            throw new IllegalStateException("Native player migration replaced an inventory branch");
        }
    }

    private static boolean isStack(INBT value) {
        return value instanceof CompoundNBT && ((CompoundNBT) value).contains("id")
                && ((CompoundNBT) value).contains("Count", 99);
    }

    private static boolean containsStack(INBT value) {
        if (isStack(value)) return true;
        if (value instanceof CompoundNBT) {
            CompoundNBT compound = (CompoundNBT) value;
            for (String key : compound.getAllKeys()) if (containsStack(compound.get(key))) return true;
        } else if (value instanceof ListNBT) {
            for (INBT child : (ListNBT) value) if (containsStack(child)) return true;
        }
        return false;
    }

    private static void restoreNamed(CompoundNBT source, CompoundNBT target, String oldKey, String newKey) {
        ListNBT before = source.getCompound("tag").getList(oldKey, 10);
        ListNBT after = target.getCompound("tag").getList(newKey, 10);
        for (int index = 0; index < before.size(); index++) {
            if (!before.getCompound(index).contains("id", 8)) continue;
            if (before.size() != after.size()) {
                throw new IllegalStateException("Native player migration changed named enchantment cardinality");
            }
            // Restore only the saved name, never the item's foreign payload or native changes.
            after.getCompound(index).putString("id", before.getCompound(index).getString("id"));
        }
    }
}
