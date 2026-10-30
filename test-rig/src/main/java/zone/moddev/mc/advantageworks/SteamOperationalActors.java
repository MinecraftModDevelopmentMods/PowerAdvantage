package zone.moddev.mc.advantageworks;

import com.mojang.authlib.GameProfile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.item.ArmorStandEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketDirection;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.state.IntegerProperty;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

/** Per-case entities: no shared fake-player factory identity or mutable counters. */
final class SteamOperationalActors {
    private SteamOperationalActors() {}

    static void register(List<MachineScenario> cases) {
        elevator(cases);
        musket(cases);
    }

    private static void elevator(List<MachineScenario> cases) {
        for (String mode : new String[]{"up", "down", "no-power", "scarce"}) {
            final ArmorStandEntity[] passenger = new ArmorStandEntity[1];
            cases.add(new MachineScenario("steam/elevator-" + mode,
                    "A real passenger moves to the physical stop, one move costs exactly 32 steam, "
                    + "a steady signal never repeats the move, and insufficient steam leaves passenger/shaft unchanged.",
                    80, c -> {
                        c.block(8, 0, 8, "steamadvantage:steam_elevator");
                        c.block(8, 9, 8, "minecraft:stone");
                        boolean down = mode.equals("down");
                        if (down) {
                            for (int y = 1; y <= 6; y++) platform(c, y, y == 6 ? 4 : 0);
                            CompoundNBT patch = new CompoundNBT(); patch.putBoolean("up", true);
                            c.seed(8, 0, 8, patch);
                        } else c.block(9, 0, 8, "minecraft:redstone_block");
                        if (!mode.equals("no-power")) c.power(8, 0, 8, "steam", mode.equals("scarce") ? 31 : 64);
                        BlockPos base = c.at(8, 0, 8);
                        ArmorStandEntity rider = new ArmorStandEntity(c.world, base.getX() + 0.5,
                                base.getY() + (down ? 7 : 1) + 0.00390625, base.getZ() + 0.5);
                        rider.setNoGravity(true);
                        rider.setInvulnerable(true);
                        c.require(c.world.addEntity(rider), "Passenger spawned into the real server world");
                        passenger[0] = rider;
                        c.record("passenger/uuid", rider.getUniqueID().toString());
                        c.record("passenger/startY", rider.getPosY());
                    }, c -> {
                        ArmorStandEntity rider = passenger[0];
                        c.require(rider != null && rider.isAlive(), "Passenger survives the movement test");
                        c.record("passenger/endY", rider.getPosY());
                        c.record("passenger/naturalTicks", rider.ticksExisted);
                        c.require(rider.ticksExisted >= 75, "Passenger received natural server entity ticks");
                        boolean movedUp = mode.equals("up"), down = mode.equals("down");
                        double destination = c.at(8, 0, 8).getY() + (movedUp ? 7 : 1) + 0.00390625;
                        c.require(Math.abs(rider.getPosY() - destination) < 0.001,
                                "Passenger reaches exactly the declared physical destination or stays put without power");
                        c.require(rider.fallDistance == 0, "Elevator movement resets passenger fall distance");
                        float expected = mode.equals("no-power") ? 0 : mode.equals("scarce") ? 31 : 32;
                        c.require(c.energy(8, 0, 8, "steam") == expected,
                                "Exactly one 32-steam move, or no debit below operation cost, after 80 ticks");
                        for (int y = 1; y <= 8; y++) {
                            BlockState state = c.world.getBlockState(c.at(8, y, 8));
                            c.record("shaft/" + y, state.toString());
                            if (movedUp && y <= 6) {
                                c.require("steamadvantage:platform".equals(String.valueOf(state.getBlock().getRegistryName())),
                                        "Raised shaft contains physical platform segment " + y);
                                IntegerProperty height = (IntegerProperty) state.getBlock().getStateContainer().getProperty("height");
                                c.require(height != null && state.get(height) == (y == 6 ? 4 : 0),
                                        "Raised shaft/top collision height is preserved at " + y);
                            } else c.require(state.isAir(), "Shaft is clear at " + y + " after " + mode);
                        }
                        c.require(c.world.getBlockState(c.at(8, 9, 8)).getBlock() == net.minecraft.block.Blocks.STONE,
                                "Elevator preserves the real upper stop");
                        c.require(c.nbt(8, 0, 8).getBoolean("up") == movedUp,
                                "Saved latch agrees with physical movement, not just an NBT flag");
                    }));
        }
    }

