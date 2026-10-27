package zone.moddev.mc.poweradvantage.migration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.datafix.DataFixesManager;
import static org.junit.Assert.*;

public class LegacyPlayerTransformerTest {
    private static final String NATIVE = "net/minecraft/nbt/NBTUtil";
    private static final String WRAPPER = "zone/moddev/mc/poweradvantage/migration/LegacyPlayerDataFixes";
    private static final String DESC = "(Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/util/datafix/DefaultTypeReferences;"
            + "Lnet/minecraft/nbt/CompoundNBT;I)Lnet/minecraft/nbt/CompoundNBT;";
    private static final String[][] TARGETS = {{"PlayerData", "legacy_player_file"}, {"ServerWorldInfo", "legacy_embedded_player"}};

    @Test public void exactNativeLoosePlayerLoadAndEmbeddedUpdateArePatched() throws Exception {
        for (String[] target : TARGETS) {
            ClassNode node = nativeClass(target[0]);
            MethodNode containing = containing(node);
            assertEquals(target[0].equals("PlayerData") ? "load" : "updatePlayerTag", containing.name);
            assertEquals(1, calls(node, NATIVE));
            transform(node, target[1]);
            assertEquals(0, calls(node, NATIVE));
            assertEquals(1, calls(node, WRAPPER));
            assertSame(containing, containing(node));
        }
    }

