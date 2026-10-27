package zone.moddev.mc.advantageworks;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import net.minecraft.world.server.ServerChunkProvider;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

/** Read-only engine observations; never adds tickets or loads witness chunks. */
final class NativeTicketGeometry {
    private NativeTicketGeometry() {}

    static Map<String, Object> observe(ServerWorld world) {
        try {
            int fullLevel = -1, entityTickingLevel = -1;
            for (int level = 0; level <= ChunkManager.MAX_CHUNK_DISTANCE; level++) {
                if (ChunkHolder.getStatus(level) == ChunkStatus.FULL) fullLevel = level;
                if (ChunkHolder.getFullChunkStatus(level) == ChunkHolder.LocationType.ENTITY_TICKING)
                    entityTickingLevel = level;
            }
            ServerChunkProvider provider = world.getChunkSource();
            TicketManager manager = ObfuscationReflectionHelper.getPrivateValue(
                    ServerChunkProvider.class, provider, "field_217240_d");
            // Forge 36 adds a second map, forcedTickets; observe the vanilla tickets map only.
            Long2ObjectOpenHashMap<?> tickets = ObfuscationReflectionHelper.getPrivateValue(
                    TicketManager.class, manager, "field_219377_e");
            Set<Integer> forcedLevels = new TreeSet<>();
            for (Object value : tickets.values()) {
                for (Object entry : (Iterable<?>) value) {
                    Ticket<?> ticket = (Ticket<?>) entry;
                    if (ticket.getType() == TicketType.FORCED) forcedLevels.add(ticket.getTicketLevel());
                }
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("fullLevel", fullLevel);
            result.put("entityTickingLevel", entityTickingLevel);
            result.put("maxDistance", ChunkStatus.maxDistance());
            result.put("lastHolderLevel", ChunkManager.MAX_CHUNK_DISTANCE);
            result.put("holderRadius", ChunkManager.MAX_CHUNK_DISTANCE - entityTickingLevel);
            result.put("observedForcedLevels", new ArrayList<>(forcedLevels));
            return result;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot observe native Forge 36 ticket geometry", error);
        }
    }
}
