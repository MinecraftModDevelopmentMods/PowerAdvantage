package zone.moddev.mc.poweradvantage.recipe;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableManager;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fml.ModList;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/** Native legacy parts, confined to original vanilla chest data without ALT. */
public final class PartsLoot {
    private static final Set<String> CHESTS = new HashSet<>(Arrays.asList(
            "chests/abandoned_mineshaft", "chests/simple_dungeon", "chests/village_blacksmith",
            "chests/stronghold_corridor", "chests/stronghold_crossing", "chests/end_city_treasure"));

    private PartsLoot() {}

    /** Forge 25 treats the Default pack as custom and skips LootTableLoadEvent. */
    public static LootTable loadLootTable(Gson gson, ResourceLocation name, String data,
            boolean defaultPack, LootTableManager manager) {
        String path = name.getPath();
        if (path.startsWith("loot_tables/") && path.endsWith(".json"))
            path = path.substring("loot_tables/".length(), path.length() - ".json".length());
        if (defaultPack && name.getNamespace().equals("minecraft") && CHESTS.contains(path)
                && ModList.get() != null && ModList.get().isLoaded("poweradvantage")
                && PowerAdvantage.chestLootFactor > 0 && !hasAdditionalLootTables()) {
            JsonObject table = new JsonParser().parse(data).getAsJsonObject();
            JsonArray pools = table.getAsJsonArray("pools");
            if (pools == null) { pools = new JsonArray(); table.add("pools", pools); }
            for (String mod : new String[] {"poweradvantage", "steamadvantage", "electricadvantage"}) {
                if (!ModList.get().isLoaded(mod) || (mod.equals("poweradvantage") && path.equals("chests/end_city_treasure"))) continue;
                String poolName = mod + ":legacy_parts";
                boolean exists = false;
                for (com.google.gson.JsonElement pool : pools)
                    if (pool.getAsJsonObject().has("name") && poolName.equals(pool.getAsJsonObject().get("name").getAsString())) exists = true;
                if (exists) continue;
                JsonObject pool = new JsonObject();
                pool.addProperty("name", poolName);
                pool.addProperty("rolls", 1);
                JsonObject entry = new JsonObject();
                entry.addProperty("type", "loot_table");
                entry.addProperty("name", mod + ":chests/advantage_parts");
                entry.addProperty("weight", 1);
                JsonArray entries = new JsonArray();
                entries.add(entry);
                pool.add("entries", entries);
                pools.add(pool);
            }
            data = table.toString();
        }
        return ForgeHooks.loadLootTable(gson, name, data, defaultPack, manager);
    }

    private static boolean hasAdditionalLootTables() {
        return ModList.get().getMods().stream().anyMatch(mod -> mod.getModId().replace("_", "").replace("-", "")
                .equals("additionalloottables"));
    }
}
