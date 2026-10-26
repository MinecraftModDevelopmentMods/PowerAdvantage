package zone.moddev.mc.poweradvantage;

import java.util.HashMap;
import java.util.Map;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.migration.LegacyConfigMigration;
import cyano.poweradvantage.api.migration.LegacyConfigMigration.Mapping;
import cyano.poweradvantage.api.migration.LegacyConfigMigration.ValueType;
import cyano.poweradvantage.api.migration.LegacyOreSpawnConfigBridge;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import zone.moddev.mc.poweradvantage.config.PowerAdvantageConfig;
import zone.moddev.mc.poweradvantage.conduitnetwork.ConduitRegistry;
import zone.moddev.mc.poweradvantage.init.Blocks;
import zone.moddev.mc.poweradvantage.init.Fluids;
import zone.moddev.mc.poweradvantage.init.GUI;
import zone.moddev.mc.poweradvantage.init.Fuels;
import zone.moddev.mc.poweradvantage.init.Items;
import zone.moddev.mc.poweradvantage.init.ModSupport;
import zone.moddev.mc.poweradvantage.init.Recipes;
import zone.moddev.mc.poweradvantage.registry.MachineGUIRegistry;
import zone.moddev.mc.poweradvantage.recipe.RecipeConditions;
import zone.moddev.mc.poweradvantage.recipe.RecipeDiscovery;
import zone.moddev.mc.poweradvantage.migration.AdvantageLegacyMigrations;
import zone.moddev.mc.poweradvantage.migration.LegacyWorldDataHook;

/** Forge 1.14 entry point for Power Advantage. */
@Mod(PowerAdvantage.MODID)
public final class PowerAdvantage {
    public static final String MODID = "poweradvantage";
    public static final String NAME = "Power Advantage";
    public static final String VERSION = "2.4.5.114041";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    private static PowerAdvantage instance;

    /** Public compatibility settings consumed by Advantage add-ons. */
    public static RecipeMode recipeMode = RecipeMode.NORMAL;
    public static boolean useOtherFluids;
    public static float chestLootFactor = 0.5F;
    public static boolean plasticIsAlsoRubber = true;

    /** All historical RF- and Tech Reborn-labelled converters use Forge Energy in 1.14. */
    public static final Map<ConduitType, Float> forgeEnergyConversionTable = new HashMap<>();

    /** Conversion table for the historical Tech Reborn-labelled Forge Energy converter. */
    public static final Map<ConduitType, Float> techRebornForgeEnergyConversionTable = new HashMap<>();

    /** @deprecated Use {@link #forgeEnergyConversionTable}. */
    @Deprecated
    public static final Map<ConduitType, Float> rfConversionTable = forgeEnergyConversionTable;
    /** @deprecated Use {@link #forgeEnergyConversionTable}. */
    @Deprecated
    public static final Map<ConduitType, Float> trConversionTable = forgeEnergyConversionTable;

    /** Retained for source compatibility; Forge Energy is always available. */
    @Deprecated public static boolean detectedRF = true;
    /** Retained for source compatibility; the labelled converter now uses Forge Energy. */
    @Deprecated public static boolean detectedTechReborn = true;

    public PowerAdvantage() {
        instance = this;

        AdvantageLegacyMigrations.registerPowerAdvantage();
        MinecraftForge.EVENT_BUS.addListener(ConduitRegistry.getInstance()::onChunkLoad);
        MinecraftForge.EVENT_BUS.addListener(ConduitRegistry.getInstance()::onChunkUnload);
        MinecraftForge.EVENT_BUS.addListener(RecipeDiscovery::onPlayerLogin);
        MinecraftForge.EVENT_BUS.addListener(zone.moddev.mc.poweradvantage.recipe.PartsLoot::onLoad);
        MinecraftForge.EVENT_BUS.addListener(zone.moddev.mc.poweradvantage.init.Villages::onTrades);
        MinecraftForge.EVENT_BUS.addListener(zone.moddev.mc.poweradvantage.recipe.MaterialTags::onTagsUpdated);

        Fluids.init();
        Blocks.init();
        Items.init();
        Fuels.init();
        GUI.init();

        migrateLegacyOreSpawnConfiguration();
        migrateLegacyConfiguration();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PowerAdvantageConfig.SPEC);
        RecipeConditions.register();
        FMLJavaModLoadingContext.get().getModEventBus().addListener(PowerAdvantageConfig::onConfigLoading);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);

        DistExecutor.runWhenOn(Dist.CLIENT, () -> () -> {
            FMLJavaModLoadingContext.get().getModEventBus().addListener(
                    (net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) ->
                            net.minecraftforge.fml.DeferredWorkQueue.runLater(MachineGUIRegistry::registerScreens));
            zone.moddev.mc.poweradvantage.client.ClientOilRenderer.register();
        });
    }

    private static void migrateLegacyOreSpawnConfiguration() {
        try {
            LegacyOreSpawnConfigBridge.Result result =
                    LegacyOreSpawnConfigBridge.migrate(FMLPaths.CONFIGDIR.get(), MODID);
            if (result.wasMigrated()) {
                LOGGER.info("Imported {} legacy Power Advantage OreSpawn rule(s) from '{}' into '{}'",
                        result.getRuleCount(), result.getSource(), result.getTarget());
            }
        } catch (IOException exception) {
            LOGGER.error("Could not migrate legacy Power Advantage OreSpawn configuration", exception);
        }
    }

    private static void migrateLegacyConfiguration() {
        try {
            LegacyConfigMigration.Result result = LegacyConfigMigration.migrate(FMLPaths.CONFIGDIR.get(), MODID,
                    Mapping.of("options", "recipe_mode", "options", "recipeMode", ValueType.STRING),
                    Mapping.of("options", "treasure_chest_loot_factor", "options", "treasureChestLootFactor", ValueType.DOUBLE),
                    Mapping.of("options", "plastic_equals_rubber", "options", "plasticEqualsRubber", ValueType.BOOLEAN),
                    Mapping.of("Other Power Mods", "use_other_fluids", "options", "useOtherFluids", ValueType.BOOLEAN),
                    Mapping.of("recipes", "distiller_recipes", "recipes", "distillerRecipes", ValueType.STRING),
                    Mapping.of("compatibility", "additional_crude_oil_fluid_aliases", "compatibility", "additionalCrudeOilFluidAliases", ValueType.STRING),
                    Mapping.of("Other Power Mods", "RF_conversions", "energy", "rfConversions", ValueType.STRING),
                    Mapping.of("Other Power Mods", "TechReborn_conversions", "energy", "techRebornConversions", ValueType.STRING));
            if (result.wasMigrated()) {
                LOGGER.info("Imported {} legacy Power Advantage settings; report: {}",
                        result.getImportedValues(), result.getReportFile());
            }
        } catch (IOException exception) {
            LOGGER.error("Could not migrate legacy Power Advantage configuration", exception);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Forge's shared hook list is not safe to mutate during parallel mod construction.
        net.minecraftforge.fml.DeferredWorkQueue.runLater(LegacyWorldDataHook::register);
        PowerAdvantageConfig.apply();
        ModSupport.configureForgeEnergyConversions();
        Recipes.initDistillationRecipes(PowerAdvantageConfig.distillationRecipes());
        LOGGER.info("Power Advantage {} common setup complete", VERSION);
    }

    /** Legacy API retained for add-ons that pass the mod owner to GUIBlock. */
    public static PowerAdvantage getInstance() {
        return instance;
    }
}
