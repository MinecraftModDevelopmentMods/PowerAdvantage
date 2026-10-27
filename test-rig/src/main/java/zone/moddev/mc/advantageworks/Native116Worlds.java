package zone.moddev.mc.advantageworks;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import net.minecraft.entity.Entity;
import net.minecraft.util.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;

final class Native116Worlds {
    private Native116Worlds() {}

    static RegistryKey<World> key(int legacyId) {
        switch (legacyId) {
            case -1: return World.NETHER;
            case 0: return World.OVERWORLD;
            case 1: return World.END;
            default: throw new IllegalArgumentException("Unsupported fixture dimension: " + legacyId);
        }
    }

    static int legacyId(RegistryKey<World> key) {
        if (key.equals(World.NETHER)) return -1;
        if (key.equals(World.OVERWORLD)) return 0;
        if (key.equals(World.END)) return 1;
        throw new IllegalArgumentException("Unsupported native fixture dimension: " + key.location());
    }

    static Stream<Entity> entities(ServerWorld world) {
        return StreamSupport.stream(world.getAllEntities().spliterator(), false);
    }
}
