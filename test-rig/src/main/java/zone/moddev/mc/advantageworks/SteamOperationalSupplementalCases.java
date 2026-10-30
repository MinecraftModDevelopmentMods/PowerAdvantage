package zone.moddev.mc.advantageworks;

import com.mojang.authlib.GameProfile;
import cyano.poweradvantage.api.ConduitType;
import cyano.poweradvantage.api.PowerRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.IPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketDirection;
import net.minecraft.network.play.ServerPlayNetHandler;
import net.minecraft.network.play.server.SEntityVelocityPacket;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.world.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.registries.ForgeRegistries;

/** Eight supplemental cases included by the Steam operating catalog. */
final class SteamOperationalSupplementalCases {
    private SteamOperationalSupplementalCases() {}

    static void register(List<MachineScenario> cases) {
        priority(cases);
        for (int level : new int[]{0, 1, 5}) recoil(cases, level);
        for (int level : new int[]{0, 1}) explosive(cases, level);
        occlusion(cases);
    }

    private static void priority(List<MachineScenario> cases) {
        for (boolean consumerFull : new boolean[]{false, true}) {
            cases.add(new MachineScenario("steam/extra-priority-" + (consumerFull ? "backup-eligible" : "consumer-first"),
                    "A finite source fills a normal consumer before backup storage. Disabling backup discharge "
                    + "prevents later relay masking the original allocation; a full-consumer control proves backup connectivity.",
                    80, c -> {
                        c.block(8, 4, 8, "steamadvantage:steam_boiler_coal");
                        c.block(9, 4, 8, "steamadvantage:steam_furnace");
                        c.block(7, 4, 8, "steamadvantage:steam_pipe");
                        c.block(6, 4, 8, "steamadvantage:steam_pipe");
                        c.block(5, 4, 8, "steamadvantage:steam_tank");
                        c.block(4, 4, 8, "minecraft:redstone_block");
                        c.power(8, 4, 8, "steam", 40);
                        if (consumerFull) c.power(9, 4, 8, "steam", 200);
                        PowerRequest backup = (PowerRequest) c.invoke(5, 4, 8, "getPowerRequest",
                                new Class<?>[]{ConduitType.class}, new ConduitType("steam"));
                        c.record("backup/requestPriority", backup.priority);
                        c.record("backup/requestAmount", backup.amount);
                        c.require(backup.amount == 10000, "Disabled discharge does not make empty backup ineligible to receive");
                        c.require(c.world.hasNeighborSignal(c.at(5, 4, 8)), "Only backup discharge is redstone-inhibited");
                        c.require(!c.world.hasNeighborSignal(c.at(8, 4, 8))
                                && !c.world.hasNeighborSignal(c.at(9, 4, 8)), "Source and consumer are not redstone-inhibited");
                    }, c -> {
                        float source = c.energy(8, 4, 8, "steam");
                        float consumer = c.energy(9, 4, 8, "steam");
                        float backup = c.energy(5, 4, 8, "steam");
                        c.record("allocation/totalRemaining", source + consumer + backup);
                        c.require(source == 0, "Finite source actually discharges through the live network");
                        if (consumerFull) {
                            c.require(backup > 30 && backup <= 40, "Connected backup receives genuine surplus from the finite source");
                            c.require(consumer > 190 && consumer <= 200, "Full-consumer control remains near capacity, without fabricated steam");
                        } else {
                            c.require(consumer > 30 && consumer <= 40, "Scarce finite steam reaches the normal consumer");
                            c.require(backup == 0, "Larger backup request never preempts the higher-priority consumer");
                        }
                        c.require(source + consumer + backup <= (consumerFull ? 240 : 40),
                                "Priority allocation conserves finite energy apart from idle leakage");
                    }));
        }
    }

    private static final class Shot {
        FakePlayer player;
        CowEntity impact, collateral;
        ItemStack weapon;
        float configuredDamage;
        int velocityPackets, immediateCollateralFire;
        float immediateCollateralHealth;
    }

    private static void recoil(List<MachineScenario> cases, int level) {
        Shot shot = new Shot();
        cases.add(new MachineScenario("steam/extra-recoil-" + level,
                "A real musket shot applies the declared horizontal recoil impulse and sends one native velocity packet "
                + "only when enchanted. This measures impulse/packet delivery, not a manually ticked fake player's travel.",
                40, c -> {
                    prepareShot(c, shot, "recoil-" + level, false);
                    if (level > 0) enchant(c, shot.weapon, "steamadvantage:recoil", level);
                    fire(c, shot);
                }, c -> {
                    verifyShot(c, shot, false, false);
                    Vector3d motion = shot.player.getDeltaMovement();
                    c.record("recoil/deltaX", motion.x); c.record("recoil/deltaY", motion.y); c.record("recoil/deltaZ", motion.z);
                    c.record("recoil/velocityPackets", shot.velocityPackets);
                    c.require(Math.abs(motion.x) < 0.00001 && Math.abs(motion.y) < 0.00001
                            && Math.abs(motion.z + 0.6 * level) < 0.00001,
                            "South-facing shot applies exactly -0.6 times recoil level on Z, without lateral or vertical drift");
                    c.require(shot.velocityPackets == (level == 0 ? 0 : 1),
                            "Native recoil velocity packet count agrees with the enchantment");
                }));
    }

