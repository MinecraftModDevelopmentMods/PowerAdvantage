package zone.moddev.mc.poweradvantage.init;

import java.util.IdentityHashMap;
import java.util.Map;

import cyano.poweradvantage.api.ConduitType;
import net.minecraft.block.BlockFlowingFluid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.IFluidState;
import net.minecraft.item.Item;
import net.minecraft.state.StateContainer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.IRegistry;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReaderBase;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

/** Native 1.13 fluids and their PowerAdvantage conduit identities. */
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
        fluidConduit_water = registerNewFluid(net.minecraft.init.Fluids.WATER);
        fluidConduit_lava = registerNewFluid(net.minecraft.init.Fluids.LAVA);

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
        IRegistry.field_212619_h.put(new ResourceLocation(PowerAdvantage.MODID, name), fluid);
        for (IFluidState state : fluid.getStateContainer().getValidStates()) {
            Fluid.STATE_REGISTRY.add(state);
        }
    }

    public static ConduitType registerNewFluid(Fluid fluid) {
        ResourceLocation id = IRegistry.field_212619_h.getKey(fluid);
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
        Fluid fluid = IRegistry.field_212619_h.func_212608_b(id);
        if (fluid == null && name.indexOf(':') < 0) {
            fluid = IRegistry.field_212619_h.func_212608_b(new ResourceLocation("minecraft", name));
        }
        return fluid;
    }

    public enum Kind { CRUDE, REFINED }

    public abstract static class PowerFluid extends FlowingFluid {
        private final Kind kind;

        protected PowerFluid(Kind kind) { this.kind = kind; }
        public Kind getKind() { return kind; }

        @Override public Fluid getFlowingFluid() {
            return kind == Kind.CRUDE ? flowing_crude_oil : flowing_refined_oil;
        }
        @Override public Fluid getStillFluid() {
            return kind == Kind.CRUDE ? crude_oil : refined_oil;
        }
        @Override public Item getFilledBucket() {
            return kind == Kind.CRUDE ? Items.crude_oil_bucket : Items.refined_oil_bucket;
        }
        @Override @OnlyIn(Dist.CLIENT) public BlockRenderLayer getRenderLayer() {
            return BlockRenderLayer.TRANSLUCENT;
        }
        @Override protected boolean canSourcesMultiply() { return false; }
        @Override protected void beforeReplacingBlock(IWorld world, BlockPos pos, IBlockState state) {
            state.dropBlockAsItem(world.getWorld(), pos, 0);
        }
        @Override public int getSlopeFindDistance(IWorldReaderBase world) { return 4; }
        @Override public int getLevelDecreasePerBlock(IWorldReaderBase world) { return 1; }
        @Override public int getTickRate(IWorldReaderBase world) { return kind == Kind.CRUDE ? 15 : 10; }
        @Override protected float getExplosionResistance() { return 100.0F; }
        @Override public boolean isEquivalentTo(Fluid fluid) {
            return fluid == getStillFluid() || fluid == getFlowingFluid();
        }
        @Override protected boolean canOtherFlowInto(IFluidState state, Fluid fluid, EnumFacing direction) {
            return direction == EnumFacing.DOWN && !fluid.isEquivalentTo(this);
        }
        @Override public IBlockState getBlockState(IFluidState state) {
            BlockFlowingFluid block = kind == Kind.CRUDE ? Blocks.crude_oil_block : Blocks.refined_oil_block;
            return block.getDefaultState().with(BlockFlowingFluid.LEVEL, Integer.valueOf(getLevelFromState(state)));
        }

        public static final class Flowing extends PowerFluid {
            public Flowing(Kind kind) { super(kind); }
            @Override protected void fillStateContainer(StateContainer.Builder<Fluid, IFluidState> builder) {
                super.fillStateContainer(builder);
                builder.add(LEVEL_1_TO_8);
            }
            @Override public int getLevel(IFluidState state) { return state.get(LEVEL_1_TO_8); }
            @Override public boolean isSource(IFluidState state) { return false; }
        }

        public static final class Source extends PowerFluid {
            public Source(Kind kind) { super(kind); }
            @Override public int getLevel(IFluidState state) { return 8; }
            @Override public boolean isSource(IFluidState state) { return true; }
        }
    }
}
