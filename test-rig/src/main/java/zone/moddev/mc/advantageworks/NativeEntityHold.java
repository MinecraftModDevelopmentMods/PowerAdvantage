package zone.moddev.mc.advantageworks;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.entity.Entity;

/** Holds native entity ticks without removing actors from the world's tracking indexes. */
final class NativeEntityHold {
    private final Map<Entity, State> held = new IdentityHashMap<>();

    void hold(Entity entity) {
        if (!held.containsKey(entity)) held.put(entity, new State(entity));
        entity.canUpdate(false);
    }

    void restore() {
        for (Map.Entry<Entity, State> entry : held.entrySet()) {
            Entity entity = entry.getKey();
            State state = entry.getValue();
            // ServerWorld increments this even when canUpdate() is false.
            entity.ticksExisted = state.ticks;
            entity.canUpdate(state.updating);
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
