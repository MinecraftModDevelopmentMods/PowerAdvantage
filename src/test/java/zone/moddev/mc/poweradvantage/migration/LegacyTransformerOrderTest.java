package zone.moddev.mc.poweradvantage.migration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.Assert.*;

/** Applies both real migration scripts to the exact native Forge classes. */
public class LegacyTransformerOrderTest {
    @Test public void advantageThenBaseMetalsPreservesBothHooks() throws Exception { check(false, false); }
    @Test public void baseMetalsThenAdvantagePreservesBothHooks() throws Exception { check(true, false); }
    @Test public void productionMethodNamesDoNotChangeEitherOrdering() throws Exception {
        check(false, true);
        check(true, true);
    }

    private static void check(boolean baseFirst, boolean productionNames) throws Exception {
        String[][] targets = {
                {"net/minecraft/world/storage/SaveFormat", "legacy_world_info", "prepareLegacyWorld"},
                {"net/minecraft/world/chunk/storage/ChunkLoader", "legacy_chunk_status", "prepareLegacyChunk"},
                {"net/minecraft/world/gen/WorldGenRegion", "legacy_worldgen_guard", "shouldBlockWorldgenWrite"}
        };
        for (String[] target : targets) {
            ClassNode node = new ClassNode();
            try (InputStream input = getResource("/" + target[0] + ".class")) { new ClassReader(input).accept(node, 0); }
            if (productionNames) for (MethodNode method : node.methods)
                if (!method.name.startsWith("<")) method.name = "test_srg_" + method.name;
            transform(node, target[1], baseFirst);
            transform(node, target[1], !baseFirst);
            assertEquals(target[0], 1, calls(node, "poweradvantage", target[2]));
            assertEquals(target[0], 1, calls(node, "basemetals", target[2]));
            if (target[1].equals("legacy_chunk_status")) {
                assertEquals(1, calls(node, "poweradvantage", "finalizeLegacyChunk"));
                assertEquals(1, calls(node, "basemetals", "finalizeLegacyChunk"));
            }
        }
    }

    private static void transform(ClassNode node, String target, boolean base) throws Exception {
        String mod = base ? "basemetals" : "poweradvantage";
        String path = base ? "/coremods/basemetals_116_compatibility.js" : "/coremods/poweradvantage_116_migration.js";
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        assertNotNull("Transformer tests require the Java 8 test toolchain", engine);
        try (InputStreamReader reader = new InputStreamReader(getResource(path), StandardCharsets.UTF_8)) { engine.eval(reader); }
        engine.put("targetClass", node);
        assertSame(node, engine.eval("initializeCoreMod()." + mod + "_" + target + ".transformer(targetClass)"));
    }

    private static InputStream getResource(String name) {
        InputStream input = LegacyTransformerOrderTest.class.getResourceAsStream(name);
        assertNotNull("Missing pinned native dependency resource " + name, input);
        return input;
    }

    private static int calls(ClassNode node, String mod, String name) {
        int calls = 0;
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.owner.equals("zone/moddev/mc/" + mod + "/migration/LegacyWorldDataHook") && call.name.equals(name)) calls++;
        }
        return calls;
    }
}
