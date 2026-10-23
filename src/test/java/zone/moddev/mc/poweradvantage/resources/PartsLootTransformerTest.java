package zone.moddev.mc.poweradvantage.resources;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.Assert.*;

public class PartsLootTransformerTest {
    @Test public void redirectsOnlyThePinnedForgeLoaderWithAnIdenticalStackContract() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = getClass().getResourceAsStream("/net/minecraft/world/storage/loot/LootTableManager.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        try (Reader script = Files.newBufferedReader(Paths.get("src/main/resources/coremods/poweradvantage_parts_loot.js"),
                StandardCharsets.UTF_8)) { engine.eval(script); }
        engine.put("manager", node);
        engine.eval("initializeCoreMod().poweradvantage_parts_loot.transformer(manager)");
        int calls = 0;
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.owner.equals("zone/moddev/mc/poweradvantage/recipe/PartsLoot")) {
                calls++;
                assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
                assertEquals("loadLootTable", call.name);
                assertEquals("(Lcom/google/gson/Gson;Lnet/minecraft/util/ResourceLocation;Ljava/lang/String;ZLnet/minecraft/world/storage/loot/LootTableManager;)Lnet/minecraft/world/storage/loot/LootTable;", call.desc);
            }
            assertFalse(call.owner.equals("net/minecraftforge/common/ForgeHooks") && call.name.equals("loadLootTable"));
        }
        assertEquals(1, calls);
    }
}
