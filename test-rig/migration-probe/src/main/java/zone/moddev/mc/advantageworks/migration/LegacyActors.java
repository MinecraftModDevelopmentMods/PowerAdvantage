package zone.moddev.mc.advantageworks.migration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.PlayerInteractionManager;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;

/** Source-version serializers, without linking to any Advantage implementation. */
final class LegacyActors {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String NAME = "AdvWorksPlayer";
    private static final UUID PLAYER = UUID.nameUUIDFromBytes(("OfflinePlayer:" + NAME).getBytes(StandardCharsets.UTF_8));
    private static final String WITNESS = "AdvWorksActor";

    static boolean witnessed(Entity entity) { return entity.getEntityData().hasKey(WITNESS); }

    static void build(MinecraftServer server, WorldServer world) throws Exception {
        if (!world.loadedEntityList.stream().noneMatch(LegacyActors::witnessed)) {
            throw new IllegalStateException("Actors already exist; use a new disposable copy");
        }
        for (int x = 128; x <= 191; x += 16) world.getChunkProvider().provideChunk(x >> 4, 0);
        for (int x = 140; x < 172; x++) {
            for (int z = 4; z < 13; z++) world.setBlockState(new BlockPos(x, 79, z), net.minecraft.init.Blocks.BEDROCK.getDefaultState(), 2);
        }
        EntityPlayerMP player = player(server, world);
        NBTTagCompound data = player.writeToNBT(new NBTTagCompound());
        data.setTag("Inventory", inventory());
        NBTTagList ender = inventory();
        while (ender.tagCount() > 27) ender.removeTag(ender.tagCount() - 1);
        data.setTag("EnderItems", ender);
        data.setFloat("Health", 17f);
        data.setInteger("XpLevel", 23);
        data.setInteger("XpTotal", 751);
        data.setFloat("XpP", 0.375f);
        data.setInteger("SelectedItemSlot", 7);
        data.setInteger("foodLevel", 16);
        data.setFloat("foodSaturationLevel", 3.5f);
        player.readFromNBT(data);
        player.setPositionAndRotation(144.5, 80, 8.5, 37, -12);
        world.getSaveHandler().getPlayerNBTManager().writePlayerData(player);
        NBTTagCompound actual = player.writeToNBT(new NBTTagCompound());
        world.getSaveHandler().saveWorldInfoWithPlayer(world.getWorldInfo(), actual);
        JsonObject definition = new JsonObject();
        definition.addProperty("playerName", NAME);
        definition.addProperty("playerUuid", PLAYER.toString());
        definition.addProperty("universalBucket", Item.REGISTRY.containsKey(new ResourceLocation("forge:bucketfilled")));
        definition.addProperty("minX", 128); definition.addProperty("maxX", 191);
        Files.write(new File("actor-fixture.json").toPath(), JSON.toJson(definition).getBytes(StandardCharsets.UTF_8));

        EntityMinecartChest cart = new EntityMinecartChest(world, 148.5, 80, 8.5);
        cart.setInventorySlotContents(0, stack("electricadvantage:lithium_battery", 3));
        cart.setInventorySlotContents(1, stack("steamadvantage:musket", 1));
        add(world, cart, "cart");
        EntityItem dropped = new EntityItem(world, 152.5, 80, 8.5, stack("steamadvantage:musket", 1));
        dropped.setPickupDelay(32767);
        add(world, dropped, "dropped-musket");
        EntityItem bucket = new EntityItem(world, 156.5, 80, 8.5, oilBucket());
        bucket.setPickupDelay(32767);
        add(world, bucket, "dropped-oil");
        EntityWolf wolf = new EntityWolf(world);
        wolf.setPositionAndRotation(160.5, 80, 8.5, 42, 0);
        wolf.setTamed(true); wolf.setOwnerId(PLAYER); wolf.setSitting(true);
        add(world, wolf, "owned-wolf");
        BlockPos pos = new BlockPos(164, 81, 8);
        world.setBlockState(pos, Block.REGISTRY.getObject(new ResourceLocation("electricadvantage:hydroelectric_generator")).getDefaultState(), 2);
        TileEntity parent = world.getTileEntity(pos);
        Entity turbine = null;
        for (String prefix : new String[] {"zone.moddev.mc", "com.mcmoddev", "cyano"}) {
            try {
                Class<?> type = Class.forName(prefix + ".electricadvantage.entities.HydroturbineEntity");
                turbine = (Entity) type.getConstructor(net.minecraft.world.World.class, TileEntity.class).newInstance(world, parent);
                break;
            } catch (ClassNotFoundException ignored) { }
        }
        if (turbine == null) throw new IllegalStateException("No source hydroturbine implementation");
        add(world, turbine, "hydroturbine");
    }

    private static EntityPlayerMP player(MinecraftServer server, WorldServer world) {
        return new EntityPlayerMP(server, world, new GameProfile(PLAYER, NAME), new PlayerInteractionManager(world));
    }

