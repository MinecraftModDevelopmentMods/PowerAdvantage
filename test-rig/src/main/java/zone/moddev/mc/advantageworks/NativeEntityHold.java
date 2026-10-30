package zone.moddev.mc.advantageworks;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.entity.Entity;

/** Holds native entity ticks without removing actors from chunk or entity-ID indexes. */
final class NativeEntityHold {
    private final Map<Entity, State> held = new IdentityHashMap<>();

    void hold(Entity entity) {
        if (!held.containsKey(entity)) held.put(entity, new State(entity));
        // Keep actors queryable in chunk and entity-ID indexes, but out of the ordinary tick list.
        entity.getEntityWorld().loadedEntityList.remove(entity);
    }

    void restore() {
        for (Map.Entry<Entity, State> entry : held.entrySet()) {
            Entity entity = entry.getKey();
            State state = entry.getValue();
            if (entity.ticksExisted != state.ticks)
                throw new IllegalStateException("A frozen fixture actor received an unexpected entity tick");
            if (!entity.isDead && !entity.getEntityWorld().loadedEntityList.contains(entity))
                entity.getEntityWorld().loadedEntityList.add(entity);
        }
        held.clear();
    }

    private static final class State {
        final int ticks;

        State(Entity entity) {
            ticks = entity.ticksExisted;
        }
    }
}
