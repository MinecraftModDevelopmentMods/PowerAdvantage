package zone.moddev.mc.poweradvantage.registry;

import zone.moddev.mc.poweradvantage.machines.fluidmachines.MetalTankTileEntity;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.StorageTankTileEntity;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/** Caption inputs only; these tests do not prove that the client renders a label. */
public class MachineGuiTitleTest {
    @BeforeClass public static void bootstrap() {
        net.minecraft.init.Bootstrap.register();
    }

    @Test public void tankNamesUseTheirPublishedTranslationsRatherThanJavaClassNames() throws Exception {
        assertCaption(new StorageTankTileEntity(), "tile.poweradvantage.fluid_storage_tank.name",
                "Portable Fluid Tank");
        assertCaption(new MetalTankTileEntity(), "tile.poweradvantage.fluid_metal_tank.name",
                "Metal Fluid Tank");
    }

    @Test public void renamedTanksKeepThePlayersFullName() {
        MetalTankTileEntity tile = new MetalTankTileEntity();
        tile.setCustomInventoryName("John's oil tank");
        assertTrue(tile.hasCustomName());
        assertEquals("John's oil tank", tile.getDisplayName().getUnformattedText());
    }

    @Test public void playerInventorySuppliesTheNativeInventoryCaption() {
        ITextComponent caption = new InventoryPlayer(null).getDisplayName();
        assertTrue(caption instanceof TextComponentTranslation);
        assertEquals("container.inventory", ((TextComponentTranslation) caption).getKey());
    }

    private static void assertCaption(IInventory tank, String key, String literal) throws Exception {
        ITextComponent caption = tank.getDisplayName();
        assertTrue("An unnamed tank needs a translatable caption", caption instanceof TextComponentTranslation);
        assertEquals(key, ((TextComponentTranslation) caption).getKey());
        Properties english = new Properties();
        try (InputStream stream = MachineGuiTitleTest.class.getResourceAsStream(
                "/assets/poweradvantage/lang/en_US.lang")) {
            assertNotNull("Published Power Advantage English language resource", stream);
            english.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        assertEquals(literal, english.getProperty(key));
    }
}
