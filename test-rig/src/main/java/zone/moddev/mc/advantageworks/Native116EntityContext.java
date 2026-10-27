package zone.moddev.mc.advantageworks;

import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.world.server.ServerWorld;

final class Native116EntityContext {
    private Native116EntityContext() {}

    static JsonObject observe(Entity entity, ServerWorld world) {
        if (entity == null || world == null) throw new IllegalStateException("Missing native entity/world context");
        JsonObject worlds = new JsonObject();
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<Entity, Boolean>());
        collect(entity, world, worlds, seen);
        JsonObject result = new JsonObject();
        result.addProperty("worldDimensionKey", entity.level.dimension().location().toString());
        result.addProperty("sameWorld", true);
        result.add("entityWorlds", worlds);
        return result;
    }

    private static void collect(Entity entity, ServerWorld world, JsonObject worlds, Set<Entity> seen) {
        if (entity.level != world) throw new IllegalStateException("Dimension witness belongs to another world instance");
        if (!seen.add(entity)) throw new IllegalStateException("Repeated entity in dimension observation");
        String uuid = entity.getUUID().toString();
        if (worlds.has(uuid)) throw new IllegalStateException("Duplicate UUID in dimension observation");
        worlds.addProperty(uuid, entity.level.dimension().location().toString());
        for (Entity passenger : entity.getPassengers()) collect(passenger, world, worlds, seen);
    }
}
