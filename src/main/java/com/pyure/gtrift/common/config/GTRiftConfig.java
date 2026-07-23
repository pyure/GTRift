package com.pyure.gtrift.common.config;

import dev.toma.configuration.Configuration;
import dev.toma.configuration.config.Config;
import dev.toma.configuration.config.Configurable;
import dev.toma.configuration.config.format.ConfigFormats;

@Config(id = "gtrift")
public class GTRiftConfig {

    public static GTRiftConfig INSTANCE;
    private static final Object LOCK = new Object();

    @Configurable
    @Configurable.Comment("Mobs never spawn closer than this many blocks from the beacon.")
    @Configurable.Range(min = 0, max = 500)
    public int safeBufferDistance = 20;

    @Configurable
    @Configurable.Comment("Mobs never spawn farther than this many blocks from the beacon.")
    @Configurable.Range(min = 1, max = 1000)
    public int spawnRadius = 60;

    @Configurable
    @Configurable.Comment("Ticks between rift mob spawn attempts while a rift is open (20 ticks = 1 second).")
    @Configurable.Range(min = 1, max = 12000)
    public int spawnIntervalTicks = 100;

    @Configurable
    @Configurable.Comment("Percent chance a given mob spawn is biased toward the near band below, instead of the full ring.")
    @Configurable.Range(min = 0, max = 100)
    public int nearSpawnChancePercent = 50;

    @Configurable
    @Configurable.Comment("How much of the safeBufferDistance-to-spawnRadius range counts as \"near\", as a percent.")
    @Configurable.Range(min = 1, max = 100)
    public int nearSpawnBandPercent = 30;

    @Configurable
    @Configurable.Comment("Percent chance a given mob spawn's angle is biased toward the rift visual's direction from the beacon, instead of the full circle. Independent of and stacks with the near-band distance bias above.")
    @Configurable.Range(min = 0, max = 100)
    public int riftSlantChancePercent = 50;

    @Configurable
    @Configurable.Comment("Width, in degrees, of the arc centered on the rift's direction that counts as \"slanted\".")
    @Configurable.Range(min = 1, max = 360)
    public int riftSlantArcDegrees = 90;

    public static void init() {
        synchronized (LOCK) {
            if (INSTANCE == null) {
                INSTANCE = Configuration.registerConfig(GTRiftConfig.class, ConfigFormats.YAML)
                        .getConfigInstance();
            }
        }
    }
}
