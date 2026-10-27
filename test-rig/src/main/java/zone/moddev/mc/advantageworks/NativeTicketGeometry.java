package zone.moddev.mc.advantageworks;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.server.ChunkHolder;
import net.minecraft.world.server.ChunkManager;
import net.minecraft.world.server.Ticket;
import net.minecraft.world.server.TicketManager;
import net.minecraft.world.server.TicketType;
import net.minecraft.world.server.ServerWorld;

/** Read-only engine observations; never adds tickets or loads witness chunks. */
final class NativeTicketGeometry {
    private NativeTicketGeometry() {}

    private static Object uniqueField(Object owner, Class<?> declaring, Class<?> type) throws Exception {
        Field selected = null;
        for (Field field : declaring.getDeclaredFields()) {
            if (type.isAssignableFrom(field.getType())) {
                if (selected != null) throw new IllegalStateException("Ambiguous native ticket field");
                selected = field;
            }
        }
        if (selected == null) throw new IllegalStateException("Missing native ticket field");
        selected.setAccessible(true);
        return selected.get(owner);
    }

    static Map<String, Object> observe(ServerWorld world) {
        try {
            int fullLevel = -1, entityTickingLevel = -1;
            for (int level = 0; level <= ChunkManager.MAX_LOADED_LEVEL; level++) {
                if (ChunkHolder.getChunkStatusFromLevel(level) == ChunkStatus.FULL) fullLevel = level;
                if (ChunkHolder.getLocationTypeFromLevel(level) == ChunkHolder.LocationType.ENTITY_TICKING)
                    entityTickingLevel = level;
            }
            Object provider = world.getChunkProvider();
            Object manager = uniqueField(provider, provider.getClass(), TicketManager.class);
            Long2ObjectOpenHashMap<?> tickets = (Long2ObjectOpenHashMap<?>) uniqueField(
                    manager, TicketManager.class, Long2ObjectOpenHashMap.class);
            Set<Integer> forcedLevels = new TreeSet<>();
            for (Object value : tickets.values()) {
                for (Object entry : (Iterable<?>) value) {
                    Ticket<?> ticket = (Ticket<?>) entry;
                    if (ticket.getType() == TicketType.FORCED) forcedLevels.add(ticket.getLevel());
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fullLevel", fullLevel);
            result.put("entityTickingLevel", entityTickingLevel);
            result.put("maxDistance", ChunkStatus.maxDistance());
            result.put("lastHolderLevel", ChunkManager.MAX_LOADED_LEVEL);
            result.put("holderRadius", ChunkManager.MAX_LOADED_LEVEL - entityTickingLevel);
            result.put("observedForcedLevels", new ArrayList<>(forcedLevels));
            return result;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot observe native Forge 31 ticket geometry", error);
        }
    }
}