    private static void platform(MachineContext c, int y, int height) {
        c.block(8, y, 8, "steamadvantage:platform");
        BlockState state = c.world.getBlockState(c.at(8, y, 8));
        IntegerProperty property = (IntegerProperty) state.getBlock().getStateContainer().getProperty("height");
        if (property == null) throw new IllegalStateException("Missing native platform height");
        c.world.setBlockState(c.at(8, y, 8), state.with(property, height), 3);
    }

    private static final class Shot {
        FakePlayer player;
        CowEntity target;
        ItemStack weapon;
        float expectedDamage;
        int expectedAmmo;
    }

    private static void musket(List<MachineScenario> cases) {
        for (String mode : new String[]{"last-cartridge", "no-ammo", "powderless", "infinity"}) {
            Shot shot = new Shot();
            cases.add(new MachineScenario("steam/musket-" + mode,
                    "Native fake-player use/release interactions prove rapid reload, ammunition policy, "
                    + "last-cartridge firing, configured target damage and one durability debit. "
                    + "The fake player is not manually ticked; this does not claim timed-reload coverage.",
                    40, c -> {
                        shootingRoom(c);
                        BlockPos muzzle = c.at(8, 0, 8);
                        UUID id = UUID.nameUUIDFromBytes(("SteamOperational/" + mode + "/" + muzzle)
                                .getBytes(StandardCharsets.UTF_8));
                        shot.player = new FakePlayer(c.world, new GameProfile(id, "SteamOperational"));
                        shot.player.connection = new ServerPlayNetHandler(c.world.getServer(),
                                new NetworkManager(PacketDirection.SERVERBOUND), shot.player) {
                            @Override public void sendPacket(IPacket<?> packet) {}
                        };
                        shot.player.interactionManager.setGameType(GameType.SURVIVAL);
                        shot.player.setPositionAndRotation(muzzle.getX() + 0.5, muzzle.getY(), muzzle.getZ() + 0.5, 0, 0);
                        Item weapon = ForgeRegistries.ITEMS.getValue(new ResourceLocation("steamadvantage:musket"));
                        c.require(weapon != null && weapon != net.minecraft.item.Items.AIR, "Native musket is registered");
                        shot.weapon = new ItemStack(weapon);
                        shot.player.setHeldItem(Hand.MAIN_HAND, shot.weapon);
                        if (!mode.equals("no-ammo")) enchant(c, shot.weapon, "steamadvantage:rapid_reload");
                        if (mode.equals("powderless")) enchant(c, shot.weapon, "steamadvantage:powderless");
                        if (mode.equals("infinity")) enchant(c, shot.weapon, "minecraft:infinity");
                        if (mode.equals("last-cartridge") || mode.equals("infinity")) {
                            Item cartridge = ForgeRegistries.ITEMS.getValue(new ResourceLocation("steamadvantage:blackpowder_cartridge"));
                            c.require(cartridge != null && cartridge != net.minecraft.item.Items.AIR, "Cartridge is registered");
                            shot.player.inventory.setInventorySlotContents(1, new ItemStack(cartridge));
                        }
                        shot.expectedAmmo = mode.equals("infinity") ? 1 : 0;
                        shot.target = EntityType.COW.create(c.world);
                        c.require(shot.target != null, "Native living target created");
                        shot.target.getAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(1000);
                        shot.target.setHealth(1000);
                        shot.target.setPositionAndRotation(muzzle.getX() + 0.5, muzzle.getY() + 0.5, muzzle.getZ() + 4.5, 0, 0);
                        shot.target.setNoAI(true); shot.target.setNoGravity(true); shot.target.enablePersistence();
                        c.require(c.world.addEntity(shot.target), "Living target added to the real server world");
                        c.record("target/uuid", shot.target.getUniqueID().toString());
                        c.record("target/initialHealth", shot.target.getHealth());
                        ActionResultType reload = weapon.onItemRightClick(c.world, shot.player, Hand.MAIN_HAND).getType();
                        c.record("musket/reload-result", reload.toString());
                        if (mode.equals("no-ammo")) {
                            c.require(reload == ActionResultType.FAIL, "Unloaded musket rejects use without ammunition");
                            shot.expectedDamage = 0;
                        } else {
                            c.require(reload == ActionResultType.SUCCESS, "Rapid-reload interaction succeeds");
                            c.require(shot.weapon.getOrCreateTag().getByte("loaded") == 1,
                                    "Reload interaction loads the chamber before the shot");
                            c.record("musket/ammo-after-reload", shot.player.inventory.getStackInSlot(1).getCount());
                            c.require(shot.player.inventory.getStackInSlot(1).getCount() == shot.expectedAmmo,
                                    "Reload consumes exactly one cartridge unless an ammunition-saving enchantment applies");
                            ActionResultType aim = weapon.onItemRightClick(c.world, shot.player, Hand.MAIN_HAND).getType();
                            c.record("musket/aim-result", aim.toString());
                            c.require(aim == ActionResultType.SUCCESS, "Loaded musket can aim even without a spare cartridge");
                            c.require(shot.player.isHandActive(), "Native player enters the use lifecycle");
                            shot.player.stopActiveHand();
                            shot.expectedDamage = shotDamage();
                        }
                    }, c -> {
                        c.record("target/finalHealth", shot.target.getHealth());
                        c.record("target/naturalTicks", shot.target.ticksExisted);
                        c.record("musket/durabilityDebit", shot.weapon.getDamage());
                        c.record("musket/finalAmmo", shot.player.inventory.getStackInSlot(1).getCount());
                        c.require(shot.target.isAlive() && shot.target.ticksExisted >= 35,
                                "Real target remains available after natural server ticks");
                        c.require(Math.abs(shot.target.getHealth() - (1000 - shot.expectedDamage)) < 0.001F,
                                "Target damage is exactly the configured shot damage, or zero for rejected use");
                        c.require(shot.weapon.getDamage() == (mode.equals("no-ammo") ? 0 : 1),
                                "Only a real shot debits one musket durability");
                        c.require(shot.player.inventory.getStackInSlot(1).getCount() == shot.expectedAmmo,
                                "Firing does not charge ammunition a second time");
                        c.require(shot.weapon.getOrCreateTag().getByte("loaded") == 0,
                                "The chamber is empty after firing or failed unladen use");
                        c.require(!shot.player.isCreative(), "All ammunition and durability measurements use survival mode");
                    }));
        }
    }

    private static float shotDamage() {
        try {
            float damage = Class.forName("zone.moddev.mc.steamadvantage.SteamAdvantage", false,
                    SteamOperationalActors.class.getClassLoader()).getDeclaredField("MUSKET_DAMAGE").getFloat(null);
            if (!Float.isFinite(damage) || damage <= 0 || damage >= 1000) throw new IllegalStateException("Unsupported configured musket damage");
            return damage;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Native musket damage contract unavailable", error);
        }
    }

    private static void enchant(MachineContext c, ItemStack weapon, String id) {
        Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(id));
        c.require(enchantment != null, "Registered enchantment " + id);
        weapon.addEnchantment(enchantment, 1);
    }

    private static void shootingRoom(MachineContext c) {
        for (int x = 6; x <= 10; x++) for (int y = 0; y <= 4; y++) for (int z = 6; z <= 16; z++) {
            if (x == 6 || x == 10 || y == 4 || z == 6 || z == 16) c.block(x, y, z, "minecraft:stone");
        }
    }
}
