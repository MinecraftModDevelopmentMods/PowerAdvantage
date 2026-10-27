package zone.moddev.mc.advantageworks;

import static org.junit.Assert.*;

import com.mojang.authlib.GameProfile;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.SharedConstants;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.util.registry.Bootstrap;
import net.minecraft.world.storage.PlayerData;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import sun.misc.Unsafe;

public class Native116PlayerIoTest {
    private static final UUID ID = UUID.fromString("a614e658-6270-33c3-9297-7ce80908964f");
    @Rule public TemporaryFolder temporary = new TemporaryFolder() {
        @Override protected void after() {
            if (!Boolean.getBoolean("native116.playerIo.keepEvidence")) super.after();
        }
    };

    @BeforeClass public static void bootstrap() { Bootstrap.bootStrap(); }

    @Test public void realForgePlayerListSaveWithoutConnectionLeavesLegacyBytesUnchanged() throws Exception {
        for (int version : new int[] {512, 1343}) {
            File directory = temporary.newFolder("guard-" + version);
            File file = new File(directory, ID + ".dat");
            CompoundNBT legacy = legacy(version);
            CompressedStreamTools.writeCompressed(legacy, file);
            byte[] before = Files.readAllBytes(file.toPath());
            PlayerList players = players(data(directory), false);
            Method save = PlayerList.class.getDeclaredMethod("save", ServerPlayerEntity.class);
            save.setAccessible(true);
            save.invoke(players, player(1, 0));
            assertArrayEquals(before, Files.readAllBytes(file.toPath()));
            assertEquals(legacy, CompressedStreamTools.readCompressed(file));
        }
    }

    @Test public void offlineAdapterReplacesLegacyFileThroughNativePlayerData() throws Exception {
        for (int version : new int[] {512, 1343}) {
            File directory = temporary.newFolder("save-" + version);
            File file = new File(directory, ID + ".dat");
            CompoundNBT legacy = legacy(version);
            CompressedStreamTools.writeCompressed(legacy, file);
            byte[] before = Files.readAllBytes(file.toPath());
            HeadlessPlayer player = player(1, 0);
            Native116PlayerIo.save(players(data(directory), false), player);
            assertFalse(java.util.Arrays.equals(before, Files.readAllBytes(file.toPath())));
            assertSaved(CompressedStreamTools.readCompressed(file), player, 1, 0);
            assertEquals(legacy, CompressedStreamTools.readCompressed(new File(directory, ID + ".dat_old")));
        }
    }

    @Test public void freshNativePlayerDataAndPlayerReloadTheSavedInventoryWithoutDrift() throws Exception {
        File directory = temporary.newFolder("reload");
        HeadlessPlayer first = player(1, 0);
        Native116PlayerIo.save(players(data(directory), false), first);
        HeadlessPlayer second = player(9, 8);
        PlayerData reopened = data(directory);
        CompoundNBT saved = reopened.load(second);
        assertNotNull(saved);
        assertSaved(saved, second, 1, 0);
        Native116PlayerIo.save(players(reopened, false), second);
        assertEquals(saved, CompressedStreamTools.readCompressed(new File(directory, ID + ".dat")));
    }

    @Test public void nativeSavePreservesExactCountSlotAndAllNamedStoredEnchantments() throws Exception {
        File directory = temporary.newFolder("count-slot");
        HeadlessPlayer player = player(3, 8);
        Native116PlayerIo.save(players(data(directory), false), player);
        assertSaved(CompressedStreamTools.readCompressed(new File(directory, ID + ".dat")), player, 3, 8);
    }

    @Test public void connectedPlayersKeepThePlayerListSavePath() throws Exception {
        File directory = temporary.newFolder("connected");
        HeadlessPlayer player = player(1, 0);
        player.connection = allocate(ServerPlayNetHandler.class);
        CountingPlayerList players = (CountingPlayerList) players(data(directory), true);
        Native116PlayerIo.save(players, player);
        assertEquals(1, players.calls);
        assertFalse(new File(directory, ID + ".dat").exists());
    }

    @Test public void missingNativeStorageFailsInsteadOfPretendingToSave() throws Exception {
        try {
            Native116PlayerIo.save(players(null, false), player(1, 0));
            fail("missing PlayerData accepted");
        } catch (IllegalStateException expected) {
            assertEquals("Native PlayerData is missing", expected.getMessage());
        }
    }

