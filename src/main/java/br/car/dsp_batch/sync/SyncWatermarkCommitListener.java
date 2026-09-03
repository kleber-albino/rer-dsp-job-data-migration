package br.car.dsp_batch.sync;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Advances the sync watermark only when the job finishes successfully.
 * Used by layer jobs and AOI jobs that populate {@link WatermarkContextKeys}.
 */
@Slf4j
@Component
public class SyncWatermarkCommitListener implements JobExecutionListener {

    private final SyncStateRepository syncStateRepository;

    public SyncWatermarkCommitListener(SyncStateRepository syncStateRepository) {
        this.syncStateRepository = syncStateRepository;
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
            log.warn(
                    "Job {} finished with status={} — watermark will not advance",
                    jobExecution.getJobInstance().getJobName(),
                    jobExecution.getStatus()
            );
            return;
        }

        var context = jobExecution.getExecutionContext();
        String syncKey = context.getString(WatermarkContextKeys.SYNC_KEY, null);
        if (syncKey == null || syncKey.isBlank()) {
            syncKey = jobExecution.getJobParameters().getString("layerKey");
        }
        if (syncKey == null || syncKey.isBlank()) {
            return;
        }

        String sourceTable = context.getString(
                WatermarkContextKeys.SOURCE_TABLE,
                syncKey
        );
        String proposed = context.getString(WatermarkContextKeys.PROPOSED_WATERMARK, null);
        Instant watermark = proposed != null ? Instant.parse(proposed) : null;

        Boolean orphanCheckRan = (Boolean) context.get(WatermarkContextKeys.ORPHAN_CHECK_RAN);

        if (watermark != null) {
            syncStateRepository.advanceWatermark(
                    syncKey,
                    sourceTable,
                    watermark,
                    jobExecution.getId()
            );
            log.info("Watermark advanced for syncKey={} to {}", syncKey, watermark);
        }

        if (Boolean.TRUE.equals(orphanCheckRan)) {
            syncStateRepository.markOrphanCheck(syncKey, sourceTable);
            log.info("Orphan check timestamp updated for syncKey={}", syncKey);
        }
    }
}
