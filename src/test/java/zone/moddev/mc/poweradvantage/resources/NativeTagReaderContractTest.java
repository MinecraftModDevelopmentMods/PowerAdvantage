package zone.moddev.mc.poweradvantage.resources;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.tags.ITag;
import net.minecraft.util.ResourceLocation;
import org.junit.BeforeClass;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.Assert.*;

public class NativeTagReaderContractTest {
    @BeforeClass public static void bootstrap() { net.minecraft.util.registry.Bootstrap.bootStrap(); }

    @Test public void optionalSteelBarsUseTheNativeParserWithAndWithoutBaseMetals() throws Exception {
        JsonObject json = new JsonParser().parse(new String(Files.readAllBytes(Paths.get(
                "src/main/resources/data/forge/tags/items/bars/steel.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        assertFalse("Do not silently ignore an obsolete optional array", json.has("optional"));
        ITag.Builder builder = ITag.Builder.tag().addFromJson(json, "test");
        ITag<Item> absent = builder.<Item>build(id -> null, id -> null).get();
        assertTrue(absent.getValues().isEmpty());
        ITag<Item> present = builder.<Item>build(id -> null, id ->
                id.equals(new ResourceLocation("basemetals:steel_bars")) ? Items.IRON_BARS : null).get();
        assertEquals(java.util.Collections.singletonList(Items.IRON_BARS), present.getValues());
    }

    @Test public void configuredAliasesAreAppliedInsideTheNativeReaderWithoutGlobalMutation() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream input = getClass().getResourceAsStream(
                "/zone/moddev/mc/poweradvantage/recipe/MaterialTags$1.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        int policies = 0;
        for (MethodNode method : node.methods) {
            boolean nativeLoaded = false;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertNotEquals("net/minecraft/tags/TagCollectionManager", call.owner);
                assertNotEquals("net/minecraft/tags/TagRegistryManager", call.owner);
                if (call.owner.equals("net/minecraft/tags/TagCollectionReader") && call.name.equals("load"))
                    nativeLoaded = true;
                if (call.owner.equals("zone/moddev/mc/poweradvantage/recipe/MaterialTags")
                        && call.name.equals("applyPolicy")) {
                    assertTrue("Preserve and resolve data-pack entries before adding aliases", nativeLoaded);
                    policies++;
                }
            }
        }
        assertEquals(1, policies);
    }
}
