package zone.moddev.mc.poweradvantage.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import static org.junit.Assert.*;

public class NativePlatformContractTest {
    @Test public void onlyPreDatafixMigrationStillUsesACoremod() throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("src/main/resources/META-INF/coremods.json")), StandardCharsets.UTF_8);
        JsonObject coremods = new JsonParser().parse(json).getAsJsonObject();
        assertEquals(1, coremods.size());
        assertEquals("coremods/poweradvantage_114_migration.js", coremods.get("poweradvantage_114_migration").getAsString());
        for (String removed : new String[] {"oil_renderer", "material_tags", "parts_loot"})
            assertFalse(Files.exists(Paths.get("src/main/resources/coremods/poweradvantage_" + removed + ".js")));
    }

    @Test public void pinnedForgeFiresTagsUpdatedAfterInstallingAllFourCollections() throws Exception {
        ClassNode node = read("net/minecraft/tags/NetworkTagManager");
        int events = 0;
        for (MethodNode method : node.methods) {
            int installed = 0;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.owner.startsWith("net/minecraft/tags/") && call.name.equals("setCollection")) installed++;
                }
                if (instruction instanceof TypeInsnNode
                        && ((TypeInsnNode) instruction).desc.equals("net/minecraftforge/event/TagsUpdatedEvent")) {
                    assertEquals(4, installed);
                    events++;
                }
            }
        }
        assertEquals(1, events);
    }

    @Test public void nativeLootLoaderCallsForgeJsonHookAndNotTheRemovedAdvantageBridge() throws Exception {
        ClassNode node = read("net/minecraft/world/storage/loot/LootTableManager");
        int calls = 0;
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            assertNotEquals("zone/moddev/mc/poweradvantage/recipe/PartsLoot", call.owner);
            if (call.owner.equals("net/minecraftforge/common/ForgeHooks") && call.name.equals("loadLootTable")) {
                assertTrue(call.desc.contains("Lcom/google/gson/JsonObject;Z"));
                calls++;
            }
        }
        assertEquals(1, calls);
    }

    private static ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = NativePlatformContractTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }
}
