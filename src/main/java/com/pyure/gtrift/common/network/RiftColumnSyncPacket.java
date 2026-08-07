package com.pyure.gtrift.common.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/**
 * Server -> client only. Carries a tracked beacon's current column positions (see
 * RiftBeaconMachine.columnPositions / RiftAmbienceTracker) to any player within its dispatch radius,
 * or an active=false "clear" (empty columns list) once that beacon stops being RIFT_OPEN — for any
 * reason, including the controller block being broken outright — or the player drops out of range.
 * Unlike AmbienceSyncPacket's continuously-changing ramp, columns are static once generated, so this
 * is dispatched unconditionally alongside the ambience packet rather than diffed for "newly
 * subscribed only" — see RiftAmbienceTracker.dispatchBeaconPackets.
 */
public class RiftColumnSyncPacket {

    private final BlockPos beaconPos;
    private final boolean active;
    private final List<BlockPos> columns;

    public RiftColumnSyncPacket(BlockPos beaconPos, boolean active, List<BlockPos> columns) {
        this.beaconPos = beaconPos;
        this.active = active;
        this.columns = columns;
    }

    public RiftColumnSyncPacket(FriendlyByteBuf buf) {
        this(buf.readBlockPos(), buf.readBoolean(), buf.readList(FriendlyByteBuf::readBlockPos));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(beaconPos);
        buf.writeBoolean(active);
        buf.writeCollection(columns, FriendlyByteBuf::writeBlockPos);
    }

    public BlockPos beaconPos() {
        return beaconPos;
    }

    public boolean active() {
        return active;
    }

    public List<BlockPos> columns() {
        return columns;
    }
}
