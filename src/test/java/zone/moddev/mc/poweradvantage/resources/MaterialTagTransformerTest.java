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

public class MaterialTagTransformerTest {
    @Test public void hooksThePinnedReloadAfterAllTagCollectionsAreInstalled() throws Exception {
        check(false);
    }

    @Test public void hooksProductionNamesWithoutDependingOnMappings() throws Exception {
        check(true);
    }

    private static void check(boolean productionName) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = MaterialTagTransformerTest.class.getResourceAsStream(
                "/net/minecraft/tags/NetworkTagManager.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        MethodNode reload = null;
        for (MethodNode method : node.methods) {
            if (method.desc.equals("(Lnet/minecraft/resources/IResourceManager;)V")) reload = method;
        }
        assertNotNull(reload);
        if (productionName) reload.name = "func_195410_a";
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        try (Reader script = Files.newBufferedReader(Paths.get("src/main/resources/coremods/poweradvantage_material_tags.js"),
                StandardCharsets.UTF_8)) { engine.eval(script); }
        engine.put("manager", node);
        engine.eval("initializeCoreMod().poweradvantage_material_tags.transformer(manager)");
        int installed = 0, hooks = 0;
        for (AbstractInsnNode instruction : reload.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.owner.startsWith("net/minecraft/tags/") && call.desc.endsWith("TagCollection;)V")) installed++;
            if (call.owner.equals("zone/moddev/mc/poweradvantage/recipe/MaterialTags")) {
                assertEquals(3, installed);
                assertEquals("finishReload", call.name);
                assertEquals(Opcodes.RETURN, call.getNext().getOpcode());
                hooks++;
            }
        }
        assertEquals(1, hooks);
    }
}