    private static void explosive(List<MachineScenario> cases, int level) {
        Shot shot = new Shot();
        cases.add(new MachineScenario("steam/extra-explosive-" + level,
                "High Explosive must damage and ignite a real off-ray collateral target, with a nonexplosive control. "
                + "Finite targets and the level-one blast remain inside the bay; no TNT or flammable terrain is seeded.",
                40, c -> {
                    prepareShot(c, shot, "explosive-" + level, true);
                    if (level > 0) enchant(c, shot.weapon, "steamadvantage:high_explosive", level);
                    fire(c, shot);
                    shot.immediateCollateralFire = shot.collateral.getRemainingFireTicks();
                    shot.immediateCollateralHealth = shot.collateral.getHealth();
                    c.record("collateral/immediateFireTicks", shot.immediateCollateralFire);
                    c.record("collateral/immediateHealth", shot.immediateCollateralHealth);
                }, c -> {
                    verifyShot(c, shot, level > 0, false);
                    c.record("collateral/finalHealth", shot.collateral.getHealth());
                    c.record("collateral/naturalTicks", shot.collateral.tickCount);
                    c.require(shot.collateral.isAlive() && shot.collateral.tickCount >= 35,
                            "Collateral target receives real natural server ticks");
                    if (level > 0) {
                        c.require(shot.immediateCollateralHealth < 1000 && shot.collateral.getHealth() < 1000,
                                "Off-ray target takes real explosive damage, not merely an enchantment NBT marker");
                        c.require(shot.immediateCollateralFire > 0, "Explosion also ignites the off-ray target");
                    } else {
                        c.require(shot.immediateCollateralHealth == 1000 && shot.collateral.getHealth() == 1000,
                                "An ordinary shot cannot hurt the off-ray target");
                        c.require(shot.immediateCollateralFire <= 0, "An ordinary shot cannot ignite collateral");
                    }
                    inside(c, shot.collateral);
                }));
    }

    private static void occlusion(List<MachineScenario> cases) {
        Shot shot = new Shot();
        cases.add(new MachineScenario("steam/extra-musket-solid-wall",
                "A real solid wall before an aligned living target must stop the shot, while the round and durability "
                + "are still spent. Do not accept target damage through the wall as historical intent.",
                40, c -> {
                    prepareShot(c, shot, "solid-wall", false);
                    for (int x = 15; x <= 17; x++) for (int y = 8; y <= 11; y++) c.block(x, y, 13, "minecraft:stone");
                    fire(c, shot);
                }, c -> {
                    verifyShot(c, shot, false, true);
                    c.require(c.world.getBlockState(c.at(16, 9, 13)).getBlock() == net.minecraft.block.Blocks.STONE,
                            "Nonexplosive shot preserves the real stone obstruction");
                }));
    }

