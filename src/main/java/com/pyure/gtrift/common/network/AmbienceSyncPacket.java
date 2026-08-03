package com.pyure.gtrift.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> client only. Carries a single tracked beacon's current ambience ramp to any player
 * within its dispatch radius (see RiftAmbienceTracker), or an active=false "clear" for a player who
 * just dropped out of that radius (including after a dimension change or the beacon's own fade-out
 * finishing).
 */
public class AmbienceSyncPacket {

    private final BlockPos beaconPos;
    private final float ramp;
    private final boolean active;
    private final boolean playMusic;

    public AmbienceSyncPacket(BlockPos beaconPos, float ramp, boolean active, boolean playMusic) {
        this.beaconPos = beaconPos;
        this.ramp = ramp;
        this.active = active;
        this.playMusic = playMusic;
    }

    public AmbienceSyncPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readFloat(), buf.readBoolean(), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(beaconPos);
        buf.writeFloat(ramp);
        buf.writeBoolean(active);
        buf.writeBoolean(playMusic);
    }

    public BlockPos beaconPos() {
        return beaconPos;
    }

    public float ramp() {
        return ramp;
    }

    public boolean active() {
        return active;
    }

    /** state == RIFT_OPEN && enableRiftMusic — deliberately not gated on encounter radius, see RiftAmbienceTracker. */
    public boolean playMusic() {
        return playMusic;
    }
}
