package zone.moddev.mc.poweradvantage.resources;

import java.io.InputStream;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.Assert.*;

/** The live interop fixture additionally checks exact conservation in all four converters. */
public class ConverterEnergyDirectionTest {
    @Test public void extractedExternalEnergyIsAddedWithoutNegatingItAgain() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = getClass().getResourceAsStream("/cyano/poweradvantage/api/modsupport/TileEntityConverter.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        int transfers = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("powerUpdate")) continue;
            boolean extracted = false;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (extracted) {
                    assertNotEquals("Positive external extraction must not be negated", Opcodes.FNEG, instruction.getOpcode());
                    assertNotEquals("Positive external extraction must not be multiplied by -1", Opcodes.FMUL, instruction.getOpcode());
                }
                if (!(instruction instanceof MethodInsnNode)) continue;
                String name = ((MethodInsnNode) instruction).name;
                if (name.equals("subtractEnergyFromOther")) extracted = true;
                if (extracted && name.equals("addEnergy")) { transfers++; extracted = false; }
            }
        }
        assertEquals("Expected exactly one incoming conversion path", 1, transfers);
    }
}