    private static NBTTagList inventory() {
        NBTTagList result = new NBTTagList();
        String[] names = {"steamadvantage:musket", "electricadvantage:lithium_battery", "electricadvantage:lead_acid_battery",
                "electricadvantage:alkaline_battery", "electricadvantage:nickel_hydride_battery", "poweradvantage:sprocket",
                "minecraft:dirt", "minecraft:apple"};
        int slot = 0;
        for (String name : names) {
            ItemStack stack = stack(name, name.endsWith("musket") ? 1 : 3);
            NBTTagCompound nbt = stack.writeToNBT(new NBTTagCompound());
            nbt.setByte("Slot", (byte) slot++); result.appendTag(nbt);
        }
        NBTTagCompound oil = oilBucket().writeToNBT(new NBTTagCompound());
        oil.setByte("Slot", (byte) slot); result.appendTag(oil);
        for (int armor = 0; armor < 4; armor++) {
            String[] pieces = {"boots", "leggings", "chestplate", "helmet"};
            ItemStack stack = stack("minecraft:iron_" + pieces[armor], 1);
            stack.setItemDamage(9 + armor);
            NBTTagCompound nbt = stack.writeToNBT(new NBTTagCompound());
            nbt.setByte("Slot", (byte) (100 + armor)); result.appendTag(nbt);
        }
        NBTTagCompound offhand = stack("minecraft:shield", 1).writeToNBT(new NBTTagCompound());
        offhand.setByte("Slot", (byte) -106); result.appendTag(offhand);
        return result;
    }

    private static ItemStack stack(String name, int count) {
        Item item = Item.REGISTRY.getObject(new ResourceLocation(name));
        if (item == null || !Item.REGISTRY.containsKey(new ResourceLocation(name))) throw new IllegalStateException("Missing witness item " + name);
        ItemStack result = new ItemStack(item, count);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("MigrationWitness", name);
        tag.setTag("Nested", new NBTTagCompound());
        tag.getCompoundTag("Nested").setString("OwnerNote", "keep-this-verbatim");
        if (name.contains("battery")) tag.setFloat("energy", 123f);
        result.setTagCompound(tag);
        if (item.isDamageable()) result.setItemDamage(5);
        if (name.endsWith(":musket")) {
            for (ResourceLocation enchantment : net.minecraft.enchantment.Enchantment.REGISTRY.getKeys()) {
                if (enchantment.toString().startsWith("steamadvantage:")) {
                    result.addEnchantment(net.minecraft.enchantment.Enchantment.REGISTRY.getObject(enchantment), 1);
                }
            }
        }
        return result;
    }

    private static ItemStack oilBucket() {
        if (!Item.REGISTRY.containsKey(new ResourceLocation("forge:bucketfilled"))) {
            return stack("poweradvantage:crude_oil", 1);
        }
        ItemStack stack = stack("forge:bucketfilled", 1);
        stack.getTagCompound().setString("FluidName", "crude_oil");
        stack.getTagCompound().setInteger("Amount", 1000);
        return stack;
    }

    private static void add(WorldServer world, Entity entity, String label) {
        entity.setUniqueId(UUID.nameUUIDFromBytes(("AdvWorksEntity:" + label).getBytes(StandardCharsets.UTF_8)));
        entity.getEntityData().setString(WITNESS, label);
        entity.motionX = 0; entity.motionY = 0; entity.motionZ = 0;
        if (!world.spawnEntity(entity)) throw new IllegalStateException("Failed to spawn " + label);
    }

    static void snapshot(MinecraftServer server, WorldServer world, String label, boolean readPlayer) throws Exception {
        net.minecraftforge.common.util.FakePlayer sentinel = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        sentinel.setPosition(128.5, 80, -16.5);
        if (!world.playerEntities.contains(sentinel)) world.playerEntities.add(sentinel);
        for (int x = 6; x <= 13; x++) {
            for (int z = -2; z <= 2; z++) world.getChunkProvider().provideChunk(x, z);
        }
        EntityPlayerMP player = player(server, world);
        if (readPlayer) {
            if (server.getPlayerList().readPlayerDataFromFile(player) == null) throw new IllegalStateException("No player file loaded");
            world.getSaveHandler().getPlayerNBTManager().writePlayerData(player);
        }
        JsonObject result = new JsonObject();
        result.addProperty("playerAdapter", readPlayer);
        result.addProperty("frozen", LegacyProbe.actorsFrozen(world));
        if (readPlayer) result.add("player", encode(player.writeToNBT(new NBTTagCompound())));
        JsonArray entities = new JsonArray();
        for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
            if (!witnessed(entity)) continue;
            NBTTagCompound nbt = new NBTTagCompound();
            if (!entity.writeToNBTAtomically(nbt)) throw new IllegalStateException("Could not serialize entity");
            JsonObject entry = encode(nbt);
            entry.addProperty("witness", entity.getEntityData().getString(WITNESS));
            entry.addProperty("ticksExisted", entity.ticksExisted);
            entities.add(entry);
        }
        result.add("entities", entities);
        result.addProperty("gameTime", world.getTotalWorldTime());
        Files.write(new File("actors-" + label + ".json").toPath(), JSON.toJson(result).getBytes(StandardCharsets.UTF_8));
        System.out.println("ADVACTORS COMPLETE " + label);
    }

    private static JsonObject encode(NBTTagCompound nbt) throws Exception {
        JsonObject result = new JsonObject();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.writeCompressed(nbt, bytes);
        result.addProperty("nbtBase64", Base64.getEncoder().encodeToString(bytes.toByteArray()));
        return result;
    }
}