    private static void prepareShot(MachineContext c, Shot shot, String id, boolean collateral) {
        BlockPos muzzle = c.at(16, 8, 10);
        UUID uuid = UUID.nameUUIDFromBytes(("SteamOperationalExtra/" + id + "/" + muzzle).getBytes(StandardCharsets.UTF_8));
        shot.player = new FakePlayer(c.world, new GameProfile(uuid, "SteamOpExtras"));
        shot.player.connection = new ServerPlayNetHandler(c.world.getServer(), new NetworkManager(PacketDirection.SERVERBOUND), shot.player) {
            @Override public void send(IPacket<?> packet) {
                if (packet instanceof SEntityVelocityPacket) shot.velocityPackets++;
            }
        };
        shot.player.gameMode.setGameModeForPlayer(GameType.SURVIVAL);
        shot.player.moveTo(muzzle.getX() + 0.5, muzzle.getY(), muzzle.getZ() + 0.5, 0, 0);
        shot.player.setDeltaMovement(Vector3d.ZERO);
        shot.weapon = new ItemStack(item(c, "steamadvantage:musket"));
        shot.player.setItemInHand(Hand.MAIN_HAND, shot.weapon);
        shot.player.inventory.setItem(1, new ItemStack(item(c, "steamadvantage:blackpowder_cartridge"), 2));
        enchant(c, shot.weapon, "steamadvantage:rapid_reload", 1);
        try {
            shot.configuredDamage = Class.forName("zone.moddev.mc.steamadvantage.SteamAdvantage", false,
                    SteamOperationalSupplementalCases.class.getClassLoader()).getDeclaredField("MUSKET_DAMAGE").getFloat(null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Musket configuration contract is unavailable", error);
        }
        c.require(Float.isFinite(shot.configuredDamage) && shot.configuredDamage > 0 && shot.configuredDamage < 900,
                "Configured damage permits a surviving target for precise native measurements");
        shot.impact = target(c, 16.5, 8.5, 16.5);
        c.record("impact/uuid", shot.impact.getUUID().toString());
        if (collateral) {
            shot.collateral = target(c, 17.1, 8.5, 16.5);
            c.record("collateral/uuid", shot.collateral.getUUID().toString());
        }
        // Even a missed shot has an in-bay, nonflammable physical backstop.
        for (int y = 8; y <= 11; y++) c.block(16, y, 24, "minecraft:obsidian");
    }

    private static CowEntity target(MachineContext c, double x, double y, double z) {
        CowEntity target = EntityType.COW.create(c.world);
        c.require(target != null, "Native living target is available");
        BlockPos origin = c.at(0, 0, 0);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(1000);
        target.moveTo(origin.getX() + x, origin.getY() + y, origin.getZ() + z, 0, 0);
        target.setNoAi(true); target.setNoGravity(true); target.setPersistenceRequired();
        c.require(c.world.addFreshEntity(target), "Target enters the actual server world");
        return target;
    }

    private static void fire(MachineContext c, Shot shot) {
        Item weapon = shot.weapon.getItem();
        c.require(weapon.use(c.world, shot.player, Hand.MAIN_HAND).getResult() == ActionResultType.SUCCESS,
                "Native rapid reload interaction succeeds");
        c.require(shot.player.inventory.getItem(1).getCount() == 1
                && shot.weapon.getOrCreateTag().getByte("loaded") == 1, "Exactly one finite cartridge loads the chamber");
        c.require(weapon.use(c.world, shot.player, Hand.MAIN_HAND).getResult() == ActionResultType.SUCCESS
                && shot.player.isUsingItem(), "Native use interaction enters the aiming lifecycle");
        shot.player.releaseUsingItem();
    }

    private static void verifyShot(MachineContext c, Shot shot, boolean explosive, boolean occluded) {
        c.record("impact/finalHealth", shot.impact.getHealth());
        c.record("impact/naturalTicks", shot.impact.tickCount);
        c.record("musket/ammo", shot.player.inventory.getItem(1).getCount());
        c.record("musket/durabilityDebit", shot.weapon.getDamageValue());
        c.require(shot.impact.isAlive() && shot.impact.tickCount >= 35, "Impact target survives real natural server ticks");
        if (occluded) c.require(shot.impact.getHealth() == 1000, "Solid wall completely prevents target damage");
        else if (explosive) c.require(shot.impact.getHealth() <= 1000 - shot.configuredDamage,
                "Explosive shot delivers at least configured direct impact damage");
        else c.require(Math.abs(shot.impact.getHealth() - (1000 - shot.configuredDamage)) < 0.001F,
                "Unenchanted damage is exactly the configured direct impact");
        c.require(shot.weapon.getDamageValue() == 1 && shot.player.inventory.getItem(1).getCount() == 1,
                "Each shot consumes one durability and exactly one cartridge, without duplicating debit");
        c.require(shot.weapon.getOrCreateTag().getByte("loaded") == 0, "Shot leaves an empty chamber");
        inside(c, shot.impact);
    }

    private static void inside(MachineContext c, CowEntity target) {
        BlockPos min = c.at(0, 0, 0), max = c.at(31, 31, 31);
        c.record("target/position/" + target.getUUID(), new double[]{target.getX(), target.getY(), target.getZ()});
        c.require(target.getX() >= min.getX() && target.getX() < max.getX() + 1
                && target.getY() >= min.getY() && target.getY() < max.getY() + 1
                && target.getZ() >= min.getZ() && target.getZ() < max.getZ() + 1,
                "Live target remains confined to its isolated bay");
    }

    private static Item item(MachineContext c, String id) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
        c.require(item != null && item != net.minecraft.item.Items.AIR, "Registered item " + id);
        return item;
    }

    private static void enchant(MachineContext c, ItemStack stack, String id, int level) {
        Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(id));
        c.require(enchantment != null && level >= 1 && level <= enchantment.getMaxLevel(), "Registered supported enchantment " + id);
        stack.enchant(enchantment, level);
    }
}
