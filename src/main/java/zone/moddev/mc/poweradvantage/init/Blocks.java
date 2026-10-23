package zone.moddev.mc.poweradvantage.init;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.GUIBlock;
import cyano.poweradvantage.api.fluid.InteractiveFluidBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFlowingFluid;
import net.minecraft.block.material.Material;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.blocks.BlockFrame;
import zone.moddev.mc.poweradvantage.blocks.BlockPowerSwitch;
import zone.moddev.mc.poweradvantage.machines.conveyors.BlockConveyor;
import zone.moddev.mc.poweradvantage.machines.conveyors.BlockConveyorFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityBlockFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityFoodFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityFuelFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityInventoryFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityOreFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityOverflowFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityPlantFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntitySmeltableFilter;
import zone.moddev.mc.poweradvantage.machines.creative.InfiniteEnergyBlock;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.FluidDischargeBlock;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.FluidDrainBlock;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.FluidPipeBlock;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.MetalTankBlock;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.StillBlock;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.StorageTankBlock;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.modsupport.TerminalFluidPipeBlock;

/** Stable block and block-item registry for the 1.13 port. */
@Mod.EventBusSubscriber(modid = PowerAdvantage.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Blocks {
    private static final float STEEL_BLAST_RESISTANCE = 37.5f;
    private static final Map<String, Block> ALL_BLOCKS = new LinkedHashMap<>();

    public static GUIBlock fluid_drain;
    public static GUIBlock fluid_discharge;
    public static GUIBlock storage_tank;
    public static GUIBlock metal_storage_tank;
    public static GUIBlock still;
    public static Block fluid_pipe;
    public static Block fluid_pipe_terminal;
    public static Block fluid_switch;
    public static GUIBlock item_conveyor;
    public static GUIBlock item_filter_block;
    public static GUIBlock item_filter_food;
    public static GUIBlock item_filter_fuel;
    public static GUIBlock item_filter_inventory;
    public static GUIBlock item_filter_ore;
    public static GUIBlock item_filter_plant;
    public static GUIBlock item_filter_smelt;
    public static GUIBlock item_filter_overflow;
    public static Block steel_frame;
    public static GUIBlock infinite_steam;
    public static GUIBlock infinite_electricity;
    public static GUIBlock infinite_quantum;
    public static BlockFlowingFluid crude_oil_block;
    public static BlockFlowingFluid refined_oil_block;

    private static boolean initialized;

    private Blocks() {}

    public static synchronized void init() {
        if (initialized) return;
        ItemGroups.init();
        Fluids.init();

        fluid_drain = (GUIBlock) add(new FluidDrainBlock(), "fluid_drain");
        fluid_discharge = (GUIBlock) add(new FluidDischargeBlock(), "fluid_discharge");
        storage_tank = (GUIBlock) add(new StorageTankBlock(), "fluid_storage_tank");
        metal_storage_tank = (GUIBlock) add(new MetalTankBlock(), "fluid_metal_tank");
        still = (GUIBlock) add(new StillBlock(), "still");
        fluid_pipe = add(new FluidPipeBlock(), "fluid_pipe");
        fluid_pipe_terminal = add(new TerminalFluidPipeBlock(), "fluid_pipe_terminal");
        fluid_switch = add(new BlockPowerSwitch(Fluids.fluidConduit_general), "fluid_switch");
        steel_frame = add(new BlockFrame(Material.PISTON, 0.75F, STEEL_BLAST_RESISTANCE), "steel_frame");

        final float hardness = 0.75F;
        item_conveyor = (GUIBlock) add(new BlockConveyor(Material.PISTON, hardness), "item_conveyor");
        item_filter_block = filter("item_filter_block", TileEntityBlockFilter.class, hardness);
        item_filter_food = filter("item_filter_food", TileEntityFoodFilter.class, hardness);
        item_filter_fuel = filter("item_filter_fuel", TileEntityFuelFilter.class, hardness);
        item_filter_inventory = filter("item_filter_inventory", TileEntityInventoryFilter.class, hardness);
        item_filter_ore = filter("item_filter_ore", TileEntityOreFilter.class, hardness);
        item_filter_plant = filter("item_filter_plant", TileEntityPlantFilter.class, hardness);
        item_filter_smelt = filter("item_filter_smelt", TileEntitySmeltableFilter.class, hardness);
        item_filter_overflow = filter("item_filter_overflow", TileEntityOverflowFilter.class, hardness);

        infinite_steam = (GUIBlock) add(new InfiniteEnergyBlock(new ConduitType("steam")), "infinite_steam");
        infinite_electricity = (GUIBlock) add(new InfiniteEnergyBlock(new ConduitType("electricity")), "infinite_electricity");
        infinite_quantum = (GUIBlock) add(new InfiniteEnergyBlock(new ConduitType("quantum")), "infinite_quantum");

        Block.Properties oilProperties = Block.Properties.create(Material.WATER).doesNotBlockMovement()
                .hardnessAndResistance(100.0F).variableOpacity();
        refined_oil_block = (BlockFlowingFluid) add(new InteractiveFluidBlock(Fluids.refined_oil,
                oilProperties, true, (world, entity) -> entity.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 200))),
                "refined_oil");
        crude_oil_block = (BlockFlowingFluid) add(new InteractiveFluidBlock(Fluids.crude_oil,
                oilProperties, true, (world, entity) -> entity.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 200, 2))),
                "crude_oil");

        ModSupport.initBlocks();
        initialized = true;
    }

    private static GUIBlock filter(String id, Class<? extends zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityConveyor> type,
            float hardness) {
        return (GUIBlock) add(new BlockConveyorFilter(Material.PISTON, hardness, type), id);
    }

    public static Block add(Block block, String id) {
        block.setRegistryName(PowerAdvantage.MODID, id);
        ALL_BLOCKS.put(id, block);
        return block;
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        init();
        event.getRegistry().registerAll(ALL_BLOCKS.values().toArray(new Block[0]));
    }

    @SubscribeEvent
    public static void registerBlockItems(RegistryEvent.Register<Item> event) {
        init();
        for (Map.Entry<String, Block> entry : ALL_BLOCKS.entrySet()) {
            Item.Properties properties = new Item.Properties();
            if (!"fluid_pipe_terminal".equals(entry.getKey())
                    && !(entry.getValue() instanceof BlockFlowingFluid)) {
                properties.group(ItemGroups.tab_powerAdvantage);
            }
            ItemBlock item = new ItemBlock(entry.getValue(), properties);
            item.setRegistryName(PowerAdvantage.MODID, entry.getKey());
            event.getRegistry().register(item);
        }
    }

    public static Map<String, Block> getModBlockRegistry() {
        return Collections.unmodifiableMap(ALL_BLOCKS);
    }
}
