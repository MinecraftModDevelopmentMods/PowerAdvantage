package zone.moddev.mc.advantageworks;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.Commands;
import net.minecraft.init.Enchantments;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import net.minecraft.network.Packet;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.state.BooleanProperty;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.shapes.IBooleanFunction;
import net.minecraft.util.math.shapes.VoxelShapes;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolType;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Actual survival harvesting, not loot-JSON presence or a synthetic getDrops call. */
final class NativeHarvestProbe {
    private static final File MARKER = new File("advantage-native-harvest.allow");
    private static final BlockPos POS = new BlockPos(1200, 8, 1024);

    static void register() {
        if (MARKER.isFile()) MinecraftForge.EVENT_BUS.addListener(new NativeHarvestProbe()::starting);
    }

    private void starting(FMLServerStartingEvent event) {
        event.getCommandDispatcher().register(Commands.literal("advharvest")
                .requires(source -> source.hasPermissionLevel(4))
                .then(Commands.argument("label", StringArgumentType.word()).executes(context -> {
                    WorksController.getInstance().requireAuthorizedWorld(context.getSource().getServer());
                    try { run(context.getSource().getWorld(), StringArgumentType.getString(context, "label")); }
                    catch (Exception error) { throw new IllegalStateException("Native harvest witness failed", error); }
                    return 1;
                })));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void witness(WorldServer world, EntityPlayerMP player, JsonArray rows, String id,
            String blockId, int level, Item toolItem, boolean silk, boolean allowed,
            String dropId, int minimum, int maximum, int trials) {
        witness(world, player, rows, id, blockId, level, toolItem, silk, allowed, dropId, minimum, maximum, trials, -1);
    }

    private static void witness(WorldServer world, EntityPlayerMP player, JsonArray rows, String id,
            String blockId, int level, Item toolItem, boolean silk, boolean allowed,
            String dropId, int minimum, int maximum, int trials, int connectionMask) {
        JsonObject row = new JsonObject();
        row.addProperty("id", id);
        JsonArray samples = new JsonArray();
        JsonArray dropEvidence = new JsonArray();
        row.add("dropEvidence", dropEvidence);
        try {
            ResourceLocation key = new ResourceLocation(blockId);
            require(ForgeRegistries.BLOCKS.containsKey(key), "Missing real block: " + blockId);
            Block block = ForgeRegistries.BLOCKS.getValue(key);
            player.interactionManager.setGameType(GameType.SURVIVAL);
            for (int trial = 0; trial < trials; trial++) {
                JsonObject evidence = new JsonObject();
                evidence.addProperty("trial", trial);
                evidence.addProperty("worldTime", world.getGameTime());
                List<EntityItem> indexed = world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(POS).grow(2));
                evidence.addProperty("removedBeforeCleanup", indexed.stream().filter(entity -> entity.removed).count());
                evidence.addProperty("liveBeforeCleanup", indexed.stream().filter(entity -> !entity.removed).count());
                dropEvidence.add(evidence);
                harvestDrops(world, new AxisAlignedBB(POS).grow(2)).forEach(EntityItem::remove);
                int remaining = harvestDrops(world, new AxisAlignedBB(POS).grow(2)).size();
                evidence.addProperty("liveAfterCleanup", remaining);
                require(remaining == 0, "Harvest fixture retained a live item before placement");
                world.setBlockState(POS, net.minecraft.init.Blocks.AIR.getDefaultState(), 2);
                IBlockState requested = block.getDefaultState();
                if (connectionMask >= 0) {
                    int bit = 0;
                    for (EnumFacing direction : EnumFacing.values()) {
                        net.minecraft.state.IProperty<?> property = block.getStateContainer().getProperty(direction.getName());
                        require(property instanceof BooleanProperty, "Missing inherited conduit connection state");
                        boolean connected = (connectionMask & (1 << bit++)) != 0;
                        requested = requested.with((BooleanProperty) property, connected);
                        world.setBlockState(POS.offset(direction), connected ? block.getDefaultState()
                                : net.minecraft.init.Blocks.AIR.getDefaultState(), 2);
                    }
                }
                require(world.setBlockState(POS, requested, 2), "Cannot place harvest witness");
                IBlockState state = world.getBlockState(POS);
                require(state.equals(requested), "Native connection state changed before mining");
                boolean expectedSolid = blockId.endsWith("_ore") || blockId.equals("electricadvantage:electric_conduit");
                require(state.isSolid() == expectedSolid, "Legacy solid/non-solid property changed");
                row.addProperty("actualSolid", state.isSolid());
                boolean fullCollision = !VoxelShapes.compare(state.getCollisionShape(world, POS),
                        VoxelShapes.fullCube(), IBooleanFunction.NOT_SAME);
                require(fullCollision != blockId.equals("electricadvantage:electric_conduit"),
                        "Legacy wire/frame/scaffold collision geometry changed");
                row.addProperty("fullCollision", fullCollision);
                if (level >= 0) require(state.getHarvestTool() == ToolType.PICKAXE
                        && state.getHarvestLevel() == level, "Historical harvest level/tool changed");
                ItemStack tool = toolItem == null ? ItemStack.EMPTY : new ItemStack(toolItem);
                if (silk) tool.addEnchantment(Enchantments.SILK_TOUCH, 1);
                player.setHeldItem(EnumHand.MAIN_HAND, tool);
                require(state.canHarvestBlock(world, POS, player) == allowed, "Tool failure/success gate changed");
                require(player.interactionManager.tryHarvestBlock(POS) && world.isAirBlock(POS),
                        "Native PlayerInteractionManager did not remove the block");
                List<EntityItem> drops = harvestDrops(world, new AxisAlignedBB(POS).grow(2));
                JsonArray actualDrops = new JsonArray();
                for (EntityItem entity : drops) {
                    JsonObject actual = new JsonObject();
                    actual.addProperty("entityId", entity.getEntityId());
                    actual.addProperty("uuid", entity.getUniqueID().toString());
                    actual.addProperty("item", entity.getItem().getItem().getRegistryName().toString());
                    actual.addProperty("count", entity.getItem().getCount());
                    actual.addProperty("removed", entity.removed);
                    actualDrops.add(actual);
                }
                evidence.add("actualDrops", actualDrops);
                int count = 0;
                for (EntityItem entity : drops) {
                    ItemStack stack = entity.getItem();
                    require(stack.getItem().getRegistryName().toString().equals(dropId), "Wrong actual drop: " + stack);
                    count += stack.getCount();
                }
                require(count >= minimum && count <= maximum, "Actual drop quantity changed: " + count);
                samples.add(count);
            }
            row.addProperty("status", "PASS");
        } catch (AssertionError | RuntimeException error) {
            row.addProperty("status", "FAIL");
            row.addProperty("failure", error.toString());
        }
        row.addProperty("block", blockId);
        row.addProperty("historicalHarvestLevel", level);
        row.addProperty("harvestAllowed", allowed);
        row.addProperty("silkTouch", silk);
        row.addProperty("trials", trials);
        row.addProperty("connectionMask", connectionMask);
        row.add("actualDropCounts", samples);
        rows.add(row);
    }