    @Test public void embeddedGetterStillReachesThePatchedNativeUpdate() throws Exception {
        ClassNode node = nativeClass("ServerWorldInfo");
        MethodNode update = containing(node);
        transform(node, "legacy_embedded_player");
        boolean getterCallsUpdate = false, saveCallsUpdate = false;
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.owner.equals(node.name) && call.name.equals(update.name) && call.desc.equals(update.desc)) {
                getterCallsUpdate |= method.name.equals("getLoadedPlayerTag");
                saveCallsUpdate |= method.name.equals("createTag");
            }
        }
        assertTrue("Embedded getter must use the patched update", getterCallsUpdate);
        assertTrue("Embedded save must use the same patched update", saveCallsUpdate);
    }

    @Test public void transformedNativeEmbeddedGetterExecutesAndDoesNotReconvert() throws Exception {
        net.minecraft.util.registry.Bootstrap.bootStrap();
        ClassNode node = nativeClass("ServerWorldInfo");
        transform(node, "legacy_embedded_player");
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        final byte[] bytes = writer.toByteArray();
        Class<?> type = new ClassLoader(getClass().getClassLoader()) {
            Class<?> define() { return defineClass(null, bytes, 0, bytes.length); }
        }.define();
        for (int version : new int[] {512, 1343}) {
            CompoundNBT source = LegacyPlayerDataFixesTest.player("Inventory", LegacyPlayerDataFixesTest
                    .enchanted("minecraft:enchanted_book", "StoredEnchantments", true));
            CompoundNBT before = source.copy();
            Object info = embeddedInfo(type, source, version);
            CompoundNBT result = (CompoundNBT) type.getMethod("getLoadedPlayerTag").invoke(info);
            assertEquals("steamadvantage:steam_capacity", LegacyPlayerDataFixesTest.enchantment(
                    LegacyPlayerDataFixesTest.item(result, "Inventory", 0), "StoredEnchantments").getString("id"));
            assertSame("The native upgraded flag must prevent repeat conversion", result,
                    type.getMethod("getLoadedPlayerTag").invoke(info));
            assertEquals(before, source);
        }
    }

    @Test public void renamedNativeAndSrgLikeCallNamesDoNotAffectMatching() throws Exception {
        for (String[] target : TARGETS) {
            ClassNode node = nativeClass(target[0]);
            for (MethodNode method : node.methods) {
                if (!method.name.startsWith("<")) method.name = "func_test_" + method.name;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (call.owner.equals(NATIVE)) call.name = "func_244999_a";
                    }
                }
            }
            transform(node, target[1]);
            assertEquals(1, calls(node, WRAPPER));
            assertEquals(0, calls(node, NATIVE));
        }
    }

    @Test public void absentCallFailsClosedWithoutPatchingOtherOwners() throws Exception {
        for (String[] target : TARGETS) {
            ClassNode node = nativeClass(target[0]);
            nativeCall(node).owner = "other/UnrelatedNBTUtil";
            rejected(node, target[1]);
            assertEquals(0, calls(node, WRAPPER));
        }
    }

    @Test public void duplicateCallsFailClosedBeforeEitherIsRewritten() throws Exception {
        for (String[] target : TARGETS) {
            ClassNode node = nativeClass(target[0]);
            MethodInsnNode call = nativeCall(node);
            containing(node).instructions.insertBefore(call, new MethodInsnNode(call.getOpcode(), call.owner, call.name, call.desc, call.itf));
            rejected(node, target[1]);
            assertEquals(2, calls(node, NATIVE));
            assertEquals(0, calls(node, WRAPPER));
        }
    }

    @Test public void wrongOverloadIsNotMistakenForTheFourArgumentCall() throws Exception {
        for (String[] target : TARGETS) {
            ClassNode node = nativeClass(target[0]);
            nativeCall(node).desc = DESC.replace(";I)", ";II)");
            rejected(node, target[1]);
            assertEquals(0, calls(node, WRAPPER));
        }
    }

    private static MethodNode containing(ClassNode node) {
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if ((call.owner.equals(NATIVE) || call.owner.equals(WRAPPER)) && call.desc.equals(DESC)) return method;
            }
        }
        throw new AssertionError("No player update call");
    }

    private static MethodInsnNode nativeCall(ClassNode node) {
        for (AbstractInsnNode instruction : containing(node).instructions.toArray()) {
            if (instruction instanceof MethodInsnNode && ((MethodInsnNode) instruction).owner.equals(NATIVE)
                    && ((MethodInsnNode) instruction).desc.equals(DESC)) return (MethodInsnNode) instruction;
        }
        throw new AssertionError("Missing native call");
    }

    private static int calls(ClassNode node, String owner) {
        int count = 0;
        for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals(owner) && call.desc.equals(DESC)) {
                    assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
                    if (owner.equals(WRAPPER)) assertEquals("update", call.name);
                    count++;
                }
            }
        }
        return count;
    }

    private static ClassNode nativeClass(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = resource("/net/minecraft/world/storage/" + name + ".class")) {
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }

    private static Object embeddedInfo(Class<?> type, CompoundNBT source, int version) throws Exception {
        // The real private constructor only assigns fields. The getter needs its
        // fixer/version/player arguments, not a world, generator, or live server.
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (!Modifier.isPrivate(constructor.getModifiers())) continue;
            Class<?>[] types = constructor.getParameterTypes();
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                if (types[i] == int.class) args[i] = 0;
                if (types[i] == boolean.class) args[i] = false;
                if (types[i] == long.class) args[i] = 0L;
                if (types[i] == float.class) args[i] = 0F;
            }
            args[0] = DataFixesManager.getDataFixer();
            args[1] = version;
            args[2] = source;
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        }
        throw new AssertionError("Missing native embedded-player constructor");
    }

    private static void rejected(ClassNode node, String target) throws Exception {
        try { transform(node, target); fail("Missing or ambiguous native player calls must abort transformation"); }
        catch (ScriptException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains("exactly one")); }
    }

    private static void transform(ClassNode node, String target) throws Exception {
        ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
        assertNotNull("Use the Java 8 test toolchain", engine);
        try (InputStreamReader reader = new InputStreamReader(resource("/coremods/poweradvantage_116_migration.js"), StandardCharsets.UTF_8)) {
            engine.eval(reader);
        }
        engine.put("targetClass", node);
        assertSame(node, engine.eval("initializeCoreMod().poweradvantage_" + target + ".transformer(targetClass)"));
    }

    private static InputStream resource(String name) {
        InputStream input = LegacyPlayerTransformerTest.class.getResourceAsStream(name);
        assertNotNull("Missing pinned native resource " + name, input);
        return input;
    }
}
