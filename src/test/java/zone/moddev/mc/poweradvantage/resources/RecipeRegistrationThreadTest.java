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

public class RecipeRegistrationThreadTest {
    @Test public void sharedForgeConditionsAreRegisteredAfterParallelSetup() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = getClass().getResourceAsStream("/zone/moddev/mc/poweradvantage/PowerAdvantage.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        String conditions = "zone/moddev/mc/poweradvantage/recipe/RecipeConditions";
        int queued = 0;
        for (MethodNode method : node.methods) {
            boolean registrationRunnable = false;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    assertFalse("Forge's condition HashMap is not safe during parallel loading",
                            call.owner.equals(conditions) && call.name.equals("register"));
                    if (call.owner.equals("net/minecraftforge/fml/DeferredWorkQueue")
                            && call.name.equals("runLater") && registrationRunnable) {
                        assertEquals("Register conditions during common setup", "commonSetup", method.name);
                        queued++;
                        registrationRunnable = false;
                    }
                }
                if (instruction instanceof InvokeDynamicInsnNode) {
                    for (Object argument : ((InvokeDynamicInsnNode) instruction).bsmArgs) {
                        if (argument instanceof Handle && ((Handle) argument).getOwner().equals(conditions)
                                && ((Handle) argument).getName().equals("register")) {
                            registrationRunnable = true;
                        }
                    }
                }
            }
        }
        assertEquals("Queue registration exactly once on Forge's loading thread", 1, queued);
    }
}
