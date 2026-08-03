package com.pyure.gtrift.common.network;

import com.pyure.gtrift.GTRift;
import com.pyure.gtrift.client.ClientAmbienceState;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

/**
 * First networking channel in this codebase. AmbienceSyncPacket is the only message on it so far,
 * server -> client only.
 */
public class GTRiftNetworking {

    private static final Logger LOGGER = LogManager.getLogger("gtrift");

    private static final String PROTOCOL_VERSION = "1";
    private static int nextPacketId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(GTRift.MOD_ID, "main"))
            .networkProtocolVersion(() -> PROTOCOL_VERSION)
            .clientAcceptedVersions(PROTOCOL_VERSION::equals)
            .serverAcceptedVersions(PROTOCOL_VERSION::equals)
            .simpleChannel();

    public static void register() {
        CHANNEL.registerMessage(nextPacketId++, AmbienceSyncPacket.class,
                AmbienceSyncPacket::encode,
                AmbienceSyncPacket::new,
                GTRiftNetworking::handleAmbienceSync);
    }

    // ClientAmbienceState has no Minecraft-client-only imports (see its own doc comment), so this
    // handler is safe to reference from common code — the enqueued work only ever actually runs when
    // a client receives the packet, which never happens on a dedicated server since it's the sender.
    private static void handleAmbienceSync(AmbienceSyncPacket packet, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            LOGGER.debug("Client received AmbienceSyncPacket beacon={} ramp={} active={} playMusic={}",
                    packet.beaconPos(), packet.ramp(), packet.active(), packet.playMusic());
            if (packet.active()) {
                ClientAmbienceState.put(packet.beaconPos(), packet.ramp(), packet.playMusic());
            } else {
                ClientAmbienceState.remove(packet.beaconPos());
            }
        });
        ctx.setPacketHandled(true);
    }

    public static void sendToPlayer(ServerPlayer player, AmbienceSyncPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
