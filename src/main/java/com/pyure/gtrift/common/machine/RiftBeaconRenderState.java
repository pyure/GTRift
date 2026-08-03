package com.pyure.gtrift.common.machine;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Drives the Rift Beacon controller's front-face overlay texture — mirrors how GTCEu's own machines
 * key their overlay swap on a custom {@code MachineRenderState} property (e.g.
 * {@code GTMachineModelProperties.CHARGER_STATE} on the charger machine), just addon-local since this
 * one has no built-in GTCEu equivalent. See assets/gtrift/models/block/machine/rift_beacon.json for
 * the variant keying that consumes this property's serialized names.
 */
public enum RiftBeaconRenderState implements StringRepresentable {
    INACTIVE("inactive"),
    CHARGING("charging"),
    RIFT_OPEN("rift_open");

    public static final EnumProperty<RiftBeaconRenderState> PROPERTY =
            EnumProperty.create("rift_state", RiftBeaconRenderState.class);

    private final String serializedName;

    RiftBeaconRenderState(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }
}
