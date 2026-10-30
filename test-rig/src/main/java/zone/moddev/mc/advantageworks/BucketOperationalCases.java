package zone.moddev.mc.advantageworks;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameType;
import net.minecraftforge.common.ForgeModContainer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.UniversalBucket;
import net.minecraftforge.fml.common.Loader;

/** Native item interactions run in setup; verification and restart inspection never create fluid. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber
public final class BucketOperationalCases {
    private static final int VOLUME = 1000;
    private static final int X = 12, Y = 2, Z = 12;

    private BucketOperationalCases() {}

    @net.minecraftforge.fml.common.eventhandler.SubscribeEvent
    public static void recordProofStartup(net.minecraftforge.event.world.WorldEvent.Load event) {
        String token = System.getenv("ADVWORKS_BUCKET_PROOF");
        if (token == null || !token.matches("[a-f0-9]{32}") || event.getWorld().isRemote
                || event.getWorld().provider.getDimension() != 0
                || !java.nio.file.Files.isRegularFile(java.nio.file.Paths.get("advantage-machine-operation.allow"))) return;
        com.google.gson.JsonObject proof = new com.google.gson.JsonObject();
        UniversalBucket bucket = ForgeModContainer.getInstance().universalBucket;
        proof.addProperty("token", token);
        proof.addProperty("enabled", FluidRegistry.isUniversalBucketEnabled());
        proof.addProperty("created", bucket != null);
        proof.addProperty("registered", bucket != null && bucket.getRegistryName() != null
                && Item.REGISTRY.getObject(bucket.getRegistryName()) == bucket);
        proof.addProperty("item", bucket == null || bucket.getRegistryName() == null ? "" : bucket.getRegistryName().toString());
        proof.addProperty("world", event.getWorld().getWorldInfo().getWorldName());
        com.google.gson.JsonObject mods = new com.google.gson.JsonObject();
        for (net.minecraftforge.fml.common.ModContainer mod : Loader.instance().getModList())
            mods.addProperty(mod.getModId(), mod.getVersion());
        proof.add("mods", mods);
        try {
            java.nio.file.Path first = java.nio.file.Paths.get("bucket-startup-operate.json");
            java.nio.file.Path path = java.nio.file.Files.exists(first)
                    ? java.nio.file.Paths.get("bucket-startup-restart.json") : first;
            java.nio.file.Files.write(path, proof.toString().getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE);
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot retain independent bucket startup proof", error);
        }
    }

    static void register(List<MachineScenario> cases) {
        cases.add(new MachineScenario("power/bucket/startup",
                "Forge's universal bucket is enabled, registered and available independently of Base Metals. "
                + "Standalone mode additionally rejects Steam, Electric and Base Metals.", 1,
                c -> {
                    c.block(6, Y, Z, "minecraft:chest");
                    startup(c);
                }, c -> {
                    startup(c);
                    c.require(c.tile(6, Y, Z) instanceof net.minecraft.inventory.IInventory,
                            "The startup witness remains present for exact cold-state inspection");
                }));
        for (String fluid : new String[]{"crude_oil", "refined_oil"}) roundTrip(cases, fluid);
    }

    private static UniversalBucket startup(MachineContext c) {
        c.record("bucket/enabled", FluidRegistry.isUniversalBucketEnabled());
        c.require(FluidRegistry.isUniversalBucketEnabled(), "Universal bucket opt-in is enabled");
        UniversalBucket bucket = ForgeModContainer.getInstance().universalBucket;
        c.record("bucket/created", bucket != null);
        c.require(bucket != null, "Forge actually created its universal bucket before our fluid setup");
        c.record("bucket/registered", bucket.getRegistryName() != null
                && Item.REGISTRY.getObject(bucket.getRegistryName()) == bucket);
        c.require(bucket.getRegistryName() != null
                && Item.REGISTRY.getObject(bucket.getRegistryName()) == bucket,
                "Universal bucket is a real registered item, not merely an enabled flag");
        c.record("bucket/item", bucket.getRegistryName().toString());
        for (String mod : new String[]{"basemetals", "steamadvantage", "electricadvantage", "orespawn"})
            c.record("bucket/mod/" + mod, Loader.isModLoaded(mod));
        if (Boolean.getBoolean("advworks.bucket.requireStandalone"))
            for (String mod : new String[]{"basemetals", "steamadvantage", "electricadvantage"})
                c.require(!Loader.isModLoaded(mod), "Standalone bucket proof excludes " + mod);
        return bucket;
    }

    private static final class Witness {
        Fluid fluid;
        FluidTank source;
        NBTTagCompound recovered;
    }

    private static void roundTrip(List<MachineScenario> cases, String name) {
        Witness witness = new Witness();
        cases.add(new MachineScenario("power/bucket/" + name,
                "One finite 1000-mB oil packet fills a native empty bucket, is placed through the registered "
                + "bucket's real right-click ray, and is collected through the vanilla empty bucket and Forge "
                + "event handler. The recovered registered bucket retains exact typed fluid NBT and amount "
                + "in a chest; no source block or extra fluid remains.", 20, c -> {
                    UniversalBucket bucket = startup(c);
                    witness.fluid = NativeFluids.get(name);
                    c.require(witness.fluid != null && witness.fluid.getBlock() != null,
                            "Oil has a registered fluid and placeable block: " + name);
                    c.require(FluidRegistry.getBucketFluids().contains(witness.fluid),
                            "Oil is registered with the universal bucket");
                    c.block(6, Y, Z, "minecraft:chest");
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                        c.block(X + dx, Y - 1, Z + dz, "minecraft:stone");
                    for (EnumFacing face : new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST}) {
                        BlockPos wall = c.at(X, Y, Z).offset(face);
                        c.block(wall.getX() - c.origin.getX(), Y, wall.getZ() - c.origin.getZ(), "minecraft:stone");
                    }
                    c.block(X, Y, Z, "minecraft:air");

                    witness.source = new FluidTank(new FluidStack(witness.fluid, VOLUME), VOLUME);
                    ItemStack emptyBucket = new ItemStack(Items.BUCKET);
                    ItemStack simulated = fill(emptyBucket, witness.source, false);
                    requireFilled(c, simulated, bucket, witness.fluid);
                    c.require(witness.source.getFluidAmount() == VOLUME, "Simulated filling does not drain the donor");
                    c.require(emptyBucket.getItem() == Items.BUCKET && count(emptyBucket) == 1
                            && FluidUtil.getFluidContained(emptyBucket) == null, "Filling does not mutate its input prototype");
                    ItemStack filled = fill(emptyBucket, witness.source, true);
                    requireFilled(c, filled, bucket, witness.fluid);
                    c.require(witness.source.getFluidAmount() == 0, "Actual filling drains exactly 1000 mB");

                    FluidTank scarce = new FluidTank(new FluidStack(witness.fluid, VOLUME - 1), VOLUME);
                    c.require(count(fill(new ItemStack(Items.BUCKET), scarce, true)) == 0,
                            "A 999-mB packet cannot manufacture a full bucket");
                    c.require(scarce.getFluidAmount() == VOLUME - 1, "Failed filling preserves the scarce control");

                    FakePlayer player = player(c, name);
                    player.setHeldItem(EnumHand.MAIN_HAND, filled);
                    requireRay(c, player, false, c.at(X, Y - 1, Z));
                    ActionResult<ItemStack> placed = click(c, player);
                    c.require(placed.getType() == EnumActionResult.SUCCESS, "Registered filled bucket right-click succeeds");
                    ItemStack returned = placed.getResult();
                    c.require(count(returned) == 1 && returned.getItem() == Items.BUCKET
                            && FluidUtil.getFluidContained(returned) == null, "Survival placement returns one empty bucket");
                    player.setHeldItem(EnumHand.MAIN_HAND, returned);
                    c.require(c.world.getBlockState(c.at(X, Y, Z)).getBlock() == witness.fluid.getBlock(),
                            "Placed block is the exact oil, not vanilla water");
                    c.require(c.world.getBlockState(c.at(X, Y, Z)).getBlock() instanceof IFluidBlock,
                            "Placed oil exposes its native drain contract");
                    FluidStack preview = ((IFluidBlock)witness.fluid.getBlock()).drain(c.world, c.at(X, Y, Z), false);
                    c.require(preview != null && preview.getFluid() == witness.fluid && preview.amount == VOLUME,
                            "Placed source represents exactly the one finite oil packet");
                    c.record("bucket/placedFluid", witness.fluid.getName());
                    c.record("bucket/placedAmount", preview.amount);

                    requireRay(c, player, true, c.at(X, Y, Z));
                    ActionResult<ItemStack> collected = click(c, player);
                    c.require(collected.getType() == EnumActionResult.SUCCESS,
                            "Vanilla empty bucket invokes Forge's real pickup event successfully");
                    ItemStack recovered = collected.getResult();
                    requireFilled(c, recovered, bucket, witness.fluid);
                    c.record("bucket/sourceGone", c.world.isAirBlock(c.at(X, Y, Z)));
                    c.require(c.world.isAirBlock(c.at(X, Y, Z)), "Collection removes the source exactly once");
                    witness.recovered = (NBTTagCompound) recovered.writeToNBT(new NBTTagCompound()).copy();
                    player.setHeldItem(EnumHand.MAIN_HAND, null);
                    c.inventory(6, Y, Z).setInventorySlotContents(0, recovered);
                    c.inventory(6, Y, Z).markDirty();
                    c.record("bucket/recoveredNbt", witness.recovered.toString());
                    c.record("bucket/donorRemaining", witness.source.getFluidAmount());
                }, c -> {
                    UniversalBucket bucket = startup(c);
                    ItemStack stored = c.inventory(6, Y, Z).getStackInSlot(0);
                    requireFilled(c, stored, bucket, witness.fluid);
                    c.require(witness.recovered.equals(stored.writeToNBT(new NBTTagCompound())),
                            "Exact recovered item and typed NBT survive ordinary world ticks");
                    c.require(witness.source.getFluidAmount() == 0, "The finite donor remains empty");
                    c.require(c.world.isAirBlock(c.at(X, Y, Z)), "No hidden or regenerated oil source remains");
                    for (int slot = 1; slot < c.inventory(6, Y, Z).getSizeInventory(); slot++)
                        c.require(count(c.inventory(6, Y, Z).getStackInSlot(slot)) == 0, "No duplicate output in chest slot " + slot);
                }));
    }

    private static void requireFilled(MachineContext c, ItemStack stack, UniversalBucket bucket, Fluid expected) {
        c.require(count(stack) == 1 && stack.getItem() == bucket, "Exactly one real universal bucket holds the oil");
        FluidStack contained = FluidUtil.getFluidContained(stack);
        c.require(contained != null && contained.getFluid() == expected && contained.amount == VOLUME,
                "Native bucket capability retains exact fluid identity and 1000-mB quantity");
        NBTTagCompound data = stack.writeToNBT(new NBTTagCompound());
        c.require(data.hasKey("id", 8) && data.hasKey("Count", 1) && data.getByte("Count") == 1
                && data.hasKey("tag", 10), "Stored item uses typed native registry/count/container NBT");
        NBTTagCompound tag = data.getCompoundTag("tag");
        c.require(tag.hasKey("FluidName", 8) && tag.getString("FluidName").equals(expected.getName())
                && tag.hasKey("Amount", 3) && tag.getInteger("Amount") == VOLUME,
                "Container NBT stores the exact fluid name and integer quantity");
        FluidStack restored = FluidUtil.getFluidContained(ItemStack.loadItemStackFromNBT(data));
        c.require(restored != null && restored.getFluid() == expected && restored.amount == VOLUME,
                "Serialized native item restores the same fluid packet");
    }

    private static ItemStack fill(ItemStack empty, FluidTank source, boolean execute) {
        ItemStack result = FluidUtil.tryFillContainer(empty, source, VOLUME, null, execute);
        return result;
    }

    private static ActionResult<ItemStack> click(MachineContext c, FakePlayer player) {
        ItemStack held = player.getHeldItem(EnumHand.MAIN_HAND);
        return held.getItem().onItemRightClick(held, c.world, player, EnumHand.MAIN_HAND);
    }

    private static FakePlayer player(MachineContext c, String fluid) {
        UUID uuid = UUID.nameUUIDFromBytes(("AdvWorksBucket/" + fluid + "/" + c.origin).getBytes(StandardCharsets.UTF_8));
        FakePlayer player = new FakePlayer(c.world, new GameProfile(uuid, "BucketWitness"));
        player.connection = new NetHandlerPlayServer(c.world.getMinecraftServer(),
                new NetworkManager(EnumPacketDirection.SERVERBOUND), player) {
            @Override public void sendPacket(Packet<?> packet) { }
        };
        player.interactionManager.setGameType(GameType.SURVIVAL);
        player.setPositionAndRotation(c.at(X, Y + 2, Z).getX() + 0.5,
                c.at(X, Y + 2, Z).getY(), c.at(X, Y + 2, Z).getZ() + 0.5, 0, 90);
        c.require(!player.capabilities.isCreativeMode && !player.isSpectator(), "Native bucket interaction uses survival");
        return player;
    }

    private static void requireRay(MachineContext c, FakePlayer player, boolean fluid, BlockPos expected) {
        Vec3d eye = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        RayTraceResult ray = c.world.rayTraceBlocks(eye, eye.addVector(0, -5, 0), fluid, !fluid, false);
        c.require(ray != null && ray.typeOfHit == RayTraceResult.Type.BLOCK
                && ray.getBlockPos().equals(expected) && ray.sideHit == EnumFacing.UP,
                "Native downward ray reaches the intended floor/source, not a nearby wall");
    }

    private static int count(ItemStack stack) {
        return NativeStacks.count(stack);
    }
}
