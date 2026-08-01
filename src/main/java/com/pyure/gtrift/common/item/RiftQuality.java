package com.pyure.gtrift.common.item;

public enum RiftQuality {
    SPARSE,
    NORMAL,
    RICH,
    EXTREMELY_RICH;

    public String getTranslationKey() {
        return "gtrift.quality." + name().toLowerCase();
    }
}
