package br.car.dsp_batch.sync;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Persistence for incremental sync watermarks on the primary (batch) datasource
 * (schema {@code data_migration} on the destination database).
 */
@Repository
public class SyncStateRepository {

    private static final RowMapper<SyncState> ROW_MAPPER = (rs, rowNum) -> new SyncState(
            rs.getString("sync_key"),
            rs.getString("source_table"),
            toInstant(rs.getObject("watermark_last_event_at", OffsetDateTime.class)),
            toInstant(rs.getObject("last_success_at", OffsetDateTime.class)),
            (Long) rs.getObject("last_job_execution_id"),
            toInstant(rs.getObject("last_orphan_check_at", OffsetDateTime.class))
    );

    private final JdbcTemplate batchJdbcTemplate;

    public SyncStateRepository(@Qualifier("jdbcTemplate") JdbcTemplate batchJdbcTemplate) {
        this.batchJdbcTemplate = batchJdbcTemplate;
    }

    public Optional<SyncState> findBySyncKey(String syncKey) {
        try {
            SyncState state = batchJdbcTemplate.queryForObject(
                    """
                    SELECT sync_key, source_table, watermark_last_event_at, last_success_at,
                           last_job_execution_id, last_orphan_check_at
                    FROM batch_job_execution_sync_state
                    WHERE sync_key = ?
                    """,
                    ROW_MAPPER,
                    syncKey
            );
            return Optional.ofNullable(state);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    public Optional<Instant> findWatermark(String syncKey) {
        return findBySyncKey(syncKey).map(SyncState::watermarkLastEventAt);
    }

    /**
     * Advances the watermark after a successful run.
     * If {@code watermarkLastEventAt} is null, keeps the current value.
     */
    public void advanceWatermark(String syncKey,
                                 String sourceTable,
                                 Instant watermarkLastEventAt,
                                 long jobExecutionId) {
        Instant now = Instant.now();
        batchJdbcTemplate.update(
                """
                INSERT INTO batch_job_execution_sync_state (
                    sync_key, source_table, watermark_last_event_at, last_success_at,
                    last_job_execution_id, last_orphan_check_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, NULL, ?)
                ON CONFLICT (sync_key) DO UPDATE SET
                    source_table = EXCLUDED.source_table,
                    watermark_last_event_at = COALESCE(EXCLUDED.watermark_last_event_at,
                        batch_job_execution_sync_state.watermark_last_event_at),
                    last_success_at = EXCLUDED.last_success_at,
                    last_job_execution_id = EXCLUDED.last_job_execution_id,
                    updated_at = EXCLUDED.updated_at
                """,
                syncKey,
                sourceTable,
                toOffsetDateTime(watermarkLastEventAt),
                toOffsetDateTime(now),
                jobExecutionId,
                toOffsetDateTime(now)
        );
    }

    public void markOrphanCheck(String syncKey, String sourceTable) {
        Instant now = Instant.now();
        batchJdbcTemplate.update(
                """
                INSERT INTO batch_job_execution_sync_state (
                    sync_key, source_table, watermark_last_event_at, last_success_at,
                    last_job_execution_id, last_orphan_check_at, updated_at
                ) VALUES (?, ?, NULL, NULL, NULL, ?, ?)
                ON CONFLICT (sync_key) DO UPDATE SET
                    source_table = EXCLUDED.source_table,
                    last_orphan_check_at = EXCLUDED.last_orphan_check_at,
                    updated_at = EXCLUDED.updated_at
                """,
                syncKey,
                sourceTable,
                toOffsetDateTime(now),
                toOffsetDateTime(now)
        );
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
