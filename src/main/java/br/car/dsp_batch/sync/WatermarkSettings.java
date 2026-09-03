package br.car.dsp_batch.sync;

import java.time.Duration;

/**
 * Shared watermark tuning for incremental sync jobs.
 */
public final class WatermarkSettings {

    /** Maximum interval between orphan primary-key scans. */
    public static final Duration ORPHAN_CHECK_INTERVAL = Duration.ofHours(24);

    private WatermarkSettings() {
    }
}
