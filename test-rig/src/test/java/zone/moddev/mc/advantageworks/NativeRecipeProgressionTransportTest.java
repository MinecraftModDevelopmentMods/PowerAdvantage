package zone.moddev.mc.advantageworks;

import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import org.junit.Test;
import static org.junit.Assert.*;

public class NativeRecipeProgressionTransportTest {
    @Test(expected = NullPointerException.class)
    public void channelLessOldFixtureReproducesTheNativeDispatcherFailure() {
        NetworkManager network = new NetworkManager(EnumPacketDirection.SERVERBOUND);
        assertNull(network.channel());
        NetworkDispatcher.get(network);
    }

    @Test public void realChannelActiveInitializesTheNetworkManagerAndProtocolAttribute() throws Exception {
        NetworkManager network = new NetworkManager(EnumPacketDirection.SERVERBOUND);
        EmbeddedChannel channel = open(network);
        try {
            assertSame(channel, network.channel());
            assertTrue(channel.isActive());
            assertSame(EnumConnectionState.HANDSHAKING, channel.attr(NetworkManager.PROTOCOL_ATTRIBUTE_KEY).get());
            assertNull("Forge's normal unassigned-dispatcher path must be readable", NetworkDispatcher.get(network));
        } finally { channel.finishAndReleaseAll(); }
    }

    @Test public void closingTheFixtureReleasesItsChannel() throws Exception {
        NetworkManager network = new NetworkManager(EnumPacketDirection.SERVERBOUND);
        EmbeddedChannel channel = open(network);
        channel.finishAndReleaseAll();
        assertFalse(channel.isActive());
        assertFalse(channel.isOpen());
    }

    private static EmbeddedChannel open(NetworkManager network) throws Exception {
        Method method = NativeRecipeProgressionProbe.class.getDeclaredMethod("recipeChannel", NetworkManager.class);
        method.setAccessible(true);
        return (EmbeddedChannel)method.invoke(null, network);
    }
}
