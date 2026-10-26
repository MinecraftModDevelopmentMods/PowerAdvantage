package zone.moddev.mc.poweradvantage.init;

import java.util.IdentityHashMap;
import java.util.Map;

import cyano.poweradvantage.api.ConduitType;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.IFluidState;
import net.minecraft.item.Item;
import net.minecraft.state.StateContainer;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fml.common.Mod;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/** Native Forge fluids and their persistent PowerAdvantage conduit identities. */
@Mod.EventBusSubscriber(modid = PowerAdvantage.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Fluids {
    public static final ConduitType fluidConduit_general = new ConduitType("fluid");
    private static final Map<Fluid, ConduitType> FLUID_TYPES = new IdentityHashMap<>();

    public static ConduitType fluidConduit_water;
    public static ConduitType fluidConduit_lava;
    public static PowerFluid.Source crude_oil;
    public static PowerFluid.Flowing flowing_crude_oil;
    public static PowerFluid.Source refined_oil;
    public static PowerFluid.Flowing flowing_refined_oil;

    private static boolean initialized;

    private Fluids() {}

    public static synchronized void init() {
        if (initialized) return;
        fluidConduit_water = registerNewFluid(net.minecraft.fluid.Fluids.WATER);
        fluidConduit_lava = registerNewFluid(net.minecraft.fluid.Fluids.LAVA);

        flowing_crude_oil = new PowerFluid.Flowing(Kind.CRUDE);
        crude_oil = new PowerFluid.Source(Kind.CRUDE);
        flowing_refined_oil = new PowerFluid.Flowing(Kind.REFINED);
        refined_oil = new PowerFluid.Source(Kind.REFINED);

        register("flowing_crude_oil", flowing_crude_oil);
        register("crude_oil", crude_oil);
        register("flowing_refined_oil", flowing_refined_oil);
        register("refined_oil", refined_oil);
        registerNewFluid(crude_oil);
        registerNewFluid(flowing_crude_oil);
        registerNewFluid(refined_oil);
        registerNewFluid(flowing_refined_oil);
        initialized = true;
    }

    private static void register(String name, Fluid fluid) {
        fluid.setRegistryName(PowerAdvantage.MODID, name);
    }

    @SubscribeEvent
    public static void registerFluids(RegistryEvent.Register<Fluid> event) {
        event.getRegistry().registerAll(crude_oil, flowing_crude_oil, refined_oil, flowing_refined_oil);
    }

    public static ConduitType registerNewFluid(Fluid fluid) {
        ResourceLocation id = fluid.getRegistryName();
        ConduitType type = new ConduitType(id == null ? "unknown_fluid" : id.toString());
        FLUID_TYPES.put(fluid, type);
        return type;
    }

    public static boolean isFluidType(ConduitType type) {
        return type.equals(fluidConduit_general) || FLUID_TYPES.containsValue(type);
    }

    public static ConduitType fluidToConduitType(Fluid fluid) {
        ConduitType type = FLUID_TYPES.get(fluid);
        return type == null ? registerNewFluid(fluid) : type;
    }

    public static Fluid conduitTypeToFluid(ConduitType type) {
        for (Map.Entry<Fluid, ConduitType> entry : FLUID_TYPES.entrySet()) {
            if (ConduitType.areSameType(entry.getValue(), type)) return entry.getKey();
        }
        return null;
    }

    public static Fluid byName(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        ResourceLocation id = name.indexOf(':') >= 0 ? new ResourceLocation(name)
                : new ResourceLocation(PowerAdvantage.MODID, name);
        Fluid fluid = registeredFluid(id);
        if (fluid == null && name.indexOf(':') < 0) {
            fluid = registeredFluid(new ResourceLocation("minecraft", name));
        }
        return fluid;
    }

    private static Fluid registeredFluid(ResourceLocation id) {
        // Forge's defaulted fluid registry returns EMPTY, not null, for unknown names.
        // Do not let a missing poweradvantage:water hide the legacy vanilla fallback.
        return net.minecraftforge.registries.ForgeRegistries.FLUIDS.containsKey(id)
                ? net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(id) : null;
    }

    public enum Kind { CRUDE, REFINED }

    public abstract static class PowerFluid extends ForgeFlowingFluid {
        private final Kind kind;

        protected PowerFluid(Kind kind) {
            super(properties(kind));
            this.kind = kind;
        }
        public Kind getKind() { return kind; }

        private static ForgeFlowingFluid.Properties properties(Kind kind) {
            String name = kind == Kind.CRUDE ? "crude_oil" : "refined_oil";
            return new ForgeFlowingFluid.Properties(
                    () -> kind == Kind.CRUDE ? crude_oil : refined_oil,
                    () -> kind == Kind.CRUDE ? flowing_crude_oil : flowing_refined_oil,
                    FluidAttributes.builder(new ResourceLocation(PowerAdvantage.MODID, "blocks/" + name + "_still"),
                            new ResourceLocation(PowerAdvantage.MODID, "blocks/" + name + "_flow"))
                            .translationKey("fluid.poweradvantage." + name).color(0xFFFFFFFF)
                            .density(kind == Kind.CRUDE ? 850 : 720)
                            .viscosity(kind == Kind.CRUDE ? 6000 : 1000).temperature(300))
                    .bucket(() -> kind == Kind.CRUDE ? Items.crude_oil_bucket : Items.refined_oil_bucket)
                    .block(() -> kind == Kind.CRUDE ? Blocks.crude_oil_block : Blocks.refined_oil_block)
                    .slopeFindDistance(4).levelDecreasePerBlock(1)
                    .tickRate(kind == Kind.CRUDE ? 15 : 10).explosionResistance(100.0F);
        }

        public static final class Flowing extends PowerFluid {
            public Flowing(Kind kind) { super(kind); }
            @Override protected void fillStateContainer(StateContainer.Builder<Fluid, IFluidState> builder) {
                super.fillStateContainer(builder);
                builder.add(LEVEL_1_8);
            }
            @Override public int getLevel(IFluidState state) { return state.get(LEVEL_1_8); }
            @Override public boolean isSource(IFluidState state) { return false; }
        }

        public static final class Source extends PowerFluid {
            public Source(Kind kind) { super(kind); }
            @Override public int getLevel(IFluidState state) { return 8; }
            @Override public boolean isSource(IFluidState state) { return true; }
        }
    }
}
