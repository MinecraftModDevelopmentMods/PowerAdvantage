package zone.moddev.mc.poweradvantage.migration;

import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import org.junit.Test;
import static org.junit.Assert.*;

public class FlattenedBiomeSnapshotTest {
    @Test public void preservesEveryNumericIdAndUnknownModEntry() {
        CompoundNBT root = root(1631);
        CompoundNBT original = registries(root).getCompound("minecraft:biomes").copy();
        assertTrue(LegacyWorldDataHook.renameFlattenedBiomeRegistry(root));
        assertEquals(original, registries(root).getCompound("minecraft:worldgen/biome"));
        assertFalse(registries(root).contains("minecraft:biomes"));
    }

    @Test public void isIdempotent() {
        CompoundNBT root = root(2230);
        assertTrue(LegacyWorldDataHook.renameFlattenedBiomeRegistry(root));
        CompoundNBT once = root.copy();
        assertFalse(LegacyWorldDataHook.renameFlattenedBiomeRegistry(root));
        assertEquals(once, root);
    }

    @Test public void neverReinterpretsAPreFlatteningSnapshot() {
        CompoundNBT root = root(1343), before = root.copy();
        assertFalse(LegacyWorldDataHook.renameFlattenedBiomeRegistry(root));
        assertEquals(before, root);
    }

    @Test public void leavesTheUppercaseLegacyArchiveUntouched() {
        CompoundNBT root = root(1631);
        CompoundNBT archive = new CompoundNBT();
        archive.put("Registries", registries(root).copy());
        root.put("FML", archive);
        CompoundNBT before = archive.copy();
        assertTrue(LegacyWorldDataHook.renameFlattenedBiomeRegistry(root));
        assertEquals(before, root.getCompound("FML"));
    }

    @Test public void refusesConflictingSnapshotsWithoutChangingEither() {
        CompoundNBT root = root(2230);
        registries(root).put("minecraft:worldgen/biome", new CompoundNBT());
        CompoundNBT before = root.copy();
        try {
            LegacyWorldDataHook.renameFlattenedBiomeRegistry(root);
            fail("A conflicting registry must not be merged or discarded");
        } catch (IllegalStateException expected) {
            assertEquals(before, root);
        }
    }

    @Test public void coalescesOnlyAnIdenticalDuplicate() {
        CompoundNBT root = root(2230);
        registries(root).put("minecraft:worldgen/biome", registries(root).getCompound("minecraft:biomes").copy());
        assertTrue(LegacyWorldDataHook.renameFlattenedBiomeRegistry(root));
        assertFalse(registries(root).contains("minecraft:biomes"));
    }

    private static CompoundNBT root(int version) {
        CompoundNBT root = new CompoundNBT(), data = new CompoundNBT(), fml = new CompoundNBT();
        CompoundNBT registries = new CompoundNBT(), snapshot = new CompoundNBT();
        data.putInt("DataVersion", version);
        root.put("Data", data);
        root.put("fml", fml);
        fml.put("Registries", registries);
        registries.put("minecraft:biomes", snapshot);
        ListNBT ids = new ListNBT();
        for (String name : new String[] {"minecraft:nether", "othermod:marsh"}) {
            CompoundNBT entry = new CompoundNBT();
            entry.putString("K", name);
            entry.putInt("V", name.startsWith("minecraft:") ? 8 : 200);
            ids.add(entry);
        }
        snapshot.put("ids", ids);
        snapshot.putIntArray("blocked", new int[] {77});
        ListNBT aliases = new ListNBT();
        CompoundNBT alias = new CompoundNBT();
        alias.putString("K", "othermod:old_marsh");
        alias.putString("V", "othermod:marsh");
        aliases.add(alias);
        snapshot.put("aliases", aliases);
        return root;
    }

    private static CompoundNBT registries(CompoundNBT root) {
        return root.getCompound("fml").getCompound("Registries");
    }
}