    private static void run(WorldServer world, String label) throws Exception {
        require(MARKER.isFile() && label.matches("[A-Za-z0-9_-]+"), "Unmarked harvest runtime or unsafe label");
        File output = new File("advantage-works-results", "native-harvest-" + label + ".json");
        require(!output.exists(), "Refusing to overwrite harvest evidence");
        EntityPlayerMP player = new EntityPlayerMP(world.getServer(), world,
                new GameProfile(UUID.fromString("c791aefe-a372-4536-9251-27032c01f115"), "AdvWorksHarvest"),
                new PlayerInteractionManager(world));
        player.connection = new NetHandlerPlayServer(world.getServer(), new NetworkManager(EnumPacketDirection.SERVERBOUND), player) {
            @Override public void sendPacket(Packet<?> packet) {}
        };
        player.setPosition(POS.getX() + 0.5, POS.getY() + 1, POS.getZ() + 0.5);
        JsonArray rows = new JsonArray();
        String sulfur = "electricadvantage:sulfur_ore", lithium = "electricadvantage:li_ore";
        witness(world, player, rows, "sulfur/hand-fails", sulfur, 0, null, false, false, "", 0, 0, 1);
        witness(world, player, rows, "sulfur/axe-fails", sulfur, 0, Items.IRON_AXE, false, false, "", 0, 0, 1);
        witness(world, player, rows, "sulfur/wood", sulfur, 0, Items.WOODEN_PICKAXE, false, true, "electricadvantage:sulfur_powder", 1, 4, 8);
        witness(world, player, rows, "sulfur/stone", sulfur, 0, Items.STONE_PICKAXE, false, true, "electricadvantage:sulfur_powder", 1, 4, 8);
        witness(world, player, rows, "sulfur/silk", sulfur, 0, Items.WOODEN_PICKAXE, true, true, sulfur, 1, 1, 1);
        witness(world, player, rows, "lithium/hand-fails", lithium, 1, null, false, false, "", 0, 0, 1);
        witness(world, player, rows, "lithium/wood-fails", lithium, 1, Items.WOODEN_PICKAXE, false, false, "", 0, 0, 1);
        witness(world, player, rows, "lithium/gold-fails", lithium, 1, Items.GOLDEN_PICKAXE, false, false, "", 0, 0, 1);
        witness(world, player, rows, "lithium/axe-fails", lithium, 1, Items.IRON_AXE, false, false, "", 0, 0, 1);
        witness(world, player, rows, "lithium/stone", lithium, 1, Items.STONE_PICKAXE, false, true, lithium, 1, 1, 1);
        witness(world, player, rows, "lithium/diamond", lithium, 1, Items.DIAMOND_PICKAXE, false, true, lithium, 1, 1, 1);
        witness(world, player, rows, "lithium/silk", lithium, 1, Items.STONE_PICKAXE, true, true, lithium, 1, 1, 1);
        witness(world, player, rows, "frame/hand", "poweradvantage:steel_frame", -1, null, false, true, "poweradvantage:steel_frame", 1, 1, 1);
        witness(world, player, rows, "frame/silk", "poweradvantage:steel_frame", -1, Items.DIAMOND_PICKAXE, true, true, "poweradvantage:steel_frame", 1, 1, 1);
        for (int mask = 0; mask < 64; mask++) {
            for (String family : new String[] {"wire", "scaffold"}) {
                String identity = "electricadvantage:" + (family.equals("wire") ? "electric_conduit" : "electric_track");
                String prefix = family + "/" + mask;
                witness(world, player, rows, prefix + "/hand", identity, -1, null, false, true, identity, 1, 1, 1, mask);
                witness(world, player, rows, prefix + "/silk", identity, -1, Items.DIAMOND_PICKAXE, true, true, identity, 1, 1, 1, mask);
            }
        }
        JsonObject solar = new JsonObject();
        solar.addProperty("id", "solar/native-solidity");
        try {
            Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("electricadvantage:photovoltaic_generator"));
            require(block != null && block != net.minecraft.init.Blocks.AIR, "Missing native photovoltaic block");
            world.setBlockState(POS, block.getDefaultState(), 2);
            require(world.getBlockState(POS).isSolid(), "Legacy photovoltaic solid=true was lost");
            solar.addProperty("actualSolid", true);
            solar.addProperty("status", "PASS");
        } catch (AssertionError | RuntimeException error) {
            solar.addProperty("status", "FAIL"); solar.addProperty("failure", error.toString());
        }
        rows.add(solar);
        world.setBlockState(POS, net.minecraft.init.Blocks.AIR.getDefaultState(), 2);
        for (EnumFacing direction : EnumFacing.values()) world.setBlockState(POS.offset(direction), net.minecraft.init.Blocks.AIR.getDefaultState(), 2);
        harvestDrops(world, new AxisAlignedBB(POS).grow(2)).forEach(EntityItem::remove);
        JsonObject report = new JsonObject();
        report.addProperty("label", label);
        report.addProperty("minecraft", "1.13.2");
        report.addProperty("worldTime", world.getGameTime());
        report.addProperty("fixtureIsolation", "native113-live-drops-v1");
        report.addProperty("status", java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .allMatch(row -> "PASS".equals(row.getAsJsonObject().get("status").getAsString())) ? "PASS" : "FAIL");
        report.add("checks", rows);
        output.getParentFile().mkdirs();
        Files.write(output.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
        System.out.println("ADVHARVEST COMPLETE " + label);
    }

    static List<EntityItem> harvestDrops(net.minecraft.world.World world, AxisAlignedBB bounds) {
        // remove() only marks an entity; the chunk index can retain it until the next tick.
        return world.getEntitiesWithinAABB(EntityItem.class, bounds, entity -> !entity.removed);
    }
}
