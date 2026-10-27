package zone.moddev.mc.advantageworks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.management.PlayerList;
import net.minecraft.world.storage.PlayerData;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

final class Native116PlayerIo {
    private static final Method SAVE = findSave();
    private static final Field PLAYER_DATA = findPlayerData();

    private Native116PlayerIo() {}

    static void save(PlayerList players, ServerPlayerEntity player) {
        try {
            // Forge skips PlayerList.save for these offline probe players.
            if (player.connection == null) {
                PlayerData data = (PlayerData) PLAYER_DATA.get(players);
                if (data == null) throw new IllegalStateException("Native PlayerData is missing");
                data.save(player);
            } else {
                SAVE.invoke(players, player);
            }
        } catch (IllegalAccessException | InvocationTargetException error) {
            throw new IllegalStateException("Native player save failed", error);
        }
    }

    private static Method findSave() {
        try {
            Method method = PlayerList.class.getDeclaredMethod("save", ServerPlayerEntity.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException officialNameAbsent) {
            return ObfuscationReflectionHelper.findMethod(PlayerList.class, "func_72391_b", ServerPlayerEntity.class);
        }
    }

    private static Field findPlayerData() {
        try {
            Field field = PlayerList.class.getDeclaredField("playerIo");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException officialNameAbsent) {
            return ObfuscationReflectionHelper.findField(PlayerList.class, "field_72412_k");
        }
    }
}
