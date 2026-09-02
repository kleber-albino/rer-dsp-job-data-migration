package br.car.dsp_batch.sync;

import java.time.Instant;

/**
 * Incremental sync state for a job entity in schema {@code data_migration}.
 */
public record SyncState(
        String syncKey,
        String sourceTable,
        Instant watermarkLastEventAt,
        Instant lastSuccessAt,
        Long lastJobExecutionId,
        Instant lastOrphanCheckAt
) {
}
