package zone.moddev.mc.poweradvantage.resources;

import java.io.InputStream;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.Assert.*;

public class PersistenceRegistrationThreadTest {
    @Test public void sharedForgeHookListIsOnlyUpdatedThroughQueuedSetup() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = getClass().getResourceAsStream("/zone/moddev/mc/poweradvantage/PowerAdvantage.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        String hook = "zone/moddev/mc/poweradvantage/migration/LegacyWorldDataHook";
        int queuedRegistrations = 0;
        for (MethodNode method : node.methods) {
            boolean hookRunnable = false;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    assertFalse("Do not mutate Forge's ArrayList during parallel mod construction",
                            call.owner.equals(hook) && call.name.equals("register"));
                    if (call.owner.equals("net/minecraftforge/fml/DeferredWorkQueue") && call.name.equals("runLater") && hookRunnable) {
                        assertEquals("Queue the hook during common setup", "commonSetup", method.name);
                        queuedRegistrations++;
                        hookRunnable = false;
                    }
                }
                if (instruction instanceof InvokeDynamicInsnNode) {
                    for (Object argument : ((InvokeDynamicInsnNode) instruction).bsmArgs) {
                        if (argument instanceof Handle && ((Handle) argument).getOwner().equals(hook)
                                && ((Handle) argument).getName().equals("register")) hookRunnable = true;
                    }
                }
            }
        }
        assertEquals("Register the shared persistence hook once, after parallel lifecycle work", 1, queuedRegistrations);
    }
}
