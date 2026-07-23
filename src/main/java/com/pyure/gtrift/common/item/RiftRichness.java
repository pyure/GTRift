package com.pyure.gtrift.common.item;

public enum RiftRichness {
    SPARSE,
    NORMAL,
    RICH,
    EXTREMELY_RICH;

    public String getTranslationKey() {
        return "gtrift.richness." + name().toLowerCase();
    }
}
