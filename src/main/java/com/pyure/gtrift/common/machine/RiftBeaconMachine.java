package com.pyure.gtrift.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;

import com.pyure.gtrift.common.config.GTRiftConfig;
import com.pyure.gtrift.common.data.RiftMobPool;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.ButtonWidget;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.joml.Vector3f;

import java.util.Locale;

public class RiftBeaconMachine extends MultiblockControllerMachine implements IFancyUIMachine {

    // Mob.checkDespawn() only makes a hostile mob despawn-eligible once its noActionTime exceeds
    // 600 ticks (30s), then rolls a 1/800 chance per tick beyond the 32-block "safe" radius. We
    // periodically reset noActionTime to 0 for every mob in the beacon's area (in quadrants, to
    // spread the cost) to keep rift mobs alive while a rift is open. The 0.9 margin means a full
    // 4-quadrant cycle finishes at 540 ticks, not 600 — a stationary mob's worst-case gap between
    // resets stays under the threshold with room to spare, so ordinary tick-order jitter (beaconTick
    // running a tick later than a mob's own aiStep, a brief server hitch, etc.) can't let a mob
    // cross into despawn-roll eligibility before its quadrant comes back around.
    private static final int DESPAWN_THRESHOLD_TICKS = 600;
    private static final double DESPAWN_SAFETY_MARGIN = 0.9;
    private static final int QUADRANT_COUNT = 4;
    private static final int TICKS_PER_QUADRANT =
            (int) (DESPAWN_THRESHOLD_TICKS * DESPAWN_SAFETY_MARGIN) / QUADRANT_COUNT;

    // Persistent rift-open visual: a short twisting stack of rotating particle rings anchored
    // somewhere out in the spawn ring (not at the beacon) — see emitRiftVisual(). Tuned up twice
    // after in-game looks: first pass made it denser/taller/faster-refreshing, but particles still
    // had no actual sideways velocity (sendParticles randomizes offset/speed as jitter unless
    // count=0, in which case they're an exact velocity vector — see emitRiftVisual), so nothing
    // visibly rotated. VISUAL_ROTATION_SPEED/LIFT are the real per-particle tangential velocity.
    private static final int VISUAL_EFFECT_INTERVAL_TICKS = 2;
    private static final float VISUAL_ROTATION_STEP_DEGREES = 10f; // ~5°/tick vs original 4°/tick
    private static final double VISUAL_RING_RADIUS = 1.2;
    private static final int VISUAL_RING_PARTICLE_COUNT = 10;
    private static final int VISUAL_RING_COUNT = 6; // was 3 — doubles both height and density
    private static final double VISUAL_RING_HEIGHT_STEP = 0.8;
    private static final double VISUAL_ROTATION_SPEED = 0.15;
    private static final double VISUAL_ROTATION_LIFT = 0.02; // slight upward push against portal's own fall
    private static final int VISUAL_PARTICLES_PER_POINT = 2;
    private static final double VISUAL_CRIMSON_MIX_CHANCE = 0.10;
    private static final Vector3f VISUAL_CRIMSON_COLOR = new Vector3f(0.55f, 0.05f, 0.08f);
    private static final float VISUAL_CRIMSON_SCALE = 1.2f; // the requested 20% larger

    // Spawn-warning flare timing — see beaconTick()'s RIFT_OPEN spawn-timer logic. Private constants
    // rather than GTRiftConfig values, to keep this addition small. Tuned after real playtesting
    // found a single one-shot burst 0.5s before spawn "barely noticeable": particles now emit
    // repeatedly across the whole window (not just once), the mob spawns partway through rather than
    // right at the end (so it visibly steps out of an already-established effect, not a blip that
    // immediately vanishes), and continue a bit further afterward.
    private static final int SPAWN_FLARE_LEAD_TICKS = 30; // 1.5s from flare start to the mob spawning
    private static final int SPAWN_FLARE_TAIL_TICKS = 10; // +0.5s more of particles after the mob spawns
    private static final int SPAWN_FLARE_EMIT_INTERVAL_TICKS = 3; // ~150ms between bursts — reads as continuous, not isolated pops

    // Elite variant — deliberately far more exaggerated (longer, denser) per explicit request.
    private static final int ELITE_SPAWN_FLARE_LEAD_TICKS = 70; // 3.5s from flare start to the elite spawning
    private static final int ELITE_SPAWN_FLARE_TAIL_TICKS = 10; // +0.5s more of particles after it spawns
    private static final int ELITE_SPAWN_FLARE_EMIT_INTERVAL_TICKS = 2; // ~100ms — denser stream than the regular flare's

