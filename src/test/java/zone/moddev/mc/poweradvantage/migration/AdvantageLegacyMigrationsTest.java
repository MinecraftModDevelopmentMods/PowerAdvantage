package zone.moddev.mc.poweradvantage.migration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import cyano.poweradvantage.api.migration.LegacyMigrationApi;
import java.util.Arrays;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;

public class AdvantageLegacyMigrationsTest {
    @Test public void registersEveryLegacyPowerBlockCodec() {
        AdvantageLegacyMigrations.registerPowerAdvantage();
        for (String path : Arrays.asList(
                "fluid_drain", "fluid_discharge", "fluid_storage_tank", "fluid_metal_tank",
                "still", "fluid_pipe", "fluid_pipe_terminal", "steel_frame", "item_conveyor",
                "item_filter_block", "item_filter_food", "item_filter_fuel",
                "item_filter_inventory", "item_filter_ore", "item_filter_plant",
                "item_filter_smelt", "item_filter_overflow", "fluid_switch", "infinite_steam",
                "infinite_electricity", "infinite_quantum", "crude_oil", "refined_oil",
                "converter_rf_steam", "converter_rf_electricity", "converter_rf_quantum",
                "converter_tr_electricity")) {
            assertNotNull(path, LegacyMigrationApi.blockStateCodecs().get(
                    new ResourceLocation("poweradvantage", path)));
        }
    }

    @Test public void retainsLegacyTileFluidAndBucketIdentities() {
        AdvantageLegacyMigrations.registerPowerAdvantage();
        assertTrue(LegacyMigrationApi.handlesNamespace("poweradvantage"));
        assertEquals("poweradvantage:tileentity.fluid_storage_tank",
                LegacyMigrationApi.tileTarget("poweradvantage.tileentity.fluid_storage_tank"));
        assertEquals("poweradvantage:tileentity.fluid_storage_tank",
                LegacyMigrationApi.tileTarget("minecraft:poweradvantage.tileentity.fluid_storage_tank"));
        assertEquals("poweradvantage:crude_oil", LegacyMigrationApi.fluidTarget("crude_oil"));
        assertEquals(new ResourceLocation("poweradvantage", "crude_oil_bucket"),
                LegacyMigrationApi.bucketTarget("crude_oil"));
        assertEquals(new ResourceLocation("poweradvantage", "refined_oil_bucket"),
                LegacyMigrationApi.bucketTarget("poweradvantage:refined_oil"));
    }
}
