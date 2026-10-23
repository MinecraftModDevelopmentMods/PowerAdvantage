package zone.moddev.mc.poweradvantage.init;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.poweradvantage.PowerAdvantage;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityBlockFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityConveyor;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityFoodFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityFuelFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityInventoryFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityOreFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityOverflowFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntityPlantFilter;
import zone.moddev.mc.poweradvantage.machines.conveyors.TileEntitySmeltableFilter;
import zone.moddev.mc.poweradvantage.machines.creative.InfiniteEnergyTileEntity;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.FluidDischargeTileEntity;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.FluidDrainTileEntity;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.MetalTankTileEntity;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.StillTileEntity;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.StorageTankTileEntity;
import zone.moddev.mc.poweradvantage.machines.fluidmachines.modsupport.TerminalFluidPipeTileEntity;
import cyano.poweradvantage.api.modsupport.rf.TileEntityRFElectricityConverter;
import cyano.poweradvantage.api.modsupport.rf.TileEntityRFQuantumConverter;
import cyano.poweradvantage.api.modsupport.rf.TileEntityRFSteamConverter;
import cyano.poweradvantage.api.modsupport.techreborn.TileEntityTRElectricityConverter;

/** Stable 1.13 tile entity type registrations. */
@Mod.EventBusSubscriber(modid = PowerAdvantage.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Entities {
    public static TileEntityType<TerminalFluidPipeTileEntity> PIPE_TERMINAL;
    public static TileEntityType<FluidDrainTileEntity> FLUID_DRAIN;
    public static TileEntityType<FluidDischargeTileEntity> FLUID_DISCHARGE;
    public static TileEntityType<StorageTankTileEntity> STORAGE_TANK;
    public static TileEntityType<MetalTankTileEntity> METAL_TANK;
    public static TileEntityType<StillTileEntity> STILL;
    public static TileEntityType<TileEntityConveyor> CONVEYOR;
    public static TileEntityType<TileEntityBlockFilter> FILTER_BLOCK;
    public static TileEntityType<TileEntityFoodFilter> FILTER_FOOD;
    public static TileEntityType<TileEntityFuelFilter> FILTER_FUEL;
    public static TileEntityType<TileEntityInventoryFilter> FILTER_INVENTORY;
    public static TileEntityType<TileEntityOreFilter> FILTER_ORE;
    public static TileEntityType<TileEntityPlantFilter> FILTER_PLANT;
    public static TileEntityType<TileEntitySmeltableFilter> FILTER_SMELT;
    public static TileEntityType<TileEntityOverflowFilter> FILTER_OVERFLOW;
    public static TileEntityType<InfiniteEnergyTileEntity> INFINITE_ENERGY;
    public static TileEntityType<TileEntityRFSteamConverter> RF_STEAM_CONVERTER;
    public static TileEntityType<TileEntityRFElectricityConverter> RF_ELECTRICITY_CONVERTER;
    public static TileEntityType<TileEntityRFQuantumConverter> RF_QUANTUM_CONVERTER;
    public static TileEntityType<TileEntityTRElectricityConverter> TR_ELECTRICITY_CONVERTER;

    private Entities() {}

    @SubscribeEvent
    public static void register(RegistryEvent.Register<TileEntityType<?>> event) {
        PIPE_TERMINAL = register(event, "tileentity.pipe_terminal", TerminalFluidPipeTileEntity::new);
        FLUID_DRAIN = register(event, "tileentity.fluid_drain", FluidDrainTileEntity::new);
        FLUID_DISCHARGE = register(event, "tileentity.fluid_discharge", FluidDischargeTileEntity::new);
        STORAGE_TANK = register(event, "tileentity.fluid_storage_tank", StorageTankTileEntity::new);
        METAL_TANK = register(event, "tileentity.fluid_metal_tank", MetalTankTileEntity::new);
        STILL = register(event, "tileentity.still", StillTileEntity::new);
        CONVEYOR = register(event, "item_conveyor", TileEntityConveyor::new);
        FILTER_BLOCK = register(event, "item_filter_block", TileEntityBlockFilter::new);
        FILTER_FOOD = register(event, "item_filter_food", TileEntityFoodFilter::new);
        FILTER_FUEL = register(event, "item_filter_fuel", TileEntityFuelFilter::new);
        FILTER_INVENTORY = register(event, "item_filter_inventory", TileEntityInventoryFilter::new);
        FILTER_ORE = register(event, "item_filter_ore", TileEntityOreFilter::new);
        FILTER_PLANT = register(event, "item_filter_plant", TileEntityPlantFilter::new);
        FILTER_SMELT = register(event, "item_filter_smelt", TileEntitySmeltableFilter::new);
        FILTER_OVERFLOW = register(event, "item_filter_overflow", TileEntityOverflowFilter::new);
        INFINITE_ENERGY = register(event, "infinite_energy_source", InfiniteEnergyTileEntity::new);
        RF_STEAM_CONVERTER = register(event, "rf_steam_converter_tileentity", TileEntityRFSteamConverter::new);
        RF_ELECTRICITY_CONVERTER = register(event, "rf_electricity_converter_tileentity", TileEntityRFElectricityConverter::new);
        RF_QUANTUM_CONVERTER = register(event, "rf_quantum_converter_tileentity", TileEntityRFQuantumConverter::new);
        TR_ELECTRICITY_CONVERTER = register(event, "tr_electricity_converter_tileentity", TileEntityTRElectricityConverter::new);
    }

    private static <T extends TileEntity> TileEntityType<T> register(
            RegistryEvent.Register<TileEntityType<?>> event, String id,
            java.util.function.Supplier<? extends T> factory) {
        TileEntityType<T> type = TileEntityType.Builder.<T>create(factory).build(null);
        type.setRegistryName(PowerAdvantage.MODID, id);
        event.getRegistry().register(type);
        return type;
    }
}
