package zone.moddev.mc.poweradvantage.migration;

import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.util.datafix.TypeReferences;
import net.minecraft.util.datafix.fixes.EntityUUID;

/** Isolates vanilla entity choices from mod choices during the chunk datafix. */
final class LegacyEntityDataFixes {
    private static final String STAGED = "AdvantageLegacyEntityDataFix";

    private LegacyEntityDataFixes() {}

    static void prepare(CompoundNBT root) {
        if (root.contains(STAGED)) return;
        int version = root.contains("DataVersion", 99) ? root.getInt("DataVersion") : -1;
        int current = SharedConstants.getCurrentVersion().getWorldVersion();
        if (version >= current || !root.contains("Level", 10)) return;
        CompoundNBT level = root.getCompound("Level");
        if (!level.contains("Entities")) return;
        requireCompoundList(level, "Entities");
        ListNBT entities = level.getList("Entities", 10);
        if (entities.isEmpty()) return;
        DataFixer fixer = DataFixesManager.getDataFixer();
        Set<?> vanillaIds = fixer.getSchema(DataFixUtils.makeKey(version))
                .findChoiceType(TypeReferences.ENTITY).types().keySet();
        ListNBT fixed = new ListNBT();
        for (int index = 0; index < entities.size(); index++) {
            fixed.add(upgrade(entities.getCompound(index), version, current, fixer, vanillaIds));
        }
        // Keep these out of vanilla's mixed-choice traversal and avoid fixing them twice.
        root.put(STAGED, fixed);
        level.put("Entities", new ListNBT());
    }

    private static CompoundNBT upgrade(CompoundNBT source, int version, int current,
            DataFixer fixer, Set<?> vanillaIds) {
        CompoundNBT entity = source.copy();
        boolean hasPassengers = source.contains("Passengers");
        ListNBT passengers = source.getList("Passengers", 10);
        entity.remove("Passengers");
        Dynamic<?> dynamic = new Dynamic<>(NBTDynamicOps.INSTANCE, entity);
        if (vanillaIds.contains(entity.getString("id"))) {
            entity = (CompoundNBT) fixer.update(TypeReferences.ENTITY, dynamic, version, current).getValue();
        } else {
            // All entities inherit this root UUID format, including unknown mod types.
            entity = (CompoundNBT) EntityUUID.updateEntityUUID(dynamic).getValue();
        }
        // Vanilla's UUID fixer rejects zero halves, although they are valid UUID bits.
        if (source.hasUUID("UUID")) {
            entity.putUUID("UUID", source.getUUID("UUID"));
        } else if (source.contains("UUIDMost", 99) && source.contains("UUIDLeast", 99)) {
            entity.putUUID("UUID", new UUID(source.getLong("UUIDMost"), source.getLong("UUIDLeast")));
        }
        if (entity.hasUUID("UUID")) {
            entity.remove("UUIDMost");
            entity.remove("UUIDLeast");
        }
        if (hasPassengers) {
            requireCompoundList(source, "Passengers");
            ListNBT fixed = new ListNBT();
            for (int index = 0; index < passengers.size(); index++) {
                fixed.add(upgrade(passengers.getCompound(index), version, current, fixer, vanillaIds));
            }
            entity.put("Passengers", fixed);
        }
        return entity;
    }

    static void finish(CompoundNBT root) {
        if (root == null || !root.contains(STAGED)) return;
        if (!root.contains(STAGED, 9) || !root.contains("Level", 10)) {
            throw new IllegalStateException("Malformed staged Advantage entity migration");
        }
        requireCompoundList(root, STAGED);
        CompoundNBT level = root.getCompound("Level");
        if (level.contains("Entities")) requireCompoundList(level, "Entities");
        if (!level.getList("Entities", 10).isEmpty()) {
            throw new IllegalStateException("Entity data appeared during isolated Advantage migration");
        }
        level.put("Entities", root.getList(STAGED, 10));
        root.remove(STAGED);
    }

    private static void requireCompoundList(CompoundNBT owner, String key) {
        if (!owner.contains(key, 9)) throw new IllegalStateException("Malformed entity list: " + key);
        ListNBT list = (ListNBT) owner.get(key);
        if (!list.isEmpty() && list.getElementType() != 10) {
            throw new IllegalStateException("Non-compound entity list: " + key);
        }
    }
}
