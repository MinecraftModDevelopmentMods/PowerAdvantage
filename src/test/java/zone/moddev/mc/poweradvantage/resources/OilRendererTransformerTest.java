package zone.moddev.mc.poweradvantage.resources;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.Assert.*;

/** Exercises the shipped JavaScript against the pinned native renderer bytecode. */
public class OilRendererTransformerTest {
    private static final String RENDER = "(Lnet/minecraft/world/IWorldReader;Lnet/minecraft/util/math/BlockPos;"
            + "Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/fluid/IFluidState;)Z";
    private static final String OWNER = "zone/moddev/mc/poweradvantage/client/ClientOilRenderer";
    private static final String PEER = "test/SiblingFluidRenderer";

    @Test public void patchesMappedRenderer() throws Exception {
        ClassNode node = renderer();
        transform(node);
        assertHooks(node, OWNER);
    }

    @Test public void patchesProductionNamedRenderer() throws Exception {
        ClassNode node = renderer();
        render(node).name = "func_209557_a";
        transform(node);
        assertHooks(node, OWNER);
    }

    @Test public void preservesEarlierSiblingHooks() throws Exception {
        ClassNode node = renderer();
        addSiblingHooks(node);
        transform(node);
        assertHooks(node, OWNER);
        assertHooks(node, PEER);
    }

    @Test public void preservesLaterSiblingHooks() throws Exception {
        ClassNode node = renderer();
        transform(node);
        addSiblingHooks(node);
        assertHooks(node, OWNER);
        assertHooks(node, PEER);
    }

    private static ClassNode renderer() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = OilRendererTransformerTest.class.getResourceAsStream(
                "/net/minecraft/client/renderer/BlockFluidRenderer.class")) {
            assertNotNull("Pinned Forge renderer is absent", input);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static MethodNode render(ClassNode node) {
        for (MethodNode method : node.methods) if (RENDER.equals(method.desc)) return method;
        throw new AssertionError("Pinned renderer signature changed");
    }

    private static void transform(ClassNode node) throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        assertNotNull("Run transformer tests with the Java 8 toolchain", engine);
        try (Reader script = Files.newBufferedReader(Paths.get("src/main/resources/coremods/poweradvantage_oil_renderer.js"),
                StandardCharsets.UTF_8)) {
            engine.eval(script);
        }
        engine.put("renderer", node);
        assertSame(node, engine.eval("initializeCoreMod().poweradvantage_oil_renderer.transformer(renderer)"));
    }

    private static void assertHooks(ClassNode node, String owner) {
        int count = 0;
        for (AbstractInsnNode instruction : render(node).instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && owner.equals(((MethodInsnNode) instruction).owner)) count++;
        }
        assertEquals("Missing or duplicated hooks for " + owner, 3, count);
    }

    private static void addSiblingHooks(ClassNode node) {
        // Base Metals and Mineralogy hook these same first local stores. Keep both chains intact.
        boolean[] patched = new boolean[3];
        for (AbstractInsnNode instruction : render(node).instructions.toArray()) {
            if (!(instruction instanceof VarInsnNode)) continue;
            int slot = ((VarInsnNode) instruction).var;
            if (slot < 5 || slot > 7 || patched[slot - 5]) continue;
            boolean reference = slot == 6;
            if (instruction.getOpcode() != (reference ? Opcodes.ASTORE : Opcodes.ISTORE)) continue;
            String desc = slot == 5 ? "Z" : reference ? "[Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;" : "I";
            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 4));
            hook.add(new VarInsnNode(reference ? Opcodes.ALOAD : Opcodes.ILOAD, slot));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PEER, "override",
                    "(Lnet/minecraft/fluid/IFluidState;" + desc + ")" + desc, false));
            hook.add(new VarInsnNode(reference ? Opcodes.ASTORE : Opcodes.ISTORE, slot));
            render(node).instructions.insert(instruction, hook);
            patched[slot - 5] = true;
        }
    }
}
