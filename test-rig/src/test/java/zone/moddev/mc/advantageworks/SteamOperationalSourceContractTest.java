package zone.moddev.mc.advantageworks;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.Assert.*;

public class SteamOperationalSourceContractTest {
    @Test public void finiteStillReservesReplaceTheTankInsteadOfMergingAnEmptyMarker() throws Exception {
        MethodNode reserve = readClass("SteamOperationalCases").methods.stream()
                .filter(method -> method.name.equals("reserve")).findFirst()
                .orElseThrow(() -> new AssertionError("Missing finite still reserve setup"));
        boolean replacement = false, setupMeasurement = false;
        for (AbstractInsnNode instruction : reserve.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            assertFalse("Recursive NBT merge preserves an old Empty marker", call.name.equals("seed"));
            if (call.owner.equals("cyano/poweradvantage/api/fluid/FluidTank") && call.name.equals("setFluid")) replacement = true;
            if (call.owner.equals("zone/moddev/mc/advantageworks/SteamOperationalCases") && call.name.equals("fluid")) setupMeasurement = true;
        }
        assertTrue("Finite tank reserve is directly initialized and measured before ticking", replacement && setupMeasurement);
    }

    @Test public void supplementalFamiliesUseTheSteamGroupAndRegisterExactlyOnce() throws Exception {
        int registrations = 0;
        for (MethodNode method : readClass("SteamOperationalCases").methods) {
            if (!method.name.equals("register")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("zone/moddev/mc/advantageworks/SteamOperationalSupplementalCases")
                        && call.name.equals("register") && call.desc.equals("(Ljava/util/List;)V")) registrations++;
            }
        }
        assertEquals("Steam registration must include supplementals once", 1, registrations);
        Set<String> families = new LinkedHashSet<>();
        for (MethodNode method : readClass("SteamOperationalSupplementalCases").methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof LdcInsnNode)) continue;
                Object value = ((LdcInsnNode) instruction).cst;
                if (!(value instanceof String)) continue;
                String text = (String) value;
                assertFalse("Supplemental IDs must not escape the steam group", text.startsWith("steam-extra/"));
                if (text.startsWith("steam/")) families.add(text);
            }
        }
        assertEquals(new LinkedHashSet<>(Arrays.asList("steam/extra-priority-", "steam/extra-recoil-",
                "steam/extra-explosive-", "steam/extra-musket-solid-wall")), families);
    }

    @Test public void musketOraclesReadTheServerConfigurationFieldWithoutReflectingItemMethods() throws Exception {
        for (String type : new String[]{"SteamOperationalActors", "SteamOperationalSupplementalCases"}) {
            ClassNode node = readClass(type);
            boolean fieldLookup = false, damageField = false, serverConfig = false;
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode) instruction;
                        if (!call.owner.equals("java/lang/Class")) continue;
                        assertFalse(type + " must not enumerate Item method signatures on the dedicated server",
                                call.name.equals("getMethod") || call.name.equals("getMethods")
                                        || call.name.equals("getDeclaredMethod") || call.name.equals("getDeclaredMethods"));
                        if (call.name.equals("getDeclaredField")) fieldLookup = true;
                    } else if (instruction instanceof LdcInsnNode) {
                        Object value = ((LdcInsnNode) instruction).cst;
                        if ("MUSKET_DAMAGE".equals(value)) damageField = true;
                        if ("zone.moddev.mc.steamadvantage.SteamAdvantage".equals(value)) serverConfig = true;
                    }
                }
            }
            assertTrue(type + " must read the actual server damage configuration", fieldLookup && damageField && serverConfig);
        }
    }

    private static ClassNode readClass(String type) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = SteamOperationalSourceContractTest.class.getResourceAsStream(
                "/zone/moddev/mc/advantageworks/" + type + ".class")) {
            assertNotNull(type, input);
            new ClassReader(input).accept(node, 0);
        }
        return node;
    }
}
