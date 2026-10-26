package zone.moddev.mc.poweradvantage.registry;

import cyano.poweradvantage.api.GUIBlock;
import cyano.poweradvantage.api.simple.SimpleMachineGUI;
import net.minecraft.block.Block;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.inventory.container.INamedContainerProvider;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.NetworkHooks;
import zone.moddev.mc.poweradvantage.PowerAdvantage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Legacy GUI definitions backed by Forge's registered container and screen factories. */
@Mod.EventBusSubscriber(modid = PowerAdvantage.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MachineGUIRegistry {
    private static final MachineGUIRegistry INSTANCE = new MachineGUIRegistry();
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final Map<Integer, ITileEntityGUI> DEFINITIONS = new ConcurrentHashMap<>();
    public static ContainerType<SimpleMachineGUI.Container> TYPE;

    private MachineGUIRegistry() {}

    public static MachineGUIRegistry getInstance() { return INSTANCE; }

    public static int addGUI(ITileEntityGUI gui) {
        int id = NEXT_ID.getAndIncrement();
        DEFINITIONS.put(id, gui);
        return id;
    }

    public void removeGUI(int id) { DEFINITIONS.remove(id); }

    @SubscribeEvent
    public static void registerTypes(RegistryEvent.Register<ContainerType<?>> event) {
        TYPE = IForgeContainerType.create(MachineGUIRegistry::createClientContainer);
        TYPE.setRegistryName(PowerAdvantage.MODID, "machine");
        event.getRegistry().register(TYPE);
    }

    private static SimpleMachineGUI.Container createClientContainer(
            int windowId, PlayerInventory inventory, PacketBuffer data) {
        BlockPos pos = data.readBlockPos();
        World world = inventory.player.world;
        TileEntity tile = world.getTileEntity(pos);
        Block block = world.getBlockState(pos).getBlock();
        if (!(block instanceof GUIBlock) || !(tile instanceof IInventory)) {
            throw new IllegalStateException("Missing machine for container at " + pos);
        }
        // Resolve the local block's definition; numeric registration order is not a network identity.
        return definition(((GUIBlock) block).getGuiID()).createContainer(windowId, inventory, (IInventory) tile);
    }

    private static SimpleMachineGUI definition(int id) {
        ITileEntityGUI gui = DEFINITIONS.get(id);
        if (!(gui instanceof SimpleMachineGUI)) {
            throw new IllegalStateException("No native machine GUI registered for " + id);
        }
        return (SimpleMachineGUI) gui;
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerScreens() {
        ScreenManager.<SimpleMachineGUI.Container, SimpleMachineGUI.GUIContainer>registerFactory(TYPE,
                (container, inventory, title) -> container.createScreen(inventory, title));
    }

    public static void open(ServerPlayerEntity player, World world, BlockPos pos, int id) {
        TileEntity tile = world.getTileEntity(pos);
        if (world.isRemote || player.world != world || !(tile instanceof IInventory)) return;
        SimpleMachineGUI gui = definition(id);
        NetworkHooks.openGui(player, new INamedContainerProvider() {
            @Override public Container createMenu(int windowId, PlayerInventory inventory, PlayerEntity owner) {
                return gui.createContainer(windowId, inventory, (IInventory) tile);
            }
            @Override public ITextComponent getDisplayName() {
                return tile instanceof net.minecraft.util.INameable
                        ? ((net.minecraft.util.INameable) tile).getDisplayName()
                        : new TranslationTextComponent(world.getBlockState(pos).getBlock().getTranslationKey());
            }
        }, pos);
    }

    public Object getServerGuiElement(int id, PlayerEntity player, World world, int x, int y, int z) {
        ITileEntityGUI gui = DEFINITIONS.get(id);
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        return gui == null || tile == null ? null : gui.getContainer(tile, player);
    }

    @OnlyIn(Dist.CLIENT)
    public Object getClientGuiElement(int id, PlayerEntity player, World world, int x, int y, int z) {
        ITileEntityGUI gui = DEFINITIONS.get(id);
        TileEntity tile = world.getTileEntity(new BlockPos(x, y, z));
        return gui == null || tile == null ? null : gui.getContainerGUI(tile, player);
    }
}
