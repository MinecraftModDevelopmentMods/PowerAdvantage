package zone.moddev.mc.advantageworks;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.management.PlayerList;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

final class Native116PlayerIo {
    private static final Method SAVE = ObfuscationReflectionHelper.findMethod(
            PlayerList.class, "func_72391_b", ServerPlayerEntity.class);

    private Native116PlayerIo() {}

    static void save(PlayerList players, ServerPlayerEntity player) {
        try {
            SAVE.invoke(players, player);
        } catch (IllegalAccessException | InvocationTargetException error) {
            throw new IllegalStateException("Native PlayerList.save failed", error);
        }
    }
}