    private static void assertSaved(CompoundNBT saved, HeadlessPlayer player, int count, int slot) throws Exception {
        assertEquals(SharedConstants.getCurrentVersion().getWorldVersion(), saved.getInt("DataVersion"));
        assertEquals(ID, saved.getUUID("UUID"));
        assertFalse(saved.contains("UUIDMost"));
        assertFalse(saved.contains("UUIDLeast"));
        ListNBT inventory = saved.getList("Inventory", 10);
        assertEquals(1, inventory.size());
        CompoundNBT stack = inventory.getCompound(0);
        assertEquals("minecraft:enchanted_book", stack.getString("id"));
        assertEquals(count, stack.getByte("Count"));
        assertEquals(slot, stack.getByte("Slot"));
        assertFalse(stack.contains("Damage"));
        assertEquals(book(count).getCompound("tag"), stack.getCompound("tag"));
        assertEquals(5, stack.getCompound("tag").getList("StoredEnchantments", 10).size());
        assertTrue(ItemStack.matches(ItemStack.of(stack), player.items.getItem(slot)));
        assertEquals(inventory, player.items.save(new ListNBT()));
    }

    private static CompoundNBT legacy(int version) throws Exception {
        CompoundNBT root = new CompoundNBT();
        root.putInt("DataVersion", version);
        root.putLong("UUIDMost", ID.getMostSignificantBits());
        root.putLong("UUIDLeast", ID.getLeastSignificantBits());
        CompoundNBT stack = book(1);
        stack.putShort("Damage", (short) 0);
        stack.putByte("Slot", (byte) 0);
        ListNBT inventory = new ListNBT();
        inventory.add(stack);
        root.put("Inventory", inventory);
        return root;
    }

    private static CompoundNBT book(int count) throws Exception {
        CompoundNBT stack = JsonToNBT.parseTag("{id:\"minecraft:enchanted_book\",Count:1b,tag:{StoredEnchantments:["
                + "{id:\"minecraft:efficiency\",lvl:3s},{id:\"steamadvantage:high_explosive\",lvl:1s},"
                + "{id:\"steamadvantage:powderless\",lvl:1s},{id:\"steamadvantage:rapid_reload\",lvl:1s},"
                + "{id:\"steamadvantage:recoil\",lvl:1s}]}}");
        stack.putByte("Count", (byte) count);
        return stack;
    }

    private static HeadlessPlayer player(int count, int slot) throws Exception {
        HeadlessPlayer player = allocate(HeadlessPlayer.class);
        player.setUUID(ID);
        player.items = new PlayerInventory(player);
        player.items.setItem(slot, ItemStack.of(book(count)));
        return player;
    }

    private static PlayerData data(File directory) throws Exception {
        PlayerData data = allocate(PlayerData.class);
        set(PlayerData.class, data, "playerDir", directory);
        set(PlayerData.class, data, "fixerUpper", DataFixesManager.getDataFixer());
        return data;
    }

    private static PlayerList players(PlayerData data, boolean counting) throws Exception {
        PlayerList players = counting ? allocate(CountingPlayerList.class) : allocate(HeadlessPlayerList.class);
        set(PlayerList.class, players, "playerIo", data);
        return players;
    }

    private static void set(Class<?> type, Object owner, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(owner, value);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    // Only entity construction/serialization is a fixture. PlayerList's guard,
    // PlayerData save/load, compressed files, DFU and ItemStacks are native.
    private static final class HeadlessPlayer extends ServerPlayerEntity {
        PlayerInventory items;
        private HeadlessPlayer() { super(null, null, new GameProfile(ID, "Headless"), null); }
        @Override public CompoundNBT saveWithoutId(CompoundNBT root) {
            root.putInt("DataVersion", SharedConstants.getCurrentVersion().getWorldVersion());
            root.putUUID("UUID", getUUID());
            root.put("Inventory", items.save(new ListNBT()));
            return root;
        }
        @Override public void load(CompoundNBT root) {
            setUUID(root.getUUID("UUID"));
            items.load(root.getList("Inventory", 10));
        }
    }

    private static class HeadlessPlayerList extends PlayerList {
        private HeadlessPlayerList() { super(null, null, null, 0); }
    }

    private static final class CountingPlayerList extends HeadlessPlayerList {
        int calls;
        @Override protected void save(ServerPlayerEntity player) { calls++; }
    }
}