    // Every class level that adds its own @Persisted/@DescSynced fields must declare and return
    // its own merged holder — MetaMachine/MultiblockControllerMachine each do the same. Skipping
    // this means getFieldHolder() dispatches to MultiblockControllerMachine's version, which knows
    // nothing about fields declared here, so @DescSynced silently never syncs to the client
    // (found by comparing server vs. client log output — tier read back as -1 on the client only).
    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER =
            new ManagedFieldHolder(RiftBeaconMachine.class, MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    /** GT voltage tier detected from the casings used in this structure. -1 while unformed. */
    @Persisted
    @DescSynced
    public int tier = -1;

    @Persisted
    @DescSynced
    public BeaconState state = BeaconState.IDLE;

    /** -1 means "never configured" — set to the beacon's own tier on first formation. */
    @Persisted
    @DescSynced
    public int selectedDifficultyTier = -1;

    @Persisted
    @DescSynced
    public int selectedDurationMinutes = 5;

    @Persisted
    @DescSynced
    public long chargeStored = 0;

    @Persisted
    @DescSynced
    public long chargeTarget = 0;

    /** Not persisted — resetting the spawn cadence on chunk reload is harmless. */
    private int spawnTimerTicks = 0;

    /**
     * Not persisted — same reasoning as spawnTimerTicks; losing a ~2s-old pending flare on a reload is
     * inconsequential. Non-null for the whole SPAWN_FLARE_LEAD_TICKS+SPAWN_FLARE_TAIL_TICKS window
     * (see beaconTick()'s RIFT_OPEN spawn-timer logic) — the flare, the eventual spawn, and the
     * tail-end particles afterward all share this exact position.
     */
    private BlockPos pendingSpawnPos = null;
    private int pendingSpawnTicksElapsed = 0;
    private boolean pendingSpawnMobSpawned = false;
    // Rolled once at flare-decision time (not at actual spawn time) so the flare visual and the
    // eventual spawn agree on elite vs. normal — see RiftEventSpawner.rollIsElite's own doc comment.
    private boolean pendingSpawnIsElite = false;

    /** Not persisted — same reasoning as spawnTimerTicks. */
    private int quadrantRefreshTicks = 0;
    private int currentQuadrant = 0;

    /**
     * Persisted (not DescSynced — nothing client-side reads this directly; particle emission is
     * already broadcast server->client independently via sendParticles) so the visible tear stays
     * at the same spot across a reload instead of jumping to a new random position. Confirmed
     * BlockPos is natively supported by @Persisted via GTCEu's own precedent —
     * MEPatternBufferProxyPartMachine.bufferPos is a plain "private BlockPos" field with exactly
     * this annotation, no special handling needed.
     */
    @Persisted
    public BlockPos riftVisualPos = null;

    /** Not persisted — cosmetic only, resetting phase/cadence on reload is harmless. */
    private float riftVisualAngle = 0f;
    private int visualEffectTicks = 0;

    public RiftBeaconMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() != null && !getLevel().isClientSide()) {
            subscribeServerTick(this::beaconTick);

            // Safety net only, now that riftVisualPos is @Persisted — should rarely trigger (e.g. a
            // save from before this field existed). It's only ever assigned at the CHARGING ->
            // RIFT_OPEN transition, which won't happen again until the next full charge cycle, so
            // without either persistence or this fallback, a reload mid-rift would leave it null
            // forever for the rest of that rift and the visual would never reappear.
            if (state == BeaconState.RIFT_OPEN && riftVisualPos == null && getLevel() instanceof ServerLevel serverLevel) {
                riftVisualPos = RiftEventSpawner.findSpawnPosition(serverLevel, getPos(), serverLevel.getRandom());
                if (riftVisualPos == null) {
                    riftVisualPos = getPos();
                }
            }

            // Same reload-recovery reasoning as riftVisualPos above: RiftAmbienceTracker.register()
            // only ever runs from tryAccept()'s IDLE -> CHARGING transition, so a server restart (or
            // any other reload) mid-charge/mid-rift would otherwise silently orphan this beacon from
            // the tracker until its next full charge cycle. ensureTracked() is a no-op if the tracker
            // is already tracking this position (e.g. an ordinary chunk unload/reload while active).
            if (state == BeaconState.CHARGING || state == BeaconState.RIFT_OPEN) {
                RiftAmbienceTracker.ensureTracked(getLevel().dimension(), getPos(), state);
            }
        }
    }

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        Integer matchedTier = getMultiblockState().getMatchContext().get(RiftBeaconTierPredicate.MATCH_CONTEXT_KEY);
        this.tier = matchedTier != null ? matchedTier : GTValues.LV;
        // First formation ever: default to the beacon's own max tier. Reformation at a lower
        // tier than previously selected: clamp down. Reformation at an equal/higher tier: keep
        // the player's prior choice rather than forcing it back up.
        if (selectedDifficultyTier < 0 || selectedDifficultyTier > tier) {
            selectedDifficultyTier = tier;
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.tier = -1;
        this.state = BeaconState.IDLE;
        this.chargeStored = 0;
        this.chargeTarget = 0;
        syncRenderState();
    }

    /**
     * Mirrors {@code state} onto the controller's {@code MachineRenderState} so the front-face overlay
     * texture (see assets/gtrift/models/block/machine/rift_beacon.json) tracks the beacon's lifecycle —
     * same idiom GTCEu itself uses for e.g. the charger machine's own custom render-state property.
     */
    private void syncRenderState() {
        RiftBeaconRenderState desired = switch (state) {
            case IDLE, CHARGED -> RiftBeaconRenderState.INACTIVE;
            case CHARGING -> RiftBeaconRenderState.CHARGING;
            case RIFT_OPEN -> RiftBeaconRenderState.RIFT_OPEN;
        };
        if (getRenderState().hasProperty(RiftBeaconRenderState.PROPERTY) &&
                getRenderState().getValue(RiftBeaconRenderState.PROPERTY) != desired) {
            setRenderState(getRenderState().setValue(RiftBeaconRenderState.PROPERTY, desired));
        }
    }

    private void adjustDifficulty(int delta) {
        if (!isFormed() || state != BeaconState.IDLE) return;
        selectedDifficultyTier = Math.max(GTValues.ULV, Math.min(tier, selectedDifficultyTier + delta));
    }

    private void adjustDuration(int delta) {
        if (!isFormed() || state != BeaconState.IDLE) return;
        selectedDurationMinutes = Math.max(1, Math.min(60, selectedDurationMinutes + delta));
    }

    private long computeChargeTarget() {
        return GTValues.VA[selectedDifficultyTier] * 20L * 60L * selectedDurationMinutes;
    }

    private void tryAccept() {
        if (!isFormed() || state != BeaconState.IDLE) return;
        chargeTarget = computeChargeTarget();
        chargeStored = 0;
        state = BeaconState.CHARGING;
        syncRenderState();
        RiftAmbienceTracker.register(getLevel().dimension(), getPos());
    }

    private void beaconTick() {
        if (state == BeaconState.CHARGING) {
            for (var part : getParts()) {
                if (!(part.self() instanceof EnergyHatchPartMachine energyHatch)) continue;
                long remaining = chargeTarget - chargeStored;
                if (remaining <= 0) break;
                long toDrain = Math.min(energyHatch.energyContainer.getEnergyStored(), remaining);
                if (toDrain > 0) {
                    energyHatch.energyContainer.removeEnergy(toDrain);
                    chargeStored += toDrain;
                }
            }
            if (chargeStored >= chargeTarget) {
                chargeStored = chargeTarget;
                // CHARGED is an instantaneous pass-through, not a lingering state — see Phase 5
                // plan notes. Both assignments happen within this one tick; only the final value
                // (RIFT_OPEN) is ever observably synced to the client.
                state = BeaconState.CHARGED;
                state = BeaconState.RIFT_OPEN;
                syncRenderState();
                spawnTimerTicks = 0;
                pendingSpawnPos = null;
                pendingSpawnTicksElapsed = 0;
                pendingSpawnMobSpawned = false;
                pendingSpawnIsElite = false;
                quadrantRefreshTicks = 0;
                currentQuadrant = 0;
                riftVisualAngle = 0f;
                visualEffectTicks = 0;

                // The rift-open visual anchors somewhere out in the spawn ring — the actual tear
                // mobs are pouring through, not a decoration on the beacon itself. Falls back to
                // the beacon's own position only if the ring is misconfigured (radius <= buffer).
                if (getLevel() instanceof ServerLevel serverLevel) {
                    riftVisualPos = RiftEventSpawner.findSpawnPosition(serverLevel, getPos(), serverLevel.getRandom());
                    if (riftVisualPos == null) {
                        riftVisualPos = getPos();
                    }
                }
            }
        } else if (state == BeaconState.RIFT_OPEN) {
            chargeStored = Math.max(0, chargeStored - GTValues.VA[selectedDifficultyTier]);

            if (getLevel() instanceof ServerLevel serverLevel) {
                if (pendingSpawnPos == null) {
                    spawnTimerTicks--;
                    if (spawnTimerTicks <= 0) {
                        // Pick the position AND roll elite/normal now — before the mob itself is
                        // chosen — so the flare visual (regular vs. the far-more-exaggerated elite
                        // variant) matches what actually ends up spawning. The eventual trySpawnMob
                        // call below reuses this same decision rather than rolling its own. If the
                        // beacon leaves RIFT_OPEN before the mob has spawned (controller broken, event
                        // ends), this whole branch simply stops running and the mob never spawns —
                        // same "spawning stops immediately" behavior the base event already has, no
                        // extra handling needed for it.
                        pendingSpawnPos = RiftEventSpawner.findSpawnPosition(
                                serverLevel, getPos(), serverLevel.getRandom(), riftVisualPos);
                        if (pendingSpawnPos != null) {
                            pendingSpawnTicksElapsed = 0;
                            pendingSpawnMobSpawned = false;
                            pendingSpawnIsElite = RiftEventSpawner.rollIsElite(serverLevel.getRandom(), selectedDifficultyTier);
                            emitPendingSpawnFlare(serverLevel);
                        } else {
                            spawnTimerTicks = GTRiftConfig.INSTANCE.spawnIntervalTicks;
                        }
                    }
                } else {
                    pendingSpawnTicksElapsed++;
                    int leadTicks = pendingSpawnIsElite ? ELITE_SPAWN_FLARE_LEAD_TICKS : SPAWN_FLARE_LEAD_TICKS;
                    int tailTicks = pendingSpawnIsElite ? ELITE_SPAWN_FLARE_TAIL_TICKS : SPAWN_FLARE_TAIL_TICKS;
                    int emitInterval = pendingSpawnIsElite ? ELITE_SPAWN_FLARE_EMIT_INTERVAL_TICKS : SPAWN_FLARE_EMIT_INTERVAL_TICKS;

                    // Repeated bursts across the whole lead+tail window, not one single burst — a
                    // single one-shot flare tested as "barely noticeable"; this reads as a sustained
                    // effect the mob visibly steps out of, not a blip.
                    if (pendingSpawnTicksElapsed % emitInterval == 0) {
                        emitPendingSpawnFlare(serverLevel);
                    }

                    if (!pendingSpawnMobSpawned && pendingSpawnTicksElapsed >= leadTicks) {
                        RiftEventSpawner.trySpawnMob(serverLevel, pendingSpawnPos, selectedDifficultyTier, pendingSpawnIsElite);
                        pendingSpawnMobSpawned = true;
                    }

                    if (pendingSpawnTicksElapsed >= leadTicks + tailTicks) {
                        pendingSpawnPos = null;
                        spawnTimerTicks = GTRiftConfig.INSTANCE.spawnIntervalTicks;
                    }
                }

                quadrantRefreshTicks--;
                if (quadrantRefreshTicks <= 0) {
                    RiftEventSpawner.refreshQuadrant(serverLevel, getPos(), currentQuadrant);
                    currentQuadrant = (currentQuadrant + 1) % QUADRANT_COUNT;
                    quadrantRefreshTicks = TICKS_PER_QUADRANT;
                }

                visualEffectTicks--;
                if (visualEffectTicks <= 0 && riftVisualPos != null) {
                    emitRiftVisual(serverLevel, riftVisualPos, riftVisualAngle);
                    riftVisualAngle += VISUAL_ROTATION_STEP_DEGREES;
                    visualEffectTicks = VISUAL_EFFECT_INTERVAL_TICKS;
                }
            }

            if (chargeStored <= 0) {
                chargeStored = 0;
                chargeTarget = 0;
                state = BeaconState.IDLE;
                syncRenderState();
                riftVisualPos = null;
                pendingSpawnPos = null;
                pendingSpawnTicksElapsed = 0;
                pendingSpawnMobSpawned = false;
                pendingSpawnIsElite = false;
            }
        }
    }

    /** Dispatches to whichever spawn-warning-flare visual matches the pending spawn's already-rolled elite/normal outcome. */
    private void emitPendingSpawnFlare(ServerLevel serverLevel) {
        if (pendingSpawnIsElite) {
            RiftAmbienceTracker.emitEliteSpawnWarningFlare(serverLevel, pendingSpawnPos);
        } else {
            RiftAmbienceTracker.emitSpawnWarningFlare(serverLevel, pendingSpawnPos);
        }
    }

    /**
     * A short twisting stack of rotating particle rings — reads as a vertical tear rather than a
     * flat disc or a shapeless scatter. Each ring's rotation phase is offset from its neighbors so
     * the whole column appears to twist, not just spin as one rigid piece.
     */
    private static void emitRiftVisual(ServerLevel level, BlockPos center, float baseAngleDegrees) {
        double centerX = center.getX() + 0.5;
        double centerZ = center.getZ() + 0.5;
        double baseY = center.getY() + 1.0;
        var random = level.getRandom();

        for (int ring = 0; ring < VISUAL_RING_COUNT; ring++) {
            double ringAngle = Math.toRadians(baseAngleDegrees + ring * (360.0 / VISUAL_RING_COUNT));
            double y = baseY + ring * VISUAL_RING_HEIGHT_STEP;
            for (int i = 0; i < VISUAL_RING_PARTICLE_COUNT; i++) {
                double theta = ringAngle + i * (2 * Math.PI / VISUAL_RING_PARTICLE_COUNT);
                double x = centerX + Math.cos(theta) * VISUAL_RING_RADIUS;
                double z = centerZ + Math.sin(theta) * VISUAL_RING_RADIUS;

                if (random.nextDouble() < VISUAL_CRIMSON_MIX_CHANCE) {
                    // Left static (no velocity) on purpose — confirmed in-game the static/moving
                    // contrast against the rotating portal particles reads well, don't "fix" it.
                    level.sendParticles(new DustParticleOptions(VISUAL_CRIMSON_COLOR, VISUAL_CRIMSON_SCALE),
                            x, y, z, VISUAL_PARTICLES_PER_POINT, 0.0, 0.0, 0.0, 0.0);
                } else {
                    // Tangent direction (perpendicular to the radius, pointing the way theta
                    // increases) gives each particle real circular velocity. sendParticles only
                    // honors an exact (xOffset,yOffset,zOffset)*speed velocity vector when count=0
                    // (confirmed in ClientPacketListener#handleParticleEvent) — with count>0 those
                    // same fields become random gaussian jitter instead, which is why the previous
                    // version showed no visible rotation, just portal's own gravity-driven fall.
                    double tangentX = -Math.sin(theta);
                    double tangentZ = Math.cos(theta);
                    for (int p = 0; p < VISUAL_PARTICLES_PER_POINT; p++) {
                        double jitterX = x + (random.nextDouble() - 0.5) * 0.1;
                        double jitterZ = z + (random.nextDouble() - 0.5) * 0.1;
                        level.sendParticles(ParticleTypes.PORTAL, jitterX, y, jitterZ, 0,
                                tangentX, VISUAL_ROTATION_LIFT, tangentZ, VISUAL_ROTATION_SPEED);
                    }
                }
            }
        }
    }

    /**
     * "Rift: Idle/Charging/Charged/Open" — a separate row from statusText() so the numeric line below
     * doesn't need to encode the phase in its own wording (no more "Charging:"/"Rift open:" prefixes).
     * Plain text, no color: LabelWidget's color is a plain field, never re-synced by
     * detectAndSendChanges/writeInitialData (confirmed by decompiling LDLib) — only text goes through
     * the live Supplier/sync path, so a reactive color would need a custom widget (like GTBS's
     * ScaledLabelWidget, which extends sync to cover what stock LabelWidget doesn't); not worth it for
     * this feature.
     */
    private String riftStatusWord() {
        return switch (state) {
            case IDLE -> "Idle";
            case CHARGING -> "Charging";
            case CHARGED -> "Charged";
            case RIFT_OPEN -> "Open";
        };
    }

    /**
     * One unified "{stored} / {target} EU (pct%)" line for every state — CHARGING/CHARGED/RIFT_OPEN
     * use the real chargeTarget field; IDLE uses computeChargeTarget() (the prospective target) since
     * chargeTarget itself is zeroed whenever state returns to IDLE (see beaconTick()'s RIFT_OPEN ->
     * IDLE transition), not the live value tryAccept() would actually charge to.
     */
    private String statusText() {
        // selectedDifficultyTier is -1 until the structure has formed at least once (see its own field
        // doc) — computeChargeTarget() indexes GTValues.VA by it, so a never-formed controller's GUI
        // would otherwise throw ArrayIndexOutOfBoundsException the moment it's opened. IDLE is the only
        // state reachable with a still-unconfigured tier — CHARGING/CHARGED/RIFT_OPEN are only
        // reachable via tryAccept(), which already requires isFormed().
        if (state == BeaconState.IDLE && selectedDifficultyTier < 0) return "Not formed";

        long target = state == BeaconState.IDLE ? computeChargeTarget() : chargeTarget;
        long pct = target > 0 ? chargeStored * 100 / target : 0;
        // LabelWidget text is routed through I18n.get -> String.format, so a bare "%" (not part of a
        // valid conversion) throws and renders as "Format error: ..." — escape it.
        return formatEu(chargeStored) + " / " + formatEu(target) + " EU (" + pct + "%%)";
    }

    /** 999 -> "999", 1400 -> "1.4k", 2_000_000 -> "2.0M", 40_000_000_000 -> "40.0B" — always one decimal
     * place, even a trailing ".0", so the string's length stays constant as the value ticks up/down
     * instead of visually "bouncing" every time it crosses a whole-number boundary. B is needed, not
     * just k/M: max tier at a 60-minute duration can exceed 40 billion EU. */
    private static String formatEu(long value) {
        if (value < 1_000L) return Long.toString(value);
        if (value < 1_000_000L) return formatWithSuffix(value, 1_000.0, "k");
        if (value < 1_000_000_000L) return formatWithSuffix(value, 1_000_000.0, "M");
        return formatWithSuffix(value, 1_000_000_000.0, "B");
    }

    private static String formatWithSuffix(long value, double divisor, String suffix) {
        return String.format(Locale.ROOT, "%.1f", value / divisor) + suffix;
    }

    /**
     * A separate label (not appended to statusText()) so it doesn't overflow the GUI's fixed 176px
     * width on top of the already-long charge-status strings. Empty string renders as nothing, so
     * this row is silently blank when the current dimension has at least one eligible entry in either
     * pool. Reads RiftMobPool.NORMAL/ELITE directly — safe because LabelWidget's supplier only ever
     * runs server-side (LDLib's detectAndSendChanges/writeInitialData), with just the resulting string
     * synced to the client, so this never sees an unpopulated client-side pool.
     */
    private String dimensionWarningText() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) return "";
        ResourceKey<Level> dimension = serverLevel.dimension();
        boolean noEligibleMobs = RiftMobPool.NORMAL.eligibleEntries(dimension).isEmpty()
                && RiftMobPool.ELITE.eligibleEntries(dimension).isEmpty();
        return noEligibleMobs ? "No mobs can spawn here" : "";
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 176, 124);
        // Background — must be the first child so it renders behind the labels, otherwise the
        // panel is just bare text with nothing to signal a GUI is even open (found this the hard way).
        group.addWidget(new ImageWidget(0, 0, 176, 124, new ColorRectTexture(0xFF1A1A1A)));
        group.addWidget(new LabelWidget(10, 8, () -> isFormed() ? "Formed: yes" : "Formed: no"));
        group.addWidget(new LabelWidget(10, 20, () -> isFormed() ? "Tier: " + GTValues.VN[tier] : ""));
        group.addWidget(new LabelWidget(10, 32, () -> "Rift: " + riftStatusWord()));

        group.addWidget(new ButtonWidget(10, 46, 12, 12, cd -> adjustDifficulty(-1)));
        group.addWidget(new LabelWidget(14, 48, "-"));
        group.addWidget(new LabelWidget(28, 48,
                () -> "Difficulty: " + (selectedDifficultyTier >= 0 ? GTValues.VN[selectedDifficultyTier] : "-")));
        group.addWidget(new ButtonWidget(150, 46, 12, 12, cd -> adjustDifficulty(1)));
        group.addWidget(new LabelWidget(154, 48, "+"));

        group.addWidget(new ButtonWidget(10, 61, 12, 12, cd -> adjustDuration(-1)));
        group.addWidget(new LabelWidget(14, 63, "-"));
        group.addWidget(new LabelWidget(28, 63, () -> "Duration: " + selectedDurationMinutes + " min"));
        group.addWidget(new ButtonWidget(150, 61, 12, 12, cd -> adjustDuration(1)));
        group.addWidget(new LabelWidget(154, 63, "+"));

        group.addWidget(new LabelWidget(10, 78, this::statusText));
        group.addWidget(new LabelWidget(10, 90, this::dimensionWarningText));

        group.addWidget(new ButtonWidget(10, 104, 60, 16, cd -> tryAccept()));
        group.addWidget(new LabelWidget(20, 108, "Accept"));

        return group;
    }
}
