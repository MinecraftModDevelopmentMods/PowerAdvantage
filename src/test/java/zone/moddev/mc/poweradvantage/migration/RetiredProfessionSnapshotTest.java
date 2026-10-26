package zone.moddev.mc.poweradvantage.migration;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import org.junit.Test;
import static org.junit.Assert.*;

public class RetiredProfessionSnapshotTest {
    @Test public void removesOnlyRetiredVanillaIndexAndIsIdempotent() {
        for (String namespace : new String[] {"FML", "fml"}) {
            CompoundNBT root = world(namespace, "minecraft:farmer");
            CompoundNBT player = new CompoundNBT();
            player.putInt("XpLevel", 34);
            root.put("Player", player);
            assertTrue(LegacyWorldDataHook.removeRetiredVanillaProfessionRegistry(root));
            assertFalse(root.getCompound(namespace).getCompound("Registries").contains("minecraft:villagerprofessions"));
            assertEquals(player, root.getCompound("Player"));
            assertFalse(LegacyWorldDataHook.removeRetiredVanillaProfessionRegistry(root));
        }
    }

    @Test public void preservesCustomProfessionIndexInsteadOfHidingMissingMappings() {
        CompoundNBT root = world("fml", "othermod:machinist");
        CompoundNBT before = root.copy();
        assertFalse(LegacyWorldDataHook.removeRetiredVanillaProfessionRegistry(root));
        assertEquals(before, root);
    }

    private static CompoundNBT world(String namespace, String profession) {
        CompoundNBT entry = new CompoundNBT();
        entry.putString("K", profession);
        entry.putInt("V", 1);
        ListNBT ids = new ListNBT();
        ids.add(entry);
        CompoundNBT snapshot = new CompoundNBT();
        snapshot.put("ids", ids);
        CompoundNBT registries = new CompoundNBT();
        registries.put("minecraft:villagerprofessions", snapshot);
        CompoundNBT fml = new CompoundNBT();
        fml.put("Registries", registries);
        CompoundNBT root = new CompoundNBT();
        root.put(namespace, fml);
        return root;
    }
}
