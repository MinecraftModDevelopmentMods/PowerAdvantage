package zone.moddev.mc.advantageworks;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraft.world.server.ServerWorld;
import org.junit.Test;
import static org.junit.Assert.*;

public class Native116EngineSignaturesTest {
    @Test public void nativeChunkRegistryAndTileSignaturesAreExact() throws Exception {
        assertEquals(Chunk.class, ServerChunkProvider.class.getMethod("getChunkNow", int.class, int.class).getReturnType());
        assertEquals(ServerWorld.class, MinecraftServer.class.getMethod("getLevel", RegistryKey.class).getReturnType());
        assertEquals(void.class, TileEntity.class.getMethod("load", BlockState.class, CompoundNBT.class).getReturnType());
        assertEquals(CompoundNBT.class, TileEntity.class.getMethod("save", CompoundNBT.class).getReturnType());
    }

    @Test public void nativePlayerSaveRemainsProtectedAndRespawnUsesRegistryKeys() throws Exception {
        Method save = PlayerList.class.getDeclaredMethod("save", ServerPlayerEntity.class);
        assertTrue(Modifier.isProtected(save.getModifiers()));
        assertEquals(RegistryKey.class, ServerPlayerEntity.class.getMethod("getRespawnDimension").getReturnType());
        assertEquals(void.class, ServerPlayerEntity.class.getMethod("setRespawnPosition", RegistryKey.class,
                net.minecraft.util.math.BlockPos.class, float.class, boolean.class, boolean.class).getReturnType());
    }

    @Test public void realNativeBitStoragePadsFiveSevenAndThirteenBitValues() throws Exception {
        Class<?> storage = Class.forName("net.minecraft.util.BitArray");
        for (int bits : new int[] {5, 7, 13}) {
            Object instance = storage.getConstructor(int.class, int.class).newInstance(bits, 4096);
            int perLong = 64 / bits;
            storage.getMethod("set", int.class, int.class).invoke(instance, perLong - 1, 3);
            storage.getMethod("set", int.class, int.class).invoke(instance, perLong, 17);
            long[] raw = (long[]) storage.getMethod("getRaw").invoke(instance);
            assertEquals((4096 + perLong - 1) / perLong, raw.length);
            assertEquals(3L << ((perLong - 1) * bits), raw[0]);
            assertEquals(17L, raw[1]);
            assertEquals(17, storage.getMethod("get", int.class).invoke(instance, perLong));
        }
    }
}
