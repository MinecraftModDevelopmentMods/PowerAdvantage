package zone.moddev.mc.advantageworks;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.entity.Entity;

/** Holds native entity ticks without removing actors from chunk or entity-ID indexes. */
final class NativeEntityHold {
    private final Map<Entity, State> held = new IdentityHashMap<>();

    void hold(Entity entity) {
        if (!held.containsKey(entity)) held.put(entity, new State(entity));
        entity.canUpdate(false);
        // Forge 25 only consults canUpdate for weather entities and passengers.
        // Ordinary actors must leave the global tick list while remaining queryable in their chunk.
        entity.getEntityWorld().loadedEntityList.remove(entity);
    }

    void restore() {
        for (Map.Entry<Entity, State> entry : held.entrySet()) {
            Entity entity = entry.getKey();
            State state = entry.getValue();
            if (entity.ticksExisted != state.ticks)
                throw new IllegalStateException("A frozen fixture actor received an unexpected entity tick");
            entity.canUpdate(state.updating);
            if (!entity.removed && !entity.getEntityWorld().loadedEntityList.contains(entity))
                entity.getEntityWorld().loadedEntityList.add(entity);
        }
        held.clear();
    }

    private static final class State {
        final boolean updating;
        final int ticks;

        State(Entity entity) {
            updating = entity.canUpdate();
            ticks = entity.ticksExisted;
        }
    }
}
