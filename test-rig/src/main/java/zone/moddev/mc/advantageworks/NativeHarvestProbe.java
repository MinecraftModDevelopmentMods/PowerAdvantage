package zone.moddev.mc.advantageworks;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Enchantments;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/** Native survival harvesting and legacy collision/opacity checks in a marked disposable world. */
final class NativeHarvestProbe {
    private static final File MARKER = new File("advantage-native-harvest.allow");
    private static final BlockPos POS = new BlockPos(1200, 8, 1024);
    private static final String[] WATER_ONLY = {"wire/waterlogged", "scaffold/waterlogged", "frame/waterlogged"};

    static void registerCommands(FMLServerStartingEvent event) {
        if (!MARKER.isFile()) return;
        event.registerServerCommand(new CommandBase() {
            @Override public String getName() { return "advharvest"; }
            @Override public String getUsage(ICommandSender sender) { return "/advharvest <label>"; }
            @Override public int getRequiredPermissionLevel() { return 4; }
            @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
                if (args.length != 1) throw new CommandException(getUsage(sender));
                try { run(server, server.getWorld(0), args[0]); }
                catch (Exception error) { throw new IllegalStateException("Native harvest witness failed", error); }
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static Block block(String id) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
        require(block != null && block != net.minecraft.init.Blocks.AIR, "Missing real block: " + id);
        return block;
    }

    static List<EntityItem> harvestDrops(net.minecraft.world.World world, AxisAlignedBB bounds) {
        return world.getEntitiesWithinAABB(EntityItem.class, bounds, entity -> entity != null && !entity.isDead);
    }

    private static AxisAlignedBB bounds() { return new AxisAlignedBB(POS).grow(2); }

    private static void clearSite(WorldServer world) {
        harvestDrops(world, bounds()).forEach(EntityItem::setDead);
        world.setBlockState(POS, net.minecraft.init.Blocks.AIR.getDefaultState(), 2);
        for (EnumFacing direction : EnumFacing.values())
            world.setBlockState(POS.offset(direction), net.minecraft.init.Blocks.AIR.getDefaultState(), 2);
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
        row.addProperty("block", blockId);
        row.addProperty("historicalHarvestLevel", level);
        row.addProperty("harvestAllowed", allowed);
        row.addProperty("silkTouch", silk);
        row.addProperty("trials", trials);
        row.addProperty("connectionMask", connectionMask);
        JsonArray samples = new JsonArray(), audits = new JsonArray();
        row.add("actualDropCounts", samples);
        row.add("dropEvidence", audits);
        try {
            Block block = block(blockId);
            player.interactionManager.setGameType(GameType.SURVIVAL);
            for (int trial = 0; trial < trials; trial++) {
                JsonObject evidence = new JsonObject();
                evidence.addProperty("trial", trial);
                evidence.addProperty("worldTime", world.getTotalWorldTime());
                List<EntityItem> indexed = world.getEntitiesWithinAABB(EntityItem.class, bounds());
                evidence.addProperty("removedBeforeCleanup", indexed.stream().filter(entity -> entity.isDead).count());
                evidence.addProperty("liveBeforeCleanup", indexed.stream().filter(entity -> !entity.isDead).count());
                audits.add(evidence);
                clearSite(world);
                int remaining = harvestDrops(world, bounds()).size();
                evidence.addProperty("liveAfterCleanup", remaining);
                require(remaining == 0, "Harvest fixture retained a live item before placement");
                if (connectionMask >= 0) for (EnumFacing direction : EnumFacing.values()) {
                    boolean connected = (connectionMask & (1 << direction.getIndex())) != 0;
                    if (connected) world.setBlockState(POS.offset(direction), block.getDefaultState(), 2);
                }
                require(world.setBlockState(POS, block.getDefaultState(), 2), "Cannot place harvest witness");
                IBlockState state = block.getActualState(world.getBlockState(POS), world, POS);
                if (connectionMask >= 0) {
                    int actualMask = 0;
                    for (EnumFacing direction : EnumFacing.values()) {
                        IProperty<?> property = state.getPropertyKeys().stream()
                                .filter(candidate -> candidate.getName().equals(direction.getName())).findFirst().orElse(null);
                        require(property instanceof PropertyBool, "Missing native conduit connection property");
                        if (state.getValue((PropertyBool) property)) actualMask |= 1 << direction.getIndex();
                    }
                    row.addProperty("actualConnectionMask", actualMask);
                    require(actualMask == connectionMask, "Physical neighbours did not produce the requested connection mask");
                }
                checkGeometry(world, block, state, blockId, connectionMask, row);
                if (level >= 0) require("pickaxe".equals(block.getHarvestTool(state))
                        && block.getHarvestLevel(state) == level, "Historical harvest level/tool changed");
                ItemStack tool = toolItem == null ? ItemStack.EMPTY : new ItemStack(toolItem);
                if (silk) tool.addEnchantment(Enchantments.SILK_TOUCH, 1);
                player.setHeldItem(EnumHand.MAIN_HAND, tool);
                require(block.canHarvestBlock(world, POS, player) == allowed, "Tool failure/success gate changed");
                require(player.interactionManager.tryHarvestBlock(POS) && world.isAirBlock(POS),
                        "Native PlayerInteractionManager did not remove the block");
                List<EntityItem> drops = harvestDrops(world, bounds());
                JsonArray actualDrops = new JsonArray();
                evidence.add("actualDrops", actualDrops);
                int count = 0;
                for (EntityItem entity : drops) {
                    ItemStack stack = entity.getItem();
                    require(stack != null && !stack.isEmpty(), "Native item entity has an empty stack");
                    JsonObject actual = new JsonObject();
                    actual.addProperty("entityId", entity.getEntityId());
                    actual.addProperty("uuid", entity.getUniqueID().toString());
                    actual.addProperty("item", stack.getItem().getRegistryName().toString());
                    actual.addProperty("count", stack.getCount());
                    actual.addProperty("damage", stack.getMetadata());
                    actual.addProperty("tagged", stack.hasTagCompound());
                    actual.addProperty("removed", entity.isDead);
                    actual.addProperty("x", entity.posX); actual.addProperty("y", entity.posY); actual.addProperty("z", entity.posZ);
                    actualDrops.add(actual);
                    require(stack.getItem().getRegistryName().toString().equals(dropId), "Wrong actual drop: " + stack);
                    require(stack.getMetadata() == 0 && !stack.hasTagCompound(), "Unexpected drop metadata/NBT");
                    count += stack.getCount();
                }
                require(count >= minimum && count <= maximum, "Actual drop quantity changed: " + count);
                samples.add(new JsonPrimitive(count));
            }
            row.addProperty("status", "PASS");
        } catch (AssertionError | RuntimeException error) {
            row.addProperty("status", "FAIL");
            row.addProperty("failure", error.toString());
        }
        rows.add(row);
    }

    private static void checkGeometry(WorldServer world, Block block, IBlockState state,
            String blockId, int mask, JsonObject row) {
        boolean ore = blockId.endsWith("_ore");
        row.addProperty("actualFullCube", state.isFullCube());
        row.addProperty("actualOpaque", state.isOpaqueCube());
        row.addProperty("materialSolid", state.getMaterial().isSolid());
        require(state.isFullCube() == ore && state.isOpaqueCube() == ore && state.getMaterial().isSolid(),
                "Legacy cube/opacity/material properties changed");
        List<AxisAlignedBB> boxes = new ArrayList<>();
        block.addCollisionBoxToList(state, world, POS, bounds(), boxes, null, true);
        List<String> actual = new ArrayList<>(), expected = new ArrayList<>();
        JsonArray boxEvidence = new JsonArray();
        for (AxisAlignedBB box : boxes) {
            double[] coordinates = {box.minX - POS.getX(), box.minY - POS.getY(), box.minZ - POS.getZ(),
                    box.maxX - POS.getX(), box.maxY - POS.getY(), box.maxZ - POS.getZ()};
            actual.add(Arrays.toString(coordinates));
            JsonArray encoded = new JsonArray();
            for (double coordinate : coordinates) encoded.add(new JsonPrimitive(coordinate));
            boxEvidence.add(encoded);
        }
        row.add("collisionBoxes", boxEvidence);
        if (blockId.equals("electricadvantage:electric_conduit")) {
            expected.add(Arrays.toString(new double[]{.375, .375, .375, .625, .625, .625}));
            if ((mask & 2) != 0) expected.add(Arrays.toString(new double[]{.375, .375, .375, .625, 1, .625}));
            if ((mask & 1) != 0) expected.add(Arrays.toString(new double[]{.375, 0, .375, .625, .625, .625}));
            if (mask == 0 || (mask & 32) != 0) expected.add(Arrays.toString(new double[]{.375, .375, .375, 1, .625, .625}));
            if (mask == 0 || (mask & 16) != 0) expected.add(Arrays.toString(new double[]{0, .375, .375, .625, .625, .625}));
            if (mask == 0 || (mask & 8) != 0) expected.add(Arrays.toString(new double[]{.375, .375, .375, .625, .625, 1}));
            if (mask == 0 || (mask & 4) != 0) expected.add(Arrays.toString(new double[]{.375, .375, 0, .625, .625, .625}));
        } else expected.add(Arrays.toString(new double[]{0, 0, 0, 1, 1, 1}));
        Collections.sort(actual); Collections.sort(expected);
        require(actual.equals(expected), "Legacy collision boxes changed: " + actual);
    }

    private static void properties(WorldServer world, JsonArray rows, String id, String blockId, boolean opaque, int lightOpacity) {
        JsonObject row = new JsonObject();
        row.addProperty("id", id); row.addProperty("block", blockId);
        try {
            clearSite(world);
            Block block = block(blockId);
            require(world.setBlockState(POS, block.getDefaultState(), 2), "Cannot place property witness");
            IBlockState state = world.getBlockState(POS);
            row.addProperty("actualOpaque", state.isOpaqueCube());
            row.addProperty("actualFullCube", state.isFullCube());
            row.addProperty("lightOpacity", state.getLightOpacity(world, POS));
            require(state.isOpaqueCube() == opaque && state.isFullCube() == opaque,
                    "Legacy opacity/full-cube flags changed");
            require(state.getLightOpacity(world, POS) == lightOpacity, "Legacy light opacity changed");
            row.addProperty("status", "PASS");
        } catch (AssertionError | RuntimeException error) {
            row.addProperty("status", "FAIL"); row.addProperty("failure", error.toString());
        }
        rows.add(row);
    }

    private static void run(MinecraftServer server, WorldServer world, String label) throws Exception {
        require(MARKER.isFile() && new File("advantage-migration.allow").isFile()
                && world.provider.getDimension() == 0 && world.getWorldInfo().getTerrainType() == WorldType.FLAT
                && "AdvantageWorksMigration".equals(world.getWorldInfo().getWorldName())
                && label.matches("[A-Za-z0-9_-]+"), "Unmarked or unsafe harvest world/label");
        File output = new File("advantage-works-results", "native-harvest-" + label + ".json");
        require(!output.exists(), "Refusing to overwrite harvest evidence");
        EntityPlayerMP player = new EntityPlayerMP(server, world,
                new GameProfile(UUID.fromString("c791aefe-a372-4536-9251-27032c01f115"), "AdvWorksHarvest"),
                new PlayerInteractionManager(world));
        player.connection = new NetHandlerPlayServer(server, new NetworkManager(EnumPacketDirection.SERVERBOUND), player) {
            @Override public void sendPacket(Packet<?> packet) {}
        };
        player.setPosition(POS.getX() + .5, POS.getY() + 1, POS.getZ() + .5);
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

        properties(world, rows, "solar/native-solidity", "electricadvantage:photovoltaic_generator", true, 0);
        properties(world, rows, "frame/native-opacity", "poweradvantage:steel_frame", false, 0);
        for (String id : WATER_ONLY) {
            JsonObject row = new JsonObject(); row.addProperty("id", id);
            row.addProperty("status", "N/A");
            row.addProperty("reason", "Minecraft 1.12.2 has no waterlogged blockstate/fluid API");
            rows.add(row);
        }
        clearSite(world);
        JsonObject report = new JsonObject();
        report.addProperty("label", label); report.addProperty("minecraft", "1.12.2");
        report.addProperty("worldTime", world.getTotalWorldTime());
        report.addProperty("fixtureIsolation", "native112-live-drops-v1");
        report.addProperty("executedChecks", 272); report.addProperty("notApplicableChecks", 3);
        int passed = 0;
        for (com.google.gson.JsonElement row : rows) if ("PASS".equals(row.getAsJsonObject().get("status").getAsString())) passed++;
        report.addProperty("status", passed == 272 && rows.size() == 275 ? "PASS" : "FAIL");
        report.add("checks", rows);
        output.getParentFile().mkdirs();
        Files.write(output.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
        System.out.println("ADVHARVEST COMPLETE " + label);
    }
}
